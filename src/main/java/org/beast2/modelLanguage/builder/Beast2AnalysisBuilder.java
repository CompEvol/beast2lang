package org.beast2.modelLanguage.builder;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Taxon;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.likelihood.TreeLikelihood;
import beast.base.evolution.tree.MRCAPrior;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeInterface;
import beast.base.evolution.tree.coalescent.ConstantPopulation;
import beast.base.evolution.tree.coalescent.RandomTree;
import beast.base.inference.*;
import beast.base.inference.parameter.Parameter;
import beast.base.inference.parameter.RealParameter;
import org.beast2.modelLanguage.model.Beast2Analysis;
import org.beast2.modelLanguage.operators.DefaultParameterOperator;
import org.beast2.modelLanguage.operators.DefaultTreeOperator;
import org.beast2.modelLanguage.operators.MCMCOperator;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.beast2.modelLanguage.BEASTObjectID.*;

/**
 * Builds the BEAST2 MCMC run from a Beast2Analysis (model + defaults).
 */
public class Beast2AnalysisBuilder {

    private static final Logger logger = Logger.getLogger(Beast2AnalysisBuilder.class.getName());

    private final Beast2ModelBuilderReflection modelBuilder;
    // TODO: to Alexei : why Map<String, Object> not Map<String, Operator> ?
    private final Map<String, Operator> operatorCache = new HashMap<>();

    public Beast2AnalysisBuilder(Beast2ModelBuilderReflection builder) {
        this.modelBuilder = builder;
    }

    /**
     * Wraps a pure Beast2Model in an MCMC run, using the parameters in Beast2Analysis.
     */
    public MCMC buildRun(Beast2Analysis analysis) throws Exception {
        // Build the BEAST2 objects if not already done
        if (modelBuilder.getAllObjects().isEmpty()) {
            modelBuilder.buildModel(analysis.getModel());
        }

        // Initialize trees with RandomTree initializer
        initializeTreesWithRandomTree();

        // Set up the core components
        State state = setupState();

        // Initialize state nodes using their distributions
        initializeStateNodes(state);

        List<Distribution> dists = modelBuilder.getCreatedDistributions();
        List<TreeLikelihood> likelihoods = filterTreeLikelihoods(dists);

        // Set up distributions
        CompoundDistribution prior = setupPrior(likelihoods);
        CompoundDistribution likelihood = setupLikelihood(likelihoods);
        CompoundDistribution posterior = setupPosterior(prior, likelihood);

        // Set up operators
        List<Operator> operators = setupOperators();

        // Create the MCMC object
        return setupMCMC(analysis, posterior, state, operators);
    }

    private void initializeTreesWithRandomTree() {
        try {
            Map<String, Object> objects = modelBuilder.getAllObjects();

            // First find all trees and MRCAPriors
            Map<String, List<MRCAPrior>> treeToPriors = new HashMap<>();

            // Collect all MRCAPriors and organize them by tree
            for (Object obj : objects.values()) {
                if (obj instanceof MRCAPrior) {
                    MRCAPrior prior = (MRCAPrior) obj;
                    Tree priorTree = prior.treeInput.get();
                    if (priorTree != null) {
                        String treeId = priorTree.getID();
                        if (!treeToPriors.containsKey(treeId)) {
                            treeToPriors.put(treeId, new ArrayList<>());
                        }
                        treeToPriors.get(treeId).add(prior);
                        logger.info("Found MRCAPrior for tree " + treeId + ": " + prior.getID());
                    }
                }
            }

            // Map to store trees to their corresponding alignments
            Map<String, Alignment> treeToAlignment = new HashMap<>();

            // Find TreeLikelihood objects to determine which alignment is used with which tree
            for (Object obj : objects.values()) {
                if (obj instanceof TreeLikelihood) {
                    TreeLikelihood likelihood = (TreeLikelihood) obj;
                    TreeInterface tree = likelihood.treeInput.get();
                    Alignment data = likelihood.dataInput.get();

                    if (tree != null && data != null) {
                        treeToAlignment.put(tree.getID(), data);
                        logger.info("Found alignment " + data.getID() + " for tree " + tree.getID());
                    }
                }
            }

            // Now create tree initializers for each tree
            for (Map.Entry<String, Object> entry : objects.entrySet()) {
                if (entry.getValue() instanceof Tree) {
                    Tree tree = (Tree) entry.getValue();
                    String treeId = tree.getID();

                    // Find the alignment for this tree
                    Alignment alignment = treeToAlignment.get(treeId);
                    if (alignment == null) {
                        logger.warning("Could not find alignment for tree " + treeId + ", skipping initialization");
                        continue;
                    }

                    // Get any MRCAPriors for this tree
                    List<MRCAPrior> priors = treeToPriors.getOrDefault(treeId, new ArrayList<>());

                    // Determine which initializer to use
                    boolean hasCalibrations = priors.stream()
                            .anyMatch(p -> p.distInput.get() != null);

                    if (hasCalibrations) {

                        // Use RandomTree
                        try {
                            createRandomTreeInitializer(tree, alignment, treeId, priors);
                        } catch (Exception e) {
                            logger.warning("Failed to create tree initializer for " + treeId + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error in tree initialization: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create a RandomTree initializer for a tree
     */
    private void createRandomTreeInitializer(Tree tree, Alignment alignment, String treeId, List<MRCAPrior> priors) {
        try {
            // Create population size parameter
            RealParameter popSize = new RealParameter("1.0");
            popSize.setID("randomPopSize." + treeId);

            // Create constant population model
            ConstantPopulation popModel = new ConstantPopulation();
            popModel.setID("ConstantPopulation." + treeId);
            popModel.initByName("popSize", popSize);

            // Create RandomTree
            RandomTree randomTree = new RandomTree();
            randomTree.setID("RandomTree." + treeId);

            // Get or create taxon set
            TaxonSet taxonSet = tree.getTaxonset();
            if (taxonSet == null) {
                // If the tree doesn't have a taxon set yet, create one from the alignment
                taxonSet = new TaxonSet();
                taxonSet.setID(treeId + ".taxonset");
                taxonSet.initByName("alignment", alignment);
                tree.setInputValue("taxonset", taxonSet);
                modelBuilder.addObjectToModel(taxonSet.getID(), taxonSet);
            }

            // Use the taxon set for the RandomTree
            randomTree.setInputValue("taxonset", taxonSet);
            randomTree.setInputValue("populationModel", popModel);
            randomTree.setInputValue("initial", tree);
            randomTree.setInputValue("estimate", false);

            // Add MRCAPriors as constraints
            if (!priors.isEmpty()) {
                randomTree.setInputValue("constraint", priors);
            }

            // Add objects to model builder
            modelBuilder.addObjectToModel(popSize.getID(), popSize);
            modelBuilder.addObjectToModel(popModel.getID(), popModel);
            modelBuilder.addObjectToModel(randomTree.getID(), randomTree);

            logger.info("Successfully created RandomTree initializer: " + randomTree.getID());
        } catch (Exception e) {
            logger.warning("Failed to create RandomTree for " + treeId + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * Set up the MCMC object.
     */
    private MCMC setupMCMC(Beast2Analysis analysis, CompoundDistribution posterior,
                           State state, List<Operator> operators) {
        // Create MCMC object using BEAST2 API
        MCMC mcmc = new MCMC();
        mcmc.setID(ID_MCMC);

        // Create loggers
        List<beast.base.inference.Logger> loggers = setupLoggers(analysis, posterior);

        // Find all RandomTree initializers to add to the MCMC init input
        List<StateNodeInitialiser> initializers = findTreeInitializers();

        // Set up chainLength, state, operators, posterior, and loggers
        if (initializers.isEmpty()) {
            // Without initializers
            mcmc.initByName(
                    INPUT_CHAIN_LENGTH, analysis.getChainLength(),
                    INPUT_STATE, state,
                    INPUT_DISTRIBUTION, posterior,
                    INPUT_OPERATOR, operators,
                    INPUT_LOGGER, loggers
            );
        } else {
            // With initializers
            mcmc.initByName(
                    INPUT_CHAIN_LENGTH, analysis.getChainLength(),
                    INPUT_STATE, state,
                    INPUT_DISTRIBUTION, posterior,
                    INPUT_OPERATOR, operators,
                    INPUT_LOGGER, loggers,
                    "init", initializers
            );
            logger.info("Added " + initializers.size() + " tree initializers to MCMC");
        }

        return mcmc;
    }

    private List<StateNodeInitialiser> findTreeInitializers() {
        List<StateNodeInitialiser> initializers = new ArrayList<>();

        // Look for all initializers, not just RandomTree
        for (Map.Entry<String, Object> entry : modelBuilder.getAllObjects().entrySet()) {
            Object obj = entry.getValue();
            if (obj instanceof StateNodeInitialiser) {
                initializers.add((StateNodeInitialiser) obj);
                logger.info("Found initializer: " + entry.getKey() + " of class " + obj.getClass().getName());
            }
        }

        logger.info("Total initializers found: " + initializers.size());
        return initializers;
    }

    /**
     * Initialize a tree using RandomTree instead of manual construction
     */
    private void initializeWithRandomTree(Tree tree, Alignment alignment, String treeId) {
        try {
            // Get the tree's existing taxon set
            TaxonSet taxonSet = tree.getTaxonset();

            // If tree doesn't have a taxon set, try to find or create one
            if (taxonSet == null) {
                logger.info("No taxon set found for tree: " + treeId + ". Will try to find or create one.");

                // Try to find taxon set from any tree likelihood that uses this tree
                for (Object obj : modelBuilder.getAllObjects().values()) {
                    if (obj instanceof TreeLikelihood) {
                        TreeLikelihood likelihood = (TreeLikelihood) obj;
                        if (likelihood.treeInput.get() == tree) {
                            // Create taxon set from the alignment
                            Alignment data = likelihood.dataInput.get();
                            if (data != null) {
                                List<String> taxaNames = data.getTaxaNames();
                                taxonSet = new TaxonSet();
                                taxonSet.setID(treeId + ".taxa");
                                for (String taxName : taxaNames) {
                                    Taxon taxon = new Taxon(taxName);
                                    taxonSet.taxonsetInput.setValue(taxon, taxonSet);
                                }
                                // Store the taxon set in the tree
                                tree.setInputValue("taxonset", taxonSet);
                                modelBuilder.addObjectToModel(taxonSet.getID(), taxonSet);
                                break;
                            }
                        }
                    }
                }

                // If still no taxon set, we can't proceed
                if (taxonSet == null) {
                    logger.warning("Could not find or create taxon set for tree: " + treeId);
                    return;
                }
            }

            logger.info("Using taxon set with " + taxonSet.getTaxonCount() + " taxa for tree: " + treeId);

            // Create constant population model
            ConstantPopulation popModel = new ConstantPopulation();
            String popModelId = "ConstantPopulation.t:" + treeId;
            popModel.setID(popModelId);

            // Create population size parameter
            RealParameter popSize = new RealParameter();
            String popSizeId = "randomPopSize.t:" + treeId;
            popSize.setID(popSizeId);
            popSize.initByName("value", "1.0");

            // Add to the model
            modelBuilder.addObjectToModel(popSizeId, popSize);

            // Set up population model with the parameter
            popModel.initByName("popSize", popSize);
            modelBuilder.addObjectToModel(popModelId, popModel);

            // Create and configure RandomTree
            RandomTree randomTree = new RandomTree();
            String randomTreeId = "RandomTree.t:" + treeId;
            randomTree.setID(randomTreeId);

            // Configure RandomTree with the tree's taxon set
            randomTree.initByName(
                    "taxa", taxonSet,
                    "populationModel", popModel,
                    "initial", tree,
                    "estimate", false
            );

            // Add the RandomTree initializer to the model
            modelBuilder.addObjectToModel(randomTreeId, randomTree);

            // Initialize the tree
            randomTree.initStateNodes();

            logger.info("Successfully initialized tree using RandomTree: " + treeId);
        } catch (Exception e) {
            logger.warning("Failed to initialize tree with RandomTree: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Original filterTreeLikelihoods method unchanged
    private List<TreeLikelihood> filterTreeLikelihoods(List<Distribution> dists) {
        return dists.stream()
                .filter(d -> d instanceof TreeLikelihood)
                .map(d -> (TreeLikelihood) d)
                .collect(Collectors.toList());
    }

    private State setupState() {
        // Create a state object using BEAST2 API
        State state = new State();
        state.setID(ID_STATE);

        // Get state nodes from the model builder
        List<StateNode> stateNodes = modelBuilder.getCreatedStateNodes();

        // Remove duplicates based on ID
        Map<String, StateNode> uniqueNodes = new HashMap<>();
        for (StateNode node : stateNodes) {
            // Only add each unique ID once
            if (node.getID() != null && !uniqueNodes.containsKey(node.getID())) {
                uniqueNodes.put(node.getID(), node);
            }
        }

        if (uniqueNodes.isEmpty()) {
            logger.warning("No state nodes found! The analysis may not run correctly.");
        } else {
            logger.info("Setting up state with " + uniqueNodes.size() + " unique state nodes");
        }

        state.initByName(INPUT_STATE_NODE, new ArrayList<>(uniqueNodes.values()));
        return state;
    }

    /**
     * Initialize state nodes using distribution sampling
     * Only calls sample on "leaf" distributions to avoid redundant sampling
     */
    private void initializeStateNodes(State state) {
        try {
            // Create a random number generator
            java.util.Random random = new java.util.Random();

            // Get all distributions
            List<Distribution> allDistributions = modelBuilder.getCreatedDistributions();

            // Create a set of distributions that are inputs to other distributions
            Set<Distribution> usedAsInputs = new HashSet<>();
            for (Distribution dist : allDistributions) {
                // Check all inputs of this distribution
                for (Input<?> input : dist.getInputs().values()) {
                    Object value = input.get();
                    if (value instanceof Distribution) {
                        usedAsInputs.add((Distribution) value);
                    } else if (value instanceof Collection) {
                        for (Object item : (Collection<?>) value) {
                            if (item instanceof Distribution) {
                                usedAsInputs.add((Distribution) item);
                            }
                        }
                    }
                }
            }

            // Find leaf distributions (those that aren't inputs to any other distribution)
            List<Distribution> leafDistributions = new ArrayList<>();
            for (Distribution dist : allDistributions) {
                if (!usedAsInputs.contains(dist) && !(dist instanceof CompoundDistribution)) {
                    leafDistributions.add(dist);
                }
            }

            // If no leaf distributions found, use all distributions
            if (leafDistributions.isEmpty()) {
                leafDistributions = allDistributions;
            }

            // Sample from leaf distributions
            logger.info("Found " + leafDistributions.size() + " leaf distributions for sampling");
            for (Distribution dist : leafDistributions) {
                try {
                    logger.info("Sampling from " + dist.getID() + " to initialize state nodes");
                    dist.sample(state, random);
                } catch (Exception e) {
                    logger.warning("Could not sample from " + dist.getID() + ": " + e.getMessage());
                }
            }

            logger.info("State nodes initialized via distribution sampling");
        } catch (Exception e) {
            logger.warning("Could not initialize state nodes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Set up the loggers for MCMC.
     */
    private List<beast.base.inference.Logger> setupLoggers(Beast2Analysis analysis, CompoundDistribution posterior) {
        List<beast.base.inference.Logger> loggers = new ArrayList<>();

        // Get parameter log interval from analysis
        int logInterval = analysis.getLogEvery();
        String traceFileName = analysis.getTraceFileName();

        if (traceFileName == null || traceFileName.isEmpty()) {
            traceFileName = "model.log";
        }

        // 1. Console logger
        beast.base.inference.Logger consoleLogger = new beast.base.inference.Logger();
        consoleLogger.setID(ID_CONSOLE_LOGGER);
        // Add items to log for console
        List<BEASTInterface> consoleLogItems = new ArrayList<>();
        consoleLogItems.add(posterior); // Always log the posterior
        consoleLogger.initByName(INPUT_LOG_EVERY, logInterval, INPUT_LOG, consoleLogItems);
        loggers.add(consoleLogger);

        // 2. File logger for parameters
        beast.base.inference.Logger fileLogger = new beast.base.inference.Logger();
        fileLogger.setID(ID_FILE_LOGGER);
        // Add items to log for file
        List<BEASTInterface> fileLogItems = new ArrayList<>();
        fileLogItems.add(posterior); // Always log the posterior

        // Add all parameters to log
        for (Object obj : modelBuilder.getAllObjects().values()) {
            if ((obj instanceof Parameter) && !(obj instanceof Tree)) {
                fileLogItems.add((BEASTInterface) obj);
            }
        }

        fileLogger.initByName(
                INPUT_FILE_NAME, traceFileName,
                INPUT_LOG_EVERY, logInterval,
                INPUT_LOG, fileLogItems
        );
        loggers.add(fileLogger);

        // 3. Tree logger
        beast.base.inference.Logger treeLogger = new beast.base.inference.Logger();
        treeLogger.setID(ID_TREE_LOGGER);
        // Add trees to log
        List<BEASTInterface> treeLogItems = new ArrayList<>();

        // Find trees to log
        for (Object obj : modelBuilder.getAllObjects().values()) {
            if (obj instanceof Tree) {
                treeLogItems.add((BEASTInterface) obj);
            }
        }

        // Only add tree logger if we have trees to log
        if (!treeLogItems.isEmpty()) {
            String treeFileName = traceFileName.replace(".log", ".trees");
            if (treeFileName.equals(traceFileName)) {
                treeFileName = "model.trees";
            }

            treeLogger.initByName(
                    INPUT_FILE_NAME, treeFileName,
                    INPUT_LOG_EVERY, logInterval,
                    INPUT_MODE, "tree",
                    INPUT_LOG, treeLogItems
            );
            loggers.add(treeLogger);
        }

        return loggers;
    }



    public boolean hasOperators(String key) {
        return operatorCache.containsKey(key);
    }

    /**
     * this key will be used by {@link Beast2AnalysisBuilder#hasOperators(String)}
     */
    public void addOperator(String key, Operator operator) {
        operatorCache.put(key, operator);
    }

    public void clearOperatorCache() {
        operatorCache.clear();
    }

    public void fine(String message) {
        logger.fine(message);
    }

    public void warning(String message) {
        logger.warning(message);
    }

    /**
     * Set up MCMC operators.
     * Only creates operators for state nodes that are in the MCMC state.
     */
    private List<Operator> setupOperators() {
        // TODO hard code
        MCMCOperator paramOpFactory = new DefaultParameterOperator(this);
        MCMCOperator treeOpFactory = new DefaultTreeOperator(this);

        operatorCache.clear();

        // Get state nodes actually in the MCMC state
        List<StateNode> stateNodes = modelBuilder.getCreatedStateNodes();

        // Create operators only for state nodes that are in the state
        for (StateNode stateNode : stateNodes) {
            try {
                if (stateNode instanceof Parameter && !(stateNode instanceof Tree)) {
                    Parameter param = (Parameter) stateNode;
                    // directly add to operatorCache
                    paramOpFactory.addOperators(param);

                } else if (stateNode instanceof Tree) {
                    Tree tree = (Tree) stateNode;
                    // directly add to operatorCache
                    treeOpFactory.addOperators(tree);
                }
            } catch (Exception e) {
                logger.warning("Could not create operators for " + stateNode.getID() + ": " + e.getMessage());
            }
        }

        return operatorCache.values().stream().toList();
    }

    /**
     * Add appropriate operators for a parameter.

    private void addParameterOperators(List<Operator> operators, Parameter param) {
        String paramID = param.getID();

        // Skip if we've already created operators for this parameter
        if (operatorCache.containsKey(paramID + "Operator")) {
            return;
        }

        try {
            if (param.getDimension() > 1) {
                // Use Delta Exchange operator for multidimensional parameters
                DeltaExchangeOperator deltaOperator = new DeltaExchangeOperator();
                deltaOperator.setID(paramID + "Operator");
                deltaOperator.initByName(INPUT_PARAMETER, param, INPUT_WEIGHT, 1.0);
                operators.add(deltaOperator);
                operatorCache.put(paramID + "Operator", deltaOperator);
                logger.fine("Added DeltaExchangeOperator for " + paramID);
            } else {
                // Use Scale operator for scalar parameters
                ScaleOperator operator = new ScaleOperator();
                operator.setID(paramID + "Operator");
                operator.initByName(INPUT_PARAMETER, param, INPUT_WEIGHT, 1.0);
                operators.add(operator);
                operatorCache.put(paramID + "Operator", operator);
                logger.fine("Added ScaleOperator for " + paramID);
            }
        } catch (Exception e) {
            logger.warning("Could not create operator for " + paramID + ": " + e.getMessage());
        }
    }

    /**
     * Add appropriate operators for a tree.

    private void addTreeOperators(List<Operator> operators, Tree tree) {
        String treeID = tree.getID();

        // Skip if we've already created operators for this tree
        if (operatorCache.containsKey(treeID + "SubtreeSlide")) {
            return;
        }

        try {
            // SubtreeSlide operator
            SubtreeSlide subtreeSlide = new SubtreeSlide();
            subtreeSlide.setID(treeID + "SubtreeSlide");
            subtreeSlide.initByName(INPUT_TREE, tree, INPUT_WEIGHT, 5.0);
            operators.add(subtreeSlide);
            operatorCache.put(treeID + "SubtreeSlide", subtreeSlide);

            // Narrow Exchange operator
            Exchange narrowExchange = new Exchange();
            narrowExchange.setID(treeID + "NarrowExchange");
            narrowExchange.initByName(INPUT_TREE, tree, INPUT_WEIGHT, 5.0, "isNarrow", true);
            operators.add(narrowExchange);
            operatorCache.put(treeID + "NarrowExchange", narrowExchange);

            // Wide Exchange operator
            Exchange wideExchange = new Exchange();
            wideExchange.setID(treeID + "WideExchange");
            wideExchange.initByName(INPUT_TREE, tree, INPUT_WEIGHT, 3.0, "isNarrow", false);
            operators.add(wideExchange);
            operatorCache.put(treeID + "WideExchange", wideExchange);

            // Wilson-Balding operator
            WilsonBalding wilsonBalding = new WilsonBalding();
            wilsonBalding.setID(treeID + "WilsonBalding");
            wilsonBalding.initByName(INPUT_TREE, tree, INPUT_WEIGHT, 3.0);
            operators.add(wilsonBalding);
            operatorCache.put(treeID + "WilsonBalding", wilsonBalding);

            // Tree Scaler operator
            ScaleOperator treeScaler = new ScaleOperator();
            treeScaler.setID(treeID + "TreeScaler");
            treeScaler.initByName(INPUT_TREE, tree, INPUT_WEIGHT, 3.0, "scaleFactor", 0.95);
            operators.add(treeScaler);
            operatorCache.put(treeID + "TreeScaler", treeScaler);

            // Root Height Scaler operator
            ScaleOperator rootHeightScaler = new ScaleOperator();
            rootHeightScaler.setID(treeID + "RootHeightScaler");
            rootHeightScaler.initByName(INPUT_TREE, tree, INPUT_WEIGHT, 3.0,
                    "scaleFactor", 0.95, "rootOnly", true);
            operators.add(rootHeightScaler);
            operatorCache.put(treeID + "RootHeightScaler", rootHeightScaler);

            // Uniform operator (for internal node heights)
            Uniform uniform = new Uniform();
            uniform.setID(treeID + "Uniform");
            uniform.initByName(INPUT_TREE, tree, INPUT_WEIGHT, 30.0);
            operators.add(uniform);
            operatorCache.put(treeID + "Uniform", uniform);

            logger.fine("Added 7 operators for tree " + treeID);
        } catch (Exception e) {
            logger.warning("Could not create operators for " + treeID + ": " + e.getMessage());
        }
    }*/

    /**
     * Set up the likelihood distribution.
     */
    private CompoundDistribution setupLikelihood(List<TreeLikelihood> treeLikelihoods) {
        // Create a compound distribution for the likelihood using BEAST2 API
        CompoundDistribution likelihood = new CompoundDistribution();
        likelihood.setID(ID_LIKELIHOOD);

        // Add tree likelihoods
        List<Distribution> likelihoods = new ArrayList<>(treeLikelihoods);

        likelihood.initByName(INPUT_DISTRIBUTION, likelihoods);
        logger.info("Set up likelihood with " + likelihoods.size() + " distributions");

        return likelihood;
    }

    /**
     * Set up the prior distribution.
     */
    private CompoundDistribution setupPrior(List<TreeLikelihood> treeLikelihoods) {
        // Create a compound distribution for the prior using BEAST2 API
        CompoundDistribution prior = new CompoundDistribution();
        prior.setID(ID_PRIOR);

        // Collect all prior distributions (excluding likelihoods)
        List<Distribution> priors = new ArrayList<>();

        for (Distribution dist : modelBuilder.getCreatedDistributions()) {
            // Skip if it's a likelihood
            if (treeLikelihoods.contains(dist)) {
                continue;
            }

            // Skip if it's the likelihood or posterior compound distribution
            String id = dist.getID();
            if (id != null && (id.equals(ID_LIKELIHOOD) || id.equals(ID_POSTERIOR))) {
                continue;
            }

            // Add it to the prior
            priors.add(dist);
        }

        prior.initByName(INPUT_DISTRIBUTION, priors);
        logger.info("Set up prior with " + priors.size() + " distributions");

        return prior;
    }

    /**
     * Set up the posterior distribution.
     */
    private CompoundDistribution setupPosterior(CompoundDistribution prior, CompoundDistribution likelihood) {
        // Create a compound distribution for the posterior using BEAST2 API
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.setID(ID_POSTERIOR);

        // Add prior and likelihood
        List<Distribution> distributions = new ArrayList<>();
        distributions.add(prior);
        distributions.add(likelihood);

        posterior.initByName(INPUT_DISTRIBUTION, distributions);
        logger.info("Set up posterior combining prior and likelihood");

        return posterior;
    }
}
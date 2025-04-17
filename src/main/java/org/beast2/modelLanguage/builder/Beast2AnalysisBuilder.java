package org.beast2.modelLanguage.builder;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.evolution.likelihood.TreeLikelihood;
import beast.base.evolution.operator.*;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.inference.*;
import beast.base.inference.operator.DeltaExchangeOperator;
import beast.base.inference.parameter.Parameter;
import org.beast2.modelLanguage.model.Beast2Analysis;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Builds the BEAST2 MCMC run from a Beast2Analysis (model + defaults).
 */
public class Beast2AnalysisBuilder {

    private static final Logger logger = Logger.getLogger(Beast2AnalysisBuilder.class.getName());

    // BEAST2 object IDs
    public static final String ID_TREE_LIKELIHOOD = "treeLikelihood";
    public static final String ID_SITE_MODEL = "siteModel";
    public static final String ID_CLOCK_MODEL = "clockModel";
    public static final String ID_GAMMA_SHAPE = "gammaShape";
    public static final String ID_PRIOR = "prior";
    public static final String ID_LIKELIHOOD = "likelihood";
    public static final String ID_POSTERIOR = "posterior";
    public static final String ID_STATE = "state";
    public static final String ID_MCMC = "mcmc";
    public static final String ID_CONSOLE_LOGGER = "consoleLogger";
    public static final String ID_FILE_LOGGER = "fileLogger";
    public static final String ID_TREE_LOGGER = "treeLogger";

    public static final String INPUT_STATE_NODE = "stateNode";
    public static final String INPUT_PARAMETER = "parameter";
    public static final String INPUT_WEIGHT = "weight";
    public static final String INPUT_TREE = "tree";
    public static final String INPUT_LOG_EVERY = "logEvery";
    public static final String INPUT_LOG = "log";
    public static final String INPUT_FILE_NAME = "fileName";
    public static final String INPUT_MODE = "mode";
    public static final String INPUT_CHAIN_LENGTH = "chainLength";
    public static final String INPUT_STATE = "state";
    public static final String INPUT_DISTRIBUTION = "distribution";
    public static final String INPUT_OPERATOR = "operator";
    public static final String INPUT_LOGGER = "logger";

    private final Beast2ModelBuilderReflection modelBuilder;
    private final Map<String, Object> operatorCache = new HashMap<>();

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

        // Initialize trees with basic topology first
        initializeTrees();

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
        MCMC mcmc = setupMCMC(analysis, posterior, state, operators);
        return mcmc;
    }

    /**
     * Initialize all Tree objects with a basic topology
     */
    private void initializeTrees() {
        try {
            Map<String, Object> objects = modelBuilder.getAllObjects();
            for (Map.Entry<String, Object> entry : objects.entrySet()) {
                if (entry.getValue() instanceof Tree) {
                    Tree tree = (Tree) entry.getValue();

                    // Skip if tree already has a root node
                    if (tree.getRoot() != null) {
                        continue;
                    }

                    // Create a simple 2-taxon tree
                    try {
                        logger.info("Initializing tree with basic topology: " + entry.getKey());

                        // Create root node and two child nodes
                        Node rootNode = new Node();
                        rootNode.setHeight(1.0);

                        Node leftChild = new Node();
                        leftChild.setHeight(0.0);
                        leftChild.setID(entry.getKey() + ".taxon1");

                        Node rightChild = new Node();
                        rightChild.setHeight(0.0);
                        rightChild.setID(entry.getKey() + ".taxon2");

                        // Connect nodes
                        rootNode.addChild(leftChild);
                        rootNode.addChild(rightChild);

                        // Set the root node of the tree
                        tree.setRoot(rootNode);

                        logger.info("Successfully initialized tree with basic topology");
                    } catch (Exception e) {
                        logger.warning("Could not initialize tree with basic topology: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error initializing trees: " + e.getMessage());
        }
    }

    /**
     * Filter TreeLikelihood objects from a list of distributions
     */
    private List<TreeLikelihood> filterTreeLikelihoods(List<Distribution> dists) {
        return dists.stream()
                .filter(d -> d instanceof TreeLikelihood)
                .map(d -> (TreeLikelihood) d)
                .collect(Collectors.toList());
    }

    /**
     * Set up the state object to be sampled.
     */
    private State setupState() {
        // Create a state object using BEAST2 API
        State state = new State();
        state.setID(ID_STATE);

        // Get state nodes from the model builder
        List<StateNode> stateNodes = modelBuilder.getCreatedStateNodes();

        if (stateNodes.isEmpty()) {
            logger.warning("No state nodes found! The analysis may not run correctly.");
        } else {
            logger.info("Setting up state with " + stateNodes.size() + " state nodes");
        }

        state.initByName(INPUT_STATE_NODE, stateNodes);
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
     * Set up the MCMC object.
     */
    private MCMC setupMCMC(Beast2Analysis analysis, CompoundDistribution posterior,
                           State state, List<Operator> operators) {
        // Create MCMC object using BEAST2 API
        MCMC mcmc = new MCMC();
        mcmc.setID(ID_MCMC);

        // Create loggers
        List<beast.base.inference.Logger> loggers = setupLoggers(analysis, posterior);

        // Set up chainLength, state, operators, and posterior using BEAST2 API
        mcmc.initByName(
                INPUT_CHAIN_LENGTH, Long.valueOf(analysis.getChainLength()),
                INPUT_STATE, state,
                INPUT_DISTRIBUTION, posterior,
                INPUT_OPERATOR, operators,
                INPUT_LOGGER, loggers
        );

        return mcmc;
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
            if (obj instanceof Parameter && !(obj instanceof Tree)) {
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

    /**
     * Set up MCMC operators.
     * Only creates operators for state nodes that are in the MCMC state.
     */
    private List<Operator> setupOperators() {
        List<Operator> operators = new ArrayList<>();
        operatorCache.clear();

        // Get state nodes actually in the MCMC state
        List<StateNode> stateNodes = modelBuilder.getCreatedStateNodes();
        Set<StateNode> stateNodeSet = new HashSet<>(stateNodes);

        // Create operators only for state nodes that are in the state
        for (StateNode stateNode : stateNodes) {
            try {
                if (stateNode instanceof Parameter && !(stateNode instanceof Tree)) {
                    Parameter param = (Parameter) stateNode;
                    addParameterOperators(operators, param);

                } else if (stateNode instanceof Tree) {
                    Tree tree = (Tree) stateNode;
                    addTreeOperators(operators, tree);
                }
            } catch (Exception e) {
                logger.warning("Could not create operators for " + stateNode.getID() + ": " + e.getMessage());
            }
        }

        return operators;
    }

    /**
     * Add appropriate operators for a parameter.
     */
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
     */
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
    }

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
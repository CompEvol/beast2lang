package org.beast2.modelLanguage.builder;

import beast.base.core.BEASTInterface;
import beast.base.evolution.likelihood.TreeLikelihood;
import beast.base.evolution.operator.*;
import beast.base.evolution.tree.Tree;
import beast.base.inference.*;
import beast.base.inference.operator.DeltaExchangeOperator;
import beast.base.inference.parameter.Parameter;
import org.beast2.modelLanguage.model.Beast2Analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the BEAST2 MCMC run from a Beast2Analysis (model + defaults).
 */
public class Beast2AnalysisBuilder {

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

    public Beast2AnalysisBuilder(Beast2ModelBuilderReflection modelBuilder) {
        this.modelBuilder = modelBuilder;
    }

    /**
     * Wraps a pure Beast2Model in an MCMC run, using the parameters in Beast2Analysis.
     */
    public MCMC buildRun(Beast2Analysis analysis) throws Exception {

        modelBuilder.buildBeast2Objects(analysis.getModel());

        State state = setupState();

        List<Distribution> dists = modelBuilder.getCreatedDistributions();

        List<TreeLikelihood> likelihoods = dists.stream().filter(o-> o instanceof TreeLikelihood).map(o -> (TreeLikelihood)o).collect(Collectors.toList());

        CompoundDistribution posterior = setupPosterior(setupPrior(likelihoods), setupLikelihood(likelihoods));

        MCMC mcmc = setupMCMC(analysis, posterior, state, setupOperators());
        return mcmc;
    }

    /**
     * Set up the state object to be sampled.
     */
    private State setupState() {
        // Create a state object using BEAST2 API
        State state = new State();
        state.setID(ID_STATE);

        List<StateNode> stateNodes = modelBuilder.getCreatedStateNodes();

        state.initByName(INPUT_STATE_NODE, stateNodes);
        return state;
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
        List<Logger> loggers = new ArrayList<>();

        // 1. Console logger
        Logger consoleLogger = new Logger();
        consoleLogger.setID(ID_CONSOLE_LOGGER);
        // Add items to log for console
        List<BEASTInterface> consoleLogItems = new ArrayList<>();
        consoleLogItems.add(posterior); // Always log the posterior
        consoleLogger.initByName(INPUT_LOG_EVERY, 1000, INPUT_LOG, consoleLogItems);
        loggers.add(consoleLogger);

        // 2. File logger for parameters
        Logger fileLogger = new Logger();
        fileLogger.setID(ID_FILE_LOGGER);
        // Add items to log for file
        List<BEASTInterface> fileLogItems = new ArrayList<>();
        fileLogItems.add(posterior); // Always log the posterior
        // Add all parameters to log
        for (Object obj : modelBuilder.getBeastObjects().values()) {
            // Skip clock rate if we're not using a clock
            if ( obj instanceof Parameter && !(obj instanceof Tree)) {
                fileLogItems.add((BEASTInterface) obj);
            }
        }
        fileLogger.initByName(
                INPUT_FILE_NAME, "model.log",  // Default name, will be updated by app
                INPUT_LOG_EVERY, 1000,
                INPUT_LOG, fileLogItems
        );
        loggers.add(fileLogger);

        // 3. Tree logger
        Logger treeLogger = new Logger();
        treeLogger.setID(ID_TREE_LOGGER);
        // Add trees to log
        List<BEASTInterface> treeLogItems = new ArrayList<>();
        // Find trees to log
        for (Object obj : modelBuilder.getBeastObjects().values()) {
            if (obj instanceof Tree) {
                treeLogItems.add((BEASTInterface) obj);
            }
        }
        treeLogger.initByName(
                INPUT_FILE_NAME, "model.trees",  // Default name, will be updated by app
                INPUT_LOG_EVERY, 1000,
                INPUT_MODE, "tree",
                INPUT_LOG, treeLogItems
        );
        loggers.add(treeLogger);

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
     * Set up MCMC operators.
     */
    private List<Operator> setupOperators() {
        List<Operator> operators = new ArrayList<>();

        // Make a defensive copy of the values to avoid ConcurrentModificationException
        List<Object> objectsCopy = new ArrayList<>(modelBuilder.getBeastObjects().values());

        // Set up operators for parameters using BEAST2 API
        for (Object obj : objectsCopy) {
            if (obj instanceof Parameter) {
                Parameter param = (Parameter) obj;
                String paramID = param.getID();

                if (param.getDimension() > 1) {
                    // Use Delta Exchange operator for multidimensional parameters
                    DeltaExchangeOperator deltaOperator = new DeltaExchangeOperator();
                    deltaOperator.setID(paramID + "Operator");
                    deltaOperator.initByName(INPUT_PARAMETER, param, INPUT_WEIGHT, 1.0);
                    operators.add(deltaOperator);
                } else {
                    // Use Scale operator for scalar parameters
                    ScaleOperator operator = new ScaleOperator();
                    operator.setID(paramID + "Operator");
                    operator.initByName(INPUT_PARAMETER, param, INPUT_WEIGHT, 1.0);
                    operators.add(operator);
                }
            }
        }

        // Set up operators for trees using BEAST2 API
        for (Object obj : objectsCopy) {
            if (obj instanceof Tree) {
                Tree tree = (Tree) obj;
                String treeID = tree.getID();

                // SubtreeSlide operator
                SubtreeSlide subtreeSlide = new SubtreeSlide();
                subtreeSlide.setID(treeID + "SubtreeSlide");
                subtreeSlide.initByName(INPUT_TREE, tree, INPUT_WEIGHT, 5.0);
                operators.add(subtreeSlide);

                // Narrow Exchange operator
                Exchange narrowExchange = new Exchange();
                narrowExchange.setID(treeID + "NarrowExchange");
                narrowExchange.initByName(INPUT_TREE, tree, INPUT_WEIGHT, 5.0, "isNarrow", true);
                operators.add(narrowExchange);

                // Wide Exchange operator
                Exchange wideExchange = new Exchange();
                wideExchange.setID(treeID + "WideExchange");
                wideExchange.initByName(INPUT_TREE, tree, INPUT_WEIGHT, 3.0, "isNarrow", false);
                operators.add(wideExchange);

                // Wilson-Balding operator
                WilsonBalding wilsonBalding = new WilsonBalding();
                wilsonBalding.setID(treeID + "WilsonBalding");
                wilsonBalding.initByName(INPUT_TREE, tree, INPUT_WEIGHT, 3.0);
                operators.add(wilsonBalding);

                // Tree Scaler operator
                ScaleOperator treeScaler = new ScaleOperator();
                treeScaler.setID(treeID + "TreeScaler");
                treeScaler.initByName(INPUT_TREE, tree, INPUT_WEIGHT, 3.0, "scaleFactor", 0.95);
                operators.add(treeScaler);

                // Root Height Scaler operator
                ScaleOperator rootHeightScaler = new ScaleOperator();
                rootHeightScaler.setID(treeID + "RootHeightScaler");
                rootHeightScaler.initByName(INPUT_TREE, tree, INPUT_WEIGHT, 3.0, "scaleFactor", 0.95, "rootOnly", true);
                operators.add(rootHeightScaler);

                // Uniform operator (for internal node heights)
                Uniform uniform = new Uniform();
                uniform.setID(treeID + "Uniform");
                uniform.initByName(INPUT_TREE, tree, INPUT_WEIGHT, 30.0);
                operators.add(uniform);
            }
        }
        return operators;
    }

    /**
     * Set up the likelihood distribution.
     */
    private CompoundDistribution setupLikelihood(List<TreeLikelihood> treeLikelihoods) throws Exception {
        // Create a compound distribution for the likelihood using BEAST2 API
        CompoundDistribution likelihood = new CompoundDistribution();
        likelihood.setID(ID_LIKELIHOOD);

        // Add tree likelihoods
        List<Distribution> likelihoods = new ArrayList<>(treeLikelihoods);

        likelihood.initByName(INPUT_DISTRIBUTION, likelihoods);

        return likelihood;
    }

    /**
     * Set up the prior distribution.
     */
    private CompoundDistribution setupPrior(List<TreeLikelihood> treeLikelihoods) throws Exception {
        // Create a compound distribution for the prior using BEAST2 API
        CompoundDistribution prior = new CompoundDistribution();
        prior.setID(ID_PRIOR);

        // Collect all prior distributions
        List<Distribution> priors = new ArrayList<>();

        for (Distribution dist : modelBuilder.getCreatedDistributions()) {
                String id = dist.getID();

                // Skip if already added, or if it's likelihood/posterior
                if (priors.contains(dist) ||
                        id.equals(ID_LIKELIHOOD) ||
                        id.equals(ID_POSTERIOR) || !treeLikelihoods.contains(dist)) {
                    continue;
                }
                // Otherwise, add it to the prior
                priors.add(dist);
            }

        prior.initByName(INPUT_DISTRIBUTION, priors);

        return prior;
    }

    /**
     * Set up the posterior distribution.
     */
    private CompoundDistribution setupPosterior(CompoundDistribution prior, CompoundDistribution likelihood)  {
        // Create a compound distribution for the posterior using BEAST2 API
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.setID(ID_POSTERIOR);

        // Add prior and likelihood
        List<Distribution> distributions = new ArrayList<>();
        distributions.add(prior);
        distributions.add(likelihood);

        posterior.initByName(INPUT_DISTRIBUTION, distributions);

        return posterior;
    }

}

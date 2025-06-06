package org.beast2.modelLanguage.converter;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.evolution.alignment.Alignment;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.inference.distribution.Prior;
import beast.base.inference.parameter.RealParameter;
import org.beast2.modelLanguage.builder.ModelObjectFactory;
import org.beast2.modelLanguage.model.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * Handles creation of Beast2Lang statements from BEAST objects
 */
public class StatementCreator {

    private static final Logger logger = Logger.getLogger(StatementCreator.class.getName());

    private final Map<BEASTInterface, String> objectToIdMap;
    private final ModelObjectFactory objectFactory;
    private final BeastConversionUtilities specialHandler;
    private final Set<BEASTInterface> usedDistributions;
    private final State state;

    public StatementCreator(Map<BEASTInterface, String> objectToIdMap,
                            ModelObjectFactory objectFactory,
                            BeastConversionUtilities specialHandler,
                            Set<BEASTInterface> usedDistributions,
                            State state) {
        this.objectToIdMap = objectToIdMap;
        this.objectFactory = objectFactory;
        this.specialHandler = specialHandler;
        this.usedDistributions = usedDistributions;
        this.state = state;
    }

    /**
     * Create a Beast2Lang statement for a BEAST object
     */
    public Statement createStatement(BEASTInterface obj) {
        String id = objectToIdMap.get(obj);

        // Handle state nodes with distributions first
        // BUT skip if this is a data source (like AlignmentFromNexus)
        if (obj instanceof StateNode && !specialHandler.isDataSource(obj)) {
            BEASTInterface dist = findDistributionFor((StateNode) obj);
            if (dist != null) {
                return createDistributionAssignment(obj, dist);
            }
        }

        // Then handle other objects with variable declarations
        Statement decl = createVariableDeclaration(obj);

        // Add annotations if needed
        if (obj instanceof Alignment && !(obj instanceof StateNode)) {
            if (specialHandler.isDataSource(obj)) {
                Map<String, Expression> params = new HashMap<>();
                Annotation annotation = new Annotation("data", params);
                return new AnnotatedStatement(List.of(annotation), decl);
            }
        }

        return decl;
    }

    /**
     * Create a distribution assignment statement (using ~)
     */
    public DistributionAssignment createDistributionAssignment(BEASTInterface param, BEASTInterface distribution) {
        String id = objectToIdMap.get(param);
        String className = param.getClass().getSimpleName();

        // Special handling for Prior - unwrap the inner distribution
        BEASTInterface actualDistribution = distribution;
        if (distribution instanceof Prior) {
            Prior prior = (Prior) distribution;
            BEASTInterface innerDist = prior.distInput.get();
            if (innerDist != null && objectFactory.isParametricDistribution(innerDist)) {
                actualDistribution = innerDist;
            }
        }

        // Create the expression for the distribution
        Expression expr = createDistributionExpression(param, actualDistribution);

        return new DistributionAssignment(className, id, expr);
    }

    /**
     * Create a variable declaration statement (using =)
     */
    private Statement createVariableDeclaration(BEASTInterface obj) {
        String id = objectToIdMap.get(obj);
        String className = obj.getClass().getSimpleName();
        Expression expr = createExpressionForObject(obj);

        VariableDeclaration decl = new VariableDeclaration(className, id, expr);

        // Check if this needs annotations
        if (obj instanceof Alignment) {
            logger.info("Checking annotations for alignment: " + id);

            // Check if it's a data source (not sampled)
            if (isDataSource(obj)) {
                logger.info("Creating @data annotation for: " + id);
                Map<String, Expression> params = new HashMap<>();
                Annotation annotation = new Annotation("data", params);
                return new AnnotatedStatement(List.of(annotation), decl);
            }
        }

        return decl;
    }

    private Expression createDistributionExpression(BEASTInterface param, BEASTInterface actualDistribution) {
        // Check if we need to add secondary inputs
        List<Argument> additionalArgs = new ArrayList<>();
        if (param instanceof StateNode) {
            collectSecondaryInputs(param, additionalArgs, actualDistribution); // Pass distribution context
        }

        // If we have additional args, we need to create a modified function call
        if (!additionalArgs.isEmpty()) {
            // Get the regular arguments for the distribution (with primary input filtered)
            List<Argument> distArgs = createArgumentsForDistribution(actualDistribution);

            // Combine them with the secondary inputs
            List<Argument> allArgs = new ArrayList<>(distArgs);
            allArgs.addAll(additionalArgs);

            // Create a new function call with all arguments
            return new FunctionCall(actualDistribution.getClass().getSimpleName(), allArgs);
        } else {
            // No secondary inputs, but still need proper filtering
            List<Argument> distArgs = createArgumentsForDistribution(actualDistribution);
            return new FunctionCall(actualDistribution.getClass().getSimpleName(), distArgs);
        }
    }

    private void collectSecondaryInputs(BEASTInterface stateNode, List<Argument> additionalArgs, BEASTInterface distribution) {
        logger.info("Checking secondary inputs for StateNode: " + stateNode.getID() + " of class " + stateNode.getClass().getName());

        // Log all available inputs
        logger.info("Available inputs for " + stateNode.getID() + ":");
        for (String inputName : stateNode.getInputs().keySet()) {
            logger.info("  - " + inputName);
        }

        // Get the primary input name for this distribution to avoid including it
        String primaryInputName = null;
        if (distribution != null && objectFactory.isDistribution(distribution)) {
            primaryInputName = objectFactory.getPrimaryInputName(distribution);
            logger.info("Primary input name for " + distribution.getClass().getSimpleName() + ": " + primaryInputName);
        }

        // Collect any inputs set on the state node that should be secondary inputs
        for (Input<?> input : stateNode.getInputs().values()) {
            String inputName = input.getName();
            logger.info("  Input '" + inputName + "' has value: " + input.get());

            // IMPORTANT: Skip if this is the primary input of the distribution
            if (primaryInputName != null && primaryInputName.equals(inputName)) {
                logger.info("    Skipping '" + inputName + "' - it's the primary input for the distribution");
                continue;
            }

            if (input.get() != null && !shouldSkipSecondaryInput(stateNode, input, distribution)) {
                Expression inputValue = createExpressionForInput(input);
                if (inputValue != null) {
                    logger.info("  Adding secondary input: " + inputName);
                    additionalArgs.add(new Argument(inputName, inputValue));
                }
            }
        }
    }

    private boolean shouldSkipSecondaryInput(BEASTInterface stateNode, Input<?> input, BEASTInterface distribution) {
        String inputName = input.getName();
        Object value = input.get();

        // Skip calculated inputs
        if (isCalculatedInput(stateNode, input)) {
            logger.info("    Skipping '" + inputName + "' - calculated input");
            return true;
        }

        // Skip empty inputs
        if (value == null || (value instanceof List && ((List<?>) value).isEmpty())) {
            logger.info("    Skipping '" + inputName + "' - empty");
            return true;
        }

        // Skip if it's at default value
        if (InputValidator.isDefaultValue(stateNode, input)) {
            logger.info("    Skipping '" + inputName + "' - at default value");
            return true;
        }

        // Skip certain standard StateNode inputs that aren't meant to be secondary
        Set<String> skipInputs = new HashSet<>(Arrays.asList(
                "id", "spec", "name", "estimate"
        ));

        if (skipInputs.contains(inputName)) {
            logger.info("    Skipping '" + inputName + "' - standard StateNode input");
            return true;
        }

        // NEW: Skip natural domain bounds for standard distributions
        if (distribution != null && ("lower".equals(inputName) || "upper".equals(inputName))) {
            if (specialHandler.shouldSuppressSecondaryInput(inputName, value, distribution)) {
                logger.info("    Skipping '" + inputName + "' - natural domain bound for " +
                        distribution.getClass().getSimpleName());
                return true;
            }
        }

        logger.info("    Including '" + inputName + "' as secondary input");
        return false;
    }

    public Expression createDistributionExpression(BEASTInterface distribution) {
        // Ensure the distribution is marked as used so primary input is filtered
        usedDistributions.add(distribution);

        // Create arguments with primary input filtered
        List<Argument> distArgs = createArgumentsForDistribution(distribution);
        return new FunctionCall(distribution.getClass().getSimpleName(), distArgs);
    }

    private List<Argument> createArgumentsForDistribution(BEASTInterface distribution) {
        // Ensure the distribution is marked as used so primary input is filtered
        usedDistributions.add(distribution);
        ExpressionCreator exprCreator = new ExpressionCreator(objectToIdMap, this);
        return exprCreator.createArgumentsForObject(distribution, usedDistributions, objectFactory);
    }

    public boolean isParameterFixed(RealParameter param) {
        // Check if this parameter is in the state (estimated parameters are in state)
        if (state != null) {
            for (StateNode stateNode : state.stateNodeInput.get()) {
                if (stateNode == param) {
                    return false; // It's in the state, so it's estimated
                }
            }
        }

        // Check if it has estimate=false explicitly
        try {
            for (Input<?> input : param.getInputs().values()) {
                if ("estimate".equals(input.getName()) && input.get() instanceof Boolean) {
                    return !(Boolean) input.get();
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        // If it's not in state and has generic ID, consider it fixed
        return param.getID() != null && param.getID().matches("RealParameter\\d*");
    }

    // Delegate methods to reduce duplication

    public Expression createExpressionForObject(BEASTInterface obj) {
        ExpressionCreator exprCreator = new ExpressionCreator(objectToIdMap, this);
        return exprCreator.createExpressionForObject(obj);
    }

    public List<Argument> createArgumentsForObject(BEASTInterface obj) {
        ExpressionCreator exprCreator = new ExpressionCreator(objectToIdMap, this);
        return exprCreator.createArgumentsForObject(obj, usedDistributions, objectFactory);
    }

    private Expression createExpressionForInput(Input<?> input) {
        ExpressionCreator exprCreator = new ExpressionCreator(objectToIdMap, this);
        return exprCreator.createExpressionForInput(input);
    }

    // Helper methods

    private boolean isDataSource(BEASTInterface obj) {
        return specialHandler.isDataSource(obj);
    }

    private BEASTInterface findDistributionFor(StateNode node) {
        for (BEASTInterface obj : objectToIdMap.keySet()) {
            if (objectFactory.isDistribution(obj)) {
                Distribution dist = (Distribution) obj;

                // Get the primary input name for this distribution type
                String primaryInputName = objectFactory.getPrimaryInputName(dist);

                if (primaryInputName != null) {
                    try {
                        Object primaryInputValue = objectFactory.getInputValue(dist, primaryInputName);
                        if (primaryInputValue == node) {
                            usedDistributions.add(dist);
                            return dist;
                        }
                    } catch (Exception e) {
                        // Log but continue searching
                        logger.fine("Failed to check primary input for " +
                                dist.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                }
            }
        }
        return null;
    }

    private boolean isCalculatedInput(BEASTInterface obj, Input<?> input) {
        // For Tree objects, taxonset is explicitly set, not calculated
        if (obj instanceof beast.base.evolution.tree.Tree && input.getName().equals("taxonset")) {
            return false;
        }

        // For StateNode objects, 'value' and 'dimension' are often calculated from other inputs
        if (obj instanceof StateNode) {
            String inputName = input.getName();
            return inputName.equals("value") || inputName.equals("dimension");
        }

        // For other cases, we can't reliably determine if it's calculated
        return false;
    }
}
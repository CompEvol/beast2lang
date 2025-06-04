package org.beast2.modelLanguage.converter.pipeline;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.inference.parameter.IntegerParameter;
import java.util.*;
import java.util.logging.Logger;

/**
 * Phase 4: Identify parameters that need RandomComposition priors
 */
public class RandomCompositionDetectionPhase implements ConversionPhase {
    private static final Logger logger = Logger.getLogger(RandomCompositionDetectionPhase.class.getName());

    @Override
    public void execute(ConversionContext context) {
        logger.info("Detecting RandomComposition parameters...");

        context.getRandomCompositionParameters().clear();

        for (BEASTInterface obj : context.getObjectToIdMap().keySet()) {
            // Pass MCMC context for operator interrogation
            if (requiresRandomCompositionPrior(obj, context.getMcmc())) {
                context.getRandomCompositionParameters().add(obj);
                logger.info("Identified RandomComposition parameter: " + obj.getID());
            }
        }

        logger.info("Found " + context.getRandomCompositionParameters().size() +
                " parameters requiring RandomComposition priors");
    }

    /**
     * Check if this parameter requires a RandomComposition prior
     * Detection based on: (a) IntegerParameter and (b) only operated on by DeltaExchange operators
     */
    private boolean requiresRandomCompositionPrior(BEASTInterface obj, BEASTInterface mcmc) {
        if (!(obj instanceof IntegerParameter)) {
            return false;
        }

        IntegerParameter param = (IntegerParameter) obj;
        String paramId = param.getID();

        logger.info("Checking IntegerParameter: " + paramId + " for RandomComposition requirement");

        // Find operators that operate on this parameter
        List<BEASTInterface> operatorsOnParam = findOperatorsForParameterInMCMC(param, mcmc);

        if (operatorsOnParam.isEmpty()) {
            logger.info("No operators found for " + paramId + " - not a RandomComposition candidate");
            return false;
        }

        // Check if ALL operators on this parameter are DeltaExchange operators with integer=true
        boolean allAreDeltaExchange = true;
        for (BEASTInterface operator : operatorsOnParam) {
            if (!isDeltaExchangeOperatorWithInteger(operator, param)) {
                allAreDeltaExchange = false;
                logger.info("Found non-DeltaExchange operator " + operator.getClass().getSimpleName() +
                        " on " + paramId + " - not a RandomComposition candidate");
                break;
            }
        }

        if (allAreDeltaExchange) {
            logger.info("Confirmed RandomComposition needed for " + paramId +
                    " (only operated on by DeltaExchange operators with integer=true)");
            return true;
        }

        return false;
    }

    /**
     * Find operators in MCMC that operate on the given parameter
     */
    private List<BEASTInterface> findOperatorsForParameterInMCMC(IntegerParameter param, BEASTInterface mcmc) {
        List<BEASTInterface> operators = new ArrayList<>();
        findOperatorsRecursively(param, mcmc, operators, new HashSet<>());
        return operators;
    }

    /**
     * Recursively search for operators that reference the parameter
     */
    private void findOperatorsRecursively(IntegerParameter param, BEASTInterface obj,
                                          List<BEASTInterface> operators, Set<BEASTInterface> visited) {
        if (obj == null || visited.contains(obj)) {
            return;
        }
        visited.add(obj);

        // Check if this object is an operator that references our parameter
        if (obj instanceof beast.base.inference.Operator && operatorReferencesParameter(obj, param)) {
            operators.add(obj);
            logger.info("Found operator " + obj.getClass().getSimpleName() + " operating on " + param.getID());
        }

        // Recursively check all inputs
        for (Input<?> input : obj.getInputs().values()) {
            if (input.get() != null) {
                if (input.get() instanceof BEASTInterface) {
                    findOperatorsRecursively(param, (BEASTInterface) input.get(), operators, visited);
                } else if (input.get() instanceof List) {
                    for (Object item : (List<?>) input.get()) {
                        if (item instanceof BEASTInterface) {
                            findOperatorsRecursively(param, (BEASTInterface) item, operators, visited);
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if an operator references the given parameter
     */
    private boolean operatorReferencesParameter(BEASTInterface operator, IntegerParameter param) {
        for (Input<?> input : operator.getInputs().values()) {
            Object value = input.get();
            if (value == param || (value instanceof List && ((List<?>) value).contains(param))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if an operator is a DeltaExchange operator with integer=true operating on the given parameter
     */
    private boolean isDeltaExchangeOperatorWithInteger(BEASTInterface operator, IntegerParameter param) {
        if (!(operator instanceof beast.base.inference.Operator)) {
            return false;
        }

        // Check if it's a DeltaExchange type operator by looking at the class hierarchy
        boolean isDeltaExchange = false;
        Class<?> currentClass = operator.getClass();

        while (currentClass != null && currentClass != Object.class) {
            if (currentClass.getSimpleName().contains("DeltaExchange")) {
                isDeltaExchange = true;
                break;
            }
            currentClass = currentClass.getSuperclass();
        }

        if (!isDeltaExchange) {
            return false;
        }

        // Check if it has integer=true and operates on our parameter
        boolean hasIntegerTrue = false;
        boolean operatesOnParam = false;

        for (Input<?> input : operator.getInputs().values()) {
            String inputName = input.getName();
            Object value = input.get();

            if ("integer".equals(inputName) && Boolean.TRUE.equals(value)) {
                hasIntegerTrue = true;
            }

            if (value == param || (value instanceof List && ((List<?>) value).contains(param))) {
                operatesOnParam = true;
            }
        }

        return hasIntegerTrue && operatesOnParam;
    }

    /**
     * Heuristic detection when operator information is not available
     */
    private boolean requiresRandomCompositionPriorHeuristic(IntegerParameter param) {
        String paramId = param.getID();

        int dimension = param.getDimension();
        if (dimension <= 1) {
            logger.info(paramId + " has dimension " + dimension + " - not a composition vector");
            return false;
        }

        // Calculate sum of current values
        int sum = 0;
        for (int i = 0; i < dimension; i++) {
            sum += param.getValue(i);
        }

        // Check if it looks like a composition vector
        boolean looksLikeComposition = (sum >= dimension);

        if (!looksLikeComposition) {
            logger.info(paramId + " sum=" + sum + ", dimension=" + dimension + " - doesn't look like composition");
            return false;
        }

        // Additional check: if ID contains common composition parameter patterns
        boolean hasCompositionName = paramId.toLowerCase().contains("group") ||
                paramId.toLowerCase().contains("size") ||
                paramId.toLowerCase().contains("count");

        if (hasCompositionName || sum > dimension) {
            logger.info("Heuristic: RandomComposition needed for " + paramId +
                    " (dimension=" + dimension + ", sum=" + sum + ", pattern match=" + hasCompositionName + ")");
            return true;
        }

        logger.info(paramId + " doesn't match heuristic composition criteria");
        return false;
    }

    @Override
    public String getName() {
        return "RandomComposition Detection";
    }

    @Override
    public String getDescription() {
        return "Identifies IntegerParameters that are only operated on by DeltaExchange operators";
    }
}
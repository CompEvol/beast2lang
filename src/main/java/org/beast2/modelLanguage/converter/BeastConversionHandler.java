package org.beast2.modelLanguage.converter;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Sequence;
import beast.base.evolution.tree.MRCAPrior;
import beast.base.evolution.tree.TreeDistribution;
import beast.base.evolution.tree.TreeIntervals;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.inference.parameter.IntegerParameter;
import org.beast2.modelLanguage.model.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Logger;

/**
 * Handles BEAST-specific conversion logic and special cases
 */
public class BeastConversionHandler {

    private static final Logger logger = Logger.getLogger(BeastConversionHandler.class.getName());

    private final Map<BEASTInterface, String> objectToIdMap;
    private final Set<BEASTInterface> usedDistributions;
    private int alignmentCounter = 0;

    public BeastConversionHandler(Map<BEASTInterface, String> objectToIdMap,
                                  Set<BEASTInterface> usedDistributions) {
        this.objectToIdMap = objectToIdMap;
        this.usedDistributions = usedDistributions;
    }

    // ===== ESSENTIAL METHODS =====

    /**
     * Check if this parameter requires a RandomComposition prior
     * Detection based on: (a) IntegerParameter and (b) only operated on by DeltaExchange operators
     */
    public boolean requiresRandomCompositionPrior(BEASTInterface obj, BEASTInterface mcmc) {
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
     * Create a RandomComposition distribution assignment for parameters with DeltaExchange constraints
     */
    public Statement createRandomCompositionStatement(IntegerParameter param,
                                                      Beast2ToBeast2LangConverter converter,
                                                      BEASTInterface mcmc) {
        String paramId = objectToIdMap.get(param);

        // Calculate n as the sum of the current parameter values
        int n = calculateSumOfParameterValues(param);
        int k = param.getDimension();

        logger.info("Creating RandomComposition for " + paramId + " with n=" + n + " (sum of current values), k=" + k);

        // Create the RandomComposition expression
        List<Argument> args = new ArrayList<>();
        args.add(new Argument("n", new Literal(n, Literal.LiteralType.INTEGER)));
        args.add(new Argument("k", new Literal(k, Literal.LiteralType.INTEGER)));

        Expression randomCompExpr = new FunctionCall("RandomComposition", args);

        logger.info("Created RandomComposition statement for " + paramId + " with n=" + n + ", k=" + k);

        return new DistributionAssignment("IntegerParameter", paramId, randomCompExpr);
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

    /**
     * Calculate the sum of values in an IntegerParameter
     */
    private int calculateSumOfParameterValues(IntegerParameter param) {
        int sum = 0;
        for (int i = 0; i < param.getDimension(); i++) {
            sum += param.getValue(i);
        }
        logger.info("Sum of values in " + param.getID() + ": " + sum);
        return sum;
    }

    /**
     * Check if a secondary input should be suppressed for distribution statements
     */
    public boolean shouldSuppressSecondaryInput(String inputName, Object value, BEASTInterface distribution) {
        if (distribution == null) return false;

        String distributionType = distribution.getClass().getSimpleName();
        return shouldSuppressNaturalBound(inputName, value, distributionType);
    }

    /**
     * Check if a bound should be suppressed because it's the natural domain of the distribution
     */
    public boolean shouldSuppressNaturalBound(String inputName, Object value, String distributionType) {
        if (value == null) return true;

        switch (distributionType.toLowerCase()) {
            case "gamma":
            case "exponential":
            case "lognormal":
            case "chi2":
            case "inversegamma":
                // These distributions naturally have lower=0
                if ("lower".equals(inputName) && isEffectivelyZero(value)) {
                    return true;
                }
                break;

            case "dirichlet":
                // Dirichlet naturally has lower=0, upper=1 for each component
                if ("lower".equals(inputName) && isEffectivelyZero(value)) {
                    return true;
                }
                if ("upper".equals(inputName) && isEffectivelyOne(value)) {
                    return true;
                }
                break;

            case "beta":
                // Beta naturally has lower=0, upper=1
                if ("lower".equals(inputName) && isEffectivelyZero(value)) {
                    return true;
                }
                if ("upper".equals(inputName) && isEffectivelyOne(value)) {
                    return true;
                }
                break;

            case "uniform":
                // Don't suppress bounds for Uniform - they're essential parameters
                break;

            default:
                break;
        }

        return false;
    }

    private boolean isEffectivelyZero(Object value) {
        if (value instanceof Number) {
            return Math.abs(((Number) value).doubleValue()) < 1e-10;
        }
        return false;
    }

    private boolean isEffectivelyOne(Object value) {
        if (value instanceof Number) {
            return Math.abs(((Number) value).doubleValue() - 1.0) < 1e-10;
        }
        return false;
    }

    /**
     * Generate unique alignment identifier
     */
    public String generateAlignmentId(Alignment alignment) {
        if (alignment.getID() != null && !alignment.getID().isEmpty()) {
            String baseId = alignment.getID().replaceAll("[^a-zA-Z0-9_]", "_");

            for (Map.Entry<BEASTInterface, String> entry : objectToIdMap.entrySet()) {
                if (entry.getValue().equals(baseId) && entry.getKey() != alignment) {
                    String uniqueId = baseId;
                    int counter = 2;
                    while (objectToIdMap.containsValue(uniqueId)) {
                        uniqueId = baseId + "_" + counter++;
                    }
                    return uniqueId;
                }
            }
            return baseId;
        }
        return "alignment" + (++alignmentCounter);
    }

    /**
     * Check if this is a tree distribution (TreeDistribution subclasses)
     */
    public boolean isTreeDistribution(BEASTInterface obj) {
        return obj instanceof TreeDistribution;
    }

    /**
     * Extract sequence data and create nexus statement
     */
    public Statement createAlignmentFromEmbeddedData(Alignment alignment, String alignmentId) {
        String fileName = alignmentId + ".nex";
        saveAlignmentToFile(alignment, fileName);

        Expression expr = new NexusFunction(fileName);
        VariableDeclaration decl = new VariableDeclaration("Alignment", alignmentId, expr);

        Map<String, Expression> params = new HashMap<>();
        Annotation annotation = new Annotation("data", params);

        return new AnnotatedStatement(Arrays.asList(annotation), decl);
    }

    /**
     * Write alignment to nexus file
     */
    public void saveAlignmentToFile(Alignment alignment, String fileName) {
        try (PrintWriter writer = new PrintWriter(fileName)) {
            writer.println("#NEXUS");
            writer.println("BEGIN DATA;");
            writer.println("DIMENSIONS NTAX=" + alignment.getTaxonCount() +
                    " NCHAR=" + alignment.getSiteCount() + ";");
            writer.println("FORMAT DATATYPE=" + alignment.getDataType().getTypeDescription() +
                    " MISSING=? GAP=-;");
            writer.println("MATRIX");

            for (Sequence seq : alignment.sequenceInput.get()) {
                writer.println(seq.taxonInput.get() + " " + seq.dataInput.get());
            }

            writer.println(";");
            writer.println("END;");
        } catch (IOException e) {
            logger.severe("Failed to write alignment file: " + fileName);
            throw new RuntimeException("Failed to write alignment file", e);
        }
    }

    /**
     * Group distributions by their target variable
     */
    public Map<BEASTInterface, List<BEASTInterface>> groupDistributionsByTarget(
            Collection<BEASTInterface> objects) {
        Map<BEASTInterface, List<BEASTInterface>> grouped = new HashMap<>();

        for (BEASTInterface obj : objects) {
            if (isTreeDistribution(obj)) {
                BEASTInterface tree = getTreeFromDistribution(obj);
                if (tree != null) {
                    grouped.computeIfAbsent(tree, k -> new ArrayList<>()).add(obj);
                }
            } else if (obj instanceof MRCAPrior) {
                MRCAPrior prior = (MRCAPrior) obj;
                BEASTInterface tree = prior.treeInput.get();
                if (tree != null) {
                    grouped.computeIfAbsent(tree, k -> new ArrayList<>()).add(prior);
                }
            }
        }

        return grouped;
    }

    /**
     * Check if object should be suppressed from decompiled output
     */
    public boolean shouldSuppressObject(BEASTInterface obj) {
        if (obj instanceof TreeIntervals) {
            TreeIntervals intervals = (TreeIntervals) obj;
            for (BEASTInterface other : objectToIdMap.keySet()) {
                if (other instanceof TreeDistribution) {
                    TreeDistribution treeDist = (TreeDistribution) other;
                    if (treeDist.treeIntervalsInput.get() == intervals) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Extract tree from distribution, handling TreeDistribution's XOR inputs
     */
    private BEASTInterface getTreeFromDistribution(BEASTInterface distribution) {
        if (distribution instanceof TreeDistribution) {
            TreeDistribution treeDist = (TreeDistribution) distribution;

            if (treeDist.treeInput.get() != null) {
                return (BEASTInterface) treeDist.treeInput.get();
            }

            if (treeDist.treeIntervalsInput.get() != null) {
                TreeIntervals intervals = treeDist.treeIntervalsInput.get();
                return intervals.treeInput.get();
            }
        }
        return null;
    }

    /**
     * Create distribution statement with calibrations
     */
    public Statement createDistributionStatementWithCalibrations(
            BEASTInterface target,
            BEASTInterface mainDistribution,
            List<MRCAPrior> calibrations,
            Beast2ToBeast2LangConverter converter) {

        String targetId = objectToIdMap.get(target);
        String className = target.getClass().getSimpleName();

        Expression distExpr = converter.createExpressionForObject(mainDistribution);
        DistributionAssignment distAssign = new DistributionAssignment(className, targetId, distExpr);

        if (calibrations.isEmpty()) {
            return distAssign;
        }

        List<Annotation> annotations = new ArrayList<>();
        for (MRCAPrior prior : calibrations) {
            try {
                Map<String, Expression> params = new HashMap<>();

                TaxonSet taxonSet = prior.taxonsetInput.get();
                if (taxonSet != null) {
                    String taxonSetId = objectToIdMap.get(taxonSet);
                    if (taxonSetId != null) {
                        params.put("taxonset", new Identifier(taxonSetId));
                    }
                }

                if (prior.distInput.get() != null) {
                    BEASTInterface dist = prior.distInput.get();
                    Expression distExpression = converter.createExpressionForObject(dist);
                    params.put("distribution", distExpression);
                }

                Boolean tipsOnly = prior.onlyUseTipsInput.get();
                if (tipsOnly != null && tipsOnly) {
                    params.put("leaf", new Literal(true, Literal.LiteralType.BOOLEAN));
                }

                Boolean monophyletic = prior.isMonophyleticInput.get();
                if (monophyletic != null && monophyletic && (tipsOnly == null || !tipsOnly)) {
                    params.put("monophyletic", new Literal(true, Literal.LiteralType.BOOLEAN));
                }

                if (params.containsKey("taxonset")) {
                    annotations.add(new Annotation("calibration", params));
                }
            } catch (Exception e) {
                logger.warning("Failed to create calibration annotation: " + e.getMessage());
            }
        }

        return new AnnotatedStatement(annotations, distAssign);
    }

    /**
     * Separate distributions into main distribution and calibrations
     */
    public DistributionSeparation separateDistributions(List<BEASTInterface> distributions) {
        BEASTInterface mainDistribution = null;
        List<MRCAPrior> calibrations = new ArrayList<>();

        for (BEASTInterface dist : distributions) {
            if (dist instanceof MRCAPrior) {
                calibrations.add((MRCAPrior) dist);
            } else if (mainDistribution == null) {
                mainDistribution = dist;
            }
        }

        return new DistributionSeparation(mainDistribution, calibrations);
    }

    /**
     * Check if object is a data source (like AlignmentFromNexus)
     */
    public boolean isDataSource(BEASTInterface obj) {
        String className = obj.getClass().getName();

        if (className.contains("AlignmentFromNexus")) {
            return true;
        }

        if (obj instanceof Alignment) {
            for (var input : obj.getInputs().values()) {
                if (input.getName().equals("fileName") && input.get() != null) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Helper class to hold separated distributions
     */
    public static class DistributionSeparation {
        public final BEASTInterface mainDistribution;
        public final List<MRCAPrior> calibrations;

        public DistributionSeparation(BEASTInterface mainDistribution, List<MRCAPrior> calibrations) {
            this.mainDistribution = mainDistribution;
            this.calibrations = calibrations;
        }
    }
}
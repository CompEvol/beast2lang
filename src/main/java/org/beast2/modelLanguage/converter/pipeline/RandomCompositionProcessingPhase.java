package org.beast2.modelLanguage.converter.pipeline;

import beast.base.core.BEASTInterface;
import beast.base.inference.parameter.IntegerParameter;
import org.beast2.modelLanguage.model.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Phase 7: Process parameters that need RandomComposition priors
 */
public class RandomCompositionProcessingPhase implements ConversionPhase {
    private static final Logger logger = Logger.getLogger(RandomCompositionProcessingPhase.class.getName());

    @Override
    public void execute(ConversionContext context) {
        logger.info("Processing RandomComposition parameters...");

        int processedCount = 0;

        for (BEASTInterface param : context.getRandomCompositionParameters()) {
            if (context.isProcessed(param)) {
                continue;
            }

            IntegerParameter intParam = (IntegerParameter) param;

            // Create the RandomComposition statement
            Statement stmt = createRandomCompositionStatement(intParam, context);

            if (stmt != null) {
                context.markProcessed(param, stmt);

                // Mark this parameter as having a distribution
                context.getUsedDistributions().add(param);

                logger.info("Created RandomComposition statement for: " + param.getID());
                processedCount++;
            }
        }

        logger.info("Processed " + processedCount + " RandomComposition parameters");
    }

    /**
     * Create a RandomComposition distribution assignment for parameters with DeltaExchange constraints
     */
    private Statement createRandomCompositionStatement(IntegerParameter param, ConversionContext context) {
        String paramId = context.getObjectToIdMap().get(param);

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

    @Override
    public String getName() {
        return "RandomComposition Processing";
    }

    @Override
    public String getDescription() {
        return "Creates RandomComposition distribution statements for constrained integer parameters";
    }
}
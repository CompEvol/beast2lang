package org.beast2.modelLanguage.converter.pipeline;

import beast.base.core.BEASTInterface;
import beast.base.inference.StateNode;
import org.beast2.modelLanguage.converter.BeastConversionUtilities;
import org.beast2.modelLanguage.model.Statement;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Phase 8: Process state nodes with their distributions
 */
public class StateNodeProcessingPhase implements ConversionPhase {
    private static final Logger logger = Logger.getLogger(StateNodeProcessingPhase.class.getName());

    @Override
    public void execute(ConversionContext context) {
        logger.info("Processing state nodes with distributions...");

        // Group distributions by their target
        Map<BEASTInterface, List<BEASTInterface>> distributionsByTarget =
                context.getConversionUtilities().groupDistributionsByTarget(context.getObjectToIdMap().keySet());

        int processedWithDistributions = 0;
        int processedWithoutDistributions = 0;

        // Process state nodes that have distributions
        for (Map.Entry<BEASTInterface, List<BEASTInterface>> entry : distributionsByTarget.entrySet()) {
            BEASTInterface target = entry.getKey();
            List<BEASTInterface> distributions = entry.getValue();

            if (target instanceof StateNode && !context.isProcessed(target)) {
                BeastConversionUtilities.DistributionSeparation separation =
                        context.getConversionUtilities().separateDistributions(distributions);

                Statement stmt = context.getConversionUtilities().createDistributionStatementWithCalibrations(
                        target, separation.mainDistribution, separation.calibrations, context);

                context.markProcessed(target, stmt);

                // Mark all distributions as processed and used
                for (BEASTInterface dist : distributions) {
                    context.markProcessed(dist, null); // No separate statement for the distribution
                    context.getUsedDistributions().add(dist);

                    // Ensure the distribution itself gets an identifier if it doesn't have one
                    if (!context.getObjectToIdMap().containsKey(dist)) {
                        String distId = context.generateIdentifier(dist);
                        context.getObjectToIdMap().put(dist, distId);
                    }
                }

                processedWithDistributions++;
            }
        }

        // Process other state nodes (but skip those with RandomComposition priors)
        for (StateNode node : context.getState().stateNodeInput.get()) {
            if (!context.isProcessed(node) && !context.getRandomCompositionParameters().contains(node)) {
                Statement stmt = context.getStatementCreator().createStatement(node);
                if (stmt != null) {
                    context.markProcessed(node, stmt);
                    processedWithoutDistributions++;
                }
            }
        }

        logger.info("Processed " + processedWithDistributions + " state nodes with distributions and " +
                processedWithoutDistributions + " without distributions");
    }

    @Override
    public String getName() {
        return "State Node Processing";
    }

    @Override
    public String getDescription() {
        return "Processes state nodes, creating distribution assignments where appropriate";
    }
}
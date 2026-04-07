package org.beast2.modelLanguage.converter.pipeline;

import beast.base.core.BEASTInterface;
import beast.base.evolution.tree.MRCAPrior;
import beast.base.inference.Distribution;
import beast.base.inference.StateNode;
import beast.base.inference.distribution.Prior;

import java.util.logging.Logger;

/**
 * Phase 5: Analyze distributions - identify used and inlined distributions
 */
public class DistributionAnalysisPhase implements ConversionPhase {
    private static final Logger logger = Logger.getLogger(DistributionAnalysisPhase.class.getName());

    @Override
    public void execute(ConversionContext context) {
        logger.info("Analyzing distributions...");

        // Identify distributions used in ~ statements
        identifyUsedDistributions(context);

        // Identify inlined distributions (from Prior unwrapping)
        identifyInlinedDistributions(context);

        logger.info("Found " + context.getUsedDistributions().size() + " used distributions and " +
                context.getInlinedDistributions().size() + " inlined distributions");
    }

    private void identifyUsedDistributions(ConversionContext context) {
        context.getUsedDistributions().clear();

        for (StateNode node : context.getState().stateNodeInput.get()) {
            for (BEASTInterface obj : context.getObjectToIdMap().keySet()) {
                if (context.getObjectFactory().isDistribution(obj)) {
                    Distribution dist = (Distribution) obj;
                    String primaryInputName = context.getObjectFactory().getPrimaryInputName(dist);

                    if (primaryInputName != null) {
                        try {
                            Object primaryInputValue = context.getObjectFactory().getInputValue(dist, primaryInputName);
                            if (primaryInputValue == node) {
                                context.getUsedDistributions().add(dist);
                                logger.info("Found used distribution: " + dist.getID() +
                                        " for state node " + node.getID());
                            }
                        } catch (Exception e) {
                            logger.warning("Failed to get input value: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private void identifyInlinedDistributions(ConversionContext context) {
        context.getInlinedDistributions().clear();

        for (BEASTInterface obj : context.getObjectToIdMap().keySet()) {
            if (obj instanceof Prior prior) {
                BEASTInterface innerDist = prior.distInput.get();
                if (innerDist != null && context.getObjectFactory().isParametricDistribution(innerDist)) {
                    context.getInlinedDistributions().add(innerDist);
                    logger.info("Found inlined distribution: " + innerDist.getID());
                }
            }
            // Also handle MRCAPrior distributions that are used in calibrations
            else if (obj instanceof MRCAPrior mrcaPrior) {
                BEASTInterface innerDist = mrcaPrior.distInput.get();
                if (innerDist != null && context.getObjectFactory().isParametricDistribution(innerDist)) {
                    context.getInlinedDistributions().add(innerDist);
                    logger.info("Found inlined calibration distribution: " + innerDist.getID());
                }
            }
        }
    }

    @Override
    public String getName() {
        return "Distribution Analysis";
    }

    @Override
    public String getDescription() {
        return "Identifies distributions used in ~ statements and inlined distributions";
    }
}
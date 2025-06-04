package org.beast2.modelLanguage.converter.pipeline;

import beast.base.core.BEASTInterface;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.likelihood.TreeLikelihood;
import org.beast2.modelLanguage.model.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * Phase 11: Handle observed alignments (final phase)
 */
public class ObservedAlignmentPhase implements ConversionPhase {
    private static final Logger logger = Logger.getLogger(ObservedAlignmentPhase.class.getName());

    @Override
    public void execute(ConversionContext context) {
        logger.info("Processing observed alignments...");

        int observedCount = 0;

        for (BEASTInterface obj : context.getObjectToIdMap().keySet()) {
            if (obj instanceof TreeLikelihood likelihood) {
                Alignment data = likelihood.dataInput.get();
                if (data != null && context.getProcessedAlignments().containsKey(context.getObjectToIdMap().get(data))) {
                    String alignmentId = "alignment_" + context.getObjectToIdMap().get(data);
                    Expression likelihoodExpr = context.getStatementCreator().createExpressionForObject(likelihood);

                    DistributionAssignment obsAlignment = new DistributionAssignment(
                            "Alignment", alignmentId, likelihoodExpr
                    );

                    Map<String, Expression> params = new HashMap<>();
                    params.put("data", new Identifier(context.getObjectToIdMap().get(data)));
                    Annotation annotation = new Annotation("observed", params);

                    context.getModel().addStatement(new AnnotatedStatement(List.of(annotation), obsAlignment));
                    observedCount++;
                }
            }
        }

        logger.info("Created " + observedCount + " observed alignment statements");
    }

    @Override
    public String getName() {
        return "Observed Alignment Processing";
    }

    @Override
    public String getDescription() {
        return "Creates @observed statements for alignments used in TreeLikelihood";
    }
}
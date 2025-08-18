package org.beast2.modelLanguage.converter.pipeline;

import org.beast2.modelLanguage.converter.BeastIdentifierNormaliser;

import java.util.logging.Logger;

/**
 * Phase 1: Normalize all identifiers to ensure they are valid Java identifiers
 */
public class IdentifierNormalizationPhase implements ConversionPhase {
    private static final Logger logger = Logger.getLogger(IdentifierNormalizationPhase.class.getName());

    @Override
    public void execute(ConversionContext context) {
        logger.info("Normalizing identifiers...");

        BeastIdentifierNormaliser normalizer = new BeastIdentifierNormaliser();
        normalizer.normaliseIdentifiers(context.getPosterior(), context.getState());

        // Also normalize MCMC identifiers if provided
        if (context.getMcmc() != null) {
            normalizer.normaliseIdentifiers(context.getMcmc(), context.getState());
        }

        logger.info("Identifier normalization completed");

        // Update statement creator with normalized state
        context.updateStatementCreator();
    }

    @Override
    public String getName() {
        return "Identifier Normalization";
    }

    @Override
    public String getDescription() {
        return "Converts BEAST identifiers (with dots, colons, etc.) to valid Java identifiers";
    }
}
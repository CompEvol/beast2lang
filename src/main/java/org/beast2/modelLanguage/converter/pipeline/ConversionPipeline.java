package org.beast2.modelLanguage.converter.pipeline;

import beast.base.core.BEASTInterface;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import org.beast2.modelLanguage.model.Beast2Model;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pipeline architecture for converting BEAST2 models to Beast2Lang.
 * Executes a series of phases in order, each handling a specific aspect of the conversion.
 */
public class ConversionPipeline {
    private static final Logger logger = Logger.getLogger(ConversionPipeline.class.getName());

    private final List<ConversionPhase> phases;
    private final boolean debugMode;

    private ConversionPipeline(Builder builder) {
        this.phases = new ArrayList<>(builder.phases);
        this.debugMode = builder.debugMode;
    }

    /**
     * Execute the conversion pipeline
     */
    public Beast2Model convert(Distribution posterior, State state, BEASTInterface mcmc) {
        logger.info("Starting conversion pipeline with " + phases.size() + " phases");

        // Create the conversion context with all shared state
        ConversionContext context = new ConversionContext(posterior, state, mcmc);

        // Execute each phase in order
        for (int i = 0; i < phases.size(); i++) {
            ConversionPhase phase = phases.get(i);
            String phaseName = phase.getName();

            logger.info("Executing phase " + (i + 1) + "/" + phases.size() + ": " + phaseName);

            try {
                long startTime = System.currentTimeMillis();

                phase.execute(context);

                long duration = System.currentTimeMillis() - startTime;
                logger.info("Phase " + phaseName + " completed in " + duration + "ms");

                if (debugMode) {
                    logContextState(context, phaseName);
                }

                // Validate context after each phase if in debug mode
                if (debugMode) {
                    validateContext(context, phaseName);
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in phase " + phaseName, e);
                throw new ConversionException("Conversion failed in phase: " + phaseName, e);
            }
        }

        logger.info("Conversion pipeline completed successfully");
        return context.getModel();
    }

    /**
     * Log the current state of the context (for debugging)
     */
    private void logContextState(ConversionContext context, String afterPhase) {
        logger.fine("Context state after " + afterPhase + ":");
        logger.fine("  - Objects identified: " + context.getObjectToIdMap().size());
        logger.fine("  - Statements created: " + context.getModel().getStatements().size());
        logger.fine("  - Used distributions: " + context.getUsedDistributions().size());
        logger.fine("  - Processed objects: " + context.getProcessedObjects().size());
    }

    /**
     * Validate the context is in a consistent state
     */
    private void validateContext(ConversionContext context, String afterPhase) {
        // Add validation logic here
        // For example: check that all referenced IDs exist, no circular dependencies, etc.
    }

    /**
     * Builder for creating conversion pipelines
     */
    public static class Builder {
        private final List<ConversionPhase> phases = new ArrayList<>();
        private boolean debugMode = false;

        public Builder addPhase(ConversionPhase phase) {
            phases.add(phase);
            return this;
        }

        public Builder enableDebugMode() {
            this.debugMode = true;
            return this;
        }

        public ConversionPipeline build() {
            if (phases.isEmpty()) {
                throw new IllegalStateException("Pipeline must have at least one phase");
            }
            return new ConversionPipeline(this);
        }
    }

    /**
     * Factory method for creating the standard conversion pipeline
     */
    public static ConversionPipeline createStandardPipeline() {
        return new Builder()
                .addPhase(new IdentifierNormalizationPhase())
                .addPhase(new ObjectIdentificationPhase())
                .addPhase(new RandomCompositionDetectionPhase())
                .addPhase(new DistributionAnalysisPhase())
                .addPhase(new AlignmentProcessingPhase())
                .addPhase(new RandomCompositionProcessingPhase())
                .addPhase(new StateNodeProcessingPhase())
                .addPhase(new RemainingObjectsPhase())
                .addPhase(new DependencySortingPhase())
                .addPhase(new ObservedAlignmentPhase())
                .build();
    }
}

/**
 * Exception thrown when conversion fails
 */
class ConversionException extends RuntimeException {
    public ConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
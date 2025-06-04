package org.beast2.modelLanguage.converter;

import beast.base.core.BEASTInterface;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import org.beast2.modelLanguage.model.*;
import org.beast2.modelLanguage.converter.pipeline.*;

import java.util.logging.Logger;

/**
 * Converts a BEAST2 object model to a Beast2Lang model using a pipeline architecture.
 * <p>
 * This refactored version delegates all conversion logic to specialized phases,
 * making the conversion process more modular, testable, and maintainable.
 */
public class Beast2ToBeast2LangConverter {

    private static final Logger logger = Logger.getLogger(Beast2ToBeast2LangConverter.class.getName());

    private final ConversionPipeline pipeline;
    private final boolean debugMode;

    /**
     * Create a converter with the standard pipeline
     */
    public Beast2ToBeast2LangConverter() {
        this(false);
    }

    /**
     * Create a converter with optional debug mode
     */
    public Beast2ToBeast2LangConverter(boolean debugMode) {
        this.debugMode = debugMode;

        // Build the pipeline
        ConversionPipeline.Builder builder = new ConversionPipeline.Builder();

        if (debugMode) {
            builder.enableDebugMode();
        }

        // Add all phases in order
        builder.addPhase(new IdentifierNormalizationPhase())
                .addPhase(new ObjectIdentificationPhase())
                .addPhase(new RandomCompositionDetectionPhase())
                .addPhase(new DistributionAnalysisPhase())
                .addPhase(new AlignmentProcessingPhase())
                .addPhase(new RandomCompositionProcessingPhase())
                .addPhase(new StateNodeProcessingPhase())
                .addPhase(new RemainingObjectsPhase())
                .addPhase(new DependencySortingPhase())
                .addPhase(new ObservedAlignmentPhase());

        this.pipeline = builder.build();
    }

    /**
     * Create a converter with a custom pipeline
     */
    public Beast2ToBeast2LangConverter(ConversionPipeline pipeline) {
        this.pipeline = pipeline;
        this.debugMode = false;
    }

    /**
     * Convert a BEAST2 analysis to a Beast2Lang model.
     *
     * @param posterior The posterior distribution
     * @param state The state containing all parameters
     * @param mcmc The MCMC object (optional, used for operator analysis)
     * @return The converted Beast2Lang model
     */
    public Beast2Model convertToBeast2Model(Distribution posterior, State state, BEASTInterface mcmc) {
        if (posterior == null) {
            throw new IllegalArgumentException("Posterior distribution cannot be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("State cannot be null");
        }

        logger.info("Starting BEAST2 to Beast2Lang conversion");

        try {
            // Execute the pipeline
            Beast2Model model = pipeline.convert(posterior, state, mcmc);

            logger.info("Conversion completed successfully");
            return model;

        } catch (Exception e) {
            logger.severe("Conversion failed: " + e.getMessage());
            throw new RuntimeException("Failed to convert BEAST2 model to Beast2Lang", e);
        }
    }

    // Legacy methods for compatibility with BeastConversionHandler
    // These can be removed once BeastConversionHandler is updated

    public Expression createExpressionForObject(BEASTInterface obj) {
        throw new UnsupportedOperationException(
                "This method is deprecated. Use ConversionContext.getStatementCreator().createExpressionForObject()");
    }

    public Expression createDistributionExpression(BEASTInterface distribution) {
        throw new UnsupportedOperationException(
                "This method is deprecated. Use ConversionContext.getStatementCreator().createDistributionExpression()");
    }
}
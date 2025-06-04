package org.beast2.modelLanguage.converter.pipeline;

/**
 * Interface for a single phase in the conversion pipeline.
 * Each phase handles a specific aspect of converting BEAST2 to Beast2Lang.
 */
public interface ConversionPhase {

    /**
     * Execute this phase of the conversion.
     * The phase should read from and write to the provided context.
     *
     * @param context The shared conversion context
     * @throws Exception if the phase fails
     */
    void execute(ConversionContext context) throws Exception;

    /**
     * Get a human-readable name for this phase.
     * Used for logging and debugging.
     *
     * @return The phase name
     */
    String getName();

    /**
     * Optional: Get a description of what this phase does.
     *
     * @return The phase description
     */
    default String getDescription() {
        return "";
    }
}
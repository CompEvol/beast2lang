package org.beast2.modelLanguage.builder.handlers;

import org.beast2.modelLanguage.builder.ObjectRegistry;
import org.beast2.modelLanguage.model.Argument;
import org.beast2.modelLanguage.model.NexusFunction;

import java.util.logging.Logger;

/**
 * Handler for the built-in nexus() function that reads alignments from Nexus files.
 * Updated to use ObjectRegistry interface instead of Map<String, Object>.
 */
public class NexusFunctionHandler extends BaseHandler {

    private static final Logger logger = Logger.getLogger(NexusFunctionHandler.class.getName());

    /**
     * Constructor
     */
    public NexusFunctionHandler() {
        super(NexusFunctionHandler.class.getName());
    }

    /**
     * Process a NexusFunction and return an Alignment object.
     *
     * @param nexusFunction The NexusFunction to process
     * @param registry The registry of existing objects
     * @return An Alignment object loaded from the Nexus file
     * @throws Exception If there is an error processing the function
     */
    public Object processFunction(NexusFunction nexusFunction, ObjectRegistry registry) {
        logger.info("Processing nexus() function");

        // Extract parameters
        String filePath = null;
        String alignmentId = "alignment"; // Default ID if none provided

        for (Argument arg : nexusFunction.getArguments()) {
            if ("file".equals(arg.getName())) {
                // Use getAllObjects() for resolving references
                Object resolvedValue = ExpressionResolver.resolveValue(arg.getValue(), registry);
                if (resolvedValue != null) {
                    filePath = resolvedValue.toString();
                }
            } else if ("id".equals(arg.getName())) {
                Object resolvedValue = ExpressionResolver.resolveValue(arg.getValue(), registry);
                if (resolvedValue != null) {
                    alignmentId = resolvedValue.toString();
                }
            }
        }

        // Validate parameters
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("nexus() function requires a 'file' parameter");
        }

        logger.info("Loading Nexus file: " + filePath);

        try {
            // Use factory to create alignment
            Object alignment = factory.createAlignment(filePath, alignmentId);

            // Add the alignment to the registry
            registry.register(alignmentId, alignment);

            logger.info("Successfully loaded alignment from Nexus file: " + filePath + " with ID: " + alignmentId);
            return alignment;
        } catch (Exception e) {
            logger.severe("Error parsing Nexus file: " + e.getMessage());
            throw new RuntimeException("Failed to parse Nexus file: " + filePath, e);
        }
    }
}
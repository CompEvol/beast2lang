package org.beast2.modelLanguage.builder.handlers;

import beast.base.evolution.alignment.Alignment;
import beast.base.parser.NexusParser;
import org.beast2.modelLanguage.model.Argument;
import org.beast2.modelLanguage.model.NexusFunction;

import java.io.File;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Handler for the built-in nexus() function that reads alignments from Nexus files.
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
     * @param objectRegistry The registry of existing objects
     * @return An Alignment object loaded from the Nexus file
     * @throws Exception If there is an error processing the function
     */
    public Alignment processFunction(NexusFunction nexusFunction, Map<String, Object> objectRegistry) throws Exception {
        logger.info("Processing nexus() function");

        // Extract parameters
        String filePath = null;
        String alignmentId = "alignment"; // Default ID if none provided

        for (Argument arg : nexusFunction.getArguments()) {
            if ("file".equals(arg.getName())) {
                Object resolvedValue = ExpressionResolver.resolveValue(arg.getValue(), objectRegistry);
                if (resolvedValue != null) {
                    filePath = resolvedValue.toString();
                }
            } else if ("id".equals(arg.getName())) {
                Object resolvedValue = ExpressionResolver.resolveValue(arg.getValue(), objectRegistry);
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

        // Create a NexusParser and parse the file
        NexusParser parser = new NexusParser();
        File file = new File(filePath);

        if (!file.exists()) {
            logger.severe("Nexus file not found: " + filePath);
            throw new IllegalArgumentException("Nexus file not found: " + filePath);
        }

        try {
            parser.parseFile(file);

            if (parser.m_alignment == null) {
                logger.severe("No alignment found in Nexus file: " + filePath);
                throw new RuntimeException("No alignment found in Nexus file: " + filePath);
            }

            // Set the ID on the alignment
            parser.m_alignment.setID(alignmentId);

            // Add the alignment to the object registry
            objectRegistry.put(alignmentId, parser.m_alignment);

            logger.info("Successfully loaded alignment from Nexus file: " + filePath + " with ID: " + alignmentId);
            return parser.m_alignment;
        } catch (Exception e) {
            logger.severe("Error parsing Nexus file: " + e.getMessage());
            throw new RuntimeException("Failed to parse Nexus file: " + filePath, e);
        }
    }
}
package org.beast2.modelLanguage.builder.handlers;

import org.beast2.modelLanguage.builder.ObjectRegistry;
import org.beast2.modelLanguage.model.AlignmentFunction;
import org.beast2.modelLanguage.model.Argument;
import org.beast2.modelLanguage.model.Expression;
import org.beast2.modelLanguage.model.MapExpression;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Handler for the built-in alignment() function that creates alignments from inline sequence data.
 */
public class AlignmentFunctionHandler extends BaseHandler {

    private static final Logger logger = Logger.getLogger(AlignmentFunctionHandler.class.getName());

    /**
     * Constructor
     */
    public AlignmentFunctionHandler() {
        super(AlignmentFunctionHandler.class.getName());
    }

    /**
     * Process an AlignmentFunction and return an Alignment object.
     *
     * @param alignmentFunction The AlignmentFunction to process
     * @param registry The registry of existing objects
     * @return An Alignment object created from the inline data
     * @throws Exception If there is an error processing the function
     */
    public Object processFunction(AlignmentFunction alignmentFunction, ObjectRegistry registry) {
        logger.info("Processing alignment() function");

        // Extract parameters
        Map<String, String> sequences = null;
        String dataType = "nucleotide"; // Default data type
        String alignmentId = "alignment"; // Default ID if none provided

        for (Argument arg : alignmentFunction.getArguments()) {
            if ("sequences".equals(arg.getName())) {
                // Extract sequences from MapExpression
                if (arg.getValue() instanceof MapExpression) {
                    sequences = extractSequences((MapExpression) arg.getValue(), registry);
                }
            } else if ("dataType".equals(arg.getName())) {
                Object resolvedValue = ExpressionResolver.resolveValue(arg.getValue(), registry);
                if (resolvedValue != null) {
                    dataType = resolvedValue.toString();
                }
            } else if ("id".equals(arg.getName())) {
                Object resolvedValue = ExpressionResolver.resolveValue(arg.getValue(), registry);
                if (resolvedValue != null) {
                    alignmentId = resolvedValue.toString();
                }
            }
        }

        // Validate parameters
        if (sequences == null || sequences.isEmpty()) {
            throw new IllegalArgumentException("alignment() function requires a 'sequences' parameter with sequence data");
        }

        logger.info("Creating alignment with " + sequences.size() + " sequences");

        try {
            // Use factory to create alignment from sequences
            Object alignment = factory.createAlignmentFromSequences(sequences, dataType, alignmentId);

            // Add the alignment to the registry
            registry.register(alignmentId, alignment);

            logger.info("Successfully created alignment with ID: " + alignmentId);
            return alignment;
        } catch (Exception e) {
            logger.severe("Error creating alignment: " + e.getMessage());
            throw new RuntimeException("Failed to create alignment", e);
        }
    }

    /**
     * Extract sequences from a MapExpression
     */
    private Map<String, String> extractSequences(MapExpression mapExpr, ObjectRegistry registry) {
        Map<String, String> sequences = new LinkedHashMap<>();

        for (Map.Entry<String, Expression> entry : mapExpr.getEntries().entrySet()) {
            String taxonName = entry.getKey();
            Object sequenceValue = ExpressionResolver.resolveValue(entry.getValue(), registry);

            if (sequenceValue != null) {
                sequences.put(taxonName, sequenceValue.toString());
            }
        }

        return sequences;
    }
}
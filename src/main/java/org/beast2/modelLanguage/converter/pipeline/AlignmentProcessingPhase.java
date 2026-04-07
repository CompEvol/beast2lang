package org.beast2.modelLanguage.converter.pipeline;

import beast.base.core.BEASTInterface;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Sequence;
import org.beast2.modelLanguage.model.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Logger;

/**
 * Phase 5: Process alignments first (they often have no dependencies)
 */
public class AlignmentProcessingPhase implements ConversionPhase {
    private static final Logger logger = Logger.getLogger(AlignmentProcessingPhase.class.getName());

    // Configuration constants
    private static final int DEFAULT_INLINE_THRESHOLD = 80; // Default max sequences for inlining
    private static final int DEFAULT_SEQUENCE_LENGTH_THRESHOLD = 1000; // Default max sequence length for inlining

    // Instance variables
    private int alignmentCounter = 0;

    @Override
    public void execute(ConversionContext context) {
        logger.info("Processing alignments...");

        int alignmentCount = 0;
        int inlinedCount = 0;
        int externalCount = 0;

        for (BEASTInterface obj : context.getObjectToIdMap().keySet()) {
            if (obj instanceof Alignment alignment) {
                if (context.isProcessed(alignment)) {
                    continue;
                }

                String alignmentId = generateAlignmentId(alignment, context.getObjectToIdMap());
                context.getObjectToIdMap().put(alignment, alignmentId);

                Statement stmt;
                if (alignment.sequenceInput.get() != null && !alignment.sequenceInput.get().isEmpty()) {
                    if (shouldInlineAlignment(alignment)) {
                        stmt = createInlineAlignment(alignment, alignmentId);
                        inlinedCount++;
                    } else {
                        stmt = createAlignmentFromEmbeddedData(alignment, alignmentId);
                        externalCount++;
                    }
                } else {
                    stmt = context.getStatementCreator().createStatement(alignment);
                }

                context.markProcessed(alignment, stmt);
                context.getProcessedAlignments().put(alignmentId, alignment);
                alignmentCount++;
            }
        }

        logger.info(String.format("Processed %d alignments (%d inlined, %d external)",
                alignmentCount, inlinedCount, externalCount));
    }

    /**
     * Determine whether an alignment should be inlined or written to external file
     */
    private boolean shouldInlineAlignment(Alignment alignment) {
        // Simple heuristic: inline small alignments
        int taxonCount = alignment.getTaxonCount();
        int siteCount = alignment.getSiteCount();

        if (taxonCount > DEFAULT_INLINE_THRESHOLD) {
            logger.fine(String.format("Alignment %s has %d taxa (> %d threshold), using external file",
                    alignment.getID(), taxonCount, DEFAULT_INLINE_THRESHOLD));
            return false;
        }

        if (siteCount > DEFAULT_SEQUENCE_LENGTH_THRESHOLD) {
            logger.fine(String.format("Alignment %s has %d sites (> %d threshold), using external file",
                    alignment.getID(), siteCount, DEFAULT_SEQUENCE_LENGTH_THRESHOLD));
            return false;
        }

        return true;
    }

    /**
     * Create an inline alignment statement
     */
    private Statement createInlineAlignment(Alignment alignment, String alignmentId) {
        // Create sequence map
        Map<String, Expression> sequenceMap = new LinkedHashMap<>();

        for (Sequence seq : alignment.sequenceInput.get()) {
            String taxonName = seq.taxonInput.get();
            String sequenceData = seq.dataInput.get();

            // Clean up taxon names if needed (replace spaces, special chars)
            String cleanTaxonName = taxonName.replaceAll("[^a-zA-Z0-9_]", "_");

            sequenceMap.put(cleanTaxonName, new Literal(sequenceData, Literal.LiteralType.STRING));
        }

        // Create the alignment expression
        List<Argument> args = new ArrayList<>();
        args.add(new Argument("sequences", new MapExpression(sequenceMap)));

        // Add dataType if it's not standard
        String dataType = alignment.getDataType().getTypeDescription();
        if (!"nucleotide".equalsIgnoreCase(dataType)) {
            args.add(new Argument("dataType", new Literal(dataType, Literal.LiteralType.STRING)));
        }

        Expression alignmentExpr = new AlignmentFunction(args);
        VariableDeclaration decl = new VariableDeclaration("Alignment", alignmentId, alignmentExpr);

        // Add @data annotation
        Map<String, Expression> params = new HashMap<>();
        Annotation annotation = new Annotation("data", params);

        logger.fine(String.format("Created inline alignment for %s with %d sequences",
                alignmentId, sequenceMap.size()));

        return new AnnotatedStatement(List.of(annotation), decl);
    }

    /**
     * Generate unique alignment identifier
     */
    private String generateAlignmentId(Alignment alignment, Map<BEASTInterface, String> objectToIdMap) {
        if (alignment.getID() != null && !alignment.getID().isEmpty()) {
            String baseId = alignment.getID().replaceAll("[^a-zA-Z0-9_]", "_");

            // Check if this ID is already taken by another object
            for (Map.Entry<BEASTInterface, String> entry : objectToIdMap.entrySet()) {
                if (entry.getValue().equals(baseId) && entry.getKey() != alignment) {
                    String uniqueId = baseId;
                    int counter = 2;
                    while (objectToIdMap.containsValue(uniqueId)) {
                        uniqueId = baseId + "_" + counter++;
                    }
                    return uniqueId;
                }
            }
            return baseId;
        }
        return "alignment" + (++alignmentCounter);
    }

    /**
     * Extract sequence data and create nexus statement
     */
    private Statement createAlignmentFromEmbeddedData(Alignment alignment, String alignmentId) {
        String fileName = alignmentId + ".nex";
        saveAlignmentToFile(alignment, fileName);

        Expression expr = new NexusFunction(fileName);
        VariableDeclaration decl = new VariableDeclaration("Alignment", alignmentId, expr);

        Map<String, Expression> params = new HashMap<>();
        Annotation annotation = new Annotation("data", params);

        return new AnnotatedStatement(List.of(annotation), decl);
    }

    /**
     * Write alignment to nexus file
     */
    private void saveAlignmentToFile(Alignment alignment, String fileName) {
        try (PrintWriter writer = new PrintWriter(fileName)) {
            writer.println("#NEXUS");
            writer.println("BEGIN DATA;");
            writer.println("DIMENSIONS NTAX=" + alignment.getTaxonCount() +
                    " NCHAR=" + alignment.getSiteCount() + ";");
            writer.println("FORMAT DATATYPE=" + alignment.getDataType().getTypeDescription() +
                    " MISSING=? GAP=-;");
            writer.println("MATRIX");

            for (Sequence seq : alignment.sequenceInput.get()) {
                writer.println(seq.taxonInput.get() + " " + seq.dataInput.get());
            }

            writer.println(";");
            writer.println("END;");
        } catch (IOException e) {
            logger.severe("Failed to write alignment file: " + fileName);
            throw new RuntimeException("Failed to write alignment file", e);
        }
    }

    @Override
    public String getName() {
        return "Alignment Processing";
    }

    @Override
    public String getDescription() {
        return "Processes alignment objects and creates nexus() statements or inline alignment() expressions";
    }
}
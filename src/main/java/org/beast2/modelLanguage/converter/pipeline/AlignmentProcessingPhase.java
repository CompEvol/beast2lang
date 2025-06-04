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

    // Instance variable to track alignment naming
    private int alignmentCounter = 0;

    @Override
    public void execute(ConversionContext context) {
        logger.info("Processing alignments...");

        int alignmentCount = 0;

        for (BEASTInterface obj : context.getObjectToIdMap().keySet()) {
            if (obj instanceof Alignment alignment) {
                if (context.isProcessed(alignment)) {
                    continue;
                }

                String alignmentId = generateAlignmentId(alignment, context.getObjectToIdMap());
                context.getObjectToIdMap().put(alignment, alignmentId);

                Statement stmt;
                if (alignment.sequenceInput.get() != null && !alignment.sequenceInput.get().isEmpty()) {
                    stmt = createAlignmentFromEmbeddedData(alignment, alignmentId);
                } else {
                    stmt = context.getStatementCreator().createStatement(alignment);
                }

                context.markProcessed(alignment, stmt);
                context.getProcessedAlignments().put(alignmentId, alignment);
                alignmentCount++;
            }
        }

        logger.info("Processed " + alignmentCount + " alignments");
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
        return "Processes alignment objects and creates nexus() statements or embedded data";
    }
}
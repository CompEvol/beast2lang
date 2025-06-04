package org.beast2.modelLanguage.builder;

import java.util.Map;

/**
 * Factory for specialized object types.
 */
public interface AlignmentFactory {
    /**
     * Create an alignment object from a file.
     */
    Object createAlignment(String filePath, String id) throws Exception;

    /**
     * Create an alignment from inline sequence data
     *
     * @param sequences Map of taxon names to sequence strings
     * @param dataType The data type (e.g., "nucleotide", "aminoacid")
     * @param alignmentId The ID for the alignment
     * @return The created alignment object
     * @throws Exception if creation fails
     */
    Object createAlignmentFromSequences(Map<String, String> sequences, String dataType, String alignmentId) throws Exception;
}
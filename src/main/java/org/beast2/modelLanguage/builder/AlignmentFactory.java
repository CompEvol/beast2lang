package org.beast2.modelLanguage.builder;

/**
 * Factory for specialized object types.
 */
public interface AlignmentFactory {
    /**
     * Create an alignment object from a file.
     */
    Object createAlignment(String filePath, String id) throws Exception;
}
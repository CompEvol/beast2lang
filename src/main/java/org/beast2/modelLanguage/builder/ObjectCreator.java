package org.beast2.modelLanguage.builder;

/**
 * Core interface for creating and managing model objects.
 * This is the minimal interface needed for object lifecycle management.
 */
public interface ObjectCreator {
    /**
     * Create an object of the specified class with the given ID.
     */
    Object createObject(String className, String id) throws Exception;

    /**
     * Set the ID of an object.
     */
    void setID(Object obj, String id) throws Exception;

    /**
     * Get the ID of an object.
     */
    String getID(Object obj) throws Exception;

    /**
     * Initialize and validate an object after all inputs have been set.
     */
    void initAndValidate(Object obj) throws Exception;

    /**
     * Initialize an object using initByName with variable arguments.
     */
    void initByName(Object obj, Object... args) throws Exception;
}
package org.beast2.modelLanguage.builder;

import org.beast2.modelLanguage.model.Calibration;

import java.util.Map;

/**
 * Interface for object registry that handlers can use to store and retrieve objects.
 * This provides a controlled way for handlers to interact with the object storage.
 */
public interface ObjectRegistry {

    /**
     * Register an object with the given ID
     */
    void register(String id, Object object);

    /**
     * Get an object by ID
     */
    Object get(String id);

    /**
     * Check if an object exists
     */
    boolean contains(String id);

    /**
     * Get all objects as an immutable view
     * Note: This returns a read-only view of the registry
     */
    Map<String, Object> getAllObjects();

    /**
     * Mark a variable as having a distribution (random variable)
     */
    void markAsRandomVariable(String varName);

    /**
     * Mark a variable as observed with optional data reference
     */
    void markAsObservedVariable(String varName, String dataRef);

    /**
     * Mark a variable as data-annotated
     */
    void markAsDataAnnotated(String varName);

    void addCalibration(String treeVar, Calibration calibration);
}

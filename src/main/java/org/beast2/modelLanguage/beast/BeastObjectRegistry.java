package org.beast2.modelLanguage.beast;

import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.MRCAPrior;
import beast.base.inference.Distribution;
import beast.base.inference.StateNode;
import org.beast2.modelLanguage.builder.ObjectRegistry;
import org.beast2.modelLanguage.model.Calibration;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Central registry for all BEAST2 objects created during model building.
 * This eliminates circular dependencies between the model builder and object factory.
 */
public class BeastObjectRegistry implements ObjectRegistry {
    private static final Logger logger = Logger.getLogger(BeastObjectRegistry.class.getName());

    // Main object storage
    private final Map<String, Object> objects = new HashMap<>();

    // Specialized storage for quick type-based lookups
    private final Map<String, StateNode> stateNodes = new HashMap<>();
    private final Map<String, Distribution> distributions = new HashMap<>();

    // Track metadata about objects
    private final Set<String> randomVariables = new HashSet<>();
    private final Set<String> observedVariables = new HashSet<>();
    private final Set<String> dataAnnotatedVariables = new HashSet<>();
    private final Map<String, String> observedDataReferences = new HashMap<>();

    /**
     * Register an object in the registry
     */
    public void register(String id, Object object) {
        if (id == null || object == null) {
            throw new IllegalArgumentException("Cannot register null id or object");
        }

        objects.put(id, object);

        // Also register in specialized maps if applicable
        if (object instanceof StateNode) {
            stateNodes.put(id, (StateNode) object);
            logger.fine("Registered StateNode: " + id);
        }

        if (object instanceof Distribution) {
            distributions.put(id, (Distribution) object);
            logger.fine("Registered Distribution: " + id);
        }

        logger.info("Registered object: " + id + " (" + object.getClass().getSimpleName() + ")");
    }

    /**
     * Get an object by ID
     */
    public Object get(String id) {
        return objects.get(id);
    }

    /**
     * Check if an object exists
     */
    public boolean contains(String id) {
        return objects.containsKey(id);
    }

    /**
     * Get all objects (returns a defensive copy)
     */
    public Map<String, Object> getAllObjects() {
        return new HashMap<>(objects);
    }

    /**
     * Get all StateNode objects
     */
    public Map<String, StateNode> getStateNodes() {
        return new HashMap<>(stateNodes);
    }

    /**
     * Get all Distribution objects
     */
    public List<Distribution> getDistributions() {
        return new ArrayList<>(distributions.values());
    }

    /**
     * Get all StateNodes that are random variables (have distributions) and not observed
     */
    public List<StateNode> getRandomStateNodes() {
        return randomVariables.stream()
                .filter(varName -> !observedVariables.contains(varName))
                .map(varName -> stateNodes.get(varName))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // Variable tracking methods

    /**
     * Mark a variable as random (has a distribution)
     */
    public void markAsRandomVariable(String varName) {
        randomVariables.add(varName);
        logger.info("Marked as random variable: " + varName);
    }

    /**
     * Mark a variable as observed
     */
    public void markAsObservedVariable(String varName, String dataRef) {
        observedVariables.add(varName);
        if (dataRef != null) {
            observedDataReferences.put(varName, dataRef);
        }
        logger.info("Marked as observed variable: " + varName +
                (dataRef != null ? " with data reference: " + dataRef : ""));
    }

    /**
     * Mark a variable as data-annotated
     */
    public void markAsDataAnnotated(String varName) {
        dataAnnotatedVariables.add(varName);
        logger.info("Marked as data-annotated: " + varName);
    }

    @Override
    public void addCalibration(String treeVar, Calibration calibration) {

        MRCAPrior mrcaPrior = new MRCAPrior();
        TaxonSet taxonSet = (TaxonSet)objects.get(calibration.getTaxonset());

        // TODO
    }

    /**
     * Check if a variable is random
     */
    public boolean isRandomVariable(String varName) {
        return randomVariables.contains(varName);
    }

    /**
     * Check if a variable is observed
     */
    public boolean isObservedVariable(String varName) {
        return observedVariables.contains(varName);
    }

    /**
     * Check if a variable is data-annotated
     */
    public boolean isDataAnnotated(String varName) {
        return dataAnnotatedVariables.contains(varName);
    }

    /**
     * Get the data reference for an observed variable
     */
    public String getDataReference(String varName) {
        return observedDataReferences.get(varName);
    }

    /**
     * Get all random variables
     */
    public List<String> getRandomVariables() {
        return new ArrayList<>(randomVariables);
    }

    /**
     * Get all observed variables
     */
    public List<String> getObservedVariables() {
        return new ArrayList<>(observedVariables);
    }

    /**
     * Get all data-annotated variables
     */
    public List<String> getDataAnnotatedVariables() {
        return new ArrayList<>(dataAnnotatedVariables);
    }

    /**
     * Clear the registry (useful for testing)
     */
    public void clear() {
        objects.clear();
        stateNodes.clear();
        distributions.clear();
        randomVariables.clear();
        observedVariables.clear();
        dataAnnotatedVariables.clear();
        observedDataReferences.clear();
        logger.info("Registry cleared");
    }

    /**
     * Get registry statistics
     */
    public String getStatistics() {
        return String.format(
                "Registry Statistics: %d total objects, %d StateNodes (%d random), " +
                        "%d Distributions, %d observed variables, %d data-annotated variables",
                objects.size(), stateNodes.size(), getRandomStateNodes().size(),
                distributions.size(), observedVariables.size(), dataAnnotatedVariables.size()
        );
    }
}
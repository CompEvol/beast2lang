package org.beast2.modelLanguage.beast;

import beast.base.core.Log;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.MRCAPrior;
import beast.base.inference.Distribution;
import beast.base.inference.StateNode;
import org.beast2.modelLanguage.builder.ObjectRegistry;
import org.beast2.modelLanguage.builder.handlers.DistributionAssignmentHandler;
import org.beast2.modelLanguage.model.Calibration;
import org.beast2.modelLanguage.model.DistributionAssignment;
import org.beast2.modelLanguage.model.FunctionCall;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Central registry for all BEAST2 objects created during model building.
 * This eliminates circular dependencies between the model builder and object factory.
 */
public class BeastObjectRegistry implements ObjectRegistry {
//    private static final Logger logger = Logger.getLogger(BeastObjectRegistry.class.getName());

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
            Log.trace("Registered StateNode: " + id);
        }

        if (object instanceof Distribution) {
            distributions.put(id, (Distribution) object);
            Log.trace("Registered Distribution: " + id);
        }

        Log.info("Registered object: " + id + " (" + object.getClass().getSimpleName() + ")");
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
        Log.info("Marked as random variable: " + varName);
    }

    /**
     * Mark a variable as observed
     */
    public void markAsObservedVariable(String varName, String dataRef) {
        observedVariables.add(varName);
        if (dataRef != null) {
            observedDataReferences.put(varName, dataRef);
        }
        Log.info("Marked as observed variable: " + varName +
                (dataRef != null ? " with data reference: " + dataRef : ""));
    }

    /**
     * Mark a variable as data-annotated
     */
    public void markAsDataAnnotated(String varName) {
        dataAnnotatedVariables.add(varName);
        Log.info("Marked as data-annotated: " + varName);
    }

    @Override
    public void addCalibration(String treeVar, Calibration calibration) {
        try {
            // Get the taxon set for this calibration
            TaxonSet taxonSet = (TaxonSet) objects.get(calibration.getTaxonset());
            if (taxonSet == null) {
                Log.warning("TaxonSet not found for calibration: " + calibration.getTaxonset());
                return;
            }

            // Get the tree for this calibration
            StateNode treeStateNode = stateNodes.get(treeVar);
            if (treeStateNode == null) {
                Log.warning("Tree StateNode not found: " + treeVar);
                return;
            }

            // Create a new MRCAPrior
            MRCAPrior mrcaPrior = new MRCAPrior();

            // Set the taxon set
            mrcaPrior.setInputValue("taxonset", taxonSet);

            // Set the tree
            mrcaPrior.setInputValue("tree", treeStateNode);

            // Set monophyletic constraint
            mrcaPrior.setInputValue("monophyletic", calibration.isMonophyletic());

            // Handle the distribution if present
            if (calibration.hasDistribution()) {
                // Create the distribution object from the FunctionCall
                String distId = taxonSet.getID() + ".parametricDistribution";
                Object distObject = createDistributionFromFunctionCall(calibration.getDistribution(), distId);
                if (distObject instanceof Distribution) {
                    // Register the distribution in the registry
                    register(distId, distObject);

                    // Set it on the MRCAPrior
                    mrcaPrior.setInputValue("distr", distObject);
                    Log.info("Created and registered distribution: " + distId + " for calibration: " + calibration.getTaxonset());
                } else {
                    Log.warning("Failed to create distribution for calibration: " + calibration.getTaxonset());
                }
            } else {
                Log.info("Creating monophyletic-only constraint (no age distribution) for: " + calibration.getTaxonset());
            }

            // Handle leaf constraints
            if (calibration.hasLeafConstraint()) {
                // For leaf calibrations, we might need to set the "isMonophyletic" to false
                // and handle it differently depending on BEAST2's MRCAPrior implementation
                Log.info("Processing leaf calibration for taxonset: " + calibration.getTaxonset());

                // Note: You may need to adjust this based on how BEAST2 handles tip dating
                // Some versions might use different parameters or require special handling
            }

            // Initialize the MRCAPrior
            mrcaPrior.initAndValidate();

            // Generate a unique ID for this MRCAPrior
            String mrcaPriorId = generateMRCAPriorId(treeVar, calibration.getTaxonset());

            // Set the ID on the MRCAPrior
            mrcaPrior.setID(mrcaPriorId);

            // Register the MRCAPrior in the registry
            register(mrcaPriorId, mrcaPrior);

            Log.info("Created MRCAPrior: " + mrcaPriorId +
                    " for taxonset: " + calibration.getTaxonset() +
                    " (monophyletic=" + calibration.isMonophyletic() +
                    ", hasDistribution=" + calibration.hasDistribution() +
                    ", leaf=" + calibration.isLeaf() + ")");

        } catch (Exception e) {
            Log.err("Error creating MRCAPrior for calibration: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to create MRCAPrior for calibration", e);
        }
    }

    /**
     * Generate a unique ID for the MRCAPrior based on tree and taxonset
     */
    private String generateMRCAPriorId(String treeVar, String taxonsetName) {
        return treeVar + ".prior." + taxonsetName;
    }

    /**
     * Create a BEAST2 Distribution object from a FunctionCall AST node
     */
    private Object createDistributionFromFunctionCall(FunctionCall funcCall, String distId) {
        try {
            // Create a DistributionAssignment for the parametric distribution
            DistributionAssignment distAssignment = new DistributionAssignment(
                    funcCall.getClassName(),
                    distId,
                    funcCall
            );

            // Use the existing DistributionAssignmentHandler
            DistributionAssignmentHandler handler = new DistributionAssignmentHandler();

            // Create the distribution objects (this will handle all the complex logic)
            handler.createObjects(distAssignment, this);

            // The distribution should now be registered in the registry
            Object distObject = objects.get(distId);

            if (distObject == null) {
                // If the main object wasn't created with that ID, try looking for a distribution with that name
                // Sometimes the handler creates additional objects with modified names
                String distName = distId + "Prior";
                distObject = objects.get(distName);

                if (distObject instanceof Distribution) {
                    Log.info("Found distribution with modified name: " + distName);
                    return distObject;
                }
            }

            return distObject;

        } catch (Exception e) {
            Log.err("Error creating distribution from function call: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
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
        Log.info("Registry cleared");
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
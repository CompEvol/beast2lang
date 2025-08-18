package org.beast2.modelLanguage.converter;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.inference.State;
import beast.base.inference.StateNode;

import java.util.*;
import java.util.logging.Logger;

/**
 * Normalizes BEAST object identifiers before conversion to ensure they are valid
 * Java identifiers while maintaining all internal references.
 */
public class BeastIdentifierNormaliser {

    private static final Logger logger = Logger.getLogger(BeastIdentifierNormaliser.class.getName());

    private final Map<String, String> idTransformationMap = new HashMap<>();
    private final Set<String> usedIdentifiers = new HashSet<>();
    private final Set<BEASTInterface> processedObjects = new HashSet<>();

    /**
     * Normalize all identifiers in the BEAST object graph before conversion
     */
    public void normaliseIdentifiers(BEASTInterface beastObject, State state) {
        logger.info("Starting identifier normalization...");

        // First pass: collect all objects and plan transformations
        collectObjects(beastObject);
        for (StateNode node : state.stateNodeInput.get()) {
            collectObjects(node);
        }

        // Second pass: apply the transformations
        applyTransformations(beastObject);
        for (StateNode node : state.stateNodeInput.get()) {
            applyTransformations(node);
        }

        logger.info("Identifier normalization complete. Transformed " + idTransformationMap.size() + " identifiers.");
    }

    /**
     * Collect all objects and plan identifier transformations
     */
    private void collectObjects(BEASTInterface obj) {
        if (obj == null || processedObjects.contains(obj)) {
            return;
        }
        processedObjects.add(obj);

        // Plan transformation for this object's ID if needed
        String currentId = obj.getID();
        if (currentId != null && !currentId.isEmpty()) {
            String normalizedId = normalizeIdentifier(currentId);
            if (!currentId.equals(normalizedId)) {
                idTransformationMap.put(currentId, normalizedId);
                logger.info("Planned transformation: '" + currentId + "' -> '" + normalizedId + "'");
            }
            usedIdentifiers.add(normalizedId);
        }

        // Recursively process all referenced objects
        for (Input<?> input : obj.getInputs().values()) {
            if (input.get() != null) {
                if (input.get() instanceof BEASTInterface) {
                    collectObjects((BEASTInterface) input.get());
                } else if (input.get() instanceof List) {
                    for (Object item : (List<?>) input.get()) {
                        if (item instanceof BEASTInterface) {
                            collectObjects((BEASTInterface) item);
                        }
                    }
                }
            }
        }
    }

    /**
     * Apply the planned transformations to all objects
     */
    private void applyTransformations(BEASTInterface obj) {
        if (obj == null) {
            return;
        }

        // Transform this object's ID if needed
        String currentId = obj.getID();
        if (currentId != null && idTransformationMap.containsKey(currentId)) {
            String newId = idTransformationMap.get(currentId);
            obj.setID(newId);
            logger.info("Applied transformation: '" + currentId + "' -> '" + newId + "'");
        }

        // Recursively apply to all referenced objects
        for (Input<?> input : obj.getInputs().values()) {
            if (input.get() != null) {
                if (input.get() instanceof BEASTInterface) {
                    applyTransformations((BEASTInterface) input.get());
                } else if (input.get() instanceof List) {
                    for (Object item : (List<?>) input.get()) {
                        if (item instanceof BEASTInterface) {
                            applyTransformations((BEASTInterface) item);
                        }
                    }
                }
            }
        }
    }

    /**
     * Normalize a single identifier to be a valid Java identifier
     */
    private String normalizeIdentifier(String originalId) {
        if (originalId == null || originalId.isEmpty()) {
            return originalId;
        }

        // Remove invalid characters, keeping only letters, digits, and underscores
        String cleaned = originalId.replaceAll("[^a-zA-Z0-9_]", "_");

        // Handle identifiers starting with numbers or other invalid characters
        if (!Character.isJavaIdentifierStart(cleaned.charAt(0))) {
            if (Character.isDigit(cleaned.charAt(0))) {
                // Prefix numeric IDs with "id" to maintain readability
                cleaned = "id" + cleaned;
            } else {
                // Prefix other invalid starts with underscore
                cleaned = "_" + cleaned;
            }
        }

        // Ensure uniqueness
        String uniqueId = cleaned;
        int counter = 1;
        while (usedIdentifiers.contains(uniqueId) && !uniqueId.equals(originalId)) {
            uniqueId = cleaned + "_" + counter++;
        }

        return uniqueId;
    }

    /**
     * Get the transformation map for debugging purposes
     */
    public Map<String, String> getTransformationMap() {
        return Collections.unmodifiableMap(idTransformationMap);
    }
}
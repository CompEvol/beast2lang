package org.beast2.modelLanguage.converter.pipeline;

import beast.base.core.BEASTInterface;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.evolution.alignment.Alignment;
import org.beast2.modelLanguage.converter.BeastConversionUtilities;
import org.beast2.modelLanguage.model.*;
import org.beast2.modelLanguage.builder.ModelObjectFactory;
import org.beast2.modelLanguage.beast.BeastObjectFactory;
import org.beast2.modelLanguage.converter.StatementCreator;

import java.util.*;

/**
 * Shared context for all phases of the conversion pipeline.
 * Contains all the state that needs to be passed between phases.
 */
public class ConversionContext {

    // Input objects
    private final Distribution posterior;
    private final State state;
    private final BEASTInterface mcmc;

    // Core data structures
    private final Map<BEASTInterface, String> objectToIdMap = new HashMap<>();
    private final Map<BEASTInterface, Statement> objectToStatementMap = new HashMap<>();
    private final Set<BEASTInterface> processedObjects = new HashSet<>();
    private final Map<String, Alignment> processedAlignments = new HashMap<>();
    private final Set<BEASTInterface> usedDistributions = new HashSet<>();
    private final Set<BEASTInterface> inlinedDistributions = new HashSet<>();
    private final Set<BEASTInterface> randomCompositionParameters = new HashSet<>();

    // Helper components
    private final ModelObjectFactory objectFactory;
    private final BeastConversionUtilities conversionUtilities;
    private StatementCreator statementCreator;

    // Output model
    private final Beast2Model model = new Beast2Model();

    // Additional context data
    private final Map<String, Object> metadata = new HashMap<>();

    public ConversionContext(Distribution posterior, State state, BEASTInterface mcmc) {
        this.posterior = posterior;
        this.state = state;
        this.mcmc = mcmc;

        // Initialize helper components
        this.objectFactory = new BeastObjectFactory();
        this.conversionUtilities = new BeastConversionUtilities(objectToIdMap);
        this.statementCreator = new StatementCreator(objectToIdMap, objectFactory,
                conversionUtilities, usedDistributions, state);
    }

    // Getters for input objects
    public Distribution getPosterior() { return posterior; }
    public State getState() { return state; }
    public BEASTInterface getMcmc() { return mcmc; }

    // Getters for data structures
    public Map<BEASTInterface, String> getObjectToIdMap() { return objectToIdMap; }
    public Map<BEASTInterface, Statement> getObjectToStatementMap() { return objectToStatementMap; }
    public Set<BEASTInterface> getProcessedObjects() { return processedObjects; }
    public Map<String, Alignment> getProcessedAlignments() { return processedAlignments; }
    public Set<BEASTInterface> getUsedDistributions() { return usedDistributions; }
    public Set<BEASTInterface> getInlinedDistributions() { return inlinedDistributions; }
    public Set<BEASTInterface> getRandomCompositionParameters() { return randomCompositionParameters; }

    // Getters for helper components
    public ModelObjectFactory getObjectFactory() { return objectFactory; }
    public BeastConversionUtilities getConversionUtilities() { return conversionUtilities; }
    public StatementCreator getStatementCreator() { return statementCreator; }

    // Model access
    public Beast2Model getModel() { return model; }

    // Metadata access for phases to store additional information
    public void setMetadata(String key, Object value) { metadata.put(key, value); }
    public Object getMetadata(String key) { return metadata.get(key); }
    public <T> T getMetadata(String key, Class<T> type) {
        return type.cast(metadata.get(key));
    }

    /**
     * Update the statement creator (needed after state is set)
     */
    public void updateStatementCreator() {
        this.statementCreator = new StatementCreator(objectToIdMap, objectFactory,
                conversionUtilities, usedDistributions, state);
    }

    /**
     * Check if an object has been processed
     */
    public boolean isProcessed(BEASTInterface obj) {
        return objectToStatementMap.containsKey(obj);
    }

    /**
     * Mark an object as processed with its statement
     */
    public void markProcessed(BEASTInterface obj, Statement statement) {
        objectToStatementMap.put(obj, statement);
        if (statement != null) {
            model.addStatement(statement);
        }
    }

    /**
     * Generate a unique identifier for an object
     */
    public String generateIdentifier(BEASTInterface obj) {
        String className = obj.getClass().getSimpleName();
        String baseName = className.substring(0, 1).toLowerCase() + className.substring(1);

        if (obj.getID() != null && !obj.getID().isEmpty()) {
            baseName = obj.getID();
            // Basic cleanup for any remaining issues
            baseName = baseName.replaceAll("[^a-zA-Z0-9_]", "_");
        }

        String uniqueName = baseName;
        int counter = 1;
        while (objectToIdMap.containsValue(uniqueName)) {
            uniqueName = baseName + "_" + counter++;
        }

        return uniqueName;
    }
}
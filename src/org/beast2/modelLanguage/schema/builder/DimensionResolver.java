package org.beast2.modelLanguage.schema.builder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Resolves dimension information for arguments that have contextual dimensions
 */
public class DimensionResolver {
    private final Map<String, DimensionInfo> knownDimensions;
    
    public DimensionResolver() {
        this.knownDimensions = initializeKnownDimensions();
    }
    
    /**
     * Resolve dimension information for a given class and argument
     */
    public Optional<JSONObject> resolveDimension(String className, String argName) {
        String fullKey = className + "." + argName;
        
        if (knownDimensions.containsKey(fullKey)) {
            DimensionInfo dimInfo = knownDimensions.get(fullKey);
            return Optional.of(createDimensionObject(dimInfo));
        }
        
        return Optional.empty();
    }
    
    /**
     * Check if a type indicates an array or collection
     */
    public boolean isCollectionType(String typeStr) {
        return typeStr.contains("[]") || typeStr.startsWith("List<");
    }
    
    private JSONObject createDimensionObject(DimensionInfo dimInfo) {
        JSONObject dimension = new JSONObject();
        dimension.put("type", dimInfo.type);
        
        JSONArray resolutions = new JSONArray();
        for (DimensionResolution res : dimInfo.resolutions) {
            JSONObject resolution = new JSONObject();
            resolution.put("context", res.context);
            resolution.put("path", res.path);
            resolution.put("when", res.when);
            resolutions.put(resolution);
        }
        dimension.put("resolution", resolutions);
        
        return dimension;
    }
    
    private Map<String, DimensionInfo> initializeKnownDimensions() {
        Map<String, DimensionInfo> dimensions = new HashMap<>();
        
        // Frequencies dimension depends on context
        DimensionInfo freqDim = new DimensionInfo("contextual");
        freqDim.addResolution("parent", "stateCount", "parent implements SubstitutionModel");
        freqDim.addResolution("sibling", "siteModel.substModel.stateCount", "parent is TreeLikelihood");
        freqDim.addResolution("alignment", "dataType.stateCount", "alignment is available");
        dimensions.put("Frequencies.frequencies", freqDim);
        
        // Add more known dimensions as needed
        
        return dimensions;
    }
    
    /**
     * Information about a dimension dependency
     */
    private static class DimensionInfo {
        final String type;
        final List<DimensionResolution> resolutions = new ArrayList<>();
        
        DimensionInfo(String type) {
            this.type = type;
        }
        
        void addResolution(String context, String path, String when) {
            resolutions.add(new DimensionResolution(context, path, when));
        }
    }
    
    /**
     * A single dimension resolution strategy
     */
    private static class DimensionResolution {
        final String context;
        final String path;
        final String when;
        
        DimensionResolution(String context, String path, String when) {
            this.context = context;
            this.path = path;
            this.when = when;
        }
    }
}
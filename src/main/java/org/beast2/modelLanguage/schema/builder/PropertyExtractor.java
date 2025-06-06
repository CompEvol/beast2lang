package org.beast2.modelLanguage.schema.builder;

import org.beast2.modelLanguage.schema.core.TypeResolver;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Extracts properties from BEAST2 classes via their getter methods
 */
public class PropertyExtractor {
    private final TypeResolver typeResolver;
    
    public PropertyExtractor(TypeResolver typeResolver) {
        this.typeResolver = typeResolver;
    }
    
    /**
     * Extract properties from getter methods
     */
    public JSONArray extractProperties(Class<?> clazz) {
        JSONArray properties = new JSONArray();
        Set<String> processedProperties = new HashSet<>();
        
        // Get all public methods
        for (Method method : clazz.getMethods()) {
            String methodName = method.getName();
            
            // Look for getters
            if (isValidGetter(method)) {
                String propertyName = extractPropertyName(methodName);
                
                if (!processedProperties.contains(propertyName)) {
                    JSONObject property = createProperty(method, propertyName, clazz);
                    properties.put(property);
                    processedProperties.add(propertyName);
                }
            }
        }
        
        return properties;
    }
    
    /**
     * Check if a method is a valid getter
     */
    private boolean isValidGetter(Method method) {
        String methodName = method.getName();
        
        // Must start with get/is
        boolean isGetter = (methodName.startsWith("get") && methodName.length() > 3) ||
                          (methodName.startsWith("is") && methodName.length() > 2);
        
        if (!isGetter) {
            return false;
        }
        
        // Skip certain methods
        if (methodName.equals("getClass") ||
            methodName.equals("getID") ||
            methodName.equals("getInput") ||
            method.getParameterCount() > 0) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Create a property object from a getter method
     */
    private JSONObject createProperty(Method method, String propertyName, Class<?> clazz) {
        JSONObject property = new JSONObject();
        property.put("name", propertyName);
        property.put("type", typeResolver.getSimpleClassName(method.getReturnType()));
        property.put("access", "read-only");
        
        // Special handling for known properties
        handleSpecialProperties(property, propertyName, clazz);
        
        return property;
    }
    
    /**
     * Handle special properties that have known values or sources
     */
    private void handleSpecialProperties(JSONObject property, String propertyName, Class<?> clazz) {
        if (propertyName.equals("stateCount") && isSubstitutionModel(clazz)) {
            handleStateCountProperty(property, clazz);
        }
        // Add more special cases as needed
    }
    
    /**
     * Handle stateCount property for SubstitutionModel
     */
    private void handleStateCountProperty(JSONObject property, Class<?> clazz) {
        try {
            // Check if it's a fixed nucleotide model
            Class<?> nucleotideBaseClass = Class.forName("beast.base.evolution.substitutionmodel.SubstitutionModel$NucleotideBase");
            if (nucleotideBaseClass.isAssignableFrom(clazz)) {
                property.put("value", 4);
            }
            // Check for LewisMK or similar parameterized models
            else if (clazz.getSimpleName().equals("LewisMK")) {
                property.put("source", "argument:stateNumber");
            }
        } catch (ClassNotFoundException e) {
            // Class not found, continue without special handling
        }
    }
    
    /**
     * Check if class is a SubstitutionModel
     */
    private boolean isSubstitutionModel(Class<?> clazz) {
        try {
            Class<?> substModelClass = Class.forName("beast.base.evolution.substitutionmodel.SubstitutionModel");
            return substModelClass.isAssignableFrom(clazz);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Extract property name from getter method
     */
    private String extractPropertyName(String methodName) {
        if (methodName.startsWith("get")) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        } else if (methodName.startsWith("is")) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        return methodName;
    }
}
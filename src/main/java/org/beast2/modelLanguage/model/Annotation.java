package org.beast2.modelLanguage.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an annotation on a statement in a Beast2 model.
 */
public class Annotation {
    private final String name;
    private final Map<String, Object> parameters;
    
    /**
     * Constructor for an annotation with parameters
     * 
     * @param name the annotation name (without @)
     * @param parameters map of parameter name to parameter value
     */
    public Annotation(String name, Map<String, Object> parameters) {
        this.name = name;
        this.parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
    }
    
    /**
     * Constructor for an annotation without parameters
     * 
     * @param name the annotation name (without @)
     */
    public Annotation(String name) {
        this(name, null);
    }
    
    /**
     * Get the annotation name
     * 
     * @return the annotation name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Get the annotation parameters
     * 
     * @return an unmodifiable map of parameter name to parameter value
     */
    public Map<String, Object> getParameters() {
        return Collections.unmodifiableMap(parameters);
    }
    
    /**
     * Check if the annotation has a specific parameter
     * 
     * @param paramName the parameter name to check
     * @return true if the parameter exists
     */
    public boolean hasParameter(String paramName) {
        return parameters.containsKey(paramName);
    }
    
    /**
     * Get a parameter value
     * 
     * @param paramName the parameter name
     * @return the parameter value, or null if not found
     */
    public Object getParameter(String paramName) {
        return parameters.get(paramName);
    }
    
    /**
     * Get a parameter value as a string
     * 
     * @param paramName the parameter name
     * @return the parameter value as a string, or null if not found
     */
    public String getParameterAsString(String paramName) {
        Object value = parameters.get(paramName);
        return value != null ? value.toString() : null;
    }
    
    /**
     * Create a string representation of this annotation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("@").append(name);
        
        if (!parameters.isEmpty()) {
            sb.append(" {");
            boolean first = true;
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                
                sb.append(entry.getKey()).append("=");
                Object value = entry.getValue();
                if (value instanceof String) {
                    sb.append("\"").append(value).append("\"");
                } else {
                    sb.append(value);
                }
            }
            sb.append("}");
        }
        
        return sb.toString();
    }
}
package org.beast2.modelLanguage.builder;

import org.beast2.modelLanguage.model.Annotation;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Validator for annotations in the Beast2 model language.
 * Keeps track of annotated variables and validates annotation parameters.
 */
public class AnnotationValidator {

    private static final Logger logger = Logger.getLogger(AnnotationValidator.class.getName());

    // Map of variable names to their annotation types
    private final Map<String, String> annotatedVariables = new HashMap<>();

    /**
     * Register a variable with the given annotation type
     *
     * @param variableName the name of the variable
     * @param annotationType the type of annotation ("data" or "observed")
     */
    public void registerAnnotatedVariable(String variableName, String annotationType) {
        annotatedVariables.put(variableName, annotationType);
        logger.fine("Registered variable " + variableName + " with annotation @" + annotationType);
    }

    /**
     * Check if a variable is annotated with the given type
     *
     * @param variableName the name of the variable to check
     * @param annotationType the annotation type to check for
     * @return true if the variable has the specified annotation type
     */
    public boolean hasAnnotation(String variableName, String annotationType) {
        return annotatedVariables.containsKey(variableName) &&
                annotatedVariables.get(variableName).equals(annotationType);
    }

    /**
     * Validate an observed annotation's data parameter
     *
     * @param annotation the annotation to validate
     * @throws IllegalArgumentException if validation fails
     */
    public void validateObservedAnnotation(Annotation annotation) {
        if (!annotation.isObservedAnnotation()) {
            return;
        }

        // Ensure the data parameter exists
        if (!annotation.hasParameter("data")) {
            throw new IllegalArgumentException("@observed annotation requires a 'data' parameter");
        }

        // Get the data parameter value
        Object dataValue = annotation.getParameter("data");

        // Ensure the data parameter is a string (identifier)
        if (!(dataValue instanceof String)) {
            throw new IllegalArgumentException("data parameter in @observed annotation must be an identifier");
        }

        String dataRef = (String) dataValue;

        // Verify the referenced variable exists and is annotated with @data
        if (!annotatedVariables.containsKey(dataRef)) {
            throw new IllegalArgumentException(
                    "data parameter '" + dataRef + "' references a variable that doesn't exist"
            );
        }

        if (!"data".equals(annotatedVariables.get(dataRef))) {
            throw new IllegalArgumentException(
                    "data parameter '" + dataRef + "' must reference a variable annotated with @data"
            );
        }

        logger.fine("Validated @observed annotation with data reference to " + dataRef);
    }

    /**
     * Get all variables with a specific annotation type
     *
     * @param annotationType the annotation type to filter by
     * @return map of variable names to annotation types matching the filter
     */
    public Map<String, String> getVariablesWithAnnotation(String annotationType) {
        Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, String> entry : annotatedVariables.entrySet()) {
            if (entry.getValue().equals(annotationType)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return result;
    }

    /**
     * Get all registered variables with their annotation types
     *
     * @return map of variable names to their annotation types
     */
    public Map<String, String> getAllAnnotatedVariables() {
        return new HashMap<>(annotatedVariables);
    }
}
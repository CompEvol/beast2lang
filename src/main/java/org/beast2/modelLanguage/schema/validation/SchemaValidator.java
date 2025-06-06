package org.beast2.modelLanguage.schema.validation;

import org.beast2.modelLanguage.schema.core.KnownTypes;
import org.beast2.modelLanguage.schema.core.TypeResolver;
import org.beast2.modelLanguage.schema.scanner.ComponentFilter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.logging.Logger;

/**
 * Validates schema closure - ensures all referenced types are defined
 */
public class SchemaValidator {
    private static final Logger logger = Logger.getLogger(SchemaValidator.class.getName());

    private final Set<String> primitiveTypes;
    private final ComponentFilter componentFilter;
    private final TypeResolver typeResolver;

    public SchemaValidator() {
        this.primitiveTypes = KnownTypes.PRIMITIVE_TYPES;
        this.componentFilter = new ComponentFilter();
        this.typeResolver = new TypeResolver();
    }

    /**
     * Validate closure of the schema
     */
    public ValidationResult validateClosure(JSONObject schema) {
        ValidationResult result = new ValidationResult();

        try {
            JSONObject modelLibrary = schema.getJSONObject("modelLibrary");
            JSONArray components = modelLibrary.getJSONArray("components");

            // Set the total component count in the result
            result.setTotalComponents(components.length());

            // Build set of all component names
            Set<String> componentNames = buildComponentNameSet(components);

            // Check all components
            for (int i = 0; i < components.length(); i++) {
                JSONObject component = components.getJSONObject(i);
                checkComponent(component, componentNames, result);
            }

        } catch (Exception e) {
            logger.severe("Error validating schema: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Build set of all component names (both simple and fully qualified)
     */
    private Set<String> buildComponentNameSet(JSONArray components) {
        Set<String> componentNames = new HashSet<>();

        for (int i = 0; i < components.length(); i++) {
            JSONObject component = components.getJSONObject(i);
            String name = component.getString("name");
            String fqn = component.getString("fullyQualifiedName");

            componentNames.add(name);
            componentNames.add(fqn);

            // Also add just the class name without package for interfaces
            if (fqn.contains(".")) {
                String simpleName = fqn.substring(fqn.lastIndexOf('.') + 1);
                componentNames.add(simpleName);
            }
        }

        return componentNames;
    }

    /**
     * Check a single component for type closure
     */
    private void checkComponent(JSONObject component, Set<String> componentNames,
                                ValidationResult result) {
        String componentName = component.getString("name");

        // Check primary argument if it exists
        if (component.has("primaryArgument")) {
            JSONObject primaryArg = component.getJSONObject("primaryArgument");
            checkArgumentType(primaryArg, componentName, "primaryArgument",
                    componentNames, result);
        }

        // Check all arguments
        if (component.has("arguments")) {
            JSONArray arguments = component.getJSONArray("arguments");
            for (int j = 0; j < arguments.length(); j++) {
                JSONObject arg = arguments.getJSONObject(j);
                checkArgumentType(arg, componentName, arg.getString("name"),
                        componentNames, result);
            }
        }
    }

    /**
     * Check a single argument type for closure
     */
    private void checkArgumentType(JSONObject arg, String componentName, String argName,
                                   Set<String> componentNames, ValidationResult result) {
        String type = arg.getString("type");
        String baseType = typeResolver.extractBaseType(type);

        // Track usage
        String usage = componentName + "." + argName;
        result.addTypeUsage(baseType, usage);

        // Check if type is defined
        if (!primitiveTypes.contains(baseType) && !componentNames.contains(baseType)) {
            // Check if it's an inference-related type
            if (componentFilter.isInferenceType(baseType)) {
                result.addInferenceType(baseType);
            }
            // Check if it's a GUI-related type
            else if (componentFilter.isGUIType(baseType)) {
                result.addGUIType(baseType);
            }
            else {
                result.addMissingType(baseType);
            }
        }
    }
}
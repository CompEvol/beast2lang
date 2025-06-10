package org.beast2.modelLanguage.schema.validation;

import org.beast2.modelLanguage.schema.core.TypeResolver;
import org.beast2.modelLanguage.schema.scanner.ComponentFilter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.logging.Logger;

/**
 * Validates closure for the new schema format with types and generators
 */
public class ClosureValidator {
    private static final Logger logger = Logger.getLogger(ClosureValidator.class.getName());

    private final TypeResolver typeResolver;
    private final ComponentFilter componentFilter;
    private final Set<String> primitiveTypes;

    public ClosureValidator(TypeResolver typeResolver) {
        this.typeResolver = typeResolver;
        this.componentFilter = new ComponentFilter();
        this.primitiveTypes = Set.of(
                "Integer", "Double", "String", "Boolean",
                "int", "double", "boolean", "Long", "Float",
                "Object", "T", "Map", "File"
        );
    }

    /**
     * Validate closure of the new schema format
     */
    public ValidationResult validateClosure(JSONObject schema) {
        ValidationResult result = new ValidationResult();

        try {
            JSONObject modelLibrary = schema.getJSONObject("modelLibrary");
            JSONArray types = modelLibrary.getJSONArray("types");
            JSONArray generators = modelLibrary.getJSONArray("generators");

            // Set total components (types + generators)
            result.setTotalComponents(types.length() + generators.length());

            // Build set of all available type names
            Set<String> availableTypes = buildAvailableTypeSet(types, generators);

            // Check all generators for type closure
            for (int i = 0; i < generators.length(); i++) {
                JSONObject generator = generators.getJSONObject(i);
                checkGenerator(generator, availableTypes, result);
            }

        } catch (Exception e) {
            logger.severe("Error validating schema: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Build set of all available types
     */
    private Set<String> buildAvailableTypeSet(JSONArray types, JSONArray generators) {
        Set<String> availableTypes = new HashSet<>();

        // Add all types
        for (int i = 0; i < types.length(); i++) {
            JSONObject type = types.getJSONObject(i);
            String name = type.getString("name");
            availableTypes.add(name);
        }

        // Add all generator names (they're also types in BEAST2)
        for (int i = 0; i < generators.length(); i++) {
            JSONObject generator = generators.getJSONObject(i);
            String name = generator.getString("name");
            availableTypes.add(name);
        }

        // Add primitive types
        availableTypes.addAll(primitiveTypes);

        return availableTypes;
    }

    /**
     * Check a generator for type closure
     */
    private void checkGenerator(JSONObject generator, Set<String> availableTypes,
                                ValidationResult result) {
        String generatorName = generator.getString("name");

        // Check primary argument if it exists
        if (generator.has("primaryArgument")) {
            JSONObject primaryArg = generator.getJSONObject("primaryArgument");
            checkArgumentType(primaryArg, generatorName, "primaryArgument",
                    availableTypes, result);
        }

        // Check all arguments
        if (generator.has("arguments")) {
            JSONArray arguments = generator.getJSONArray("arguments");
            for (int j = 0; j < arguments.length(); j++) {
                JSONObject arg = arguments.getJSONObject(j);
                checkArgumentType(arg, generatorName, arg.getString("name"),
                        availableTypes, result);
            }
        }
    }

    /**
     * Check a single argument type for closure
     */
    private void checkArgumentType(JSONObject arg, String generatorName, String argName,
                                   Set<String> availableTypes, ValidationResult result) {
        String type = arg.getString("type");
        String baseType = typeResolver.extractBaseType(type);

        // Track usage
        String usage = generatorName + "." + argName;
        result.addTypeUsage(baseType, usage);

        // Check if type is defined
        if (!availableTypes.contains(baseType)) {
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
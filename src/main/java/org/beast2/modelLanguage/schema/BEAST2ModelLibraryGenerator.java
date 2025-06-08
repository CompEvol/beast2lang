package org.beast2.modelLanguage.schema;

import beast.pkgmgmt.BEASTVersion;
import beast.pkgmgmt.Package;
import org.beast2.modelLanguage.beast.BeastObjectFactory;
import org.beast2.modelLanguage.schema.builder.*;
import org.beast2.modelLanguage.schema.core.ComponentInfo;
import org.beast2.modelLanguage.schema.core.KnownTypes;
import org.beast2.modelLanguage.schema.core.TypeResolver;
import org.beast2.modelLanguage.schema.scanner.*;


import org.beast2.modelLanguage.schema.validation.SchemaValidator;
import org.beast2.modelLanguage.schema.validation.ValidationResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Refactored BEAST2 Model Library Generator with modular architecture
 */
public class BEAST2ModelLibraryGenerator {
    private static final Logger logger = Logger.getLogger(BEAST2ModelLibraryGenerator.class.getName());

    private final BeastObjectFactory factory;
    private final ComponentScanner scanner;
    private final ComponentDefinitionBuilder definitionBuilder;
    private final SchemaValidator validator;

    public final ComponentFilter filter;


    public BEAST2ModelLibraryGenerator() {
        // Initialize factory
        this.factory = new BeastObjectFactory();

        // Initialize type system components
        TypeResolver typeResolver = new TypeResolver();
        this.filter = new ComponentFilter();

        DimensionResolver dimensionResolver = new DimensionResolver();
        ConstraintResolver constraintResolver = new ConstraintResolver();

        // Initialize builders
        ArgumentBuilder argumentBuilder = new ArgumentBuilder(
                typeResolver, dimensionResolver, constraintResolver
        );
        PropertyExtractor propertyExtractor = new PropertyExtractor(typeResolver);

        // Initialize main components
        this.scanner = new ComponentScanner(factory, filter);
        this.definitionBuilder = new ComponentDefinitionBuilder(
                typeResolver, argumentBuilder, propertyExtractor, factory
        );
        this.validator = new SchemaValidator();
    }

    /**
     * Generate the engine-agnostic model library schema for BEAST2
     */
    public String generateModelLibrary() throws Exception {
        logger.info("Starting BEAST2 model library generation...");

        // Scan for all components
        List<ComponentInfo> components = scanAllComponents();
        logger.info("Found " + components.size() + " total components");

        // Build component definitions
        JSONArray componentDefinitions = buildDefinitions(components);
        logger.info("Built " + componentDefinitions.length() + " component definitions");

        // Create the schema
        JSONObject schema = createSchema(componentDefinitions);

        // Second pass: scan for inner types used in arguments
        addReferencedInnerTypes(schema);

        return schema.toString(2);
    }

    /**
     * Validate the generated schema
     */
    public ValidationResult validateSchema(String jsonSchema) {
        return validator.validateClosure(new JSONObject(jsonSchema));
    }

    /**
     * Scan all sources for components
     */
    private List<ComponentInfo> scanAllComponents() {
        List<ComponentInfo> allComponents = new ArrayList<>();
        Set<String> processedClasses = new HashSet<>();

        // 1. Scan important base types
        logger.info("Scanning important base types...");
        List<ComponentInfo> baseTypes = scanner.scanImportantTypes(
                KnownTypes.IMPORTANT_BASE_TYPES, "BEAST.base"
        );
        allComponents.addAll(filterNewComponents(baseTypes, processedClasses));

        // 2. Scan important non-BEAST interfaces
        logger.info("Scanning important non-BEAST interfaces...");
        List<ComponentInfo> nonBeastInterfaces = scanner.scanImportantTypes(
                KnownTypes.IMPORTANT_NON_BEAST_INTERFACES, "unknown"
        );
        allComponents.addAll(filterNewComponents(nonBeastInterfaces, processedClasses));

        // 3. Scan important inner classes
        logger.info("Scanning important inner classes...");
        List<ComponentInfo> innerClasses = scanner.scanImportantTypes(
                KnownTypes.IMPORTANT_INNER_CLASSES, "BEAST.base"
        );
        allComponents.addAll(filterNewComponents(innerClasses, processedClasses));

        // 4. Scan protected enums
        logger.info("Scanning protected enums...");
        List<ComponentInfo> enums = scanner.scanEnums(KnownTypes.ENUMS);
        allComponents.addAll(filterNewComponents(enums, processedClasses));

        // 5. Scan all plugins/packages
        logger.info("Scanning plugin packages...");
        Map<String, Package> packages = getPackages();
        List<ComponentInfo> packageComponents = scanner.scanPackages(packages);
        allComponents.addAll(filterNewComponents(packageComponents, processedClasses));

        return allComponents;
    }

    /**
     * Filter out already processed components
     */
    private List<ComponentInfo> filterNewComponents(List<ComponentInfo> components,
                                                    Set<String> processedClasses) {
        return components.stream()
                .filter(c -> {
                    String className = c.getClassName();
                    if (!processedClasses.contains(className)) {
                        processedClasses.add(className);
                        // Also add simple name for tracking
                        processedClasses.add(c.getSimpleName());
                        return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get all packages from the factory
     */
    private Map<String, Package> getPackages() {
        Map<String, Package> packages = new HashMap<>();
        Map<String, Object> allPlugins = factory.getAllPlugins();

        for (Map.Entry<String, Object> entry : allPlugins.entrySet()) {
            if (entry.getValue() instanceof Package) {
                packages.put(entry.getKey(), (Package) entry.getValue());
            }
        }

        logger.info("Found " + packages.size() + " packages");
        return packages;
    }

    /**
     * Build component definitions from ComponentInfo objects
     */
    private JSONArray buildDefinitions(List<ComponentInfo> components) {
        JSONArray definitions = new JSONArray();

        for (ComponentInfo component : components) {
            try {
                JSONObject definition = definitionBuilder.buildDefinition(component);
                definitions.put(definition);
            } catch (Exception e) {
                logger.warning("Failed to build definition for " +
                        component.getSimpleName() + ": " + e.getMessage());
            }
        }

        return definitions;
    }

    // Add this method to BEAST2ModelLibraryGenerator class

    /**
     * Create type definitions for common BEAST2 types
     */
    private JSONObject createTypeDefinitions() {
        JSONObject typeDefinitions = new JSONObject();

        // Function can accept Real and Real[] (since RealParameter implements Function)
        JSONObject functionDef = new JSONObject();
        functionDef.put("acceptsTypes", new JSONArray()
                .put("Double")
                .put("Double[]")
                .put("Float")
                .put("Float[]")
                .put("Integer")
                .put("Integer[]")
        );
        typeDefinitions.put("Function", functionDef);

        // RealParameter can accept primitive values
        JSONObject realParamDef = new JSONObject();
        realParamDef.put("primitiveAssignable", true);
        realParamDef.put("acceptedPrimitives", new JSONArray()
                .put("Double")
                .put("Double[]")
                .put("Integer")
                .put("Integer[]")
        );
        typeDefinitions.put("RealParameter", realParamDef);

        // IntegerParameter can accept primitive values
        JSONObject intParamDef = new JSONObject();
        intParamDef.put("primitiveAssignable", true);
        intParamDef.put("acceptedPrimitives", new JSONArray()
                .put("Integer")
                .put("Long")
        );
        typeDefinitions.put("IntegerParameter", intParamDef);

        // BooleanParameter can accept primitive values
        JSONObject boolParamDef = new JSONObject();
        boolParamDef.put("primitiveAssignable", true);
        boolParamDef.put("acceptedPrimitives", new JSONArray()
                .put("Boolean")
        );
        typeDefinitions.put("BooleanParameter", boolParamDef);

        return typeDefinitions;
    }

    // Update the createSchema method to include type definitions:
    private JSONObject createSchema(JSONArray componentDefinitions) {
        JSONObject schema = new JSONObject();
        JSONObject modelLibrary = new JSONObject();

        // Basic metadata
        modelLibrary.put("name", "BEAST2 Core Library");
        modelLibrary.put("version", "1.0.0");
        modelLibrary.put("engine", "BEAST2");
        modelLibrary.put("engineVersion", BEASTVersion.INSTANCE.getVersion());
        modelLibrary.put("description", "Core model components for BEAST2");

        // Add components
        modelLibrary.put("components", componentDefinitions);

        // Add type definitions
        modelLibrary.put("typeDefinitions", createTypeDefinitions());

        schema.put("modelLibrary", modelLibrary);
        return schema;
    }

    /**
     * Second pass: scan for inner types referenced in arguments
     */
    private void addReferencedInnerTypes(JSONObject schema) {
        JSONObject modelLibrary = schema.getJSONObject("modelLibrary");
        JSONArray components = modelLibrary.getJSONArray("components");

        // Collect all inner types referenced
        Set<String> innerTypesToAdd = collectReferencedInnerTypes(components);

        // Get already processed classes
        Set<String> processedClasses = new HashSet<>();
        for (int i = 0; i < components.length(); i++) {
            JSONObject component = components.getJSONObject(i);
            processedClasses.add(component.getString("fullyQualifiedName"));
        }

        // Remove already processed
        innerTypesToAdd.removeAll(processedClasses);

        if (!innerTypesToAdd.isEmpty()) {
            logger.info("Found " + innerTypesToAdd.size() + " referenced inner types to add");

            // Scan for these inner types
            List<ComponentInfo> innerComponents = scanner.scanReferencedInnerTypes(
                    innerTypesToAdd, KnownTypes.PACKAGE_SEARCH_PREFIXES.toArray(new String[0])
            );

            // Build definitions and add to schema
            for (ComponentInfo innerComponent : innerComponents) {
                try {
                    JSONObject definition = definitionBuilder.buildDefinition(innerComponent);
                    components.put(definition);
                } catch (Exception e) {
                    logger.warning("Failed to add inner type " +
                            innerComponent.getSimpleName() + ": " + e.getMessage());
                }
            }

            logger.info("Added " + innerComponents.size() + " inner types");
        }
    }

    /**
     * Collect all inner types referenced in arguments
     */
    private Set<String> collectReferencedInnerTypes(JSONArray components) {
        Set<String> innerTypes = new HashSet<>();
        TypeResolver typeResolver = new TypeResolver();

        for (int i = 0; i < components.length(); i++) {
            JSONObject component = components.getJSONObject(i);

            // Check primary argument
            if (component.has("primaryArgument")) {
                JSONObject primaryArg = component.getJSONObject("primaryArgument");
                collectInnerType(primaryArg.getString("type"), innerTypes, typeResolver);
            }

            // Check all arguments
            if (component.has("arguments")) {
                JSONArray arguments = component.getJSONArray("arguments");
                for (int j = 0; j < arguments.length(); j++) {
                    JSONObject arg = arguments.getJSONObject(j);
                    collectInnerType(arg.getString("type"), innerTypes, typeResolver);
                }
            }
        }

        return innerTypes;
    }

    /**
     * Collect inner type from a type string
     */
    private void collectInnerType(String type, Set<String> innerTypes, TypeResolver typeResolver) {
        String baseType = typeResolver.extractBaseType(type);

        // Check if it looks like an inner type
        if (baseType.contains(".") &&
                !baseType.contains("java.") &&
                Character.isUpperCase(baseType.charAt(0))) {
            innerTypes.add(baseType);
        }
    }

    /**
     * Main method to generate schema and test closure
     */
    public static void main(String[] args) {
        try {
            BEAST2ModelLibraryGenerator generator =
                    new BEAST2ModelLibraryGenerator();

            // Generate the schema
            String jsonOutput = generator.generateModelLibrary();

            // Write to file
            Files.write(
                    Paths.get("beast2-model-library.json"),
                    jsonOutput.getBytes()
            );

            System.out.println("Schema written to beast2-model-library.json");

            ValidationResult validation = generator.validateSchema(jsonOutput);
            System.out.println(validation.generateReport());

            System.out.println(generator.filter.getFilterReport().generateReport());

        } catch (Exception e) {
            logger.severe("Error generating model library: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
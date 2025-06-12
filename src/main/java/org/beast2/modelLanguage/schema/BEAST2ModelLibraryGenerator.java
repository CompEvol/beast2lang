package org.beast2.modelLanguage.schema;

import beast.base.core.BEASTInterface;
import beast.base.core.Function;
import beast.base.core.Input;
import beast.base.inference.Distribution;
import beast.base.inference.distribution.ParametricDistribution;
import beast.base.evolution.tree.TreeDistribution;
import beast.base.inference.distribution.Poisson;
import beast.base.util.Binomial;
import beast.pkgmgmt.BEASTVersion;
import beast.pkgmgmt.Package;
import org.beast2.modelLanguage.beast.BeastObjectFactory;
import org.beast2.modelLanguage.beast.BEASTUtils;
import org.beast2.modelLanguage.schema.builder.*;
import org.beast2.modelLanguage.schema.core.ComponentInfo;
import org.beast2.modelLanguage.schema.core.KnownTypes;
import org.beast2.modelLanguage.schema.core.TypeResolver;
import org.beast2.modelLanguage.schema.scanner.ComponentFilter;
import org.beast2.modelLanguage.schema.scanner.ComponentScanner;
import org.beast2.modelLanguage.schema.validation.ClosureValidator;
import org.beast2.modelLanguage.schema.validation.ValidationResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/**
 * BEAST2 Model Library Generator - generates types and generators for the new schema format
 */
public class BEAST2ModelLibraryGenerator {
    private static final Logger logger = Logger.getLogger(BEAST2ModelLibraryGenerator.class.getName());

    private final BeastObjectFactory factory;
    private final TypeResolver typeResolver;
    private final ArgumentBuilder argumentBuilder;
    private final DimensionResolver dimensionResolver;
    private final ConstraintResolver constraintResolver;
    private final ComponentFilter filter;
    private final ComponentScanner scanner;
    private final ClosureValidator validator;

    private Set<String> knownTypeNames = new HashSet<>();

    public BEAST2ModelLibraryGenerator() {
        this.factory = new BeastObjectFactory();
        this.typeResolver = new TypeResolver();
        this.dimensionResolver = new DimensionResolver();
        this.constraintResolver = new ConstraintResolver();
        this.argumentBuilder = new ArgumentBuilder(typeResolver, dimensionResolver, constraintResolver);
        this.filter = new ComponentFilter();
        this.scanner = new ComponentScanner(factory, filter);
        this.validator = new ClosureValidator(typeResolver);
    }

    public ComponentFilter getFilter() {
        return filter;
    }

    /**
     * Generate the complete model library schema
     */
    public String generateModelLibrary() throws Exception {
        JSONObject schema = new JSONObject();
        JSONObject modelLibrary = new JSONObject();

        // Basic metadata
        modelLibrary.put("name", "BEAST2 Core Library");
        modelLibrary.put("version", "1.0.0");
        modelLibrary.put("engine", "BEAST2");
        modelLibrary.put("engineVersion", BEASTVersion.INSTANCE.getVersion());
        modelLibrary.put("description", "Core model components for BEAST2");

        // Scan for all components
        List<ComponentInfo> allComponents = scanAllComponents();

        // Generate types and generators from components
        JSONArray types = generateTypes(allComponents);
        modelLibrary.put("types", types);

        JSONArray generators = generateGenerators(allComponents);
        modelLibrary.put("generators", generators);

        schema.put("modelLibrary", modelLibrary);
        return schema.toString(2);
    }

    /**
     * Scan for all components using ComponentScanner
     */
    private List<ComponentInfo> scanAllComponents() {
        List<ComponentInfo> allComponents = new ArrayList<>();

        // 1. Scan important base types
        allComponents.addAll(scanner.scanImportantTypes(
                KnownTypes.IMPORTANT_BASE_TYPES, "beast.base"));

        // 2. Scan important non-BEAST interfaces
        allComponents.addAll(scanner.scanImportantTypes(
                KnownTypes.IMPORTANT_NON_BEAST_INTERFACES, "beast.base"));

        // 3. Scan known inner classes
        allComponents.addAll(scanner.scanImportantTypes(
                KnownTypes.IMPORTANT_INNER_CLASSES, "beast.base"));

        // 4. Scan known enums
        allComponents.addAll(scanner.scanEnums(KnownTypes.ENUMS));

        // 5. Scan all packages
        Map<String, Package> packages = getAllPackages();
        allComponents.addAll(scanner.scanPackages(packages));

        // 6. Collect any referenced inner types from the closure test
        Set<String> referencedInnerTypes = collectReferencedInnerTypes();
        if (!referencedInnerTypes.isEmpty()) {
            allComponents.addAll(scanner.scanReferencedInnerTypes(
                    referencedInnerTypes,
                    KnownTypes.PACKAGE_SEARCH_PREFIXES.toArray(new String[0])));
        }

        logger.info("Found " + allComponents.size() + " total components");
        return allComponents;
    }

    /**
     * Get all packages as a map
     */
    private Map<String, Package> getAllPackages() {
        Map<String, Package> packages = new HashMap<>();
        Map<String, Object> allPlugins = factory.getAllPlugins();

        for (Map.Entry<String, Object> entry : allPlugins.entrySet()) {
            if (entry.getValue() instanceof Package) {
                Package pkg = (Package) entry.getValue();
                packages.put(pkg.getName(), pkg);
            }
        }

        return packages;
    }

    /**
     * Collect inner types that are referenced but might not be discovered yet
     */
    private Set<String> collectReferencedInnerTypes() {
        Set<String> innerTypes = new HashSet<>();

        // Add known problematic inner types from the closure test
        innerTypes.add("BinaryCovarion.MODE");
        innerTypes.add("CalibratedBirthDeathModel.Type");
        innerTypes.add("CalibratedYuleModel.Type");
        innerTypes.add("ClusterTree.Type");
        innerTypes.add("ConstrainedClusterTree.Type");
        innerTypes.add("Gamma.mode");
        innerTypes.add("Script.Engine");
        innerTypes.add("StarBeastStartState.Method");
        innerTypes.add("ThreadedTreeLikelihood.Scaling");
        innerTypes.add("TreeLikelihood.Scaling");
        innerTypes.add("TraitSet.Units");

        return innerTypes;
    }

    /**
     * Generate all type definitions from components
     */
    private JSONArray generateTypes(List<ComponentInfo> components) throws Exception {
        JSONArray types = new JSONArray();
        Set<String> processedClasses = new HashSet<>();

        // Add primitive types first
        addPrimitiveTypes(types);

        // First pass: populate knownTypeNames with all component types
        for (ComponentInfo component : components) {
            knownTypeNames.add(typeResolver.getSimpleClassName(component.getClazz()));
        }

        // Second pass: generate type definitions (now with complete knownTypeNames)
        for (ComponentInfo component : components) {
            String className = component.getClassName();
            if (!processedClasses.contains(className)) {
                JSONObject type = generateTypeDefinition(component);
                if (type != null) {
                    types.put(type);
                    processedClasses.add(className);
                }
            }
        }

        logger.info("Generated " + types.length() + " type definitions");
        return types;
    }

    /**
     * Generate all generator definitions from components
     */
    private JSONArray generateGenerators(List<ComponentInfo> components) throws Exception {
        JSONArray generators = new JSONArray();
        Set<String> processedClasses = new HashSet<>();

        for (ComponentInfo component : components) {
            Class<?> clazz = component.getClazz();
            String className = component.getClassName();

            // Only concrete BEASTInterface classes can be generators
            if (!processedClasses.contains(className) &&
                    BEASTInterface.class.isAssignableFrom(clazz) &&
                    !component.isInterface() &&
                    !component.isAbstract() &&
                    !component.isEnum()) {

                try {
                    JSONObject generator = generateGeneratorDefinition(component);
                    if (generator != null) {
                        generators.put(generator);
                        processedClasses.add(className);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to generate definition for " + className + ": " + e.getMessage());
                    // Continue with next component
                }
            }
        }

        logger.info("Generated " + generators.length() + " generator definitions");
        return generators;
    }

    /**
     * Generate a type definition from ComponentInfo
     */
    private JSONObject generateTypeDefinition(ComponentInfo component) {
        JSONObject type = new JSONObject();
        Class<?> clazz = component.getClazz();

        type.put("name", typeResolver.getSimpleClassName(clazz));
        type.put("package", component.getPackageName());  // BEAST2 package/plugin name

        // Add Java package name as fullyQualifiedName
        String javaPackage = clazz.getPackage() != null ? clazz.getPackage().getName() : "";
        String fullyQualifiedName = javaPackage.isEmpty() ?
                clazz.getName() : clazz.getName();
        type.put("fullyQualifiedName", fullyQualifiedName);

        type.put("description", component.getDescription());

        // Handle enums specially
        if (component.isEnum()) {
            type.put("isEnum", true);
            JSONArray values = new JSONArray();
            for (Object constant : clazz.getEnumConstants()) {
                values.put(constant.toString());
            }
            type.put("values", values);
            return type;
        }

        // Class modifiers
        type.put("isAbstract", component.isAbstract());
        type.put("isInterface", component.isInterface());

        // Inheritance - only for BEASTInterface types
        if (BEASTInterface.class.isAssignableFrom(clazz)) {
            if (clazz.getSuperclass() != null &&
                    !clazz.getSuperclass().equals(Object.class) &&
                    BEASTInterface.class.isAssignableFrom(clazz.getSuperclass())) {
                type.put("extends", typeResolver.getSimpleClassName(clazz.getSuperclass()));
            }

            // Interfaces - include both BEASTInterface and other known model interfaces
            JSONArray interfaces = new JSONArray();
            for (Class<?> iface : clazz.getInterfaces()) {
                String ifaceName = typeResolver.getSimpleClassName(iface);

                // Include if it's a BEASTInterface OR if it's in our known types
                if (BEASTInterface.class.isAssignableFrom(iface) || knownTypeNames.contains(ifaceName)) {
                    interfaces.put(ifaceName);
                }
            }
            if (interfaces.length() > 0) {
                type.put("implements", interfaces);
            }
        }

        // Primitive assignment for parameter types
        if (BEASTUtils.isParameterType(clazz) || clazz.equals(Function.class)) {
            type.put("primitiveAssignable", true);
            JSONArray acceptedPrimitives = determineAcceptedPrimitives(clazz);
            if (acceptedPrimitives.length() > 0) {
                type.put("acceptedPrimitives", acceptedPrimitives);
            }
        }

        // Collection type info
        if (isCollectionType(clazz)) {
            JSONObject collectionInfo = new JSONObject();
            collectionInfo.put("elementType", determineElementType(clazz));
            collectionInfo.put("kind", determineCollectionKind(clazz));
            type.put("collectionType", collectionInfo);
        }

        return type;
    }

    /**
     * Generate a generator definition from ComponentInfo
     */
    private JSONObject generateGeneratorDefinition(ComponentInfo component) {
        JSONObject generator = new JSONObject();
        Class<?> clazz = component.getClazz();

        String className = typeResolver.getSimpleClassName(clazz);
        generator.put("name", className);
        generator.put("package", component.getPackageName());  // BEAST2 package/plugin name

        // Add Java package name as fullyQualifiedName
        String fullyQualifiedName = clazz.getName();
        generator.put("fullyQualifiedName", fullyQualifiedName);

        generator.put("description", component.getDescription());

        // Determine if distribution or function
        generator.put("generatorType", component.isDistribution() ? "distribution" : "function");

        // Try to create instance - if it fails, return minimal generator with no arguments
        BEASTInterface instance = null;
        try {
            instance = (BEASTInterface) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.info("Cannot instantiate " + className + " (no no-arg constructor): " + e.getMessage());

            // Return generator with empty arguments
            generator.put("arguments", new JSONArray());

            // Still try to determine generatedType for known cases
            String generatedType = determineGeneratedType(clazz, component.isDistribution(), null);
            if (generatedType != null) {
                generator.put("generatedType", generatedType);
            }

            return generator;
        }

        Map<String, Input<?>> inputMap = BEASTUtils.buildInputMap(instance, clazz);

        if (component.isDistribution()) {
            // Determine generated type with instance available
            String generatedType = determineGeneratedType(clazz, component.isDistribution(), instance);
            if (generatedType != null) {
                generator.put("generatedType", generatedType);
            }

            addDistributionArguments(generator, instance, inputMap, clazz);
        } else {
            // Functions - determine generated type
            String generatedType = determineGeneratedType(clazz, false, null);
            if (generatedType != null) {
                generator.put("generatedType", generatedType);
            }

            // All inputs are arguments
            JSONArray arguments = new JSONArray();
            for (Input<?> input : inputMap.values()) {
                JSONObject arg = argumentBuilder.buildArgument(input, instance, clazz);
                arguments.put(arg);
            }
            generator.put("arguments", arguments);
        }

        return generator;
    }

    /**
     * Determine what type a generator produces
     */
    private String determineGeneratedType(Class<?> clazz, boolean isDistribution, BEASTInterface instance) {
        String className = typeResolver.getSimpleClassName(clazz);

        if (isDistribution) {
            // Tree distributions
            if (isTreeDistribution(clazz)) {
                return "Tree";
            }

            // Parametric distributions
            if (ParametricDistribution.class.isAssignableFrom(clazz)) {
                if (Poisson.class.isAssignableFrom(clazz) || Binomial.class.isAssignableFrom(clazz)) {
                    return "IntegerParameter";
                }
                return "RealParameter";
            }

            // For other distributions, determine from primary input if we have an instance
            if (instance != null) {
                String primaryInputName = factory.getPrimaryInputName(instance);
                if (primaryInputName != null) {
                    Input<?> primaryInput = instance.getInput(primaryInputName);
                    if (primaryInput != null) {
                        // Use BEASTUtils to get the proper type, handling generics
                        Type inputType = BEASTUtils.getInputExpectedType(primaryInput, instance, primaryInputName);
                        if (inputType != null) {
                            return typeResolver.resolveType(inputType);
                        } else {
                            logger.warning("Could not determine type for primary input '" + primaryInputName + "' of " + className);
                        }
                    }
                }
            }

            // No generatedType if we can't determine it properly
            return null;
        } else {
            // Special function cases
            if (className.equals("nexus") || className.equals("fasta")) {
                return "Alignment";
            }
            if (className.equals("newick")) {
                return "Tree";
            }

            // Most functions generate their own type
            return className;
        }
    }

    /**
     * Add distribution arguments (excluding primary argument)
     */
    private void addDistributionArguments(JSONObject generator, BEASTInterface instance, Map<String, Input<?>> inputMap, Class<?> clazz) {
        String primaryInputName = factory.getPrimaryInputName(instance);

        // All inputs except primary argument are arguments for distributions
        JSONArray arguments = new JSONArray();
        for (Map.Entry<String, Input<?>> entry : inputMap.entrySet()) {
            String inputName = entry.getKey();
            Input<?> input = entry.getValue();

            // Skip the primary argument if it exists
            if (primaryInputName != null && inputName.equals(primaryInputName)) {
                continue;
            }

            JSONObject arg = argumentBuilder.buildArgument(input, instance, clazz);
            arguments.put(arg);
        }

        generator.put("arguments", arguments);
    }

    /**
     * Add primitive type definitions
     */
    private void addPrimitiveTypes(JSONArray types) {
        String[][] primitiveTypes = {
                {"String", "String", "Text values"},
                {"Integer", "Integer", "Integer numbers"},
                {"Double", "Double", "Floating point numbers"},
                {"Boolean", "Boolean", "True/false values"}
        };

        for (String[] typeInfo : primitiveTypes) {
            JSONObject type = new JSONObject();
            type.put("name", typeInfo[0]);
            type.put("package", "primitive");
            type.put("description", typeInfo[2]);
            type.put("primitiveAssignable", true);

            JSONArray accepted = new JSONArray();
            accepted.put(typeInfo[1]);
            type.put("acceptedPrimitives", accepted);

            types.put(type);
        }
    }

    /**
     * Validate the generated schema
     */
    public ValidationResult validateSchema(String jsonSchema) {
        return validator.validateClosure(new JSONObject(jsonSchema));
    }

    /**
     * Determine accepted primitives for a type
     */
    private JSONArray determineAcceptedPrimitives(Class<?> clazz) {
        JSONArray primitives = new JSONArray();
        String className = clazz.getSimpleName();

        if (className.contains("RealParameter") || className.contains("DoubleParameter")) {
            primitives.put("Double");
            primitives.put("Float");
        } else if (className.contains("IntegerParameter")) {
            primitives.put("Integer");
            primitives.put("Long");
        } else if (className.contains("BooleanParameter")) {
            primitives.put("Boolean");
        } else if (className.equals("Function")) {
            primitives.put("Double");
            primitives.put("Integer");
        }

        return primitives;
    }

    /**
     * Check if class is a collection type
     */
    private boolean isCollectionType(Class<?> clazz) {
        return List.class.isAssignableFrom(clazz) ||
                clazz.isArray() ||
                clazz.getName().contains("Vector") ||
                clazz.getName().contains("Matrix");
    }

    /**
     * Determine element type for collections
     */
    private String determineElementType(Class<?> clazz) {
        if (clazz.isArray()) {
            return typeResolver.getSimpleClassName(clazz.getComponentType());
        }

        String className = clazz.getSimpleName();
        if (className.contains("RealParameter")) return "Real";
        if (className.contains("IntegerParameter")) return "Integer";
        if (className.contains("Tree")) return "Tree";

        return "Object";
    }

    /**
     * Determine collection kind
     */
    private String determineCollectionKind(Class<?> clazz) {
        if (clazz.isArray()) return "array";
        if (List.class.isAssignableFrom(clazz)) return "list";
        if (clazz.getName().contains("Matrix")) return "matrix";
        if (clazz.getName().contains("Vector")) return "vector";

        return "collection";
    }

    /**
     * Check if class is a tree distribution
     */
    private boolean isTreeDistribution(Class<?> clazz) {
        // Use direct class check instead of Class.forName
        return TreeDistribution.class.isAssignableFrom(clazz);
    }

    /**
     * Main method
     */
    public static void main(String[] args) {
        try {
            BEAST2ModelLibraryGenerator generator = new BEAST2ModelLibraryGenerator();
            String jsonOutput = generator.generateModelLibrary();

            Files.write(Paths.get("beast2-model-library.json"), jsonOutput.getBytes());

            System.out.println("Generated beast2-model-library.json");
            System.out.println("Types and generators have been created following the new schema format");

        } catch (Exception e) {
            logger.severe("Error generating model library: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
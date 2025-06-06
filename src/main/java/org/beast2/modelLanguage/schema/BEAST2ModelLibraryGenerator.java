package org.beast2.modelLanguage.schema;

import beast.base.core.BEASTInterface;
import beast.base.core.Function;
import beast.base.core.Input;
import beast.base.evolution.substitutionmodel.SubstitutionModel;
import beast.base.inference.Distribution;
import beast.base.inference.StateNode;
import beast.base.inference.distribution.ParametricDistribution;
import beast.pkgmgmt.BEASTVersion;
import org.beast2.modelLanguage.beast.BeastObjectFactory;
import org.beast2.modelLanguage.beast.BEASTUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import beast.pkgmgmt.Package;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * BEAST2-specific implementation to generate the engine-agnostic JSON schema
 * Updated to comply with simplified schema focusing on isDistribution distinction
 */
public class BEAST2ModelLibraryGenerator {

    private static final Logger logger = Logger.getLogger(BEAST2ModelLibraryGenerator.class.getName());
    private final BeastObjectFactory factory;

    public BEAST2ModelLibraryGenerator() {
        this.factory = new BeastObjectFactory();
    }

    /**
     * Check if a class represents a model component (not inference/logging)
     * We exclude inference machinery (Operators, Loggers, MCMC, Runnable) as this is a Model Library
     */
    private boolean isModelClass(Class<?> clazz) {
        String className = clazz.getName();
        String packageName = clazz.getPackage() != null ? clazz.getPackage().getName() : "";

        // Exclude beastfx.app packages - BEAUti GUI components
        if (packageName.startsWith("beastfx.app")) {
            logger.fine("Excluding BEAUti/GUI class: " + clazz.getSimpleName());
            return false;
        }

        // Exclude classes that extend anything from beastfx.app packages
        Class<?> current = clazz.getSuperclass();
        while (current != null) {
            if (current.getPackage() != null &&
                    current.getPackage().getName().startsWith("beastfx.app")) {
                logger.fine("Excluding subclass of BEAUti/GUI class: " + clazz.getSimpleName());
                return false;
            }
            current = current.getSuperclass();
        }

        // Exclude Runnable subclasses (MCMC, simulators, etc.) - not part of model specification
        try {
            Class<?> runnableClass = Class.forName("beast.base.inference.Runnable");
            if (runnableClass.isAssignableFrom(clazz)) {
                logger.fine("Excluding Runnable: " + clazz.getSimpleName());
                return false;
            }
        } catch (ClassNotFoundException e) {
            // Runnable class not found, continue
        }

        // Exclude Operator subclasses (MCMC operators) - not part of model specification
        try {
            Class<?> operatorClass = Class.forName("beast.base.inference.Operator");
            if (operatorClass.isAssignableFrom(clazz)) {
                logger.fine("Excluding Operator: " + clazz.getSimpleName());
                return false;
            }
        } catch (ClassNotFoundException e) {
            // Operator class not found, continue
        }

        // Exclude classes in operator packages
        if (packageName.contains(".operators") || packageName.contains(".operator")) {
            logger.fine("Excluding operator package class: " + clazz.getSimpleName());
            return false;
        }

        // Exclude OperatorSchedule subclasses - not part of model specification
        try {
            Class<?> operatorScheduleClass = Class.forName("beast.base.inference.OperatorSchedule");
            if (operatorScheduleClass.isAssignableFrom(clazz)) {
                logger.fine("Excluding OperatorSchedule: " + clazz.getSimpleName());
                return false;
            }
        } catch (ClassNotFoundException e) {
            // OperatorSchedule class not found, continue
        }

        // Exclude Logger subclasses - not part of model specification
        try {
            Class<?> loggerClass = Class.forName("beast.base.inference.Logger");
            if (loggerClass.isAssignableFrom(clazz)) {
                logger.fine("Excluding Logger: " + clazz.getSimpleName());
                return false;
            }
        } catch (ClassNotFoundException e) {
            // Logger class not found, continue
        }

        // Check if this class has inputs that require inference types
        if (BEASTInterface.class.isAssignableFrom(clazz) && !clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
            try {
                BEASTInterface instance = (BEASTInterface) clazz.getDeclaredConstructor().newInstance();
                for (Input<?> input : instance.listInputs()) {
                    String typeName = getInputTypeName(input);
                    if (isInferenceType(typeName)) {
                        logger.fine("Excluding " + clazz.getSimpleName() + " - uses inference type: " + typeName);
                        return false;
                    }
                    if (isGUIType(typeName)) {
                        logger.fine("Excluding " + clazz.getSimpleName() + " - uses GUI type: " + typeName);
                        return false;
                    }
                }
            } catch (Exception e) {
                // Can't check inputs, continue
            }
        }

        // Exclude test classes
        if (className.endsWith("Test") || className.endsWith("Tests")) {
            logger.fine("Excluding test class: " + clazz.getSimpleName());
            return false;
        }

        // Exclude BEAUti-related classes
        if (className.contains("Beauti") || className.contains("BEAUti")) {
            logger.fine("Excluding BEAUti-related class: " + clazz.getSimpleName());
            return false;
        }

        // Exclude classes with Logger in their name that aren't model components
        if (className.contains("Logger") && !className.contains("TreeLogger")) {
            // TreeLogger might be a legitimate tree component, but other *Logger classes are usually logging
            logger.fine("Excluding logger-like class: " + clazz.getSimpleName());
            return false;
        }

        // Include all other classes
        return true;
    }

    /**
     * Check if a type name is an inference-related type
     */
    private boolean isInferenceType(String typeName) {
        return typeName.equals("Logger") ||
                typeName.equals("Operator") ||
                typeName.equals("MCMC") ||
                typeName.equals("OperatorSchedule") ||
                typeName.equals("Runnable") ||
                typeName.contains("Logger") ||
                typeName.contains("Operator") ||
                typeName.contains("OperatorSchedule") ||
                typeName.equals("DistanceProvider");  // In operators package
    }

    /**
     * Check if a type name is a BEAUti/GUI-related type
     */
    private boolean isGUIType(String typeName) {
        return typeName.equals("LogFile") ||
                typeName.equals("TreeFile") ||
                typeName.equals("OutFile") ||
                typeName.equals("BeautiSubTemplate") ||
                typeName.contains("Beauti");
    }

    /**
     * Generate the engine-agnostic model library schema for BEAST2
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

        // All model components in one unified list
        JSONArray components = generateComponents();
        modelLibrary.put("components", components);

        schema.put("modelLibrary", modelLibrary);
        return schema.toString(2);
    }

    /**
     * Generate all model components
     */
    private JSONArray generateComponents() throws Exception {
        JSONArray components = new JSONArray();
        Set<String> processedClasses = new HashSet<>();

        // First, add important base types that might not be found through package scanning
        String[] importantBaseTypes = {
                "beast.base.core.BEASTInterface",
                "beast.base.core.BEASTObject",
                "beast.base.inference.Distribution",
                "beast.base.inference.distribution.ParametricDistribution",
                "beast.base.inference.StateNode",
                "beast.base.inference.StateNodeInitialiser",
                "beast.base.evolution.tree.TreeDistribution",
                "beast.base.evolution.speciation.SpeciesTreeDistribution",
                // Base implementations
                "beast.base.evolution.branchratemodel.BranchRateModel$Base",
                "beast.base.evolution.substitutionmodel.SubstitutionModel$Base",
                "beast.base.evolution.datatype.DataType$Base",
                // SA package type that's missing from service providers (packaging issue)
                "sa.evolution.speciation.SABDParameterization"
        };

        // Special handling for important interfaces - some are not BEASTInterface but crucial for type system
        String[] importantNonBEASTInterfaces = {
                "beast.base.core.Function",
                "beast.base.evolution.sitemodel.SiteModelInterface",
                "beast.base.inference.parameter.Parameter",
                "starbeast3.evolution.branchratemodel.BranchRateModelSB3",
                // Tree interface
                "beast.base.evolution.tree.TreeInterface",
                // Model interfaces
                "beast.base.evolution.branchratemodel.BranchRateModel",
                "beast.base.evolution.substitutionmodel.SubstitutionModel",
                "beast.base.evolution.tree.coalescent.PopulationFunction",
                // Note: PopulationModel is in starbeast3, not beast.base
                "starbeast3.evolution.speciation.PopulationModel",
                // Distance and metrics
                "beast.base.evolution.distance.Distance",
                "beast.base.evolution.tree.TreeMetric"
        };

        // Important inner classes that might not be discovered through package scanning
        String[] importantInnerClasses = {
                "beast.base.evolution.sitemodel.SiteModelInterface$Base",
                "beast.base.inference.parameter.Parameter$Base",
                "beast.base.evolution.branchratemodel.BranchRateModel$Base",
                "beast.base.evolution.substitutionmodel.SubstitutionModel$Base",
                "beast.base.evolution.datatype.DataType$Base"
        };

        // Known protected enums that need special handling
        String[] protectedEnums = {
                "starbeast3.evolution.speciation.SpeciesTreePrior$TreePopSizeFunction",
                // These appear to be in different packages or have different structures
                "beast.evolution.substitutionmodel.GeneralLazySubstitutionModel$RelaxationMode",
                "beast.evolution.substitutionmodel.LazyHKY$RelaxationMode",  // Without .base
                "beast.evolution.tree.ClusterTree$Type",
                "beast.evolution.tree.ConstrainedClusterTree$Type"
        };

        // Add important BEASTInterface types
        for (String className : importantBaseTypes) {
            if (!processedClasses.contains(className)) {
                try {
                    Class<?> clazz = factory.loadClass(className);
                    if (BEASTInterface.class.isAssignableFrom(clazz) && isModelClass(clazz)) {
                        JSONObject component = generateComponentDefinition(clazz, "BEAST.base");
                        if (component != null) {
                            components.put(component);
                            processedClasses.add(className);
                            logger.info("Added important base type: " + clazz.getSimpleName());
                        }

                        // Also check for public inner BEASTInterfaces
                        addPublicInnerBEASTInterfaces(clazz, components, processedClasses, "BEAST.base");
                    }
                } catch (Exception e) {
                    logger.warning("Could not load important base type: " + className + " - " + e.getMessage());
                }
            }
        }

        // Add important non-BEASTInterface types (like Function, Parameter, TreeInterface)
        for (String className : importantNonBEASTInterfaces) {
            if (!processedClasses.contains(className)) {
                try {
                    Class<?> clazz = factory.loadClass(className);
                    // For non-BEAST interfaces, we don't check isModelClass since they might not follow BEAST patterns
                    // Get the correct package name for the component
                    String packageName = clazz.getPackage() != null ? clazz.getPackage().getName() : "unknown";
                    JSONObject component = generateComponentDefinition(clazz, packageName);
                    if (component != null) {
                        components.put(component);
                        processedClasses.add(className);
                        // Also add by simple name for interfaces
                        processedClasses.add(clazz.getSimpleName());
                        logger.info("Added important non-BEAST interface: " + clazz.getSimpleName() + " from package: " + packageName);
                    } else {
                        logger.warning("generateComponentDefinition returned null for: " + className);
                    }
                } catch (Exception e) {
                    logger.warning("Could not load important non-BEAST interface: " + className + " - " + e.getMessage());
                    if (className.contains("BranchRateModelSB3")) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // Add important inner classes that might be missed
        for (String className : importantInnerClasses) {
            if (!processedClasses.contains(className)) {
                try {
                    Class<?> clazz = factory.loadClass(className);
                    if (BEASTInterface.class.isAssignableFrom(clazz)) {
                        JSONObject component = generateComponentDefinition(clazz, "BEAST.base");
                        if (component != null) {
                            components.put(component);
                            processedClasses.add(className);
                            logger.info("Added important inner class: " + getSimpleClassName(clazz));
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Could not load important inner class: " + className + " - " + e.getMessage());
                }
            }
        }

        // Add protected enums that control string inputs
        for (String enumName : protectedEnums) {
            if (!processedClasses.contains(enumName)) {
                try {
                    Class<?> enumClass = factory.loadClass(enumName);
                    if (enumClass.isEnum()) {
                        JSONObject component = createInnerTypeComponent(enumClass);
                        if (component != null) {
                            components.put(component);
                            processedClasses.add(enumName);
                            logger.info("Added protected enum: " + getSimpleClassName(enumClass));
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Could not load protected enum: " + enumName + " - " + e.getMessage());
                }
            }
        }

        // Get all plugins - we know these are Package objects
        Map<String, Object> allPlugins = factory.getAllPlugins();
        logger.info("Found " + allPlugins.size() + " total plugins");

        for (Map.Entry<String, Object> entry : allPlugins.entrySet()) {
            String pluginName = entry.getKey();
            Object pluginObj = entry.getValue();

            if (pluginObj instanceof Package) {
                Package pkg = (Package) pluginObj;
                List<String> classes = factory.findModelObjectClasses(pkg.getName());

                // Process the classes
                for (String className : classes) {
                    if (!processedClasses.contains(className)) {
                        try {
                            Class<?> clazz = factory.loadClass(className);

                            if (BEASTInterface.class.isAssignableFrom(clazz) && isModelClass(clazz)) {
                                JSONObject component = generateComponentDefinition(clazz, pkg.getName());
                                if (component != null) {
                                    components.put(component);
                                    processedClasses.add(className);
                                }

                                // Also check for public inner classes that are BEASTInterfaces
                                addPublicInnerBEASTInterfaces(clazz, components, processedClasses, pkg.getName());
                            }
                        } catch (Exception e) {
                            logger.fine("Could not process class: " + className + " - " + e.getMessage());
                        }
                    }
                }
            }
        }

        logger.info("Total components generated: " + components.length());

        // Second pass: scan for inner types (enums, inner classes) used in arguments
        addInnerTypes(components, processedClasses);

        return components;
    }

    /**
     * Add public inner classes that are BEASTInterfaces
     */
    private void addPublicInnerBEASTInterfaces(Class<?> outerClass, JSONArray components,
                                               Set<String> processedClasses, String packageName) {
        try {
            // Get all inner classes
            Class<?>[] innerClasses = outerClass.getDeclaredClasses();

            for (Class<?> innerClass : innerClasses) {
                // Check if it's public and a BEASTInterface
                if (java.lang.reflect.Modifier.isPublic(innerClass.getModifiers()) &&
                        BEASTInterface.class.isAssignableFrom(innerClass) &&
                        !processedClasses.contains(innerClass.getName()) &&
                        isModelClass(innerClass)) {

                    JSONObject component = generateComponentDefinition(innerClass, packageName);
                    if (component != null) {
                        components.put(component);
                        processedClasses.add(innerClass.getName());
                        logger.info("Added public inner BEASTInterface: " + getSimpleClassName(innerClass));
                    }
                }
            }
        } catch (Exception e) {
            logger.fine("Error checking inner classes for " + outerClass.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Scan components for inner types used in arguments and add them
     */
    private void addInnerTypes(JSONArray components, Set<String> processedClasses) {
        Set<String> innerTypesToAdd = new HashSet<>();

        // First, collect all inner types referenced in arguments
        for (int i = 0; i < components.length(); i++) {
            JSONObject component = components.getJSONObject(i);

            // Check primary argument
            if (component.has("primaryArgument")) {
                JSONObject primaryArg = component.getJSONObject("primaryArgument");
                String type = primaryArg.getString("type");
                collectInnerTypes(type, innerTypesToAdd, processedClasses);
            }

            // Check all arguments
            if (component.has("arguments")) {
                JSONArray arguments = component.getJSONArray("arguments");
                for (int j = 0; j < arguments.length(); j++) {
                    JSONObject arg = arguments.getJSONObject(j);
                    String type = arg.getString("type");
                    collectInnerTypes(type, innerTypesToAdd, processedClasses);
                }
            }
        }

        // Now try to load and add these inner types
        int addedCount = 0;
        for (String innerTypeName : innerTypesToAdd) {
            try {
                // Convert inner class notation to proper class name
                String className = innerTypeName.replace('.', '$');

                // Try to find the full class name
                Class<?> innerClass = null;

                // First try direct loading
                try {
                    innerClass = factory.loadClass(className);
                } catch (ClassNotFoundException e) {
                    // Try with common package prefixes
                    String[] packagePrefixes = {
                            "beast.base.evolution.substitutionmodel.",
                            "beast.base.evolution.tree.",
                            "beast.base.evolution.speciation.",
                            "beast.base.evolution.datatype.",
                            "beast.base.evolution.distance.",
                            "beast.base.evolution.likelihood.",
                            "beast.base.inference.",
                            "beast.base.inference.distribution.",
                            "beast.base.inference.parameter.",
                            "beast.base.inference.util.",
                            "beast.base.core.",
                            "starbeast3.evolution.",
                            "starbeast3.tree.",
                            "starbeast3.core.",
                            "beast.pkgmgmt.",
                            "beast.app.seqgen.",
                            "beastlabs.evolution.tree.",
                            "beastlabs.math.distributions.",
                            ""  // Try without any prefix too
                    };

                    for (String prefix : packagePrefixes) {
                        try {
                            innerClass = factory.loadClass(prefix + className);
                            break;
                        } catch (ClassNotFoundException e2) {
                            // Continue trying
                        }
                    }
                }

                if (innerClass != null && !processedClasses.contains(innerClass.getName())) {
                    // Create a simple component definition for enums and inner classes
                    JSONObject innerComponent = createInnerTypeComponent(innerClass);
                    if (innerComponent != null) {
                        components.put(innerComponent);
                        processedClasses.add(innerClass.getName());
                        addedCount++;
                    }
                }
            } catch (Exception e) {
                logger.fine("Could not load inner type: " + innerTypeName + " - " + e.getMessage());
            }
        }

        if (addedCount > 0) {
            logger.info("Added " + addedCount + " inner types (enums/inner classes)");
        }
    }

    /**
     * Collect inner types from a type string
     */
    private void collectInnerTypes(String type, Set<String> innerTypesToAdd, Set<String> processedClasses) {
        // Extract base type from collections
        String baseType = extractBaseType(type);

        // Check if it looks like an inner type (contains dot but not a package)
        if (baseType.contains(".") && !baseType.contains("java.") && Character.isUpperCase(baseType.charAt(0))) {
            // This might be an inner class/enum like ClusterTree.Type
            if (!processedClasses.contains(baseType)) {
                innerTypesToAdd.add(baseType);
            }
        }
    }

    /**
     * Create a component definition for an inner type (enum or inner class)
     */
    private JSONObject createInnerTypeComponent(Class<?> innerClass) {
        JSONObject component = new JSONObject();

        String componentName = getSimpleClassName(innerClass);
        component.put("name", componentName);
        component.put("fullyQualifiedName", innerClass.getName());
        component.put("isDistribution", false); // Enums and inner types are not distributions
        component.put("isAbstract", java.lang.reflect.Modifier.isAbstract(innerClass.getModifiers()));
        component.put("isInterface", innerClass.isInterface());
        component.put("isEnum", innerClass.isEnum());

        // Extract package from the declaring class
        String packageName = "";
        if (innerClass.getDeclaringClass() != null) {
            java.lang.Package pkg = innerClass.getDeclaringClass().getPackage();
            if (pkg != null) {
                packageName = pkg.getName();
            }
        }
        component.put("package", packageName);

        if (innerClass.isEnum()) {
            component.put("description", "Enum type for " + innerClass.getDeclaringClass().getSimpleName());

            // For enums, add the possible values as a property
            JSONArray properties = new JSONArray();
            JSONObject valuesProperty = new JSONObject();
            valuesProperty.put("name", "values");
            valuesProperty.put("type", "String[]");
            valuesProperty.put("access", "read-only");

            // Get enum constants
            Object[] constants = innerClass.getEnumConstants();
            if (constants != null) {
                JSONArray values = new JSONArray();
                for (Object constant : constants) {
                    values.put(constant.toString());
                }
                valuesProperty.put("value", values);
            }
            properties.put(valuesProperty);
            component.put("properties", properties);
        } else {
            component.put("description", "Inner type of " + innerClass.getDeclaringClass().getSimpleName());
            component.put("properties", new JSONArray());
        }

        component.put("arguments", new JSONArray()); // Inner types typically don't have arguments

        return component;
    }

    /**
     * Generate a single component definition with BEAST2-specific logic
     */
    private JSONObject generateComponentDefinition(Class<?> clazz, String packageName) throws Exception {
        JSONObject component = new JSONObject();

        // Use qualified name for inner classes to avoid collisions like Parameter.Base vs Distance.Base
        String componentName = getSimpleClassName(clazz);
        component.put("name", componentName);
        component.put("fullyQualifiedName", clazz.getName());

        // Determine if this is a distribution
        boolean isDistribution = Distribution.class.isAssignableFrom(clazz) ||
                ParametricDistribution.class.isAssignableFrom(clazz);
        component.put("isDistribution", isDistribution);

        component.put("isAbstract", java.lang.reflect.Modifier.isAbstract(clazz.getModifiers()));
        component.put("isInterface", clazz.isInterface());
        component.put("package", packageName);
        component.put("description", "BEAST2 " + componentName);

        // Inheritance
        if (clazz.getSuperclass() != null && BEASTInterface.class.isAssignableFrom(clazz.getSuperclass())) {
            component.put("extends", clazz.getSuperclass().getName());
        }

        // Interfaces - need to check ALL interfaces, not just direct ones
        JSONArray interfaces = new JSONArray();
        Set<String> allInterfaces = new HashSet<>();
        collectAllInterfaces(clazz, allInterfaces);

        for (String interfaceName : allInterfaces) {
            interfaces.put(interfaceName);
        }
        component.put("implements", interfaces);

        // Add component-specific details
        if (!clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
            // Only try to instantiate if it's a BEASTInterface
            if (BEASTInterface.class.isAssignableFrom(clazz)) {
                try {
                    BEASTInterface instance = (BEASTInterface) clazz.getDeclaredConstructor().newInstance();
                    addComponentDetails(component, instance, clazz, isDistribution);
                } catch (Exception e) {
                    logger.fine("Can't instantiate " + clazz.getSimpleName() + ": " + e.getMessage());
                    // Can't instantiate, but still include the component with empty arrays
                    component.put("arguments", new JSONArray());
                    component.put("properties", new JSONArray());
                }
            } else {
                // Non-BEASTInterface concrete class
                component.put("arguments", new JSONArray());
                component.put("properties", extractProperties(clazz));
            }
        } else {
            // For interfaces and abstract classes, we can't instantiate but we still want them in the schema
            component.put("arguments", new JSONArray());
            component.put("properties", new JSONArray());
        }

        return component;
    }

    /**
     * Recursively collect all interfaces implemented by a class and its superclasses
     */
    private void collectAllInterfaces(Class<?> clazz, Set<String> interfaces) {
        if (clazz == null) return;

        // Add direct interfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            interfaces.add(iface.getName());
            // Recursively add interfaces that this interface extends
            collectAllInterfaces(iface, interfaces);
        }

        // Recursively check superclass
        collectAllInterfaces(clazz.getSuperclass(), interfaces);
    }

    /**
     * Add component details (arguments and properties)
     */
    private void addComponentDetails(JSONObject component, BEASTInterface instance, Class<?> clazz, boolean isDistribution) {
        if (isDistribution) {
            // For distributions, handle primary argument separately
            addDistributionDetails(component, instance, clazz);
        } else {
            // For non-distributions, all inputs are regular arguments
            JSONArray arguments = new JSONArray();
            for (Input<?> input : instance.listInputs()) {
                JSONObject arg = createArgumentObject(input, instance, clazz);
                arguments.put(arg);
            }
            component.put("arguments", arguments);
        }

        // Add properties (from getter methods)
        JSONArray properties = extractProperties(clazz);
        component.put("properties", properties);
    }

    /**
     * Add distribution-specific details
     */
    private void addDistributionDetails(JSONObject component, BEASTInterface instance, Class<?> clazz) {
        String primaryInputName = factory.getPrimaryInputName(instance);
        boolean isParametricDistribution = ParametricDistribution.class.isAssignableFrom(clazz);

        // Handle primary argument
        if (primaryInputName != null) {
            Input<?> primaryInput = instance.getInput(primaryInputName);
            if (primaryInput != null) {
                JSONObject primaryArg = createArgumentObject(primaryInput, instance, clazz);
                component.put("primaryArgument", primaryArg);
            }
        } else if (isParametricDistribution) {
            // For ParametricDistributions, create a synthetic primary argument for Function/RealParameter
            JSONObject primaryArg = new JSONObject();
            primaryArg.put("name", "x");
            primaryArg.put("type", "Function");
            primaryArg.put("description", "Random variable (automatically wrapped in Prior)");
            component.put("primaryArgument", primaryArg);
        }

        // Add other arguments (non-primary inputs)
        JSONArray arguments = new JSONArray();
        for (Input<?> input : instance.listInputs()) {
            if (!input.getName().equals(primaryInputName)) {
                JSONObject arg = createArgumentObject(input, instance, clazz);
                arguments.put(arg);
            }
        }
        component.put("arguments", arguments);
    }

    /**
     * Create an argument object from an Input
     */
    private JSONObject createArgumentObject(Input<?> input, BEASTInterface instance, Class<?> clazz) {
        JSONObject arg = new JSONObject();
        arg.put("name", input.getName());

        String inputType = getProperInputTypeName(input, instance);
        arg.put("type", inputType);

        arg.put("description", input.getTipText());
        arg.put("required", input.getRule() == Input.Validate.REQUIRED);
        if (input.defaultValue != null) {
            arg.put("default", input.defaultValue.toString());
        }

        // Add dimension information
        addArgumentDimensionInfo(arg, input, instance, clazz);

        // Add constraints
        addArgumentConstraints(arg, input, clazz);

        return arg;
    }

    /**
     * Get proper input type name using BEASTUtils
     */
    private String getProperInputTypeName(Input<?> input, BEASTInterface instance) {
        try {
            // Use your existing BEASTUtils method to get the proper type
            Type expectedType = BEASTUtils.getInputExpectedType(input, instance, input.getName());

            if (expectedType != null) {
                return convertTypeToString(expectedType);
            }
        } catch (Exception e) {
            logger.fine("Error getting input type for " + input.getName() + ": " + e.getMessage());
        }

        // Fallback to the basic approach
        return getInputTypeName(input);
    }

    /**
     * Convert a Type to a readable string
     */
    private String convertTypeToString(Type type) {
        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;

            // Handle arrays
            if (clazz.isArray()) {
                return getSimpleClassName(clazz.getComponentType()) + "[]";
            }

            // Handle primitives
            if (clazz.isPrimitive()) {
                return capitalizeFirstLetter(clazz.getSimpleName());
            }

            // Handle wrapper classes as primitives
            if (clazz == Integer.class) return "Integer";
            if (clazz == Double.class) return "Double";
            if (clazz == Boolean.class) return "Boolean";
            if (clazz == String.class) return "String";

            return getSimpleClassName(clazz);

        } else if (type instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) type;
            Type rawType = pType.getRawType();

            if (rawType instanceof Class<?>) {
                Class<?> rawClass = (Class<?>) rawType;

                // Handle List<T> types
                if (List.class.isAssignableFrom(rawClass)) {
                    Type[] typeArgs = pType.getActualTypeArguments();
                    if (typeArgs.length > 0) {
                        String elementType = convertTypeToString(typeArgs[0]);
                        return "List<" + elementType + ">";
                    }
                    return "List";
                }

                return getSimpleClassName(rawClass);
            }
        }

        // For complex types, try to extract something useful
        String typeName = type.toString();
        if (typeName.startsWith("class ")) {
            typeName = typeName.substring(6);
        }

        // Extract just the class name
        int lastDot = typeName.lastIndexOf('.');
        if (lastDot >= 0) {
            typeName = typeName.substring(lastDot + 1);
        }

        return typeName;
    }

    /**
     * Fallback method for getting input type names
     */
    private String getInputTypeName(Input<?> input) {
        Type type = input.getType();

        if (type == null) {
            return "Object";
        }

        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;

            if (clazz.isArray()) {
                return getSimpleClassName(clazz.getComponentType()) + "[]";
            }

            return getSimpleClassName(clazz);
        }

        return "Object";
    }

    /**
     * Get a simple, readable class name for BEAST2 types
     */
    private String getSimpleClassName(Class<?> clazz) {
        // Handle inner classes (like Function$Constant -> Function.Constant)
        if (clazz.isMemberClass()) {
            return clazz.getDeclaringClass().getSimpleName() + "." + clazz.getSimpleName();
        }

        // Handle nested static classes that might not be detected as member classes
        String fullName = clazz.getName();
        if (fullName.contains("$")) {
            // Split on $ and convert to dot notation
            String[] parts = fullName.split("\\$");
            if (parts.length >= 2) {
                // Get simple name of outer class
                String outerClassName = parts[parts.length - 2];
                int lastDot = outerClassName.lastIndexOf('.');
                if (lastDot >= 0) {
                    outerClassName = outerClassName.substring(lastDot + 1);
                }

                // Get simple name of inner class
                String innerClassName = parts[parts.length - 1];

                return outerClassName + "." + innerClassName;
            }
        }

        String className = clazz.getSimpleName();

        // For empty class names (can happen with some generic types), use the full name
        if (className.isEmpty()) {
            return clazz.getName();
        }

        return className;
    }

    /**
     * Extract properties from getter methods
     */
    private JSONArray extractProperties(Class<?> clazz) {
        JSONArray properties = new JSONArray();
        Set<String> processedProperties = new HashSet<>();

        // Get all public methods
        for (Method method : clazz.getMethods()) {
            String methodName = method.getName();

            // Look for getters
            if ((methodName.startsWith("get") && methodName.length() > 3) ||
                    (methodName.startsWith("is") && methodName.length() > 2)) {

                // Skip certain methods
                if (methodName.equals("getClass") ||
                        methodName.equals("getID") ||
                        methodName.equals("getInput") ||
                        method.getParameterCount() > 0) {
                    continue;
                }

                String propertyName = extractPropertyName(methodName);
                if (!processedProperties.contains(propertyName)) {
                    JSONObject property = new JSONObject();
                    property.put("name", propertyName);
                    property.put("type", getSimpleClassName(method.getReturnType()));
                    property.put("access", "read-only");

                    // Special handling for known properties
                    if (propertyName.equals("stateCount") && SubstitutionModel.class.isAssignableFrom(clazz)) {
                        handleStateCountProperty(property, clazz);
                    }

                    properties.put(property);
                    processedProperties.add(propertyName);
                }
            }
        }

        return properties;
    }

    /**
     * Handle stateCount property for SubstitutionModel
     */
    private void handleStateCountProperty(JSONObject property, Class<?> clazz) {
        // Check if it's a fixed nucleotide model
        if (SubstitutionModel.NucleotideBase.class.isAssignableFrom(clazz)) {
            property.put("value", 4);
        }
        // Check for LewisMK or similar parameterized models
        else if (clazz.getSimpleName().equals("LewisMK")) {
            property.put("source", "argument:stateNumber");
        }
        // Add more specific cases as needed
    }

    // Known dimension dependencies
    private static class DimensionInfo {
        final String type = "contextual";
        final List<DimensionResolution> resolutions = new ArrayList<>();

        static class DimensionResolution {
            final String context; // "parent", "sibling", "alignment"
            final String path;
            final String when;

            DimensionResolution(String context, String path, String when) {
                this.context = context;
                this.path = path;
                this.when = when;
            }
        }
    }

    private static final Map<String, DimensionInfo> DIMENSION_DEPENDENCIES = new HashMap<>();
    static {
        // Frequencies dimension depends on context
        DimensionInfo freqDim = new DimensionInfo();
        freqDim.resolutions.add(new DimensionInfo.DimensionResolution(
                "parent", "stateCount", "parent implements SubstitutionModel"
        ));
        freqDim.resolutions.add(new DimensionInfo.DimensionResolution(
                "sibling", "siteModel.substModel.stateCount", "parent is TreeLikelihood"
        ));
        freqDim.resolutions.add(new DimensionInfo.DimensionResolution(
                "alignment", "dataType.stateCount", "alignment is available"
        ));
        DIMENSION_DEPENDENCIES.put("Frequencies.frequencies", freqDim);
    }

    /**
     * Add dimension information to an argument
     */
    private void addArgumentDimensionInfo(JSONObject arg, Input<?> input, BEASTInterface instance, Class<?> clazz) {
        String className = clazz.getSimpleName();
        String argName = input.getName();
        String fullKey = className + "." + argName;

        // Check for known dimension dependencies
        if (DIMENSION_DEPENDENCIES.containsKey(fullKey)) {
            DimensionInfo dimInfo = DIMENSION_DEPENDENCIES.get(fullKey);
            JSONObject dimension = new JSONObject();
            dimension.put("type", dimInfo.type);

            JSONArray resolutions = new JSONArray();
            for (DimensionInfo.DimensionResolution res : dimInfo.resolutions) {
                JSONObject resolution = new JSONObject();
                resolution.put("context", res.context);
                resolution.put("path", res.path);
                resolution.put("when", res.when);
                resolutions.put(resolution);
            }
            dimension.put("resolution", resolutions);
            arg.put("dimension", dimension);
        }

        // Handle array/list types - check if the type string contains collection indicators
        String typeStr = arg.getString("type");
        if (typeStr.contains("[]") || typeStr.startsWith("List<")) {
            if (!arg.has("dimension")) {
                // For simple arrays/lists without specific dimension requirements
                // we don't add a dimension field - the type itself indicates it's a collection
            }
        }
    }

    /**
     * Add constraints to arguments based on their name and type
     */
    private void addArgumentConstraints(JSONObject arg, Input<?> input, Class<?> clazz) {
        String className = clazz.getSimpleName();
        String argName = input.getName();
        String fullKey = className + "." + argName;

        // Known constraints
        if (fullKey.equals("Frequencies.frequencies")) {
            arg.put("constraint", "simplex");
        } else if (argName.toLowerCase().contains("rate") || argName.toLowerCase().contains("scale")) {
            arg.put("constraint", "positive");
        } else if (argName.toLowerCase().contains("probability") || argName.toLowerCase().contains("prob")) {
            arg.put("constraint", "probability");
        }
        // Add more constraint rules as needed
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

    /**
     * Capitalize first letter of a string
     */
    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * Test for closure - verify that all argument types are either primitives or components in the library
     */
    public void testClosure(String jsonOutput) {
        try {
            JSONObject schema = new JSONObject(jsonOutput);
            JSONObject modelLibrary = schema.getJSONObject("modelLibrary");
            JSONArray components = modelLibrary.getJSONArray("components");

            // Build set of all component names (both simple and fully qualified)
            Set<String> componentNames = new HashSet<>();
            Map<String, String> simpleToFullyQualified = new HashMap<>();

            for (int i = 0; i < components.length(); i++) {
                JSONObject component = components.getJSONObject(i);
                String name = component.getString("name");
                String fqn = component.getString("fullyQualifiedName");
                componentNames.add(name);
                componentNames.add(fqn);
                simpleToFullyQualified.put(name, fqn);

                // Also add just the class name without package for interfaces
                if (fqn.contains(".")) {
                    String simpleName = fqn.substring(fqn.lastIndexOf('.') + 1);
                    componentNames.add(simpleName);
                }
            }

            // Known primitive types
            Set<String> primitiveTypes = new HashSet<>(Arrays.asList(
                    "Integer", "Double", "String", "Boolean", "int", "double", "boolean",
                    "Long", "Float", "Object", "T", "Map", "File"
            ));

            // Known inference types that we intentionally exclude from the model library
            Set<String> inferenceTypes = new HashSet<>(Arrays.asList(
                    "Logger", "Operator", "MCMC", "OperatorSchedule", "Runnable", "DistanceProvider"
            ));

            // Known GUI/BEAUti types that we intentionally exclude
            Set<String> guiTypes = new HashSet<>(Arrays.asList(
                    "LogFile", "TreeFile", "OutFile", "BeautiSubTemplate"
            ));

            // Track missing types
            Set<String> missingTypes = new TreeSet<>();
            Set<String> inferenceRelatedTypes = new TreeSet<>();
            Set<String> guiRelatedTypes = new TreeSet<>();
            Map<String, Set<String>> typeUsage = new HashMap<>();

            // Check all argument types
            for (int i = 0; i < components.length(); i++) {
                JSONObject component = components.getJSONObject(i);
                String componentName = component.getString("name");

                // Check primary argument if it exists
                if (component.has("primaryArgument")) {
                    JSONObject primaryArg = component.getJSONObject("primaryArgument");
                    checkArgumentType(primaryArg, componentName, "primaryArgument",
                            componentNames, primitiveTypes, inferenceTypes, guiTypes,
                            missingTypes, inferenceRelatedTypes, guiRelatedTypes, typeUsage);
                }

                // Check all arguments
                if (component.has("arguments")) {
                    JSONArray arguments = component.getJSONArray("arguments");
                    for (int j = 0; j < arguments.length(); j++) {
                        JSONObject arg = arguments.getJSONObject(j);
                        checkArgumentType(arg, componentName, arg.getString("name"),
                                componentNames, primitiveTypes, inferenceTypes, guiTypes,
                                missingTypes, inferenceRelatedTypes, guiRelatedTypes, typeUsage);
                    }
                }
            }

            // Report results
            System.out.println("\n=== CLOSURE TEST RESULTS ===");
            System.out.println("Total components: " + components.length());
            System.out.println("Total unique types referenced: " + typeUsage.size());

            boolean hasErrors = !missingTypes.isEmpty();
            boolean hasWarnings = !inferenceRelatedTypes.isEmpty() || !guiRelatedTypes.isEmpty();

            if (!hasErrors && !hasWarnings) {
                System.out.println("✓ CLOSURE TEST PASSED: All argument types are defined!");
            } else {
                if (hasErrors) {
                    System.out.println("\n✗ MISSING MODEL TYPES: " + missingTypes.size() + " types are not defined:");
                    for (String missing : missingTypes) {
                        System.out.println("  - " + missing + " (used by: " +
                                String.join(", ", typeUsage.get(missing)) + ")");
                    }
                }

                if (!inferenceRelatedTypes.isEmpty()) {
                    System.out.println("\n⚠ INFERENCE-RELATED TYPES (excluded by design): " +
                            inferenceRelatedTypes.size() + " types");
                    for (String inferenceType : inferenceRelatedTypes) {
                        System.out.println("  - " + inferenceType + " (used by: " +
                                String.join(", ", typeUsage.get(inferenceType)) + ")");
                    }
                }

                if (!guiRelatedTypes.isEmpty()) {
                    System.out.println("\n⚠ GUI/BEAUTI-RELATED TYPES (excluded by design): " +
                            guiRelatedTypes.size() + " types");
                    for (String guiType : guiRelatedTypes) {
                        System.out.println("  - " + guiType + " (used by: " +
                                String.join(", ", typeUsage.get(guiType)) + ")");
                    }
                }

                if (hasWarnings) {
                    // List which components were excluded due to using inference/GUI types
                    System.out.println("\nComponents excluded from model library:");
                    Set<String> excludedComponents = new TreeSet<>();
                    for (String type : inferenceRelatedTypes) {
                        for (String usage : typeUsage.get(type)) {
                            String componentName = usage.substring(0, usage.lastIndexOf('.'));
                            excludedComponents.add(componentName + " (uses inference type)");
                        }
                    }
                    for (String type : guiRelatedTypes) {
                        for (String usage : typeUsage.get(type)) {
                            String componentName = usage.substring(0, usage.lastIndexOf('.'));
                            excludedComponents.add(componentName + " (uses GUI type)");
                        }
                    }
                    for (String excluded : excludedComponents) {
                        System.out.println("  - " + excluded);
                    }
                }

                // Suggest which types might need to be added
                if (hasErrors) {
                    System.out.println("\nSuggestions for missing model types:");
                    for (String missing : missingTypes) {
                        if (missing.contains(".")) {
                            System.out.println("  - " + missing + " might be an inner class or from another package");
                        } else {
                            System.out.println("  - " + missing + " might need to be added to importantBaseTypes");
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.severe("Error running closure test: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check a single argument type for closure
     */
    private void checkArgumentType(JSONObject arg, String componentName, String argName,
                                   Set<String> componentNames, Set<String> primitiveTypes,
                                   Set<String> inferenceTypes, Set<String> guiTypes,
                                   Set<String> missingTypes, Set<String> inferenceRelatedTypes,
                                   Set<String> guiRelatedTypes, Map<String, Set<String>> typeUsage) {
        String type = arg.getString("type");
        String baseType = extractBaseType(type);

        // Track usage
        typeUsage.computeIfAbsent(baseType, k -> new TreeSet<>())
                .add(componentName + "." + argName);

        // Check if type is defined
        if (!primitiveTypes.contains(baseType) && !componentNames.contains(baseType)) {
            // Check if it's an inference-related type
            if (inferenceTypes.contains(baseType) ||
                    baseType.contains("Operator") ||
                    baseType.contains("Logger") ||
                    baseType.equals("MCMC") ||
                    baseType.contains("OperatorSchedule")) {
                inferenceRelatedTypes.add(baseType);
            }
            // Check if it's a GUI-related type
            else if (guiTypes.contains(baseType)) {
                guiRelatedTypes.add(baseType);
            }
            else {
                missingTypes.add(baseType);
            }
        }
    }

    /**
     * Extract base type from potentially complex type strings like List<Tree> or Double[]
     */
    private String extractBaseType(String type) {
        // Handle array types
        if (type.endsWith("[]")) {
            return type.substring(0, type.length() - 2);
        }

        // Handle List<T> types
        if (type.startsWith("List<") && type.endsWith(">")) {
            return type.substring(5, type.length() - 1);
        }

        // Handle other generic types if needed
        int genericStart = type.indexOf('<');
        if (genericStart > 0) {
            return type.substring(0, genericStart);
        }

        return type;
    }

    /**
     * Main method to generate schema and test closure
     */
    public static void main(String[] args) {
        try {
            BEAST2ModelLibraryGenerator generator = new BEAST2ModelLibraryGenerator();
            String jsonOutput = generator.generateModelLibrary();

            // Write to file
            java.nio.file.Files.write(
                    java.nio.file.Paths.get("beast2-model-library.json"),
                    jsonOutput.getBytes()
            );

            // Test closure
            generator.testClosure(jsonOutput);

        } catch (Exception e) {
            logger.severe("Error generating model library: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
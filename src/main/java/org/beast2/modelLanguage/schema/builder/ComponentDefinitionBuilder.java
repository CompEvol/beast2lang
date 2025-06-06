package org.beast2.modelLanguage.schema.builder;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.inference.distribution.ParametricDistribution;
import org.beast2.modelLanguage.beast.BeastObjectFactory;
import org.beast2.modelLanguage.schema.core.ComponentInfo;
import org.beast2.modelLanguage.schema.core.TypeResolver;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Builds component definitions from ComponentInfo objects
 */
public class ComponentDefinitionBuilder {
    private static final Logger logger = Logger.getLogger(ComponentDefinitionBuilder.class.getName());
    
    private final TypeResolver typeResolver;
    private final ArgumentBuilder argumentBuilder;
    private final PropertyExtractor propertyExtractor;
    private final BeastObjectFactory factory;
    
    public ComponentDefinitionBuilder(TypeResolver typeResolver,
                                    ArgumentBuilder argumentBuilder,
                                    PropertyExtractor propertyExtractor,
                                    BeastObjectFactory factory) {
        this.typeResolver = typeResolver;
        this.argumentBuilder = argumentBuilder;
        this.propertyExtractor = propertyExtractor;
        this.factory = factory;
    }
    
    /**
     * Build a component definition from ComponentInfo
     */
    public JSONObject buildDefinition(ComponentInfo component) {
        JSONObject definition = new JSONObject();
        Class<?> clazz = component.getClazz();
        
        // Basic metadata
        definition.put("name", typeResolver.getSimpleClassName(clazz));
        definition.put("fullyQualifiedName", clazz.getName());
        definition.put("isDistribution", component.isDistribution());
        definition.put("isAbstract", component.isAbstract());
        definition.put("isInterface", component.isInterface());
        definition.put("isEnum", component.isEnum());
        definition.put("package", component.getPackageName());
        definition.put("description", component.getDescription());
        
        // Add inheritance information
        addInheritanceInfo(definition, clazz);
        
        // Add component-specific details
        if (component.isEnum()) {
            addEnumDetails(definition, clazz);
        } else if (!clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers())) {
            addConcreteClassDetails(definition, clazz, component.isDistribution());
        } else {
            // For interfaces and abstract classes
            definition.put("arguments", new JSONArray());
            definition.put("properties", new JSONArray());
        }
        
        return definition;
    }
    
    /**
     * Add inheritance information
     */
    private void addInheritanceInfo(JSONObject definition, Class<?> clazz) {
        // Superclass
        if (clazz.getSuperclass() != null && BEASTInterface.class.isAssignableFrom(clazz.getSuperclass())) {
            definition.put("extends", clazz.getSuperclass().getName());
        }
        
        // Interfaces
        JSONArray interfaces = new JSONArray();
        Set<String> allInterfaces = new HashSet<>();
        collectAllInterfaces(clazz, allInterfaces);
        
        for (String interfaceName : allInterfaces) {
            interfaces.put(interfaceName);
        }
        definition.put("implements", interfaces);
    }
    
    /**
     * Recursively collect all interfaces implemented by a class
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
     * Add details for enum types
     */
    private void addEnumDetails(JSONObject definition, Class<?> enumClass) {
        JSONArray properties = new JSONArray();
        JSONObject valuesProperty = new JSONObject();
        valuesProperty.put("name", "values");
        valuesProperty.put("type", "String[]");
        valuesProperty.put("access", "read-only");
        
        // Get enum constants
        Object[] constants = enumClass.getEnumConstants();
        if (constants != null) {
            JSONArray values = new JSONArray();
            for (Object constant : constants) {
                values.put(constant.toString());
            }
            valuesProperty.put("value", values);
        }
        properties.put(valuesProperty);
        
        definition.put("properties", properties);
        definition.put("arguments", new JSONArray());
    }
    
    /**
     * Add details for concrete classes
     */
    private void addConcreteClassDetails(JSONObject definition, Class<?> clazz, boolean isDistribution) {
        if (BEASTInterface.class.isAssignableFrom(clazz)) {
            try {
                BEASTInterface instance = (BEASTInterface) clazz.getDeclaredConstructor().newInstance();
                
                if (isDistribution) {
                    addDistributionDetails(definition, instance, clazz);
                } else {
                    addNonDistributionDetails(definition, instance, clazz);
                }
                
                // Add properties
                JSONArray properties = propertyExtractor.extractProperties(clazz);
                definition.put("properties", properties);
                
            } catch (Exception e) {
                logger.fine("Can't instantiate " + clazz.getSimpleName() + ": " + e.getMessage());
                // Can't instantiate, but still include with empty arrays
                definition.put("arguments", new JSONArray());
                definition.put("properties", new JSONArray());
            }
        } else {
            // Non-BEASTInterface concrete class
            definition.put("arguments", new JSONArray());
            definition.put("properties", propertyExtractor.extractProperties(clazz));
        }
    }
    
    /**
     * Add distribution-specific details
     */
    private void addDistributionDetails(JSONObject definition, BEASTInterface instance, Class<?> clazz) {
        String primaryInputName = factory.getPrimaryInputName(instance);
        boolean isParametricDistribution = ParametricDistribution.class.isAssignableFrom(clazz);
        
        // Handle primary argument
        if (primaryInputName != null) {
            JSONObject primaryArg = argumentBuilder.buildPrimaryArgument(instance, primaryInputName);
            if (primaryArg != null) {
                definition.put("primaryArgument", primaryArg);
            }
        } else if (isParametricDistribution) {
            // For ParametricDistributions, create synthetic primary argument
            definition.put("primaryArgument", argumentBuilder.createSyntheticPrimaryArgument());
        }
        
        // Add other arguments (non-primary inputs)
        JSONArray arguments = new JSONArray();
        for (Input<?> input : instance.listInputs()) {
            if (!input.getName().equals(primaryInputName)) {
                JSONObject arg = argumentBuilder.buildArgument(input, instance, clazz);
                arguments.put(arg);
            }
        }
        definition.put("arguments", arguments);
    }
    
    /**
     * Add non-distribution details
     */
    private void addNonDistributionDetails(JSONObject definition, BEASTInterface instance, Class<?> clazz) {
        JSONArray arguments = new JSONArray();
        for (Input<?> input : instance.listInputs()) {
            JSONObject arg = argumentBuilder.buildArgument(input, instance, clazz);
            arguments.put(arg);
        }
        definition.put("arguments", arguments);
    }
}
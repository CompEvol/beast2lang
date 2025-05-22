package org.beast2.modelLanguage.builder.handlers;

import org.beast2.modelLanguage.builder.NameResolver;
import org.beast2.modelLanguage.builder.util.AutoboxingRegistry;
import org.beast2.modelLanguage.model.*;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Handler for VariableDeclaration statements, responsible for creating model objects.
 */
public class VariableDeclarationHandler extends BaseHandler {

    /**
     * Constructor
     */
    public VariableDeclarationHandler() {
        super(VariableDeclarationHandler.class.getName());
    }

    public Object createObject(VariableDeclaration varDecl, Map<String, Object> objectRegistry) throws Exception {
        String declaredTypeName = varDecl.getClassName();
        String variableName = varDecl.getVariableName();
        Expression value = varDecl.getValue();

        // Handle array literals
        if (value instanceof ArrayLiteral) {
            return handleArrayLiteral(declaredTypeName, variableName, (ArrayLiteral) value, objectRegistry);
        }

        // Handle literal values directly
        if (value instanceof Literal) {
            Object literalValue = ((Literal) value).getValue();
            try {
                Class<?> declaredType = loadClass(declaredTypeName);
                // Use AutoboxingRegistry instead of direct Parameter handling
                return AutoboxingRegistry.getInstance().autobox(literalValue, declaredType, objectRegistry);
            } catch (ClassNotFoundException e) {
                logger.warning("Class not found: " + declaredTypeName);
                return ((Literal) value).getValue();
            }
        }

        // Handle nexus function calls
        if (value instanceof NexusFunction) {
            return handleNexusFunction(declaredTypeName, variableName, (NexusFunction) value, objectRegistry);
        }

        // Handle function calls
        if (!(value instanceof FunctionCall funcCall)) {
            throw new IllegalArgumentException("Value must be a function call, literal, array literal, or nexus function");
        }

        String implementationClassName = funcCall.getClassName();

        // Load and check classes
        Class<?> declaredType = loadClass(declaredTypeName);
        Class<?> implementationClass = loadClass(implementationClassName);

        if (!declaredType.isAssignableFrom(implementationClass)) {
            throw new ClassCastException(
                    implementationClassName + " is not assignable to " + declaredTypeName
            );
        }

        // Create and configure the object using factory
        Object modelObject = factory.createObject(implementationClassName, variableName);

        // Configure using factory method
        factory.configureFromFunctionCall(modelObject, funcCall, objectRegistry);
        factory.initAndValidate(modelObject);

        return modelObject;
    }

    /**
     * Handle nexus function calls to load alignments from Nexus files
     */
    private Object handleNexusFunction(String declaredTypeName, String variableName,
                                       NexusFunction nexusFunction, Map<String, Object> objectRegistry) throws Exception {
        // Check if the declared type is compatible with Alignment
        Class<?> declaredType;
        try {
            declaredType = loadClass(declaredTypeName);
            // Check if it's an alignment type - we'll need to add a method to factory for this
            // For now, we'll just proceed
        } catch (ClassNotFoundException e) {
            logger.warning("Class not found: " + declaredTypeName);
            throw new ClassNotFoundException("Declared type not found: " + declaredTypeName);
        }

        // Use the NexusFunctionHandler to process the nexus function
        NexusFunctionHandler handler = new NexusFunctionHandler();
        Object alignment = handler.processFunction(nexusFunction, objectRegistry);

        // Set the ID if it wasn't set by the handler
        try {
            String currentId = factory.getID(alignment);
            if (currentId == null || currentId.isEmpty()) {
                factory.setID(alignment, variableName);
            }
        } catch (Exception e) {
            logger.warning("Could not set ID on alignment: " + e.getMessage());
        }

        // Store the object in the registry with the variable name
        objectRegistry.put(variableName, alignment);

        logger.info("Created alignment from Nexus file: " + variableName);
        return alignment;
    }

    /**
     * Handle array literal expressions and create appropriate arrays
     */
    private Object handleArrayLiteral(String declaredTypeName, String variableName,
                                      ArrayLiteral arrayLiteral, Map<String, Object> objectRegistry) throws Exception {
        // Check if this is an array type
        if (!declaredTypeName.endsWith("[]")) {
            throw new IllegalArgumentException("Expected array type for array literal, got: " + declaredTypeName);
        }

        // Get the component type name (remove the [] suffix)
        String componentTypeName = declaredTypeName.substring(0, declaredTypeName.length() - 2);

        // Use NameResolver instance to resolve the component type name
        NameResolver resolver = new NameResolver();
        String resolvedComponentTypeName = resolver.resolveClassName(componentTypeName);

        try {
            // Load the component class using the resolved name
            Class<?> componentClass = loadClass(resolvedComponentTypeName);

            // Create array of the proper component type
            int length = arrayLiteral.getElements().size();
            Object array = Array.newInstance(componentClass, length);

            // Fill the array with resolved values
            for (int i = 0; i < length; i++) {
                Expression elem = arrayLiteral.getElements().get(i);
                Object resolvedValue = ExpressionResolver.resolveValue(elem, objectRegistry);

                // For primitive component types, we need additional conversion
                if (componentClass.isPrimitive()) {
                    if (componentClass == int.class) {
                        Array.setInt(array, i, factory.convertToInteger(resolvedValue));
                    } else if (componentClass == double.class) {
                        Array.setDouble(array, i, factory.convertToDouble(resolvedValue));
                    } else if (componentClass == boolean.class) {
                        Array.setBoolean(array, i, factory.convertToBoolean(resolvedValue));
                    } else if (componentClass == byte.class) {
                        Array.setByte(array, i, (byte) factory.convertToInteger(resolvedValue));
                    } else if (componentClass == char.class) {
                        char charValue = resolvedValue != null ? resolvedValue.toString().charAt(0) : '\0';
                        Array.setChar(array, i, charValue);
                    } else if (componentClass == short.class) {
                        Array.setShort(array, i, (short) factory.convertToInteger(resolvedValue));
                    } else if (componentClass == long.class) {
                        Array.setLong(array, i, (long) factory.convertToInteger(resolvedValue));
                    } else if (componentClass == float.class) {
                        Array.setFloat(array, i, (float) factory.convertToDouble(resolvedValue));
                    }
                } else {
                    // For object types, check if the value is assignable to the component type
                    if (resolvedValue != null && !componentClass.isAssignableFrom(resolvedValue.getClass())) {
                        // Try autoboxing
                        resolvedValue = AutoboxingRegistry.getInstance().autobox(resolvedValue, componentClass, objectRegistry);
                    }

                    // Set the array element
                    Array.set(array, i, resolvedValue);
                }
            }

            // Store the array in the object registry and return it
            objectRegistry.put(variableName, array);
            return array;
        } catch (ClassNotFoundException e) {
            throw new ClassNotFoundException("Could not find component class: " + componentTypeName +
                    " (resolved as " + resolvedComponentTypeName + ")", e);
        }
    }
}
package org.beast2.modelLanguage.builder.handlers;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.inference.parameter.Parameter;
import org.beast2.modelLanguage.builder.NameResolver;
import org.beast2.modelLanguage.builder.util.AutoboxingRegistry;
import org.beast2.modelLanguage.builder.util.BEASTUtils;
import org.beast2.modelLanguage.model.*;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Handler for VariableDeclaration statements, responsible for creating BEAST2 objects.
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

        // Handle function calls
        if (!(value instanceof FunctionCall)) {
            throw new IllegalArgumentException("Value must be a function call, literal, or array literal");
        }

        FunctionCall funcCall = (FunctionCall) value;
        String implementationClassName = funcCall.getClassName();

        // Load and check classes
        Class<?> declaredType = loadClass(declaredTypeName);
        Class<?> implementationClass = loadClass(implementationClassName);

        if (!declaredType.isAssignableFrom(implementationClass)) {
            throw new ClassCastException(
                    implementationClassName + " is not assignable to " + declaredTypeName
            );
        }

        // Create and configure the object
        Object beastObject = instantiateClass(implementationClass);
        BEASTUtils.setObjectId(beastObject, implementationClass, variableName);
        Map<String, Input<?>> inputMap = BEASTUtils.buildInputMap(beastObject, implementationClass);
        configureInputs(beastObject, funcCall, inputMap, objectRegistry);
        BEASTUtils.callInitAndValidate(beastObject, implementationClass);

        return beastObject;
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
        NameResolver resolver = new NameResolver();  // Use an empty resolver - it will use fallbacks
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
                        Array.setInt(array, i, BEASTUtils.convertToInteger(resolvedValue));
                    } else if (componentClass == double.class) {
                        Array.setDouble(array, i, BEASTUtils.convertToDouble(resolvedValue));
                    } else if (componentClass == boolean.class) {
                        Array.setBoolean(array, i, BEASTUtils.convertToBoolean(resolvedValue));
                    } else if (componentClass == byte.class) {
                        Array.setByte(array, i, (byte) BEASTUtils.convertToInteger(resolvedValue));
                    } else if (componentClass == char.class) {
                        char charValue = resolvedValue != null ? resolvedValue.toString().charAt(0) : '\0';
                        Array.setChar(array, i, charValue);
                    } else if (componentClass == short.class) {
                        Array.setShort(array, i, (short) BEASTUtils.convertToInteger(resolvedValue));
                    } else if (componentClass == long.class) {
                        Array.setLong(array, i, (long) BEASTUtils.convertToInteger(resolvedValue));
                    } else if (componentClass == float.class) {
                        Array.setFloat(array, i, (float) BEASTUtils.convertToDouble(resolvedValue));
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

    /**
     * Configure inputs for the BEAST object
     */
    private void configureInputs(Object object, FunctionCall funcCall, Map<String, Input<?>> inputMap,
                                 Map<String, Object> objectRegistry) throws Exception {
        if (!(object instanceof BEASTInterface)) {
            return;
        }

        BEASTInterface beastObject = (BEASTInterface) object;

        for (Argument arg : funcCall.getArguments()) {
            String name = arg.getName();
            Input<?> input = inputMap.get(name);

            if (input == null) {
                throw new RuntimeException("No input named '" + name + "' found");
            }

            // Resolve the value (without autoboxing first)
            Object resolvedValue = ExpressionResolver.resolveValue(arg.getValue(), objectRegistry);

            Type expectedType = BEASTUtils.getInputExpectedType(input,beastObject,name);

            // Apply autoboxing if needed
            Object autoboxedValue = AutoboxingRegistry.getInstance().autobox(resolvedValue, expectedType, objectRegistry);

            // Set the input value
            try {
                BEASTUtils.setInputValue(input, autoboxedValue, beastObject);
            } catch (Exception e) {
                logger.warning("Error setting input '" + name + "': " + e.getMessage());
                throw e;
            }
        }
    }
}
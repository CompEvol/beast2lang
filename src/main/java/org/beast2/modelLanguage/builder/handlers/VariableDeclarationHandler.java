package org.beast2.modelLanguage.builder.handlers;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.inference.parameter.Parameter;
import org.beast2.modelLanguage.builder.util.AutoboxingRegistry;
import org.beast2.modelLanguage.builder.util.BEASTUtils;
import org.beast2.modelLanguage.model.*;

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
            ArrayLiteral arrayLiteral = (ArrayLiteral) value;

            // Handle String[] array
            if (declaredTypeName.equals("String[]")) {
                String[] stringArray = new String[arrayLiteral.getElements().size()];
                for (int i = 0; i < arrayLiteral.getElements().size(); i++) {
                    Expression elem = arrayLiteral.getElements().get(i);
                    if (elem instanceof Literal) {
                        stringArray[i] = ((Literal) elem).getValue().toString();
                    } else {
                        Object resolvedValue = ExpressionResolver.resolveValue(elem, objectRegistry);
                        stringArray[i] = resolvedValue != null ? resolvedValue.toString() : null;
                    }
                }
                objectRegistry.put(variableName, stringArray);
                return stringArray;
            }

            // Add support for other array types as needed
            throw new UnsupportedOperationException("Array type not supported: " + declaredTypeName);
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
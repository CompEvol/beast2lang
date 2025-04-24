package org.beast2.modelLanguage.builder.handlers;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.inference.parameter.Parameter;
import org.beast2.modelLanguage.builder.util.BEASTUtils;
import org.beast2.modelLanguage.model.*;

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

    /**
     * Create a BEAST2 object from a variable declaration
     *
     * @param varDecl the variable declaration to process
     * @param objectRegistry registry of created objects for reference resolution
     * @return the created BEAST2 object
     * @throws Exception if object creation fails
     */
    public Object createObject(VariableDeclaration varDecl, Map<String, Object> objectRegistry) throws Exception {
        String declaredTypeName = varDecl.getClassName();
        String variableName = varDecl.getVariableName();
        Expression value = varDecl.getValue();

        // Handle literal values for Parameter types
        if (value instanceof Literal) {
            try {
                Class<?> declaredType = loadClass(declaredTypeName);
                if (Parameter.class.isAssignableFrom(declaredType)) {
                    return ParameterAutoboxer.literalToParameter((Literal) value, variableName);
                }
            } catch (ClassNotFoundException e) {
                // If class not found, continue with normal processing
            }
        }

        // Handle function calls
        if (!(value instanceof FunctionCall)) {
            throw new IllegalArgumentException("Value must be a function call or a literal for Parameter types");
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

            // Get expected type
            Class<?> expectedType = input.getType();

            // Resolve the value based on the expected type
            Object resolvedValue = resolveValueForInput(arg.getValue(), objectRegistry, expectedType);

            // Set the input value
            try {
                BEASTUtils.setInputValue(input, resolvedValue, beastObject);
            } catch (Exception e) {
                // Try parameter conversion as fallback for type mismatches
                if (resolvedValue != null) {
                    Object paramValue = BEASTUtils.createRealParameter(resolvedValue);
                    BEASTUtils.setInputValue(input, paramValue, beastObject);
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * Resolve a value based on its expression and expected type
     */
    private Object resolveValueForInput(Expression expr, Map<String, Object> objectRegistry,
                                        Class<?> expectedType) {
        // Handle literal values that need conversion
        if (expr instanceof Literal && expectedType != null) {
            Literal literal = (Literal) expr;
            Object value = literal.getValue();

            if (BEASTUtils.isParameterType(expectedType)) {
                return BEASTUtils.createParameterForType(value, expectedType);
            }

            return value;
        }

        // Use standard resolver for other cases
        return ExpressionResolver.resolveValue(expr, objectRegistry);
    }
}
package org.beast2.modelLanguage.builder.handlers;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import org.beast2.modelLanguage.builder.util.BEASTUtils;
import org.beast2.modelLanguage.model.*;

import java.util.Map;

/**
 * Handler for VariableDeclaration statements, responsible for creating BEAST2 objects.
 * Refactored to use centralized utility methods.
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

        logger.info("Creating object with name: " + variableName + " of declared type: " + declaredTypeName);

        // Handle autoboxing for literals when the declared type is a Parameter
        if (value instanceof Literal && BEASTUtils.isParameterType(declaredTypeName)) {
            return ParameterAutoboxer.literalToParameter((Literal) value, variableName);
        }

        // Continue with existing implementation for function calls
        if (!(value instanceof FunctionCall)) {
            throw new IllegalArgumentException("Right side of variable declaration must be a function call or a literal value for Parameters");
        }

        FunctionCall funcCall = (FunctionCall) value;
        String implementationClassName = funcCall.getClassName();

        // 1) Load classes
        Class<?> declaredType = loadClass(declaredTypeName);
        Class<?> implementationClass = loadClass(implementationClassName);

        if (!declaredType.isAssignableFrom(implementationClass)) {
            throw new ClassCastException(
                    implementationClassName + " is not assignable to " + declaredTypeName
            );
        }

        // 2) Instantiate
        Object beastObject = instantiateClass(implementationClass);
        logger.info("Successfully instantiated " + implementationClassName);

        // 3) setID if present
        BEASTUtils.setObjectId(beastObject, implementationClass, variableName);

        // 4) Build input map
        Map<String, Input<?>> inputMap = BEASTUtils.buildInputMap(beastObject, implementationClass);

        // 5) CONFIGURE all arguments
        configureInputs(beastObject, funcCall, inputMap, objectRegistry);

        // 6) Call initAndValidate
        BEASTUtils.callInitAndValidate(beastObject, implementationClass);

        return beastObject;
    }

    /**
     * Configure inputs for the BEAST object
     */
    private void configureInputs(Object object, FunctionCall funcCall, Map<String, Input<?>> inputMap,
                                 Map<String, Object> objectRegistry) throws Exception {
        // Print information about all inputs for debugging
        logger.info("Available inputs for " + object.getClass().getName() + ":");
        for (Map.Entry<String, Input<?>> entry : inputMap.entrySet()) {
            Class<?> type = entry.getValue().getType();
            logger.info("  Input: " + entry.getKey() + " Type: " + (type != null ? type.getName() : "null"));
        }

        for (Argument arg : funcCall.getArguments()) {
            String name = arg.getName();

            Input<?> input = inputMap.get(name);
            if (input == null) {
                throw new RuntimeException("No Input named '" + name + "' found");
            }

            // Get the expected type from the Input
            Class<?> expectedType = input.getType();
            logger.info("Processing input " + name + " with expected type: " + (expectedType != null ? expectedType.getName() : "null"));

            // Handle literal values that need to be converted to Parameter objects
            Object rawValue;
            if (arg.getValue() instanceof Literal && expectedType != null) {
                Literal literal = (Literal) arg.getValue();
                Object literalValue = literal.getValue();
                logger.info("Converting literal value: " + literalValue + " to expected type: " + expectedType.getName());

                // Check if input expects a Parameter type
                if (BEASTUtils.isParameterType(expectedType)) {
                    rawValue = BEASTUtils.createParameterForType(literalValue, expectedType);
                    logger.info("Created Parameter for input " + name);
                } else {
                    // For other expected types, just pass the literal value
                    rawValue = literalValue;
                }
            } else {
                // Use standard resolver for non-literals or when expected type is unknown
                rawValue = ExpressionResolver.resolveValue(arg.getValue(), objectRegistry);
            }

            logger.fine("Setting input " + name + " to " + rawValue);

            // Set the input value - this is where the type mismatch can occur
            try {
                BEASTUtils.setInputValue(input, rawValue, (BEASTInterface) object);
            } catch (Exception e) {
                logger.warning("Error setting input " + name + ": " + e.getMessage());
                // If this is a type mismatch and we have a raw value, try creating a Parameter
                if (e.getMessage() != null && e.getMessage().contains("type mismatch") && rawValue != null) {
                    logger.info("Trying to convert " + rawValue + " to Parameter for input " + name);

                    // Try to create a Parameter object as a last resort
                    Object paramValue = BEASTUtils.createRealParameter(rawValue);
                    BEASTUtils.setInputValue(input, paramValue, (BEASTInterface) object);
                    logger.info("Successfully set input " + name + " using Parameter conversion");
                } else {
                    // Re-throw if we can't handle it
                    throw e;
                }
            }
        }
    }
}
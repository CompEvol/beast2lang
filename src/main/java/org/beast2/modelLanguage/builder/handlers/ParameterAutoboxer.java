package org.beast2.modelLanguage.builder.handlers;

import org.beast2.modelLanguage.builder.util.BEASTUtils;
import org.beast2.modelLanguage.model.Literal;
import org.beast2.modelLanguage.model.Literal.LiteralType;

import beast.base.inference.parameter.Parameter;

import java.util.logging.Logger;

/**
 * Handles conversion from Literal values to appropriate Parameter objects
 * for autoboxing in the BEAST2 model language.
 * Enhanced to better handle numeric conversion for distribution model inputs.
 * Refactored to use centralized utility methods.
 */
public class ParameterAutoboxer {

    private static final Logger logger = Logger.getLogger(ParameterAutoboxer.class.getName());

    /**
     * Convert a Literal to the appropriate Parameter type based on the Literal's type
     *
     * @param literal the literal value to convert
     * @param parameterName optional parameter name (ID) to assign to the created Parameter
     * @return a Parameter object containing the literal value
     * @throws IllegalArgumentException if the Literal type is unsupported
     */
    public static Parameter<?> literalToParameter(Literal literal, String parameterName) {
        LiteralType type = literal.getType();
        Object value = literal.getValue();
        Parameter.Base<?> parameter;

        logger.fine("Autoboxing " + value + " of type " + type + " to Parameter");

        try {
            // Use centralized parameter creation logic
            parameter = (Parameter.Base<?>) BEASTUtils.createParameterForType(value, null);

            // Set the parameter ID if a name was provided
            if (parameterName != null && !parameterName.isEmpty()) {
                parameter.setID(parameterName);
            }

            return parameter;
        } catch (Exception e) {
            logger.severe("Error autoboxing literal to Parameter: " + e.getMessage());
            throw new RuntimeException("Failed to autobox literal to Parameter", e);
        }
    }

    /**
     * Convert a Literal to the appropriate Parameter type
     *
     * @param literal the literal value to convert
     * @param parameterName optional parameter name (ID) to assign to the created Parameter
     * @param expectedType the expected Parameter type (if known)
     * @return a Parameter object containing the literal value
     */
    public static Parameter<?> literalToParameter(Literal literal, String parameterName, Class<?> expectedType) {
        Object value = literal.getValue();
        Parameter.Base<?> parameter;

        try {
            // Use centralized parameter creation logic
            parameter = (Parameter.Base<?>) BEASTUtils.createParameterForType(value, expectedType);

            // Set the parameter ID if a name was provided
            if (parameterName != null && !parameterName.isEmpty()) {
                parameter.setID(parameterName);
            }

            return parameter;
        } catch (Exception e) {
            logger.severe("Error autoboxing literal to Parameter: " + e.getMessage());
            throw new RuntimeException("Failed to autobox literal to Parameter for expected type " +
                    (expectedType != null ? expectedType.getSimpleName() : "unknown"), e);
        }
    }

    /**
     * Convert a literal value to a simple object (not a Parameter)
     * based on expected type. Useful for direct inputs that don't need Parameters.
     *
     * @param literal the literal value to convert
     * @param expectedType the expected type (if known)
     * @return a converted object of appropriate type
     */
    public static Object literalToObject(Literal literal, Class<?> expectedType) {
        if (expectedType == null) {
            return literal.getValue(); // No conversion if no type info
        }

        LiteralType type = literal.getType();
        Object value = literal.getValue();

        try {
            // Handle primitive types
            if (expectedType == Double.class || expectedType == double.class) {
                return BEASTUtils.convertToDouble(value);
            } else if (expectedType == Integer.class || expectedType == int.class) {
                return BEASTUtils.convertToInteger(value);
            } else if (expectedType == Boolean.class || expectedType == boolean.class) {
                return BEASTUtils.convertToBoolean(value);
            } else if (expectedType == String.class) {
                return value.toString();
            } else if (Parameter.class.isAssignableFrom(expectedType)) {
                // If expected type is a Parameter, use the Parameter conversion
                return literalToParameter(literal, null, expectedType);
            }

            // If no conversion applied, return the original value
            return value;

        } catch (Exception e) {
            logger.warning("Error converting literal to " + expectedType.getSimpleName() + ": " + e.getMessage());
            return value; // Return original value on error
        }
    }

    /**
     * Convenience method without requiring a parameter name
     */
    public static Parameter<?> literalToParameter(Literal literal) {
        return literalToParameter(literal, null);
    }
}
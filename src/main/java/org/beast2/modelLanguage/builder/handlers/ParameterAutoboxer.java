package org.beast2.modelLanguage.builder.handlers;

import org.beast2.modelLanguage.builder.util.BEASTUtils;
import org.beast2.modelLanguage.model.Literal;
import beast.base.inference.parameter.Parameter;

/**
 * Handles conversion from Literal values to appropriate Parameter objects
 * for autoboxing in the BEAST2 model language.
 */
public class ParameterAutoboxer {

    /**
     * Convert a Literal to the appropriate Parameter type
     *
     * @param literal the literal value to convert
     * @param parameterName optional parameter name (ID) to assign to the created Parameter
     * @return a Parameter object containing the literal value
     */
    public static Parameter<?> literalToParameter(Literal literal, String parameterName) {
        Object value = literal.getValue();
        Parameter.Base<?> parameter = (Parameter.Base<?>) BEASTUtils.createParameterForType(value, null);

        // Set the parameter ID if provided
        if (parameterName != null && !parameterName.isEmpty()) {
            parameter.setID(parameterName);
        }

        return parameter;
    }

    /**
     * Convert a Literal to a Parameter with specified expected type
     */
    public static Parameter<?> literalToParameter(Literal literal, String parameterName, Class<?> expectedType) {
        Object value = literal.getValue();
        Parameter.Base<?> parameter = (Parameter.Base<?>) BEASTUtils.createParameterForType(value, expectedType);

        // Set the parameter ID if provided
        if (parameterName != null && !parameterName.isEmpty()) {
            parameter.setID(parameterName);
        }

        return parameter;
    }

    /**
     * Convert a literal value to a simple object (not a Parameter)
     * based on expected type.
     */
    public static Object literalToObject(Literal literal, Class<?> expectedType) {
        if (expectedType == null) {
            return literal.getValue();
        }

        Object value = literal.getValue();

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
            return literalToParameter(literal, null, expectedType);
        }

        // Return original value if no conversion applies
        return value;
    }

    /**
     * Convenience method without requiring a parameter name
     */
    public static Parameter<?> literalToParameter(Literal literal) {
        return literalToParameter(literal, null);
    }
}
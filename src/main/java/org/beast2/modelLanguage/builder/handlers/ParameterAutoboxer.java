package org.beast2.modelLanguage.builder.handlers;

import org.beast2.modelLanguage.model.Literal;
import org.beast2.modelLanguage.model.Literal.LiteralType;

import beast.base.inference.parameter.BooleanParameter;
import beast.base.inference.parameter.IntegerParameter;
import beast.base.inference.parameter.RealParameter;
import beast.base.inference.parameter.Parameter;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Handles conversion from Literal values to appropriate Parameter objects
 * for autoboxing in the BEAST2 model language.
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
        Parameter.Base<?> parameter = null;

        logger.fine("Autoboxing " + value + " of type " + type + " to Parameter");

        try {
            switch (type) {
                case INTEGER:
                    parameter = createIntegerParameter((Integer) value);
                    break;
                case FLOAT:
                    parameter = createRealParameter((Number) value);
                    break;
                case BOOLEAN:
                    parameter = createBooleanParameter((Boolean) value);
                    break;
                case STRING:
                    // Convert to RealParameter if the string is numeric
                    if (isNumeric((String) value)) {
                        double doubleValue = Double.parseDouble((String) value);
                        parameter = createRealParameter(doubleValue);
                    } else {
                        throw new IllegalArgumentException("Cannot convert String literal to Parameter: " + value);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported literal type for Parameter autoboxing: " + type);
            }

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
        Literal.LiteralType type = literal.getType();
        Object value = literal.getValue();
        Parameter.Base<?> parameter = null;

        try {
            // Determine which Parameter type to create
            boolean useRealParameter = (expectedType == null) ||
                    RealParameter.class.isAssignableFrom(expectedType);
            boolean useIntegerParameter = IntegerParameter.class.isAssignableFrom(expectedType);
            boolean useBooleanParameter = BooleanParameter.class.isAssignableFrom(expectedType);

            switch (type) {
                case INTEGER:
                    if (useIntegerParameter || !useRealParameter) {
                        parameter = createIntegerParameter((Integer) value);
                    } else {
                        parameter = createRealParameter(((Integer) value).doubleValue());
                    }
                    break;

                case FLOAT:
                    parameter = createRealParameter(((Number) value).doubleValue());
                    break;

                case BOOLEAN:
                    if (useBooleanParameter) {
                        parameter = createBooleanParameter((Boolean) value);
                    } else {
                        // Numeric representation of boolean (1.0/0.0) if not expecting BooleanParameter
                        double numValue = ((Boolean) value) ? 1.0 : 0.0;
                        parameter = createRealParameter(numValue);
                    }
                    break;

                case STRING:
                    // Try to convert strings to numeric parameters
                    if (isNumeric((String) value)) {
                        if (useIntegerParameter && isInteger((String) value)) {
                            parameter = createIntegerParameter(Integer.parseInt((String) value));
                        } else {
                            parameter = createRealParameter(Double.parseDouble((String) value));
                        }
                    } else if (isBoolean((String) value) && useBooleanParameter) {
                        parameter = createBooleanParameter(Boolean.parseBoolean((String) value));
                    } else {
                        throw new IllegalArgumentException("Cannot convert String literal to Parameter: " + value);
                    }
                    break;

                default:
                    throw new IllegalArgumentException("Unsupported literal type for Parameter autoboxing: " + type);
            }

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
     * Convenience method without requiring a parameter name
     */
    public static Parameter<?> literalToParameter(Literal literal) {
        return literalToParameter(literal, null);
    }

    /**
     * Create a RealParameter from a double value
     */
    private static RealParameter createRealParameter(Double value) {
        RealParameter param = new RealParameter();
        List<Double> values = new ArrayList<>();
        values.add(value);
        try {
            param.initByName("value", values);
            return param;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create RealParameter", e);
        }
    }

    /**
     * Check if a string can be parsed as an integer
     */
    private static boolean isInteger(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Check if a string represents a boolean value
     */
    private static boolean isBoolean(String str) {
        String lowerStr = str.toLowerCase();
        return lowerStr.equals("true") || lowerStr.equals("false");
    }

    /**
     * Create an IntegerParameter from an Integer value
     */
    private static IntegerParameter createIntegerParameter(Integer value) {
        IntegerParameter param = new IntegerParameter();
        List<Integer> values = new ArrayList<>();
        values.add(value);
        try {
            param.initByName("value", values);
            return param;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create IntegerParameter", e);
        }
    }

    /**
     * Create a RealParameter from a numeric value
     */
    private static RealParameter createRealParameter(Number value) {
        RealParameter param = new RealParameter();
        List<Double> values = new ArrayList<>();
        values.add(value.doubleValue());
        try {
            param.initByName("value", values);
            return param;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create RealParameter", e);
        }
    }

    /**
     * Create a BooleanParameter from a boolean value
     */
    private static BooleanParameter createBooleanParameter(Boolean value) {
        BooleanParameter param = new BooleanParameter();
        List<Boolean> values = new ArrayList<>();
        values.add(value);
        try {
            param.initByName("value", values);
            return param;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create BooleanParameter", e);
        }
    }

    /**
     * Check if a string can be parsed as a number
     */
    private static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
package org.beast2.modelLanguage.builder.util;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.inference.parameter.BooleanParameter;
import beast.base.inference.parameter.IntegerParameter;
import beast.base.inference.parameter.RealParameter;
import beast.base.inference.parameter.Parameter;
import org.beast2.modelLanguage.builder.handlers.ExpressionResolver;
import org.beast2.modelLanguage.model.Argument;
import org.beast2.modelLanguage.model.Expression;
import org.beast2.modelLanguage.model.FunctionCall;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Utility class for common BEAST2 operations.
 * Centralizes functionality previously duplicated across multiple handlers.
 */
public class BEASTUtils {

    private static final Logger logger = Logger.getLogger(BEASTUtils.class.getName());

    /**
     * Set the ID on a BEAST object
     */
    public static void setObjectId(Object object, Class<?> clazz, String id) {
        try {
            Method setId = clazz.getMethod("setID", String.class);
            setId.invoke(object, id);
        } catch (NoSuchMethodException e) {
            // Some classes might not have setID, which is fine
            logger.fine("No setID method found for " + clazz.getName());
        } catch (Exception e) {
            logger.warning("Failed to set ID on " + clazz.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Call initAndValidate method on a BEAST object if it exists
     */
    public static void callInitAndValidate(Object object, Class<?> clazz) {
        try {
            Method initMethod = clazz.getMethod("initAndValidate");
            logger.fine("Calling initAndValidate() on " + clazz.getName());
            initMethod.invoke(object);
        } catch (NoSuchMethodException e) {
            // Try initialize as fallback
            try {
                Method initMethod = clazz.getMethod("initialize");
                initMethod.invoke(object);
                logger.fine("Called initialize() on " + clazz.getName());
            } catch (NoSuchMethodException e2) {
                // No initialization method, which is fine for some objects
                logger.fine("No initialization method found for " + clazz.getName());
            } catch (Exception e2) {
                logger.warning("Failed to call initialize on " + clazz.getName() + ": " + e2.getMessage());
            }
        } catch (Exception e) {
            logger.warning("Failed to call initAndValidate on " + clazz.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Build a map of input names to Input objects for a BEAST object
     */
    public static Map<String, Input<?>> buildInputMap(Object object, Class<?> clazz) {
        Map<String, Input<?>> inputMap = new HashMap<>();

        for (Field field : clazz.getFields()) {
            if (Input.class.isAssignableFrom(field.getType())) {
                try {
                    @SuppressWarnings("unchecked")
                    Input<?> input = (Input<?>) field.get(object);
                    inputMap.put(input.getName(), input);
                    logger.fine("Found Input: " + input.getName() + " in " + clazz.getName());
                } catch (IllegalAccessException e) {
                    logger.warning("Failed to access Input field: " + field.getName());
                }
            }
        }

        return inputMap;
    }

    /**
     * Check if a type is a Parameter type
     */
    public static boolean isParameterType(Class<?> type) {
        if (type == null) return false;
        return Parameter.class.isAssignableFrom(type) ||
                type.getName().contains("Parameter");
    }

    /**
     * Check if a string represents a class name of a Parameter type
     */
    public static boolean isParameterType(String typeName) {
        if (typeName == null) return false;
        return typeName.endsWith("RealParameter") ||
                typeName.endsWith("IntegerParameter") ||
                typeName.endsWith("BooleanParameter") ||
                typeName.endsWith("Parameter");
    }

    /**
     * Create a RealParameter from a double value
     */
    public static RealParameter createRealParameter(Double value) {
        RealParameter param = new RealParameter();
        List<Double> values = new ArrayList<>();
        values.add(value != null ? value : 0.0);
        try {
            param.initByName("value", values);
            return param;
        } catch (Exception e) {
            logger.severe("Failed to create RealParameter: " + e.getMessage());
            throw new RuntimeException("Failed to create RealParameter", e);
        }
    }

    /**
     * Create an IntegerParameter from an integer value
     */
    public static IntegerParameter createIntegerParameter(Integer value) {
        IntegerParameter param = new IntegerParameter();
        List<Integer> values = new ArrayList<>();
        values.add(value != null ? value : 0);
        try {
            param.initByName("value", values);
            return param;
        } catch (Exception e) {
            logger.severe("Failed to create IntegerParameter: " + e.getMessage());
            throw new RuntimeException("Failed to create IntegerParameter", e);
        }
    }

    /**
     * Create a BooleanParameter from a boolean value
     */
    public static BooleanParameter createBooleanParameter(Boolean value) {
        BooleanParameter param = new BooleanParameter();
        List<Boolean> values = new ArrayList<>();
        values.add(value != null ? value : false);
        try {
            param.initByName("value", values);
            return param;
        } catch (Exception e) {
            logger.severe("Failed to create BooleanParameter: " + e.getMessage());
            throw new RuntimeException("Failed to create BooleanParameter", e);
        }
    }

    /**
     * Create a RealParameter from any value with appropriate type conversion
     */
    public static RealParameter createRealParameter(Object value) {
        double doubleValue = convertToDouble(value);
        return createRealParameter(doubleValue);
    }

    /**
     * Create an IntegerParameter from any value with appropriate type conversion
     */
    public static IntegerParameter createIntegerParameter(Object value) {
        int intValue = convertToInteger(value);
        return createIntegerParameter(intValue);
    }

    /**
     * Create a BooleanParameter from any value with appropriate type conversion
     */
    public static BooleanParameter createBooleanParameter(Object value) {
        boolean boolValue = convertToBoolean(value);
        return createBooleanParameter(boolValue);
    }

    /**
     * Convert any value to a double
     */
    public static double convertToDouble(Object value) {
        if (value == null) {
            return 0.0;
        }

        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof Boolean) {
            return ((Boolean) value) ? 1.0 : 0.0;
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                logger.warning("Cannot convert string '" + value + "' to double, using 0.0");
                return 0.0;
            }
        } else {
            logger.warning("Cannot convert " + value.getClass().getName() + " to double, using 0.0");
            return 0.0;
        }
    }

    /**
     * Convert any value to an integer
     */
    public static int convertToInteger(Object value) {
        if (value == null) {
            return 0;
        }

        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof Boolean) {
            return ((Boolean) value) ? 1 : 0;
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                try {
                    // Try parsing as double then convert to int
                    return (int) Double.parseDouble((String) value);
                } catch (NumberFormatException e2) {
                    logger.warning("Cannot convert string '" + value + "' to integer, using 0");
                    return 0;
                }
            }
        } else {
            logger.warning("Cannot convert " + value.getClass().getName() + " to integer, using 0");
            return 0;
        }
    }

    /**
     * Convert any value to a boolean
     */
    public static boolean convertToBoolean(Object value) {
        if (value == null) {
            return false;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        } else if (value instanceof String) {
            String str = ((String) value).toLowerCase();
            return str.equals("true") || str.equals("yes") || str.equals("1");
        } else {
            logger.warning("Cannot convert " + value.getClass().getName() + " to boolean, using false");
            return false;
        }
    }

    /**
     * Check if a string can be parsed as an integer
     */
    public static boolean isInteger(String str) {
        if (str == null) return false;
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Check if a string can be parsed as a number
     */
    public static boolean isNumeric(String str) {
        if (str == null) return false;
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Check if a string represents a boolean value
     */
    public static boolean isBoolean(String str) {
        if (str == null) return false;
        String lowerStr = str.toLowerCase();
        return lowerStr.equals("true") || lowerStr.equals("false");
    }

    /**
     * Set an input value on a BEAST object
     */
    public static void setInputValue(Input<?> input, Object value, BEASTInterface beastObject) {
        try {
            @SuppressWarnings("unchecked")
            Input<Object> typedInput = (Input<Object>) input;
            typedInput.setValue(value, beastObject);
            logger.fine("Set input '" + input.getName() + "' to " + value);
        } catch (Exception e) {
            logger.warning("Error setting input " + input.getName() + ": " + e.getMessage());
            throw e;
        }
    }

    // Add these methods to BEASTUtils.java

    /**
     * Find a field by name, including in superclasses
     */
    public static Field findField(Class<?> clazz, String fieldName) {
        // Look in the class itself
        try {
            return clazz.getField(fieldName);
        } catch (NoSuchFieldException e) {
            // Look in superclasses
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null) {
                return findField(superClass, fieldName);
            }
            return null;
        }
    }

    /**
     * Configure a BEAST object with arguments from a function call
     */
    public static void configureFromFunctionCall(Object object, FunctionCall funcCall,
                                                 Map<String, Input<?>> inputMap,
                                                 Map<String, Object> objectRegistry) {
        if (!(object instanceof BEASTInterface)) {
            return;
        }

        BEASTInterface beastObject = (BEASTInterface) object;
        Logger logger = Logger.getLogger(BEASTUtils.class.getName());

        for (Argument arg : funcCall.getArguments()) {
            String name = arg.getName();

            Input<?> input = inputMap.get(name);
            if (input == null) {
                logger.warning("No input named '" + name + "' found");
                continue;
            }

            // Get expected type for potential autoboxing
            Class<?> expectedType = input.getType();

            // Resolve value with potential autoboxing
            Object argValue = ExpressionResolver.resolveValueWithAutoboxing(
                    arg.getValue(), objectRegistry, expectedType);

            try {
                setInputValue(input, argValue, beastObject);
            } catch (Exception e) {
                logger.warning("Failed to set input '" + name + "': " + e.getMessage());
            }
        }
    }

    /**
     * Connect an object to target using first available input from a list of names
     */
    public static boolean connectToFirstMatchingInput(Object source, BEASTInterface target,
                                                      String[] inputNames) {
        Logger logger = Logger.getLogger(BEASTUtils.class.getName());

        for (String inputName : inputNames) {
            try {
                beast.base.core.Input<?> input = target.getInput(inputName);
                if (input != null) {
                    setInputValue(input, source, target);
                    logger.fine("Connected object to '" + inputName + "' input");
                    return true;
                }
            } catch (Exception e) {
                // Try next input name
            }
        }

        logger.warning("Could not find matching input for connection");
        return false;
    }

    /**
     * Add to BEASTUtils:
     * Resolve a value according to an expected input type, with appropriate conversion
     */
    public static Object resolveValueForInput(Expression expr, Map<String, Object> objectRegistry,
                                              Input<?> input, String inputName) {
        Object resolvedValue;
        Class<?> expectedType = input.getType();

        // Resolve with autoboxing if we have type information
        if (expectedType != null) {
            resolvedValue = ExpressionResolver.resolveValueWithAutoboxing(expr, objectRegistry, expectedType);
        } else {
            resolvedValue = ExpressionResolver.resolveValue(expr, objectRegistry);
        }

        // If value is null or resolution failed
        if (resolvedValue == null) {
            Logger.getLogger(BEASTUtils.class.getName())
                    .warning("Failed to resolve value for input: " + inputName);
            return null;
        }

        return resolvedValue;
    }

    /**
     * Add to BEASTUtils:
     * Create the most appropriate Parameter type based on an expected type
     */
    public static Parameter<?> createParameterForType(Object value, Class<?> expectedType) {
        if (value == null) {
            return createRealParameter(0.0);
        }

        try {
            boolean isReal = expectedType == null ||
                    RealParameter.class.isAssignableFrom(expectedType) ||
                    expectedType.getName().contains("RealParameter");

            boolean isInteger = IntegerParameter.class.isAssignableFrom(expectedType) ||
                    expectedType.getName().contains("IntegerParameter");

            boolean isBoolean = BooleanParameter.class.isAssignableFrom(expectedType) ||
                    expectedType.getName().contains("BooleanParameter");

            // Default to RealParameter if no specific type is identified
            if (!isReal && !isInteger && !isBoolean) {
                isReal = true;
            }

            if (isInteger) {
                return createIntegerParameter(convertToInteger(value));
            } else if (isBoolean) {
                return createBooleanParameter(convertToBoolean(value));
            } else {
                return createRealParameter(convertToDouble(value));
            }
        } catch (Exception e) {
            Logger.getLogger(BEASTUtils.class.getName())
                    .warning("Error creating parameter: " + e.getMessage());
            return createRealParameter(0.0);
        }
    }
}
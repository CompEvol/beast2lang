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

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Utility class for common BEAST2 operations.
 * Centralized with minimal dependencies on BEAST2 specific knowledge.
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
        } catch (Exception e) {
            logger.warning("Failed to set ID on " + clazz.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Call initAndValidate method on a BEAST object
     */
    public static void callInitAndValidate(Object object, Class<?> clazz) {
        try {
            Method initMethod = clazz.getMethod("initAndValidate");
            initMethod.invoke(object);
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
                    Input<?> input = (Input<?>) field.get(object);
                    inputMap.put(input.getName(), input);
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
        return Parameter.class.isAssignableFrom(type);
    }

    /**
     * Check if a string represents a class name of a Parameter type
     */
    public static boolean isParameterType(String typeName) {
        if (typeName == null) return false;
        try {
            Class<?> clazz = Class.forName(typeName);
            return Parameter.class.isAssignableFrom(clazz);
        } catch (ClassNotFoundException e) {
            return false;
        }
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
            param.initAndValidate();
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
     * Set an input value on a BEAST object
     */
    public static void setInputValue(Input<?> input, Object value, BEASTInterface beastObject) {
        try {
            @SuppressWarnings("unchecked")
            Input<Object> typedInput = (Input<Object>) input;
            logger.fine("Setting input '" + input.getName() + "' to " + value);
            typedInput.setValue(value, beastObject);
        } catch (Exception e) {
            logger.warning("Error setting input " + input.getName() + " with value " + value + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * Find a field by name, including in superclasses
     */
    public static Field findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getField(fieldName);
        } catch (NoSuchFieldException e) {
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

        for (Argument arg : funcCall.getArguments()) {
            String name = arg.getName();

            Input<?> input = inputMap.get(name);
            if (input == null) {
                logger.warning("No input named '" + name + "' found");
                continue;
            }

            // Resolve value with potential autoboxing
            Object argValue = resolveValueWithAutoboxing(
                    arg.getValue(), objectRegistry, BEASTUtils.getInputExpectedType(input,beastObject, name));

            try {
                setInputValue(input, argValue, beastObject);
            } catch (Exception e) {
                logger.warning("Failed to set input '" + name + "': " + e.getMessage());
            }
        }
    }

    /**
     * Connect a random variable to its distribution
     */
    public static boolean connectToFirstMatchingInput(Object source, BEASTInterface target,
                                                      String[] inputNames) {
        for (String inputName : inputNames) {
            try {
                Input<?> input = target.getInput(inputName);
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
     * Create the most appropriate Parameter type based on an expected type
     */
    public static Parameter<?> createParameterForType(Object value, Type expectedType) {
        if (value == null) {
            return createRealParameter(0.0);
        }

        try {
            if (expectedType != null) {
                if (IntegerParameter.class.isAssignableFrom(expectedType.getClass())) {
                    return createIntegerParameter(convertToInteger(value));
                } else if (BooleanParameter.class.isAssignableFrom(expectedType.getClass())) {
                    return createBooleanParameter(convertToBoolean(value));
                } else if (RealParameter.class.isAssignableFrom(expectedType.getClass())) {
                    return createRealParameter(convertToDouble(value));
                }
            }

            // Default to RealParameter if no specific type is identified
            return createRealParameter(convertToDouble(value));
        } catch (Exception e) {
            logger.warning("Error creating parameter: " + e.getMessage());
            return createRealParameter(0.0);
        }
    }

    /**
     * Placeholder methods that will need implementations from ExpressionResolver
     */
    public static Object resolveValueWithAutoboxing(Expression expr, Map<String, Object> objectRegistry, Type targetType) {
        return ExpressionResolver.resolveValueWithAutoboxing(expr, objectRegistry, targetType);
    }

    public static Object resolveValue(Expression expr, Map<String, Object> objectRegistry) {
        return ExpressionResolver.resolveValue(expr, objectRegistry);
    }

    /**
     * Get the expected type for an Input field using Java reflection to examine generic type parameters
     *
     * @param input       The Input object to examine
     * @param beastObject The BEAST object containing the input
     * @param inputName   The name of the input
     * @return The expected Type for the input, or null if it cannot be determined
     */
    public static Type getInputExpectedType(Input<?> input, BEASTInterface beastObject, String inputName) {
        if (input == null) {
            return null;
        }

        // First try the direct approach to get raw class
        Class<?> rawType = input.getType();
        if (rawType != null) {
            return rawType; // Return as Type
        }

        // Get the class containing this input
        Class<?> clazz = beastObject.getClass();

        // Use reflection to find the field and examine its generic type
        while (clazz != null) {
            // Try to find the field in this class
            try {
                for (Field field : clazz.getDeclaredFields()) {
                    // Check if this is an Input field with the right name
                    if (Input.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Input<?> fieldInput = (Input<?>) field.get(beastObject);

                        // Confirm this is the input we're looking for
                        if (fieldInput != null && fieldInput.getName().equals(inputName)) {
                            // Extract the generic parameter from Input<T>
                            Type genericType = field.getGenericType();
                            if (genericType instanceof ParameterizedType) {
                                ParameterizedType paramType = (ParameterizedType) genericType;
                                Type[] typeArgs = paramType.getActualTypeArguments();

                                if (typeArgs.length > 0) {
                                    // Return the actual Type argument instead of trying to convert it to a Class
                                    return typeArgs[0];
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Just log and continue searching in parent classes
                logger.fine("Exception examining field in " + clazz.getName() + ": " + e.getMessage());
            }

            // Move up to the parent class
            clazz = clazz.getSuperclass();
        }

        // Could not determine the type
        return null;
    }
}
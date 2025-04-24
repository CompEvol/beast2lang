package org.beast2.modelLanguage.builder.handlers;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Logger;

import org.beast2.modelLanguage.builder.util.BEASTUtils;
import org.beast2.modelLanguage.model.*;

import beast.base.core.BEASTInterface;

/**
 * Utility class for resolving Expression values to Java objects
 * Enhanced with better generic support for parameter autoboxing
 * and null-safety
 */
public class ExpressionResolver {

    private static final Logger logger = Logger.getLogger(ExpressionResolver.class.getName());

    /**
     * Resolve an Expression to its corresponding value
     *
     * @param expr the expression to resolve
     * @param objectRegistry registry of created objects for reference resolution
     * @return the resolved value
     */
    public static Object resolveValue(Expression expr, Map<String, Object> objectRegistry) {
        if (expr == null) {
            logger.warning("Null expression encountered in resolveValue");
            return null;
        }

        if (expr instanceof Identifier) {
            // Reference to another object
            String refName = ((Identifier) expr).getName();
            logger.fine("Resolving identifier: " + refName);

            Object beastObject = objectRegistry.get(refName);
            if (beastObject == null) {
                logger.warning("Failed to resolve reference: " + refName);
                logger.fine("Available objects: " + objectRegistry.keySet());
            }
            return beastObject;

        } else if (expr instanceof Literal) {
            // Literal value
            return ((Literal) expr).getValue();

        } else if (expr instanceof FunctionCall) {
            // Nested function call - create a new object
            return createNestedObject((FunctionCall) expr, objectRegistry);
        }

        return null;
    }

    /**
     * Resolve an Expression with potential autoboxing to Parameter types based on expected type
     *
     * @param expr the expression to resolve
     * @param objectRegistry registry of created objects for reference resolution
     * @param expectedType the expected type for the resolved value (used for autoboxing)
     * @return the resolved value, potentially autoboxed to a Parameter
     */
    public static Object resolveValueWithAutoboxing(Expression expr, Map<String, Object> objectRegistry, Class<?> expectedType) {
        if (expr == null) {
            logger.warning("Null expression encountered in resolveValueWithAutoboxing");
            return null;
        }

        // Handle autoboxing for literals
        if (expr instanceof Literal) {
            Literal literal = (Literal) expr;
            Object value = literal.getValue();

            // If we have an expected type, try to convert the literal to that type
            if (expectedType != null) {
                // Handle parameters
                if (BEASTUtils.isParameterType(expectedType)) {
                    // Convert to appropriate parameter type using centralized method
                    return BEASTUtils.createParameterForType(value, expectedType);
                }

                // Handle primitive types
                if (expectedType == Double.class || expectedType == double.class) {
                    return BEASTUtils.convertToDouble(value);
                } else if (expectedType == Integer.class || expectedType == int.class) {
                    return BEASTUtils.convertToInteger(value);
                } else if (expectedType == Boolean.class || expectedType == boolean.class) {
                    return BEASTUtils.convertToBoolean(value);
                } else if (expectedType == String.class) {
                    return value != null ? value.toString() : null;
                }
            }

            // No type info or no specific conversion, return the raw value
            return value;
        }

        // Standard resolution without autoboxing for non-literals
        return resolveValue(expr, objectRegistry);
    }

    /**
     * Create a nested object from a FunctionCall expression
     */
    private static Object createNestedObject(FunctionCall funcCall, Map<String, Object> objectRegistry) {
        if (funcCall == null) {
            logger.warning("Null function call encountered");
            return null;
        }

        String className = funcCall.getClassName();
        if (className == null) {
            logger.warning("Null class name in function call");
            return null;
        }

        try {
            // Load the class
            Class<?> beastClass = Class.forName(className);

            // Instantiate the object
            Object nestedObject = beastClass.getDeclaredConstructor().newInstance();
            logger.fine("Created nested object of class " + className);

            // Configure the object
            configureObject(nestedObject, beastClass, funcCall, objectRegistry);

            return nestedObject;
        } catch (Exception e) {
            logger.severe("Failed to create nested object of class " + className + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Configure a nested object with arguments from a function call
     */
    private static void configureObject(Object object, Class<?> objectClass,
                                        FunctionCall funcCall, Map<String, Object> objectRegistry) {
        if (object == null || objectClass == null || funcCall == null) {
            logger.warning("Null parameters in configureObject");
            return;
        }

        // Check if the object implements BEASTInterface for standard BEAST object handling
        if (object instanceof BEASTInterface) {
            // Use centralized configuration logic
            Map<String, beast.base.core.Input<?>> inputMap = BEASTUtils.buildInputMap(object, objectClass);
            BEASTUtils.configureFromFunctionCall(object, funcCall, inputMap, objectRegistry);
        } else {
            // Use Java Beans style setters for non-BEAST objects
            configureGenericObject(object, objectClass, funcCall, objectRegistry);
        }

        // Call initialization methods
        BEASTUtils.callInitAndValidate(object, objectClass);
    }

    /**
     * Configure a generic Java object using setter methods
     */
    private static void configureGenericObject(Object object, Class<?> objectClass,
                                               FunctionCall funcCall, Map<String, Object> objectRegistry) {
        for (Argument arg : funcCall.getArguments()) {
            String paramName = arg.getName();
            if (paramName == null) {
                logger.warning("Null parameter name in argument");
                continue;
            }

            // Try different setter patterns
            try {
                // First try initByName(String, Object) method
                try {
                    Method initMethod = objectClass.getMethod("initByName", String.class, Object.class);

                    // Get parameter type if possible for autoboxing
                    Class<?> expectedType = getParameterTypeFromSetters(objectClass, paramName);

                    // Resolve parameter value with potential autoboxing
                    Object paramValue;
                    if (expectedType != null) {
                        paramValue = resolveValueWithAutoboxing(arg.getValue(), objectRegistry, expectedType);
                    } else {
                        paramValue = resolveValue(arg.getValue(), objectRegistry);
                    }

                    if (paramValue == null) {
                        logger.warning("Resolved null value for parameter: " + paramName);
                        continue;
                    }

                    initMethod.invoke(object, paramName, paramValue);
                    logger.fine("Set property '" + paramName + "' using initByName");
                    continue;
                } catch (NoSuchMethodException e) {
                    // Try next approach
                }

                // Try setter method (setXxx)
                String setterName = "set" + Character.toUpperCase(paramName.charAt(0)) + paramName.substring(1);
                Method[] methods = objectClass.getMethods();
                boolean found = false;

                for (Method method : methods) {
                    if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                        Class<?> paramType = method.getParameterTypes()[0];

                        // Resolve parameter value with potential autoboxing
                        Object paramValue = resolveValueWithAutoboxing(arg.getValue(), objectRegistry, paramType);

                        if (paramValue == null) {
                            logger.warning("Resolved null value for parameter: " + paramName);
                            continue;
                        }

                        method.invoke(object, paramValue);
                        logger.fine("Set property '" + paramName + "' using setter " + setterName);
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    logger.warning("No setter found for property '" + paramName + "' on " + objectClass.getName());
                }
            } catch (Exception e) {
                logger.warning("Failed to set property '" + paramName + "': " + e.getMessage());
            }
        }
    }

    /**
     * Get parameter type from setter methods
     */
    private static Class<?> getParameterTypeFromSetters(Class<?> objectClass, String paramName) {
        try {
            String setterName = "set" + Character.toUpperCase(paramName.charAt(0)) + paramName.substring(1);
            Method[] methods = objectClass.getMethods();

            for (Method method : methods) {
                if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                    return method.getParameterTypes()[0];
                }
            }
        } catch (Exception e) {
            // Ignore, will return null
        }

        return null;
    }
}
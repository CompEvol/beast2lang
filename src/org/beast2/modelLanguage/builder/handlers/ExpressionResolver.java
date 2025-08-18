package org.beast2.modelLanguage.builder.handlers;

import org.beast2.modelLanguage.beast.AutoboxingRegistry;
import org.beast2.modelLanguage.builder.FactoryProvider;
import org.beast2.modelLanguage.builder.ModelObjectFactory;
import org.beast2.modelLanguage.builder.ObjectRegistry;
import org.beast2.modelLanguage.model.*;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Utility class for resolving Expression values to Java objects
 */
public class ExpressionResolver {

    private static final Logger logger = Logger.getLogger(ExpressionResolver.class.getName());
    private static final ModelObjectFactory factory = FactoryProvider.getFactory();

    /**
     * Resolve an Expression to its corresponding value
     */
    public static Object resolveValue(Expression expr, ObjectRegistry objectRegistry) {
        if (expr == null) {
            return null;
        }

        if (expr instanceof Identifier) {
            // Reference to another object
            String refName = ((Identifier) expr).getName();
            return objectRegistry.get(refName);
        } else if (expr instanceof Literal) {
            // Literal value
            return ((Literal) expr).getValue();
        } else if (expr instanceof ArrayLiteral) {
            // Handle array literals
            return resolveArrayLiteral((ArrayLiteral) expr, objectRegistry);
        } else if (expr instanceof FunctionCall) {
            // Nested function call - create a new object
            return createNestedObject((FunctionCall) expr, objectRegistry);
        } else if (expr instanceof NexusFunction) {
            // Handle nexus function
            return handleNexusFunction((NexusFunction) expr, objectRegistry);
        } else if (expr instanceof AlignmentFunction) {
            // Handle alignment function
            return handleAlignmentFunction((AlignmentFunction) expr, objectRegistry);
        } else if (expr instanceof MapExpression) {
            // Handle map expressions
            return resolveMapExpression((MapExpression) expr, objectRegistry);
        }

        return null;
    }

    private static Object handleAlignmentFunction(AlignmentFunction alignmentFunction, ObjectRegistry objectRegistry) {
        try {
            AlignmentFunctionHandler handler = new AlignmentFunctionHandler();
            return handler.processFunction(alignmentFunction, objectRegistry);
        } catch (Exception e) {
            logger.severe("Error processing alignment() function: " + e.getMessage());
            throw new RuntimeException("Failed to process alignment() function", e);
        }
    }

    private static Map<String, Object> resolveMapExpression(MapExpression mapExpr, ObjectRegistry objectRegistry) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Expression> entry : mapExpr.getEntries().entrySet()) {
            result.put(entry.getKey(), resolveValue(entry.getValue(), objectRegistry));
        }
        return result;
    }

    /**
     * Resolve an array literal to an array of objects
     */
    public static Object resolveArrayLiteral(ArrayLiteral arrayLiteral, ObjectRegistry objectRegistry) {
        logger.info("Resolving array literal with " + arrayLiteral.getElements().size() + " elements");

        // Get all elements
        java.util.List<Expression> elements = arrayLiteral.getElements();
        if (elements.isEmpty()) {
            return new Object[0]; // Empty array
        }

        // Resolve all elements
        Object[] values = new Object[elements.size()];
        for (int i = 0; i < elements.size(); i++) {
            values[i] = resolveValue(elements.get(i), objectRegistry);
        }

        // Determine component type from values
        Class<?> componentType = determineComponentType(values);
        logger.info("Determined component type: " + componentType.getName());

        // Create and fill properly typed array
        Object typedArray = java.lang.reflect.Array.newInstance(componentType, values.length);
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];

            // Convert numeric types if needed
            if (componentType == Double.class && value instanceof Number) {
                value = ((Number) value).doubleValue();
            } else if (componentType == Integer.class && value instanceof Number) {
                value = ((Number) value).intValue();
            }

            java.lang.reflect.Array.set(typedArray, i, value);
        }

        logger.info("Created array of type " + typedArray.getClass().getName());
        return typedArray;
    }

    /**
     * Determine the most specific common component type for an array of values
     */
    private static Class<?> determineComponentType(Object[] values) {
        if (values.length == 0) {
            return Object.class;
        }

        // Check if all elements are numbers
        boolean allNumbers = true;
        for (Object value : values) {
            if (!(value instanceof Number)) {
                allNumbers = false;
                break;
            }
        }

        // For numeric arrays, determine the most appropriate numeric type
        if (allNumbers) {
            boolean allIntegers = true;

            for (Object value : values) {
                Number num = (Number) value;

                // If it's already a Double or Float, keep it as Double
                if (num instanceof Double || num instanceof Float) {
                    allIntegers = false;
                    break;
                } else if (num instanceof Long) {
                    long longVal = num.longValue();
                    if (longVal > Integer.MAX_VALUE || longVal < Integer.MIN_VALUE) {
                        allIntegers = false;
                        break;
                    }
                }
                // Integer, Short, Byte are already within integer range
            }

            return allIntegers ? Integer.class : Double.class;
        }

        // Otherwise, try to find common superclass
        Class<?> commonType = values[0] != null ? values[0].getClass() : Object.class;
        for (int i = 1; i < values.length; i++) {
            if (values[i] == null) {
                continue;
            }
            Class<?> valueClass = values[i].getClass();
            if (!commonType.isAssignableFrom(valueClass)) {
                if (valueClass.isAssignableFrom(commonType)) {
                    commonType = valueClass;
                } else {
                    // Find nearest common superclass
                    commonType = Object.class;
                    break;
                }
            }
        }
        return commonType;
    }

    public static Object resolveValueWithAutoboxing(Expression expr, ObjectRegistry objectRegistry, Type targetType) {
        // Just delegate to AutoboxingRegistry
        Object value = resolveValue(expr, objectRegistry);

        if (targetType != null) {
            return AutoboxingRegistry.getInstance().autobox(value, targetType, objectRegistry);
        }

        return value;
    }

    /**
     * Create a nested object from a FunctionCall expression
     */
    private static Object createNestedObject(FunctionCall funcCall, ObjectRegistry objectRegistry) {
        if (funcCall == null) {
            return null;
        }

        String className = funcCall.getClassName();
        if (className == null) {
            return null;
        }

        try {
            Object nestedObject = factory.createObject(className, null);

            // Configure the object using factory
            if (factory.isModelObject(nestedObject)) {
                factory.configureFromFunctionCall(nestedObject, funcCall, objectRegistry);
                factory.initAndValidate(nestedObject);
            } else {
                logger.warning("Created object is not a model object: " + className);
            }

            return nestedObject;
        } catch (Exception e) {
            logger.warning("Failed to create nested object: " + e.getMessage());
            return null;
        }
    }

    /**
     * Handle a nexus function call
     */
    private static Object handleNexusFunction(NexusFunction nexusFunction, ObjectRegistry objectRegistry) {
        try {
            NexusFunctionHandler handler = new NexusFunctionHandler();
            return handler.processFunction(nexusFunction, objectRegistry);
        } catch (Exception e) {
            logger.severe("Error processing nexus() function: " + e.getMessage());
            throw new RuntimeException("Failed to process nexus() function", e);
        }
    }
}
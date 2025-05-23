package org.beast2.modelLanguage.builder.handlers;

import org.beast2.modelLanguage.builder.FactoryProvider;
import org.beast2.modelLanguage.builder.ModelObjectFactory;
import org.beast2.modelLanguage.builder.ObjectRegistry;
import org.beast2.modelLanguage.model.Argument;
import org.beast2.modelLanguage.model.Expression;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Base handler class providing common functionality for all model object handlers.
 * Updated to use ObjectRegistry interface instead of Map<String, Object>.
 *
 * @author Alexei Drummond
 */
public abstract class BaseHandler {
    protected final Logger logger;
    protected final ModelObjectFactory factory;

    /**
     * Constructor with logger name
     *
     * @param loggerName Name for the logger
     */
    protected BaseHandler(String loggerName) {
        this.logger = Logger.getLogger(loggerName);
        this.factory = FactoryProvider.getFactory();
    }

    /**
     * Constructor using calling class name for logger
     */
    protected BaseHandler() {
        this(getCallerClassName());
    }

    private static String getCallerClassName() {
        return Thread.currentThread().getStackTrace()[2].getClassName();
    }

    /**
     * Load a class by name.
     *
     * @param className Fully qualified class name
     * @return The loaded Class object
     * @throws ClassNotFoundException if class cannot be found
     */
    protected Class<?> loadClass(String className) throws ClassNotFoundException {
        return factory.loadClass(className);
    }

    /**
     * Instantiate a class
     *
     * @param clazz The class to instantiate
     * @return New instance of the class
     * @throws Exception if instantiation fails
     */
    protected Object instantiateClass(Class<?> clazz) throws Exception {
        return clazz.getDeclaredConstructor().newInstance();
    }

    /**
     * Create a new model object with the specified class and ID.
     *
     * @param className Fully qualified class name
     * @param objectID Object identifier (may be null)
     * @return The created object
     * @throws Exception if creation fails
     */
    protected Object createBEASTObject(String className, String objectID) throws Exception {
        return factory.createObject(className, objectID);
    }

    /**
     * Resolve an expression and apply autoboxing if needed.
     *
     * @param expr Expression to resolve
     * @param registry Registry containing existing objects
     * @param targetType Expected type for autoboxing
     * @return Resolved and potentially autoboxed value
     */
    protected Object resolveAndAutobox(Expression expr, ObjectRegistry registry, Type targetType) {
        // First resolve the value - use getAllObjects() for read access
        Object value = ExpressionResolver.resolveValue(expr, registry);

        // Then apply autoboxing through factory
        if (targetType != null && factory.canAutobox(value, targetType)) {
            return factory.autobox(value, targetType, registry);
        }

        return value;
    }

    /**
     * Configure an input on a model object.
     * Updated to accept ObjectRegistry instead of Map<String, Object>.
     *
     * @param name Input name
     * @param valueExpr Expression providing the value
     * @param target Target object
     * @param inputMap Map of available inputs (for compatibility)
     * @param registry Registry of existing objects
     */
    protected void configureInput(String name, Expression valueExpr,
                                  Object target, Map<String, Object> inputMap,
                                  ObjectRegistry registry) {
        if (!factory.isModelObject(target)) {
            logger.warning("Target is not a model object");
            return;
        }

        try {
            // Get expected type for autoboxing
            Type expectedType = factory.getInputType(target, name);

            // Resolve and autobox in one step
            Object value = resolveAndAutobox(valueExpr, registry, expectedType);

            // Set the input
            factory.setInputValue(target, name, value);
        } catch (Exception e) {
            logger.warning("Failed to set input '" + name + "': " + e.getMessage());
        }
    }

    /**
     * Configure input for multiple objects (primary and secondary).
     * Tries primary first, then falls back to secondary if needed.
     * Updated to accept ObjectRegistry instead of Map<String, Object>.
     *
     * @param name Input name
     * @param arg Argument containing the value
     * @param primaryObject Primary target object
     * @param secondaryObject Secondary target object (fallback)
     * @param registry Registry of existing objects
     */
    protected void configureInputForObjects(String name, Argument arg,
                                            Object primaryObject, Object secondaryObject,
                                            ObjectRegistry registry) {
        if (!factory.isModelObject(primaryObject)) {
            return;
        }

        // Get the value from the expression - use getAllObjects() for read access
        Object argValue = ExpressionResolver.resolveValue(arg.getValue(), registry);

        // Get expected types
        Type primaryExpectedType = factory.getInputType(primaryObject, name);
        Type secondaryExpectedType = (secondaryObject != null && factory.isModelObject(secondaryObject)) ?
                factory.getInputType(secondaryObject, name) : null;

        boolean inputSet = false;

        // First, try to set the input on the primary object
        if (primaryExpectedType != null) {
            try {
                Object primaryValue = factory.canAutobox(argValue, primaryExpectedType) ?
                        factory.autobox(argValue, primaryExpectedType, registry) : argValue;

                logger.info("Attempting to set input '" + name + "' on primary object");
                factory.setInputValue(primaryObject, name, primaryValue);
                inputSet = true;
            } catch (Exception e) {
                logger.warning("Failed to set input on primary object: " + e.getMessage());
            }
        }

        // Only try secondary if primary wasn't set successfully
        if (!inputSet && secondaryExpectedType != null && factory.isModelObject(secondaryObject)) {
            try {
                Object secondaryValue = factory.canAutobox(argValue, secondaryExpectedType) ?
                        factory.autobox(argValue, secondaryExpectedType, registry) : argValue;

                logger.info("Falling back to secondary object for input: " + name);
                factory.setInputValue(secondaryObject, name, secondaryValue);
                inputSet = true;
            } catch (Exception e) {
                logger.warning("Failed to set input on secondary object: " + e.getMessage());
            }
        }

        if (!inputSet) {
            logger.warning("Could not set input '" + name + "' on either primary or secondary object");
        }
    }
}
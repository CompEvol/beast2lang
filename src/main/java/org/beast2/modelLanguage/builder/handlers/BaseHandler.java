package org.beast2.modelLanguage.builder.handlers;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import org.beast2.modelLanguage.builder.util.AutoboxingRegistry;
import org.beast2.modelLanguage.builder.util.BEASTUtils;
import org.beast2.modelLanguage.model.*;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Base handler class providing common functionality for all BEAST2 object handlers.
 */
public abstract class BaseHandler {
    protected final Logger logger;

    /**
     * Constructor with logger name
     */
    protected BaseHandler(String loggerName) {
        this.logger = Logger.getLogger(loggerName);
    }

    /**
     * Constructor using class name for logger
     */
    protected BaseHandler() {
        this(getCallerClassName());
    }

    private static String getCallerClassName() {
        return Thread.currentThread().getStackTrace()[2].getClassName();
    }

    /**
     * Load a class by name
     */
    protected Class<?> loadClass(String className) throws ClassNotFoundException {
        return Class.forName(className);
    }

    /**
     * Instantiate a class
     */
    protected Object instantiateClass(Class<?> clazz) throws Exception {
        return clazz.getDeclaredConstructor().newInstance();
    }

    /**
     * Create a new BEAST object
     */
    protected Object createBEASTObject(String className, String objectID) throws Exception {
        Class<?> clazz = loadClass(className);
        Object object = instantiateClass(clazz);

        if (objectID != null && !objectID.isEmpty()) {
            BEASTUtils.setObjectId(object, clazz, objectID);
        }

        return object;
    }

    /**
     * Single entry point for resolving and autoboxing values
     */
    protected Object resolveAndAutobox(Expression expr, Map<String, Object> objectRegistry, Type targetType) {
        // First resolve the value
        Object value = ExpressionResolver.resolveValue(expr, objectRegistry);

        // Then apply autoboxing
        if (targetType != null) {
            return AutoboxingRegistry.getInstance().autobox(value, targetType, objectRegistry);
        }

        return value;
    }

    /**
     * Configure an input with autoboxing
     */
    protected void configureInput(String name, Expression valueExpr,
                                  BEASTInterface target, Map<String, Input<?>> inputMap,
                                  Map<String, Object> objectRegistry) {
        Input<?> input = inputMap.get(name);
        if (input == null) {
            logger.warning("No input named '" + name + "' found");
            return;
        }

        // Get expected type for autoboxing
        Type expectedType = BEASTUtils.getInputExpectedType(input,target,name);

        // Resolve and autobox in one step
        Object value = resolveAndAutobox(valueExpr, objectRegistry, expectedType);

        // Set the input
        try {
            BEASTUtils.setInputValue(input, value, target);
        } catch (Exception e) {
            logger.warning("Failed to set input '" + name + "': " + e.getMessage());
        }
    }

    protected void configureInputForObjects(String name, Argument arg,
                                            Object primaryObject, Object secondaryObject,
                                            Map<String, Input<?>> primaryInputMap,
                                            Map<String, Input<?>> secondaryInputMap,
                                            Map<String, Object> objectRegistry) {
        if (!(primaryObject instanceof BEASTInterface)) {
            return;
        }

        // Get the value from the expression
        Object argValue = ExpressionResolver.resolveValue(arg.getValue(), objectRegistry);

        // Check both inputs
        Input<?> primaryInput = primaryInputMap.get(name);
        Input<?> secondaryInput = (secondaryInputMap != null) ? secondaryInputMap.get(name) : null;

        // Get expected types
        Type primaryExpectedType = (primaryInput != null) ?
                BEASTUtils.getInputExpectedType(primaryInput, (BEASTInterface) primaryObject, name) : null;
        Type secondaryExpectedType = (secondaryInput != null && secondaryObject instanceof BEASTInterface) ?
                BEASTUtils.getInputExpectedType(secondaryInput, (BEASTInterface) secondaryObject, name) : null;

        // Try to find the best match for this input based on the arg value's type
        boolean useSecondary = false;

        // If argValue is a TaxonSet or Alignment and secondaryInput exists on Tree, prefer that
        if (argValue != null &&
                secondaryInput != null &&
                (argValue.getClass().getName().contains("TaxonSet") ||
                        argValue.getClass().getName().contains("Alignment"))) {
            useSecondary = true;
            logger.info("Preferring secondary object for taxon-related input: " + name);
        }

        // Apply autoboxing to the value based on target type
        Object primaryValue = primaryExpectedType != null ?
                AutoboxingRegistry.getInstance().autobox(argValue, primaryExpectedType, objectRegistry) : argValue;
        Object secondaryValue = secondaryExpectedType != null ?
                AutoboxingRegistry.getInstance().autobox(argValue, secondaryExpectedType, objectRegistry) : argValue;

        boolean inputSet = false;

        // Try secondary first if we determined it's more appropriate
        if (useSecondary && secondaryObject instanceof BEASTInterface && secondaryInput != null) {
            try {
                logger.info("Setting input '" + name + "' on secondary object (Tree)");
                BEASTUtils.setInputValue(secondaryInput, secondaryValue, (BEASTInterface) secondaryObject);
                inputSet = true;
            } catch (Exception e) {
                logger.warning("Failed to set input on secondary object: " + e.getMessage());
            }
        }

        // Try primary if secondary wasn't used or failed
        if (!inputSet && primaryInput != null) {
            try {
                logger.info("Setting input '" + name + "' on primary object (Distribution)");
                BEASTUtils.setInputValue(primaryInput, primaryValue, (BEASTInterface) primaryObject);
                inputSet = true;
            } catch (Exception e) {
                logger.warning("Failed to set input on primary object: " + e.getMessage());

                // If we haven't tried secondary yet, try it now
                if (!useSecondary && secondaryObject instanceof BEASTInterface && secondaryInput != null) {
                    try {
                        logger.info("Falling back to secondary object for input: " + name);
                        BEASTUtils.setInputValue(secondaryInput, secondaryValue, (BEASTInterface) secondaryObject);
                        inputSet = true;
                    } catch (Exception e2) {
                        logger.warning("Failed to set input on secondary object: " + e2.getMessage());
                    }
                }
            }
        }

        if (!inputSet) {
            logger.warning("Could not set input '" + name + "' on either primary or secondary object");
        }
    }
}
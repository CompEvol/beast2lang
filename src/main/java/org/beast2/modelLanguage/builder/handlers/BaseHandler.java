package org.beast2.modelLanguage.builder.handlers;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import org.beast2.modelLanguage.builder.util.BEASTUtils;
import org.beast2.modelLanguage.model.*;

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
     * Initialize a RealParameter with default values
     */
    protected void initializeRealParameter(Object paramObject, Class<?> paramClass) {
        try {
            // Set default value for RealParameter
            List<Double> defaultValue = List.of(0.0);
            ((BEASTInterface)paramObject).initByName("value", defaultValue);

            // Initialize the parameter
            BEASTUtils.callInitAndValidate(paramObject, paramClass);
        } catch (Exception e) {
            logger.warning("Failed to initialize RealParameter: " + e.getMessage());
        }
    }

    /**
     * Configure inputs for multiple objects (primary and optional secondary)
     */
    protected void configureInputForObjects(String name, Argument arg,
                                            Object primaryObject, Object secondaryObject,
                                            Map<String, Input<?>> primaryInputMap,
                                            Map<String, Input<?>> secondaryInputMap,
                                            Map<String, Object> objectRegistry) {
        if (!(primaryObject instanceof BEASTInterface)) {
            return;
        }

        // Get input and expected type
        Input<?> primaryInput = primaryInputMap.get(name);
        Class<?> expectedType = (primaryInput != null) ? primaryInput.getType() : null;

        if (expectedType == null && secondaryInputMap != null) {
            Input<?> secondaryInput = secondaryInputMap.get(name);
            if (secondaryInput != null) {
                expectedType = secondaryInput.getType();
            }
        }

        // Resolve the value
        Object argValue = ExpressionResolver.resolveValueWithAutoboxing(
                arg.getValue(), objectRegistry, expectedType);

        // Try to set on primary object first
        boolean inputSet = false;
        if (primaryInput != null) {
            try {
                BEASTUtils.setInputValue(primaryInput, argValue, (BEASTInterface) primaryObject);
                inputSet = true;
            } catch (Exception e) {
                // Try secondary if primary fails
            }
        }

        // Try secondary object if primary failed
        if (!inputSet && secondaryObject instanceof BEASTInterface && secondaryInputMap != null) {
            Input<?> secondaryInput = secondaryInputMap.get(name);
            if (secondaryInput != null) {
                try {
                    BEASTUtils.setInputValue(secondaryInput, argValue, (BEASTInterface) secondaryObject);
                } catch (Exception e) {
                    // Both failed, nothing more to do
                }
            }
        }
    }
}
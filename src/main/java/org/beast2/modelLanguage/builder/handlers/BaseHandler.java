package org.beast2.modelLanguage.builder.handlers;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import org.beast2.modelLanguage.builder.util.BEASTUtils;
import org.beast2.modelLanguage.model.*;

import java.lang.reflect.Field;
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
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            logger.severe("Failed to load class: " + className);
            throw e;
        }
    }

    /**
     * Instantiate a class
     */
    protected Object instantiateClass(Class<?> clazz) throws Exception {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.severe("Failed to instantiate class: " + clazz.getName());
            throw e;
        }
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
     * Initialize a parameter with default values
     */
    protected void initializeParameter(Object paramObject, Class<?> paramClass, String inputFieldName, Object defaultValue) {
        try {
            Field inputField = BEASTUtils.findField(paramClass, inputFieldName);
            if (inputField != null) {
                @SuppressWarnings("unchecked")
                Input<Object> input = (Input<Object>) inputField.get(paramObject);
                BEASTUtils.setInputValue(input, defaultValue, (BEASTInterface) paramObject);
                logger.fine("Set default value for parameter: " + defaultValue);

                // Call initAndValidate
                BEASTUtils.callInitAndValidate(paramObject, paramClass);
            } else {
                logger.warning("Could not find input field " + inputFieldName + " in " + paramClass.getName());
            }
        } catch (Exception e) {
            logger.warning("Failed to initialize parameter: " + e.getMessage());
        }
    }

    /**
     * Initialize a RealParameter with default values
     */
    protected void initializeRealParameter(Object paramObject, Class<?> paramClass) {
        try {
            // Find the valuesInput field
            Field valuesInputField = BEASTUtils.findField(paramClass, "valuesInput");
            if (valuesInputField != null) {
                @SuppressWarnings("unchecked")
                Input<Object> valuesInput = (Input<Object>) valuesInputField.get(paramObject);

                // Set a default value
                List<Double> defaultValue = List.of(0.0);
                BEASTUtils.setInputValue(valuesInput, defaultValue, (BEASTInterface) paramObject);
                logger.fine("Set default value for RealParameter: " + defaultValue);

                // Call initAndValidate
                BEASTUtils.callInitAndValidate(paramObject, paramClass);
            } else {
                logger.warning("Could not find valuesInput field in RealParameter");
            }
        } catch (Exception e) {
            logger.warning("Failed to initialize RealParameter: " + e.getMessage());
        }
    }

    /**
     * Attempt to connect a source object to a target object by trying multiple input names
     */
    protected boolean connectToInput(Object sourceObject, Object targetObject,
                                     Map<String, Input<?>> inputMap, String[] inputNames) {
        for (String inputName : inputNames) {
            Input<?> input = inputMap.get(inputName);
            if (input != null) {
                try {
                    BEASTUtils.setInputValue(input, sourceObject, (BEASTInterface) targetObject);
                    logger.fine("Connected object to '" + inputName + "' input");
                    return true;
                } catch (Exception e) {
                    // Continue to next input name
                }
            }
        }

        logger.warning("Could not find appropriate input for connection");
        return false;
    }

    /**
     * Configure an object using function call arguments
     */
    protected void configureFromFunctionCall(Object object, FunctionCall funcCall,
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

            // Get expected type for potential autoboxing
            Class<?> expectedType = input.getType();

            // Resolve value with potential autoboxing
            Object argValue = BEASTUtils.resolveValueForInput(
                    arg.getValue(), objectRegistry, input, name);

            if (argValue == null) {
                logger.warning("Failed to resolve value for input: " + name);
                continue;
            }

            try {
                BEASTUtils.setInputValue(input, argValue, beastObject);
            } catch (Exception e) {
                logger.warning("Failed to set input '" + name + "': " + e.getMessage());

                // If this is a type mismatch, try creating a Parameter
                if (e.getMessage() != null && e.getMessage().contains("type mismatch") && argValue != null) {
                    try {
                        Object paramValue = BEASTUtils.createRealParameter(argValue);
                        BEASTUtils.setInputValue(input, paramValue, beastObject);
                        logger.fine("Set input '" + name + "' using Parameter conversion");
                    } catch (Exception ex) {
                        logger.warning("Parameter conversion also failed: " + ex.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Configure inputs for multiple objects, trying primary first then secondary
     */
    protected void configureInputForObjects(String name, Argument arg,
                                            Object primaryObject, Object secondaryObject,
                                            Map<String, Input<?>> primaryInputMap,
                                            Map<String, Input<?>> secondaryInputMap,
                                            Map<String, Object> objectRegistry) {
        // Get expected type for potential autoboxing
        Class<?> expectedType = null;
        Input<?> primaryInput = primaryInputMap.get(name);
        if (primaryInput != null) {
            expectedType = primaryInput.getType();
        } else if (secondaryInputMap != null) {
            Input<?> secondaryInput = secondaryInputMap.get(name);
            if (secondaryInput != null) {
                expectedType = secondaryInput.getType();
            }
        }

        // Resolve value with possible autoboxing
        Object argValue = ExpressionResolver.resolveValueWithAutoboxing(
                arg.getValue(), objectRegistry, expectedType);

        boolean inputSet = false;

        // Try to set on primary object first
        if (primaryInput != null) {
            try {
                BEASTUtils.setInputValue(primaryInput, argValue, (BEASTInterface) primaryObject);
                logger.fine("Set primary object input '" + name + "' to " + argValue);
                inputSet = true;
            } catch (Exception e) {
                logger.warning("Failed to set input '" + name + "' on primary object: " + e.getMessage());
            }
        }

        // If not set and secondary object exists, try there
        if (!inputSet && secondaryInputMap != null) {
            Input<?> secondaryInput = secondaryInputMap.get(name);
            if (secondaryInput != null) {
                try {
                    BEASTUtils.setInputValue(secondaryInput, argValue, (BEASTInterface) secondaryObject);
                    logger.fine("Set secondary object input '" + name + "' to " + argValue);
                    inputSet = true;
                } catch (Exception e) {
                    logger.warning("Failed to set input '" + name + "' on secondary object: " + e.getMessage());
                }
            }
        }

        // Log if input wasn't found or couldn't be set
        if (!inputSet) {
            logger.warning("Input '" + name + "' not found or couldn't be set on any object");
        }
    }
}
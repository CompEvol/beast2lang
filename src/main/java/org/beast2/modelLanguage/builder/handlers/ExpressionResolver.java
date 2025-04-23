package org.beast2.modelLanguage.builder.handlers;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import beast.base.inference.parameter.BooleanParameter;
import beast.base.inference.parameter.IntegerParameter;
import beast.base.inference.parameter.Parameter;
import beast.base.inference.parameter.RealParameter;
import org.beast2.modelLanguage.model.*;

import beast.base.core.BEASTInterface;

/**
 * Utility class for resolving Expression values to Java objects
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
        // Handle autoboxing for literals when the expected type is a Parameter
        if (expr instanceof Literal && expectedType != null && Parameter.class.isAssignableFrom(expectedType)) {
            logger.fine("Autoboxing literal to " + expectedType.getSimpleName());
            return ParameterAutoboxer.literalToParameter((Literal) expr, null, expectedType);
        }

        // Standard resolution without autoboxing
        return resolveValue(expr, objectRegistry);
    }

    /**
     * Create a nested object from a FunctionCall expression
     */
    private static Object createNestedObject(FunctionCall funcCall, Map<String, Object> objectRegistry) {
        String className = funcCall.getClassName();

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
        // Check if the object implements BEASTInterface for standard BEAST object handling
        if (object instanceof BEASTInterface) {
            configureBeastInterface(object, funcCall, objectRegistry);
        } else {
            // Use Java Beans style setters for non-BEAST objects
            configureGenericObject(object, objectClass, funcCall, objectRegistry);
        }
        
        // Call initialization methods
        try {
            // Try initAndValidate first (BEAST standard)
            try {
                Method initMethod = objectClass.getMethod("initAndValidate");
                initMethod.invoke(object);
                logger.fine("Called initAndValidate() on " + objectClass.getName());
                return;
            } catch (NoSuchMethodException e) {
                // Try initialize as fallback
                try {
                    Method initMethod = objectClass.getMethod("initialize");
                    initMethod.invoke(object);
                    logger.fine("Called initialize() on " + objectClass.getName());
                } catch (NoSuchMethodException e2) {
                    // No initialization method, which is fine for some objects
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to initialize object of class " + objectClass.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Configure a BEAST object that implements BEASTInterface
     */
    private static void configureBeastInterface(Object object, FunctionCall funcCall, Map<String, Object> objectRegistry) {
        BEASTInterface beastObject = (BEASTInterface) object;
        
        for (Argument arg : funcCall.getArguments()) {
            String paramName = arg.getName();
            Object paramValue = resolveValue(arg.getValue(), objectRegistry);
            
            try {
                // Get the Input for this parameter
                beast.base.core.Input<?> input = beastObject.getInput(paramName);
                if (input == null) {
                    logger.warning("No input named '" + paramName + "' found on " + beastObject.getClass().getName());
                    continue;
                }
                
                // Set the input value
                @SuppressWarnings("unchecked")
                beast.base.core.Input<Object> typedInput = (beast.base.core.Input<Object>) input;
                typedInput.setValue(paramValue, beastObject);
                logger.fine("Set input '" + paramName + "' to " + paramValue);
            } catch (Exception e) {
                logger.warning("Failed to set input '" + paramName + "': " + e.getMessage());
            }
        }
    }
    
    /**
     * Configure a generic Java object using setter methods
     */
    private static void configureGenericObject(Object object, Class<?> objectClass, 
                                             FunctionCall funcCall, Map<String, Object> objectRegistry) {
        for (Argument arg : funcCall.getArguments()) {
            String paramName = arg.getName();
            Object paramValue = resolveValue(arg.getValue(), objectRegistry);
            
            // Try different setter patterns
            try {
                // First try initByName(String, Object) method
                try {
                    Method initMethod = objectClass.getMethod("initByName", String.class, Object.class);
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
}
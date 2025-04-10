package org.beast2.modelLanguage.builder.handlers;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import org.beast2.modelLanguage.model.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Handler for VariableDeclaration statements, responsible for creating BEAST2 objects.
 */
public class VariableDeclarationHandler {
    
    private static final Logger logger = Logger.getLogger(VariableDeclarationHandler.class.getName());
    
    /**
     * Create a BEAST2 object from a variable declaration
     * 
     * @param varDecl the variable declaration to process
     * @param objectRegistry registry of created objects for reference resolution
     * @return the created BEAST2 object
     * @throws Exception if object creation fails
     */
    public Object createObject(VariableDeclaration varDecl, Map<String, Object> objectRegistry) throws Exception {
        String declaredTypeName = varDecl.getClassName();   // e.g. "beast.base.inference.parameter.RealParameter"
        String variableName = varDecl.getVariableName(); 
        Expression value = varDecl.getValue();       // should be a FunctionCall

        logger.info("Creating object with name: " + variableName 
                   + " of declared type: " + declaredTypeName);

        if (!(value instanceof FunctionCall)) {
            throw new IllegalArgumentException("Right side of variable declaration must be a function call");
        }
        FunctionCall funcCall = (FunctionCall) value;
        String implementationClassName = funcCall.getClassName();

        // 1) Load classes
        Class<?> declaredType = loadClass(declaredTypeName);
        Class<?> implementationClass = loadClass(implementationClassName);
        
        if (!declaredType.isAssignableFrom(implementationClass)) {
            throw new ClassCastException(
                implementationClassName + " is not assignable to " + declaredTypeName
            );
        }

        // 2) Instantiate
        Object beastObject = instantiateClass(implementationClass);
        logger.info("Successfully instantiated " + implementationClassName);

        // 3) setID if present
        setObjectId(beastObject, implementationClass, variableName);

        // 4) INTROSPECT all declared public Input<?> fields on the class
        //    and build a name→Input<?> map
        Map<String, Input<?>> inputMap = buildInputMap(beastObject, implementationClass);

        // 5) CONFIGURE all arguments by matching name & type against that map
        configureInputs(beastObject, funcCall, inputMap, objectRegistry);

        // 6) Call initAndValidate if it exists
        callInitAndValidate(beastObject, implementationClass);

        return beastObject;
    }
    
    private Class<?> loadClass(String className) throws ClassNotFoundException {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            logger.severe("Failed to load class: " + className);
            throw e;
        }
    }
    
    private Object instantiateClass(Class<?> clazz) throws Exception {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            logger.severe("Failed to instantiate class: " + clazz.getName());
            throw e;
        }
    }
    
    private void setObjectId(Object object, Class<?> clazz, String id) {
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
    
    private Map<String, Input<?>> buildInputMap(Object object, Class<?> clazz) {
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
    
    private void configureInputs(Object object, FunctionCall funcCall, Map<String, Input<?>> inputMap, 
                                Map<String, Object> objectRegistry) throws Exception {
        for (Argument arg : funcCall.getArguments()) {
            String name = arg.getName();
            Object rawValue = ExpressionResolver.resolveValue(arg.getValue(), objectRegistry);
            
            logger.fine("Setting input " + name + " to " + rawValue);
            
            Input<?> input = inputMap.get(name);
            if (input == null) {
                throw new RuntimeException("No Input named '" + name + "' found");
            }
            
            // Type check if we have an expected type and a non-null value
            Class<?> expectedType = input.getType();
            if (expectedType != null && rawValue != null) {
                if (!expectedType.isAssignableFrom(rawValue.getClass())) {
                    throw new RuntimeException(String.format(
                        "Type mismatch for Input '%s': expected %s but got %s",
                        name,
                        expectedType.getSimpleName(),
                        rawValue.getClass().getSimpleName()
                    ));
                }
            }
            
            // Set the input value
            @SuppressWarnings("unchecked")
            Input<Object> typedInput = (Input<Object>) input;
            typedInput.setValue(rawValue, (BEASTInterface) object);
        }
    }
    
    private void callInitAndValidate(Object object, Class<?> clazz) {
        try {
            Method initAndValidate = clazz.getMethod("initAndValidate");
            logger.info("Calling initAndValidate() on " + clazz.getName());
            initAndValidate.invoke(object);
        } catch (NoSuchMethodException e) {
            // Some classes might not have initAndValidate, which is fine
            logger.fine("No initAndValidate method found for " + clazz.getName());
        } catch (Exception e) {
            logger.warning("Failed to call initAndValidate on " + clazz.getName() + ": " + e.getMessage());
        }
    }
}
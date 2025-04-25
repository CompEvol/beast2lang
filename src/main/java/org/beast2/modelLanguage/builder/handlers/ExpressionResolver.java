package org.beast2.modelLanguage.builder.handlers;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Logger;

import org.beast2.modelLanguage.builder.util.AutoboxingRegistry;
import org.beast2.modelLanguage.builder.util.BEASTUtils;
import org.beast2.modelLanguage.model.*;

import beast.base.core.BEASTInterface;
import beast.base.inference.parameter.Parameter;

/**
 * Utility class for resolving Expression values to Java objects
 */
public class ExpressionResolver {

    private static final Logger logger = Logger.getLogger(ExpressionResolver.class.getName());

    /**
     * Resolve an Expression to its corresponding value
     */
    public static Object resolveValue(Expression expr, Map<String, Object> objectRegistry) {
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
        } else if (expr instanceof FunctionCall) {
            // Nested function call - create a new object
            return createNestedObject((FunctionCall) expr, objectRegistry);
        }

        return null;
    }

    public static Object resolveValueWithAutoboxing(Expression expr, Map<String, Object> objectRegistry, Class<?> expectedType) {
        // Just delegate to AutoboxingRegistry
        Object value = resolveValue(expr, objectRegistry);

        if (value != null && expectedType != null) {
            return AutoboxingRegistry.getInstance().autobox(value, expectedType, objectRegistry);
        }

        return value;
    }

    /**
     * Create a nested object from a FunctionCall expression
     */
    private static Object createNestedObject(FunctionCall funcCall, Map<String, Object> objectRegistry) {
        if (funcCall == null) {
            return null;
        }

        String className = funcCall.getClassName();
        if (className == null) {
            return null;
        }

        try {
            Class<?> beastClass = Class.forName(className);
            Object nestedObject = beastClass.getDeclaredConstructor().newInstance();

            // Configure the object
            if (nestedObject instanceof BEASTInterface) {
                Map<String, beast.base.core.Input<?>> inputMap = BEASTUtils.buildInputMap(nestedObject, beastClass);
                BEASTUtils.configureFromFunctionCall(nestedObject, funcCall, inputMap, objectRegistry);
                BEASTUtils.callInitAndValidate(nestedObject, beastClass);
            } else {
                configureGenericObject(nestedObject, beastClass, funcCall, objectRegistry);
            }

            return nestedObject;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Configure a generic Java object using setter methods
     */
    private static void configureGenericObject(Object object, Class<?> objectClass,
                                               FunctionCall funcCall, Map<String, Object> objectRegistry) {
        for (Argument arg : funcCall.getArguments()) {
            String paramName = arg.getName();
            if (paramName == null) {
                continue;
            }

            try {
                // Try initByName if available
                try {
                    Method initMethod = objectClass.getMethod("initByName", String.class, Object.class);
                    Class<?> expectedType = getParameterTypeFromSetters(objectClass, paramName);
                    Object paramValue = resolveValueWithAutoboxing(arg.getValue(), objectRegistry, expectedType);

                    if (paramValue != null) {
                        initMethod.invoke(object, paramName, paramValue);
                        continue;
                    }
                } catch (NoSuchMethodException e) {
                    // Try setter methods
                }

                // Try setter methods
                String setterName = "set" + Character.toUpperCase(paramName.charAt(0)) + paramName.substring(1);
                for (Method method : objectClass.getMethods()) {
                    if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                        Class<?> paramType = method.getParameterTypes()[0];
                        Object paramValue = resolveValueWithAutoboxing(arg.getValue(), objectRegistry, paramType);

                        if (paramValue != null) {
                            method.invoke(object, paramValue);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // Skip this parameter if setting fails
            }
        }
    }

    /**
     * Get parameter type from setter methods
     */
    private static Class<?> getParameterTypeFromSetters(Class<?> objectClass, String paramName) {
        String setterName = "set" + Character.toUpperCase(paramName.charAt(0)) + paramName.substring(1);

        for (Method method : objectClass.getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                return method.getParameterTypes()[0];
            }
        }

        return null;
    }
}
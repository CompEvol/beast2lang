package org.beast2.modelLanguage.builder.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for working with BEAST2 classes via reflection
 */
public class Beast2ReflectionUtils {

    // Cache of class and method information to improve performance
    private static final Map<Class<?>, Map<String, Method>> methodCache = new HashMap<>();
    private static final Map<Class<?>, List<Field>> fieldCache = new HashMap<>();
    
    /**
     * Find an initialization method in a BEAST2 class
     * 
     * @param beastClass the BEAST2 class to search
     * @param name the name of the parameter to initialize
     * @return the found method or null if not found
     */
    public static Method findInitMethod(Class<?> beastClass, String name) {
        // Check for initByName method first (common in BEAST2)
        try {
            Method initByName = beastClass.getMethod("initByName", String.class, Object.class);
            return initByName;
        } catch (NoSuchMethodException e) {
            // Try alternative init methods
        }
        
        // Check for setInputValue method (common in BEAST2)
        try {
            Method setInputValue = beastClass.getMethod("setInputValue", String.class, Object.class);
            return setInputValue;
        } catch (NoSuchMethodException e) {
            // Try alternative methods
        }
        
        // Check for property setter method
        String setterName = "set" + capitalize(name);
        Method[] methods = beastClass.getMethods();
        for (Method method : methods) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                return method;
            }
        }
        
        return null;
    }
    
    /**
     * Get all known input parameters for a BEAST2 class
     * 
     * @param beastClass the BEAST2 class to inspect
     * @return a list of parameter names
     */
    public static List<String> getInputParameterNames(Class<?> beastClass) {
        List<String> paramNames = new ArrayList<>();
        
        // Look for @Input annotations in the class hierarchy
        Class<?> currentClass = beastClass;
        while (currentClass != null && !currentClass.equals(Object.class)) {
            Field[] fields = currentClass.getDeclaredFields();
            for (Field field : fields) {
                // Check if field has @Input annotation
                if (hasInputAnnotation(field)) {
                    paramNames.add(field.getName());
                }
            }
            
            // Move to parent class
            currentClass = currentClass.getSuperclass();
        }
        
        return paramNames;
    }
    
    /**
     * Check if a field has the BEAST2 @Input annotation
     * 
     * @param field the field to check
     * @return true if the field has an @Input annotation
     */
    public static boolean hasInputAnnotation(Field field) {
        return Arrays.stream(field.getAnnotations())
                .anyMatch(a -> a.annotationType().getSimpleName().equals("Input"));
    }
    
    /**
     * Convert a Beast2Model expression value to a BEAST2 object value
     * 
     * @param value the value to convert
     * @param targetType the target BEAST2 type
     * @param beastObjects map of existing BEAST2 objects
     * @return the converted value
     */
    public static Object convertValue(Object value, Class<?> targetType, Map<String, Object> beastObjects) throws Exception {
        if (value == null) {
            return null;
        }
        
        // If the value is already of the target type, return it
        if (targetType.isInstance(value)) {
            return value;
        }
        
        // String reference to another object
        if (value instanceof String && beastObjects.containsKey(value)) {
            return beastObjects.get(value);
        }
        
        // Primitive type conversions
        if (targetType == Integer.class || targetType == int.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                return Integer.parseInt((String) value);
            }
        } else if (targetType == Double.class || targetType == double.class) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            } else if (value instanceof String) {
                return Double.parseDouble((String) value);
            }
        } else if (targetType == Float.class || targetType == float.class) {
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            } else if (value instanceof String) {
                return Float.parseFloat((String) value);
            }
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof String) {
                return Boolean.parseBoolean((String) value);
            }
        } else if (targetType == String.class) {
            return value.toString();
        }
        
        // Lists and Arrays
        if (List.class.isAssignableFrom(targetType) && value instanceof Object[]) {
            return Arrays.asList((Object[]) value);
        }
        
        // Try to find a constructor that accepts the value type
        try {
            return targetType.getConstructor(value.getClass()).newInstance(value);
        } catch (NoSuchMethodException e) {
            // No direct constructor, try alternative approaches
        }
        
        // For BEAST2 parameters, try to find a factory method or special constructor
        try {
            Method valueOf = targetType.getMethod("valueOf", String.class);
            if (Modifier.isStatic(valueOf.getModifiers())) {
                return valueOf.invoke(null, value.toString());
            }
        } catch (NoSuchMethodException e) {
            // No valueOf method, continue with other attempts
        }
        
        // If all else fails, use string constructor
        try {
            return targetType.getConstructor(String.class).newInstance(value.toString());
        } catch (NoSuchMethodException e) {
            // No string constructor available
        }
        
        // Could not convert
        throw new IllegalArgumentException("Cannot convert " + value + " of type " + 
                value.getClass().getName() + " to " + targetType.getName());
    }
    
    /**
     * Capitalize the first letter of a string
     */
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * Find specific BEAST2 classes by their name pattern
     * 
     * @param namePattern substring to match in class names
     * @return list of matching class names
     */
    public static List<String> findBeast2Classes(String namePattern) {
        List<String> matchingClasses = new ArrayList<>();
        
        // BEAST2 common package prefixes to search in
        String[] packagePrefixes = {
            "beast.base.",
            "beast.core.",
            "beast.evolution.",
            "beast.math.",
            "beast.util."
        };
        
        // Search common BEAST2 packages for classes matching the pattern
        for (String prefix : packagePrefixes) {
            try {
                // Note: This would require a custom ClassLoader implementation 
                // to scan packages in a production environment
                // For now, this is a placeholder for the concept
                matchingClasses.add(prefix + namePattern);
            } catch (Exception e) {
                // Ignore package scanning errors
            }
        }
        
        return matchingClasses;
    }
    
    /**
     * Generate a BEAST2 ID for an object based on its type and a suffix
     */
    public static String generateBeast2Id(Class<?> beastClass, String suffix) {
        String simpleName = beastClass.getSimpleName();
        // Convert camelCase to lowercase with underscores
        String baseId = simpleName.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
        return baseId + (suffix != null ? "_" + suffix : "");
    }
    
    /**
     * Check if a class is a BEAST2 distribution
     */
    public static boolean isBeast2Distribution(Class<?> clazz) {
        // Check if the class implements Distribution interface or extends some Distribution class
        try {
            Class<?> distributionClass = Class.forName("beast.base.inference.Distribution");
            return distributionClass.isAssignableFrom(clazz);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Check if a class is a BEAST2 parameter
     */
    public static boolean isBeast2Parameter(Class<?> clazz) {
        // Check if the class implements Parameter interface or extends some Parameter class
        try {
            Class<?> parameterClass = Class.forName("beast.base.inference.parameter.Parameter");
            return parameterClass.isAssignableFrom(clazz);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Check if a class is a BEAST2 tree
     */
    public static boolean isBeast2Tree(Class<?> clazz) {
        // Check if the class implements Tree interface or extends some Tree class
        try {
            Class<?> treeClass = Class.forName("beast.base.evolution.tree.Tree");
            return treeClass.isAssignableFrom(clazz);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
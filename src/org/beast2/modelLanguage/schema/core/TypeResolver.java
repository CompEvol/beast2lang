package org.beast2.modelLanguage.schema.core;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Handles type resolution and naming for BEAST2 types
 */
public class TypeResolver {
    
    /**
     * Convert a Type to a readable string representation
     */
    public String resolveType(Type type) {
        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;
            
            // Handle arrays
            if (clazz.isArray()) {
                return getSimpleClassName(clazz.getComponentType()) + "[]";
            }
            
            // Handle primitives
            if (clazz.isPrimitive()) {
                return capitalizeFirstLetter(clazz.getSimpleName());
            }
            
            // Handle wrapper classes as primitives
            if (clazz == Integer.class) return "Integer";
            if (clazz == Double.class) return "Double";
            if (clazz == Boolean.class) return "Boolean";
            if (clazz == String.class) return "String";
            
            return getSimpleClassName(clazz);
            
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) type;
            Type rawType = pType.getRawType();
            
            if (rawType instanceof Class<?>) {
                Class<?> rawClass = (Class<?>) rawType;
                
                // Handle List<T> types
                if (List.class.isAssignableFrom(rawClass)) {
                    Type[] typeArgs = pType.getActualTypeArguments();
                    if (typeArgs.length > 0) {
                        String elementType = resolveType(typeArgs[0]);
                        return "List<" + elementType + ">";
                    }
                    return "List";
                }
                
                return getSimpleClassName(rawClass);
            }
        }
        
        // For complex types, try to extract something useful
        String typeName = type.toString();
        if (typeName.startsWith("class ")) {
            typeName = typeName.substring(6);
        }
        
        // Extract just the class name
        int lastDot = typeName.lastIndexOf('.');
        if (lastDot >= 0) {
            typeName = typeName.substring(lastDot + 1);
        }
        
        return typeName;
    }
    
    /**
     * Get a simple, readable class name for BEAST2 types
     */
    public String getSimpleClassName(Class<?> clazz) {
        // Handle inner classes (like Function$Constant -> Function.Constant)
        if (clazz.isMemberClass()) {
            return clazz.getDeclaringClass().getSimpleName() + "." + clazz.getSimpleName();
        }
        
        // Handle nested static classes that might not be detected as member classes
        String fullName = clazz.getName();
        if (fullName.contains("$")) {
            // Split on $ and convert to dot notation
            String[] parts = fullName.split("\\$");
            if (parts.length >= 2) {
                // Get simple name of outer class
                String outerClassName = parts[parts.length - 2];
                int lastDot = outerClassName.lastIndexOf('.');
                if (lastDot >= 0) {
                    outerClassName = outerClassName.substring(lastDot + 1);
                }
                
                // Get simple name of inner class
                String innerClassName = parts[parts.length - 1];
                
                return outerClassName + "." + innerClassName;
            }
        }
        
        String className = clazz.getSimpleName();
        
        // For empty class names (can happen with some generic types), use the full name
        if (className.isEmpty()) {
            return clazz.getName();
        }
        
        return className;
    }
    
    /**
     * Extract base type from potentially complex type strings like List<Tree> or Double[]
     */
    public String extractBaseType(String type) {
        // Handle array types
        if (type.endsWith("[]")) {
            return type.substring(0, type.length() - 2);
        }
        
        // Handle List<T> types
        if (type.startsWith("List<") && type.endsWith(">")) {
            return type.substring(5, type.length() - 1);
        }
        
        // Handle other generic types if needed
        int genericStart = type.indexOf('<');
        if (genericStart > 0) {
            return type.substring(0, genericStart);
        }
        
        return type;
    }
    
    /**
     * Capitalize first letter of a string
     */
    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
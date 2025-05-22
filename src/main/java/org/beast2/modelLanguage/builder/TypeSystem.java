package org.beast2.modelLanguage.builder;

import java.lang.reflect.Type;

/**
 * Interface for type checking and type-related operations.
 */
public interface TypeSystem {
    // Basic type checking
    boolean isModelObject(Object obj);
    boolean isStateNode(Object obj);
    boolean isParameter(Object obj);

    boolean isFunction(Object obj);

    boolean isDistribution(Object obj);
    boolean isParametricDistribution(Object obj);
    boolean isRealParameter(Object obj);

    // Class-based type checking
    boolean isParameterType(Class<?> type);
    boolean isParameterType(String typeName);

    // Type loading and resolution
    Class<?> loadClass(String className) throws ClassNotFoundException;
    boolean classExists(String className);

    // Type relationships
    Type getInputType(Object obj, String inputName);
}
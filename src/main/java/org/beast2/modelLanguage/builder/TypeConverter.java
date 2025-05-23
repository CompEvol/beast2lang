package org.beast2.modelLanguage.builder;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Interface for type conversion and autoboxing operations.
 */
public interface TypeConverter {
    // Autoboxing
    Object autobox(Object value, Type targetType, ObjectRegistry objectRegistry);
    boolean canAutobox(Object value, Type targetType);

    // Basic type conversions
    double convertToDouble(Object value);
    int convertToInteger(Object value);
    boolean convertToBoolean(Object value);
}
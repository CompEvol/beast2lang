package org.beast2.modelLanguage.builder;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Factory interface for creating and managing parameters.
 */
public interface ParameterFactory {
    /**
     * Enumeration of parameter types.
     */
    enum ParameterType {
        REAL("Real"),
        INTEGER("Integer"),
        BOOLEAN("Boolean");

        private final String typeName;

        ParameterType(String typeName) {
            this.typeName = typeName;
        }

        public String getTypeName() {
            return typeName;
        }

        public static ParameterType fromString(String type) {
            if (type == null) return null;
            String normalized = type.replaceAll("Parameter$", "");
            for (ParameterType pt : values()) {
                if (pt.typeName.equalsIgnoreCase(normalized) ||
                        pt.name().equalsIgnoreCase(normalized)) {
                    return pt;
                }
            }
            return null;
        }
    }

    // Parameter creation
    Object createParameter(ParameterType parameterType, Object value);
    Object createParameterForType(Object value, Type expectedType) throws Exception;
    Object createRealParameter(Double value) throws Exception;
    Object createIntegerParameter(Integer value) throws Exception;
    Object createBooleanParameter(Boolean value) throws Exception;

    // Parameter operations
    void initializeParameterValues(Object parameter, List<Double> values) throws Exception;
    int getParameterDimension(Object parameter);
    void setParameterValue(Object parameter, int index, double value) throws Exception;

    // Parameter-specific object creation
    Object createPriorForParametricDistribution(Object parameter, Object distribution, String priorId) throws Exception;
}
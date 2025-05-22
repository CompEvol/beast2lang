package org.beast2.modelLanguage.builder;

import org.beast2.modelLanguage.model.FunctionCall;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Interface for creating and managing model objects.
 * This abstraction allows the handler classes to be independent of BEAST classes.
 * Only includes methods needed by handlers - Beast2AnalysisBuilder can use BEAST classes directly.
 */
public interface ObjectFactory {

    /**
     * Enumeration of parameter types supported by the factory.
     * This provides type safety when creating parameters.
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

        /**
         * Get ParameterType from a string representation
         * @param type String representation of the type
         * @return The matching ParameterType, or null if not found
         */
        public static ParameterType fromString(String type) {
            if (type == null) return null;

            // Remove "Parameter" suffix if present
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

    // Generic object creation - used by handlers

    /**
     * Create an object of the specified class with the given ID.
     *
     * @param className Fully qualified class name of the object to create
     * @param id Unique identifier for the object (may be null)
     * @return The created object
     * @throws Exception if object creation fails
     */
    Object createObject(String className, String id) throws Exception;

    /**
     * Create a parameter object of the specified type with the given value.
     *
     * @param parameterType Type of parameter to create (use ParameterType enum)
     * @param value Initial value for the parameter
     * @return The created parameter object
     */
    Object createParameter(ParameterType parameterType, Object value);

    /**
     * Create an alignment object from a file.
     *
     * @param filePath Path to the alignment file (e.g., Nexus format)
     * @param id Unique identifier for the alignment
     * @return The created alignment object
     * @throws Exception if file reading or alignment creation fails
     */
    Object createAlignment(String filePath, String id) throws Exception;

    // Type checking methods used by handlers

    /**
     * Check if an object is a valid model object that can have inputs set.
     * For BEAST2, this means it implements BEASTInterface.
     *
     * @param obj The object to test
     * @return true if this object is a valid model object
     */
    boolean isModelObject(Object obj);

    /**
     * Check if an object is a state node (parameter that can be estimated).
     *
     * @param obj The object to test
     * @return true if this object is a state node
     */
    boolean isStateNode(Object obj);

    /**
     * Check if an object is a parameter.
     *
     * @param obj The object to test
     * @return true if this object is a parameter
     */
    boolean isParameter(Object obj);

    /**
     * Check if an object is a distribution.
     *
     * @param obj The object to test
     * @return true if this object is a distribution
     */
    boolean isDistribution(Object obj);

    /**
     * Check if an object is a parametric distribution.
     *
     * @param obj The object to test
     * @return true if this object is a parametric distribution
     */
    boolean isParametricDistribution(Object obj);

    // Object manipulation methods used by handlers

    /**
     * Set the ID of an object.
     *
     * @param obj The object whose ID to set
     * @param id The ID to set
     * @throws Exception if the object doesn't support IDs or setting fails
     */
    void setID(Object obj, String id) throws Exception;

    /**
     * Get the ID of an object.
     *
     * @param obj The object whose ID to get
     * @return The object's ID, or null if it has none
     * @throws Exception if getting the ID fails
     */
    String getID(Object obj) throws Exception;

    /**
     * Initialize and validate an object after all inputs have been set.
     *
     * @param obj The object to initialize
     * @throws Exception if initialization or validation fails
     */
    void initAndValidate(Object obj) throws Exception;

    /**
     * Set an input value on an object.
     *
     * @param obj The object whose input to set
     * @param inputName Name of the input to set
     * @param value Value to set for the input
     * @throws Exception if the object doesn't have this input or setting fails
     */
    void setInputValue(Object obj, String inputName, Object value) throws Exception;

    /**
     * Get an input value from an object.
     *
     * @param obj The object whose input to get
     * @param inputName Name of the input to get
     * @return The input value, or null if not set
     * @throws Exception if getting the input fails
     */
    Object getInputValue(Object obj, String inputName) throws Exception;

    /**
     * Get all inputs of an object.
     *
     * @param obj The object whose inputs to get
     * @return Map of input names to input objects
     */
    Map<String, Object> getInputs(Object obj);

    /**
     * Get the expected type for an input.
     *
     * @param obj The object containing the input
     * @param inputName Name of the input
     * @return The expected Type for the input, or null if unknown
     */
    Type getInputType(Object obj, String inputName);

    // Collection operations used by ReflectionBeast2ObjectFactory

    /**
     * Filter objects to get only distributions.
     *
     * @param objects Map of all objects
     * @return List of objects that are distributions
     */
    List<Object> getDistributions(Map<String, Object> objects);

    // Autoboxing support

    /**
     * Attempt to convert a value to the target type using autoboxing rules.
     *
     * @param value The value to convert
     * @param targetType The desired target type
     * @param objectRegistry Registry of existing objects (for references)
     * @return The converted value, or the original if no conversion is needed
     */
    Object autobox(Object value, Type targetType, Map<String, Object> objectRegistry);

    /**
     * Check if a value can be autoboxed to the target type.
     *
     * @param value The value to check
     * @param targetType The target type
     * @return true if autoboxing is possible
     */
    boolean canAutobox(Object value, Type targetType);

    // Class loading support

    /**
     * Load a class by name.
     *
     * @param className Fully qualified class name
     * @return The loaded Class object
     * @throws ClassNotFoundException if the class cannot be found
     */
    Class<?> loadClass(String className) throws ClassNotFoundException;

    /**
     * Check if a class exists and can be loaded.
     *
     * @param className Fully qualified class name
     * @return true if the class exists and can be loaded
     */
    boolean classExists(String className);

    // Package management support (for PackageUtils and NameResolver)

    /**
     * Get all available packages (both installed and available).
     *
     * @return Map of package names to package objects
     */
    Map<String, Object> getAllPackages();

    /**
     * Find all model object classes in a package.
     * For BEAST2, this finds all BEASTInterface implementations.
     *
     * @param packageName Name of the package to search
     * @return List of fully qualified class names
     */
    List<String> findModelObjectClasses(String packageName);

    /**
     * Get the primary input name for a distribution object.
     * For example, Prior uses "x", TreeLikelihood uses "data", etc.
     *
     * @param distributionObject The distribution object
     * @return The name of the primary input, or null if not found
     */
    String getPrimaryInputName(Object distributionObject);

    /**
     * Sample from a parametric distribution.
     *
     * @param distribution The distribution object to sample from
     * @param sampleSize Number of samples to generate
     * @return A 2D array of samples, or null if sampling fails
     */
    Double[][] sampleFromDistribution(Object distribution, int sampleSize);

    /**
     * Initialize a parameter with values.
     *
     * @param parameter The parameter object to initialize
     * @param values List of values to set
     * @throws Exception if initialization fails
     */
    void initializeParameterValues(Object parameter, List<Double> values) throws Exception;

    /**
     * Initialize an object using initByName with variable arguments.
     *
     * @param obj The object to initialize
     * @param args The arguments in the pattern: "name1", value1, "name2", value2, ...
     * @throws Exception if initialization fails
     */
    void initByName(Object obj, Object... args) throws Exception;

    /**
     * Get the dimension of a parameter.
     *
     * @param parameter The parameter object
     * @return The dimension, or 0 if it cannot be determined
     */
    int getParameterDimension(Object parameter);

    /**
     * Set a value at a specific index in a parameter.
     *
     * @param parameter The parameter object
     * @param index The index to set
     * @param value The value to set
     * @throws Exception if setting fails
     */
    void setParameterValue(Object parameter, int index, double value) throws Exception;

    // Input manipulation methods from BEASTUtils
    Map<String, Object> buildInputMap(Object obj) throws Exception;
    void configureFromFunctionCall(Object obj, FunctionCall funcCall,
                                   Map<String, Object> objectRegistry) throws Exception;
    boolean connectToFirstMatchingInput(Object source, Object target,
                                        String[] inputNames) throws Exception;

    // Type checking methods
    boolean isParameterType(Class<?> type);
    boolean isParameterType(String typeName);

    // Parameter creation methods
    Object createParameterForType(Object value, Type expectedType) throws Exception;
    Object createRealParameter(Double value) throws Exception;
    Object createIntegerParameter(Integer value) throws Exception;
    Object createBooleanParameter(Boolean value) throws Exception;

    // Utility methods
    double convertToDouble(Object value);
    int convertToInteger(Object value);
    boolean convertToBoolean(Object value);

    // Type-safe prior creation
    Object createPriorForParametricDistribution(Object parameter, Object distribution, String priorId) throws Exception;

    // Type-safe parameter type checking
    boolean isRealParameterType(Object obj);
}
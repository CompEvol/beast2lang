package org.beast2.modelLanguage.beast;

import beast.base.core.BEASTInterface;
import beast.base.core.Function;
import beast.base.core.Input;
import beast.base.evolution.likelihood.GenericTreeLikelihood;
import beast.base.evolution.tree.MRCAPrior;
import beast.base.evolution.tree.TreeDistribution;
import beast.base.evolution.tree.coalescent.BayesianSkyline;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.inference.Distribution;
import beast.base.inference.StateNode;
import beast.base.inference.distribution.MarkovChainDistribution;
import beast.base.inference.distribution.ParametricDistribution;
import beast.base.inference.distribution.Prior;
import beast.base.inference.parameter.BooleanParameter;
import beast.base.inference.parameter.IntegerParameter;
import beast.base.inference.parameter.Parameter;
import beast.base.inference.parameter.RealParameter;
import beast.base.parser.NexusParser;
import beast.pkgmgmt.BEASTClassLoader;
import beast.pkgmgmt.Package;
import beast.pkgmgmt.PackageManager;
import org.beast2.modelLanguage.builder.ModelObjectFactory;
import org.beast2.modelLanguage.builder.ObjectRegistry;
import org.beast2.modelLanguage.builder.handlers.ExpressionResolver;
import org.beast2.modelLanguage.model.Argument;
import org.beast2.modelLanguage.model.FunctionCall;

import java.io.File;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * BEAST2 implementation of the ObjectFactory interface.
 * This class consolidates ALL BEAST2 framework dependencies for the handler classes.
 * <p>
 * All handler and utility classes should
 * use this factory through the ObjectFactory interface.
 *
 * @author Alexei Drummond
 * @version 1.0
 */
public class BeastObjectFactory implements ModelObjectFactory {

    private static final Logger logger = Logger.getLogger(BeastObjectFactory.class.getName());

    /**
     * Cache for loaded classes to improve performance
     */
    private static final Map<String, Class<?>> CLASS_CACHE = new HashMap<>();

    /**
     * Map from distribution classes to their primary input names
     */
    private static final Map<Class<?>, String> DISTRIBUTION_CLASS_TO_ARGUMENT_INPUT = new HashMap<>();

    // Initialize the map with known mappings
    static {
        DISTRIBUTION_CLASS_TO_ARGUMENT_INPUT.put(Prior.class, "x");
        DISTRIBUTION_CLASS_TO_ARGUMENT_INPUT.put(Coalescent.class, "treeIntervals");
        DISTRIBUTION_CLASS_TO_ARGUMENT_INPUT.put(BayesianSkyline.class, "treeIntervals");
        DISTRIBUTION_CLASS_TO_ARGUMENT_INPUT.put(TreeDistribution.class, "tree");
        DISTRIBUTION_CLASS_TO_ARGUMENT_INPUT.put(MRCAPrior.class, "tree");
        DISTRIBUTION_CLASS_TO_ARGUMENT_INPUT.put(GenericTreeLikelihood.class, "data");
        DISTRIBUTION_CLASS_TO_ARGUMENT_INPUT.put(MarkovChainDistribution.class, "parameter");
    }

    /**
     * Create an object of the specified class with the given ID.
     * Uses BEAST's class loader to ensure proper loading of plugin classes.
     */
    @Override
    public Object createObject(String className, String id) throws Exception {
        Class<?> clazz = loadClass(className);
        Object obj = clazz.getDeclaredConstructor().newInstance();
        if (id != null) {
            setID(obj, id);
        }
        return obj;
    }

    /**
     * Create a parameter object based on the ParameterType enum.
     * Supports RealParameter, IntegerParameter, and BooleanParameter.
     */
    @Override
    public Object createParameter(ParameterType parameterType, Object value) {
        if (parameterType == null) {
            // Try to infer type from value
            if (value instanceof Boolean) {
                parameterType = ParameterType.BOOLEAN;
            } else if (value instanceof Integer) {
                parameterType = ParameterType.INTEGER;
            } else {
                parameterType = ParameterType.REAL;
            }
        }

        switch (parameterType) {
            case REAL:
                return new RealParameter(String.valueOf(value));
            case INTEGER:
                if (value instanceof Integer) {
                    return new IntegerParameter(new Integer[]{(Integer) value});
                } else {
                    return new IntegerParameter(new Integer[]{Integer.parseInt(String.valueOf(value))});
                }
            case BOOLEAN:
                if (value instanceof Boolean) {
                    return new BooleanParameter(new Boolean[]{(Boolean) value});
                } else {
                    return new BooleanParameter(new Boolean[]{Boolean.parseBoolean(String.valueOf(value))});
                }
            default:
                throw new IllegalArgumentException("Unknown parameter type: " + parameterType);
        }
    }

    /**
     * Create an alignment from a Nexus file.
     * Uses BEAST's NexusParser to read the file.
     */
    @Override
    public Object createAlignment(String filePath, String id) throws Exception {
        NexusParser parser = new NexusParser();
        parser.parseFile(new File(filePath));

        if (parser.m_alignment == null) {
            throw new RuntimeException("No alignment found in Nexus file: " + filePath);
        }

        parser.m_alignment.setID(id);
        return parser.m_alignment;
    }

    /**
     * Create an alignment from inline sequence data.
     * Uses BEAST's Alignment and Sequence classes to build the alignment programmatically.
     */
    @Override
    public Object createAlignmentFromSequences(Map<String, String> sequences, String dataType, String alignmentId) throws Exception {
        if (sequences == null || sequences.isEmpty()) {
            throw new IllegalArgumentException("Sequences map cannot be null or empty");
        }

        // Create a new Alignment object
        beast.base.evolution.alignment.Alignment alignment = new beast.base.evolution.alignment.Alignment();

        // Create Sequence objects for each taxon
        List<beast.base.evolution.alignment.Sequence> sequenceList = new ArrayList<>();

        for (Map.Entry<String, String> entry : sequences.entrySet()) {
            String taxonName = entry.getKey();
            String sequenceData = entry.getValue();

            // Create a Sequence object
            beast.base.evolution.alignment.Sequence sequence = new beast.base.evolution.alignment.Sequence();
            sequence.initByName(
                    "taxon", taxonName,
                    "value", sequenceData
            );

            sequenceList.add(sequence);
        }

        // Determine the data type
        String dataTypeString = dataType.toLowerCase();
        Object dataTypeObject = null;

        switch (dataTypeString) {
            case "nucleotide":
            case "dna":
                dataTypeObject = new beast.base.evolution.datatype.Nucleotide();
                break;
            case "aminoacid":
            case "protein":
                dataTypeObject = new beast.base.evolution.datatype.Aminoacid();
                break;
            case "binary":
                dataTypeObject = new beast.base.evolution.datatype.Binary();
                break;
            case "integer":
                dataTypeObject = new beast.base.evolution.datatype.IntegerData();
                break;
            default:
                // Try to load as a class name
                try {
                    Class<?> dataTypeClass = loadClass(dataType);
                    dataTypeObject = dataTypeClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    logger.warning("Unknown data type: " + dataType + ", defaulting to nucleotide");
                    dataTypeObject = new beast.base.evolution.datatype.Nucleotide();
                }
        }

        // Initialize the alignment with sequences and data type
        alignment.initByName(
                "sequence", sequenceList,
                "dataType", dataTypeObject
        );

        // Set the ID
        alignment.setID(alignmentId);

        logger.info("Created alignment '" + alignmentId + "' with " + sequences.size() +
                " sequences of type " + dataType);

        return alignment;
    }

    // Type checking methods

    @Override
    public boolean isModelObject(Object obj) {
        return obj instanceof BEASTInterface;
    }

    @Override
    public boolean isStateNode(Object obj) {
        return obj instanceof StateNode;
    }

    @Override
    public boolean isParameter(Object obj) {
        return obj instanceof Parameter;
    }

    public boolean isFunction(Object obj) {
        return obj instanceof Function;
    }

    public boolean isFunctionClass(Class c) {
        return Function.class.isAssignableFrom(c);
    }

    @Override
    public boolean isDistribution(Object obj) {
        return obj instanceof Distribution;
    }

    @Override
    public boolean isParametricDistribution(Object obj) {
        return obj instanceof ParametricDistribution;
    }

    // Object manipulation methods

    /**
     * Set the ID of a BEAST object using reflection.
     * Most BEAST objects have a setID(String) method.
     */
    @Override
    public void setID(Object obj, String id) throws Exception {
        obj.getClass().getMethod("setID", String.class).invoke(obj, id);
    }

    /**
     * Get the ID of a BEAST object using reflection.
     * Most BEAST objects have a getID() method.
     */
    @Override
    public String getID(Object obj) throws Exception {
        return (String) obj.getClass().getMethod("getID").invoke(obj);
    }

    /**
     * Initialize and validate a BEAST object.
     * This should be called after all inputs have been set.
     */
    @Override
    public void initAndValidate(Object obj) throws Exception {
        obj.getClass().getMethod("initAndValidate").invoke(obj);
    }

    /**
     * Set an input value on a BEASTInterface object.
     */
    @Override
    public void setInputValue(Object obj, String inputName, Object value) throws Exception {
        if (!(obj instanceof BEASTInterface)) {
            throw new IllegalArgumentException("Object must be a BEASTInterface");
        }

        BEASTInterface beastObj = (BEASTInterface) obj;
        Input<?> input = beastObj.getInput(inputName);

        if (input == null) {
            throw new IllegalArgumentException("No input named '" + inputName + "' found in " +
                    obj.getClass().getSimpleName());
        }

        BEASTUtils.setInputValue(input, value, beastObj);
    }

    /**
     * Get an input value from a BEASTInterface object.
     */
    @Override
    public Object getInputValue(Object obj, String inputName) throws Exception {
        if (!(obj instanceof BEASTInterface)) {
            return null;
        }

        BEASTInterface beastObj = (BEASTInterface) obj;
        Input<?> input = beastObj.getInput(inputName);

        return input != null ? input.get() : null;
    }

    /**
     * Get all inputs of a BEASTInterface object.
     */
    @Override
    public Map<String, Object> getInputs(Object obj) {
        if (obj instanceof BEASTInterface) {
            BEASTInterface beastObj = (BEASTInterface) obj;
            return new HashMap<>(beastObj.getInputs());
        }
        return new HashMap<>();
    }

    /**
     * Get the expected type for an input using reflection.
     */
    @Override
    public Type getInputType(Object obj, String inputName) {
        if (!(obj instanceof BEASTInterface)) {
            return null;
        }

        try {
            BEASTInterface beastObj = (BEASTInterface) obj;
            Input<?> input = beastObj.getInput(inputName);
            if (input != null) {
                return BEASTUtils.getInputExpectedType(input, beastObj, inputName);
            }
        } catch (Exception e) {
            logger.warning("Failed to get input type for '" + inputName + "': " + e.getMessage());
        }

        return null;
    }

    // Collection operations

    /**
     * Filter objects to get only Distribution instances.
     */
    @Override
    public List<Object> getDistributions(Map<String, Object> objects) {
        return objects.values().stream()
                .filter(obj -> obj instanceof Distribution)
                .collect(Collectors.toList());
    }

    // Autoboxing support

    /**
     * Delegate autoboxing to the AutoboxingRegistry.
     */
    @Override
    public Object autobox(Object value, Type targetType, ObjectRegistry objectRegistry) {
        return AutoboxingRegistry.getInstance().autobox(value, targetType, objectRegistry);
    }

    /**
     * Check if autoboxing is needed and possible.
     */
    @Override
    public boolean canAutobox(Object value, Type targetType) {
        return !AutoboxingRegistry.isDirectlyAssignable(value, targetType);
    }

    /**
     * Load a class using BEAST's class loader.
     * This ensures that plugin classes are properly loaded.
     */
    @Override
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        if (CLASS_CACHE.containsKey(className)) {
            return CLASS_CACHE.get(className);
        }

        Class<?> clazz = BEASTClassLoader.forName(className);
        CLASS_CACHE.put(className, clazz);
        return clazz;
    }

    /**
     * Check if a class exists without throwing an exception.
     */
    @Override
    public boolean classExists(String className) {
        try {
            loadClass(className);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Package management support

    /**
     * Get all BEAST packages (both installed and available).
     * Used by PackageUtils to discover available functionality.
     */
    @Override
    public Map<String, Object> getAllPlugins() {
        Map<String, Package> packageMap = new TreeMap<>(PackageManager::comparePackageNames);
        PackageManager.addInstalledPackages(packageMap);
        try {
            PackageManager.addAvailablePackages(packageMap);
        } catch (Exception e) {
            logger.warning("Could not retrieve available packages: " + e.getMessage());
        }
        return new TreeMap<>(packageMap);
    }

    /**
     * Find all BEASTInterface implementations in a package.
     * Used by NameResolver for import resolution.
     */
    @Override
    public List<String> findModelObjectClasses(String packageName) {
        return PackageManager.find(BEASTInterface.class, packageName.toLowerCase());
    }

    /**
     * Get the primary input name for a distribution object.
     * Searches through the class hierarchy to find a match.
     */
    @Override
    public String getPrimaryInputName(Object distributionObject) {
        if (distributionObject == null) {
            return null;
        }

        // Search for the most specific class match in our map
        Class<?> distClass = distributionObject.getClass();
        while (distClass != null) {
            if (DISTRIBUTION_CLASS_TO_ARGUMENT_INPUT.containsKey(distClass)) {
                return DISTRIBUTION_CLASS_TO_ARGUMENT_INPUT.get(distClass);
            }

            // Check interfaces as well
            for (Class<?> iface : distClass.getInterfaces()) {
                if (DISTRIBUTION_CLASS_TO_ARGUMENT_INPUT.containsKey(iface)) {
                    return DISTRIBUTION_CLASS_TO_ARGUMENT_INPUT.get(iface);
                }
            }

            // Move up the class hierarchy
            distClass = distClass.getSuperclass();
        }

        return null;
    }

    /**
     * Sample from a parametric distribution.
     */
    @Override
    public Double[][] sampleFromDistribution(Object distribution, int sampleSize) {
        if (!isParametricDistribution(distribution)) {
            return null;
        }

        try {

            ParametricDistribution parametricDistribution = (ParametricDistribution)distribution;

            return parametricDistribution.sample(1);
        } catch (Exception e) {
            logger.warning("Failed to sample from distribution: " + e.getMessage());
            return null;
        }
    }

    /**
     * Initialize a parameter with values.
     */
    @Override
    public void initializeParameterValues(Object parameter, List<Double> values) throws Exception {
        if (!isParameter(parameter)) {
            throw new IllegalArgumentException("Object is not a parameter");
        }

        // initByName is a varargs method, so we need to call it with Object... args
        // The pattern is: initByName("inputName1", value1, "inputName2", value2, ...)
        parameter.getClass()
                .getMethod("initByName", Object[].class)
                .invoke(parameter, new Object[] { new Object[] { "value", values } });
    }

    /**
     * Initialize an object using initByName with variable arguments.
     */
    @Override
    public void initByName(Object obj, Object... args) throws Exception {
        // initByName in BEAST2 is a varargs method
        obj.getClass()
                .getMethod("initByName", Object[].class)
                .invoke(obj, new Object[] { args });
    }

    /**
     * Get the dimension of a parameter.
     */
    @Override
    public int getParameterDimension(Object parameter) {
        if (!isParameter(parameter)) {
            return 0;
        }

        try {
            return (Integer) parameter.getClass()
                    .getMethod("getDimension")
                    .invoke(parameter);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Set a value at a specific index in a parameter.
     */
    @Override
    public void setParameterValue(Object parameter, int index, double value) throws Exception {
        if (!isParameter(parameter)) {
            throw new IllegalArgumentException("Object is not a parameter");
        }

        parameter.getClass()
                .getMethod("setValue", int.class, double.class)
                .invoke(parameter, index, value);
    }

    // Add these implementations to BeastObjectFactoryImpl.java

    @Override
    public void configureFromFunctionCall(Object obj, FunctionCall funcCall,
                                          ObjectRegistry objectRegistry) throws Exception {
        if (!isModelObject(obj)) {
            return;
        }

        BEASTInterface beastObject = (BEASTInterface) obj;
        Map<String, Input<?>> inputMap = BEASTUtils.buildInputMap(obj, obj.getClass());

        for (Argument arg : funcCall.getArguments()) {
            String name = arg.getName();
            Input<?> input = inputMap.get(name);

            if (input == null) {
                logger.warning("No input named '" + name + "' found");
                continue;
            }

            // Resolve value with potential autoboxing
            Type expectedType = BEASTUtils.getInputExpectedType(input, beastObject, name);
            Object argValue = ExpressionResolver.resolveValueWithAutoboxing(
                    arg.getValue(), objectRegistry, expectedType);

            try {
                BEASTUtils.setInputValue(input, argValue, beastObject);
            } catch (Exception e) {
                logger.warning("Failed to set input '" + name + "': " + e.getMessage());
            }
        }
    }

    @Override
    public Map<String, Object> buildInputMap(Object obj) throws Exception {
        if (!isModelObject(obj)) {
            return new HashMap<>();
        }
        return new HashMap<>(BEASTUtils.buildInputMap(obj, obj.getClass()));
    }

    @Override
    public Object createParameterForType(Object value, Type expectedType) throws Exception {
        return BEASTUtils.createParameterForType(value, expectedType);
    }

    @Override
    public Object createRealParameter(Double value) throws Exception {
        return BEASTUtils.createRealParameter(value);
    }

    @Override
    public Object createIntegerParameter(Integer value) throws Exception {
        return BEASTUtils.createIntegerParameter(value);
    }

    @Override
    public Object createBooleanParameter(Boolean value) throws Exception {
        return BEASTUtils.createBooleanParameter(value);
    }

    @Override
    public double convertToDouble(Object value) {
        return BEASTUtils.convertToDouble(value);
    }

    @Override
    public int convertToInteger(Object value) {
        return BEASTUtils.convertToInteger(value);
    }

    @Override
    public boolean convertToBoolean(Object value) {
        return BEASTUtils.convertToBoolean(value);
    }

    @Override
    public boolean isParameterType(Class<?> type) {
        return BEASTUtils.isParameterType(type);
    }

    @Override
    public boolean isParameterType(String typeName) {
        return BEASTUtils.isParameterType(typeName);
    }

    @Override
    public boolean connectToFirstMatchingInput(Object source, Object target,
                                               String[] inputNames) throws Exception {
        if (!isModelObject(target)) {
            return false;
        }
        return BEASTUtils.connectToFirstMatchingInput(source, (BEASTInterface) target, inputNames);
    }

    @Override
    public Object createPriorForParametricDistribution(Object parameter, Object distribution, String priorId) throws Exception {
        // Here's where the BEAST-specific Prior class is used
        Prior prior = new Prior();
        prior.setID(priorId);
        prior.initByName("x", parameter, "distr", distribution);
        return prior;
    }

    @Override
    public boolean isRealParameter(Object obj) {
        return obj instanceof RealParameter;
    }
}
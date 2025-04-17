package org.beast2.modelLanguage.builder.handlers;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;

import org.beast2.modelLanguage.data.NexusAlignment;
import org.beast2.modelLanguage.model.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Handler for DistributionAssignment statements, responsible for creating BEAST2 objects
 * with associated distributions.
 * This implementation supports the @observed annotation with data references and
 * flexibly handles inputs that should be set on random variables rather than distributions.
 */
public class DistributionAssignmentHandler {

    private static final Logger logger = Logger.getLogger(DistributionAssignmentHandler.class.getName());

    /**
     * Create BEAST2 objects from a distribution assignment
     *
     * @param distAssign     the distribution assignment to process
     * @param objectRegistry registry of created objects for reference resolution
     * @throws Exception if object creation fails
     */
    public void createObjects(DistributionAssignment distAssign, Map<String, Object> objectRegistry) throws Exception {
        String className = distAssign.getClassName();
        String varName = distAssign.getVariableName();
        Expression distribution = distAssign.getDistribution();

        logger.info("Creating random variable: " + varName);

        // Create the parameter object
        Class<?> beastClass = Class.forName(className);
        Object beastObject = beastClass.getDeclaredConstructor().newInstance();
        setObjectId(beastObject, beastClass, varName);

        // Special handling for RealParameter
        if (beastClass.getName().equals("beast.base.inference.parameter.RealParameter")) {
            initializeRealParameter(beastObject, beastClass);
        }

        // Store the parameter object
        objectRegistry.put(varName, beastObject);

        // If no distribution is specified, we're done
        if (!(distribution instanceof FunctionCall)) {
            logger.info("No distribution specified for " + varName);
            return;
        }

        // Create the distribution object
        FunctionCall funcCall = (FunctionCall) distribution;
        String distClassName = funcCall.getClassName();

        // Special handling for TreeLikelihood
        if (distClassName.equals("beast.base.evolution.likelihood.TreeLikelihood")) {
            createTreeLikelihood(varName, funcCall, beastObject, objectRegistry);
            return;
        }

        Class<?> distClass = Class.forName(distClassName);
        Object distObject = distClass.getDeclaredConstructor().newInstance();
        setObjectId(distObject, distClass, varName + "Prior");

        // Configure the distribution and parameter object with function call arguments
        configureObjects(distObject, beastObject, funcCall, objectRegistry);

        // Store the distribution object
        objectRegistry.put(varName + "Prior", distObject);
    }

    /**
     * Create BEAST2 objects from a distribution assignment with observed data reference
     *
     * @param distAssign     the distribution assignment to process
     * @param objectRegistry registry of created objects for reference resolution
     * @param dataRef        the name of the variable annotated with @data
     * @throws Exception if object creation fails
     */
    public void createObservedObjects(DistributionAssignment distAssign, Map<String, Object> objectRegistry, String dataRef) throws Exception {
        String className = distAssign.getClassName();
        String varName = distAssign.getVariableName();
        Expression distribution = distAssign.getDistribution();

        logger.info("Creating observed random variable: " + varName + " with data reference: " + dataRef);

        // Get the referenced data object
        Object dataObject = objectRegistry.get(dataRef);
        if (dataObject == null) {
            throw new IllegalArgumentException("Data reference '" + dataRef + "' not found in object registry");
        }

        // Check that the data object is type-compatible with the declaration
        Class<?> expectedClass = Class.forName(className);
        if (!expectedClass.isInstance(dataObject)) {
            throw new IllegalArgumentException(
                    "Data reference '" + dataRef + "' of type " + dataObject.getClass().getName() +
                            " is not compatible with expected type " + className
            );
        }

        // Use the same data object for the observed variable
        objectRegistry.put(varName, dataObject);
        logger.info("Using data object " + dataRef + " for observed variable " + varName);

        // If no distribution is specified, we're done
        if (!(distribution instanceof FunctionCall)) {
            logger.info("No distribution specified for observed variable " + varName);
            return;
        }

        // Create and configure the likelihood object
        FunctionCall funcCall = (FunctionCall) distribution;
        String likelihoodClassName = funcCall.getClassName();

        // Use special handling for TreeLikelihood
        if (likelihoodClassName.equals("beast.base.evolution.likelihood.TreeLikelihood")) {
            createTreeLikelihood(varName, funcCall, dataObject, objectRegistry);
        } else {
            // General case for other likelihoods
            createLikelihood(likelihoodClassName, varName, funcCall, dataObject, objectRegistry);
        }
    }

    /**
     * Create a TreeLikelihood object and configure it
     */
    private void createTreeLikelihood(String varName, FunctionCall funcCall, Object dataObject, Map<String, Object> objectRegistry) throws Exception {
        logger.info("Creating TreeLikelihood for: " + varName);

        // Create the TreeLikelihood object
        Class<?> treeLikelihoodClass = Class.forName("beast.base.evolution.likelihood.TreeLikelihood");
        Object treeLikelihood = treeLikelihoodClass.getDeclaredConstructor().newInstance();

        // Set the ID
        setObjectId(treeLikelihood, treeLikelihoodClass, varName + "Likelihood");

        // Build input maps
        Map<String, Input<?>> likelihoodInputMap = buildInputMap(treeLikelihood, treeLikelihoodClass);
        Map<String, Input<?>> dataInputMap = null;
        if (dataObject instanceof BEASTInterface) {
            dataInputMap = buildInputMap(dataObject, dataObject.getClass());
        }

        // Connect the data object to the appropriate input
        Input<?> dataInput = likelihoodInputMap.get("data");
        if (dataInput != null) {
            @SuppressWarnings("unchecked")
            Input<Object> typedInput = (Input<Object>) dataInput;
            typedInput.setValue(dataObject, (BEASTInterface) treeLikelihood);
            logger.fine("Connected " + varName + " to TreeLikelihood's 'data' input");
        } else {
            logger.warning("Could not find 'data' input on TreeLikelihood");
        }

        // Configure function call arguments
        for (Argument arg : funcCall.getArguments()) {
            String name = arg.getName();
            if ("data".equals(name)) continue; // Skip 'data' as we've already handled it

            try {
                Object argValue = ExpressionResolver.resolveValue(arg.getValue(), objectRegistry);
                Input<?> likelihoodInput = likelihoodInputMap.get(name);

                if (likelihoodInput != null) {
                    // Set input on likelihood object
                    @SuppressWarnings("unchecked")
                    Input<Object> typedInput = (Input<Object>) likelihoodInput;
                    typedInput.setValue(argValue, (BEASTInterface) treeLikelihood);
                    logger.fine("Set TreeLikelihood input '" + name + "' to " + argValue);
                } else if (dataInputMap != null) {
                    // Try to set input on data object if not found in likelihood
                    Input<?> dataObjectInput = dataInputMap.get(name);
                    if (dataObjectInput != null) {
                        @SuppressWarnings("unchecked")
                        Input<Object> typedInput = (Input<Object>) dataObjectInput;
                        typedInput.setValue(argValue, (BEASTInterface) dataObject);
                        logger.fine("Set data object input '" + name + "' to " + argValue);
                    } else {
                        logger.warning("Input '" + name + "' not found in TreeLikelihood or data object");
                    }
                } else {
                    logger.warning("Input '" + name + "' not found in TreeLikelihood");
                }
            } catch (Exception e) {
                logger.warning("Could not set input '" + name + "': " + e.getMessage());
            }
        }

        // Initialize both objects
        callInitAndValidate(treeLikelihood, treeLikelihoodClass);
        if (dataObject instanceof BEASTInterface) {
            callInitAndValidate(dataObject, dataObject.getClass());
        }

        // Store the TreeLikelihood
        objectRegistry.put(varName + "Likelihood", treeLikelihood);
        logger.info("Created and stored TreeLikelihood for: " + varName);
    }

    /**
     * Create a general likelihood object
     */
    private void createLikelihood(String className, String varName, FunctionCall funcCall,
                                  Object dataObject, Map<String, Object> objectRegistry) throws Exception {
        Class<?> likelihoodClass = Class.forName(className);
        Object likelihoodObject = likelihoodClass.getDeclaredConstructor().newInstance();

        // Set ID
        setObjectId(likelihoodObject, likelihoodClass, varName + "Likelihood");

        // Build input maps
        Map<String, Input<?>> likelihoodInputMap = buildInputMap(likelihoodObject, likelihoodClass);
        Map<String, Input<?>> dataInputMap = null;
        if (dataObject instanceof BEASTInterface) {
            dataInputMap = buildInputMap(dataObject, dataObject.getClass());
        }

        // Connect data object to the likelihood
        boolean dataConnected = connectDataToLikelihood(dataObject, likelihoodObject, likelihoodInputMap);

        // Configure other arguments from function call
        for (Argument arg : funcCall.getArguments()) {
            String name = arg.getName();

            // Skip data inputs we've already handled
            if (dataConnected && isDataInputName(name)) {
                continue;
            }

            Object argValue = ExpressionResolver.resolveValue(arg.getValue(), objectRegistry);
            Input<?> likelihoodInput = likelihoodInputMap.get(name);

            if (likelihoodInput != null) {
                // Set input on likelihood
                @SuppressWarnings("unchecked")
                Input<Object> typedInput = (Input<Object>) likelihoodInput;
                typedInput.setValue(argValue, (BEASTInterface) likelihoodObject);
                logger.fine("Set likelihood input '" + name + "' to " + argValue);
            } else if (dataInputMap != null) {
                // Try to set on data object if not found in likelihood
                Input<?> dataObjectInput = dataInputMap.get(name);
                if (dataObjectInput != null) {
                    @SuppressWarnings("unchecked")
                    Input<Object> typedInput = (Input<Object>) dataObjectInput;
                    typedInput.setValue(argValue, (BEASTInterface) dataObject);
                    logger.fine("Set data object input '" + name + "' to " + argValue);
                } else {
                    logger.warning("Input '" + name + "' not found in likelihood or data object");
                }
            } else {
                logger.warning("Input '" + name + "' not found in " + className);
            }
        }

        // Initialize both objects
        callInitAndValidate(likelihoodObject, likelihoodClass);
        if (dataObject instanceof BEASTInterface) {
            callInitAndValidate(dataObject, dataObject.getClass());
        }

        // Store the likelihood object
        objectRegistry.put(varName + "Likelihood", likelihoodObject);
        logger.info("Created and stored likelihood " + varName + "Likelihood");
    }

    /**
     * Connect data object to likelihood using common input names
     */
    private boolean connectDataToLikelihood(Object dataObject, Object likelihoodObject, Map<String, Input<?>> inputMap) {
        // Try common input names for data objects
        String[] dataInputNames = {"data", "patterns", "alignment", "x"};
        for (String inputName : dataInputNames) {
            Input<?> input = inputMap.get(inputName);
            if (input != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Input<Object> typedInput = (Input<Object>) input;
                    typedInput.setValue(dataObject, (BEASTInterface) likelihoodObject);
                    logger.fine("Connected data object to likelihood's '" + inputName + "' input");
                    return true;
                } catch (Exception e) {
                    logger.warning("Failed to connect data to input '" + inputName + "': " + e.getMessage());
                }
            }
        }

        logger.warning("Could not find appropriate input for data object");
        return false;
    }

    /**
     * Check if an input name is commonly used for data objects
     */
    private boolean isDataInputName(String name) {
        return name.equals("data") || name.equals("patterns") ||
                name.equals("alignment") || name.equals("x");
    }

    /**
     * Configure distribution and parameter objects with function call arguments
     */
    private void configureObjects(Object distObject, Object paramObject, FunctionCall funcCall,
                                  Map<String, Object> objectRegistry) throws Exception {
        // Build input maps for both objects
        Map<String, Input<?>> distInputMap = buildInputMap(distObject, distObject.getClass());
        Map<String, Input<?>> paramInputMap = null;
        if (paramObject instanceof BEASTInterface) {
            paramInputMap = buildInputMap(paramObject, paramObject.getClass());
        }

        // Connect parameter to appropriate input in distribution
        boolean parameterConnected = connectParameterToDistribution(paramObject, distObject, distInputMap);

        // Process all function call arguments
        for (Argument arg : funcCall.getArguments()) {
            String name = arg.getName();

            // Skip parameter inputs we've already handled
            if (parameterConnected && isParameterInputName(name, paramObject)) {
                continue;
            }

            Object argValue = ExpressionResolver.resolveValue(arg.getValue(), objectRegistry);

            // Try to set input on distribution first
            Input<?> distInput = distInputMap.get(name);
            if (distInput != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Input<Object> typedInput = (Input<Object>) distInput;
                    typedInput.setValue(argValue, (BEASTInterface) distObject);
                    logger.fine("Set distribution input '" + name + "' to " + argValue);
                    continue;  // Successfully set on distribution, proceed to next argument
                } catch (Exception e) {
                    logger.warning("Failed to set input '" + name + "' on distribution: " + e.getMessage());
                }
            }

            // If setting on distribution failed or input not found, try parameter object
            if (paramInputMap != null) {
                Input<?> paramInput = paramInputMap.get(name);
                if (paramInput != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        Input<Object> typedInput = (Input<Object>) paramInput;
                        typedInput.setValue(argValue, (BEASTInterface) paramObject);
                        logger.fine("Set parameter input '" + name + "' to " + argValue);
                        continue;  // Successfully set on parameter, proceed to next argument
                    } catch (Exception e) {
                        logger.warning("Failed to set input '" + name + "' on parameter: " + e.getMessage());
                    }
                }
            }

            // If we get here, the input wasn't found or couldn't be set on either object
            logger.warning("Input '" + name + "' not found in distribution or parameter object");
        }

        // Initialize both objects
        callInitAndValidate(distObject, distObject.getClass());
        if (paramObject instanceof BEASTInterface) {
            callInitAndValidate(paramObject, paramObject.getClass());
        }
    }

    /**
     * Connect parameter object to the appropriate input in the distribution
     */
    private boolean connectParameterToDistribution(Object paramObject, Object distObject,
                                                   Map<String, Input<?>> distInputMap) {
        // 1. For Prior-like distributions, connect to 'x' input
        Input<?> xInput = distInputMap.get("x");
        if (xInput != null) {
            try {
                @SuppressWarnings("unchecked")
                Input<Object> typedXInput = (Input<Object>) xInput;
                typedXInput.setValue(paramObject, (BEASTInterface) distObject);
                logger.fine("Connected parameter to distribution's 'x' input");
                return true;
            } catch (Exception e) {
                logger.warning("Failed to connect to 'x' input: " + e.getMessage());
            }
        }

        // 2. For tree distributions, connect to 'tree' input if parameter is a Tree
        if (paramObject instanceof beast.base.evolution.tree.Tree) {
            Input<?> treeInput = distInputMap.get("tree");
            if (treeInput != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Input<Object> typedTreeInput = (Input<Object>) treeInput;
                    typedTreeInput.setValue(paramObject, (BEASTInterface) distObject);
                    logger.fine("Connected Tree to distribution's 'tree' input");
                    return true;
                } catch (Exception e) {
                    logger.warning("Failed to connect to 'tree' input: " + e.getMessage());
                }
            }
        }

        // 3. Try other common input names
        String[] commonInputNames = {"data", "taxonset", "network", "trait", "patterns"};
        for (String inputName : commonInputNames) {
            Input<?> input = distInputMap.get(inputName);
            if (input != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Input<Object> typedInput = (Input<Object>) input;
                    typedInput.setValue(paramObject, (BEASTInterface) distObject);
                    logger.fine("Connected parameter to distribution's '" + inputName + "' input");
                    return true;
                } catch (Exception e) {
                    // Continue to next input name
                }
            }
        }

        logger.warning("Could not find appropriate input for parameter in distribution");
        return false;
    }

    /**
     * Check if an input name is commonly used for parameter objects
     */
    private boolean isParameterInputName(String name, Object paramObject) {
        if (name.equals("x") || name.equals("parameter")) {
            return true;
        }

        if (paramObject instanceof beast.base.evolution.tree.Tree &&
                (name.equals("tree") || name.equals("treeModel"))) {
            return true;
        }

        return false;
    }

    /**
     * Set object ID
     */
    private void setObjectId(Object object, Class<?> clazz, String id) {
        try {
            Method setId = clazz.getMethod("setID", String.class);
            setId.invoke(object, id);
        } catch (NoSuchMethodException e) {
            // Some classes might not have setID, which is fine
        } catch (Exception e) {
            logger.warning("Failed to set ID on " + clazz.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Initialize a RealParameter with default values
     */
    private void initializeRealParameter(Object paramObject, Class<?> paramClass) {
        try {
            // Find the valuesInput field
            Field valuesInputField = findField(paramClass, "valuesInput");
            if (valuesInputField != null) {
                @SuppressWarnings("unchecked")
                Input<Object> valuesInput = (Input<Object>) valuesInputField.get(paramObject);

                // Set a default value
                List<Double> defaultValue = new ArrayList<>();
                defaultValue.add(0.0);
                valuesInput.setValue(defaultValue, (BEASTInterface) paramObject);
                logger.fine("Set default value for RealParameter: " + defaultValue);

                // Call initAndValidate
                callInitAndValidate(paramObject, paramClass);
            } else {
                logger.warning("Could not find valuesInput field in RealParameter");
            }
        } catch (Exception e) {
            logger.warning("Failed to initialize RealParameter: " + e.getMessage());
        }
    }

    /**
     * Find a field by name, including in superclasses
     */
    private Field findField(Class<?> clazz, String fieldName) {
        // Look in the class itself
        try {
            return clazz.getField(fieldName);
        } catch (NoSuchFieldException e) {
            // Look in superclasses
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null) {
                return findField(superClass, fieldName);
            }
            return null;
        }
    }

    /**
     * Build a map of input names to Input objects
     */
    private Map<String, Input<?>> buildInputMap(Object object, Class<?> clazz) {
        Map<String, Input<?>> inputMap = new HashMap<>();

        // Get all fields from this class and superclasses
        List<Field> fields = new ArrayList<>();
        Class<?> currentClass = clazz;

        while (currentClass != null) {
            fields.addAll(Arrays.asList(currentClass.getFields()));
            currentClass = currentClass.getSuperclass();
        }

        // Extract Input objects
        for (Field field : fields) {
            if (Input.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Input<?> input = (Input<?>) field.get(object);
                    if (input != null) {
                        inputMap.put(input.getName(), input);
                    }
                } catch (IllegalAccessException e) {
                    logger.warning("Failed to access Input field: " + field.getName());
                }
            }
        }

        return inputMap;
    }

    /**
     * Call initAndValidate method on object if it exists
     */
    private void callInitAndValidate(Object object, Class<?> clazz) {
        try {
            Method initMethod = clazz.getMethod("initAndValidate");
            logger.fine("Calling initAndValidate() on " + clazz.getName());
            initMethod.invoke(object);
        } catch (NoSuchMethodException e) {
            // Some classes might not have initAndValidate, which is fine
        } catch (Exception e) {
            logger.warning("Failed to call initAndValidate on " + clazz.getName() + ": " + e.getMessage());
        }
    }
}
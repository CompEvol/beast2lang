package org.beast2.modelLanguage.builder.handlers;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.evolution.likelihood.TreeLikelihood;
import beast.base.inference.distribution.ParametricDistribution;
import beast.base.inference.distribution.Prior;
import beast.base.evolution.speciation.SpeciesTreeDistribution;

import beast.base.inference.parameter.Parameter;
import org.beast2.modelLanguage.builder.util.BEASTUtils;
import org.beast2.modelLanguage.model.*;

import java.util.*;

/**
 * Handler for DistributionAssignment statements, responsible for creating BEAST2 objects
 * with associated distributions.
 */
public class DistributionAssignmentHandler extends BaseHandler {

    // Single map containing all class-to-input-name mappings
    private static final Map<Class<?>, String> CLASS_TO_ARGUMENT_INPUT = new HashMap<>();

    Class<?> paramDistClass = ParametricDistribution.class;

    // Initialize the map with known mappings
    static {
        CLASS_TO_ARGUMENT_INPUT.put(Prior.class, "x");
        CLASS_TO_ARGUMENT_INPUT.put(SpeciesTreeDistribution.class, "tree");
        CLASS_TO_ARGUMENT_INPUT.put(TreeLikelihood.class, "data");
    }

    /**
     * Constructor
     */
    public DistributionAssignmentHandler() {
        super(DistributionAssignmentHandler.class.getName());
    }

    /**
     * Get the appropriate input name for connecting a random variable to a distribution
     */
    private String getArgumentInputName(Object distribution) {
        // Search for the most specific class match in our map
        Class<?> distClass = distribution.getClass();
        while (distClass != null) {
            if (CLASS_TO_ARGUMENT_INPUT.containsKey(distClass)) {
                return CLASS_TO_ARGUMENT_INPUT.get(distClass);
            }

            // Check interfaces as well
            for (Class<?> iface : distClass.getInterfaces()) {
                if (CLASS_TO_ARGUMENT_INPUT.containsKey(iface)) {
                    return CLASS_TO_ARGUMENT_INPUT.get(iface);
                }
            }

            // Move up the class hierarchy
            distClass = distClass.getSuperclass();
        }

        return null;
    }

    /**
     * Create BEAST2 objects from a distribution assignment
     */
    public void createObjects(DistributionAssignment distAssign, Map<String, Object> objectRegistry) throws Exception {
        String className = distAssign.getClassName();
        String varName = distAssign.getVariableName();
        Expression distribution = distAssign.getDistribution();

        // If this is potentially a ParametricDistribution assignment
        if (distribution instanceof FunctionCall && Parameter.class.isAssignableFrom(loadClass(className))) {
            FunctionCall distFunc = (FunctionCall) distribution;
            String distClassName = distFunc.getClassName();

            try {
                // Try to load the distribution class
                Class<?> distClass = loadClass(distClassName);

                // Check if it's a ParametricDistribution (or subclass)
                if (ParametricDistribution.class.isAssignableFrom(distClass)) {
                    logger.info("Detected ParametricDistribution assignment, creating Prior wrapper");

                    // 1. Create the distribution object first
                    String distVarName = varName + "Dist";
                    Object distObject = createBEASTObject(distClassName, distVarName);

// 2. Configure the distribution with its arguments
                    Map<String, Input<?>> distInputMap = BEASTUtils.buildInputMap(distObject, distClass);
                    for (Argument arg : distFunc.getArguments()) {
                        String argName = arg.getName();
                        Input<?> input = distInputMap.get(argName);

                        // Get the expected type for proper autoboxing
                        Class<?> expectedType = BEASTUtils.getInputExpectedType(input,(BEASTInterface) distObject,argName);

                        // Resolve with autoboxing based on expected type
                        Object argValue = ExpressionResolver.resolveValueWithAutoboxing(
                                arg.getValue(), objectRegistry, expectedType);

                        if (input != null && distObject instanceof BEASTInterface) {
                            try {
                                BEASTUtils.setInputValue(input, argValue, (BEASTInterface)distObject);
                            } catch (Exception e) {
                                logger.warning("Failed to set input " + argName + ": " + e.getMessage());

                                // Try parameter conversion as fallback
                                if (argValue != null) {
                                    Object paramValue = BEASTUtils.createParameterForType(argValue, expectedType);
                                    BEASTUtils.setInputValue(input, paramValue, (BEASTInterface)distObject);
                                }
                            }
                        }
                    }
                    // Initialize the distribution
                    BEASTUtils.callInitAndValidate(distObject, distClass);

                    // Store it in the registry
                    objectRegistry.put(distVarName, distObject);

                    // 3. Now create a Prior that wraps this distribution
                    Object priorObject = createBEASTObject("beast.base.inference.distribution.Prior",
                            varName + "Prior");

                    // 4. Create the parameter
                    Object paramObject = createBEASTObject(className, varName);

                    // Special handling for RealParameter or other Parameter types
                    if (Parameter.class.isAssignableFrom(paramObject.getClass())) {
                        // Initialize with default values if needed
                        if (paramObject.getClass().getName().equals("beast.base.inference.parameter.RealParameter")) {
                            initializeRealParameter(paramObject, paramObject.getClass());
                        }
                    }

                    // Store the parameter
                    objectRegistry.put(varName, paramObject);

                    // 5. Wire the distribution and parameter to the prior
                    Map<String, Input<?>> priorInputMap = BEASTUtils.buildInputMap(priorObject,
                            priorObject.getClass());

                    // Connect distribution to prior
                    Input<?> distrInput = priorInputMap.get("distr");
                    if (distrInput != null && priorObject instanceof BEASTInterface) {
                        BEASTUtils.setInputValue(distrInput, distObject, (BEASTInterface)priorObject);
                    }

                    // Connect parameter to prior
                    Input<?> paramInput = ((BEASTInterface)priorObject).getInput("x");
                    if (paramInput != null) {
                        BEASTUtils.setInputValue(paramInput, paramObject, (BEASTInterface)priorObject);
                    }

                    // Initialize the prior
                    BEASTUtils.callInitAndValidate(priorObject, priorObject.getClass());

                    // Store the prior
                    objectRegistry.put(varName + "Prior", priorObject);

                    // We're done - the pattern is handled
                    return;
                }
            } catch (ClassNotFoundException e) {
                // Not a recognized distribution class, continue with normal processing
                logger.fine("Distribution class not found: " + distClassName);
            }
        }

        // Normal processing if we didn't handle the special case

        // Create the parameter object
        Object beastObject = createBEASTObject(className, varName);

        // Special handling for RealParameter
        if (className.equals("beast.base.inference.parameter.RealParameter")) {
            initializeRealParameter(beastObject, beastObject.getClass());
        }

        // Store the parameter object
        objectRegistry.put(varName, beastObject);

        // If no distribution is specified, we're done
        if (!(distribution instanceof FunctionCall)) {
            return;
        }

        // Create the distribution object
        FunctionCall funcCall = (FunctionCall) distribution;
        String distClassName = funcCall.getClassName();
        Object distObject = createBEASTObject(distClassName, varName + "Prior");

        // Configure the distribution
        configureDistribution(distObject, beastObject, funcCall, objectRegistry);

        // Store the distribution object
        objectRegistry.put(varName + "Prior", distObject);
    }

    /**
     * Create BEAST2 objects from a distribution assignment with observed data reference
     */
    public void createObservedObjects(DistributionAssignment distAssign, Map<String, Object> objectRegistry, String dataRef) throws Exception {
        String className = distAssign.getClassName();
        String varName = distAssign.getVariableName();
        Expression distribution = distAssign.getDistribution();

        // Get the referenced data object
        Object dataObject = objectRegistry.get(dataRef);
        if (dataObject == null) {
            throw new IllegalArgumentException("Data reference not found: " + dataRef);
        }

        // Check type compatibility
        Class<?> expectedClass = loadClass(className);
        if (!expectedClass.isInstance(dataObject)) {
            throw new IllegalArgumentException("Data reference type mismatch");
        }

        // Use the same data object for the observed variable
        objectRegistry.put(varName, dataObject);

        // If no distribution is specified, we're done
        if (!(distribution instanceof FunctionCall)) {
            return;
        }

        // Create and configure the likelihood object
        FunctionCall funcCall = (FunctionCall) distribution;
        String likelihoodClassName = funcCall.getClassName();
        createLikelihood(likelihoodClassName, varName, funcCall, dataObject, objectRegistry);
    }

    /**
     * Create a likelihood object and configure it
     */
    private void createLikelihood(String className, String varName, FunctionCall funcCall,
                                  Object dataObject, Map<String, Object> objectRegistry) throws Exception {
        // Create the likelihood object
        Object likelihoodObject = createBEASTObject(className, varName + "Likelihood");

        // Build input maps
        Map<String, Input<?>> likelihoodInputMap = BEASTUtils.buildInputMap(likelihoodObject, likelihoodObject.getClass());
        Map<String, Input<?>> dataInputMap = null;
        if (dataObject instanceof BEASTInterface) {
            dataInputMap = BEASTUtils.buildInputMap(dataObject, dataObject.getClass());
        }

        // Connect data to likelihood
        String inputName = getArgumentInputName(likelihoodObject);
        boolean dataConnected = false;

        if (inputName != null) {
            Input<?> input = likelihoodInputMap.get(inputName);
            if (input != null) {
                try {
                    BEASTUtils.setInputValue(input, dataObject, (BEASTInterface) likelihoodObject);
                    dataConnected = true;
                } catch (Exception e) {
                    logger.warning("Failed to connect data to input: " + e.getMessage());
                }
            }
        }

        // Configure function call arguments
        for (Argument arg : funcCall.getArguments()) {
            String name = arg.getName();

            // Skip data input if already handled
            if (dataConnected && name.equals(inputName)) {
                continue;
            }

            configureInput(arg.getName(), arg.getValue(),
                    (BEASTInterface)likelihoodObject, likelihoodInputMap, objectRegistry);
        }

        // Initialize objects
        BEASTUtils.callInitAndValidate(likelihoodObject, likelihoodObject.getClass());
        if (dataObject instanceof BEASTInterface) {
            BEASTUtils.callInitAndValidate(dataObject, dataObject.getClass());
        }

        // Store the likelihood object
        objectRegistry.put(varName + "Likelihood", likelihoodObject);
    }

    /**
     * Configure distribution and parameter objects
     */
    private void configureDistribution(Object distObject, Object paramObject, FunctionCall funcCall,
                                       Map<String, Object> objectRegistry) throws Exception {
        // Build input maps
        Map<String, Input<?>> distInputMap = BEASTUtils.buildInputMap(distObject, distObject.getClass());
        Map<String, Input<?>> paramInputMap = null;
        if (paramObject instanceof BEASTInterface) {
            paramInputMap = BEASTUtils.buildInputMap(paramObject, paramObject.getClass());
        }

        // Connect parameter to distribution
        String inputName = getArgumentInputName(distObject);
        boolean parameterConnected = false;

        if (inputName != null && distObject instanceof BEASTInterface) {
            Input<?> input = ((BEASTInterface) distObject).getInput(inputName);
            if (input != null) {
                try {
                    BEASTUtils.setInputValue(input, paramObject, (BEASTInterface) distObject);
                    parameterConnected = true;
                } catch (Exception e) {
                    logger.warning("Failed to connect parameter: " + e.getMessage());
                }
            }
        }

        // Process function call arguments
        for (Argument arg : funcCall.getArguments()) {
            String name = arg.getName();

            // Skip parameter input if already handled
            if (parameterConnected && name.equals(inputName)) {
                continue;
            }

            configureInputForObjects(name, arg, distObject, paramObject,
                    distInputMap, paramInputMap, objectRegistry);
        }

        // Initialize objects
        BEASTUtils.callInitAndValidate(distObject, distObject.getClass());
        if (paramObject instanceof BEASTInterface) {
            BEASTUtils.callInitAndValidate(paramObject, paramObject.getClass());
        }
    }
}
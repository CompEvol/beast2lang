package org.beast2.modelLanguage.builder.handlers;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;

import org.beast2.modelLanguage.builder.util.BEASTUtils;
import org.beast2.modelLanguage.model.*;

import java.util.*;

/**
 * Handler for DistributionAssignment statements, responsible for creating BEAST2 objects
 * with associated distributions.
 * This implementation supports the @observed annotation with data references and
 * flexibly handles inputs that should be set on random variables rather than distributions.
 */
public class DistributionAssignmentHandler extends BaseHandler {

    // Common input names for data objects
    private static final String[] DATA_INPUT_NAMES = {"data", "patterns", "alignment", "x"};

    // Common input names for parameter objects
    private static final String[] PARAMETER_INPUT_NAMES = {"x", "parameter"};

    // Common input names for tree objects
    private static final String[] TREE_INPUT_NAMES = {"tree", "treeModel"};

    // Common input names for other objects
    private static final String[] COMMON_INPUT_NAMES = {"data", "taxonset", "network", "trait", "patterns"};

    /**
     * Constructor
     */
    public DistributionAssignmentHandler() {
        super(DistributionAssignmentHandler.class.getName());
    }

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
        Object beastObject = createBEASTObject(className, varName);

        // Special handling for RealParameter
        if (className.equals("beast.base.inference.parameter.RealParameter")) {
            initializeRealParameter(beastObject, beastObject.getClass());
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
            createLikelihood(distClassName, varName, funcCall, beastObject, objectRegistry, true);
            return;
        }

        // Create distribution object
        Object distObject = createBEASTObject(distClassName, varName + "Prior");

        // Configure the distribution and parameter object with function call arguments
        configureDistribution(distObject, beastObject, funcCall, objectRegistry);

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
        Class<?> expectedClass = loadClass(className);
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

        // Create likelihood object with special handling for TreeLikelihood
        boolean isTreeLikelihood = likelihoodClassName.equals("beast.base.evolution.likelihood.TreeLikelihood");
        createLikelihood(likelihoodClassName, varName, funcCall, dataObject, objectRegistry, isTreeLikelihood);
    }

    /**
     * Create a likelihood object and configure it
     */
    private void createLikelihood(String className, String varName, FunctionCall funcCall,
                                  Object dataObject, Map<String, Object> objectRegistry,
                                  boolean isTreeLikelihood) throws Exception {
        logger.info("Creating " + (isTreeLikelihood ? "TreeLikelihood" : "Likelihood") + " for: " + varName);

        // Create the likelihood object
        Object likelihoodObject = createBEASTObject(className, varName + "Likelihood");

        // Build input maps
        Map<String, Input<?>> likelihoodInputMap = BEASTUtils.buildInputMap(likelihoodObject, likelihoodObject.getClass());
        Map<String, Input<?>> dataInputMap = null;
        if (dataObject instanceof BEASTInterface) {
            dataInputMap = BEASTUtils.buildInputMap(dataObject, dataObject.getClass());
        }

        // Connect data to likelihood (using specific method for TreeLikelihood if needed)
        boolean dataConnected;
        if (isTreeLikelihood) {
            Input<?> dataInput = likelihoodInputMap.get("data");
            if (dataInput != null) {
                BEASTUtils.setInputValue(dataInput, dataObject, (BEASTInterface) likelihoodObject);
                logger.fine("Connected " + varName + " to TreeLikelihood's 'data' input");
                dataConnected = true;
            } else {
                logger.warning("Could not find 'data' input on TreeLikelihood");
                dataConnected = false;
            }
        } else {
            dataConnected = connectDataToLikelihood(dataObject, likelihoodObject, likelihoodInputMap);
        }

        // Configure function call arguments
        for (Argument arg : funcCall.getArguments()) {
            String name = arg.getName();

            // Skip data inputs we've already handled
            if (dataConnected && (name.equals("data") || isDataInputName(name))) {
                continue;
            }

            configureInputForObjects(name, arg, likelihoodObject, dataObject,
                    likelihoodInputMap, dataInputMap, objectRegistry);
        }

        // Initialize both objects
        BEASTUtils.callInitAndValidate(likelihoodObject, likelihoodObject.getClass());
        if (dataObject instanceof BEASTInterface) {
            BEASTUtils.callInitAndValidate(dataObject, dataObject.getClass());
        }

        // Store the likelihood object
        objectRegistry.put(varName + "Likelihood", likelihoodObject);
        logger.info("Created and stored likelihood " + varName + "Likelihood");
    }

    /**
     * Configure distribution and parameter objects
     */
    private void configureDistribution(Object distObject, Object paramObject, FunctionCall funcCall,
                                       Map<String, Object> objectRegistry) throws Exception {
        // Build input maps for both objects
        Map<String, Input<?>> distInputMap = BEASTUtils.buildInputMap(distObject, distObject.getClass());
        Map<String, Input<?>> paramInputMap = null;
        if (paramObject instanceof BEASTInterface) {
            paramInputMap = BEASTUtils.buildInputMap(paramObject, paramObject.getClass());
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

            configureInputForObjects(name, arg, distObject, paramObject,
                    distInputMap, paramInputMap, objectRegistry);
        }

        // Initialize both objects
        BEASTUtils.callInitAndValidate(distObject, distObject.getClass());
        if (paramObject instanceof BEASTInterface) {
            BEASTUtils.callInitAndValidate(paramObject, paramObject.getClass());
        }
    }

    /**
     * Connect data object to likelihood using common input names
     */
    private boolean connectDataToLikelihood(Object dataObject, Object likelihoodObject, Map<String, Input<?>> inputMap) {
        return connectToInput(dataObject, likelihoodObject, inputMap, DATA_INPUT_NAMES);
    }

    /**
     * Connect parameter object to the appropriate input in the distribution
     */
    private boolean connectParameterToDistribution(Object paramObject, Object distObject,
                                                   Map<String, Input<?>> distInputMap) {
        // 1. For Prior-like distributions, connect to 'x' input
        if (connectToInput(paramObject, distObject, distInputMap, PARAMETER_INPUT_NAMES)) {
            return true;
        }

        // 2. For tree distributions, connect to 'tree' input if parameter is a Tree
        if (paramObject instanceof beast.base.evolution.tree.Tree) {
            if (connectToInput(paramObject, distObject, distInputMap, TREE_INPUT_NAMES)) {
                return true;
            }
        }

        // 3. Try other common input names
        return connectToInput(paramObject, distObject, distInputMap, COMMON_INPUT_NAMES);
    }

    /**
     * Check if an input name is commonly used for data objects
     */
    private boolean isDataInputName(String name) {
        for (String dataInput : DATA_INPUT_NAMES) {
            if (dataInput.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if an input name is commonly used for parameter objects
     */
    private boolean isParameterInputName(String name, Object paramObject) {
        // Check general parameter names
        for (String paramName : PARAMETER_INPUT_NAMES) {
            if (paramName.equals(name)) {
                return true;
            }
        }

        // Check tree-specific names if applicable
        if (paramObject instanceof beast.base.evolution.tree.Tree) {
            for (String treeName : TREE_INPUT_NAMES) {
                if (treeName.equals(name)) {
                    return true;
                }
            }
        }

        return false;
    }
}
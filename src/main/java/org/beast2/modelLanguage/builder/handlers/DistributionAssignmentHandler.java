package org.beast2.modelLanguage.builder.handlers;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.evolution.likelihood.TreeLikelihood;
import beast.base.evolution.tree.Tree;
import beast.base.inference.distribution.Prior;
import beast.base.evolution.speciation.SpeciesTreeDistribution;

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

    // Define only the essential input names
    private static final String PRIOR_PARAMETER_INPUT = "x";
    private static final String TREE_DISTRIBUTION_INPUT = "tree";
    private static final String TREE_LIKELIHOOD_DATA_INPUT = "data";

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

        // Create likelihood object
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
        boolean dataConnected = false;
        Input<?> dataInput = likelihoodInputMap.get(TREE_LIKELIHOOD_DATA_INPUT);
        if (dataInput != null) {
            try {
                BEASTUtils.setInputValue(dataInput, dataObject, (BEASTInterface) likelihoodObject);
                dataConnected = true;
            } catch (Exception e) {
                logger.warning("Failed to connect data to " + TREE_LIKELIHOOD_DATA_INPUT + " input: " + e.getMessage());
            }
        }

        // Configure function call arguments
        for (Argument arg : funcCall.getArguments()) {
            String name = arg.getName();

            // Skip data input if we've already handled it
            if (dataConnected && name.equals(TREE_LIKELIHOOD_DATA_INPUT)) {
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

            // Skip parameter input if we've already handled it
            if (parameterConnected &&
                    (name.equals(PRIOR_PARAMETER_INPUT) ||
                            (name.equals(TREE_DISTRIBUTION_INPUT) && paramObject instanceof Tree))) {
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
     * Connect parameter object to the appropriate input in the distribution
     */
    private boolean connectParameterToDistribution(Object paramObject, Object distObject,
                                                   Map<String, Input<?>> distInputMap) {
        if (!(distObject instanceof BEASTInterface)) {
            return false;
        }

        BEASTInterface beastDist = (BEASTInterface) distObject;

        // For Prior distributions
        if (distObject instanceof Prior) {
            try {
                Input<?> input = beastDist.getInput(PRIOR_PARAMETER_INPUT);
                if (input != null) {
                    BEASTUtils.setInputValue(input, paramObject, beastDist);
                    return true;
                }
            } catch (Exception e) {
                logger.warning("Failed to connect parameter to Prior: " + e.getMessage());
            }
        }

        // For tree distributions when parameter is a Tree
        if (distObject instanceof SpeciesTreeDistribution && paramObject instanceof Tree) {
            try {
                Input<?> input = beastDist.getInput(TREE_DISTRIBUTION_INPUT);
                if (input != null) {
                    BEASTUtils.setInputValue(input, paramObject, beastDist);
                    return true;
                }
            } catch (Exception e) {
                logger.warning("Failed to connect tree to TreeDistribution: " + e.getMessage());
            }
        }

        return false;
    }
}
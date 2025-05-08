package org.beast2.modelLanguage.builder.handlers;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.evolution.likelihood.TreeLikelihood;
import beast.base.evolution.tree.MRCAPrior;
import beast.base.evolution.tree.TreeDistribution;
import beast.base.inference.distribution.ParametricDistribution;
import beast.base.inference.distribution.Prior;
import beast.base.inference.parameter.Parameter;
import beast.base.inference.parameter.RealParameter;
import org.beast2.modelLanguage.builder.util.BEASTUtils;
import org.beast2.modelLanguage.model.Argument;
import org.beast2.modelLanguage.model.DistributionAssignment;
import org.beast2.modelLanguage.model.Expression;
import org.beast2.modelLanguage.model.FunctionCall;

import java.lang.reflect.Type;
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
        CLASS_TO_ARGUMENT_INPUT.put(TreeDistribution.class, "tree");
        CLASS_TO_ARGUMENT_INPUT.put(MRCAPrior.class, "tree");
        CLASS_TO_ARGUMENT_INPUT.put(TreeLikelihood.class, "data");
    }

    /**
     * Constructor
     */
    public DistributionAssignmentHandler() {
        super(DistributionAssignmentHandler.class.getName());
    }

    private String getArgumentInputName(Object distribution) {
        // Log what we're trying to match
        logger.info("Finding argument input name for: " + distribution.getClass().getName());

        // Search for the most specific class match in our map
        Class<?> distClass = distribution.getClass();
        while (distClass != null) {
            logger.info("Checking class: " + distClass.getName());
            if (CLASS_TO_ARGUMENT_INPUT.containsKey(distClass)) {
                String result = CLASS_TO_ARGUMENT_INPUT.get(distClass);
                logger.info("Found matching argument input name: " + result);
                return result;
            }

            // Check interfaces as well
            for (Class<?> iface : distClass.getInterfaces()) {
                logger.info("Checking interface: " + iface.getName());
                if (CLASS_TO_ARGUMENT_INPUT.containsKey(iface)) {
                    String result = CLASS_TO_ARGUMENT_INPUT.get(iface);
                    logger.info("Found matching argument input name from interface: " + result);
                    return result;
                }
            }

            // Move up the class hierarchy
            distClass = distClass.getSuperclass();
        }

        logger.warning("No matching argument input name found for: " + distribution.getClass().getName());
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
                    logger.info("Detected ParametricDistribution assignment for variable " + varName + ", creating Prior wrapper");

                    // 1. Create the distribution object first
                    String distVarName = varName + "Dist";
                    ParametricDistribution distObject = (ParametricDistribution)createBEASTObject(distClassName, distVarName);

                    // 2. Configure the distribution with its arguments
                    Map<String, Input<?>> distInputMap = BEASTUtils.buildInputMap(distObject, distClass);
                    for (Argument arg : distFunc.getArguments()) {
                        String argName = arg.getName();
                        Input<?> input = distInputMap.get(argName);

                        // Get the expected type for proper autoboxing
                        Type expectedType = BEASTUtils.getInputExpectedType(input, (BEASTInterface) distObject, argName);

                        // Resolve with autoboxing based on expected type
                        Object argValue = ExpressionResolver.resolveValueWithAutoboxing(
                                arg.getValue(), objectRegistry, expectedType);

                        if (input != null && distObject != null) {
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

                    // 3. Create or get the parameter object
                    // Check if parameter already exists
                    Parameter paramObject;
                    Object existingParam = objectRegistry.get(varName);
                    if (existingParam != null && Parameter.class.isAssignableFrom(existingParam.getClass())) {
                        // Reuse existing parameter
                        logger.info("Using existing parameter object: " + varName);
                        paramObject = (Parameter) existingParam;
                    } else {
                        // Create new parameter
                        paramObject = (Parameter) createBEASTObject(className, varName);

                        initializeParameterFromParametricDistribution(paramObject, distObject);

                        // Store the parameter in registry
                        objectRegistry.put(varName, paramObject);
                    }

                    // 4. Now create a Prior that wraps this distribution
                    String priorName = getUniquePriorName(varName, objectRegistry);
                    Object priorObject = createBEASTObject("beast.base.inference.distribution.Prior", priorName);

                    // 5. Wire the distribution and parameter to the prior
                    Map<String, Input<?>> priorInputMap = BEASTUtils.buildInputMap(priorObject, priorObject.getClass());

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

                    // Store the prior and update prior list
                    objectRegistry.put(priorName, priorObject);
                    addDistributionForObject(varName, priorName, objectRegistry);
                    
                    return;
                }
            } catch (ClassNotFoundException e) {
                // Not a recognized distribution class, continue with normal processing
                logger.fine("Distribution class not found: " + distClassName);
            }
        }

        // Normal processing if we didn't handle the special case

        // Check if the object already exists in the registry
        Object beastObject;
        Object existingObject = objectRegistry.get(varName);
        Class<?> objectClass = loadClass(className);

        if (existingObject != null && objectClass.isInstance(existingObject)) {
            // Use existing object if it's compatible
            logger.info("Using existing object " + varName + " for multiple distributions");
            beastObject = existingObject;
        } else {
            // Create the object if it doesn't exist
            beastObject = createBEASTObject(className, varName);

            if (className.equals("beast.base.inference.parameter.RealParameter")) {
                initializeRealParameter(beastObject);
            }

            // Store the object
            objectRegistry.put(varName, beastObject);
        }

        // If no distribution is specified, we're done
        if (!(distribution instanceof FunctionCall)) {
            return;
        }

        // Create the distribution object with a unique name
        FunctionCall funcCall = (FunctionCall) distribution;
        String distClassName = funcCall.getClassName();
        String priorName = getUniquePriorName(varName, objectRegistry);
        Object distObject = createBEASTObject(distClassName, priorName);

        // Configure the distribution
        configureDistribution(distObject, beastObject, funcCall, objectRegistry);

        // Store the distribution object
        objectRegistry.put(priorName, distObject);

        // Track this distribution for the object
        addDistributionForObject(varName, priorName, objectRegistry);
    }

    /**
     * Initializes parameter values based on the associated distribution
     */
    private void initializeParameterFromDistribution(Object paramObject, Object priorObject) {
        // Input validation
        if (paramObject == null || !(paramObject instanceof Parameter) ||
                priorObject == null || !(priorObject instanceof beast.base.inference.distribution.Prior)) {
            return;
        }

        Parameter<?> param = (Parameter<?>) paramObject;
        beast.base.inference.distribution.Prior prior = (beast.base.inference.distribution.Prior) priorObject;
        beast.base.inference.distribution.ParametricDistribution dist = prior.distInput.get();

        if (dist != null) {
            if (ParameterInitializer.initializeParameter(param, dist)) {
                logger.info("Successfully initialized parameter " + param.getID() + " from distribution");
            }
        }
    }

    /**
     * Initializes parameter values based on the parametric distribution
     */
    private void initializeParameterFromParametricDistribution(Parameter<?> param, ParametricDistribution dist) {
        if (dist != null && param != null) {
            if (ParameterInitializer.initializeParameter(param, dist)) {
                logger.info("Successfully initialized parameter " + param.getID() + " from parametric distribution");
            }
        }
    }

    /**
     * Initialize RealParameter objects with default values
     */
    private void initializeRealParameter(Object paramObject) {
        if (paramObject instanceof RealParameter) {
            RealParameter realParameter = (RealParameter) paramObject;
            logger.info("Initializing real parameter " + realParameter.getID());

            if (ParameterInitializer.initializeRealParameterWithDefault(realParameter)) {
                logger.info("Successfully initialized parameter with default values");
            }
        }
    }

    /**
     * Get a unique name for a prior
     */
    private String getUniquePriorName(String varName, Map<String, Object> objectRegistry) {
        String basePriorName = varName + "Prior";

        // If base name is available, use it
        if (!objectRegistry.containsKey(basePriorName)) {
            return basePriorName;
        }

        // Otherwise, generate a unique name with a counter
        int counter = 1;
        String priorName;
        do {
            priorName = basePriorName + counter;
            counter++;
        } while (objectRegistry.containsKey(priorName));

        return priorName;
    }

    /**
     * Add a distribution to the list of distributions for an object
     */
    private void addDistributionForObject(String objectName, String priorName, Map<String, Object> objectRegistry) {
        // Key for storing the list of distributions for this object
        String distributionsKey = objectName + "Distributions";

        // Get existing list or create a new one
        @SuppressWarnings("unchecked")
        List<String> distributionList = (List<String>) objectRegistry.get(distributionsKey);

        if (distributionList == null) {
            distributionList = new ArrayList<>();
            objectRegistry.put(distributionsKey, distributionList);
        }

        // Add this distribution to the list
        distributionList.add(priorName);
        logger.info("Added distribution " + priorName + " for object " + objectName +
                " (total: " + distributionList.size() + ")");
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

    private void configureDistribution(Object distObject, Object paramObject, FunctionCall funcCall,
                                       Map<String, Object> objectRegistry) throws Exception {
        logger.info("Configuring distribution: " + distObject.getClass().getName() +
                " with parameter: " + paramObject.getClass().getName());

        // Build input maps
        Map<String, Input<?>> distInputMap = BEASTUtils.buildInputMap(distObject, distObject.getClass());
        Map<String, Input<?>> paramInputMap = null;
        if (paramObject instanceof BEASTInterface) {
            paramInputMap = BEASTUtils.buildInputMap(paramObject, paramObject.getClass());
        }

        // IMPORTANT CHANGE: Process function call arguments FIRST
        for (Argument arg : funcCall.getArguments()) {
            String name = arg.getName();
            configureInputForObjects(name, arg, distObject, paramObject,
                    distInputMap, paramInputMap, objectRegistry);
        }

        // NOW try to initialize the parameter object if needed
        if (paramObject instanceof BEASTInterface) {
            try {
                BEASTUtils.callInitAndValidate(paramObject, paramObject.getClass());
                logger.info("Successfully initialized parameter object");
            } catch (Exception e) {
                logger.warning("Failed to initialize parameter object: " + e.getMessage());
            }
        }

        // THEN connect parameter to distribution
        String inputName = getArgumentInputName(distObject);
        boolean parameterConnected = false;

        if (inputName != null && distObject instanceof BEASTInterface) {
            Input<?> input = ((BEASTInterface) distObject).getInput(inputName);
            if (input != null) {
                try {
                    logger.info("Connecting " + paramObject.getClass().getSimpleName() +
                            " to " + distObject.getClass().getSimpleName() +
                            " via input '" + inputName + "'");
                    BEASTUtils.setInputValue(input, paramObject, (BEASTInterface) distObject);
                    parameterConnected = true;
                } catch (Exception e) {
                    logger.warning("Failed to connect parameter: " + e.getMessage());
                }
            } else {
                logger.warning("Could not find input '" + inputName + "' in " +
                        distObject.getClass().getSimpleName());
            }
        }

        // Initialize distribution object
        BEASTUtils.callInitAndValidate(distObject, distObject.getClass());
    }
}
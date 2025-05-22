package org.beast2.modelLanguage.builder.handlers;

import org.beast2.modelLanguage.builder.util.AutoboxingRegistry;
import org.beast2.modelLanguage.model.Argument;
import org.beast2.modelLanguage.model.DistributionAssignment;
import org.beast2.modelLanguage.model.Expression;
import org.beast2.modelLanguage.model.FunctionCall;

import java.lang.reflect.Type;
import java.util.*;

/**
 * Handler for DistributionAssignment statements, responsible for creating model objects
 * with associated distributions.
 * Refactored to use ObjectFactory instead of direct BEAST dependencies.
 */
public class DistributionAssignmentHandler extends BaseHandler {

    /**
     * Constructor
     */
    public DistributionAssignmentHandler() {
        super(DistributionAssignmentHandler.class.getName());
    }

    /**
     * Determines the input name for the primary parameter that a distribution is defined over.
     */
    private String getArgumentInputName(Object distribution) {
        logger.info("Finding argument input name for: " + distribution.getClass().getName());

        String result = factory.getPrimaryInputName(distribution);

        if (result != null) {
            logger.info("Found matching argument input name: " + result);
        } else {
            logger.warning("No matching argument input name found for: " + distribution.getClass().getName());
        }

        return result;
    }

    /**
     * Connects a primary parameter to a distribution or likelihood object with proper autoboxing.
     */
    private boolean connectPrimaryParameter(Object primaryObject, Object targetObject,
                                            Map<String, Object> objectRegistry) {
        if (!factory.isModelObject(targetObject)) {
            logger.warning("Target object is not a model object");
            return false;
        }

        // Find the appropriate input name
        String inputName = getArgumentInputName(targetObject);
        if (inputName == null) {
            logger.warning("Could not determine input name for " + targetObject.getClass().getSimpleName());
            return false;
        }

        try {
            logger.info("Connecting " + primaryObject.getClass().getSimpleName() +
                    " to " + targetObject.getClass().getSimpleName() +
                    " via input '" + inputName + "'");

            // Get expected type for the input
            Type expectedType = factory.getInputType(targetObject, inputName);

            // Check if autoboxing is needed
            Object valueToSet = primaryObject;
            if (!AutoboxingRegistry.isDirectlyAssignable(primaryObject, expectedType)) {
                // Apply autoboxing if needed
                valueToSet = factory.autobox(primaryObject, expectedType, objectRegistry);
                logger.info("Autoboxed parameter from " + primaryObject.getClass().getSimpleName() +
                        " to " + (valueToSet != null ? valueToSet.getClass().getSimpleName() : "null"));
            }

            // Now set the input
            factory.setInputValue(targetObject, inputName, valueToSet);
            return true;
        } catch (Exception e) {
            logger.warning("Failed to connect parameter: " + e.getMessage());
            return false;
        }
    }

    /**
     * Create objects from a distribution assignment
     */
    public void createObjects(DistributionAssignment distAssign, Map<String, Object> objectRegistry) throws Exception {
        String className = distAssign.getClassName();
        String varName = distAssign.getVariableName();
        Expression distribution = distAssign.getDistribution();

        // Check if this is potentially a ParametricDistribution assignment
        if (distribution instanceof FunctionCall distFunc && isParameterType(className)) {
            String distClassName = distFunc.getClassName();

            try {
                // Try to load the distribution class
                //Class<?> distClass = loadClass(distClassName);
                Object testDist = factory.createObject(distClassName, null);

                // Check if it's a ParametricDistribution
                if (factory.isParametricDistribution(testDist)) {
                    logger.info("Detected ParametricDistribution assignment for variable " + varName + ", creating Prior wrapper");

                    // 1. Create the distribution object first
                    String distVarName = varName + "Dist";
                    Object distObject = createBEASTObject(distClassName, distVarName);

                    // 2. Configure the distribution with its arguments
                    configureObject(distObject, distFunc, objectRegistry);

                    // Initialize the distribution
                    factory.initAndValidate(distObject);

                    // Store it in the registry
                    objectRegistry.put(distVarName, distObject);

                    // 3. Create or get the parameter object
                    Object paramObject;
                    Object existingParam = objectRegistry.get(varName);
                    if (existingParam != null && factory.isParameter(existingParam)) {
                        // Reuse existing parameter
                        logger.info("Using existing parameter object: " + varName);
                        paramObject = existingParam;
                    } else {
                        // Create new parameter
                        paramObject = createBEASTObject(className, varName);

                        initializeParameterFromParametricDistribution(paramObject, distObject);

                        // Store the parameter in registry
                        objectRegistry.put(varName, paramObject);
                    }

                    // 4. Now create a Prior that wraps this distribution
                    String priorName = getUniquePriorName(varName, objectRegistry);
                    Object priorObject = factory.createPriorForParametricDistribution(paramObject, distObject, priorName);

                    // 5. Wire the distribution and parameter to the prior
                    factory.setInputValue(priorObject, "distr", distObject);
                    factory.setInputValue(priorObject, "x", paramObject);

                    // Initialize the prior
                    factory.initAndValidate(priorObject);

                    // Store the prior
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
        Object beastObject;
        Object existingObject = objectRegistry.get(varName);
        Class<?> objectClass = loadClass(className);

        if (objectClass.isInstance(existingObject)) {
            // Use existing object if it's compatible
            logger.info("Using existing object " + varName + " for multiple distributions");
            beastObject = existingObject;
        } else {
            // Create the object if it doesn't exist
            beastObject = createBEASTObject(className, varName);

            if (factory.isRealParameterType(beastObject)) {
                initializeRealParameter(beastObject);
            }

            // Store the object
            objectRegistry.put(varName, beastObject);
        }

        // If no distribution is specified, we're done
        if (!(distribution instanceof FunctionCall funcCall)) {
            return;
        }

        // Create the distribution object with a unique name
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
     * Check if a class name represents a parameter type
     */
    private boolean isParameterType(String className) {
        try {
            //Class<?> clazz = loadClass(className);
            Object testObj = factory.createObject(className, null);
            return factory.isParameter(testObj);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Configure an object from a function call
     */
    private void configureObject(Object object, FunctionCall funcCall, Map<String, Object> objectRegistry) throws Exception {
        if (!factory.isModelObject(object)) {
            return;
        }

        for (Argument arg : funcCall.getArguments()) {
            String argName = arg.getName();
            Type expectedType = factory.getInputType(object, argName);

            Object argValue = ExpressionResolver.resolveValueWithAutoboxing(
                    arg.getValue(), objectRegistry, expectedType);

            try {
                factory.setInputValue(object, argName, argValue);
            } catch (Exception e) {
                logger.warning("Failed to set input " + argName + ": " + e.getMessage());

                // Try parameter conversion as fallback
                if (argValue != null) {
                    Object paramValue = factory.createParameterForType(argValue, expectedType);
                    factory.setInputValue(object, argName, paramValue);
                }
            }
        }
    }

    /**
     * Initializes parameter values based on the parametric distribution
     */
    private void initializeParameterFromParametricDistribution(Object param, Object dist) {
        if (dist != null && param != null && factory.isParameter(param) && factory.isParametricDistribution(dist)) {
            if (ParameterInitializer.initializeParameter(param, dist)) {
                try {
                    String paramId = factory.getID(param);
                    logger.info("Successfully initialized parameter " + paramId + " from parametric distribution");
                } catch (Exception e) {
                    logger.info("Successfully initialized parameter from parametric distribution");
                }
            }
        }
    }

    /**
     * Initialize RealParameter objects with default values
     */
    private void initializeRealParameter(Object paramObject) {
        if (paramObject != null && factory.isRealParameterType(paramObject)) {
            try {
                String paramId = factory.getID(paramObject);
                logger.info("Initializing real parameter " + paramId);
            } catch (Exception e) {
                logger.info("Initializing real parameter");
            }

            if (ParameterInitializer.initializeRealParameterWithDefault(paramObject)) {
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
     * Create objects from a distribution assignment with observed data reference
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
        if (!(distribution instanceof FunctionCall funcCall)) {
            return;
        }

        // Create and configure the likelihood object
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

        // Connect data to likelihood
        boolean dataConnected = connectPrimaryParameter(dataObject, likelihoodObject, objectRegistry);
        if (!dataConnected) {
            logger.warning("Could not connect data to likelihood");
        }

        // Configure function call arguments
        for (Argument arg : funcCall.getArguments()) {
            String name = arg.getName();

            // Skip data input if already handled
            String inputName = getArgumentInputName(likelihoodObject);
            if (dataConnected && name.equals(inputName)) {
                continue;
            }

            configureInput(arg.getName(), arg.getValue(),
                    likelihoodObject, Collections.emptyMap(), objectRegistry);
        }

        // Initialize objects
        factory.initAndValidate(likelihoodObject);
        if (factory.isModelObject(dataObject)) {
            factory.initAndValidate(dataObject);
        }

        // Store the likelihood object
        objectRegistry.put(varName + "Likelihood", likelihoodObject);
    }

    private void configureDistribution(Object distObject, Object paramObject, FunctionCall funcCall,
                                       Map<String, Object> objectRegistry) throws Exception {
        logger.info("Configuring distribution: " + distObject.getClass().getName() +
                " with parameter: " + paramObject.getClass().getName());

        // Process function call arguments FIRST
        for (Argument arg : funcCall.getArguments()) {
            configureInputForObjects(arg.getName(), arg, distObject, paramObject, objectRegistry);
        }

        // NOW try to initialize the parameter object if needed
        if (factory.isModelObject(paramObject)) {
            try {
                factory.initAndValidate(paramObject);
                logger.info("Successfully initialized parameter object");
            } catch (Exception e) {
                logger.warning("Failed to initialize parameter object: " + e.getMessage());
            }
        }

        // THEN connect parameter to distribution
        boolean parameterConnected = connectPrimaryParameter(paramObject, distObject, objectRegistry);
        if (!parameterConnected) {
            logger.warning("Could not connect parameter to distribution");
        }

        // Initialize distribution object
        factory.initAndValidate(distObject);
    }
}
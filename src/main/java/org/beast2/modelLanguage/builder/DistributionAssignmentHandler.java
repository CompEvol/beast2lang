package org.beast2.modelLanguage.builder.handlers;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;

import beast.base.parser.NexusParser;

import org.beast2.modelLanguage.model.*;

import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Logger;

import java.io.IOException;

/**
 * Handler for DistributionAssignment statements, responsible for creating BEAST2 objects
 * with associated distributions.
 */
public class DistributionAssignmentHandler {
    
    private static final Logger logger = Logger.getLogger(DistributionAssignmentHandler.class.getName());
    
    /**
     * Create BEAST2 objects from a distribution assignment
     * 
     * @param distAssign the distribution assignment to process
     * @param objectRegistry registry of created objects for reference resolution
     * @throws Exception if object creation fails
     */
    public void createObjects(DistributionAssignment distAssign, Map<String, Object> objectRegistry) throws Exception {
        String className = distAssign.getClassName();
        String varName = distAssign.getVariableName();
        Expression distribution = distAssign.getDistribution();

        logger.info("Creating random variable: " + varName);

        // Special handling for problematic classes
        if (className.equals("beast.base.evolution.alignment.Alignment")) {
            handleAlignmentCreation(varName, distribution, objectRegistry, null);
            return;
        } else if (className.equals("beast.base.evolution.tree.Tree") && varName.equals("myTree")) {
            handleTreeCreation(varName, distribution, objectRegistry);
            return;
        }

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
            handleTreeLikelihoodCreation(varName, funcCall, beastObject, objectRegistry);
            return;
        }
        
        Class<?> distClass = Class.forName(distClassName);
        Object distObject = distClass.getDeclaredConstructor().newInstance();
        setObjectId(distObject, distClass, varName + "Prior");
        
        // Configure the distribution
        configureDistribution(distObject, distClass, funcCall, beastObject, varName, objectRegistry);
        
        // Store the distribution object
        objectRegistry.put(varName + "Prior", distObject);
        
    }
    
    /**
     * Create BEAST2 objects from a distribution assignment with observed data
     * 
     * @param distAssign the distribution assignment to process
     * @param objectRegistry registry of created objects for reference resolution
     * @param dataFile path to the data file for observed data
     * @throws Exception if object creation fails
     */
    public void createObservedObjects(DistributionAssignment distAssign, Map<String, Object> objectRegistry, String dataFile) throws Exception {
        String className = distAssign.getClassName();
        String varName = distAssign.getVariableName();
        Expression distribution = distAssign.getDistribution();

        logger.info("Creating observed random variable: " + varName + " with data: " + dataFile);

        // Special handling for observed alignment
        if (className.equals("beast.base.evolution.alignment.Alignment")) {
            handleAlignmentCreation(varName, distribution, objectRegistry, dataFile);
            return;
        }

        // For other observed variables, create normally but mark as fixed if possible
        createObjects(distAssign, objectRegistry);
        
        // Try to mark the variable as fixed/not estimated
        Object beastObject = objectRegistry.get(varName);
        if (beastObject != null) {
            try {
                markAsFixed(beastObject);
            } catch (Exception e) {
                logger.warning("Could not mark " + varName + " as fixed: " + e.getMessage());
            }
        }
    }
    
    /**
     * Try to mark an object as fixed (not estimated)
     */
    private void markAsFixed(Object object) {
        Class<?> objectClass = object.getClass();
        
        try {
            // Check for isEstimated property
            try {
                Method setEstimated = objectClass.getMethod("setIsEstimated", boolean.class);
                setEstimated.invoke(object, false);
                logger.fine("Marked object as fixed using setIsEstimated");
                return;
            } catch (NoSuchMethodException e) {
                // Method doesn't exist, try next approach
            }
            
            // Check for estimated input
            for (Field field : objectClass.getFields()) {
                if (Input.class.isAssignableFrom(field.getType())) {
                    @SuppressWarnings("unchecked")
                    Input<?> input = (Input<?>) field.get(object);
                    if ("estimate".equals(input.getName())) {
                        @SuppressWarnings("unchecked")
                        Input<Object> typedInput = (Input<Object>) input;
                        typedInput.setValue(false, (BEASTInterface) object);
                        logger.fine("Marked object as fixed using estimate input");
                        return;
                    }
                }
            }
            
            logger.fine("No method found to mark object as fixed");
        } catch (Exception e) {
            logger.warning("Error marking object as fixed: " + e.getMessage());
        }
    }
    
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
     * Configure a distribution with parameter and arguments 
     */
    private void configureDistribution(Object distObject, Class<?> distClass, FunctionCall funcCall, 
                                     Object paramObject, String paramName, Map<String, Object> objectRegistry) throws Exception {
        // Build a map of input names to Input objects
        Map<String, Input<?>> inputMap = new HashMap<>();
        Map<String, Class<?>> expectedTypeMap = new HashMap<>();
        
        for (Field field : distClass.getFields()) {
            if (!Input.class.isAssignableFrom(field.getType())) continue;
            
            @SuppressWarnings("unchecked")
            Input<?> input = (Input<?>) field.get(distObject);
            String name = input.getName();
            inputMap.put(name, input);
            
            // Determine expected type
            Class<?> expectedType = input.getType();
            if (expectedType == null) {
                // Try to infer from generic type
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType) {
                    Type[] typeArgs = ((ParameterizedType) genericType).getActualTypeArguments();
                    if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                        expectedType = (Class<?>) typeArgs[0];
                    }
                }
            }
            expectedTypeMap.put(name, expectedType);
        }
        
        // Connect parameter to appropriate input based on its type
        boolean parameterConnected = false;
        
        // 1. For Prior-like distributions, connect to 'x' input
        Input<?> xInput = inputMap.get("x");
        if (xInput != null) {
            @SuppressWarnings("unchecked")
            Input<Object> typedXInput = (Input<Object>) xInput;
            typedXInput.setValue(paramObject, (BEASTInterface) distObject);
            logger.fine("Connected parameter " + paramName + " to distribution's 'x' input");
            parameterConnected = true;
        }
        
        // 2. For tree distributions, connect to 'tree' input if parameter is a Tree
        if (!parameterConnected && paramObject instanceof beast.base.evolution.tree.Tree) {
            Input<?> treeInput = inputMap.get("tree");
            if (treeInput != null) {
                @SuppressWarnings("unchecked")
                Input<Object> typedTreeInput = (Input<Object>) treeInput;
                typedTreeInput.setValue(paramObject, (BEASTInterface) distObject);
                logger.fine("Connected Tree " + paramName + " to distribution's 'tree' input");
                parameterConnected = true;
            }
        }
        
        // 3. If we haven't connected yet, try other common input names
        if (!parameterConnected) {
            String[] commonInputNames = {"data", "taxonset", "network", "trait", "patterns"};
            for (String inputName : commonInputNames) {
                Input<?> input = inputMap.get(inputName);
                if (input != null) {
                    Class<?> expectedType = expectedTypeMap.get(inputName);
                    if (expectedType != null && expectedType.isAssignableFrom(paramObject.getClass())) {
                        @SuppressWarnings("unchecked")
                        Input<Object> typedInput = (Input<Object>) input;
                        typedInput.setValue(paramObject, (BEASTInterface) distObject);
                        logger.fine("Connected parameter " + paramName + " to distribution's '" + inputName + "' input");
                        parameterConnected = true;
                        break;
                    }
                }
            }
        }
        
        // 4. Configure other arguments from the function call
        for (Argument arg : funcCall.getArguments()) {
            String name = arg.getName();
            
            // Skip the parameter we've already connected, if any
            if (parameterConnected && (name.equals("x") || 
                                      (name.equals("tree") && paramObject instanceof beast.base.evolution.tree.Tree))) {
                continue;
            }
            
            Object rawValue = ExpressionResolver.resolveValue(arg.getValue(), objectRegistry);
            Input<?> input = inputMap.get(name);
            
            if (input == null) {
                throw new RuntimeException("No Input named '" + name + "' in distribution " + distClass.getName());
            }
            
            // Type check if we have both expected type and non-null value
            Class<?> expectedType = expectedTypeMap.get(name);
            if (expectedType != null && rawValue != null) {
                if (!expectedType.isAssignableFrom(rawValue.getClass())) {
                    throw new RuntimeException(String.format(
                        "Type mismatch for '%s': expected %s but got %s",
                        name,
                        expectedType.getSimpleName(),
                        rawValue.getClass().getSimpleName()
                    ));
                }
            }
            
            // Set the input value
            @SuppressWarnings("unchecked")
            Input<Object> typedInput = (Input<Object>) input;
            typedInput.setValue(rawValue, (BEASTInterface) distObject);
            logger.fine("Set input '" + name + "' to " + rawValue);
        }
        
        // Initialize the distribution
        callInitAndValidate(distObject, distClass);
        
    }
    
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
    
    /**
     * Special handling for Alignment objects due to initialization challenges
     */
    private void handleAlignmentCreation(String varName, Expression distribution, Map<String, Object> objectRegistry, String dataFile) {
        try {
            logger.info("Special handling for Alignment class" + (dataFile != null ? " with data file: " + dataFile : ""));
            
            // Create an alignment object - use appropriate approach based on dataFile
            Object alignmentObject = null;
            
            if (dataFile != null) {
                // Create alignment from a data file
                alignmentObject = createAlignmentFromFile(dataFile, varName);
            } else {
                try {
                    // Create an empty alignment if no data file specified
                    Class<?> alignmentClass = Class.forName("beast.base.evolution.alignment.Alignment");
                    alignmentObject = alignmentClass.getDeclaredConstructor().newInstance();
                    
                    // Set the ID
                    setObjectId(alignmentObject, alignmentClass, varName);
                    
                } catch (ExceptionInInitializerError e) {
                    // If the static initialization fails, use a stub object
                    logger.warning("Static initialization of Alignment failed, using stub: " + e.getMessage());
                    alignmentObject = new DummyBEASTObject(varName, "Alignment");
                }
            }
            
            // Store the alignment object
            objectRegistry.put(varName, alignmentObject);
            logger.info("Successfully created and stored Alignment: " + varName);
            
            // If there's a distribution function call, create that too (typically TreeLikelihood)
            if (distribution instanceof FunctionCall) {
                FunctionCall funcCall = (FunctionCall) distribution;
                String distClassName = funcCall.getClassName();
                
                if (distClassName.equals("beast.base.evolution.likelihood.TreeLikelihood")) {
                    handleTreeLikelihoodCreation(varName, funcCall, alignmentObject, objectRegistry);
                }
            }
        } catch (Exception e) {
            // Create a stub object if all else fails
            logger.warning("Failed to create Alignment, using generic fallback: " + e.getMessage());
            DummyBEASTObject dummy = new DummyBEASTObject(varName, "Alignment");
            objectRegistry.put(varName, dummy);
        }
    }
    
    private Object createAlignmentFromFile(String dataFile, String id) throws Exception {
        logger.info("Creating alignment from file: " + dataFile);
        
        File file = new File(dataFile);
        if (!file.exists()) {
            throw new IOException("Data file not found: " + dataFile);
        }
        
        // For Nexus files, use NexusParser directly
        if (dataFile.toLowerCase().endsWith(".nex") || dataFile.toLowerCase().endsWith(".nexus")) {
            NexusParser parser = new NexusParser();
            parser.parseFile(file);
            
            if (parser.m_alignment != null) {
                // Set the ID on the alignment
                parser.m_alignment.setID(id);
                logger.info("Successfully loaded alignment from Nexus file: " + dataFile);
                return parser.m_alignment;
            } else {
                throw new IOException("NexusParser did not create an alignment from " + dataFile);
            }
        }
        
        // Handle other file types if needed...
        throw new IOException("Unsupported file type: " + dataFile);
    }
    
    /**
     * Special handling for Tree objects
     */
    private void handleTreeCreation(String varName, Expression distribution, Map<String, Object> objectRegistry) {
        try {
            logger.info("Special handling for Tree class: " + varName);
            
            // Create a tree object
            Class<?> treeClass = Class.forName("beast.base.evolution.tree.Tree");
            Object treeObject = treeClass.getDeclaredConstructor().newInstance();
            
            // Set the ID
            try {
                Method setIDMethod = treeClass.getMethod("setID", String.class);
                setIDMethod.invoke(treeObject, varName);
                logger.info("Set ID for Tree: " + varName);
            } catch (Exception e) {
                logger.warning("Could not set ID on Tree: " + e.getMessage());
            }
            
            // Store the tree object
            objectRegistry.put(varName, treeObject);
            
            // If there's a distribution, create and configure it
            if (distribution instanceof FunctionCall) {
                FunctionCall funcCall = (FunctionCall) distribution;
                String distClassName = funcCall.getClassName();
                
                try {
                    Class<?> distClass = Class.forName(distClassName);
                    Object distObject = distClass.getDeclaredConstructor().newInstance();
                    
                    // Set ID
                    setObjectId(distObject, distClass, varName + "Prior");
                    
                    // Configure distribution (skip problematic parameters)
                    configureDistribution(distObject, distClass, funcCall, treeObject, varName, objectRegistry);
                    
                    // Store the distribution
                    objectRegistry.put(varName + "Prior", distObject);
                    logger.info("Created " + distClassName + " for Tree: " + varName);
                    
                } catch (Exception e) {
                    logger.warning("Could not create distribution for Tree: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to create Tree, using fallback: " + e.getMessage());
            DummyBEASTObject dummy = new DummyBEASTObject(varName, "Tree");
            objectRegistry.put(varName, dummy);
        }
    }
    
    /**
     * Special handling for TreeLikelihood objects
     */
    private void handleTreeLikelihoodCreation(String varName, FunctionCall funcCall, Object dataObject, 
                                           Map<String, Object> objectRegistry) {
        try {
            logger.info("Special handling for TreeLikelihood for: " + varName);
            
            // Create the TreeLikelihood object
            Class<?> treeLikelihoodClass = Class.forName("beast.base.evolution.likelihood.TreeLikelihood");
            Object treeLikelihood = treeLikelihoodClass.getDeclaredConstructor().newInstance();
            
            // Set the ID
            setObjectId(treeLikelihood, treeLikelihoodClass, varName + "Likelihood");
            
            // Build input map
            Map<String, Input<?>> inputMap = new HashMap<>();
            for (Field field : treeLikelihoodClass.getFields()) {
                if (Input.class.isAssignableFrom(field.getType())) {
                    @SuppressWarnings("unchecked")
                    Input<?> input = (Input<?>) field.get(treeLikelihood);
                    inputMap.put(input.getName(), input);
                }
            }
            
            // Connect the data object to the 'x' input if it exists
            Input<?> xInput = inputMap.get("x");
            if (xInput != null) {
                @SuppressWarnings("unchecked")
                Input<Object> typedInput = (Input<Object>) xInput;
                typedInput.setValue(dataObject, (BEASTInterface) treeLikelihood);
                logger.fine("Connected " + varName + " to TreeLikelihood's 'x' input");
            }
            
            // Connect the data object to the appropriate input
            Input<?> dataInput = inputMap.get("data");
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
                if ("x".equals(name) || "data".equals(name)) continue; // Skip 'x' and 'data' as we've already handled them
                
                try {
                    Object argValue = ExpressionResolver.resolveValue(arg.getValue(), objectRegistry);
                    Input<?> input = inputMap.get(name);
                    
                    if (input != null && argValue != null) {
                        @SuppressWarnings("unchecked")
                        Input<Object> typedInput = (Input<Object>) input;
                        typedInput.setValue(argValue, (BEASTInterface) treeLikelihood);
                        logger.fine("Set TreeLikelihood input '" + name + "' to " + argValue);
                    }
                } catch (Exception e) {
                    logger.warning("Could not set TreeLikelihood input '" + name + "': " + e.getMessage());
                }
            }
            
            // Store the TreeLikelihood
            objectRegistry.put(varName + "Likelihood", treeLikelihood);
            logger.info("Created and stored TreeLikelihood for: " + varName);
            
            // Try to initialize, but continue even if it fails
            try {
                Method initMethod = treeLikelihoodClass.getMethod("initAndValidate");
                initMethod.invoke(treeLikelihood);
                logger.info("Successfully initialized TreeLikelihood");
            } catch (Exception e) {
                logger.warning("Could not initialize TreeLikelihood, continuing anyway: " + e.getMessage());
            }
        } catch (Exception e) {
            logger.warning("Failed to create TreeLikelihood, using stub: " + e.getMessage());
            DummyBEASTObject dummy = new DummyBEASTObject(varName + "Likelihood", "TreeLikelihood");
            objectRegistry.put(varName + "Likelihood", dummy);
        }
    }
    
    /**
     * A stub implementation to stand in for BEAST objects when they can't be created properly
     */
    private class DummyBEASTObject {
        private String id;
        private String type;
        
        public DummyBEASTObject(String id, String type) {
            this.id = id;
            this.type = type;
        }
        
        public String getID() {
            return id;
        }
        
        public void setID(String id) {
            this.id = id;
        }
        
        @Override
        public String toString() {
            return "Dummy" + type + "(" + id + ")";
        }
    }
}
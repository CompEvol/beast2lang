package org.beast2.modelLanguage.builder;

import org.beast2.modelLanguage.builder.handlers.DistributionAssignmentHandler;
import org.beast2.modelLanguage.builder.handlers.VariableDeclarationHandler;
import org.beast2.modelLanguage.model.*;

import beast.base.inference.StateNode;
import beast.base.inference.parameter.Parameter;
import beast.base.evolution.tree.Tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Implementation of Beast2ObjectFactory that uses reflection to create BEAST2 objects.
 * This class uses the visitor pattern to process statements.
 * Enhanced to better track random variables and observed variables.
 */
public class ReflectionBeast2ObjectFactory implements Beast2ObjectFactory, StatementVisitor {
    
    private static final Logger logger = Logger.getLogger(ReflectionBeast2ObjectFactory.class.getName());
    
    // Handlers for different statement types
    private final VariableDeclarationHandler varDeclHandler;
    private final DistributionAssignmentHandler distAssignHandler;
    
    // Name resolver for handling imports
    private final NameResolver nameResolver;
    
    // Map to store created BEAST2 objects by ID
    private final Map<String, Object> beastObjects;
    
    // Map to store specifically identified StateNode objects
    private final Map<String, StateNode> stateNodeObjects;
    
    // Map to store data files for observed variables
    private final Map<String, String> observedDataFiles;
    
    // Set to track which variables are observed (not just those with data files)
    private final List<String> observedVariables;
    
    // Set to track which variables are random variables (have distributions)
    private final List<String> randomVariables;
    
    // Current annotation being processed
    private Annotation currentAnnotation = null;
    
    /**
     * Constructor that initializes handlers and object registry
     */
    public ReflectionBeast2ObjectFactory() {
        this.varDeclHandler = new VariableDeclarationHandler();
        this.distAssignHandler = new DistributionAssignmentHandler();
        this.nameResolver = new NameResolver();
        this.beastObjects = new HashMap<>();
        this.stateNodeObjects = new HashMap<>();
        this.observedDataFiles = new HashMap<>();
        this.observedVariables = new ArrayList<>();
        this.randomVariables = new ArrayList<>();
    }

    @Override
    public Object buildFromModel(Beast2Model model) throws Exception {
        logger.info("Building BEAST2 objects from model...");
        
        // Clear any existing objects
        beastObjects.clear();
        stateNodeObjects.clear();
        observedDataFiles.clear();
        observedVariables.clear();
        randomVariables.clear();
        
        // Process imports
        processImports(model.getImports());
        
        // Phase 1: Create all objects by visiting all statements
        model.accept(this);
        
        // Phase 2: Identify and track StateNode objects
        identifyStateNodes();
        
        // Phase 3: Connect objects (if needed for complex relationships)
        connectObjects();
        
        // Phase 4: Create a fallback model object if needed
        ensureMinimalModelStructure();
        
        // Phase 5: Find and return the root object
        return findRootObject();
    }
    
    /**
     * Process all import statements
     */
    private void processImports(List<ImportStatement> imports) {
        for (ImportStatement importStmt : imports) {
            if (importStmt.isWildcard()) {
                nameResolver.addWildcardImport(importStmt.getPackageName());
            } else {
                nameResolver.addExplicitImport(importStmt.getPackageName());
            }
        }
    }
    
    /**
     * Identify StateNode objects among created objects
     */
    private void identifyStateNodes() {
        for (Map.Entry<String, Object> entry : beastObjects.entrySet()) {
            Object obj = entry.getValue();
            
            // Check if the object is a StateNode
            if (obj instanceof StateNode) {
                stateNodeObjects.put(entry.getKey(), (StateNode) obj);
                logger.info("Identified StateNode: " + entry.getKey());
            }
        }
        
        // Log the results
        logger.info("Found " + stateNodeObjects.size() + " StateNode objects");
        logger.info("Found " + randomVariables.size() + " random variables");
        logger.info("Found " + observedVariables.size() + " observed variables");
    }
    
    /**
     * Handle VariableDeclaration statements
     */
    @Override
    public void visit(VariableDeclaration varDecl) {
        try {
            logger.fine("Processing VariableDeclaration: " + varDecl.getVariableName());
            
            // Resolve the class name using imports
            String resolvedClassName = nameResolver.resolveClassName(varDecl.getClassName());
            VariableDeclaration resolvedVarDecl = new VariableDeclaration(
                resolvedClassName, 
                varDecl.getVariableName(), 
                resolveExpressionClassNames(varDecl.getValue())
            );
            
            // Create the object
            Object beastObject = varDeclHandler.createObject(resolvedVarDecl, beastObjects);
            
            // Store the object
            String variableName = resolvedVarDecl.getVariableName();
            beastObjects.put(variableName, beastObject);
            
            // Store all StateNode objects for reference but don't mark as random variables
            // (only DistributionAssignments create random variables)
            if (beastObject instanceof StateNode) {
                stateNodeObjects.put(variableName, (StateNode) beastObject);
                logger.info("Variable " + variableName + " is a StateNode (from VariableDeclaration)");
            }
            
            logger.info("Created and stored object: " + variableName);
        } catch (Exception e) {
            logger.severe("Error processing variable declaration: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * No longer needed since we check if an object is a StateNode directly,
     * but kept for compatibility with other methods that may call it.
     */
    private boolean isStateNodeClass(String className) {
        return className.contains("parameter.") || 
               className.contains("Parameter") || 
               className.contains("Tree") || 
               className.contains("StateNode");
    }
    
    /**
     * Handle DistributionAssignment statements
     */
    @Override
    public void visit(DistributionAssignment distAssign) {
        try {
            logger.fine("Processing DistributionAssignment: " + distAssign.getVariableName());
            
            // Resolve the class name using imports
            String resolvedClassName = nameResolver.resolveClassName(distAssign.getClassName());
            DistributionAssignment resolvedDistAssign = new DistributionAssignment(
                resolvedClassName, 
                distAssign.getVariableName(), 
                resolveExpressionClassNames(distAssign.getDistribution())
            );
            
            // Record that this is a random variable
            String varName = resolvedDistAssign.getVariableName();
            randomVariables.add(varName);
            
            // Check if this is an observed variable with a data file
            if (observedDataFiles.containsKey(varName)) {
                // This is an observed variable, include the data file in creation
                distAssignHandler.createObservedObjects(
                    resolvedDistAssign, 
                    beastObjects, 
                    observedDataFiles.get(varName)
                );
                
                // Mark as observed
                observedVariables.add(varName);
            } else {
                // Regular distribution assignment
                distAssignHandler.createObjects(resolvedDistAssign, beastObjects);
            }
            
            logger.info("Created distribution for: " + varName);
        } catch (Exception e) {
            logger.severe("Error processing distribution assignment: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle AnnotatedStatement statements
     */
    public void visit(AnnotatedStatement annotatedStmt) {
        Annotation annotation = annotatedStmt.getAnnotation();
        Statement innerStmt = annotatedStmt.getStatement();

        System.out.println("Visit AnnotatedStatement: " + annotation.getName());
        System.out.println("  - Inner statement type: " + innerStmt.getClass().getSimpleName());

        // Debug all annotation parameters
        System.out.println("  - Annotation parameters:");
        for (String paramName : annotation.getParameters().keySet()) {
            System.out.println("    - " + paramName + " = " + annotation.getParameterAsString(paramName));
        }

        // Check if it's a distribution assignment
        if (innerStmt instanceof DistributionAssignment) {
            DistributionAssignment distAssign = (DistributionAssignment) innerStmt;
            System.out.println("  - Variable name: " + distAssign.getVariableName());
            System.out.println("  - Class name: " + distAssign.getClassName());
        }

        // Process annotations
        if ("observed".equals(annotation.getName())) {
            // Handle @observed annotation
            if (innerStmt instanceof DistributionAssignment) {
                DistributionAssignment distAssign = (DistributionAssignment) innerStmt;
                String varName = distAssign.getVariableName();

                // Mark as observed regardless of data file
                observedVariables.add(varName);
                System.out.println("  ✅ Marked " + varName + " as observed");
                logger.info("Marked " + varName + " as observed");

                // Handle data file if present
                if (annotation.hasParameter("alignment")) {
                    String dataFile = annotation.getParameterAsString("alignment");
                    observedDataFiles.put(varName, dataFile);
                    System.out.println("  ✅ Added alignment file: " + dataFile);
                    logger.info("Observed variable " + varName + " has alignment file: " + dataFile);
                } else if (annotation.hasParameter("data")) {
                    String dataFile = annotation.getParameterAsString("data");
                    observedDataFiles.put(varName, dataFile);
                    System.out.println("  ✅ Added data file: " + dataFile);
                    logger.info("Observed variable " + varName + " has data file: " + dataFile);
                } else {
                    System.out.println("  ❌ No data or alignment parameter found!");
                }

                // Debug the maps after update
                System.out.println("  - observedVariables size: " + observedVariables.size());
                System.out.println("  - observedDataFiles size: " + observedDataFiles.size());
            } else {
                System.out.println("  ❌ @observed annotation only applies to distribution assignments");
                logger.warning("@observed annotation only applies to distribution assignments");
            }
        } else {
            System.out.println("  ❌ Unknown annotation: " + annotation.getName());
            logger.warning("Unknown annotation: " + annotation.getName());
        }

        // Process the inner statement
        System.out.println("  - Now visiting inner statement...");
        innerStmt.accept(this);
        System.out.println("  - Finished visiting inner statement");
    }
    
    /**
     * Resolve class names in an expression using imports
     */
    private Expression resolveExpressionClassNames(Expression expr) {
        if (expr instanceof FunctionCall) {
            FunctionCall funcCall = (FunctionCall) expr;
            String resolvedClassName = nameResolver.resolveClassName(funcCall.getClassName());
            
            // Resolve class names in arguments
            List<Argument> resolvedArgs = funcCall.getArguments();
            for (int i = 0; i < resolvedArgs.size(); i++) {
                Argument arg = resolvedArgs.get(i);
                Expression resolvedValue = resolveExpressionClassNames(arg.getValue());
                if (resolvedValue != arg.getValue()) {
                    resolvedArgs.set(i, new Argument(arg.getName(), resolvedValue));
                }
            }
            
            return new FunctionCall(resolvedClassName, resolvedArgs);
        } else if (expr instanceof Identifier) {
            // Identifiers don't need resolution
            return expr;
        } else {
            // Literals don't need resolution
            return expr;
        }
    }
    
    /**
     * Connect objects with complex relationships that couldn't be fully resolved during creation
     */
    private void connectObjects() {
        // In most cases, the connections are already made during object creation
        // This method could be expanded for more complex models with circular dependencies
        logger.fine("Object connection phase completed");
    }
    
    /**
     * Ensure the model has at least a minimal structure needed for BEAST2 XML
     */
    private void ensureMinimalModelStructure() {
        // Check if we have all the essential components
        boolean hasTree = false;
        boolean hasSiteModel = false;
        boolean hasAlignment = false;
        
        for (Object obj : beastObjects.values()) {
            String className = obj.getClass().getName();
            if (className.contains(".Tree")) {
                hasTree = true;
            } else if (className.contains(".SiteModel")) {
                hasSiteModel = true;
            } else if (className.contains(".Alignment")) {
                hasAlignment = true;
            }
        }
        
        // Create a minimal model structure for XML output if needed
        if (!hasAlignment && !hasTree && !hasSiteModel) {
            logger.info("Creating minimal model structure for proper XML output");
            
            try {
                // If we're missing key components, create a simple Run element as the root
                Class<?> runClass = Class.forName("beast.base.inference.Runnable");
                if (runClass != null) {
                    Object runObject = runClass.getDeclaredConstructor().newInstance();
                    
                    // Set an ID
                    try {
                        java.lang.reflect.Method setIDMethod = runClass.getMethod("setID", String.class);
                        setIDMethod.invoke(runObject, "run");
                    } catch (Exception e) {
                        logger.warning("Could not set ID on Run object: " + e.getMessage());
                    }
                    
                    // Store it
                    beastObjects.put("run", runObject);
                    logger.info("Created Run object as fallback root");
                }
            } catch (Exception e) {
                logger.warning("Failed to create minimal model structure: " + e.getMessage());
            }
        }
    }
    
    /**
     * Find the root object of the model
     */
    private Object findRootObject() {
        // Look for likely candidates for root objects based on naming conventions
        for (String key : beastObjects.keySet()) {
            // MCMC is typically the root in BEAST2 XML
            if (key.toLowerCase().contains("mcmc")) {
                logger.info("Found root object: " + key);
                return beastObjects.get(key);
            }
        }
        
        // If no MCMC, look for other likely candidates
        for (String key : beastObjects.keySet()) {
            if (key.toLowerCase().contains("posterior") || 
                key.toLowerCase().contains("model") ||
                key.toLowerCase().contains("tree")) {
                logger.info("Found potential root object: " + key);
                return beastObjects.get(key);
            }
        }
        
        // If no obvious root, return the first object as a fallback
        if (!beastObjects.isEmpty()) {
            String firstKey = beastObjects.keySet().iterator().next();
            logger.info("Using first object as root: " + firstKey);
            return beastObjects.get(firstKey);
        }
        
        logger.warning("No objects found in model");
        return null;
    }

    @Override
    public Map<String, Object> getAllObjects() {
        return new HashMap<>(beastObjects); // Return a copy to prevent external modification
    }

    @Override
    public Object getObject(String name) {
        return beastObjects.get(name);
    }
    
      
    /**
     * Get all created state nodes that should be part of MCMC sampling.
     * Only includes StateNodes that are random variables (have distributions)
     * and are not observed.
     */
    public List<StateNode> getCreatedStateNodes() {
        List<StateNode> stateNodes = new ArrayList<>();
        
        // Include only random variables (from DistributionAssignments) that are StateNodes
        // and are not observed
        for (String varName : randomVariables) {
            if (!observedVariables.contains(varName)) { // Skip observed variables
                Object obj = beastObjects.get(varName);
                if (obj instanceof StateNode) {
                    stateNodes.add((StateNode) obj);
                    logger.fine("Added random variable to state nodes: " + varName);
                } else {
                    logger.warning("Random variable " + varName + " is not a StateNode, cannot add to state");
                }
            } else {
                logger.fine("Skipped observed variable: " + varName);
            }
        }
        
        logger.info("Returning " + stateNodes.size() + " StateNode objects for state");
        return stateNodes;
    }
    
    /**
     * Get all created distributions
     */
    public List<beast.base.inference.Distribution> getCreatedDistributions() {
        List<beast.base.inference.Distribution> distributions = new ArrayList<>();
        
        for (Object obj : beastObjects.values()) {
            if (obj instanceof beast.base.inference.Distribution) {
                distributions.add((beast.base.inference.Distribution) obj);
            }
        }
        
        return distributions;
    }
    
    /**
     * Get all random variables (variables with distributions)
     */
    public List<String> getRandomVariables() {
        return new ArrayList<>(randomVariables);
    }
    
    /**
     * Get all observed variables
     */
    public List<String> getObservedVariables() {
        return new ArrayList<>(observedVariables);
    }
    
    /**
     * Check if a variable is observed
     */
    public boolean isObserved(String variableName) {
        return observedVariables.contains(variableName);
    }
}
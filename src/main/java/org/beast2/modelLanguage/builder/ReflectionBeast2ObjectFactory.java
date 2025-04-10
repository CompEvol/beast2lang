package org.beast2.modelLanguage.builder;

import org.beast2.modelLanguage.builder.handlers.DistributionAssignmentHandler;
import org.beast2.modelLanguage.builder.handlers.VariableDeclarationHandler;
import org.beast2.modelLanguage.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Implementation of Beast2ObjectFactory that uses reflection to create BEAST2 objects.
 * This class uses the visitor pattern to process statements.
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
    
    // Map to store data files for observed variables
    private final Map<String, String> observedDataFiles;
    
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
        this.observedDataFiles = new HashMap<>();
    }

    @Override
    public Object buildFromModel(Beast2Model model) throws Exception {
        logger.info("Building BEAST2 objects from model...");
        
        // Clear any existing objects
        beastObjects.clear();
        observedDataFiles.clear();
        
        // Process imports
        processImports(model.getImports());
        
        // Phase 1: Create all objects by visiting all statements
        model.accept(this);
        
        // Phase 2: Connect objects (if needed for complex relationships)
        connectObjects();
        
        // Phase 3: Create a fallback model object if needed
        ensureMinimalModelStructure();
        
        // Phase 4: Find and return the root object
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
            logger.info("Created and stored object: " + variableName);
        } catch (Exception e) {
            logger.severe("Error processing variable declaration: " + e.getMessage());
            e.printStackTrace();
        }
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
            
            // Check if this is an observed variable with a data file
            String varName = resolvedDistAssign.getVariableName();
            if (observedDataFiles.containsKey(varName)) {
                // This is an observed variable, include the data file in creation
                distAssignHandler.createObservedObjects(
                    resolvedDistAssign, 
                    beastObjects, 
                    observedDataFiles.get(varName)
                );
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
    @Override
    public void visit(AnnotatedStatement annotatedStmt) {
        Annotation annotation = annotatedStmt.getAnnotation();
        Statement innerStmt = annotatedStmt.getStatement();
        
        // Process annotations
        if ("observed".equals(annotation.getName())) {
            // Handle @observed annotation
            if (annotation.hasParameter("data")) {
                String dataFile = annotation.getParameterAsString("data");
                
                if (innerStmt instanceof DistributionAssignment) {
                    DistributionAssignment distAssign = (DistributionAssignment) innerStmt;
                    String varName = distAssign.getVariableName();
                    observedDataFiles.put(varName, dataFile);
                    logger.info("Marked " + varName + " as observed with data file: " + dataFile);
                } else {
                    logger.warning("@observed annotation only applies to distribution assignments");
                }
            } else {
                logger.warning("@observed annotation missing data parameter");
            }
        } else {
            logger.warning("Unknown annotation: " + annotation.getName());
        }
        
        // Process the inner statement
        innerStmt.accept(this);
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
}
package org.beast2.modelLanguage.beast;

import beast.base.core.BEASTInterface;
import beast.base.inference.StateNode;

import org.beast2.modelLanguage.builder.Beast2ObjectFactory;
import org.beast2.modelLanguage.builder.NameResolver;
import org.beast2.modelLanguage.builder.handlers.DistributionAssignmentHandler;
import org.beast2.modelLanguage.builder.handlers.VariableDeclarationHandler;
import org.beast2.modelLanguage.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Implementation of Beast2ObjectFactory that uses reflection to create BEAST2 objects.
 * This class uses the visitor pattern to process statements.
 * Enhanced to handle @data and @observed annotations.
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

    // Map to store variables annotated with @data
    private final Map<String, Boolean> dataAnnotatedVars;

    // Set to track which variables are observed (including those with @observed annotation)
    private final List<String> observedVariables;

    // Set to track which variables are random variables (have distributions)
    private final List<String> randomVariables;

    private final Map<String, String> observedDataRefs = new HashMap<>();

    /**
     * Constructor that initializes handlers and object registry
     */
    public ReflectionBeast2ObjectFactory() {
        this.varDeclHandler = new VariableDeclarationHandler();
        this.distAssignHandler = new DistributionAssignmentHandler();
        this.nameResolver = new NameResolver();
        this.beastObjects = new HashMap<>();
        this.stateNodeObjects = new HashMap<>();
        this.dataAnnotatedVars = new HashMap<>();
        this.observedVariables = new ArrayList<>();
        this.randomVariables = new ArrayList<>();
    }

    @Override
    public Object buildFromModel(Beast2Model model) {
        logger.info("Building BEAST2 objects from model...");

        // Clear any existing objects
        beastObjects.clear();
        stateNodeObjects.clear();
        dataAnnotatedVars.clear();
        observedVariables.clear();
        randomVariables.clear();

        // Process imports
        processImports(model.getImports());

        // Process requires statements first to ensure proper class loading
        // before creating any objects
        processRequiresStatements(model);

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
     * Process all requires statements to load necessary BEAST packages
     */
    private void processRequiresStatements(Beast2Model model) {
        for (RequiresStatement stmt : model.getRequires()) {
            visit(stmt);
        }
    }

    @Override
    public void addObjectToModel(String id, Object object) {
        beastObjects.put(id, object);
        logger.info("Added object to model: " + id);

        // Also track StateNode objects if applicable
        if (object instanceof StateNode) {
            stateNodeObjects.put(id, (StateNode) object);
            logger.info("Added StateNode to model: " + id);
        }
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
        logger.info("Found " + dataAnnotatedVars.size() + " data-annotated variables");
    }

    /**
     * Handle RequiresStatement statements
     */
    public void visit(RequiresStatement requiresStmt) {
        try {
            String pluginName = requiresStmt.getPluginName();
            logger.info("Processing requires statement for BEAST package: " + pluginName);

            // Add the required package to the name resolver for proper class resolution
            nameResolver.addRequiredPackage(pluginName);

            logger.info("Added required BEAST package: " + pluginName);
        } catch (Exception e) {
            logger.severe("Error processing requires statement: " + e.getMessage());
            e.printStackTrace();
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

            // Special handling for NexusFunction
            if (resolvedVarDecl.getValue() instanceof NexusFunction) {
                handleNexusFunctionCall(resolvedVarDecl);
                return;
            }

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
     * Handle nexus() function calls
     */
    private void handleNexusFunctionCall(VariableDeclaration varDecl) {
        try {
            String varName = varDecl.getVariableName();
            NexusFunction nexusFunc = (NexusFunction) varDecl.getValue();

            // Create and process the nexus function
            logger.info("Processing nexus() function for variable: " + varName);

            // Use the NexusFunctionHandler to process the function
            org.beast2.modelLanguage.builder.handlers.NexusFunctionHandler handler =
                    new org.beast2.modelLanguage.builder.handlers.NexusFunctionHandler();

            // Process the function and get the alignment
            BEASTInterface alignment =
                    (BEASTInterface) handler.processFunction(nexusFunc, beastObjects);

            // Set the ID on the alignment if needed
            if (alignment.getID() == null || !alignment.getID().equals(varName)) {
                alignment.setID(varName);
            }

            // Store the alignment in the registry with the variable name
            // This is crucial for observed variables that reference this data
            beastObjects.put(varName, alignment);

            logger.info("Created and stored Alignment from nexus() function: " + varName);

        } catch (Exception e) {
            logger.severe("Error processing nexus() function: " + e.getMessage());
            throw new RuntimeException("Failed to process nexus() function: " + e.getMessage(), e);
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

            // Record that this is a random variable
            String varName = resolvedDistAssign.getVariableName();
            randomVariables.add(varName);

            // Check if this is a regular or observed variable
            if (observedVariables.contains(varName)) {
                // This is an observed variable
                logger.info("Processing observed distribution assignment: " + varName);

                // Find the referenced data object from prior annotation processing
                String dataRef = findDataReferenceForObserved(varName);
                if (dataRef != null) {
                    // Verify that the data reference exists in the registry
                    if (!beastObjects.containsKey(dataRef)) {
                        logger.severe("Data reference not found in object registry: " + dataRef);
                        throw new IllegalArgumentException("Data reference not found: " + dataRef);
                    }
                    distAssignHandler.createObservedObjects(resolvedDistAssign, beastObjects, dataRef);
                } else {
                    logger.warning("No data reference found for observed variable: " + varName);
                    // Fall back to regular processing
                    distAssignHandler.createObjects(resolvedDistAssign, beastObjects);
                }
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
     * Find the data reference for an observed variable
     */
    private String findDataReferenceForObserved(String varName) {
        // Look up the specific data reference for this observed variable
        return observedDataRefs.get(varName);
    }

    /**
     * Handle AnnotatedStatement statements
     */
    @Override
    public void visit(AnnotatedStatement annotatedStmt) {
        Annotation annotation = annotatedStmt.getAnnotation();
        Statement innerStmt = annotatedStmt.getStatement();

        logger.info("Processing annotated statement with @" + annotation.getName() + " annotation");

        // Process based on annotation type
        if ("data".equals(annotation.getName())) {
            // Handle @data annotation
            if (innerStmt instanceof VariableDeclaration) {
                String varName = ((VariableDeclaration) innerStmt).getVariableName();
                dataAnnotatedVars.put(varName, Boolean.TRUE);
                logger.info("Registered variable with @data annotation: " + varName);
            } else {
                logger.warning("@data annotation can only be applied to variable declarations");
            }
        } else if ("observed".equals(annotation.getName())) {
            // Handle @observed annotation
            if (innerStmt instanceof DistributionAssignment) {
                String varName = ((DistributionAssignment) innerStmt).getVariableName();

                // Check for data parameter
                if (annotation.hasParameter("data")) {
                    String dataRef = annotation.getParameterAsString("data");

                    // Store reference to data object
                    logger.info("Registered variable with @observed annotation: " + varName +
                            " with data reference: " + dataRef);

                    // Store mapping for later use
                    observedVariables.add(varName);
                    observedDataRefs.put(varName, dataRef); // Store the data reference
                } else {
                    logger.warning("@observed annotation requires a 'data' parameter");
                }
            }
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
        } else if (expr instanceof NexusFunction) {
            NexusFunction nexusFunc = (NexusFunction) expr;

            // Resolve class names in arguments
            List<Argument> resolvedArgs = new ArrayList<>(nexusFunc.getArguments());
            for (int i = 0; i < resolvedArgs.size(); i++) {
                Argument arg = resolvedArgs.get(i);
                Expression resolvedValue = resolveExpressionClassNames(arg.getValue());
                if (resolvedValue != arg.getValue()) {
                    resolvedArgs.set(i, new Argument(arg.getName(), resolvedValue));
                }
            }

            return new NexusFunction(resolvedArgs);
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
     * Get all data-annotated variables
     */
    public List<String> getDataAnnotatedVariables() {
        return new ArrayList<>(dataAnnotatedVars.keySet());
    }

    /**
     * Check if a variable is observed
     */
    public boolean isObserved(String variableName) {
        return observedVariables.contains(variableName);
    }

    /**
     * Check if a variable is data-annotated
     */
    public boolean isDataAnnotated(String variableName) {
        return dataAnnotatedVars.containsKey(variableName);
    }
}
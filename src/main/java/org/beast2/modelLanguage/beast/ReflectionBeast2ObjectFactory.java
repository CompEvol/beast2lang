package org.beast2.modelLanguage.beast;

import beast.base.core.BEASTInterface;
import beast.base.inference.StateNode;

import org.beast2.modelLanguage.builder.Beast2ObjectFactory;
import org.beast2.modelLanguage.builder.NameResolver;
import org.beast2.modelLanguage.builder.ObjectRegistry;
import org.beast2.modelLanguage.builder.handlers.DistributionAssignmentHandler;
import org.beast2.modelLanguage.builder.handlers.VariableDeclarationHandler;
import org.beast2.modelLanguage.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Implementation of Beast2ObjectFactory that uses reflection to create BEAST2 objects.
 * Refactored to use BeastObjectRegistry to eliminate circular dependencies.
 */
public class ReflectionBeast2ObjectFactory implements Beast2ObjectFactory, StatementVisitor {

    private static final Logger logger = Logger.getLogger(ReflectionBeast2ObjectFactory.class.getName());

    // Handlers for different statement types
    private final VariableDeclarationHandler varDeclHandler;
    private final DistributionAssignmentHandler distAssignHandler;

    // Name resolver for handling imports
    private final NameResolver nameResolver;

    // Shared registry - injected via constructor
    private final ObjectRegistry registry;

    /**
     * Constructor that accepts a registry
     */
    public ReflectionBeast2ObjectFactory(ObjectRegistry registry) {
        this.varDeclHandler = new VariableDeclarationHandler();
        this.distAssignHandler = new DistributionAssignmentHandler();
        this.nameResolver = new NameResolver();
        this.registry = registry;
    }

    @Override
    public Object buildFromModel(Beast2Model model) {
        logger.info("Building BEAST2 objects from model...");

        // Process imports
        processImports(model.getImports());

        // Process requires statements first
        processRequiresStatements(model);

        // Phase 1: Create all objects by visiting all statements
        model.accept(this);

        // Phase 2: Create a fallback model object if needed
        ensureMinimalModelStructure();

        // Phase 3: Find and return the root object
        return findRootObject();
    }

    @Override
    public void addObjectToModel(String id, Object object) {
        registry.register(id, object);
    }

    /**
     * Process all requires statements to load necessary BEAST packages
     */
    private void processRequiresStatements(Beast2Model model) {
        for (RequiresStatement stmt : model.getRequires()) {
            visit(stmt);
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
     * Handle RequiresStatement statements
     */
    public void visit(RequiresStatement requiresStmt) {
        try {
            String pluginName = requiresStmt.getPluginName();
            logger.info("Processing requires statement for BEAST package: " + pluginName);
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

            // Create the object using the handler
            Object beastObject = varDeclHandler.createObject(resolvedVarDecl, registry);

            // Store the object
            String variableName = resolvedVarDecl.getVariableName();
            registry.register(variableName, beastObject);

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

            logger.info("Processing nexus() function for variable: " + varName);

            // Use the NexusFunctionHandler to process the function
            org.beast2.modelLanguage.builder.handlers.NexusFunctionHandler handler =
                    new org.beast2.modelLanguage.builder.handlers.NexusFunctionHandler();

            // Process the function with the registry
            BEASTInterface alignment = (BEASTInterface) handler.processFunction(nexusFunc, registry);

            // Set the ID on the alignment if needed
            if (alignment.getID() == null || !alignment.getID().equals(varName)) {
                alignment.setID(varName);
            }

            // Store the alignment in the registry
            registry.register(varName, alignment);

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
            registry.markAsRandomVariable(varName);

            // Check if this is a regular or observed variable
            if (registry instanceof BeastObjectRegistry &&
                    ((BeastObjectRegistry) registry).isObservedVariable(varName)) {
                // This is an observed variable
                logger.info("Processing observed distribution assignment: " + varName);

                // Find the referenced data object
                String dataRef = ((BeastObjectRegistry) registry).getDataReference(varName);
                if (dataRef != null && registry.contains(dataRef)) {
                    distAssignHandler.createObservedObjects(resolvedDistAssign, registry, dataRef);
                } else {
                    logger.warning("No data reference found for observed variable: " + varName);
                    distAssignHandler.createObjects(resolvedDistAssign, registry);
                }
            } else {
                // Regular distribution assignment
                distAssignHandler.createObjects(resolvedDistAssign, registry);
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

        logger.info("Processing annotated statement with @" + annotation.getName() + " annotation");

        // Process based on annotation type
        if ("data".equals(annotation.getName())) {
            // Handle @data annotation
            if (innerStmt instanceof VariableDeclaration) {
                String varName = ((VariableDeclaration) innerStmt).getVariableName();
                registry.markAsDataAnnotated(varName);
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
                    registry.markAsObservedVariable(varName, dataRef);
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
            List<Argument> resolvedArgs = new ArrayList<>(funcCall.getArguments());
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
        } else {
            // Identifiers and Literals don't need resolution
            return expr;
        }
    }

    /**
     * Ensure the model has at least a minimal structure needed for BEAST2 XML
     */
    private void ensureMinimalModelStructure() {
        Map<String, Object> objects = registry.getAllObjects();

        // Check if we have all the essential components
        boolean hasTree = false;
        boolean hasSiteModel = false;
        boolean hasAlignment = false;

        for (Object obj : objects.values()) {
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
                    registry.register("run", runObject);
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
        Map<String, Object> objects = registry.getAllObjects();

        // Look for likely candidates for root objects based on naming conventions
        for (String key : objects.keySet()) {
            // MCMC is typically the root in BEAST2 XML
            if (key.toLowerCase().contains("mcmc")) {
                logger.info("Found root object: " + key);
                return objects.get(key);
            }
        }

        // If no MCMC, look for other likely candidates
        for (String key : objects.keySet()) {
            if (key.toLowerCase().contains("posterior") ||
                    key.toLowerCase().contains("model") ||
                    key.toLowerCase().contains("tree")) {
                logger.info("Found potential root object: " + key);
                return objects.get(key);
            }
        }

        // If no obvious root, return the first object as a fallback
        if (!objects.isEmpty()) {
            String firstKey = objects.keySet().iterator().next();
            logger.info("Using first object as root: " + firstKey);
            return objects.get(firstKey);
        }

        logger.warning("No objects found in model");
        return null;
    }

    @Override
    public Map<String, Object> getAllObjects() {
        return registry.getAllObjects();
    }

    @Override
    public Object getObject(String name) {
        return registry.get(name);
    }

    /**
     * Get all created distributions
     */
    public List<beast.base.inference.Distribution> getCreatedDistributions() {
        if (registry instanceof BeastObjectRegistry) {
            return ((BeastObjectRegistry) registry).getDistributions();
        }

        // Fallback if not using BeastObjectRegistry
        List<beast.base.inference.Distribution> distributions = new ArrayList<>();
        for (Object obj : registry.getAllObjects().values()) {
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
        if (registry instanceof BeastObjectRegistry) {
            return ((BeastObjectRegistry) registry).getRandomVariables();
        }
        return new ArrayList<>();
    }

    /**
     * Get all observed variables
     */
    public List<String> getObservedVariables() {
        if (registry instanceof BeastObjectRegistry) {
            return ((BeastObjectRegistry) registry).getObservedVariables();
        }
        return new ArrayList<>();
    }

    /**
     * Get all data-annotated variables
     */
    public List<String> getDataAnnotatedVariables() {
        if (registry instanceof BeastObjectRegistry) {
            return ((BeastObjectRegistry) registry).getDataAnnotatedVariables();
        }
        return new ArrayList<>();
    }

    /**
     * Check if a variable is observed
     */
    public boolean isObserved(String variableName) {
        if (registry instanceof BeastObjectRegistry) {
            return ((BeastObjectRegistry) registry).isObservedVariable(variableName);
        }
        return false;
    }

    /**
     * Check if a variable is data-annotated
     */
    public boolean isDataAnnotated(String variableName) {
        if (registry instanceof BeastObjectRegistry) {
            return ((BeastObjectRegistry) registry).isDataAnnotated(variableName);
        }
        return false;
    }
}
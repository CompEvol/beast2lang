package org.beast2.modelLanguage.builder;

import org.beast2.modelLanguage.beast.BeastObjectRegistry;
import org.beast2.modelLanguage.builder.handlers.DistributionAssignmentHandler;
import org.beast2.modelLanguage.builder.handlers.VariableDeclarationHandler;
import org.beast2.modelLanguage.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Processes Beast2Model statements using the visitor pattern to create model objects.
 * This class is framework-agnostic and simply processes the AST to create objects.
 */
public class ModelStatementProcessor implements StatementVisitor {

    private static final Logger logger = Logger.getLogger(ModelStatementProcessor.class.getName());

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
    public ModelStatementProcessor(ObjectRegistry registry) {
        this.varDeclHandler = new VariableDeclarationHandler();
        this.distAssignHandler = new DistributionAssignmentHandler();
        this.nameResolver = new NameResolver();
        this.registry = registry;
    }

    public void buildFromModel(Beast2Model model) {
        logger.info("Processing Beast2Model statements...");

        // Process imports
        processImports(model.getImports());

        // Process requires statements first
        processRequiresStatements(model);

        // Process all statements by visiting them
        model.accept(this);

        logger.info("Finished processing model statements");
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
            Object alignment = handler.processFunction(nexusFunc, registry);

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
            if (isObservedVariable(varName)) {
                // This is an observed variable
                logger.info("Processing observed distribution assignment: " + varName);

                // Find the referenced data object
                String dataRef = getDataReference(varName);
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
        Statement innerStmt = annotatedStmt.getStatement();

        // 1) If this is a Tree distribution, attach any @calibration annotations to it
        if (innerStmt instanceof DistributionAssignment) {
            DistributionAssignment da = (DistributionAssignment) innerStmt;
            String treeVar = da.getVariableName();

                for (Annotation ann : annotatedStmt.getAnnotations()) {
                    if ("calibration".equals(ann.getName())) {
                        // taxonset → IdentifierExpr.getName()
                        Identifier taxId = (Identifier)ann.getParameter("taxonset");
                        String taxonset = taxId.getName();
                        // distribution → FunctionCall AST
                        FunctionCall dist = (FunctionCall)ann.getParameter("distribution");
                        registry.addCalibration(treeVar, new Calibration(taxonset, dist));
                    }
                }
        }

        // 2) Now handle @data and @observed (unchanged)
        for (Annotation annotation : annotatedStmt.getAnnotations()) {
            String name = annotation.getName();
            if ("data".equals(name)) {
                if (innerStmt instanceof VariableDeclaration) {
                    String varName = ((VariableDeclaration) innerStmt).getVariableName();
                    registry.markAsDataAnnotated(varName);
                    logger.info("Registered variable with @data annotation: " + varName);
                } else {
                    logger.warning("@data annotation can only be applied to variable declarations");
                }

            } else if ("observed".equals(name)) {
                if (innerStmt instanceof DistributionAssignment) {
                    String varName = ((DistributionAssignment) innerStmt).getVariableName();
                    if (annotation.hasParameter("data")) {
                        String dataRef = annotation.getParameterAsIdentifer("data");
                        registry.markAsObservedVariable(varName, dataRef);
                    } else {
                        logger.warning("@observed annotation requires a 'data' parameter");
                    }
                }
            }
        }

        // 3) Finally, delegate the actual statement processing
        innerStmt.accept(this);
    }


    /**
     * Resolve class names in an expression using imports
     */
    private Expression resolveExpressionClassNames(Expression expr) {
        if (expr instanceof FunctionCall funcCall) {
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
        } else if (expr instanceof NexusFunction nexusFunc) {

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

    // Helper methods to check registry state
    private boolean isObservedVariable(String varName) {
        if (registry instanceof BeastObjectRegistry) {
            return ((BeastObjectRegistry) registry).isObservedVariable(varName);
        }
        return false;
    }

    private String getDataReference(String varName) {
        if (registry instanceof BeastObjectRegistry) {
            return ((BeastObjectRegistry) registry).getDataReference(varName);
        }
        return null;
    }
}
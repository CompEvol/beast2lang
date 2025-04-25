package org.beast2.modelLanguage.builder;

import org.beast2.modelLanguage.model.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Transforms a Beast2Model by mapping PhyloSpec types, distributions and parameters
 * to their BEAST2 equivalents.
 */
public class PhyloSpecModelTransformer {
    private static final Logger logger = Logger.getLogger(PhyloSpecModelTransformer.class.getName());

    // Track imports we need to add
    private final Set<String> requiredImports = new HashSet<>();

    // Track distribution names that have been created
    private final Map<String, String> createdDistributions = new HashMap<>();

    /**
     * Transform a parsed Beast2Model by mapping PhyloSpec elements to BEAST2 equivalents
     */
    public Beast2Model transform(Beast2Model model) {
        // Reset state
        requiredImports.clear();
        createdDistributions.clear();

        // Create new model
        Beast2Model transformedModel = new Beast2Model();

        // First pass - collect all PhyloSpec types and distributions
        scanModelForRequiredImports(model);

        // Add required imports first
        addRequiredImports(transformedModel);

        // Add existing imports from the original model
        addExistingImports(model, transformedModel);

        // Transform all statements
        for (Statement stmt : model.getStatements()) {
            // Skip import statements as we've already handled them
            if (stmt instanceof ImportStatement) {
                continue;
            }

            List<Statement> transformedStmts = transformStatementToList(stmt);
            for (Statement transformedStmt : transformedStmts) {
                transformedModel.addStatement(transformedStmt);
            }
        }

        return transformedModel;
    }

    /**
     * Scan model to identify all required imports
     */
    private void scanModelForRequiredImports(Beast2Model model) {
        // Analyze all statements to find PhyloSpec types and distributions
        for (Statement stmt : model.getStatements()) {
            if (stmt instanceof VariableDeclaration) {
                VariableDeclaration decl = (VariableDeclaration) stmt;
                String className = decl.getClassName();

                if (PhyloSpecMapper.isPhyloSpecType(className)) {
                    String mappedType = PhyloSpecMapper.mapType(className);
                    addImportForClass(mappedType);
                }

                scanExpression(decl.getValue());
            }
            else if (stmt instanceof DistributionAssignment) {
                DistributionAssignment distAssign = (DistributionAssignment) stmt;
                String className = distAssign.getClassName();

                if (PhyloSpecMapper.isPhyloSpecType(className)) {
                    String mappedType = PhyloSpecMapper.mapType(className);
                    addImportForClass(mappedType);
                }

                scanExpression(distAssign.getDistribution());

                // If this is Real ~ ParametricDist pattern, add Prior import
                if (className.equals("Real") && distAssign.getDistribution() instanceof FunctionCall) {
                    FunctionCall distFunc = (FunctionCall) distAssign.getDistribution();
                    if (PhyloSpecMapper.mapsToParametricDistribution(distFunc.getClassName())) {
                        addImportForClass("beast.base.inference.distribution.Prior");
                    }
                }
            }
        }
    }

    /**
     * Scan an expression for PhyloSpec elements
     */
    private void scanExpression(Expression expr) {
        if (expr == null) {
            return;
        }

        if (expr instanceof FunctionCall) {
            FunctionCall funcCall = (FunctionCall) expr;
            String funcName = funcCall.getClassName();

            if (PhyloSpecMapper.isPhyloSpecDistribution(funcName)) {
                String mappedFunc = PhyloSpecMapper.mapDistribution(funcName);
                addImportForClass(mappedFunc);
            }

            // Scan arguments recursively
            for (Argument arg : funcCall.getArguments()) {
                scanExpression(arg.getValue());
            }
        }
    }

    /**
     * Add import for a fully qualified class name
     */
    private void addImportForClass(String fullyQualifiedClassName) {
        int lastDot = fullyQualifiedClassName.lastIndexOf('.');
        if (lastDot > 0) {
            String packageName = fullyQualifiedClassName.substring(0, lastDot);
            requiredImports.add(packageName);
        }
    }

    /**
     * Add required imports to the transformed model
     */
    private void addRequiredImports(Beast2Model model) {
        // Standard imports
        for (String s : Arrays.asList("beast.base.inference.parameter", "beast.base.inference.distribution", "beast.base.evolution.tree", "beast.base.evolution.speciation", "beast.base.evolution.substitutionmodel", "beast.base.evolution.sitemodel", "beast.base.evolution.alignment", "beast.base.evolution.likelihood")) {
            model.addImport(new ImportStatement(s, true));
        }

        // Add any additional imports found during scanning
        for (String packageName : requiredImports) {
            model.addImport(new ImportStatement(packageName, true));
        }
    }

    /**
     * Add existing imports from original model
     */
    private void addExistingImports(Beast2Model originalModel, Beast2Model transformedModel) {
        Set<String> existingImports = new HashSet<>();

        // First collect what we've already added
        for (Statement stmt : transformedModel.getStatements()) {
            if (stmt instanceof ImportStatement) {
                ImportStatement importStmt = (ImportStatement) stmt;
                existingImports.add(importStmt.getPackageName() + (importStmt.isWildcard() ? ".*" : ""));
            }
        }

        // Add imports from original model if not already added
        for (Statement stmt : originalModel.getStatements()) {
            if (stmt instanceof ImportStatement) {
                ImportStatement importStmt = (ImportStatement) stmt;
                String importPath = importStmt.getPackageName() + (importStmt.isWildcard() ? ".*" : "");

                if (!existingImports.contains(importPath)) {
                    transformedModel.addImport(new ImportStatement(
                            importStmt.getPackageName(), importStmt.isWildcard()));
                    existingImports.add(importPath);
                }
            }
        }
    }

    /**
     * Transform a statement to a list of statements (to handle special patterns)
     */
    private List<Statement> transformStatementToList(Statement stmt) {
        List<Statement> result = new ArrayList<>();

        if (stmt instanceof VariableDeclaration) {
            result.add(transformVariableDeclaration((VariableDeclaration) stmt));
        } else if (stmt instanceof DistributionAssignment) {
            result.addAll(transformDistributionAssignmentToList((DistributionAssignment) stmt));
        } else if (stmt instanceof AnnotatedStatement) {
            result.add(transformAnnotatedStatement((AnnotatedStatement) stmt));
        } else {
            // For other types, add as is
            result.add(stmt);
        }

        return result;
    }

    /**
     * Transform a distribution assignment to a list of statements
     * This handles special patterns like Real ~ LogNormal -> ParametricDistribution + Prior
     */
    private List<Statement> transformDistributionAssignmentToList(DistributionAssignment distAssign) {
        List<Statement> result = new ArrayList<>();

        String className = distAssign.getClassName();
        String variableName = distAssign.getVariableName();
        Expression distribution = distAssign.getDistribution();

        // Special handling for Real ~ Distribution pattern
        if (className.equals("Real") && distribution instanceof FunctionCall) {
            FunctionCall distFunc = (FunctionCall) distribution;
            String distName = distFunc.getClassName();

            // Check if this maps to a ParametricDistribution
            if (PhyloSpecMapper.mapsToParametricDistribution(distName)) {
                logger.info("Transforming " + distName + " to ParametricDistribution + Prior pattern");

                // Generate distribution variable name
                String distVarName = variableName + "Dist";

                // Transform to BEAST2 pattern
                result.addAll(createParametricDistributionAndPrior(
                        distName, distFunc.getArguments(), distVarName, variableName));

                return result;
            }
        } else if (className.equals("Integer") && distribution instanceof FunctionCall) {
            FunctionCall distFunc = (FunctionCall) distribution;
            String distName = distFunc.getClassName();

            // Check for integer-generating distributions
            if (PhyloSpecMapper.isIntegerDistribution(distName)) {
                logger.info("Transforming " + distName + " to ParametricDistribution + Prior pattern for integer");

                // Generate distribution variable name
                String distVarName = variableName + "Dist";

                // Transform to BEAST2 pattern with IntegerParameter
                result.addAll(createParametricDistributionAndPrior(
                        distName, distFunc.getArguments(), distVarName, variableName, "IntegerParameter"));

                return result;
            }
        }

        // If no special pattern applies, do standard transformation
        result.add(transformStandardDistributionAssignment(distAssign));
        return result;
    }

    /**
     * Create a ParametricDistribution and Prior pair from a PhyloSpec distribution
     */
    private List<Statement> createParametricDistributionAndPrior(
            String distName, List<Argument> arguments, String distVarName,
            String paramVarName) {
        return createParametricDistributionAndPrior(distName, arguments, distVarName, paramVarName, "RealParameter");
    }

    /**
     * Create a ParametricDistribution and Prior pair from a PhyloSpec distribution
     * with specified parameter type
     */
    private List<Statement> createParametricDistributionAndPrior(
            String distName, List<Argument> arguments, String distVarName,
            String paramVarName, String parameterType) {
        List<Statement> result = new ArrayList<>();

        // 1. Map the distribution name
        String mappedDistName = PhyloSpecMapper.mapDistribution(distName);

        // Extract simple class name
        int lastDot = mappedDistName.lastIndexOf('.');
        String shortDistName = lastDot > 0 ? mappedDistName.substring(lastDot + 1) : mappedDistName;

        // 2. Transform arguments
        List<Argument> mappedArgs = new ArrayList<>();
        for (Argument arg : arguments) {
            String paramName = PhyloSpecMapper.mapParameterName(distName, arg.getName());
            Expression paramValue = arg.getValue();

            // Transform nested function calls
            if (paramValue instanceof FunctionCall) {
                paramValue = transformFunctionCall((FunctionCall) paramValue);
            }

            mappedArgs.add(new Argument(paramName, paramValue));
        }

        // 3. Create ParametricDistribution declaration
        FunctionCall distFunc = new FunctionCall(shortDistName, mappedArgs);
        VariableDeclaration distDecl = new VariableDeclaration(
                "ParametricDistribution", distVarName, distFunc);

        // 4. Create Prior assignment
        List<Argument> priorArgs = new ArrayList<>();
        priorArgs.add(new Argument("distr", new Identifier(distVarName)));
        FunctionCall priorFunc = new FunctionCall("Prior", priorArgs);

        DistributionAssignment priorAssign = new DistributionAssignment(
                parameterType, paramVarName, priorFunc);

        // Add both statements
        result.add(distDecl);
        result.add(priorAssign);

        return result;
    }

    /**
     * Transform a standard distribution assignment (without special patterns)
     */
    private DistributionAssignment transformStandardDistributionAssignment(DistributionAssignment distAssign) {
        String className = distAssign.getClassName();
        String variableName = distAssign.getVariableName();
        Expression distribution = distAssign.getDistribution();

        // Map PhyloSpec type to BEAST2 type
        if (PhyloSpecMapper.isPhyloSpecType(className)) {
            className = PhyloSpecMapper.mapType(className);

            // Extract simple class name from fully qualified name
            int lastDot = className.lastIndexOf('.');
            if (lastDot > 0) {
                className = className.substring(lastDot + 1);
            }
        }

        // Transform distribution expression if needed
        if (distribution instanceof FunctionCall) {
            distribution = transformFunctionCall((FunctionCall) distribution);
        }

        // Create new distribution assignment
        return new DistributionAssignment(className, variableName, distribution);
    }

    /**
     * Transform a variable declaration
     */
    private VariableDeclaration transformVariableDeclaration(VariableDeclaration decl) {
        String className = decl.getClassName();
        String variableName = decl.getVariableName();
        Expression value = decl.getValue();

        // Map PhyloSpec type to BEAST2 type
        if (PhyloSpecMapper.isPhyloSpecType(className)) {
            className = PhyloSpecMapper.mapType(className);

            // Extract simple class name from fully qualified name
            int lastDot = className.lastIndexOf('.');
            if (lastDot > 0) {
                className = className.substring(lastDot + 1);
            }
        }

        // Transform the value expression if needed
        if (value instanceof FunctionCall) {
            value = transformFunctionCall((FunctionCall) value);
        }

        // Create a new declaration
        return new VariableDeclaration(className, variableName, value);
    }

    /**
     * Transform an annotated statement
     */
    private AnnotatedStatement transformAnnotatedStatement(AnnotatedStatement annotStmt) {
        // Transform the underlying statement
        List<Statement> transformedStmts = transformStatementToList(annotStmt.getStatement());

        if (transformedStmts.size() != 1) {
            // Can't annotate multiple statements - just annotate the first one
            logger.warning("Annotation applied to a pattern that expands to multiple statements!");
            return new AnnotatedStatement(annotStmt.getAnnotation(), transformedStmts.get(0));
        }

        // Create new annotated statement with the same annotation
        return new AnnotatedStatement(annotStmt.getAnnotation(), transformedStmts.get(0));
    }

    /**
     * Transform a function call
     */
    private FunctionCall transformFunctionCall(FunctionCall funcCall) {
        String className = funcCall.getClassName();
        List<Argument> arguments = funcCall.getArguments();

        // Map PhyloSpec distribution to BEAST2 class
        if (PhyloSpecMapper.isPhyloSpecDistribution(className)) {
            String originalClass = className; // Keep for parameter mapping
            className = PhyloSpecMapper.mapDistribution(className);

            // Extract simple class name from fully qualified name
            int lastDot = className.lastIndexOf('.');
            if (lastDot > 0) {
                className = className.substring(lastDot + 1);
            }

            // Transform arguments
            List<Argument> transformedArgs = new ArrayList<>();
            for (Argument arg : arguments) {
                String paramName = arg.getName();
                Expression paramValue = arg.getValue();

                // Map parameter name if needed
                paramName = PhyloSpecMapper.mapParameterName(originalClass, paramName);

                // Transform parameter value if it's a function call
                if (paramValue instanceof FunctionCall) {
                    paramValue = transformFunctionCall((FunctionCall) paramValue);
                }

                transformedArgs.add(new Argument(paramName, paramValue));
            }

            return new FunctionCall(className, transformedArgs);
        }

        // If not a PhyloSpec distribution, just transform the arguments
        List<Argument> transformedArgs = new ArrayList<>();
        for (Argument arg : arguments) {
            String paramName = arg.getName();
            Expression paramValue = arg.getValue();

            // Transform parameter value if it's a function call
            if (paramValue instanceof FunctionCall) {
                paramValue = transformFunctionCall((FunctionCall) paramValue);
            }

            transformedArgs.add(new Argument(paramName, paramValue));
        }

        return new FunctionCall(className, transformedArgs);
    }
}
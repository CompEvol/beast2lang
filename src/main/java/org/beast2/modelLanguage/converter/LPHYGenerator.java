package org.beast2.modelLanguage.converter;

import org.beast2.modelLanguage.model.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This updated implementation maps @data declarations to @observed distribution assignment variables
 * by finding the relationship between them and renaming the data variables
 */
public class LPHYGenerator {
    private final LPHYMappingProvider mappingProvider;
    private Map<String, String> dataToObservedMap;

    public LPHYGenerator(LPHYMappingProvider mappingProvider) {
        this.mappingProvider = mappingProvider;
        this.dataToObservedMap = new HashMap<>();
    }

    /**
     * Generate LPHY code from a Beast2Model.
     *
     * @param model The Beast2Model to convert
     * @return LPHY code as a string
     */
    public String generate(Beast2Model model) {
        // First, identify observed distributions and their data sources
        findDataToObservedMappings(model);

        StringBuilder sb = new StringBuilder();

        // Separate statements into data and model blocks
        List<Statement> dataStatements = new ArrayList<>();
        List<Statement> modelStatements = new ArrayList<>();

        for (Statement stmt : model.getStatements()) {
            if (isDataStatement(stmt)) {
                dataStatements.add(stmt);
            } else {
                modelStatements.add(stmt);
            }
        }

        // Generate data block
        if (!dataStatements.isEmpty()) {
            sb.append("data {\n");
            for (Statement stmt : dataStatements) {
                sb.append("  ").append(generateStatement(stmt, true)).append("\n");
            }
            sb.append("}\n");
        }

        // Generate model block
        if (!modelStatements.isEmpty()) {
            if (!dataStatements.isEmpty()) {
                sb.append("\n");
            }
            sb.append("model {\n");
            for (Statement stmt : modelStatements) {
                sb.append("  ").append(generateStatement(stmt, false)).append("\n");
            }
            sb.append("}\n");
        }

        return sb.toString();
    }

    /**
     * Find all mappings between @data declarations and @observed distributions
     */
    private void findDataToObservedMappings(Beast2Model model) {
        dataToObservedMap.clear();

        // Find all @observed statements with data parameter
        for (Statement stmt : model.getStatements()) {
            if (stmt instanceof AnnotatedStatement) {
                AnnotatedStatement annotatedStmt = (AnnotatedStatement) stmt;
                Annotation annotation = annotatedStmt.getAnnotation();

                if (annotation.isObservedAnnotation()) {
                    // Get the data source from the annotation
                    String dataSource = annotation.getParameterAsString("data");

                    if (dataSource != null && annotatedStmt.getStatement() instanceof DistributionAssignment) {
                        DistributionAssignment dist = (DistributionAssignment) annotatedStmt.getStatement();
                        String observedVar = dist.getVariableName();

                        // Map the data source to the observed variable
                        dataToObservedMap.put(dataSource, observedVar);
                    }
                }
            }
        }
    }

    /**
     * Determine if a statement belongs in the data block.
     * Only @data VariableDeclarations and nexus statements go in the data block.
     * All distribution assignments (including observed ones) go in the model block.
     */
    private boolean isDataStatement(Statement stmt) {
        // If it's an annotated statement
        if (stmt instanceof AnnotatedStatement) {
            AnnotatedStatement annotatedStmt = (AnnotatedStatement) stmt;
            Annotation annotation = annotatedStmt.getAnnotation();

            // Only explicit @data annotations go in the data block
            if (annotation.isDataAnnotation()) {
                // And only if they're variable declarations, not distributions
                return annotatedStmt.getStatement() instanceof VariableDeclaration;
            }

            // All distributions (including @observed) go in the model block
            if (annotatedStmt.getStatement() instanceof DistributionAssignment) {
                return false;
            }

            // For other annotations, check the underlying statement
            return isDataStatement(annotatedStmt.getStatement());
        }

        // Check for nexus function calls in variable declarations - these are data statements
        if (stmt instanceof VariableDeclaration) {
            VariableDeclaration decl = (VariableDeclaration) stmt;
            return decl.getValue() instanceof NexusFunction;
        }

        // All distribution assignments go in the model block
        if (stmt instanceof DistributionAssignment) {
            return false;
        }

        // By default, put everything else in the model block
        return false;
    }

    /**
     * Generate LPHY code for a statement.
     */
    private String generateStatement(Statement stmt, boolean isDataBlock) {
        if (stmt instanceof VariableDeclaration) {
            return generateVariableDeclaration((VariableDeclaration) stmt);
        } else if (stmt instanceof DistributionAssignment) {
            return generateDistributionAssignment((DistributionAssignment) stmt);
        } else if (stmt instanceof AnnotatedStatement) {
            AnnotatedStatement annotatedStmt = (AnnotatedStatement) stmt;
            Statement innerStmt = annotatedStmt.getStatement();
            Annotation annotation = annotatedStmt.getAnnotation();

            // Handle @observed annotation specially
            if (annotation.isObservedAnnotation() && innerStmt instanceof DistributionAssignment) {
                return generateObservedDistribution((DistributionAssignment) innerStmt, annotation);
            }

            // Handle @data annotation for variable declarations
            if (annotation.isDataAnnotation() && innerStmt instanceof VariableDeclaration) {
                return generateDataDeclaration((VariableDeclaration) innerStmt);
            }

            // For other annotations, just generate the inner statement
            return generateStatement(innerStmt, isDataBlock);
        }

        return "// Unsupported statement type: " + stmt.getClass().getSimpleName();
    }

    /**
     * Generate LPHY code for a @data variable declaration, renaming if needed.
     */
    private String generateDataDeclaration(VariableDeclaration decl) {
        StringBuilder sb = new StringBuilder();

        String originalName = decl.getVariableName();
        String newName = originalName;

        // If this data variable is used in an @observed statement, rename it to match
        if (dataToObservedMap.containsKey(originalName)) {
            newName = dataToObservedMap.get(originalName);
        }

        // In LPHY, we don't include the type
        sb.append(newName).append(" = ");

        // Handle special case for nexus function
        if (decl.getValue() instanceof NexusFunction) {
            sb.append(generateNexusFunction((NexusFunction) decl.getValue()));
        } else {
            sb.append(generateExpression(decl.getValue()));
        }

        sb.append(";");
        return sb.toString();
    }

    /**
     * Generate LPHY code for a variable declaration.
     */
    private String generateVariableDeclaration(VariableDeclaration decl) {
        StringBuilder sb = new StringBuilder();

        String originalName = decl.getVariableName();
        String newName = originalName;

        // If this is a data block variable and it's mapped to an observed variable, rename it
        if (dataToObservedMap.containsKey(originalName)) {
            newName = dataToObservedMap.get(originalName);
        }

        // In LPHY, we don't include the type
        sb.append(newName).append(" = ");

        // Handle special case for nexus function
        if (decl.getValue() instanceof NexusFunction) {
            sb.append(generateNexusFunction((NexusFunction) decl.getValue()));
        } else {
            sb.append(generateExpression(decl.getValue()));
        }

        sb.append(";");
        return sb.toString();
    }

    /**
     * Generate LPHY code for a distribution assignment.
     */
    private String generateDistributionAssignment(DistributionAssignment dist) {
        StringBuilder sb = new StringBuilder();

        // In LPHY, we don't include the type
        sb.append(dist.getVariableName()).append(" ~ ");
        sb.append(generateExpression(dist.getDistribution()));
        sb.append(";");

        return sb.toString();
    }

    /**
     * Generate LPHY code for a distribution with @observed annotation.
     */
    private String generateObservedDistribution(DistributionAssignment dist, Annotation annotation) {
        StringBuilder sb = new StringBuilder();

        sb.append(dist.getVariableName()).append(" ~ ");

        if (dist.getDistribution() instanceof FunctionCall) {
            FunctionCall func = (FunctionCall) dist.getDistribution();
            if (func.getClassName().contains("TreeLikelihood")) {
                // Map TreeLikelihood to PhyloCTMC
                String mappedName = "phyloCTMC";
                sb.append(mappedName).append("(");

                // Process arguments with mapping
                boolean first = true;
                for (Argument arg : func.getArguments()) {
                    if (!first) {
                        sb.append(", ");
                    }

                    // Map parameter name
                    String origParamName = arg.getName();
                    String mappedParamName = mappingProvider.mapParameterName("PhyloCTMC", origParamName);
                    sb.append(mappedParamName).append("=").append(generateExpression(arg.getValue()));

                    first = false;
                }

                // Add L parameter for sequence length
                if (!first) {
                    sb.append(", ");
                }

                // Get data source from annotation for length
                String dataSource = annotation.getParameterAsString("data");
                if (dataSource != null) {
                    // Use the renamed variable if it exists
                    String nameToUse = dist.getVariableName(); // Default to the current distribution variable
                    sb.append("L=").append(nameToUse).append(".nchar()");
                } else {
                    sb.append("L=data.nchar() /* data source not found */");
                }

                sb.append(")");
            } else {
                sb.append(generateExpression(dist.getDistribution()));
            }
        } else {
            sb.append(generateExpression(dist.getDistribution()));
        }

        sb.append(";");
        return sb.toString();
    }

    // [Rest of the methods remain the same]

    /**
     * Generate LPHY code for a nexus function.
     */
    private String generateNexusFunction(NexusFunction nexusFunc) {
        StringBuilder sb = new StringBuilder();

        // Map nexus() to readNexus()
        sb.append("readNexus(");

        // Process arguments
        boolean first = true;
        for (Argument arg : nexusFunc.getArguments()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(arg.getName()).append("=").append(generateExpression(arg.getValue()));
            first = false;
        }

        sb.append(")");
        return sb.toString();
    }

    /**
     * Generate LPHY code for an expression.
     */
    private String generateExpression(Expression expr) {
        if (expr instanceof FunctionCall) {
            return generateFunctionCall((FunctionCall) expr);
        } else if (expr instanceof Identifier) {
            return ((Identifier) expr).getName();
        } else if (expr instanceof Literal) {
            return expr.getId();
        } else if (expr instanceof ArrayLiteral) {
            return generateArrayLiteral((ArrayLiteral) expr);
        } else if (expr instanceof NexusFunction) {
            return generateNexusFunction((NexusFunction) expr);
        }

        return "/* Unsupported expression type: " + expr.getClass().getSimpleName() + " */";
    }

    /**
     * Generate LPHY code for a function call.
     */
    private String generateFunctionCall(FunctionCall func) {
        StringBuilder sb = new StringBuilder();

        // Map function name from B2L to LPHY
        String originalFunctionName = func.getClassName();
        String mappedFunctionName = mappingProvider.mapFunctionName(originalFunctionName);
        sb.append(mappedFunctionName).append("(");

        // Process arguments
        boolean first = true;
        for (Argument arg : func.getArguments()) {
            if (!first) {
                sb.append(", ");
            }

            // Map parameter name
            String mappedParamName = mappingProvider.mapParameterName(mappedFunctionName, arg.getName());
            sb.append(mappedParamName).append("=").append(generateExpression(arg.getValue()));

            first = false;
        }

        sb.append(")");
        return sb.toString();
    }

    /**
     * Generate LPHY code for an array literal.
     */
    private String generateArrayLiteral(ArrayLiteral array) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        boolean first = true;
        for (Expression element : array.getElements()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(generateExpression(element));
            first = false;
        }

        sb.append("]");
        return sb.toString();
    }
}
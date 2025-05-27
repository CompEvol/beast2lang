package org.beast2.modelLanguage.converter;

import org.beast2.modelLanguage.model.*;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Converts a Beast2Model to a Beast2Lang script as a string.
 */
public class Beast2ModelWriter {

    private static final String INDENT = "    ";
    private final StringBuilder sb = new StringBuilder();

    /**
     * Write a Beast2Model as a Beast2Lang script
     *
     * @param model The Beast2Model to write
     * @return A string containing the Beast2Lang script
     */
    public String writeModel(Beast2Model model) {

        // Add requires statements first
        for (RequiresStatement req : model.getRequires()) {
            sb.append(req.toString()).append("\n");
        }

        // Only add imports if no requires statements
        if (model.getRequires().isEmpty()) {
            for (ImportStatement imp : model.getImports()) {
                writeImport(imp);
            }
        }

        // Add blank line if we have requires or imports
        if (!model.getRequires().isEmpty() || !model.getImports().isEmpty()) {
            sb.append("\n");
        }

        // Add statements
        for (Statement stmt : model.getStatements()) {
            writeStatement(stmt);
            sb.append("\n");
        }

        return sb.toString();
    }
    
    /**
     * Write an import statement
     */
    private void writeImport(ImportStatement importStmt) {
        sb.append("import ").append(importStmt.getPackageName());
        if (importStmt.isWildcard()) {
            sb.append(".*");
        }
        sb.append(";");
    }

    /**
     * Write a statement
     */
    private void writeStatement(Statement stmt) {
        if (stmt instanceof AnnotatedStatement) {
            writeAnnotatedStatement((AnnotatedStatement) stmt);
        } else if (stmt instanceof VariableDeclaration) {
            writeVariableDeclaration((VariableDeclaration) stmt);
        } else if (stmt instanceof DistributionAssignment) {
            writeDistributionAssignment((DistributionAssignment) stmt);
        } else {
            throw new IllegalArgumentException("Unknown statement type: " + stmt.getClass().getName());
        }
    }

    /**
     * Write an annotated statement
     */
    private void writeAnnotatedStatement(AnnotatedStatement stmt) {
        writeAnnotation(stmt.getAnnotation());
        sb.append("\n");
        writeStatement(stmt.getStatement());
    }

    /**
     * Write an annotation
     */
    private void writeAnnotation(Annotation annotation) {
        sb.append("@").append(annotation.getName());

        // Add parameters if present
        if (!annotation.getParameters().isEmpty()) {
            sb.append("(");
            boolean first = true;

            for (String paramName : annotation.getParameters().keySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;

                sb.append(paramName).append("=");
                Object value = annotation.getParameters().get(paramName);

                // Write parameter value
                if (value instanceof String) {
                    sb.append(value);
                } else {
                    sb.append(value);
                }
            }

            sb.append(")");
        }
    }

    /**
     * Write a variable declaration
     */
    private void writeVariableDeclaration(VariableDeclaration decl) {
        sb.append(decl.getClassName()).append(" ")
                .append(decl.getVariableName())
                .append(" = ");

        writeExpression(decl.getValue());

        sb.append(";");
    }

    /**
     * Write a distribution assignment
     */
    private void writeDistributionAssignment(DistributionAssignment asgn) {
        sb.append(asgn.getClassName()).append(" ")
                .append(asgn.getVariableName())
                .append(" ~ ");

        writeExpression(asgn.getDistribution());

        sb.append(";");
    }

    /**
     * Write an expression
     */
    private void writeExpression(Expression expr) {
        if (expr instanceof FunctionCall) {
            writeFunctionCall((FunctionCall) expr);
        } else if (expr instanceof NexusFunction) {
            writeNexusFunction((NexusFunction) expr);
        } else if (expr instanceof Identifier) {
            writeIdentifier((Identifier) expr);
        } else if (expr instanceof Literal) {
            writeLiteral((Literal) expr);
        } else if (expr instanceof ArrayLiteral) {
            writeArrayLiteral((ArrayLiteral) expr);
        } else {
            throw new IllegalArgumentException("Unknown expression type: " + expr.getClass().getName());
        }
    }

    /**
     * Write a function call
     */
    private void writeFunctionCall(FunctionCall call) {
        sb.append(call.getClassName()).append("(");

        List<Argument> arguments = call.getArguments();
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            Argument arg = arguments.get(i);
            sb.append(arg.getName()).append("=");
            writeExpression(arg.getValue());
        }

        sb.append(")");
    }

    /**
     * Write a nexus function
     */
    private void writeNexusFunction(NexusFunction func) {
        sb.append("nexus(");

        List<Argument> arguments = func.getArguments();
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            Argument arg = arguments.get(i);
            sb.append(arg.getName()).append("=");
            writeExpression(arg.getValue());
        }

        sb.append(")");
    }

    /**
     * Write an identifier
     */
    private void writeIdentifier(Identifier id) {
        sb.append(id.getName());
    }

    /**
     * Write a literal
     */
    private void writeLiteral(Literal literal) {
        Object value = literal.getValue();

        switch (literal.getType()) {
            case STRING:
                sb.append("\"").append(value).append("\"");
                break;
            case INTEGER:
            case FLOAT:
            case BOOLEAN:
                sb.append(value);
                break;
            default:
                throw new IllegalArgumentException("Unknown literal type: " + literal.getType());
        }
    }

    /**
     * Write an array literal
     */
    private void writeArrayLiteral(ArrayLiteral array) {
        sb.append("[");

        List<Expression> elements = array.getElements();
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            writeExpression(elements.get(i));
        }

        sb.append("]");
    }
}
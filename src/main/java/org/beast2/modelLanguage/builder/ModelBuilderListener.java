package org.beast2.modelLanguage.builder;

import org.beast2.modelLanguage.model.*;
import org.beast2.modelLanguage.parser.Beast2ModelLanguageBaseListener;
import org.beast2.modelLanguage.parser.Beast2ModelLanguageParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * ANTLR listener for building a Beast2Model from a parse tree.
 * This implementation supports the @data and @observed annotations,
 * the built-in nexus() function, and the new "requires" statement.
 */
public class ModelBuilderListener extends Beast2ModelLanguageBaseListener {

    private static final Logger logger = Logger.getLogger(ModelBuilderListener.class.getName());

    private final Beast2Model model = new Beast2Model();
    private List<Annotation> currentAnnotations = new ArrayList<>();

    /**
     * Get the constructed model
     */
    public Beast2Model getModel() {
        return model;
    }

    /**
     * Handle import statements
     */
    @Override
    public void exitImportStatement(Beast2ModelLanguageParser.ImportStatementContext ctx) {
        String packageName = ctx.importName().qualifiedName().getText();
        boolean isWildcard = ctx.importName().STAR() != null;

        ImportStatement importStmt = new ImportStatement(packageName, isWildcard);
        model.addImport(importStmt);

        logger.fine("Added import: " + importStmt);
    }

    /**
     * Handle requires statements
     */
    @Override
    public void exitRequiresStatement(Beast2ModelLanguageParser.RequiresStatementContext ctx) {
        String packageName = ctx.pluginName().getText();

        RequiresStatement requiresStmt = new RequiresStatement(packageName);
        model.addRequires(requiresStmt);

        logger.info("Added requires statement for package: " + packageName);
    }

    /**
     * Handle annotations
     */
    @Override
    public void enterAnnotation(Beast2ModelLanguageParser.AnnotationContext ctx) {
        String type = ctx.annotationName().getText();
        Map<String, Expression> parameters = new LinkedHashMap<>();
        if (ctx.annotationBody() != null) {
            for (var p : ctx.annotationBody().annotationParameter()) {
                String key = p.identifier().getText();
                // p.expression is always present per grammar
                Expression val = createExpression(p.expression());
                parameters.put(key, val);
            }
        }
        currentAnnotations.add(new Annotation(type, parameters));
    }


    /**
     * Handle statements with annotations
     */
    @Override
    public void exitStatement(Beast2ModelLanguageParser.StatementContext ctx) {
        Statement stmt = ctx.variableDeclaration()   != null
                ? createVariableDeclaration(ctx.variableDeclaration())
                : createDistributionAssignment(ctx.distributionAssignment());

        // wrap with all annotations seen since last statement
        if (!currentAnnotations.isEmpty()) {
            stmt = new AnnotatedStatement(new ArrayList<>(currentAnnotations), stmt);
            currentAnnotations.clear();
        }

        model.addStatement(stmt);
    }

    /**
     * Create a VariableDeclaration from its context
     */
    private VariableDeclaration createVariableDeclaration(Beast2ModelLanguageParser.VariableDeclarationContext ctx) {
        String className = ctx.className().getText();
        String variableName = ctx.identifier().getText();
        Expression expression = createExpression(ctx.expression());

        return new VariableDeclaration(className, variableName, expression);
    }

    /**
     * Create a DistributionAssignment from its context
     */
    private DistributionAssignment createDistributionAssignment(Beast2ModelLanguageParser.DistributionAssignmentContext ctx) {
        String className = ctx.className().getText();
        String variableName = ctx.identifier().getText();
        Expression distribution = createExpression(ctx.expression());

        return new DistributionAssignment(className, variableName, distribution);
    }

    /**
     * Create an Expression from its context
     */
    private Expression createExpression(Beast2ModelLanguageParser.ExpressionContext ctx) {
        if (ctx instanceof Beast2ModelLanguageParser.FunctionCallExprContext) {
            Beast2ModelLanguageParser.FunctionCallContext funcCtx =
                    ((Beast2ModelLanguageParser.FunctionCallExprContext) ctx).functionCall();
            return createFunctionCall(funcCtx);
        } else if (ctx instanceof Beast2ModelLanguageParser.NexusFunctionExprContext) {
            Beast2ModelLanguageParser.NexusFunctionContext nexusCtx =
                    ((Beast2ModelLanguageParser.NexusFunctionExprContext) ctx).nexusFunction();
            return createNexusFunction(nexusCtx);
        } else if (ctx instanceof Beast2ModelLanguageParser.IdentifierExprContext) {
            String name = ((Beast2ModelLanguageParser.IdentifierExprContext) ctx).identifier().getText();
            return new Identifier(name);
        } else if (ctx instanceof Beast2ModelLanguageParser.LiteralExprContext) {
            Object value = getLiteralValue(((Beast2ModelLanguageParser.LiteralExprContext) ctx).literal());
            return createLiteral(value);
        } else if (ctx instanceof Beast2ModelLanguageParser.ArrayLiteralExprContext) {
            Beast2ModelLanguageParser.ArrayLiteralContext arrayCtx =
                    ((Beast2ModelLanguageParser.ArrayLiteralExprContext) ctx).arrayLiteral();
            return createArrayLiteral(arrayCtx);
        } else {
            throw new IllegalStateException("Unknown expression type: " + ctx.getClass().getName());
        }
    }

    /**
     * Create an Expression from an argument value context
     */
    private Expression createExpressionFromArgumentValue(Beast2ModelLanguageParser.ArgumentValueContext ctx) {
        if (ctx.expression() != null) {
            return createExpression(ctx.expression());
        } else if (ctx.literal() != null) {
            Object value = getLiteralValue(ctx.literal());
            return createLiteral(value);
        } else if (ctx.arrayLiteral() != null) {
            return createArrayLiteral(ctx.arrayLiteral());
        } else {
            throw new IllegalStateException("Unknown argument value type");
        }
    }

    /**
     * Create an ArrayLiteral from its context
     */
    private ArrayLiteral createArrayLiteral(Beast2ModelLanguageParser.ArrayLiteralContext ctx) {
        List<Expression> elements = new ArrayList<>();

        // Process array elements if present
        if (ctx.arrayElement() != null) {
            for (Beast2ModelLanguageParser.ArrayElementContext elemCtx : ctx.arrayElement()) {
                if (elemCtx.literal() != null) {
                    Object value = getLiteralValue(elemCtx.literal());
                    elements.add(createLiteral(value));
                } else if (elemCtx.identifier() != null) {
                    String name = elemCtx.identifier().getText();
                    elements.add(new Identifier(name));
                } else if (elemCtx.functionCall() != null) {
                    elements.add(createFunctionCall(elemCtx.functionCall()));
                }
            }
        }

        return new ArrayLiteral(elements);
    }

    /**
     * Create a FunctionCall from its context
     */
    private FunctionCall createFunctionCall(Beast2ModelLanguageParser.FunctionCallContext ctx) {
        String className = ctx.className().getText();
        List<Argument> arguments = new ArrayList<>();

        // Add arguments if present
        if (ctx.argumentList() != null) {
            for (Beast2ModelLanguageParser.ArgumentContext argCtx : ctx.argumentList().argument()) {
                String name = argCtx.argumentName().getText();
                Expression value = createExpressionFromArgumentValue(argCtx.argumentValue());
                arguments.add(new Argument(name, value));
            }
        }

        return new FunctionCall(className, arguments);
    }

    /**
     * Create a NexusFunction from its context
     */
    private NexusFunction createNexusFunction(Beast2ModelLanguageParser.NexusFunctionContext ctx) {
        List<Argument> arguments = new ArrayList<>();

        // Add arguments if present
        if (ctx.argumentList() != null) {
            for (Beast2ModelLanguageParser.ArgumentContext argCtx : ctx.argumentList().argument()) {
                String name = argCtx.argumentName().getText();
                Expression value = createExpressionFromArgumentValue(argCtx.argumentValue());
                arguments.add(new Argument(name, value));
            }
        }

        logger.info("Created nexus function with " + arguments.size() + " arguments");
        return new NexusFunction(arguments);
    }

    /**
     * Create a Literal with the appropriate LiteralType
     */
    private Literal createLiteral(Object value) {
        if (value instanceof Double) {
            return new Literal(value, Literal.LiteralType.FLOAT);
        } else if (value instanceof Integer) {
            return new Literal(value, Literal.LiteralType.INTEGER);
        } else if (value instanceof Boolean) {
            return new Literal(value, Literal.LiteralType.BOOLEAN);
        } else {
            // Default to string for other types
            return new Literal(value, Literal.LiteralType.STRING);
        }
    }

    /**
     * Get a value from a literal context
     */
    private Object getLiteralValue(Beast2ModelLanguageParser.LiteralContext ctx) {
        if (ctx.FLOAT_LITERAL() != null) {
            return Double.parseDouble(ctx.FLOAT_LITERAL().getText());
        } else if (ctx.INTEGER_LITERAL() != null) {
            return Integer.parseInt(ctx.INTEGER_LITERAL().getText());
        } else if (ctx.STRING_LITERAL() != null) {
            // Remove surrounding quotes
            String text = ctx.STRING_LITERAL().getText();
            return text.substring(1, text.length() - 1);
        } else if (ctx.BOOLEAN_LITERAL() != null) {
            return Boolean.parseBoolean(ctx.BOOLEAN_LITERAL().getText());
        } else {
            throw new IllegalStateException("Unknown literal type");
        }
    }
}
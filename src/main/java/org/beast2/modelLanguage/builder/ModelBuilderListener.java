package org.beast2.modelLanguage.builder;

import org.beast2.modelLanguage.model.*;
import org.beast2.modelLanguage.parser.Beast2ModelLanguageBaseListener;
import org.beast2.modelLanguage.parser.Beast2ModelLanguageParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.logging.Logger;

/**
 * ANTLR listener that builds a Beast2Model as it traverses the parse tree
 */
public class ModelBuilderListener extends Beast2ModelLanguageBaseListener {
    private static final Logger logger = Logger.getLogger(ModelBuilderListener.class.getName());
    
    private Beast2Model model = new Beast2Model();
    private Stack<Object> stack = new Stack<>();
    
    // Current annotation being built
    private Annotation currentAnnotation = null;
    
    /**
     * Get the constructed model
     */
    public Beast2Model getModel() {
        return model;
    }


    @Override
    public void enterProgram(Beast2ModelLanguageParser.ProgramContext ctx) {
        System.out.println("DEBUG - Entering program");
    }

    @Override
    public void enterStatement(Beast2ModelLanguageParser.StatementContext ctx) {
        System.out.println("DEBUG - Entering statement");
    }

    @Override
    public void enterAnnotation(Beast2ModelLanguageParser.AnnotationContext ctx) {
        System.out.println("DEBUG - Entering annotation: @" + ctx.IDENTIFIER().getText());
    }

    @Override
    public void exitImportStatement(Beast2ModelLanguageParser.ImportStatementContext ctx) {
        // Check if it's a wildcard import
        boolean isWildcard = ctx.DOT() != null && ctx.STAR() != null;
        
        // Get the qualified name
        String packageName = ctx.qualifiedName().getText();
        
        logger.fine("Processing import: " + packageName + (isWildcard ? ".*" : ""));
        
        // Create and add the import statement
        ImportStatement importStatement = new ImportStatement(packageName, isWildcard);
        model.addImport(importStatement);
    }
    
    @Override
    public void exitAnnotation(Beast2ModelLanguageParser.AnnotationContext ctx) {
        System.out.println("DEBUG - Parsing annotation: @" + ctx.IDENTIFIER().getText());

        String name = ctx.IDENTIFIER().getText();
        Map<String, Object> parameters = new HashMap<>();

        // If there's an annotation body, process its parameters
        if (ctx.annotationBody() != null) {
            System.out.println("DEBUG - Annotation has parameters");
            Beast2ModelLanguageParser.AnnotationBodyContext bodyCtx = ctx.annotationBody();
            for (Beast2ModelLanguageParser.AnnotationParameterContext paramCtx : bodyCtx.annotationParameter()) {
                String paramName = paramCtx.identifier().getText();
                Object paramValue = getLiteralValue(paramCtx.literal());
                parameters.put(paramName, paramValue);
                System.out.println("DEBUG - Parameter: " + paramName + " = " + paramValue);
            }
        }

        // Create the annotation
        currentAnnotation = new Annotation(name, parameters);
        System.out.println("DEBUG - Created annotation: " + currentAnnotation);
    }
    @Override
    public void exitVariableDeclaration(Beast2ModelLanguageParser.VariableDeclarationContext ctx) {
        // Get the expression from the stack
        Expression expression = (Expression) stack.pop();
        
        // Get class name and variable name
        String className = ctx.className().getText();
        String variableName = ctx.identifier().getText();
        
        logger.fine("Creating variable declaration: " + className + " " + variableName);
        
        // Create variable declaration
        VariableDeclaration varDecl = new VariableDeclaration(className, variableName, expression);
        
        // If there's an annotation, wrap it in an AnnotatedStatement
        if (currentAnnotation != null) {
            AnnotatedStatement annotatedStmt = new AnnotatedStatement(currentAnnotation, varDecl);
            model.addStatement(annotatedStmt);
            currentAnnotation = null;
        } else {
            model.addStatement(varDecl);
        }
    }
    
    @Override
    public void exitDistributionAssignment(Beast2ModelLanguageParser.DistributionAssignmentContext ctx) {
        // Get the expression from the stack
        Expression expression = (Expression) stack.pop();

        // Get class name and variable name
        String className = ctx.className().getText();
        String variableName = ctx.identifier().getText();

        System.out.println("DEBUG - Creating distribution assignment: " + className + " " + variableName);

        // Create distribution assignment
        DistributionAssignment distAssign = new DistributionAssignment(className, variableName, expression);

        // If there's an annotation, wrap it in an AnnotatedStatement
        if (currentAnnotation != null) {
            System.out.println("DEBUG - Attaching annotation to distribution assignment: " + currentAnnotation.getName());
            AnnotatedStatement annotatedStmt = new AnnotatedStatement(currentAnnotation, distAssign);
            model.addStatement(annotatedStmt);
            currentAnnotation = null;
        } else {
            System.out.println("DEBUG - No annotation for distribution assignment");
            model.addStatement(distAssign);
        }
    }
    
    @Override
    public void exitFunctionCallExpr(Beast2ModelLanguageParser.FunctionCallExprContext ctx) {
        // Function call expression is already on the stack from exitFunctionCall
    }
    
    @Override
    public void exitIdentifierExpr(Beast2ModelLanguageParser.IdentifierExprContext ctx) {
        // Create identifier expression and push to stack
        String name = ctx.identifier().getText();
        Identifier identifier = new Identifier(name);
        stack.push(identifier);
    }
    
    @Override
    public void exitLiteralExpr(Beast2ModelLanguageParser.LiteralExprContext ctx) {
        // Literal is already on the stack from exitLiteral
    }
    
    @Override
    public void exitFunctionCall(Beast2ModelLanguageParser.FunctionCallContext ctx) {
        List<Argument> arguments = new ArrayList<>();
        
        // If there are arguments, get them from the stack
        if (ctx.argumentList() != null) {
            int argCount = ctx.argumentList().argument().size();
            for (int i = 0; i < argCount; i++) {
                arguments.add(0, (Argument) stack.pop()); // Reverse order to maintain correct order
            }
        }
        
        // Get class name
        String className = ctx.className().getText();
        
        // Create function call and push to stack
        FunctionCall functionCall = new FunctionCall(className, arguments);
        stack.push(functionCall);
    }
    
    @Override
    public void exitArgument(Beast2ModelLanguageParser.ArgumentContext ctx) {
        // Get the argument value from the stack
        Object value = stack.pop();
        Expression expr = null;
        
        // Convert the value to an Expression if it's not already
        if (value instanceof Expression) {
            expr = (Expression) value;
        } else if (value instanceof Literal) {
            expr = (Expression) value;
        } else {
            throw new IllegalStateException("Unexpected argument value type: " + value.getClass().getName());
        }
        
        // Get argument name
        String name = ctx.identifier().getText();
        
        // Create argument and push to stack
        Argument argument = new Argument(name, expr);
        stack.push(argument);
    }
    
    @Override
    public void exitLiteral(Beast2ModelLanguageParser.LiteralContext ctx) {
        Literal.LiteralType type;
        Object value = getLiteralValue(ctx);
        
        if (ctx.INTEGER_LITERAL() != null) {
            type = Literal.LiteralType.INTEGER;
        } else if (ctx.FLOAT_LITERAL() != null) {
            type = Literal.LiteralType.FLOAT;
        } else if (ctx.STRING_LITERAL() != null) {
            type = Literal.LiteralType.STRING;
        } else if (ctx.BOOLEAN_LITERAL() != null) {
            type = Literal.LiteralType.BOOLEAN;
        } else {
            throw new IllegalStateException("Unknown literal type");
        }
        
        // Create literal and push to stack
        Literal literal = new Literal(value, type);
        stack.push(literal);
    }
    
    /**
     * Extract the raw value from a literal context
     */
    private Object getLiteralValue(Beast2ModelLanguageParser.LiteralContext ctx) {
        if (ctx.INTEGER_LITERAL() != null) {
            return Integer.parseInt(ctx.INTEGER_LITERAL().getText());
        } else if (ctx.FLOAT_LITERAL() != null) {
            return Double.parseDouble(ctx.FLOAT_LITERAL().getText());
        } else if (ctx.STRING_LITERAL() != null) {
            // Remove quotes from the string literal
            String text = ctx.STRING_LITERAL().getText();
            return text.substring(1, text.length() - 1);
        } else if (ctx.BOOLEAN_LITERAL() != null) {
            return Boolean.parseBoolean(ctx.BOOLEAN_LITERAL().getText());
        } else {
            throw new IllegalStateException("Unknown literal type");
        }
    }
}
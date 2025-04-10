package org.beast2.modelLanguage.builder;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.beast2.modelLanguage.model.*;
import org.beast2.modelLanguage.parser.Beast2ModelLanguageBaseListener;
import org.beast2.modelLanguage.parser.Beast2ModelLanguageLexer;
import org.beast2.modelLanguage.parser.Beast2ModelLanguageParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Implementation of Beast2LangParser that uses ANTLR to parse Beast2Lang syntax.
 */
public class Beast2LangParserImpl implements Beast2LangParser {

    @Override
    public Beast2Model parseFromStream(InputStream inputStream) throws IOException {
        // Create lexer and parser
        Beast2ModelLanguageLexer lexer = new Beast2ModelLanguageLexer(CharStreams.fromStream(inputStream));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        Beast2ModelLanguageParser parser = new Beast2ModelLanguageParser(tokens);
        
        // Parse the input
        Beast2ModelLanguageParser.ProgramContext programContext = parser.program();
        
        // Create a model builder listener
        ModelBuilderListener listener = new ModelBuilderListener();
        
        // Walk the parse tree with our listener
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, programContext);
        
        // Return the constructed model
        return listener.getModel();
    }

    @Override
    public Beast2Model parseFromString(String input) {
        // Create lexer and parser
        Beast2ModelLanguageLexer lexer = new Beast2ModelLanguageLexer(CharStreams.fromString(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        Beast2ModelLanguageParser parser = new Beast2ModelLanguageParser(tokens);
        
        // Parse the input
        Beast2ModelLanguageParser.ProgramContext programContext = parser.program();
        
        // Create a model builder listener
        ModelBuilderListener listener = new ModelBuilderListener();
        
        // Walk the parse tree with our listener
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, programContext);
        
        // Return the constructed model
        return listener.getModel();
    }
    
    /**
     * ANTLR listener that builds a Beast2Model as it traverses the parse tree
     */
    private static class ModelBuilderListener extends Beast2ModelLanguageBaseListener {
        private Beast2Model model = new Beast2Model();
        private Stack<Object> stack = new Stack<>();
        
        public Beast2Model getModel() {
            return model;
        }
        
        @Override
        public void exitVariableDeclaration(Beast2ModelLanguageParser.VariableDeclarationContext ctx) {
            // Get the expression from the stack
            Expression expression = (Expression) stack.pop();
            
            // Get class name and variable name
            String className = ctx.className().getText();
            String variableName = ctx.identifier().getText();
            
            // Create variable declaration and add to model
            VariableDeclaration varDecl = new VariableDeclaration(className, variableName, expression);
            model.addStatement(varDecl);
        }
        
        @Override
        public void exitDistributionAssignment(Beast2ModelLanguageParser.DistributionAssignmentContext ctx) {
            // Get the expression from the stack
            Expression expression = (Expression) stack.pop();
            
            // Get class name and variable name
            String className = ctx.className().getText();
            String variableName = ctx.identifier().getText();
            
            // Create distribution assignment and add to model
            DistributionAssignment distAssign = new DistributionAssignment(className, variableName, expression);
            model.addStatement(distAssign);
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
            Expression value = (Expression) stack.pop();
            
            // Get argument name
            String name = ctx.identifier().getText();
            
            // Create argument and push to stack
            Argument argument = new Argument(name, value);
            stack.push(argument);
        }
        
        @Override
        public void exitLiteral(Beast2ModelLanguageParser.LiteralContext ctx) {
            Literal.LiteralType type;
            Object value;
            
            if (ctx.INTEGER_LITERAL() != null) {
                type = Literal.LiteralType.INTEGER;
                value = Integer.parseInt(ctx.INTEGER_LITERAL().getText());
            } else if (ctx.FLOAT_LITERAL() != null) {
                type = Literal.LiteralType.FLOAT;
                value = Double.parseDouble(ctx.FLOAT_LITERAL().getText());
            } else if (ctx.STRING_LITERAL() != null) {
                type = Literal.LiteralType.STRING;
                // Remove quotes from the string literal
                String text = ctx.STRING_LITERAL().getText();
                value = text.substring(1, text.length() - 1);
            } else if (ctx.BOOLEAN_LITERAL() != null) {
                type = Literal.LiteralType.BOOLEAN;
                value = Boolean.parseBoolean(ctx.BOOLEAN_LITERAL().getText());
            } else {
                throw new IllegalStateException("Unknown literal type");
            }
            
            // Create literal and push to stack
            Literal literal = new Literal(value, type);
            stack.push(literal);
        }
    }
}
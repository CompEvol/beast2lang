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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import java.lang.reflect.Modifier;

/**
 * Builder class that constructs a Beast2Model from an input file using ANTLR,
 * and can also construct BEAST2 objects through reflection.
 */
public class Beast2ModelBuilderReflection {
    
    // Map to store created BEAST2 objects by ID
    private Map<String, Object> beastObjects = new HashMap<>();
    
    /**
     * Parse an input stream and build a Beast2Model
     * 
     * @param inputStream the input stream to parse
     * @return the constructed Beast2Model
     * @throws IOException if an I/O error occurs
     */
    public Beast2Model buildFromStream(InputStream inputStream) throws IOException {
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
    
    /**
     * Parse a string and build a Beast2Model
     * 
     * @param input the input string to parse
     * @return the constructed Beast2Model
     */
    public Beast2Model buildFromString(String input) {
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
     * Build BEAST2 objects from the Beast2Model using reflection
     * 
     * @param model the Beast2Model to convert to BEAST2 objects
     * @return the root BEAST2 object (typically the MCMC object)
     * @throws Exception if construction fails
     */
    public Object buildBeast2Objects(Beast2Model model) throws Exception {
        // First phase: Create all BEAST2 objects
        createBeast2Objects(model);
        
        // Second phase: Connect objects by resolving references
        connectBeast2Objects(model);
        
        // Build full BEAST2 model (structure depends on your BEAST2 version)
        Object rootObject = buildFullModel(model);
        
        return rootObject;
    }
    
    /**
     * Create BEAST2 objects for all statements in the model
     */
    private void createBeast2Objects(Beast2Model model) throws Exception {
        for (Statement statement : model.getStatements()) {
            if (statement instanceof VariableDeclaration) {
                createBeast2ObjectFromVariableDecl((VariableDeclaration) statement);
            } else if (statement instanceof DistributionAssignment) {
                createBeast2ObjectFromDistAssign((DistributionAssignment) statement);
            }
        }
    }
    
    /**
     * Create a BEAST2 object from a VariableDeclaration
     */
    private void createBeast2ObjectFromVariableDecl(VariableDeclaration varDecl) throws Exception {
        String className = varDecl.getClassName();
        String variableName = varDecl.getVariableName();
        Expression value = varDecl.getValue();
        
        // Create BEAST2 object using reflection
        Class<?> beastClass = Class.forName(className);
        Object beastObject = beastClass.getDeclaredConstructor().newInstance();
        
        // Set ID if method exists
        try {
            Method setIdMethod = beastClass.getMethod("setID", String.class);
            setIdMethod.invoke(beastObject, variableName);
        } catch (NoSuchMethodException e) {
            // Not all BEAST2 objects have setID method
        }
        
        // Process the value expression
        if (value instanceof FunctionCall) {
            FunctionCall funcCall = (FunctionCall) value;
            configureBeast2Object(beastObject, funcCall);
        }
        
        // Store the created object
        beastObjects.put(variableName, beastObject);
    }
    
    /**
     * Create a BEAST2 object from a DistributionAssignment
     */
    private void createBeast2ObjectFromDistAssign(DistributionAssignment distAssign) throws Exception {
        String className = distAssign.getClassName();
        String variableName = distAssign.getVariableName();
        Expression distribution = distAssign.getDistribution();
        
        // Create the main object (e.g. RealParameter)
        Class<?> beastClass = Class.forName(className);
        Object beastObject = null;
        
        try {
            // Try to create using the default constructor
            beastObject = beastClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | NoSuchMethodException e) {
            // Try to find a static factory method like 'getInstance' or 'new'
            try {
                Method factoryMethod = beastClass.getMethod("getInstance");
                beastObject = factoryMethod.invoke(null);
            } catch (NoSuchMethodException ex) {
                try {
                    Method newMethod = beastClass.getMethod("new" + beastClass.getSimpleName());
                    beastObject = newMethod.invoke(null);
                } catch (NoSuchMethodException ex2) {
                    // As a fallback, try to find any concrete implementation
                    if (beastClass.isInterface() || Modifier.isAbstract(beastClass.getModifiers())) {
                        // Try to find a concrete implementation
                        String implClassName = className + "Impl";
                        try {
                            Class<?> implClass = Class.forName(implClassName);
                            beastObject = implClass.getDeclaredConstructor().newInstance();
                        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException ex3) {
                            throw new RuntimeException("Cannot instantiate abstract class or interface: " + className);
                        }
                    } else {
                        throw new RuntimeException("Cannot instantiate class: " + className, e);
                    }
                }
            }
        }
        
        if (beastObject == null) {
            throw new RuntimeException("Failed to instantiate object of class: " + className);
        }
        
        // Set ID if method exists
        try {
            Method setIdMethod = beastClass.getMethod("setID", String.class);
            setIdMethod.invoke(beastObject, variableName);
        } catch (NoSuchMethodException e) {
            // Not all BEAST2 objects have setID method
        }
        
        // Store the created object
        beastObjects.put(variableName, beastObject);
        
        // Process the distribution expression to create the distribution object
        if (distribution instanceof FunctionCall) {
            FunctionCall funcCall = (FunctionCall) distribution;
            
            // Create the distribution object
            String distClassName = funcCall.getClassName();
            Class<?> distClass = Class.forName(distClassName);
            Object distObject = null;
            
            try {
                // Try to create using the default constructor
                distObject = distClass.getDeclaredConstructor().newInstance();
            } catch (InstantiationException | NoSuchMethodException e) {
                // Try to find a static factory method like 'getInstance' or 'new'
                try {
                    Method factoryMethod = distClass.getMethod("getInstance");
                    distObject = factoryMethod.invoke(null);
                } catch (NoSuchMethodException ex) {
                    try {
                        Method newMethod = distClass.getMethod("new" + distClass.getSimpleName());
                        distObject = newMethod.invoke(null);
                    } catch (NoSuchMethodException ex2) {
                        // As a fallback, try to find any concrete implementation
                        if (distClass.isInterface() || Modifier.isAbstract(distClass.getModifiers())) {
                            // Try to find a concrete implementation
                            String implClassName = distClassName + "Impl";
                            try {
                                Class<?> implClass = Class.forName(implClassName);
                                distObject = implClass.getDeclaredConstructor().newInstance();
                            } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException ex3) {
                                throw new RuntimeException("Cannot instantiate abstract class or interface: " + distClassName);
                            }
                        } else {
                            throw new RuntimeException("Cannot instantiate class: " + distClassName, e);
                        }
                    }
                }
            }
            
            if (distObject == null) {
                throw new RuntimeException("Failed to instantiate distribution object of class: " + distClassName);
            }
            
            // Set a unique ID for the distribution object
            try {
                Method setIdMethod = distClass.getMethod("setID", String.class);
                setIdMethod.invoke(distObject, variableName + "Prior");  // E.g. "kappaPrior"
            } catch (NoSuchMethodException e) {
                // Not all BEAST2 objects have setID method
            }
            
            // First, automatically set the 'x' parameter to the main object
            try {
                // Try initByName method first
                try {
                    Method initMethod = distClass.getMethod("initByName", String.class, Object.class);
                    initMethod.invoke(distObject, "x", beastObject);
                } catch (NoSuchMethodException e) {
                    // Try setInputValue method next
                    try {
                        Method setInputMethod = distClass.getMethod("setInputValue", String.class, Object.class);
                        setInputMethod.invoke(distObject, "x", beastObject);
                    } catch (NoSuchMethodException e2) {
                        try {
                            // If all else fails, try setter method
                            Method setXMethod = distClass.getMethod("setX", beastClass);
                            setXMethod.invoke(distObject, beastObject);
                        } catch (NoSuchMethodException e3) {
                            System.err.println("Could not find a method to set 'x' parameter on distribution");
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to set 'x' parameter on distribution: " + e.getMessage());
            }
            
            // Then, configure the distribution object with the parameters provided
            configureBeast2Object(distObject, funcCall);
            
            // Store the distribution object
            beastObjects.put(variableName + "Prior", distObject);
            
            // Try to establish the back-reference from the parameter to the distribution
            try {
                Method setDistMethod = beastClass.getMethod("setDistribution", distClass);
                setDistMethod.invoke(beastObject, distObject);
            } catch (NoSuchMethodException e) {
                // Not all parameter classes have setDistribution method
            }
        }
    } 
    
    /**
     * Configure a BEAST2 object with parameters from a function call
     */
    private void configureBeast2Object(Object beastObject, FunctionCall funcCall) throws Exception {
        Class<?> beastClass = beastObject.getClass();
        
        // Process each argument
        for (Argument arg : funcCall.getArguments()) {
            String paramName = arg.getName();
            Expression paramValue = arg.getValue();
            
            // Try to find initByName or setInputValue method
            try {
                // First try initByName(String, Object) method
                Method initMethod = findMethodInHierarchy(beastClass, "initByName", String.class, Object.class);
                if (initMethod != null) {
                    Object value = resolveExpressionValue(paramValue);
                    initMethod.invoke(beastObject, paramName, value);
                    continue;
                }
                
                // Try setInputValue(String, Object) method
                Method setInputMethod = findMethodInHierarchy(beastClass, "setInputValue", String.class, Object.class);
                if (setInputMethod != null) {
                    Object value = resolveExpressionValue(paramValue);
                    setInputMethod.invoke(beastObject, paramName, value);
                    continue;
                }
                
                // Try setter method
                String setterName = "set" + capitalize(paramName);
                Method[] methods = beastClass.getMethods();
                for (Method method : methods) {
                    if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                        Object value = resolveExpressionValue(paramValue);
                        method.invoke(beastObject, value);
                        break;
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to set parameter " + paramName + " on " + beastClass.getName() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Find a method in a class hierarchy
     */
    private Method findMethodInHierarchy(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null) {
                return findMethodInHierarchy(superClass, methodName, paramTypes);
            }
            return null;
        }
    }
    
    /**
     * Resolve the value of an expression to a Java object
     */
    private Object resolveExpressionValue(Expression expr) throws Exception {
        if (expr instanceof Identifier) {
            // Reference to another object
            String refName = ((Identifier) expr).getName();
            return beastObjects.get(refName);
        } else if (expr instanceof Literal) {
            // Literal value
            Literal literal = (Literal) expr;
            return literal.getValue();
        } else if (expr instanceof FunctionCall) {
            // Nested function call - create a new object
            FunctionCall funcCall = (FunctionCall) expr;
            String className = funcCall.getClassName();
            
            // Create the object using reflection
            Class<?> beastClass = Class.forName(className);
            Object nestedObject = beastClass.getDeclaredConstructor().newInstance();
            
            // Configure the object
            configureBeast2Object(nestedObject, funcCall);
            
            return nestedObject;
        }
        
        return null;
    }
    
    /**
     * Connect BEAST2 objects by resolving references
     */
    private void connectBeast2Objects(Beast2Model model) throws Exception {
        // This phase is useful if there are cyclic dependencies or complex relationships
        // that couldn't be fully resolved during the first phase
        
        // For most cases, the connections are already made during object creation
    }
    
    /**
     * Build the full BEAST2 model
     */
    private Object buildFullModel(Beast2Model model) throws Exception {
        // This would depend on the BEAST2 version and model structure
        // In a typical BEAST2 model, you would:
        // 1. Find or create the MCMC object
        // 2. Set up posterior, prior, likelihood components
        // 3. Set up state, operators, and loggers
        
        // For now, return the "main" object from the model
        // In a real implementation, you would use reflection to build
        // the proper BEAST2 model structure
        
        // Example of finding a likely "root" object
        for (String key : beastObjects.keySet()) {
            if (key.toLowerCase().contains("mcmc")) {
                return beastObjects.get(key);
            }
        }
        
        // If no MCMC found, return the first object as a fallback
        if (!beastObjects.isEmpty()) {
            return beastObjects.values().iterator().next();
        }
        
        return null;
    }
    
    /**
     * Capitalize the first letter of a string
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
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
                value = Float.parseFloat(ctx.FLOAT_LITERAL().getText());
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
    
    /**
     * Get all created BEAST2 objects
     */
    public Map<String, Object> getBeastObjects() {
        return beastObjects;
    }
    
    /**
     * Get a specific BEAST2 object by name
     */
    public Object getBeastObject(String name) {
        return beastObjects.get(name);
    }
}
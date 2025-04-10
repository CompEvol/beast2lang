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
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;

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

        System.out.println("Creating beast2 objects");

        // First phase: Create all BEAST2 objects
        createBeast2Objects(model);

        System.out.println("Connecting beast2 objects");


        // Second phase: Connect objects by resolving references
        connectBeast2Objects(model);
        
        // Build full BEAST2 model (structure depends on your BEAST2 version)
        Object rootObject = buildFullModel(model);
        
        return rootObject;
    }
    
private void createBeast2Objects(Beast2Model model) throws Exception {
    List<Statement> stmts = model.getStatements();
    System.out.println("[DEBUG] createBeast2Objects: total statements = " + stmts.size());
    for (Statement statement : stmts) {
        System.out.println("[DEBUG]   statement = "
            + statement.getClass().getSimpleName() + " → " + statement);
        if (statement instanceof VariableDeclaration) {
            System.out.println("[DEBUG]     → VariableDeclaration branch");
            createBeast2ObjectFromVariableDecl((VariableDeclaration) statement);
        } else if (statement instanceof DistributionAssignment) {
            System.out.println("[DEBUG]     → DistributionAssignment branch");
            createBeast2ObjectFromDistAssign((DistributionAssignment) statement);
        } else {
            System.out.println("[DEBUG]     → UNKNOWN statement type");
        }
    }
}
    
private void createBeast2ObjectFromVariableDecl(VariableDeclaration varDecl) {
    String declaredTypeName      = varDecl.getClassName();   // e.g. "beast.base.inference.parameter.RealParameter"
    String variableName          = varDecl.getVariableName(); 
    Expression value             = varDecl.getValue();       // should be a FunctionCall

    System.out.println("Creating object with name: " + variableName 
                       + " of declared type: " + declaredTypeName);

    if (!(value instanceof FunctionCall)) {
        throw new IllegalArgumentException("Right side of variable declaration must be a function call");
    }
    FunctionCall funcCall = (FunctionCall) value;
    String implementationClassName = funcCall.getClassName();

    // 1) Load classes
    Class<?> declaredType       = null;
    try {
        declaredType = Class.forName(declaredTypeName);
    } catch (ClassNotFoundException e) {
        throw new RuntimeException("Failed to obtain declared class " + declaredTypeName,e);
    }
    Class<?> implementationClass = null;
    try {
        implementationClass = Class.forName(implementationClassName);
    } catch (ClassNotFoundException e) {
        throw new RuntimeException("Failed to obtain implementation class " + implementationClassName,e);
    }
    if (!declaredType.isAssignableFrom(implementationClass)) {
        throw new ClassCastException(
            implementationClassName + " is not assignable to " + declaredTypeName
        );
    }

    // 2) Instantiate
    Object beastObject = null;
    try {
        beastObject = implementationClass.getDeclaredConstructor().newInstance();
    } catch (InstantiationException e) {
        throw new RuntimeException("Failed instantiation of " + implementationClassName, e);
    } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
        throw new RuntimeException(e);
    } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
    }
    System.out.println("Successfully instantiated " + implementationClassName);

    // 3) setID if present
    try {
        Method setId = implementationClass.getMethod("setID", String.class);
        try {
            setId.invoke(beastObject, variableName);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to setID on " + implementationClassName, e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    } catch (NoSuchMethodException ignore) {}

    // 4) INTROSPECT all declared public Input<?> fields on the class
    //    and build a name→Input<?> map
    Map<String, Input<?>> inputMap = new HashMap<>();
    for (Field f : implementationClass.getFields()) {
    	
    	System.out.println("  Field " + f);
    	System.out.println("    type " + f.getType());
    
        if (Input.class.isAssignableFrom(f.getType())) {
            @SuppressWarnings("unchecked")
            Input<?> in = null;
            try {
                in = (Input<?>) f.get(beastObject);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to get field " + f + " from " + beastObject, e);
            }
            inputMap.put(in.getName(), in);
            // now inputMap.get("M") → the MParameterInput instance, etc.
        }
    }

    // 5) CONFIGURE all arguments by matching name & type against that map
    for (Argument arg : funcCall.getArguments()) {

        System.out.println("  Argument " + arg);
        System.out.println("    name " + arg.getName());

        String   name     = arg.getName();
        Object   rawValue = resolveExpressionValue(arg.getValue());

        System.out.println("    rawValue: " + rawValue);

        Input<?> in       = inputMap.get(name);
        if (in == null) {
            throw new RuntimeException("No Input named '" + name 
                                       + "' in " + implementationClassName);
        }
        Class<?> expected = in.getType();
        
        // Check type compatibility only if we have an expected type and a non-null value
        if (expected != null && rawValue != null) {
            if (!expected.isAssignableFrom(rawValue.getClass())) {
                throw new RuntimeException(String.format(
                    "Type mismatch for Input '%s' on %s: expected %s but got %s",
                    name,
                    implementationClassName,
                    expected.getSimpleName(),
                    rawValue.getClass().getSimpleName()
                ));
            }
        } else if (expected == null) {
            // Log that we're skipping type checking due to null expected type
            System.out.println("    skipping type check for '" + name + "' (unknown expected type)");
        }
        // finally, set it
        @SuppressWarnings("unchecked")
        Input<Object> inObj = (Input<Object>) in;
        inObj.setValue(rawValue, (BEASTInterface) beastObject);
    }

    // 6) Call initAndValidate if it exists
    try {
        Method initAndValidate = implementationClass.getMethod("initAndValidate");
        try {
            System.out.println("Calling initAndValidate() on " + implementationClassName);
            initAndValidate.invoke(beastObject);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to call initAndValidate on " + implementationClassName, e);
        }
    } catch (NoSuchMethodException e) {
        // Some BEAST objects might not have this method, that's OK
        System.out.println("Note: " + implementationClassName + " has no initAndValidate method");
    }

    // 7) Store the object
    storeObject(variableName, beastObject);
}

    private void storeObject(String name, Object object) {
        System.out.println("Storing object ");
        System.out.println("  Name " + name);
        System.out.println("  Object " + object);
        beastObjects.put(name, object);

    }

    private void createBeast2ObjectFromDistAssign(DistributionAssignment distAssign) throws Exception {

        String className     = distAssign.getClassName();
        String varName       = distAssign.getVariableName();
        Expression distribution = distAssign.getDistribution();

        System.out.println("Creating random variable: " + varName);

        // 1) Instantiate the parameter object
        Class<?> beastClass = Class.forName(className);
        Object beastObject  = beastClass.getDeclaredConstructor().newInstance();
        System.out.println("[DEBUG] Instantiated parameter " + className + " as '" + varName + "'");
        
        // 1.1) Set ID
        try {
            beastClass.getMethod("setID", String.class).invoke(beastObject, varName);
        } catch (NoSuchMethodException ignore) {}
        
        // 1.2) Special handling for RealParameter to ensure proper initialization
        if (beastClass.getName().equals("beast.base.inference.parameter.RealParameter")) {
            System.out.println("[DEBUG] Special handling for RealParameter initialization");
            
            // Find and set a default value for the parameter
            try {
                // Set a default value of 0.0 - this ensures the values array gets initialized
                Field valuesInputField = null;
                
                // First look for valuesInput in the class itself
                for (Field field : beastClass.getFields()) {
                    if (field.getName().equals("valuesInput") && Input.class.isAssignableFrom(field.getType())) {
                        valuesInputField = field;
                        break;
                    }
                }
                
                // If not found, look in superclasses
                if (valuesInputField == null) {
                    Class<?> currentClass = beastClass.getSuperclass();
                    while (currentClass != null && valuesInputField == null) {
                        for (Field field : currentClass.getFields()) {
                            if (field.getName().equals("valuesInput") && Input.class.isAssignableFrom(field.getType())) {
                                valuesInputField = field;
                                break;
                            }
                        }
                        currentClass = currentClass.getSuperclass();
                    }
                }
                
                if (valuesInputField != null) {
                    @SuppressWarnings("unchecked")
                    Input<Object> valuesInput = (Input<Object>) valuesInputField.get(beastObject);
                    
                    // Try to set a default value (could be a Double or a List)
                    List<Double> defaultValue = new ArrayList<>();
                    defaultValue.add(0.0); // Default value of 0.0
                    valuesInput.setValue(defaultValue, (BEASTInterface) beastObject);
                    System.out.println("[DEBUG] Set default value for RealParameter: " + defaultValue);
                    
                    // Call initAndValidate to ensure internal arrays are initialized
                    try {
                        Method initMethod = beastClass.getMethod("initAndValidate");
                        initMethod.invoke(beastObject);
                        System.out.println("[DEBUG] Called initAndValidate on RealParameter");
                    } catch (Exception e) {
                        System.err.println("[ERROR] Failed to call initAndValidate: " + e.getMessage());
                    }
                } else {
                    System.err.println("[ERROR] Could not find valuesInput field in RealParameter");
                }
            } catch (Exception e) {
                System.err.println("[ERROR] Failed to initialize RealParameter: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        storeObject(varName, beastObject);

        // 2) Bail if no distribution
        if (!(distribution instanceof FunctionCall)) {
            System.out.println("[DEBUG] No distribution for " + varName);
            return;
        }
        FunctionCall funcCall = (FunctionCall) distribution;

        // 3) Instantiate the distribution
        String distClassName = funcCall.getClassName();
        Class<?> distClass   = Class.forName(distClassName);
        Object distObject    = distClass.getDeclaredConstructor().newInstance();
        System.out.println("[DEBUG] Instantiated distribution " + distClassName + " as '" + varName + "Prior'");
        try {
            distClass.getMethod("setID", String.class).invoke(distObject, varName + "Prior");
        } catch (NoSuchMethodException ignore) {}

        // 4) Reflect over Input<?> fields
        Map<String,Input<?>> inputMap = new HashMap<>();
        Map<String,Class<?>> expectedTypeMap = new HashMap<>();
        System.out.println("[DEBUG] Scanning public Input<?> fields on " + distClass.getSimpleName());
        for (Field f : distClass.getFields()) {
            if (!Input.class.isAssignableFrom(f.getType())) continue;
            @SuppressWarnings("unchecked")
            Input<?> in = (Input<?>) f.get(distObject);
            String name = in.getName();
            inputMap.put(name, in);

            // first try the Input's own getType()
            Class<?> expected = in.getType();
            System.out.printf("[DEBUG]  field %-20s → B2L name '%s', Input.getType() = %s%n",
                            f.getName(), name, expected);

            // fallback if getType() was null
            if (expected == null) {
                Type gtype = f.getGenericType();
                if (gtype instanceof ParameterizedType) {
                    Type[] args = ((ParameterizedType) gtype).getActualTypeArguments();
                    if (args.length == 1 && args[0] instanceof Class<?>) {
                        expected = (Class<?>) args[0];
                        System.out.printf("[DEBUG]    fallback generic type for '%s' = %s%n",
                                        name, expected);
                    }
                }
            }
            expectedTypeMap.put(name, expected);
        }

        // 5) Wire the 'x' input
        Input<?> xIn = inputMap.get("x");
        if (xIn != null) {
            @SuppressWarnings("unchecked")
            Input<Object> xi = (Input<Object>) xIn;
            xi.setValue(beastObject, (BEASTInterface) distObject);
            System.out.println("[DEBUG]  wired 'x' → " + varName);
        }

        // 6) Configure remaining args
        System.out.println("[DEBUG] Configuring distribution arguments:");
        for (Argument arg : funcCall.getArguments()) {
            String name = arg.getName();
            if ("x".equals(name)) continue;

            Object rawValue = resolveExpressionValue(arg.getValue());
            Class<?> rawClass = rawValue == null ? null : rawValue.getClass();
            Class<?> expected = expectedTypeMap.get(name);

            System.out.printf("[DEBUG]   arg '%s': rawValue=%s (%s), expected=%s%n",
                            name,
                            rawValue,
                            rawClass,
                            expected);

            Input<?> in = inputMap.get(name);
            if (in == null) {
                throw new RuntimeException("No Input named '" + name
                    + "' on distribution " + distClass.getSimpleName());
            }
            
            // Only check compatibility if we have both an expected type and a non-null value
            if (expected != null && rawValue != null) {
                // Check if the raw value's class is assignable to the expected type
                if (!expected.isAssignableFrom(rawValue.getClass())) {
                    throw new RuntimeException(String.format(
                        "Type mismatch for '%s': expected %s but got %s",
                        name,
                        expected.getSimpleName(),
                        rawValue.getClass().getSimpleName()
                    ));
                }
            } else if (expected == null) {
                // Log that we're skipping type checking due to null expected type
                System.out.println("[DEBUG]    skipping type check for '" + name + "' (unknown expected type)");
                
                // Try to determine type from generic parameter if possible
                try {
                    // Get the field for this input
                    for (Field field : distClass.getFields()) {
                        if (field.getType() == Input.class && field.get(distObject) == in) {
                            // We found the field, now check its generic type
                            Type genericType = field.getGenericType();
                            if (genericType instanceof ParameterizedType) {
                                Type[] typeArgs = ((ParameterizedType) genericType).getActualTypeArguments();
                                if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                                    Class<?> typeArgClass = (Class<?>) typeArgs[0];
                                    System.out.println("[DEBUG]    inferred expected type for '" + name + 
                                                      "' from generic parameter: " + typeArgClass.getName());
                                    
                                    // Now check compatibility with inferred type
                                    if (rawValue != null && !typeArgClass.isAssignableFrom(rawValue.getClass())) {
                                        throw new RuntimeException(String.format(
                                            "Type mismatch for '%s': inferred expected %s but got %s",
                                            name,
                                            typeArgClass.getSimpleName(),
                                            rawValue.getClass().getSimpleName()
                                        ));
                                    }
                                }
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[DEBUG]    could not infer type from generic parameter: " + e.getMessage());
                }
            }

            try {
                @SuppressWarnings("unchecked")
                Input<Object> inObj = (Input<Object>) in;
                inObj.setValue(rawValue, (BEASTInterface) distObject);
                System.out.println("[DEBUG]    set '" + name + "' → " + rawValue);
            } catch (Exception e) {
                System.err.println("[ERROR] Failed to set input '" + name + "': " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
        }

        // 7) Call initAndValidate for the distribution object
        try {
            Method initAndValidate = distClass.getMethod("initAndValidate");
            System.out.println("[DEBUG] Calling initAndValidate() on " + distClassName);
            initAndValidate.invoke(distObject);
        } catch (NoSuchMethodException e) {
            System.out.println("[DEBUG] Note: " + distClassName + " has no initAndValidate method");
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to call initAndValidate on " + distClassName + ": " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        // 8) Call initAndValidate for the parameter object if it wasn't called earlier
        try {
            Method initAndValidate = beastClass.getMethod("initAndValidate");
            System.out.println("[DEBUG] Calling initAndValidate() on " + className);
            initAndValidate.invoke(beastObject);
        } catch (NoSuchMethodException e) {
            System.out.println("[DEBUG] Note: " + className + " has no initAndValidate method");
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to call initAndValidate on " + className + ": " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        // 9) Store and hook back
        storeObject(varName + "Prior", distObject);
        try {
            Method setDist = beastClass.getMethod("setDistribution", distClass);
            setDist.invoke(beastObject, distObject);
            System.out.println("[DEBUG] hooked distribution into " + varName);
        } catch (NoSuchMethodException ignore) {}
    }
    
    /**
     * Configure a BEAST2 object with parameters from a function call
     */
    private void configureBeast2Object(Object beastObject, FunctionCall funcCall) {
        
        Class<?> beastClass = beastObject.getClass();
        
        if (beastObject instanceof BEASTInterface) {
            BEASTInterface beastIface = (BEASTInterface) beastObject;
            for (Argument arg : funcCall.getArguments()) {
                String paramName = arg.getName();
                Object paramVal = resolveExpressionValue(arg.getValue());
        
                // 1. Inspect the Input
                Input<?> input = beastIface.getInput(paramName);
                Class<?> inputType = input.getType();
        
                // 2. If inputType is a list, but paramVal is a single Double, wrap it
                //    Or if paramVal is "[...]", parse it into a List<Double>, etc.
                Object convertedVal = convertIfNeeded(inputType, paramVal);
        
                // 3. Set the input
                input.setValue(convertedVal, beastIface);
            }
            
            // 4. Call initAndValidate after all inputs are set
            try {
                Method initMethod = findMethodInHierarchy(beastClass, "initAndValidate");
                if (initMethod != null) {
                    System.out.println("[DEBUG] Calling initAndValidate() on nested object of class " + beastClass.getName());
                    initMethod.invoke(beastObject);
                }
            } catch (Exception e) {
                System.err.println("[WARN] Could not call initAndValidate on nested object: " + e.getMessage());
            }
        }
        else {
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
            
            // Call initAndValidate or similar initialization method if it exists
            try {
                Method initMethod = findMethodInHierarchy(beastClass, "initAndValidate");
                if (initMethod != null) {
                    System.out.println("[DEBUG] Calling initAndValidate() on non-BEAST object of class " + beastClass.getName());
                    initMethod.invoke(beastObject);
                } else {
                    // Try initialize method as fallback
                    Method initializeMethod = findMethodInHierarchy(beastClass, "initialize");
                    if (initializeMethod != null) {
                        System.out.println("[DEBUG] Calling initialize() on non-BEAST object of class " + beastClass.getName());
                        initializeMethod.invoke(beastObject);
                    }
                }
            } catch (Exception e) {
                System.err.println("[WARN] Could not call initialization method on object: " + e.getMessage());
            }
        }
    }
    
    /**
 * Convert the given paramVal into whatever type the BEAST Input expects.
 * E.g., if the Input type is a List<Double> but paramVal is just "1.0",
 * we can wrap it in a single-element list.
 */
private Object convertIfNeeded(Class<?> inputType, Object paramVal) {
    // If nothing supplied, nothing to convert
    if (paramVal == null) {
        return null;
    }

    // Example: if the input expects a List and paramVal is not already a List, convert it
    if (List.class.isAssignableFrom(inputType)) {
        // If it's already a List, just return it
        if (paramVal instanceof List) {
            return paramVal;
        }

        // If it's a single number, wrap in a one-element list
        if (paramVal instanceof Number) {
            List<Double> singleList = new ArrayList<>();
            singleList.add(((Number) paramVal).doubleValue());
            return singleList;
        }

        // If it's a bracketed String like "[1.0, 2.0]", parse into a List<Double>
        if (paramVal instanceof String) {
            String str = ((String) paramVal).trim();
            if (str.startsWith("[") && str.endsWith("]")) {
                String inner = str.substring(1, str.length() - 1).trim();
                if (inner.isEmpty()) {
                    // e.g. "[]" => empty list
                    return new ArrayList<Double>();
                }
                String[] tokens = inner.split(",");
                List<Double> result = new ArrayList<>();
                for (String token : tokens) {
                    // Attempt to parse each token as a double
                    result.add(Double.valueOf(token.trim()));
                }
                return result;
            }
        }
        // If none of the above matched, just return paramVal unchanged
        // (the setValue(...) might fail if it's not the correct type)
        return paramVal;
    }

    // You could add more checks if inputType is e.g. Double[].class or RealParameter
    // For now, if we don't need to transform anything, return as-is.
    return paramVal;
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
    private Object resolveExpressionValue(Expression expr) {

        if (expr instanceof Identifier) {
            // Reference to another object
            String refName = ((Identifier) expr).getName();
            System.out.println("Resolving identifier " + refName);
            System.out.println("Keys: " + beastObjects.keySet());
            System.out.println("Values: " + beastObjects.values());
            Object beastObject = null;

            try {
                beastObject = beastObjects.get(refName);
            } catch (Exception e) {
                System.out.println("Runtime exception trying to retrieve object from beastObjects");
                throw new RuntimeException(e);
            }

            System.out.println("Got object " + beastObject);

            if (beastObject == null) {
                System.out.println("Keys: " + beastObjects.keySet());
            } else {
                System.out.println("Retrieved " + refName + ":" + beastObject);
            }

            return beastObject;
        } else if (expr instanceof Literal) {
            // Literal value
            Literal literal = (Literal) expr;
            return literal.getValue();
        } else if (expr instanceof FunctionCall) {
            // Nested function call - create a new object
            FunctionCall funcCall = (FunctionCall) expr;
            String className = funcCall.getClassName();
            
            // Create the object using reflection
            Class<?> beastClass = null;
            try {
                beastClass = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Failed to create class " + className, e);
            }
            Object nestedObject = null;
            try {
                nestedObject = beastClass.getDeclaredConstructor().newInstance();
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }

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
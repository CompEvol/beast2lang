package org.beast2.modelLanguage.converter;

import org.beast2.modelLanguage.model.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Converter from Beast2 model to PhyloSpec JSON
 */
public class Beast2ToPhyloSpecConverter implements StatementVisitor {
    private JSONObject phyloSpec;
    private JSONArray variables;
    private JSONArray distributions;
    
    /**
     * Convert a Beast2 model to PhyloSpec JSON
     * 
     * @param model the Beast2 model to convert
     * @return the PhyloSpec JSON object
     */
    public JSONObject convert(Beast2Model model) {
        // Initialize the PhyloSpec JSON structure
        phyloSpec = new JSONObject();
        variables = new JSONArray();
        distributions = new JSONArray();
        
        phyloSpec.put("variables", variables);
        phyloSpec.put("distributions", distributions);
        
        // Process all statements in the model
        model.accept(this);
        
        return phyloSpec;
    }
    
    @Override
    public void visit(VariableDeclaration varDecl) {
        // Convert a variable declaration to PhyloSpec
        JSONObject variable = new JSONObject();
        variable.put("name", varDecl.getVariableName());
        variable.put("type", varDecl.getClassName());
        
        // Handle the value expression
        JSONObject value = convertExpression(varDecl.getValue());
        if (value != null) {
            variable.put("value", value);
        }
        
        variables.put(variable);
    }
    
    @Override
    public void visit(DistributionAssignment distAssign) {
        // Convert a distribution assignment to PhyloSpec
        JSONObject distribution = new JSONObject();
        distribution.put("variable", distAssign.getVariableName());
        
        // Handle the distribution expression
        JSONObject distValue = convertExpression(distAssign.getDistribution());
        if (distValue != null) {
            distribution.put("distribution", distValue);
        }
        
        distributions.put(distribution);
    }
    
    @Override
    public void visit(AnnotatedStatement annotatedStmt) {
        // Process annotations
        Statement innerStmt = annotatedStmt.getStatement();
        for (Annotation annotation : annotatedStmt.getAnnotations()) {

            // Special handling for @observed annotation
            if ("observed".equals(annotation.getName()) && innerStmt instanceof DistributionAssignment) {
                DistributionAssignment distAssign = (DistributionAssignment) innerStmt;

                // Visit the inner statement
                visit(distAssign);

                // Mark the variable as observed in the last added distribution
                if (distributions.length() > 0) {
                    JSONObject lastDist = distributions.getJSONObject(distributions.length() - 1);
                    lastDist.put("observed", true);

                    // Add data file if available
                    if (annotation.hasParameter("data")) {
                        lastDist.put("dataFile", annotation.getParameterAsIdentifer("data"));
                    }
                }
            } else {
                // For other types of annotations, just process the inner statement
                innerStmt.accept(this);
            }
        }
    }
    
    /**
     * Convert an expression to a PhyloSpec JSON object
     * 
     * @param expr the expression to convert
     * @return the PhyloSpec JSON object
     */
    private JSONObject convertExpression(Expression expr) {
        if (expr instanceof FunctionCall) {
            FunctionCall funcCall = (FunctionCall) expr;
            JSONObject function = new JSONObject();
            function.put("type", funcCall.getClassName());
            
            // Add arguments
            JSONObject args = new JSONObject();
            for (Argument arg : funcCall.getArguments()) {
                JSONObject argValue;
                if (arg.getValue() instanceof Identifier) {
                    // Reference to another variable
                    Identifier id = (Identifier) arg.getValue();
                    args.put(arg.getName(), id.getName());
                } else if (arg.getValue() instanceof FunctionCall) {
                    // Nested function call
                    argValue = convertExpression(arg.getValue());
                    args.put(arg.getName(), argValue);
                } else if (arg.getValue() instanceof Literal) {
                    // Literal value
                    Literal lit = (Literal) arg.getValue();
                    args.put(arg.getName(), lit.getValue());
                }
            }
            
            function.put("args", args);
            return function;
        } else if (expr instanceof Identifier) {
            // Reference to another variable
            Identifier id = (Identifier) expr;
            JSONObject ref = new JSONObject();
            ref.put("$ref", id.getName());
            return ref;
        } else if (expr instanceof Literal) {
            // Literal value - not typically used in PhyloSpec top-level expressions
            Literal lit = (Literal) expr;
            JSONObject value = new JSONObject();
            value.put("value", lit.getValue());
            return value;
        }
        
        return null;
    }
}
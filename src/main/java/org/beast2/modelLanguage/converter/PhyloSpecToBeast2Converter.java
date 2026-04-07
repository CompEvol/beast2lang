package org.beast2.modelLanguage.converter;

import org.beast2.modelLanguage.model.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Converter that transforms a PhyloSpec/ModelPhy JSON representation into a Beast2Model
 */
public class PhyloSpecToBeast2Converter {
    
    /**
     * Convert a PhyloSpec/ModelPhy JSON representation to a Beast2Model
     * 
     * @param phyloSpec the JSONObject containing the PhyloSpec representation
     * @return a Beast2Model
     */
    public Beast2Model convert(JSONObject phyloSpec) {
        Beast2Model model = new Beast2Model();
        
        // Get components from the PhyloSpec model
        JSONArray components = phyloSpec.getJSONObject("model").getJSONArray("components");
        
        // Process each component
        for (int i = 0; i < components.length(); i++) {
            JSONObject component = components.getJSONObject(i);
            Statement statement = convertComponent(component);
            model.addStatement(statement);
        }
        
        return model;
    }
    
    /**
     * Convert a PhyloSpec component to a Beast2 statement
     * 
     * @param component the JSONObject containing the component
     * @return a Beast2 Statement
     */
    private Statement convertComponent(JSONObject component) {
        String id = component.getString("id");
        String type = component.getString("type");
        String className = getFullClassName(type);
        
        if (component.has("distribution")) {
            // It's a distribution assignment
            Expression distribution = convertExpression(component.getJSONObject("distribution"));
            return new DistributionAssignment(className, id, distribution);
        } else if (component.has("value")) {
            // It's a variable declaration
            Expression value = convertExpression(component.getJSONObject("value"));
            return new VariableDeclaration(className, id, value);
        } else {
            throw new IllegalArgumentException("Unknown component type: " + component.toString());
        }
    }
    
    /**
     * Convert a PhyloSpec expression to a Beast2 expression
     * 
     * @param expr the JSONObject containing the expression
     * @return a Beast2 Expression
     */
    private Expression convertExpression(JSONObject expr) {
        if (expr.has("ref")) {
            // It's a reference to another component
            return new Identifier(expr.getString("ref"));
        } else if (expr.has("type")) {
            // It's a function call
            String type = expr.getString("type");
            String className = getFullClassName(type);
            List<Argument> arguments = new ArrayList<>();
            
            // Process function parameters
            if (expr.has("parameters")) {
                JSONObject parameters = expr.getJSONObject("parameters");
                for (String paramName : parameters.keySet()) {
                    Object paramValue = parameters.get(paramName);
                    
                    if (paramValue instanceof JSONObject) {
                        // It's a nested expression
                        Expression argExpr = convertExpression((JSONObject) paramValue);
                        arguments.add(new Argument(paramName, argExpr));
                    } else {
                        // It's a literal value
                        Literal.LiteralType literalType;
                        
                        if (paramValue instanceof Integer) {
                            literalType = Literal.LiteralType.INTEGER;
                        } else if (paramValue instanceof Float || paramValue instanceof Double) {
                            literalType = Literal.LiteralType.FLOAT;
                        } else if (paramValue instanceof Boolean) {
                            literalType = Literal.LiteralType.BOOLEAN;
                        } else {
                            literalType = Literal.LiteralType.STRING;
                        }
                        
                        Literal literal = new Literal(paramValue, literalType);
                        arguments.add(new Argument(paramName, literal));
                    }
                }
            }
            
            return new FunctionCall(className, arguments);
        } else if (expr.has("value")) {
            // It's a literal value
            Object value = expr.get("value");
            Literal.LiteralType literalType;
            
            if (value instanceof Integer) {
                literalType = Literal.LiteralType.INTEGER;
            } else if (value instanceof Float || value instanceof Double) {
                literalType = Literal.LiteralType.FLOAT;
            } else if (value instanceof Boolean) {
                literalType = Literal.LiteralType.BOOLEAN;
            } else {
                literalType = Literal.LiteralType.STRING;
            }
            
            return new Literal(value, literalType);
        } else {
            throw new IllegalArgumentException("Unknown expression type: " + expr.toString());
        }
    }
    
    /**
     * Get a full class name from a simple type name
     * This is a mapping from PhyloSpec types to Beast2 class names
     * 
     * @param typeName the simple type name
     * @return the fully qualified class name
     */
    private String getFullClassName(String typeName) {
        // This is a mapping from simple type names to fully qualified class names
        // It would need to be expanded with all the necessary mappings
        Map<String, String> typeMap = new HashMap<>();
        typeMap.put("LogNormalDistributionModel", "beast.base.inference.distribution.LogNormalDistributionModel");
        typeMap.put("RealParameter", "beast.base.inference.parameter.RealParameter");
        typeMap.put("Prior", "beast.base.inference.distribution.Prior");
        typeMap.put("YuleModel", "beast.base.evolution.speciation.YuleModel");
        typeMap.put("Tree", "beast.base.evolution.tree.Tree");
        typeMap.put("Alignment", "beast.base.evolution.alignment.Alignment");
        typeMap.put("TreeLikelihood", "beast.base.evolution.likelihood.TreeLikelihood");
        typeMap.put("ParametricDistribution", "beast.base.inference.distribution.ParametricDistribution");
        
        return typeMap.getOrDefault(typeName, "beast.base." + typeName);
    }
}
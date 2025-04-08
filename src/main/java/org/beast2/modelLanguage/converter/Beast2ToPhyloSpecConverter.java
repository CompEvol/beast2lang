package org.beast2.modelLanguage.converter;

import org.beast2.modelLanguage.model.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Converter that transforms a Beast2Model into a PhyloSpec/ModelPhy representation
 */
public class Beast2ToPhyloSpecConverter implements ModelVisitor<JSONObject> {
    
    /**
     * Convert a Beast2Model to a PhyloSpec/ModelPhy JSON representation
     * 
     * @param model the Beast2Model to convert
     * @return a JSONObject containing the PhyloSpec representation
     */
    public JSONObject convert(Beast2Model model) {
        return model.accept(this);
    }
    
    @Override
    public JSONObject visit(Beast2Model model) {
        JSONObject phyloSpec = new JSONObject();
        JSONObject modelObj = new JSONObject();
        JSONArray components = new JSONArray();
        
        // Set model metadata
        modelObj.put("id", "generated_model");
        modelObj.put("type", "phylogenetic");
        
        // Process each statement in the model
        for (Statement statement : model.getStatements()) {
            components.put(statement.accept(this));
        }
        
        // Add components to the model
        modelObj.put("components", components);
        
        // Add the model to the top-level object
        phyloSpec.put("model", modelObj);
        
        return phyloSpec;
    }
    
    @Override
    public JSONObject visit(VariableDeclaration varDecl) {
        JSONObject component = new JSONObject();
        
        // Set component properties
        component.put("id", varDecl.getVariableName());
        component.put("type", getSimpleTypeName(varDecl.getClassName()));
        component.put("value", varDecl.getValue().accept(this));
        
        return component;
    }
    
    @Override
    public JSONObject visit(DistributionAssignment distAssign) {
        JSONObject component = new JSONObject();
        
        // Set component properties
        component.put("id", distAssign.getVariableName());
        component.put("type", getSimpleTypeName(distAssign.getClassName()));
        component.put("distribution", distAssign.getDistribution().accept(this));
        
        return component;
    }
    
    @Override
    public JSONObject visit(FunctionCall funcCall) {
        JSONObject function = new JSONObject();
        JSONObject parameters = new JSONObject();
        
        // Set function type
        function.put("type", getSimpleTypeName(funcCall.getClassName()));
        
        // Process function arguments
        for (Argument arg : funcCall.getArguments()) {
            parameters.put(arg.getName(), arg.getValue().accept(this));
        }
        
        // Add parameters to the function
        function.put("parameters", parameters);
        
        return function;
    }
    
    @Override
    public JSONObject visit(Identifier identifier) {
        JSONObject ref = new JSONObject();
        ref.put("ref", identifier.getName());
        return ref;
    }
    
    @Override
    public JSONObject visit(Literal literal) {
        // For literals, just return the value directly
        Object value = literal.getValue();
        
        // If it's a string, need to wrap it in a JSONObject to maintain type info
        if (literal.getType() == Literal.LiteralType.STRING) {
            JSONObject stringObj = new JSONObject();
            stringObj.put("value", value);
            stringObj.put("type", "string");
            return stringObj;
        }
        
        // Numbers and booleans can be directly included in JSON
        return new JSONObject().put("value", value);
    }
    
    /**
     * Extract a simple type name from a fully qualified class name
     * 
     * @param className the fully qualified class name
     * @return the simple type name
     */
    private String getSimpleTypeName(String className) {
        String[] parts = className.split("\\.");
        return parts[parts.length - 1];
    }
}
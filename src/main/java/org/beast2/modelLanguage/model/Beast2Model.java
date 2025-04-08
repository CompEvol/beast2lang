package org.beast2.modelLanguage.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a complete Beast2 model
 */
public class Beast2Model {
    private List<Statement> statements;
    private Map<String, ModelNode> nodeMap;
    
    public Beast2Model() {
        statements = new ArrayList<>();
        nodeMap = new HashMap<>();
    }
    
    public void addStatement(Statement statement) {
        statements.add(statement);
        
        // Register the statement's node in the node map
        if (statement instanceof VariableDeclaration) {
            VariableDeclaration varDecl = (VariableDeclaration) statement;
            nodeMap.put(varDecl.getVariableName(), varDecl);
        } else if (statement instanceof DistributionAssignment) {
            DistributionAssignment distAssign = (DistributionAssignment) statement;
            nodeMap.put(distAssign.getVariableName(), distAssign);
        }
    }
    
    public List<Statement> getStatements() {
        return statements;
    }
    
    public ModelNode getNodeById(String id) {
        return nodeMap.get(id);
    }
    
    /**
     * Accept method for the visitor pattern
     */
    public <T> T accept(ModelVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
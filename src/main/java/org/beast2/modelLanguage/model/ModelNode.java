package org.beast2.modelLanguage.model;

/**
 * Abstract base class for all model nodes in the Beast2 model definition
 */
public abstract class ModelNode {
    private String id;
    
    public ModelNode(String id) {
        this.id = id;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    /**
     * Accept method for the visitor pattern
     */
    public abstract <T> T accept(ModelVisitor<T> visitor);
}

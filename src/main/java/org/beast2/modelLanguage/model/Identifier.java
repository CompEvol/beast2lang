package org.beast2.modelLanguage.model;

/**
 * Represents a reference to a previously defined variable
 */
public class Identifier extends Expression {
    private String name;
    
    public Identifier(String name) {
        super(name);
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    @Override
    public <T> T accept(ModelVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return name;
    }
}



package org.beast2.modelLanguage.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a literal value (number, string, boolean)
 */
public class Literal extends Expression {
    private Object value;
    private LiteralType type;
    
    public enum LiteralType {
        INTEGER, FLOAT, STRING, BOOLEAN
    }
    
    public Literal(Object value, LiteralType type) {
        super(value.toString());
        this.value = value;
        this.type = type;
    }
    
    public Object getValue() {
        return value;
    }
    
    public LiteralType getType() {
        return type;
    }
    
    @Override
    public <T> T accept(ModelVisitor<T> visitor) {
        return visitor.visit(this);
    }
}

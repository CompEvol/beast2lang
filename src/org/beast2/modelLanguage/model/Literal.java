package org.beast2.modelLanguage.model;

/**
 * Represents a literal value (number, string, boolean)
 */
public class Literal extends Expression {
    private final Object value;
    private final LiteralType type;
    
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

    @Override
    public String toString() {
        if (value == null) {
            return "null";
        }

        return switch (type) {
            case STRING -> "\"" + value + "\"";
            default -> value.toString();
        };
    }
}

package org.beast2.modelLanguage.model;

/**
 * Represents a named argument in a function call
 */
public class Argument {
    private String name;
    private Expression value;
    
    public Argument(String name, Expression value) {
        this.name = name;
        this.value = value;
    }
    
    public String getName() {
        return name;
    }
    
    public Expression getValue() {
        return value;
    }

    @Override
    public String toString() {
        if (name != null && !name.isEmpty()) {
            return name + "=" + (value != null ? value.toString() : "null");
        } else {
            return value != null ? value.toString() : "null";
        }
    }
}

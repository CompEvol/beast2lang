package org.beast2.modelLanguage.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
}

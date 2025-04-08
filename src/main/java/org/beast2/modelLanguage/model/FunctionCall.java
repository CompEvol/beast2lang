package org.beast2.modelLanguage.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a function call expression
 * Example: beast.base.inference.distribution.LogNormalDistributionModel(M=1, S=1)
 */
public class FunctionCall extends Expression {
    private String className;
    private List<Argument> arguments;
    
    public FunctionCall(String className, List<Argument> arguments) {
        super(className);
        this.className = className;
        this.arguments = arguments;
    }
    
    public String getClassName() {
        return className;
    }
    
    public List<Argument> getArguments() {
        return arguments;
    }
    
    @Override
    public <T> T accept(ModelVisitor<T> visitor) {
        return visitor.visit(this);
    }
}

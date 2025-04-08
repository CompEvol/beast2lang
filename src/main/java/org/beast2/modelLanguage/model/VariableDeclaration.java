package org.beast2.modelLanguage.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a variable declaration with a value assignment
 * Example: beast.base.inference.distribution.ParametricDistribution lognorm = beast.base.inference.distribution.LogNormalDistributionModel(M=1, S=1);
 */
public class VariableDeclaration extends Statement {
    private String className;
    private String variableName;
    private Expression value;
    
    public VariableDeclaration(String className, String variableName, Expression value) {
        super(variableName);
        this.className = className;
        this.variableName = variableName;
        this.value = value;
    }
    
    public String getClassName() {
        return className;
    }
    
    public String getVariableName() {
        return variableName;
    }
    
    public Expression getValue() {
        return value;
    }
    
    @Override
    public <T> T accept(ModelVisitor<T> visitor) {
        return visitor.visit(this);
    }
}


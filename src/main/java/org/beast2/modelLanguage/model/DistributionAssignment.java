package org.beast2.modelLanguage.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a distribution assignment with the ~ operator
 * Example: beast.base.inference.parameter.RealParameter lambda ~ Prior(distr=lognorm);
 */
public class DistributionAssignment extends Statement {
    private String className;
    private String variableName;
    private Expression distribution;
    
    public DistributionAssignment(String className, String variableName, Expression distribution) {
        super(variableName);
        this.className = className;
        this.variableName = variableName;
        this.distribution = distribution;
    }
    
    public String getClassName() {
        return className;
    }
    
    public String getVariableName() {
        return variableName;
    }
    
    public Expression getDistribution() {
        return distribution;
    }
    
    @Override
    public <T> T accept(ModelVisitor<T> visitor) {
        return visitor.visit(this);
    }
}

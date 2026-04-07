package org.beast2.modelLanguage.model;

import java.util.List;

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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(className).append("(");

        boolean first = true;
        for (Argument arg : arguments) {
            if (!first) {
                sb.append(", ");
            }
            first = false;

            // Add parameter name if present
            if (arg.getName() != null && !arg.getName().isEmpty()) {
                sb.append(arg.getName()).append("=");
            }

            // Add parameter value
            Expression value = arg.getValue();
            if (value != null) {
                sb.append(value.toString());
            }
        }

        sb.append(")");
        return sb.toString();
    }

}


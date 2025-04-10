package org.beast2.modelLanguage.model;

/**
 * Represents a distribution assignment in a Beast2 model.
 */
public class DistributionAssignment implements Statement {
    private final String className;
    private final String variableName;
    private final Expression distribution;
    
    /**
     * Constructor for a distribution assignment
     * 
     * @param className the class name
     * @param variableName the variable name
     * @param distribution the distribution expression
     */
    public DistributionAssignment(String className, String variableName, Expression distribution) {
        this.className = className;
        this.variableName = variableName;
        this.distribution = distribution;
    }
    
    /**
     * Get the class name
     * 
     * @return the class name
     */
    public String getClassName() {
        return className;
    }
    
    /**
     * Get the variable name
     * 
     * @return the variable name
     */
    public String getVariableName() {
        return variableName;
    }
    
    /**
     * Get the distribution expression
     * 
     * @return the distribution expression
     */
    public Expression getDistribution() {
        return distribution;
    }
    
    /**
     * Accept a visitor for this statement
     * 
     * @param visitor the visitor to accept
     */
    @Override
    public void accept(StatementVisitor visitor) {
        visitor.visit(this);
    }
    
    /**
     * Create a string representation of this distribution assignment
     */
    @Override
    public String toString() {
        return className + " " + variableName + " ~ " + distribution + ";";
    }
}
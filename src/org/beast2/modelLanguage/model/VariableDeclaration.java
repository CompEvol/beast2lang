package org.beast2.modelLanguage.model;

/**
 * Represents a variable declaration in a Beast2 model.
 */
public class VariableDeclaration implements Statement {
    private final String className;
    private final String variableName;
    private final Expression value;
    
    /**
     * Constructor for a variable declaration
     * 
     * @param className the class name
     * @param variableName the variable name
     * @param value the expression value
     */
    public VariableDeclaration(String className, String variableName, Expression value) {
        this.className = className;
        this.variableName = variableName;
        this.value = value;
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
     * Get the value expression
     * 
     * @return the value expression
     */
    public Expression getValue() {
        return value;
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
     * Create a string representation of this variable declaration
     */
    @Override
    public String toString() {
        return className + " " + variableName + " = " + value + ";";
    }
}
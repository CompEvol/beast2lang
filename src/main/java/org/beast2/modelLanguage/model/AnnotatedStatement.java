package org.beast2.modelLanguage.model;

/**
 * Represents a statement with an annotation.
 */
public class AnnotatedStatement implements Statement {
    private final Annotation annotation;
    private final Statement statement;
    
    /**
     * Constructor for an annotated statement
     * 
     * @param annotation the annotation
     * @param statement the statement being annotated
     */
    public AnnotatedStatement(Annotation annotation, Statement statement) {
        this.annotation = annotation;
        this.statement = statement;
    }
    
    /**
     * Get the annotation
     * 
     * @return the annotation
     */
    public Annotation getAnnotation() {
        return annotation;
    }
    
    /**
     * Get the statement being annotated
     * 
     * @return the statement
     */
    public Statement getStatement() {
        return statement;
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
     * Create a string representation of this annotated statement
     */
    @Override
    public String toString() {
        return annotation.toString() + "\n" + statement.toString();
    }
}
package org.beast2.modelLanguage.model;

import java.util.List;

/**
 * Represents a statement with an annotation.
 */
public class AnnotatedStatement implements Statement {
    private final List<Annotation> annotations;
    private final Statement statement;

    /**
     * Constructor for an annotated statement
     *
     * @param annotation the annotation
     * @param statement the statement being annotated
     */
    public AnnotatedStatement(Annotation annotation, Statement statement) {
        this.annotations = List.of(annotation);
        this.statement = statement;
    }

    /**
     * Constructor for an annotated statement
     * 
     * @param annotations the annotation
     * @param statement the statement being annotated
     */
    public AnnotatedStatement(List<Annotation> annotations, Statement statement) {
        this.annotations = annotations;
        this.statement = statement;
    }
    
    /**
     * Get the annotation
     * 
     * @return the annotation
     */
    public List<Annotation> getAnnotations() {
        return annotations;
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
        StringBuilder sb = new StringBuilder();
        // print each annotation on its own line
        for (Annotation ann : annotations) {
            sb.append(ann.toString())
                    .append(System.lineSeparator());
        }
        // then the statement
        sb.append(statement.toString());
        return sb.toString();
    }
}
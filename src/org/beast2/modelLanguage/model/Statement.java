package org.beast2.modelLanguage.model;

/**
 * Interface representing a statement in a Beast2 model.
 */
public interface Statement {
    /**
     * Accept a visitor for this statement
     * 
     * @param visitor the visitor to accept
     */
    void accept(StatementVisitor visitor);
}
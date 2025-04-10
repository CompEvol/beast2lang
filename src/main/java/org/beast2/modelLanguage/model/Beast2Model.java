package org.beast2.modelLanguage.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a complete Beast2 model.
 */
public class Beast2Model {
    private final List<ImportStatement> imports;
    private final List<Statement> statements;
    
    /**
     * Constructor for a Beast2 model
     */
    public Beast2Model() {
        this.imports = new ArrayList<>();
        this.statements = new ArrayList<>();
    }
    
    /**
     * Add an import statement to the model
     * 
     * @param importStatement the import statement to add
     */
    public void addImport(ImportStatement importStatement) {
        imports.add(importStatement);
    }
    
    /**
     * Add a statement to the model
     * 
     * @param statement the statement to add
     */
    public void addStatement(Statement statement) {
        statements.add(statement);
    }
    
    /**
     * Get all import statements in the model
     * 
     * @return an unmodifiable list of import statements
     */
    public List<ImportStatement> getImports() {
        return Collections.unmodifiableList(imports);
    }
    
    /**
     * Get all statements in the model
     * 
     * @return an unmodifiable list of statements
     */
    public List<Statement> getStatements() {
        return Collections.unmodifiableList(statements);
    }
    
    /**
     * Accept a visitor for this model
     * 
     * @param visitor the visitor to accept
     */
    public void accept(StatementVisitor visitor) {
        for (Statement statement : statements) {
            statement.accept(visitor);
        }
    }
    
    /**
     * Create a string representation of this model
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        // Add imports
        for (ImportStatement importStatement : imports) {
            sb.append(importStatement.toString()).append("\n");
        }
        
        if (!imports.isEmpty()) {
            sb.append("\n");
        }
        
        // Add statements
        for (Statement statement : statements) {
            sb.append(statement.toString()).append("\n");
        }
        
        return sb.toString();
    }
}
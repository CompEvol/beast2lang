package org.beast2.modelLanguage.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a complete Beast2 model.
 */
public class Beast2Model {
    private final List<ImportStatement> imports;
    private final List<RequiresStatement> requires;
    private final List<Statement> statements;
    private final List<Calibration> calibrations = new ArrayList<>();

    /**
     * Constructor for a Beast2 model
     */
    public Beast2Model() {
        this.imports = new ArrayList<>();
        this.requires = new ArrayList<>();
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
     * Add a requires statement to the model
     *
     * @param requiresStatement the "requires" statement to add
     */
    public void addRequires(RequiresStatement requiresStatement) {
        requires.add(requiresStatement);
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
     * Get all requires statements in the model
     *
     * @return an unmodifiable list of requires statements
     */
    public List<RequiresStatement> getRequires() {
        return Collections.unmodifiableList(requires);
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

        // Add requires
        for (RequiresStatement requiresStatement : requires) {
            sb.append(requiresStatement.toString()).append("\n");
        }

        if (!imports.isEmpty() || !requires.isEmpty()) {
            sb.append("\n");
        }

        // Add statements
        for (Statement statement : statements) {
            sb.append(statement.toString()).append("\n");
        }

        return sb.toString();
    }

    public void clearStatements() {
        statements.clear();
    }
}
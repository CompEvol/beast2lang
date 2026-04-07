package org.beast2.modelLanguage.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a call to the built-in alignment() function.
 * This function creates an alignment from inline sequence data.
 */
public class AlignmentFunction extends Expression {

    private final List<Argument> arguments;

    /**
     * Constructor
     *
     * @param arguments The arguments for the alignment function
     */
    public AlignmentFunction(List<Argument> arguments) {
        super("alignment");
        this.arguments = arguments != null ? new ArrayList<>(arguments) : new ArrayList<>();
    }

    /**
     * Get the arguments for the alignment function.
     *
     * @return The arguments
     */
    public List<Argument> getArguments() {
        return new ArrayList<>(arguments);
    }

    /**
     * Accept a visitor
     */
    @Override
    public <T> T accept(ModelVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("alignment(");

        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(arguments.get(i).toString());
        }

        sb.append(")");
        return sb.toString();
    }
}
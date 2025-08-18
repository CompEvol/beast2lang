package org.beast2.modelLanguage.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a call to the built-in nexus() function.
 * This function loads an alignment from a Nexus file.
 */
public class NexusFunction extends Expression {

    private final List<Argument> arguments;

    /**
     * Constructor
     *
     * @param arguments The arguments for the nexus function
     */
    public NexusFunction(List<Argument> arguments) {
        super("nexus");
        this.arguments = arguments != null ? new ArrayList<>(arguments) : new ArrayList<>();
    }

    public NexusFunction(String file) {
        this(List.of(new Argument("file", new Literal(file, Literal.LiteralType.STRING))));
    }

    /**
     * Get the arguments for the nexus function.
     *
     * @return The arguments
     */
    public List<Argument> getArguments() {
        return arguments;
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
        sb.append("nexus(");

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
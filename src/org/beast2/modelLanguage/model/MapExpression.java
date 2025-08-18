package org.beast2.modelLanguage.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a map expression in Beast2Lang, e.g., {key1: value1, key2: value2}
 */
public class MapExpression extends Expression {
    private final Map<String, Expression> entries;

    public MapExpression(Map<String, Expression> entries) {
        super("map");
        // Use LinkedHashMap to preserve order
        this.entries = new LinkedHashMap<>(entries);
    }

    public Map<String, Expression> getEntries() {
        return new LinkedHashMap<>(entries);
    }

    /**
     * Convert this map expression back to source code representation
     */
    public String toSourceCode() {
        if (entries.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Expression> entry : entries.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;

            sb.append(entry.getKey()).append(": ");

            // Handle different expression types
            Expression value = entry.getValue();
            if (value instanceof Literal) {
                Literal lit = (Literal) value;
                if (lit.getType() == Literal.LiteralType.STRING) {
                    sb.append("\"").append(lit.getValue()).append("\"");
                } else {
                    sb.append(lit.getValue());
                }
            } else if (value instanceof Identifier) {
                sb.append(((Identifier) value).getName());
            } else if (value instanceof FunctionCall) {
                sb.append(value.toString());
            } else {
                sb.append(value.toString());
            }
        }

        sb.append("}");
        return sb.toString();
    }

    @Override
    public <T> T accept(ModelVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return toSourceCode();
    }
}
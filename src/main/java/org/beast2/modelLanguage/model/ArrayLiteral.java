package org.beast2.modelLanguage.model;

import java.util.List;

/**
 * Represents an array literal expression
 */
public class ArrayLiteral extends Expression {
    private final List<Expression> elements;

    public ArrayLiteral(List<Expression> elements) {
        super(elementsToString(elements));
        this.elements = elements;
    }

    public List<Expression> getElements() {
        return elements;
    }

    private static String elementsToString(List<Expression> elements) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(elements.get(i).toString());
        }

        sb.append("]");
        return sb.toString();
    }

    @Override
    public <T> T accept(ModelVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
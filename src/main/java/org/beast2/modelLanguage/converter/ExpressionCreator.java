package org.beast2.modelLanguage.converter;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.inference.StateNode;
import beast.base.inference.parameter.RealParameter;
import beast.base.inference.distribution.Prior;
import org.beast2.modelLanguage.builder.ModelObjectFactory;
import org.beast2.modelLanguage.model.*;

import java.util.*;

/**
 * Handles creation of expressions from BEAST objects
 */
public class ExpressionCreator {

    private final Map<BEASTInterface, String> objectToIdMap;
    private final StatementCreator statementCreator;

    public ExpressionCreator(Map<BEASTInterface, String> objectToIdMap,
                             StatementCreator statementCreator) {
        this.objectToIdMap = objectToIdMap;
        this.statementCreator = statementCreator;
    }

    /**
     * Create an expression for a BEAST object
     */
    public Expression createExpressionForObject(BEASTInterface obj) {
        // For RealParameter with generic IDs, return the actual value
        if (obj instanceof RealParameter && obj.getID() != null && obj.getID().matches("RealParameter\\d+")) {
            RealParameter param = (RealParameter) obj;
            if (param.getDimension() == 1) {
                return new Literal(param.getValue(), Literal.LiteralType.FLOAT);
            } else {
                // Return array of values
                List<Expression> values = new ArrayList<>();
                for (int i = 0; i < param.getDimension(); i++) {
                    values.add(new Literal(param.getValue(i), Literal.LiteralType.FLOAT));
                }
                return new ArrayLiteral(values);
            }
        }

        // Otherwise create function call
        List<Argument> args = createArgumentsForObject(obj, new HashSet<>(), null);
        return new FunctionCall(obj.getClass().getSimpleName(), args);
    }

    /**
     * Create arguments for a function call representing a BEAST object
     */
    public List<Argument> createArgumentsForObject(BEASTInterface obj,
                                                   Set<BEASTInterface> usedDistributions,
                                                   ModelObjectFactory objectFactory) {
        List<Argument> args = new ArrayList<>();

        // If this is a distribution being used in a ~ statement, get its primary input name
        String primaryInputName = null;
        if (objectFactory != null && objectFactory.isDistribution(obj) && usedDistributions.contains(obj)) {
            primaryInputName = objectFactory.getPrimaryInputName(obj);
        }

        for (Input<?> input : obj.getInputs().values()) {
            // Skip empty inputs
            if (input.get() == null || (input.get() instanceof List && ((List<?>) input.get()).isEmpty())) {
                continue;
            }

            // Skip inputs we should filter out
            if (InputValidator.shouldSkipInput(obj, input)) {
                continue;
            }

            String inputName = input.getName();

            // Skip the primary input for distributions used in ~ statements
            if (primaryInputName != null && inputName.equals(primaryInputName)) {
                continue;
            }

            Expression value = createExpressionForInput(input);
            if (value != null) {
                args.add(new Argument(inputName, value));
            }
        }

        return args;
    }

    /**
     * Create an expression for an input value
     */
    public Expression createExpressionForInput(Input<?> input) {
        Object value = input.get();

        if (value instanceof BEASTInterface) {
            BEASTInterface obj = (BEASTInterface) value;
            if (objectToIdMap.containsKey(obj)) {
                return new Identifier(objectToIdMap.get(obj));
            } else {
                return createExpressionForObject(obj);
            }
        } else if (value instanceof List) {
            List<Expression> elements = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item instanceof BEASTInterface) {
                    BEASTInterface listObj = (BEASTInterface) item;
                    if (objectToIdMap.containsKey(listObj)) {
                        elements.add(new Identifier(objectToIdMap.get(listObj)));
                    } else {
                        elements.add(createExpressionForObject(listObj));
                    }
                } else {
                    elements.add(createLiteralForValue(item));
                }
            }
            return new ArrayLiteral(elements);
        } else {
            return createLiteralForValue(value);
        }
    }

    /**
     * Create a literal expression for a primitive value
     */
    private Literal createLiteralForValue(Object value) {
        if (value == null) {
            return new Literal("null", Literal.LiteralType.STRING);
        } else if (value instanceof Double || value instanceof Float) {
            return new Literal(value, Literal.LiteralType.FLOAT);
        } else if (value instanceof Integer || value instanceof Long) {
            return new Literal(value, Literal.LiteralType.INTEGER);
        } else if (value instanceof Boolean) {
            return new Literal(value, Literal.LiteralType.BOOLEAN);
        } else {
            return new Literal(value.toString(), Literal.LiteralType.STRING);
        }
    }
}
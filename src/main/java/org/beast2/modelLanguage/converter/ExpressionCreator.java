package org.beast2.modelLanguage.converter;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.evolution.alignment.TaxonSet;
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
     * Create a proper expression for TaxonSet with taxon array
     */
    private Expression createTaxonSetExpression(BEASTInterface obj) {
        if (!(obj instanceof TaxonSet)) {
            return createExpressionForObject(obj);
        }

        TaxonSet taxonSet = (TaxonSet) obj;
        List<Argument> args = new ArrayList<>();

        // Get the taxon list from the TaxonSet
        List<beast.base.evolution.alignment.Taxon> taxa = taxonSet.taxonsetInput.get();

        if (taxa != null && !taxa.isEmpty()) {
            // Create a string array of taxon names for autoboxing
            List<Literal> taxonNames = new ArrayList<>();
            for (beast.base.evolution.alignment.Taxon taxon : taxa) {
                String taxonName = taxon.getID();
                if (taxonName != null) {
                    taxonNames.add(new Literal(taxonName, Literal.LiteralType.STRING));
                }
            }

            if (!taxonNames.isEmpty()) {
                ArrayLiteral taxonArray = new ArrayLiteral(taxonNames.stream()
                        .map(lit -> (Expression) lit)
                        .collect(java.util.stream.Collectors.toList()));
                args.add(new Argument("taxon", taxonArray));
            }
        }

        // Add other non-taxon inputs
        for (Input<?> input : obj.getInputs().values()) {
            if (input.getName().equals("taxonset")) {
                continue; // Skip the taxonset input, we handled it above
            }

            if (input.get() == null || (input.get() instanceof List && ((List<?>) input.get()).isEmpty())) {
                continue;
            }

            if (InputValidator.shouldSkipInput(obj, input)) {
                continue;
            }

            Expression value = createExpressionForInput(input);
            if (value != null) {
                args.add(new Argument(input.getName(), value));
            }
        }

        return new FunctionCall("TaxonSet", args);
    }

    public Expression createExpressionForObject(BEASTInterface obj) {
        // Handle TaxonSet specially
        if (obj instanceof TaxonSet) {
            return createTaxonSetExpression(obj);
        }

        // FIXED: For RealParameter with generic IDs or fixed parameters, return the actual value
        if (obj instanceof RealParameter) {
            RealParameter param = (RealParameter) obj;

            // Check if this is a fixed parameter (not estimated)
            if (isFixedParameter(param)) {
                if (param.getDimension() == 1) {
                    return new Literal(param.getValue(), Literal.LiteralType.FLOAT);
                } else {
                    // Return array of values for multi-dimensional parameters
                    List<Expression> values = new ArrayList<>();
                    for (int i = 0; i < param.getDimension(); i++) {
                        values.add(new Literal(param.getValue(i), Literal.LiteralType.FLOAT));
                    }
                    return new ArrayLiteral(values);
                }
            }

            // For generic IDs like "RealParameter1", "RealParameter10", etc., also return literal values
            if (param.getID() != null && param.getID().matches("RealParameter\\d*")) {
                if (param.getDimension() == 1) {
                    return new Literal(param.getValue(), Literal.LiteralType.FLOAT);
                } else {
                    List<Expression> values = new ArrayList<>();
                    for (int i = 0; i < param.getDimension(); i++) {
                        values.add(new Literal(param.getValue(i), Literal.LiteralType.FLOAT));
                    }
                    return new ArrayLiteral(values);
                }
            }
        }

        // Otherwise create function call
        List<Argument> args = createArgumentsForObject(obj, new HashSet<>(), null);
        return new FunctionCall(obj.getClass().getSimpleName(), args);
    }
    
    private boolean isFixedParameter(RealParameter param) {
        // Check if this parameter is in the state (estimated parameters)
        if (statementCreator != null) {
            // Delegate to the statement creator's logic for checking fixed parameters
            return statementCreator.isParameterFixed(param);
        }

        // Fallback: check if estimate attribute is false
        try {
            // Try to get the estimate input if it exists
            for (Input<?> input : param.getInputs().values()) {
                if ("estimate".equals(input.getName()) && input.get() instanceof Boolean) {
                    return !(Boolean) input.get();
                }
            }
        } catch (Exception e) {
            // Ignore exceptions
        }

        // Default: assume it's fixed if it has a generic ID
        return param.getID() != null && param.getID().matches("RealParameter\\d*");
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

            // FIXED: Check if this is a fixed parameter that should be inlined as literal
            if (obj instanceof RealParameter) {
                RealParameter param = (RealParameter) obj;
                if (isFixedParameter(param)) {
                    // Return the literal value instead of an identifier
                    if (param.getDimension() == 1) {
                        return new Literal(param.getValue(), Literal.LiteralType.FLOAT);
                    } else {
                        // Return array of values for multi-dimensional parameters
                        List<Expression> values = new ArrayList<>();
                        for (int i = 0; i < param.getDimension(); i++) {
                            values.add(new Literal(param.getValue(i), Literal.LiteralType.FLOAT));
                        }
                        return new ArrayLiteral(values);
                    }
                }
            }

            // For non-fixed parameters, check if we have an identifier, otherwise create expression
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

                    // FIXED: Check if list item is a fixed parameter
                    if (listObj instanceof RealParameter) {
                        RealParameter param = (RealParameter) listObj;
                        if (isFixedParameter(param)) {
                            // Return the literal value instead of an identifier
                            if (param.getDimension() == 1) {
                                elements.add(new Literal(param.getValue(), Literal.LiteralType.FLOAT));
                            } else {
                                // For multi-dimensional, add individual values
                                for (int i = 0; i < param.getDimension(); i++) {
                                    elements.add(new Literal(param.getValue(i), Literal.LiteralType.FLOAT));
                                }
                            }
                            continue;
                        }
                    }

                    // For non-fixed parameters
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
package org.beast2.modelLanguage.operators;

import beast.base.evolution.tree.Tree;
import beast.base.inference.Operator;
import beast.base.inference.StateNode;
import beast.base.inference.operator.kernel.BactrianDeltaExchangeOperator;
import beast.base.inference.operator.kernel.BactrianUpDownOperator;
import beast.base.inference.parameter.Parameter;
import org.beast2.modelLanguage.beast.Beast2AnalysisBuilder;

import java.util.List;

import static org.beast2.modelLanguage.beast.BEASTObjectID.*;
import static org.beast2.modelLanguage.operators.MCMCOperator.getOperatorWeight;


/**
 * A class to create all default operators
 * @author Walter Xie
 * @author Alexei Drommand
 */
public class ExtraOperator implements MCMCOperator<List<StateNode>> {

    private final Beast2AnalysisBuilder builder;

    /**
     * @param builder               passing all configurations
     */
    public ExtraOperator(Beast2AnalysisBuilder builder) {
        this.builder = builder;
    }

    /**
     * create and add {@link Operator} for List<StateNode>.
     */
    @Override
    public void addOperators(List<StateNode> stateNodes) {

        String paramID = MCMCOperator.getStateNodeID(stateNodes);
        // Skip if we've already created operators for this parameter
        if (!builder.hasOperators(paramID + ".UpDownOperator") && stateNodes.size() == 2
                && stateNodes.get(0) instanceof Parameter clockRate
                    && stateNodes.get(1) instanceof Tree tree) {
            // TODO only working for a pair of clockRate and Tree
            Operator operator = getUpDownOperator(clockRate, tree);
            builder.addOperator(paramID + ".UpDownOperator", operator);
        }

        // this is for relative rates
        if (!builder.hasOperators(paramID + ".DeltaExchange")) {
            List<Parameter> parameterList = stateNodes.stream()
                    .filter(p -> p instanceof Parameter)
                    .map(p -> (Parameter) p)
                    .toList();
            if (parameterList.size() > 1) {
                Operator operator = getDeltaExchangeOperator(parameterList);
                builder.addOperator(paramID + ".DeltaExchange", operator);

                //TODO need to remove their ScaleOperator
            }
        }

    }


    // when both clockRate and tree are random var
    protected Operator getUpDownOperator(Parameter clockRate, Tree tree) {
        Operator upDownOperator = new BactrianUpDownOperator();
        upDownOperator.setInputValue("up", clockRate);
        upDownOperator.setInputValue("down", tree);
        upDownOperator.setInputValue(SCALE_FACTOR, 0.9);
        upDownOperator.setInputValue(INPUT_WEIGHT, getOperatorWeight(tree.getInternalNodeCount() + 1));
        upDownOperator.initAndValidate();

        String idStr = clockRate.getID() + "." + tree.getID() + ".UpDownOperator";
        upDownOperator.setID(idStr);
        return upDownOperator;
    }

    // this is for relative rates
    protected Operator getDeltaExchangeOperator(List<Parameter> rates) {

        BactrianDeltaExchangeOperator deltaOperator = new BactrianDeltaExchangeOperator();
        deltaOperator.initByName(INPUT_PARAMETER, rates,
                INPUT_WEIGHT, getOperatorWeight(rates.size() - 1),
                "delta", 1.0 / rates.size());

        String paramID = MCMCOperator.getParamID(rates);
        deltaOperator.setID(paramID + ".DeltaExchange");

        return deltaOperator;
    }

}

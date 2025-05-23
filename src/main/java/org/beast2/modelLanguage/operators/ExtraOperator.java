package org.beast2.modelLanguage.operators;

import beast.base.evolution.tree.Tree;
import beast.base.inference.Operator;
import beast.base.inference.StateNode;
import beast.base.inference.operator.kernel.BactrianUpDownOperator;
import beast.base.inference.parameter.Parameter;
import org.beast2.modelLanguage.beast.Beast2AnalysisBuilder;

import java.util.List;
import java.util.stream.Collectors;

import static org.beast2.modelLanguage.beast.BEASTObjectID.INPUT_WEIGHT;
import static org.beast2.modelLanguage.beast.BEASTObjectID.SCALE_FACTOR;
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

        String paramID = stateNodes.stream()
                .map(StateNode::getID)       // get the ID from each node
                .collect(Collectors.joining("."));
        // Skip if we've already created operators for this parameter
        if (!builder.hasOperators(paramID + ".UpDownOperator")) {
            // TODO only working for a pair of clockRate and Tree
            if (stateNodes.size() == 2 && stateNodes.get(0) instanceof Parameter clockRate
                    && stateNodes.get(1) instanceof Tree tree) {
                Operator operator = getUpDownOperator(clockRate, tree);
                builder.addOperator(paramID + ".UpDownOperator", operator);
            } else
                throw new UnsupportedOperationException("Up-down operator for more than two StateNode is not supported !");
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

}

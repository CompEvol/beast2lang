package org.beast2.modelLanguage.operators;

import beast.base.inference.StateNode;
import beast.base.inference.parameter.Parameter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Defines a family of parameter operators
 * which will create in {@link DefaultParameterOperator}.
 * @author Walter Xie
 */
public interface MCMCOperator<T> {

    /**
     * add operators for that state node
     */
    void addOperators(T input);

    static double getOperatorWeight(int size) {
        return getOperatorWeight(size, 0.7);
    }

    static double getOperatorWeight(int size, double pow) {
        return Math.pow(size, pow);
    }

    static String getStateNodeID(List<? extends StateNode> stateNodes) {
        return stateNodes.stream()
                .map(StateNode::getID)       // get the ID from each node
                .collect(Collectors.joining("."));
    }

    static String getParamID(List<? extends Parameter> parameters) {
        return parameters.stream()
                .map(Parameter::getID)       // get the ID from each node
                .collect(Collectors.joining("."));
    }

}

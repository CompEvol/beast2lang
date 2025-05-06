package org.beast2.modelLanguage.operators;

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

}

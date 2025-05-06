package org.beast2.modelLanguage.operators;

import beast.base.evolution.operator.kernel.BactrianScaleOperator;
import beast.base.inference.Operator;
import beast.base.inference.operator.BitFlipOperator;
import beast.base.inference.operator.IntRandomWalkOperator;
import beast.base.inference.operator.kernel.BactrianDeltaExchangeOperator;
import beast.base.inference.operator.kernel.BactrianRandomWalkOperator;
import beast.base.inference.parameter.BooleanParameter;
import beast.base.inference.parameter.IntegerParameter;
import beast.base.inference.parameter.Parameter;
import beast.base.inference.parameter.RealParameter;
import org.beast2.modelLanguage.builder.Beast2AnalysisBuilder;

import static org.beast2.modelLanguage.BEASTObjectID.*;
import static org.beast2.modelLanguage.operators.MCMCOperator.getOperatorWeight;


/**
 * A class to create parameter operators
 * @author Walter Xie
 * @author Alexei Drommand
 */
public class DefaultParameterOperator implements MCMCOperator<Parameter> {

    private final Beast2AnalysisBuilder builder;

    /**
     * @param builder               passing all configurations
     */
    public DefaultParameterOperator(Beast2AnalysisBuilder builder) {
        this.builder = builder;
    }

    /**
     * create and add {@link Operator} for {@link Parameter}.
     */
    @Override
    public void addOperators(Parameter param) {
        String paramID = param.getID();
        // Skip if we've already created operators for this parameter
        if (!builder.hasOperators(paramID + "Operator")) {
            Operator operator = null;
            try {
                if (param.getDimension() > 1) {
                    // TODO DeltaExchange should be added only if the distribution is a Dirichlet
                    // Use Delta Exchange operator for multidimensional parameters
                    operator = getDeltaExchangeOperator(param);

                } else if (param instanceof BooleanParameter booleanParam) {
                    operator = getBitFlipOperator(booleanParam);

                } else if (param instanceof IntegerParameter intParam) {
//                    if (intParam.getLower() < 0)
                    operator = getIntRandomWalkOperator(intParam);
//                    else
//                        operator = getScaleOperator(intParam);
//TODO Integer ScaleOperator ?

                } else if (param instanceof RealParameter realParam) {
                    if (realParam.getLower() < 0)
                        // any distribution with support in negative values, e.g. Normal, Laplace.
                        operator = getRandomWalkOperator(realParam);
                    else
                        operator = getScaleOperator(realParam);
                }

                //TODO with default or without ?
            } catch (Exception e) {
                builder.warning("Could not create operator for " + paramID + ": " + e.getMessage());
            }

            if (operator != null)
                builder.addOperator(paramID + "Operator", operator);
            else
                builder.warning("Could not create operator for " + paramID);
        }
    }

    //*** methods to create operators ***//

    protected Operator getScaleOperator(RealParameter param) {
        Operator operator = new BactrianScaleOperator();
        operator.setInputValue(INPUT_PARAMETER, param);
        operator.setInputValue(INPUT_WEIGHT, getOperatorWeight(param.getDimension()));
        operator.setInputValue(SCALE_FACTOR, 0.75);
        operator.initAndValidate();
        operator.setID(param.getID() + ".scale");
        builder.fine("Added BactrianScaleOperator for " + param.getID());
        return operator;
    }

    protected Operator getRandomWalkOperator(RealParameter param) {
        Operator operator = new BactrianRandomWalkOperator();
        operator.setInputValue(INPUT_PARAMETER, param);
        operator.setInputValue(INPUT_WEIGHT, getOperatorWeight(param.getDimension()));
        operator.setInputValue(SCALE_FACTOR, 0.75);
        operator.initAndValidate();
        operator.setID(param.getID() + ".randomWalk");
        builder.fine("Added BactrianRandomWalkOperator for " + param.getID());
        return operator;
    }

    protected Operator getIntRandomWalkOperator(IntegerParameter param) {
        Operator operator = new IntRandomWalkOperator();
        operator.setInputValue(INPUT_PARAMETER, param);
        operator.setInputValue(INPUT_WEIGHT, getOperatorWeight(param.getDimension()));
        // TODO implement an optimizable int random walk that uses a reflected Poisson distribution for the jump size with the mean of the Poisson being the optimizable parameter
        operator.setInputValue(WINDOW_SIZE, 1);
        operator.initAndValidate();
        operator.setID(param.getID() + ".randomWalk");
        builder.fine("Added IntRandomWalkOperator for " + param.getID());
        return operator;
    }

    protected Operator getDeltaExchangeOperator(Parameter param) {
        BactrianDeltaExchangeOperator deltaOperator = new BactrianDeltaExchangeOperator();
        deltaOperator.initByName(INPUT_PARAMETER, param,
                INPUT_WEIGHT, getOperatorWeight(param.getDimension() - 1),
                "delta", 1.0 / param.getDimension());
        deltaOperator.setID(param.getID() + ".deltaExchange");
        builder.fine("Added BactrianDeltaExchangeOperator for " + param.getID());
        return deltaOperator;
    }

    protected Operator getBitFlipOperator(BooleanParameter param) {
        Operator operator = new BitFlipOperator();
        operator.setInputValue(INPUT_PARAMETER, param);
        operator.setInputValue(INPUT_WEIGHT, getOperatorWeight(param.getDimension()));
        operator.initAndValidate();
        operator.setID(param.getID() + ".bitFlip");
        builder.fine("Added BitFlipOperator for " + param.getID());
        return operator;
    }


}

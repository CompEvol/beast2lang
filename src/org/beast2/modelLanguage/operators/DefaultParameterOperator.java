package org.beast2.modelLanguage.operators;

import beast.base.core.BEASTInterface;
import beast.base.core.Function;
import beast.base.evolution.operator.kernel.BactrianScaleOperator;
import beast.base.inference.Operator;
import beast.base.inference.StateNode;
import beast.base.inference.distribution.ParametricDistribution;
import beast.base.inference.distribution.Prior;
import beast.base.inference.operator.BitFlipOperator;
import beast.base.inference.operator.IntRandomWalkOperator;
import beast.base.inference.operator.kernel.BactrianDeltaExchangeOperator;
import beast.base.inference.operator.kernel.BactrianRandomWalkOperator;
import beast.base.inference.parameter.BooleanParameter;
import beast.base.inference.parameter.IntegerParameter;
import beast.base.inference.parameter.Parameter;
import beast.base.inference.parameter.RealParameter;
import beastlabs.math.distributions.WeightedDirichlet;
import org.beast2.modelLanguage.beast.Beast2AnalysisBuilder;

import java.util.List;
import java.util.Set;

import static org.beast2.modelLanguage.beast.BEASTObjectID.*;
import static org.beast2.modelLanguage.operators.MCMCOperator.getOperatorWeight;


/**
 * A class to create parameter operators
 * @author Walter Xie
 * @author Alexei Drommand
 */
public class DefaultParameterOperator implements MCMCOperator<StateNode> {

    private final Beast2AnalysisBuilder builder;

    final List<StateNode> skipOperator;

    /**
     * @param builder               passing all configurations
     */
    public DefaultParameterOperator(Beast2AnalysisBuilder builder, List<StateNode> skipOperator) {
        this.builder = builder;
        this.skipOperator = skipOperator;
    }

    /**
     * create and add {@link Operator} for {@link Parameter}.
     */
    @Override
    public void addOperators(StateNode stateNode) {
        if (skipOperator.contains(stateNode)) return;

        String paramID = stateNode.getID();
        // Skip if we've already created operators for this parameter
        if (!builder.hasOperators(paramID + "Operator")) {
            Operator operator = null;
            try {
                if (stateNode.getDimension() > 1) {
                    // TODO DeltaExchange should be added only if the distribution is a Dirichlet
                    // Use Delta Exchange operator for multidimensional parameters
                    operator = getDeltaExchangeOperator(stateNode);

                } else if (stateNode instanceof BooleanParameter booleanParam) {
                    operator = getBitFlipOperator(booleanParam);

                } else if (stateNode instanceof IntegerParameter intParam) {
//                    if (intParam.getLower() < 0)
                    operator = getIntRandomWalkOperator(intParam);
//                    else
//                        operator = getScaleOperator(intParam);
//TODO Integer ScaleOperator ?

                } else if (stateNode instanceof RealParameter realParam) {
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

    protected Operator getDeltaExchangeOperator(StateNode stateNode) {
        BactrianDeltaExchangeOperator deltaOperator = new BactrianDeltaExchangeOperator();
        deltaOperator.setInputValue(INPUT_PARAMETER, stateNode);
        deltaOperator.setInputValue("delta", 1.0 / stateNode.getDimension());
        deltaOperator.setInputValue(INPUT_WEIGHT, getOperatorWeight(stateNode.getDimension() - 1) );
        // handle WeightedDirichlet with weight vector
        Set<BEASTInterface> outputs = stateNode.getOutputs();
        for (BEASTInterface output : outputs) {
            if (output instanceof Prior prior) {
                ParametricDistribution x = prior.distInput.get();
                if (x instanceof WeightedDirichlet weightedDirichlet) { // in BEASTLabs
// <weights id="L" spec="parameter.IntegerParameter" dimension="3" estimate="false">209 210 210</weights>
                    Function wL = weightedDirichlet.weightsInput.get();
                    if ( !(wL instanceof IntegerParameter) )
                        throw new IllegalArgumentException("WeightedDirichlet weights parameter must be an integer parameter !");
                    deltaOperator.setInputValue("weightvector", wL);

                    break;
                }
            }
        }
        deltaOperator.initAndValidate();
        deltaOperator.setID(stateNode.getID() + ".deltaExchange");
        builder.fine("Added BactrianDeltaExchangeOperator for " + stateNode.getID());
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

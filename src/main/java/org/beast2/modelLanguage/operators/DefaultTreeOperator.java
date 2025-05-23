package org.beast2.modelLanguage.operators;

import beast.base.evolution.operator.EpochFlexOperator;
import beast.base.evolution.operator.Exchange;
import beast.base.evolution.operator.TreeStretchOperator;
import beast.base.evolution.operator.WilsonBalding;
import beast.base.evolution.operator.kernel.BactrianNodeOperator;
import beast.base.evolution.operator.kernel.BactrianScaleOperator;
import beast.base.evolution.operator.kernel.BactrianSubtreeSlide;
import beast.base.evolution.tree.Tree;
import beast.base.inference.Operator;
import org.beast2.modelLanguage.beast.Beast2AnalysisBuilder;

import static org.beast2.modelLanguage.beast.BEASTObjectID.*;
import static org.beast2.modelLanguage.operators.MCMCOperator.getOperatorWeight;


/**
 * A class to create all default operators
 * @author Walter Xie
 * @author Alexei Drommand
 */
public class DefaultTreeOperator implements MCMCOperator<Tree> {

    private final Beast2AnalysisBuilder builder;

    /**
     * @param builder               passing all configurations
     */
    public DefaultTreeOperator(Beast2AnalysisBuilder builder) {
        this.builder = builder;
    }

    /**
     * create and add {@link Operator} for {@link Tree}.
     */
    @Override
    public void addOperators(Tree tree) {
        String treeID = tree.getID();

        // Skip if we've already created operators for this tree
        if (!builder.hasOperators(treeID + "RootHeightScaler")) {
            try {
                // default
                builder.addOperator(treeID + "RootHeightScaler", getRootHeightOperator(tree));
                builder.addOperator(treeID + "Uniform", getTreeUniformOperator(tree));

                builder.addOperator(treeID + "BICEPSEpochTop", getBICEPSEpochTop(tree));
                builder.addOperator(treeID + "BICEPSEpochAll", getBICEPSEpochAll(tree));
                builder.addOperator(treeID + "BICEPSTreeFlex", getBICEPSTreeFlex(tree));

                builder.addOperator(treeID + "SubtreeSlide", getSubtreeSlideOperator(tree));
                builder.addOperator(treeID + "NarrowExchange", getExchangeOperator(tree, true));
                builder.addOperator(treeID + "WideExchange", getExchangeOperator(tree, false));
                builder.addOperator(treeID + "WilsonBalding", getWilsonBaldingOperator(tree));

                builder.fine("Added 9 operators for tree " + treeID);
            } catch (Exception e) {
                builder.warning("Could not create operators for " + treeID + ": " + e.getMessage());
            }
        }

    }

    /**
     * Tree operators, attributes values are based on BEAUti template
     */

    protected Operator getRootHeightOperator(Tree tree) {
        Operator operator = new BactrianScaleOperator();
        operator.setInputValue(INPUT_TREE, tree);
        operator.setInputValue("rootOnly", true);
        operator.setInputValue(SCALE_FACTOR, 0.75);
        // set the upper of the scale factor
        operator.setInputValue("upper", 0.975);
        operator.setInputValue(INPUT_WEIGHT, getOperatorWeight(1));
        operator.initAndValidate();
        operator.setID(tree.getID() + "." + "rootAgeScale");
        return operator;
    }

    protected Operator getExchangeOperator(Tree tree, boolean isNarrow) {
        Operator exchange = new Exchange();
        exchange.setInputValue(INPUT_TREE, tree);
        double pow = (isNarrow) ? 0.7 : 0.2; // WideExchange size^0.2
        exchange.setInputValue(INPUT_WEIGHT, getOperatorWeight(tree.getInternalNodeCount(), pow));
        exchange.setInputValue("isNarrow", isNarrow);
        exchange.initAndValidate();
        exchange.setID(tree.getID() + "." + ((isNarrow) ? "narrow" : "wide") + "Exchange");
        return exchange;
    }

    protected Operator getTreeUniformOperator(Tree tree) {
        Operator uniform = new BactrianNodeOperator();
        uniform.setInputValue(INPUT_TREE, tree);
        uniform.setInputValue(INPUT_WEIGHT, getOperatorWeight(tree.getInternalNodeCount()));
        uniform.initAndValidate();
        uniform.setID(tree.getID() + "." + "uniform");
        return uniform;
    }

    protected Operator getSubtreeSlideOperator(Tree tree) {
        Operator subtreeSlide = new BactrianSubtreeSlide();
        subtreeSlide.setInputValue(INPUT_TREE, tree);
        subtreeSlide.setInputValue(INPUT_WEIGHT, getOperatorWeight(tree.getInternalNodeCount()));
        subtreeSlide.setInputValue("size", tree.getRoot().getHeight() / 10.0);
        subtreeSlide.initAndValidate();
        subtreeSlide.setID(tree.getID() + "." + "subtreeSlide");
        return subtreeSlide;
    }

    protected Operator getWilsonBaldingOperator(Tree tree) {
        Operator wilsonBalding = new WilsonBalding();
        wilsonBalding.setInputValue(INPUT_TREE, tree);
        wilsonBalding.setInputValue(INPUT_WEIGHT, getOperatorWeight(tree.getInternalNodeCount(), 0.2));
        wilsonBalding.initAndValidate();
        wilsonBalding.setID(tree.getID() + "." + "wilsonBalding");
        return wilsonBalding;
    }

    protected Operator getBICEPSEpochTop(Tree tree) {
        Operator operator = new EpochFlexOperator();
        operator.setInputValue(INPUT_TREE, tree);
        // weight="2.0" scaleFactor="0.1"
        operator.setInputValue(SCALE_FACTOR, 0.1);
        operator.setInputValue(INPUT_WEIGHT, getOperatorWeight(1));
        operator.initAndValidate();
        operator.setID(tree.getID() + "." + "BICEPSEpochTop");
        return operator;
    }

    protected Operator getBICEPSEpochAll(Tree tree) {
        Operator operator = new EpochFlexOperator();
        operator.setInputValue(INPUT_TREE, tree);
        // weight="2.0" scaleFactor="0.1" fromOldestTipOnly="false"
        operator.setInputValue(SCALE_FACTOR, 0.1);
        operator.setInputValue(INPUT_WEIGHT, getOperatorWeight(2)); // TODO check ?
        operator.setInputValue("fromOldestTipOnly", false);
        operator.initAndValidate();
        operator.setID(tree.getID() + "." + "BICEPSEpochAll");
        return operator;
    }

    protected Operator getBICEPSTreeFlex(Tree tree) {
        Operator operator = new TreeStretchOperator();;
        operator.setInputValue(INPUT_TREE, tree);
        // weight="2.0" scaleFactor="0.01"
        operator.setInputValue(SCALE_FACTOR, 0.01); // TODO used to be 0.75 ?
        operator.setInputValue(INPUT_WEIGHT, getOperatorWeight(tree.getInternalNodeCount()));
        operator.initAndValidate();
        operator.setID(tree.getID() + "." + "BICEPSTreeFlex");
        return operator;
    }

}

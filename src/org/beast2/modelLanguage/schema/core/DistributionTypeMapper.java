package org.beast2.modelLanguage.schema.core;

import beast.base.evolution.speciation.BirthDeathGernhard08Model;
import beast.base.evolution.speciation.YuleModel;
import beast.base.evolution.tree.MRCAPrior;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.coalescent.BayesianSkyline;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.inference.distribution.*;
import beast.base.inference.parameter.IntegerParameter;
import beast.base.inference.parameter.RealParameter;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps distribution classes to the types they generate.
 * This helps determine what type of parameter a distribution produces.
 */
public class DistributionTypeMapper {

    private static final Map<Class, Class> DISTRIBUTION_TO_GENERATED_TYPE = new HashMap<>();

    static {
        // Tree distributions
        DISTRIBUTION_TO_GENERATED_TYPE.put(YuleModel.class, Tree.class);
        DISTRIBUTION_TO_GENERATED_TYPE.put(BirthDeathGernhard08Model.class, Tree.class);
        DISTRIBUTION_TO_GENERATED_TYPE.put(Coalescent.class, Tree.class);
        DISTRIBUTION_TO_GENERATED_TYPE.put(BayesianSkyline.class, Tree.class);
        DISTRIBUTION_TO_GENERATED_TYPE.put(MRCAPrior.class, Tree.class);

        // RealParameter distributions
        DISTRIBUTION_TO_GENERATED_TYPE.put(Beta.class, RealParameter.class);
        DISTRIBUTION_TO_GENERATED_TYPE.put(LogNormalDistributionModel.class, RealParameter.class);
        DISTRIBUTION_TO_GENERATED_TYPE.put(Normal.class, RealParameter.class);
        DISTRIBUTION_TO_GENERATED_TYPE.put(Gamma.class, RealParameter.class);
        DISTRIBUTION_TO_GENERATED_TYPE.put(Dirichlet.class, RealParameter.class);
        DISTRIBUTION_TO_GENERATED_TYPE.put(Exponential.class, RealParameter.class);
        DISTRIBUTION_TO_GENERATED_TYPE.put(LaplaceDistribution.class, RealParameter.class);
        DISTRIBUTION_TO_GENERATED_TYPE.put(InverseGamma.class, RealParameter.class);
        DISTRIBUTION_TO_GENERATED_TYPE.put(Uniform.class, RealParameter.class);

        // IntegerParameter distributions
        DISTRIBUTION_TO_GENERATED_TYPE.put(Poisson.class, IntegerParameter.class);
    }

    /**
     * Get the generated type for a distribution
     * @param distribution the distribution or parametric distribution class
     * @return The type this distribution generates, or null if unknown
     */
    public static Class getGeneratedType(Class distribution) {
        return DISTRIBUTION_TO_GENERATED_TYPE.get(distribution);
    }
}
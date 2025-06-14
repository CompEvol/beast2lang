package org.beast2.modelLanguage.schema.core;

import java.util.Set;

/**
 * Configuration of known types for BEAST2 model library generation
 */
public class KnownTypes {
    
    public static final String[] IMPORTANT_BASE_TYPES = {
        "beast.base.core.BEASTInterface",
        "beast.base.core.BEASTObject",
        "beast.base.inference.Distribution",
        "beast.base.inference.distribution.ParametricDistribution",
        "beast.base.inference.StateNode",
        "beast.base.inference.CalculationNode",
        "beast.base.inference.StateNodeInitialiser",
        "beast.base.inference.parameter.GeneralParameterList",
        "beast.base.inference.parameter.Map",
        "beast.base.evolution.tree.TreeDistribution",
        "beast.base.evolution.speciation.SpeciesTreeDistribution",
        // Base implementations
        "beast.base.evolution.branchratemodel.BranchRateModel$Base",
        "beast.base.evolution.substitutionmodel.SubstitutionModel$Base",
        "beast.base.evolution.substitutionmodel.SubstitutionModel$NucleotideBase",
        "beast.base.evolution.substitutionmodel.EmpiricalSubstitutionModel",
        "beast.base.evolution.tree.coalescent.PopulationFunction$Abstract",
        "beast.base.evolution.datatype.DataType$Base",
        "beastlabs.evolution.speciation.UltrametricSpeciationModel",
        "sa.evolution.speciation.SABDParameterization",
        "feast.function.LoggableFunction",
        "feast.fileio.logfileiterator.LogFileState"
    };
    
    public static final String[] IMPORTANT_NON_BEAST_INTERFACES = {
        "beast.base.core.Function",
        "beast.base.evolution.sitemodel.SiteModelInterface",
        "beast.base.inference.parameter.Parameter",
        "starbeast3.evolution.branchratemodel.BranchRateModelSB3",
        // Tree interface
        "beast.base.evolution.tree.TreeInterface",
        // Model interfaces
        "beast.base.evolution.branchratemodel.BranchRateModel",
        "beast.base.evolution.substitutionmodel.SubstitutionModel",
        "beast.base.evolution.tree.coalescent.PopulationFunction",
        // Note: PopulationModel is in starbeast3, not beast.base
        "starbeast3.evolution.speciation.PopulationModel",
        // Distance and metrics
        "beast.base.evolution.distance.Distance",
        "beast.base.evolution.tree.TreeMetric",
        "beast.base.inference.operator.kernel.Transform",
        "beastlabs.util.Transform"
    };
    
    public static final String[] IMPORTANT_INNER_CLASSES = {
        "beast.base.evolution.sitemodel.SiteModelInterface$Base",
        "beast.base.inference.parameter.Parameter$Base",
        "beast.base.evolution.branchratemodel.BranchRateModel$Base",
        "beast.base.evolution.substitutionmodel.SubstitutionModel$Base",
        "beast.base.evolution.datatype.DataType$Base",
        "beastlabs.util.Transform$MultivariableTransform",
        "beastlabs.util.Transform$MultivariateTransform",
        "beastlabs.util.Transform$MultivariableTransformWithParameter",
        "beastlabs.util.Transform$UnivariableTransform",
        "beast.base.inference.operator.kernel.Transform$MultivariateTransform"
    };
    
    public static final String[] ENUMS = {
        "starbeast3.evolution.speciation.SpeciesTreePrior$TreePopSizeFunction",
        "beast.evolution.substitutionmodel.GeneralLazySubstitutionModel$RelaxationMode",
        "beast.evolution.substitutionmodel.LazyHKY$RelaxationMode", 
        "beast.evolution.tree.ClusterTree$Type",
        "beast.evolution.tree.ConstrainedClusterTree$Type"
    };
    
    public static final Set<String> PACKAGE_SEARCH_PREFIXES = Set.of(
        "beast.base.evolution.substitutionmodel.",
        "beast.base.evolution.tree.",
        "beast.base.evolution.speciation.",
        "beast.base.evolution.datatype.",
        "beast.base.evolution.distance.",
        "beast.base.evolution.likelihood.",
        "beast.base.inference.",
        "beast.base.inference.distribution.",
        "beast.base.inference.parameter.",
        "beast.base.inference.util.",
        "beast.base.core.",
        "starbeast3.evolution.",
        "starbeast3.tree.",
        "starbeast3.core.",
        "beast.pkgmgmt.",
        "beast.app.seqgen.",
        "beastlabs.evolution.tree.",
        "beastlabs.math.distributions."
    );
}
package org.beast2.modelLanguage.phylospec;

import java.util.*;
import java.util.logging.Logger;

/**
 * Maps PhyloSpec names, types, and parameters to their BEAST2 equivalents
 */
public class PhyloSpecMapper {
    private static final Logger logger = Logger.getLogger(PhyloSpecMapper.class.getName());

    // Distribution mappings (PhyloSpec -> fully qualified BEAST2 class)
    private static final Map<String, String> DISTRIBUTION_MAP = new HashMap<>();

    // Parameter name mappings (for parameters with different names)
    private static final Map<String, String> PARAMETER_MAP = new HashMap<>();

    // Type mappings (PhyloSpec -> fully qualified BEAST2 class)
    private static final Map<String, String> TYPE_MAP = new HashMap<>();

    // Set of distributions that map to ParametricDistribution
    private static final Set<String> PARAMETRIC_DISTRIBUTIONS = new HashSet<>();

    // Set of distributions that produce integers rather than reals
    private static final Set<String> INTEGER_DISTRIBUTIONS = new HashSet<>();

    // Initialize mapping tables
    static {
        // Standard distributions
        DISTRIBUTION_MAP.put("LogNormal", "beast.base.inference.distribution.LogNormalDistributionModel");
        DISTRIBUTION_MAP.put("Normal", "beast.base.inference.distribution.Normal");
        DISTRIBUTION_MAP.put("Exponential", "beast.base.inference.distribution.Exponential");
        DISTRIBUTION_MAP.put("Gamma", "beast.base.inference.distribution.Gamma");
        DISTRIBUTION_MAP.put("Beta", "beast.base.inference.distribution.Beta");
        DISTRIBUTION_MAP.put("Dirichlet", "beast.base.inference.distribution.Dirichlet");
        DISTRIBUTION_MAP.put("Uniform", "beast.base.inference.distribution.Uniform");
        DISTRIBUTION_MAP.put("Prior", "beast.base.inference.distribution.Prior");
        DISTRIBUTION_MAP.put("ChiSquare", "beast.base.inference.distribution.ChiSquare");
        DISTRIBUTION_MAP.put("InverseGamma", "beast.base.inference.distribution.InverseGamma");
        DISTRIBUTION_MAP.put("LaplaceDistribution", "beast.base.inference.distribution.LaplaceDistribution");
        DISTRIBUTION_MAP.put("Poisson", "beast.base.inference.distribution.Poisson");

        // Tree models
        DISTRIBUTION_MAP.put("Yule", "beast.base.evolution.speciation.YuleModel");
        DISTRIBUTION_MAP.put("BirthDeath", "beast.base.evolution.speciation.BirthDeathModel");
        DISTRIBUTION_MAP.put("Coalescent", "beast.base.evolution.tree.coalescent.Coalescent");

        // Sequence models
        DISTRIBUTION_MAP.put("PhyloCTMC", "beast.base.evolution.likelihood.TreeLikelihood");

        // Substitution models
        DISTRIBUTION_MAP.put("JC69", "beast.base.evolution.substitutionmodel.JukesCantor");
        DISTRIBUTION_MAP.put("K80", "beast.base.evolution.substitutionmodel.HKY"); // K80 is a special case of HKY
        DISTRIBUTION_MAP.put("F81", "beast.base.evolution.substitutionmodel.HKY"); // F81 is a special case of HKY
        DISTRIBUTION_MAP.put("HKY", "beast.base.evolution.substitutionmodel.HKY");
        DISTRIBUTION_MAP.put("GTR", "beast.base.evolution.substitutionmodel.GTR");
        DISTRIBUTION_MAP.put("WAG", "beast.base.evolution.substitutionmodel.WAG");
        DISTRIBUTION_MAP.put("JTT", "beast.base.evolution.substitutionmodel.JTT");
        DISTRIBUTION_MAP.put("LG", "beast.base.evolution.substitutionmodel.LG");

        // Site models
        DISTRIBUTION_MAP.put("SiteModel", "beast.base.evolution.sitemodel.SiteModel");

        // Parameter names
        PARAMETER_MAP.put("LogNormal.meanlog", "M");
        PARAMETER_MAP.put("LogNormal.sdlog", "S");
        PARAMETER_MAP.put("Yule.birthRate", "birthDiffRate");
        PARAMETER_MAP.put("PhyloCTMC.L", "sequenceLength");
        PARAMETER_MAP.put("PhyloCTMC.Q", "substModel");
        PARAMETER_MAP.put("Dirichlet.conc", "alpha");

        // Type mappings
        TYPE_MAP.put("Real", "beast.base.inference.parameter.RealParameter");
        TYPE_MAP.put("Integer", "beast.base.inference.parameter.IntegerParameter");
        TYPE_MAP.put("Boolean", "beast.base.inference.parameter.BooleanParameter");
        TYPE_MAP.put("TimeTree", "beast.base.evolution.tree.Tree");
        TYPE_MAP.put("Tree", "beast.base.evolution.tree.Tree");
        TYPE_MAP.put("QMatrix", "beast.base.evolution.substitutionmodel.SubstitutionModel");
        TYPE_MAP.put("DNAAlignment", "beast.base.evolution.alignment.Alignment");
        TYPE_MAP.put("Alignment", "beast.base.evolution.alignment.Alignment");
        TYPE_MAP.put("Simplex", "beast.base.inference.parameter.RealParameter"); // Special handling needed
        TYPE_MAP.put("TaxonSet", "beast.base.evolution.alignment.TaxonSet");
        TYPE_MAP.put("PositiveReal", "beast.base.inference.parameter.RealParameter"); // Special handling needed
        TYPE_MAP.put("Probability", "beast.base.inference.parameter.RealParameter"); // Special handling needed
        TYPE_MAP.put("TreeNode", "beast.base.evolution.tree.Node");

        // Initialize ParametricDistribution subclasses
        PARAMETRIC_DISTRIBUTIONS.addAll(Arrays.asList(
                "Beta", "ChiSquare", "Dirichlet", "Uniform", "LogNormal",
                "Poisson", "Exponential", "InverseGamma", "LaplaceDistribution",
                "Normal", "Gamma"
        ));

        // Initialize integer distributions
        INTEGER_DISTRIBUTIONS.add("Poisson");
    }

    /**
     * Map a PhyloSpec distribution name to its BEAST2 equivalent
     */
    public static String mapDistribution(String phyloSpecName) {
        String result = DISTRIBUTION_MAP.get(phyloSpecName);
        if (result == null) {
            logger.fine("No mapping found for PhyloSpec distribution: " + phyloSpecName);
            return phyloSpecName; // Return original if no mapping exists
        }
        logger.fine("Mapped PhyloSpec distribution '" + phyloSpecName + "' to '" + result + "'");
        return result;
    }

    /**
     * Map a parameter name if needed
     */
    public static String mapParameterName(String distribution, String paramName) {
        String key = distribution + "." + paramName;
        String result = PARAMETER_MAP.get(key);
        if (result == null) {
            return paramName; // Return original if no mapping exists
        }
        logger.fine("Mapped parameter '" + paramName + "' to '" + result + "' for distribution '" + distribution + "'");
        return result;
    }

    /**
     * Map a PhyloSpec type to its BEAST2 equivalent
     */
    public static String mapType(String phyloSpecType) {
        String result = TYPE_MAP.get(phyloSpecType);
        if (result == null) {
            logger.fine("No mapping found for PhyloSpec type: " + phyloSpecType);
            return phyloSpecType; // Return original if no mapping exists
        }
        logger.fine("Mapped PhyloSpec type '" + phyloSpecType + "' to '" + result + "'");
        return result;
    }

    /**
     * Check if a name is a PhyloSpec distribution
     */
    public static boolean isPhyloSpecDistribution(String name) {
        return DISTRIBUTION_MAP.containsKey(name);
    }

    /**
     * Check if a name is a known PhyloSpec type
     */
    public static boolean isPhyloSpecType(String name) {
        return TYPE_MAP.containsKey(name);
    }

    /**
     * Check if a PhyloSpec distribution maps to a BEAST2 ParametricDistribution
     */
    public static boolean mapsToParametricDistribution(String phyloSpecDistName) {
        return PARAMETRIC_DISTRIBUTIONS.contains(phyloSpecDistName);
    }

    /**
     * Check if a distribution produces integer values
     */
    public static boolean isIntegerDistribution(String phyloSpecDistName) {
        return INTEGER_DISTRIBUTIONS.contains(phyloSpecDistName);
    }

    /**
     * Get the short name (without package) for a fully qualified class name
     */
    public static String getShortName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        if (lastDot > 0) {
            return fullyQualifiedName.substring(lastDot + 1);
        }
        return fullyQualifiedName;
    }
}
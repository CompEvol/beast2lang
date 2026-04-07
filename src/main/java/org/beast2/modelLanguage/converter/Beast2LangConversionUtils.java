package org.beast2.modelLanguage.converter;

import beast.base.core.BEASTInterface;
import beast.base.core.Function;
import beast.base.core.Input;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.branchratemodel.BranchRateModel;
import beast.base.evolution.branchratemodel.StrictClockModel;
import beast.base.evolution.likelihood.GenericTreeLikelihood;
import beast.base.evolution.sitemodel.SiteModel;
import beast.base.evolution.substitutionmodel.SubstitutionModel;
import beast.base.evolution.tree.Tree;
import beast.base.inference.Distribution;
import beast.base.inference.StateNode;
import beast.base.inference.distribution.ParametricDistribution;
import beast.base.inference.distribution.Prior;
import beast.base.inference.parameter.Parameter;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

/**
 * Utility class for BEAST2 to Beast2Lang conversion.
 */
public class Beast2LangConversionUtils {

    private static final Logger logger = Logger.getLogger(Beast2LangConversionUtils.class.getName());

    /**
     * Determine if a BEAST object should be represented as a distribution assignment (using ~)
     * in Beast2Lang.
     *
     * @param obj The BEAST object to check
     * @return true if the object should use ~ syntax, false otherwise
     */
    public static boolean isDistributionAssignment(BEASTInterface obj) {
        // Parameters with priors should use ~ syntax
        if (obj instanceof Parameter) {
            return hasExplicitPrior((Parameter<?>) obj);
        }

        // Trees with tree priors should use ~ syntax
        if (obj instanceof Tree) {
            return hasTreePrior((Tree) obj);
        }

        // GenericTreeLikelihood with observed data should use ~ syntax
        if (obj instanceof GenericTreeLikelihood) {
            return true;
        }

        return false;
    }

    /**
     * Check if a parameter has an explicit prior distribution
     */
    private static boolean hasExplicitPrior(Parameter<?> param) {
        // This requires access to the full model to find priors that reference this parameter
        // For a complete implementation, you would need to search all distributions in the model
        // to find ones that reference this parameter
        return true; // Simplified for now, assuming all parameters have priors
    }

    /**
     * Check if a tree has a tree prior distribution
     */
    private static boolean hasTreePrior(Tree tree) {
        // Similar to hasExplicitPrior, this requires access to the full model
        return true; // Simplified for now
    }

    /**
     * Find the distribution that targets a specific state node
     *
     * @param node The state node to find a distribution for
     * @param distributions List of all distributions in the model
     * @return The distribution for this node, or null if not found
     */
    public static Distribution findDistributionFor(StateNode node, List<Distribution> distributions) {
        for (Distribution dist : distributions) {
            if (dist instanceof Prior) {
                Prior prior = (Prior) dist;
                if (prior.m_x.get() == node) {
                    return prior;
                }
            }
        }
        return null;
    }

    /**
     * Determine if a file is likely a Nexus file
     */
    public static boolean isNexusFile(String filePath) {
        String lowerPath = filePath.toLowerCase();
        return lowerPath.endsWith(".nex") || lowerPath.endsWith(".nexus");
    }

    /**
     * Extract a filename from a path, keeping only the filename without directory
     */
    public static String extractFilename(String path) {
        return new File(path).getName();
    }

    /**
     * Check if an alignment is observed data in a likelihood
     */
    public static boolean isObservedData(Alignment alignment, List<GenericTreeLikelihood> likelihoods) {
        for (GenericTreeLikelihood likelihood : likelihoods) {
            if (likelihood.dataInput.get() == alignment) {
                return true;
            }
        }
        return false;
    }

    /**
     * Try to get the Nexus file name from an Alignment object
     *
     * @param alignment The alignment object
     * @return The nexus file name or "data.nex" if not found
     */
    public static String getNexusFileName(Alignment alignment) {
        // This is a heuristic, as the file name might not be available
        // in the Alignment object
        String fileName = "data.nex";

        // Try to get the ID which often contains the file name
        String id = alignment.getID();
        if (id != null && !id.isEmpty()) {
            if (id.contains(".nex")) {
                // Extract the part that looks like a Nexus file name
                int index = id.indexOf(".nex");
                int startIndex = id.lastIndexOf('/', index);
                if (startIndex < 0) startIndex = id.lastIndexOf('\\', index);
                if (startIndex < 0) startIndex = 0; else startIndex++;

                fileName = id.substring(startIndex, index + 4);
            }
        }

        return fileName;
    }

    /**
     * Find the distribution class to use in Beast2Lang for a distribution
     * For example, unwrap Prior to use the underlying parametric distribution
     *
     * @param dist The distribution to analyze
     * @return The class name to use in Beast2Lang
     */
    public static String getDistributionClassName(Distribution dist) {
        if (dist instanceof Prior) {
            Prior prior = (Prior) dist;
            ParametricDistribution paramDist = prior.distInput.get();
            if (paramDist != null) {
                return paramDist.getClass().getSimpleName();
            }
        }

        return dist.getClass().getSimpleName();
    }

    /**
     * Get the arguments from a Prior's parametric distribution
     *
     * @param prior The prior to unwrap
     * @return List of arguments from the parametric distribution, or empty list if not available
     */
    public static Map<String, Object> getPriorDistributionArguments(Prior prior) {
        Map<String, Object> args = new HashMap<>();

        ParametricDistribution paramDist = prior.distInput.get();
        if (paramDist != null) {
            // Analyze the parametric distribution's inputs
            for (Input<?> input : paramDist.getInputs().values()) {
                if (input.get() != null && !input.getName().equals("x")) {
                    args.put(input.getName(), input.get());
                }
            }
        }

        return args;
    }

    /**
     * Check if SiteModel can be bypassed and the SubstitutionModel used directly
     * This takes advantage of Beast2Lang's autoboxing capabilities
     */
    public static boolean canUseSubstitutionModelDirectly(SiteModel siteModel) {
        // Check if it's a simple site model with default settings
        SubstitutionModel substModel = siteModel.substModelInput.get();

        // If no substitution model, we can't bypass
        if (substModel == null) {
            return false;
        }

        // Check if all other inputs have default values
        // Note: the logic is inverted from what you might expect - we return true
        // if it's a simple SiteModel that can be bypassed
        return siteModel.gammaCategoryCount.get() == 1 &&
                siteModel.invarParameterInput.get().getValue() == 0.0 &&
                siteModel.muParameterInput.get().getValue() == 1.0;
    }

    /**
     * Check if a branch rate model can be omitted (using strict clock with rate=1.0)
     */
    public static boolean canOmitBranchRateModel(BranchRateModel branchRateModel) {
        // Check for StrictClockModel with rate 1.0
        if (branchRateModel instanceof StrictClockModel) {
            StrictClockModel clockModel = (StrictClockModel) branchRateModel;
            Function rateParam = clockModel.meanRateInput.get();

            if (rateParam != null && rateParam.getDimension() == 1) {
                return rateParam.getArrayValue() == 1.0;
            }
        }

        return false;
    }

    /**
     * Find objects of a specific type in a model, starting from a root object
     *
     * @param root The root object to start from
     * @param targetClass The class to search for
     * @param <T> Type parameter for the target class
     * @return List of found objects of the target type
     */
    public static <T extends BEASTInterface> List<T> findObjectsOfType(BEASTInterface root, Class<T> targetClass) {
        List<T> result = new ArrayList<>();
        Set<BEASTInterface> visited = new HashSet<>();
        findObjectsOfTypeRecursive(root, targetClass, result, visited);
        return result;
    }

    /**
     * Recursive helper for findObjectsOfType
     */
    @SuppressWarnings("unchecked")
    private static <T extends BEASTInterface> void findObjectsOfTypeRecursive(
            BEASTInterface obj,
            Class<T> targetClass,
            List<T> result,
            Set<BEASTInterface> visited) {

        if (obj == null || visited.contains(obj)) {
            return;
        }

        visited.add(obj);

        if (targetClass.isInstance(obj)) {
            result.add((T) obj);
        }

        // Process all inputs
        for (Input<?> input : obj.getInputs().values()) {
            if (input.get() != null) {
                if (input.get() instanceof BEASTInterface) {
                    findObjectsOfTypeRecursive((BEASTInterface) input.get(), targetClass, result, visited);
                } else if (input.get() instanceof List) {
                    for (Object item : (List<?>) input.get()) {
                        if (item instanceof BEASTInterface) {
                            findObjectsOfTypeRecursive((BEASTInterface) item, targetClass, result, visited);
                        }
                    }
                }
            }
        }
    }
}
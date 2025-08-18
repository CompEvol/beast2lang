package org.beast2.modelLanguage.converter;

import beast.base.core.BEASTInterface;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.MRCAPrior;
import beast.base.evolution.tree.TreeDistribution;
import beast.base.evolution.tree.TreeIntervals;
import org.beast2.modelLanguage.converter.pipeline.ConversionContext;
import org.beast2.modelLanguage.model.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * Utility class for BEAST-specific conversion operations.
 * This slimmed-down version contains only the core utilities needed by multiple phases.
 * Phase-specific logic has been moved to the respective phase classes.
 */
public class BeastConversionUtilities {

    private static final Logger logger = Logger.getLogger(BeastConversionUtilities.class.getName());

    private final Map<BEASTInterface, String> objectToIdMap;

    public BeastConversionUtilities(Map<BEASTInterface, String> objectToIdMap) {
        this.objectToIdMap = objectToIdMap;
    }

    /**
     * Check if a secondary input should be suppressed for distribution statements
     */
    public boolean shouldSuppressSecondaryInput(String inputName, Object value, BEASTInterface distribution) {
        if (distribution == null) return false;

        String distributionType = distribution.getClass().getSimpleName();
        return shouldSuppressNaturalBound(inputName, value, distributionType);
    }

    /**
     * Check if a bound should be suppressed because it's the natural domain of the distribution
     */
    private boolean shouldSuppressNaturalBound(String inputName, Object value, String distributionType) {
        if (value == null) return true;

        switch (distributionType.toLowerCase()) {
            case "gamma":
            case "exponential":
            case "lognormal":
            case "chi2":
            case "inversegamma":
                // These distributions naturally have lower=0
                if ("lower".equals(inputName) && isEffectivelyZero(value)) {
                    return true;
                }
                break;

            case "dirichlet", "beta":
                // beta and dirichlet naturally has lower=0, upper=1
                if ("lower".equals(inputName) && isEffectivelyZero(value)) {
                    return true;
                }
                if ("upper".equals(inputName) && isEffectivelyOne(value)) {
                    return true;
                }
                break;

            case "uniform":
                // Don't suppress bounds for Uniform - they're essential parameters
                break;

            default:
                break;
        }

        return false;
    }

    private boolean isEffectivelyZero(Object value) {
        if (value instanceof Number) {
            return Math.abs(((Number) value).doubleValue()) < 1e-10;
        }
        return false;
    }

    private boolean isEffectivelyOne(Object value) {
        if (value instanceof Number) {
            return Math.abs(((Number) value).doubleValue() - 1.0) < 1e-10;
        }
        return false;
    }

    /**
     * Check if this is a tree distribution (TreeDistribution subclasses)
     */
    public boolean isTreeDistribution(BEASTInterface obj) {
        return obj instanceof TreeDistribution;
    }

    /**
     * Group distributions by their target variable
     */
    public Map<BEASTInterface, List<BEASTInterface>> groupDistributionsByTarget(
            Collection<BEASTInterface> objects) {
        Map<BEASTInterface, List<BEASTInterface>> grouped = new HashMap<>();

        for (BEASTInterface obj : objects) {
            if (isTreeDistribution(obj)) {
                BEASTInterface tree = getTreeFromDistribution(obj);
                if (tree != null) {
                    grouped.computeIfAbsent(tree, k -> new ArrayList<>()).add(obj);
                }
            } else if (obj instanceof MRCAPrior prior) {
                BEASTInterface tree = prior.treeInput.get();
                if (tree != null) {
                    grouped.computeIfAbsent(tree, k -> new ArrayList<>()).add(prior);
                }
            }
        }

        return grouped;
    }

    /**
     * Check if object should be suppressed from decompiled output
     */
    public boolean shouldSuppressObject(BEASTInterface obj) {
        if (obj instanceof TreeIntervals intervals) {
            for (BEASTInterface other : objectToIdMap.keySet()) {
                if (other instanceof TreeDistribution treeDist) {
                    if (treeDist.treeIntervalsInput.get() == intervals) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Extract tree from distribution, handling TreeDistribution's XOR inputs
     */
    private BEASTInterface getTreeFromDistribution(BEASTInterface distribution) {
        if (distribution instanceof TreeDistribution treeDist) {

            if (treeDist.treeInput.get() != null) {
                return (BEASTInterface) treeDist.treeInput.get();
            }

            if (treeDist.treeIntervalsInput.get() != null) {
                TreeIntervals intervals = treeDist.treeIntervalsInput.get();
                return intervals.treeInput.get();
            }
        }
        return null;
    }

    /**
     * Create distribution statement with calibrations
     */
    public Statement createDistributionStatementWithCalibrations(
            BEASTInterface target,
            BEASTInterface mainDistribution,
            List<MRCAPrior> calibrations,
            ConversionContext context) {

        String targetId = objectToIdMap.get(target);
        String className = target.getClass().getSimpleName();

        // Use context's statement creator to create distribution expression
        Expression distExpr = context.getStatementCreator().createDistributionExpression(mainDistribution);
        DistributionAssignment distAssign = new DistributionAssignment(className, targetId, distExpr);

        if (calibrations.isEmpty()) {
            return distAssign;
        }

        List<Annotation> annotations = new ArrayList<>();
        for (MRCAPrior prior : calibrations) {
            try {
                Map<String, Expression> params = new HashMap<>();

                TaxonSet taxonSet = prior.taxonsetInput.get();
                if (taxonSet != null) {
                    String taxonSetId = objectToIdMap.get(taxonSet);
                    if (taxonSetId != null) {
                        params.put("taxonset", new Identifier(taxonSetId));
                    }
                }

                if (prior.distInput.get() != null) {
                    BEASTInterface dist = prior.distInput.get();
                    Expression distExpression = context.getStatementCreator().createExpressionForObject(dist);
                    params.put("distribution", distExpression);
                }

                Boolean tipsOnly = prior.onlyUseTipsInput.get();
                if (tipsOnly != null && tipsOnly) {
                    params.put("leaf", new Literal(true, Literal.LiteralType.BOOLEAN));
                }

                Boolean monophyletic = prior.isMonophyleticInput.get();
                if (monophyletic != null && monophyletic && (tipsOnly == null || !tipsOnly)) {
                    params.put("monophyletic", new Literal(true, Literal.LiteralType.BOOLEAN));
                }

                if (params.containsKey("taxonset")) {
                    annotations.add(new Annotation("calibration", params));
                }
            } catch (Exception e) {
                logger.warning("Failed to create calibration annotation: " + e.getMessage());
            }
        }

        return new AnnotatedStatement(annotations, distAssign);
    }

    /**
     * Separate distributions into main distribution and calibrations
     */
    public DistributionSeparation separateDistributions(List<BEASTInterface> distributions) {
        BEASTInterface mainDistribution = null;
        List<MRCAPrior> calibrations = new ArrayList<>();

        for (BEASTInterface dist : distributions) {
            if (dist instanceof MRCAPrior) {
                calibrations.add((MRCAPrior) dist);
            } else if (mainDistribution == null) {
                mainDistribution = dist;
            }
        }

        return new DistributionSeparation(mainDistribution, calibrations);
    }

    /**
     * Check if object is a data source (like AlignmentFromNexus)
     */
    public boolean isDataSource(BEASTInterface obj) {
        String className = obj.getClass().getName();

        if (className.contains("AlignmentFromNexus")) {
            return true;
        }

        if (obj instanceof Alignment) {
            for (var input : obj.getInputs().values()) {
                if (input.getName().equals("fileName") && input.get() != null) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Helper class to hold separated distributions
     */
    public static class DistributionSeparation {
        public final BEASTInterface mainDistribution;
        public final List<MRCAPrior> calibrations;

        public DistributionSeparation(BEASTInterface mainDistribution, List<MRCAPrior> calibrations) {
            this.mainDistribution = mainDistribution;
            this.calibrations = calibrations;
        }
    }
}
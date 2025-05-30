package org.beast2.modelLanguage.converter;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Sequence;
import beast.base.evolution.tree.MRCAPrior;
import beast.base.evolution.tree.TreeDistribution;
import beast.base.evolution.tree.TreeIntervals;
import beast.base.evolution.alignment.TaxonSet;
import org.beast2.modelLanguage.model.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Logger;

/**
 * Handles BEAST-specific conversion logic and special cases
 */
public class BeastConversionHandler {

    private static final Logger logger = Logger.getLogger(BeastConversionHandler.class.getName());

    private final Map<BEASTInterface, String> objectToIdMap;
    private final Set<BEASTInterface> usedDistributions;
    private int alignmentCounter = 0;

    public BeastConversionHandler(Map<BEASTInterface, String> objectToIdMap,
                                  Set<BEASTInterface> usedDistributions) {
        this.objectToIdMap = objectToIdMap;
        this.usedDistributions = usedDistributions;
    }

    /**
     * Generate unique alignment identifier
     */
    public String generateAlignmentId(Alignment alignment) {
        if (alignment.getID() != null && !alignment.getID().isEmpty()) {
            String baseId = alignment.getID().replaceAll("[^a-zA-Z0-9_]", "_");

            // If the ID already exists in the map, check if it's the same object
            for (Map.Entry<BEASTInterface, String> entry : objectToIdMap.entrySet()) {
                if (entry.getValue().equals(baseId) && entry.getKey() != alignment) {
                    // Only add counter if it's a different object with the same ID
                    String uniqueId = baseId;
                    int counter = 2;
                    while (objectToIdMap.containsValue(uniqueId)) {
                        uniqueId = baseId + "_" + counter++;
                    }
                    return uniqueId;
                }
            }
            return baseId;
        }
        return "alignment" + (++alignmentCounter);
    }

    /**
     * Check if this is a tree distribution (TreeDistribution subclasses)
     */
    public boolean isTreeDistribution(BEASTInterface obj) {
        return obj instanceof TreeDistribution;
    }

    /**
     * Extract sequence data and create nexus statement
     */
    public Statement createAlignmentFromEmbeddedData(Alignment alignment, String alignmentId) {
        // Save sequence data to a separate nexus file
        String fileName = alignmentId + ".nex";
        saveAlignmentToFile(alignment,fileName);

        Expression expr = new NexusFunction(fileName);

        // Create the variable declaration with @data annotation
        VariableDeclaration decl = new VariableDeclaration("Alignment", alignmentId, expr);

        Map<String, Expression> params = new HashMap<>();
        Annotation annotation = new Annotation("data", params);

        return new AnnotatedStatement(Arrays.asList(annotation), decl);
    }

    // Add this helper method to actually write the nexus file
    public void saveAlignmentToFile(Alignment alignment, String fileName) {
        try (PrintWriter writer = new PrintWriter(fileName)) {
            writer.println("#NEXUS");
            writer.println("BEGIN DATA;");
            writer.println("DIMENSIONS NTAX=" + alignment.getTaxonCount() +
                    " NCHAR=" + alignment.getSiteCount() + ";");
            writer.println("FORMAT DATATYPE=" + alignment.getDataType().getTypeDescription() +
                    " MISSING=? GAP=-;");
            writer.println("MATRIX");

            for (Sequence seq : alignment.sequenceInput.get()) {
                writer.println(seq.taxonInput.get() + " " + seq.dataInput.get());
            }

            writer.println(";");
            writer.println("END;");
        } catch (IOException e) {
            logger.severe("Failed to write alignment file: " + fileName);
            throw new RuntimeException("Failed to write alignment file", e);
        }
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
                    logger.info("Grouped tree distribution " + obj.getClass().getSimpleName() +
                            " with tree " + tree.getID());
                }
            } else if (obj instanceof MRCAPrior) {
                MRCAPrior prior = (MRCAPrior) obj;
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
        // Suppress TreeIntervals that are used as intermediaries for TreeDistributions
        if (obj instanceof TreeIntervals) {
            TreeIntervals intervals = (TreeIntervals) obj;

            // Check if this TreeIntervals is used by any TreeDistribution
            for (BEASTInterface other : objectToIdMap.keySet()) {
                if (other instanceof TreeDistribution) {
                    TreeDistribution treeDist = (TreeDistribution) other;
                    if (treeDist.treeIntervalsInput.get() == intervals) {
                        logger.info("Suppressing TreeIntervals " + obj.getID() +
                                " - used as intermediary for " + other.getClass().getSimpleName());
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * FIXED: Extract tree from distribution, handling TreeDistribution's XOR inputs
     */
    private BEASTInterface getTreeFromDistribution(BEASTInterface distribution) {
        if (distribution instanceof TreeDistribution) {
            TreeDistribution treeDist = (TreeDistribution) distribution;

            // TreeDistribution has XOR between treeInput and treeIntervalsInput
            // First try direct tree input
            if (treeDist.treeInput.get() != null) {
                return (BEASTInterface) treeDist.treeInput.get();
            }

            // If no direct tree, try treeIntervals input (like BayesianSkyline uses)
            if (treeDist.treeIntervalsInput.get() != null) {
                TreeIntervals intervals = treeDist.treeIntervalsInput.get();
                return intervals.treeInput.get();
            }
        }

        return null;
    }

    public Statement createDistributionStatementWithCalibrations(
            BEASTInterface target,
            BEASTInterface mainDistribution,
            List<MRCAPrior> calibrations,
            Beast2ToBeast2LangConverter converter) {

        String targetId = objectToIdMap.get(target);
        String className = target.getClass().getSimpleName();

        // Create the main distribution assignment
        Expression distExpr = converter.createExpressionForObject(mainDistribution);
        DistributionAssignment distAssign = new DistributionAssignment(
                className, targetId, distExpr);

        // If no calibrations, return the simple statement
        if (calibrations.isEmpty()) {
            return distAssign;
        }

        // Create calibration annotations
        List<Annotation> annotations = new ArrayList<>();

        for (MRCAPrior prior : calibrations) {
            try {
                Map<String, Expression> params = new HashMap<>();

                // Add taxonset parameter
                TaxonSet taxonSet = prior.taxonsetInput.get();
                if (taxonSet != null) {
                    String taxonSetId = objectToIdMap.get(taxonSet);
                    if (taxonSetId != null) {
                        params.put("taxonset", new Identifier(taxonSetId));
                    }
                }

                // Add distribution parameter - use the existing createDistributionExpression method
                if (prior.distInput.get() != null) {
                    BEASTInterface dist = prior.distInput.get();
                    Expression distExpression = createDistributionExpression(dist, converter);
                    params.put("distribution", distExpression);
                    logger.info("Created calibration with distribution: " + dist.getClass().getSimpleName());
                }

                // Add leaf parameter if tipsonly is true
                Boolean tipsOnly = prior.onlyUseTipsInput.get();
                if (tipsOnly != null && tipsOnly) {
                    params.put("leaf", new Literal(true, Literal.LiteralType.BOOLEAN));
                    logger.info("Added leaf=true for tipsonly MRCAPrior: " + prior.getID());
                }

                Boolean monophyletic = prior.isMonophyleticInput.get();
                if (monophyletic != null && monophyletic && (tipsOnly == null || !tipsOnly)) {
                    params.put("monophyletic", new Literal(true, Literal.LiteralType.BOOLEAN));
                    logger.info("  Added monophyletic=true for node constraint: " + prior.getID());
                }

                // Only add the annotation if we have a taxonset
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
     * Create a proper distribution expression instead of using object references
     */
    private Expression createDistributionExpression(BEASTInterface dist, Beast2ToBeast2LangConverter converter) {
        // Use the converter's expression creation method to get a proper function call
        Expression expr = converter.createExpressionForObject(dist);
        logger.info("Created distribution expression for " + dist.getClass().getSimpleName() + ": " + expr);
        return expr;
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
        // Check the actual class name
        String className = obj.getClass().getName();
        logger.info("Checking if " + className + " is a data source");

        // AlignmentFromNexus is always a data source
        if (className.contains("AlignmentFromNexus")) {
            logger.info("Found AlignmentFromNexus - it's a data source");
            return true;
        }

        // Check for fileName input
        if (obj instanceof Alignment) {
            for (var input : obj.getInputs().values()) {
                if (input.getName().equals("fileName") && input.get() != null) {
                    logger.info("Found fileName input - it's a data source");
                    return true;
                }
            }
        }

        logger.info("Not a data source");
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
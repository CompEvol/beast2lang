package org.beast2.modelLanguage.converter;

import beast.base.core.BEASTInterface;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Sequence;
import beast.base.evolution.tree.MRCAPrior;
import beast.base.evolution.tree.TreeDistribution;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.TreeInterface;
import beast.base.inference.Distribution;
import org.beast2.modelLanguage.model.*;

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
            // Ensure uniqueness
            String uniqueId = baseId;
            int counter = 1;
            while (objectToIdMap.containsValue(uniqueId)) {
                uniqueId = baseId + "_" + counter++;
            }
            return uniqueId;
        }
        return "alignment" + (alignmentCounter++);
    }

    /**
     * Check if this is a tree distribution (SABirthDeathModel, MRCAPrior, etc.)
     */
    public boolean isTreeDistribution(BEASTInterface obj) {
        return obj instanceof TreeDistribution || obj instanceof MRCAPrior;
    }

    /**
     * Extract sequence data and create AlignmentFromNexus statement
     */
    public Statement createAlignmentFromEmbeddedData(Alignment alignment, String alignmentId) {
        // Option 1: Use nexus() function
        List<Argument> nexusArgs = new ArrayList<>();

        // Extract sequences to create nexus data
        StringBuilder nexusData = new StringBuilder();
        nexusData.append("#NEXUS\n");
        nexusData.append("BEGIN DATA;\n");
        nexusData.append("DIMENSIONS NTAX=").append(alignment.getTaxonCount());
        nexusData.append(" NCHAR=").append(alignment.getSiteCount()).append(";\n");
        nexusData.append("FORMAT DATATYPE=").append(alignment.getDataType().getTypeDescription());
        nexusData.append(" MISSING=? GAP=-;\n");
        nexusData.append("MATRIX\n");

        for (Sequence seq : alignment.sequenceInput.get()) {
            nexusData.append(seq.taxonInput.get()).append(" ");
            nexusData.append(seq.dataInput.get()).append("\n");
        }

        nexusData.append(";\nEND;\n");

        nexusArgs.add(new Argument("data", new Literal(nexusData.toString(), Literal.LiteralType.STRING)));

        Expression nexusExpr = new NexusFunction(nexusArgs);

        // Create the variable declaration with @data annotation
        VariableDeclaration decl = new VariableDeclaration("Alignment", alignmentId, nexusExpr);

        Map<String, Expression> params = new HashMap<>();
        Annotation annotation = new Annotation("data", params);

        return new AnnotatedStatement(Arrays.asList(annotation), decl);
    }

    /**
     * Group distributions by their target variable
     */
    public Map<BEASTInterface, List<BEASTInterface>> groupDistributionsByTarget(
            Collection<BEASTInterface> objects) {
        Map<BEASTInterface, List<BEASTInterface>> grouped = new HashMap<>();

        for (BEASTInterface obj : objects) {
            if (isTreeDistribution(obj)) {
                TreeDistribution dist = (TreeDistribution) obj;
                BEASTInterface tree = (BEASTInterface) dist.treeInput.get();
                if (tree != null) {
                    grouped.computeIfAbsent(tree, k -> new ArrayList<>()).add(dist);
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
     * Create compound distribution statement for multiple distributions on same variable
     */
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
            Map<String, Expression> params = new HashMap<>();

            // Add taxonset parameter
            TaxonSet taxonSet = prior.taxonsetInput.get();
            if (taxonSet != null && objectToIdMap.containsKey(taxonSet)) {
                params.put("taxonset", new Identifier(objectToIdMap.get(taxonSet)));
            }

            // Add distribution parameter
            if (prior.distInput.get() != null) {
                params.put("distribution", converter.createExpressionForObject(prior.distInput.get()));
            }

            annotations.add(new Annotation("calibration", params));
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
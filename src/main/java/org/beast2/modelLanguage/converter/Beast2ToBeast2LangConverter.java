package org.beast2.modelLanguage.converter;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Sequence;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.likelihood.TreeLikelihood;
import beast.base.evolution.tree.MRCAPrior;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.inference.distribution.Prior;
import beast.base.inference.parameter.RealParameter;
import org.beast2.modelLanguage.beast.BeastObjectFactory;
import org.beast2.modelLanguage.builder.ModelObjectFactory;
import org.beast2.modelLanguage.model.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * Converts a BEAST2 object model to a Beast2Lang model.
 *
 * This converter orchestrates the conversion process using specialized helper classes.
 */
public class Beast2ToBeast2LangConverter {

    private static final Logger logger = Logger.getLogger(Beast2ToBeast2LangConverter.class.getName());

    // Core data structures
    private final Map<BEASTInterface, String> objectToIdMap = new HashMap<>();
    private final Map<BEASTInterface, Statement> objectToStatementMap = new HashMap<>();
    private final Set<BEASTInterface> processedObjects = new HashSet<>();
    private final Map<String, Alignment> processedAlignments = new HashMap<>();
    private final Set<BEASTInterface> usedDistributions = new HashSet<>();
    private final Set<BEASTInterface> inlinedDistributions = new HashSet<>();

    // Helper components
    private final ModelObjectFactory objectFactory;
    private final BeastConversionHandler specialHandler;
    private StatementCreator statementCreator;

    private State state = null;

    public Beast2ToBeast2LangConverter() {
        this.objectFactory = new BeastObjectFactory();
        this.specialHandler = new BeastConversionHandler(objectToIdMap, usedDistributions);
        this.statementCreator = new StatementCreator(objectToIdMap, objectFactory,
                specialHandler, usedDistributions, state);
    }

    /**
     * Convert a BEAST2 analysis to a Beast2Lang model.
     */
    public Beast2Model convertToBeast2Model(Distribution posterior, State state) {
        Beast2Model model = new Beast2Model();
        this.state = state;

        // STEP 0: Normalize all identifiers first to maintain references
        BeastIdentifierNormaliser normalizer = new BeastIdentifierNormaliser();
        normalizer.normaliseIdentifiers(posterior, state);
        logger.info("Identifier normalization completed");

        // Update statement creator with state
        this.statementCreator = new StatementCreator(objectToIdMap, objectFactory,
                specialHandler, usedDistributions, state);

        // Add common imports
        addCommonImports(model);

        // First pass: identify all objects and generate identifiers
        identifyObjects(posterior, state);

        // Pre-pass: identify all distributions used in ~ statements
        identifyUsedDistributions();

        // Pre-pass: identify all inlined distributions (from Prior unwrapping)
        identifyInlinedDistributions();

        // Group distributions by target
        Map<BEASTInterface, List<BEASTInterface>> distributionsByTarget =
                specialHandler.groupDistributionsByTarget(objectToIdMap.keySet());

        // Second pass: create statements for all objects
        Set<BEASTInterface> statementProcessed = new HashSet<>();

        // Process alignments first
        processAlignments(model, statementProcessed);

        // Process state nodes with their distributions
        processStateNodesWithDistributions(model, statementProcessed, distributionsByTarget);

        // Process remaining objects
        for (BEASTInterface object : objectToIdMap.keySet()) {
            if (shouldCreateStatement(object) && !statementProcessed.contains(object)) {
                processObject(object, model, statementProcessed);
            }
        }

        // Sort statements based on dependencies
        StatementSorter sorter = new StatementSorter();
        sorter.sortStatements(model);

        // Handle observed alignments
        processObservedAlignments(model);

        return model;
    }

    private void processAlignments(Beast2Model model, Set<BEASTInterface> processed) {
        for (BEASTInterface obj : objectToIdMap.keySet()) {
            if (obj instanceof Alignment alignment) {
                if (processed.contains(alignment)) {
                    continue;
                }

                String alignmentId = specialHandler.generateAlignmentId(alignment);
                objectToIdMap.put(alignment, alignmentId);

                Statement stmt;
                if (alignment.sequenceInput.get() != null && !alignment.sequenceInput.get().isEmpty()) {
                    stmt = specialHandler.createAlignmentFromEmbeddedData(alignment, alignmentId);
                } else {
                    stmt = statementCreator.createStatement(alignment);
                }

                model.addStatement(stmt);
                processed.add(alignment);
                processedAlignments.put(alignmentId, alignment);
            }
        }
    }

    private void processStateNodesWithDistributions(Beast2Model model,
                                                    Set<BEASTInterface> processed,
                                                    Map<BEASTInterface, List<BEASTInterface>> distributionsByTarget) {
        for (Map.Entry<BEASTInterface, List<BEASTInterface>> entry : distributionsByTarget.entrySet()) {
            BEASTInterface target = entry.getKey();
            List<BEASTInterface> distributions = entry.getValue();

            if (target instanceof StateNode && !processed.contains(target)) {
                BeastConversionHandler.DistributionSeparation separation =
                        specialHandler.separateDistributions(distributions);

                Statement stmt = specialHandler.createDistributionStatementWithCalibrations(
                        target, separation.mainDistribution, separation.calibrations, this);

                model.addStatement(stmt);
                objectToStatementMap.put(target, stmt);
                processed.add(target);

                // Mark all distributions as processed and used
                for (BEASTInterface dist : distributions) {
                    processed.add(dist);
                    usedDistributions.add(dist);
                    // Ensure the distribution itself gets an identifier if it doesn't have one
                    if (!objectToIdMap.containsKey(dist)) {
                        String distId = generateIdentifier(dist);
                        objectToIdMap.put(dist, distId);
                    }
                }
            }
        }

        // Process other state nodes
        for (StateNode node : state.stateNodeInput.get()) {
            if (!processed.contains(node)) {
                Statement stmt = statementCreator.createStatement(node);
                if (stmt != null) {
                    model.addStatement(stmt);
                    objectToStatementMap.put(node, stmt);
                    processed.add(node);
                }
            }
        }
    }

    private void identifyInlinedDistributions() {
        inlinedDistributions.clear();

        for (BEASTInterface obj : objectToIdMap.keySet()) {
            if (obj instanceof Prior prior) {
                BEASTInterface innerDist = prior.distInput.get();
                if (innerDist != null && objectFactory.isParametricDistribution(innerDist)) {
                    inlinedDistributions.add(innerDist);
                    logger.info("Found inlined distribution: " + innerDist.getID());
                }
            }
            // Also handle MRCAPrior distributions that are used in calibrations
            else if (obj instanceof MRCAPrior mrcaPrior) {
                BEASTInterface innerDist = mrcaPrior.distInput.get();
                if (innerDist != null && objectFactory.isParametricDistribution(innerDist)) {
                    inlinedDistributions.add(innerDist);
                    logger.info("Found inlined calibration distribution: " + innerDist.getID());
                }
            }
        }
    }

    private void processObservedAlignments(Beast2Model model) {
        for (BEASTInterface obj : objectToIdMap.keySet()) {
            if (obj instanceof TreeLikelihood likelihood) {
                Alignment data = likelihood.dataInput.get();
                if (data != null && processedAlignments.containsKey(objectToIdMap.get(data))) {
                    String alignmentId = "alignment_" + objectToIdMap.get(data);
                    Expression likelihoodExpr = createExpressionForObject(likelihood);

                    DistributionAssignment obsAlignment = new DistributionAssignment(
                            "Alignment", alignmentId, likelihoodExpr
                    );

                    Map<String, Expression> params = new HashMap<>();
                    params.put("data", new Identifier(objectToIdMap.get(data)));
                    Annotation annotation = new Annotation("observed", params);

                    model.addStatement(new AnnotatedStatement(List.of(annotation), obsAlignment));
                }
            }
        }
    }

    private void processObject(BEASTInterface obj, Beast2Model model, Set<BEASTInterface> processed) {
        if (processed.contains(obj)) {
            return;
        }
        processed.add(obj);

        // Process dependencies first
        processDependencies(obj, model, processed);

        // Create statement
        Statement statement = statementCreator.createStatement(obj);
        objectToStatementMap.put(obj, statement);
        model.addStatement(statement);

        // Ensure the class is imported
        addImportForClass(obj.getClass(), model);
    }

    private void processDependencies(BEASTInterface obj, Beast2Model model, Set<BEASTInterface> processed) {
        for (Input<?> input : obj.getInputs().values()) {
            if (input.get() != null) {
                if (input.get() instanceof BEASTInterface dependency) {
                    if (shouldCreateStatement(dependency) && !objectToStatementMap.containsKey(dependency)) {
                        processObject(dependency, model, processed);
                    }
                } else if (input.get() instanceof List) {
                    for (Object item : (List<?>) input.get()) {
                        if (item instanceof BEASTInterface dependency) {
                            if (shouldCreateStatement(dependency) && !objectToStatementMap.containsKey(dependency)) {
                                processObject(dependency, model, processed);
                            }
                        }
                    }
                }
            }
        }
    }

    // Delegate to statementCreator for expression creation
    public Expression createExpressionForObject(BEASTInterface obj) {
        return statementCreator.createExpressionForObject(obj);
    }

    private void identifyObjects(Distribution posterior, State state) {
        for (StateNode node : state.stateNodeInput.get()) {
            String id = generateIdentifier(node);
            objectToIdMap.put(node, id);
        }
        processObjectGraph(posterior);
    }

    private void processObjectGraph(BEASTInterface obj) {
        if (obj == null || processedObjects.contains(obj)) {
            return;
        }

        processedObjects.add(obj);

        if (!objectToIdMap.containsKey(obj)) {
            String id = generateIdentifier(obj);
            objectToIdMap.put(obj, id);
        }

        for (Input<?> input : obj.getInputs().values()) {
            if (input.get() != null) {
                if (input.get() instanceof BEASTInterface) {
                    processObjectGraph((BEASTInterface) input.get());
                } else if (input.get() instanceof List) {
                    for (Object item : (List<?>) input.get()) {
                        if (item instanceof BEASTInterface) {
                            processObjectGraph((BEASTInterface) item);
                        }
                    }
                }
            }
        }
    }

    private String generateIdentifier(BEASTInterface obj) {
        String className = obj.getClass().getSimpleName();
        String baseName = className.substring(0, 1).toLowerCase() + className.substring(1);

        if (obj.getID() != null && !obj.getID().isEmpty()) {
            // Since identifiers were normalized upfront, we can use them directly
            baseName = obj.getID();
            // Basic cleanup for any remaining issues
            baseName = baseName.replaceAll("[^a-zA-Z0-9_]", "_");
        }

        String uniqueName = baseName;
        int counter = 1;
        while (objectToIdMap.containsValue(uniqueName)) {
            uniqueName = baseName + "_" + counter++;
        }

        return uniqueName;
    }

    protected boolean shouldCreateStatement(BEASTInterface obj) {
        if (obj instanceof Sequence) return false;
        if (obj instanceof Prior) return false;
        if (specialHandler.isTreeDistribution(obj)) return false;
        if (isFixedParameter(obj)) return false;
        if (obj instanceof RealParameter &&
                "RealParameter".equals(obj.getID()) &&
                ((RealParameter)obj).getDimension() == 0) {
            return false;
        }

        if (specialHandler.shouldSuppressObject(obj)) {
            return false;
        }

        // Skip individual Taxon objects that are part of TaxonSets
        if (obj instanceof beast.base.evolution.alignment.Taxon) {
            // Check if this taxon is part of any TaxonSet
            for (BEASTInterface other : objectToIdMap.keySet()) {
                if (other instanceof TaxonSet) {
                    TaxonSet taxonSet = (TaxonSet) other;
                    List<beast.base.evolution.alignment.Taxon> taxa = taxonSet.taxonsetInput.get();
                    if (taxa != null && taxa.contains(obj)) {
                        return false; // Skip this taxon, it will be included in the TaxonSet
                    }
                }
            }
        }

        if (obj instanceof TaxonSet ts) {
            if (ts.getID() == null && ts.getTaxonCount() == 0 && ts.alignmentInput.get() == null) {
                return false;
            }
        }
        if (obj instanceof TreeLikelihood) return false;
        if (usedDistributions.contains(obj)) return false;
        if (inlinedDistributions.contains(obj)) return false;
        if (obj instanceof CompoundDistribution) {
            String id = obj.getID();
            if (id != null && (id.equals("prior") || id.equals("likelihood") || id.equals("posterior"))) {
                return false;
            }
        }
        return true;
    }

    private boolean isFixedParameter(BEASTInterface obj) {
        if (!(obj instanceof RealParameter)) {
            return false;
        }

        for (StateNode stateNode : state.stateNodeInput.get()) {
            if (stateNode == obj) {
                return false;
            }
        }

        for (BEASTInterface other : objectToIdMap.keySet()) {
            if (other instanceof Prior prior) {
                if (prior.m_x.get() == obj) {
                    return false;
                }
            }
        }

        return true;
    }

    private void identifyUsedDistributions() {
        usedDistributions.clear();

        for (StateNode node : state.stateNodeInput.get()) {
            for (BEASTInterface obj : objectToIdMap.keySet()) {
                if (objectFactory.isDistribution(obj)) {
                    Distribution dist = (Distribution) obj;
                    String primaryInputName = objectFactory.getPrimaryInputName(dist);

                    if (primaryInputName != null) {
                        try {
                            Object primaryInputValue = objectFactory.getInputValue(dist, primaryInputName);
                            if (primaryInputValue == node) {
                                usedDistributions.add(dist);
                                logger.info("Found used distribution: " + dist.getID() +
                                        " for state node " + node.getID());
                            }
                        } catch (Exception e) {
                            logger.warning("Failed to get input value: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private void addCommonImports(Beast2Model model) {
        model.addImport(new ImportStatement("beast.base.inference.parameter", true));
        model.addImport(new ImportStatement("beast.base.inference.distribution", true));
        model.addImport(new ImportStatement("beast.base.evolution.tree", true));
        model.addImport(new ImportStatement("beast.base.evolution.speciation", true));
        model.addImport(new ImportStatement("beast.base.evolution.substitutionmodel", true));
        model.addImport(new ImportStatement("beast.base.evolution.alignment", true));
        model.addImport(new ImportStatement("beast.base.evolution.likelihood", true));
        model.addImport(new ImportStatement("beast.base.evolution.branchratemodel", true));
    }

    private void addImportForClass(Class<?> clazz, Beast2Model model) {
        String packageName = clazz.getPackage().getName();

        for (ImportStatement existing : model.getImports()) {
            if (existing.getPackageName().equals(packageName) && existing.isWildcard()) {
                return;
            }
        }

        ImportStatement importStmt = new ImportStatement(packageName, true);
        model.addImport(importStmt);
    }
}
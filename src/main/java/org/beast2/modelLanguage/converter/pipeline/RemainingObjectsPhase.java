package org.beast2.modelLanguage.converter.pipeline;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.evolution.alignment.Sequence;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.likelihood.TreeLikelihood;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.distribution.Prior;
import beast.base.inference.parameter.RealParameter;
import org.beast2.modelLanguage.model.Statement;
import org.beast2.modelLanguage.model.ImportStatement;

import java.util.*;
import java.util.logging.Logger;

/**
 * Phase 9: Process all remaining objects that haven't been handled by previous phases
 */
public class RemainingObjectsPhase implements ConversionPhase {
    private static final Logger logger = Logger.getLogger(RemainingObjectsPhase.class.getName());

    @Override
    public void execute(ConversionContext context) {
        logger.info("Processing remaining objects...");

        int processedCount = 0;

        for (BEASTInterface object : context.getObjectToIdMap().keySet()) {
            if (shouldCreateStatement(object, context) && !context.isProcessed(object)) {
                processObject(object, context);
                processedCount++;
            }
        }

        logger.info("Processed " + processedCount + " remaining objects");
    }

    private void processObject(BEASTInterface obj, ConversionContext context) {
        if (context.isProcessed(obj)) {
            return;
        }

        // Process dependencies first
        processDependencies(obj, context);

        // Create statement
        Statement statement = context.getStatementCreator().createStatement(obj);
        context.markProcessed(obj, statement);

        // Ensure the class is imported
        addImportForClass(obj.getClass(), context);
    }

    private void processDependencies(BEASTInterface obj, ConversionContext context) {
        for (Input<?> input : obj.getInputs().values()) {
            if (input.get() != null) {
                if (input.get() instanceof BEASTInterface dependency) {
                    if (shouldCreateStatement(dependency, context) && !context.isProcessed(dependency)) {
                        processObject(dependency, context);
                    }
                } else if (input.get() instanceof List) {
                    for (Object item : (List<?>) input.get()) {
                        if (item instanceof BEASTInterface dependency) {
                            if (shouldCreateStatement(dependency, context) && !context.isProcessed(dependency)) {
                                processObject(dependency, context);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean shouldCreateStatement(BEASTInterface obj, ConversionContext context) {
        if (obj instanceof Sequence) return false;
        if (obj instanceof Prior) return false;
        if (context.getConversionUtilities().isTreeDistribution(obj)) return false;
        if (isFixedParameter(obj, context)) return false;

        if (obj instanceof RealParameter &&
                "RealParameter".equals(obj.getID()) &&
                ((RealParameter)obj).getDimension() == 0) {
            return false;
        }

        if (context.getConversionUtilities().shouldSuppressObject(obj)) {
            return false;
        }

        // Skip individual Taxon objects that are part of TaxonSets
        if (obj instanceof beast.base.evolution.alignment.Taxon) {
            for (BEASTInterface other : context.getObjectToIdMap().keySet()) {
                if (other instanceof TaxonSet) {
                    TaxonSet taxonSet = (TaxonSet) other;
                    List<beast.base.evolution.alignment.Taxon> taxa = taxonSet.taxonsetInput.get();
                    if (taxa != null && taxa.contains(obj)) {
                        return false;
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
        if (context.getUsedDistributions().contains(obj)) return false;
        if (context.getInlinedDistributions().contains(obj)) return false;
        if (context.getRandomCompositionParameters().contains(obj)) return false;

        // Skip operators
        if (obj instanceof beast.base.inference.Operator) return false;

        if (obj instanceof CompoundDistribution) {
            String id = obj.getID();
            if (id != null && (id.equals("prior") || id.equals("likelihood") || id.equals("posterior"))) {
                return false;
            }
        }

        return true;
    }

    private boolean isFixedParameter(BEASTInterface obj, ConversionContext context) {
        if (!(obj instanceof RealParameter)) {
            return false;
        }

        for (beast.base.inference.StateNode stateNode : context.getState().stateNodeInput.get()) {
            if (stateNode == obj) {
                return false;
            }
        }

        for (BEASTInterface other : context.getObjectToIdMap().keySet()) {
            if (other instanceof Prior prior) {
                if (prior.m_x.get() == obj) {
                    return false;
                }
            }
        }

        return true;
    }

    private void addImportForClass(Class<?> clazz, ConversionContext context) {
        String packageName = clazz.getPackage().getName();

        for (ImportStatement existing : context.getModel().getImports()) {
            if (existing.getPackageName().equals(packageName) && existing.isWildcard()) {
                return;
            }
        }

        ImportStatement importStmt = new ImportStatement(packageName, true);
        context.getModel().addImport(importStmt);
    }

    @Override
    public String getName() {
        return "Remaining Objects Processing";
    }

    @Override
    public String getDescription() {
        return "Processes all objects not handled by previous phases";
    }
}
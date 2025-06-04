package org.beast2.modelLanguage.converter.pipeline;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.inference.StateNode;
import java.util.List;
import java.util.logging.Logger;

/**
 * Phase 2: Identify all objects in the BEAST graph and generate identifiers
 */
public class ObjectIdentificationPhase implements ConversionPhase {
    private static final Logger logger = Logger.getLogger(ObjectIdentificationPhase.class.getName());

    @Override
    public void execute(ConversionContext context) {
        logger.info("Identifying objects in BEAST graph...");

        // First, identify all state nodes
        for (StateNode node : context.getState().stateNodeInput.get()) {
            String id = context.generateIdentifier(node);
            context.getObjectToIdMap().put(node, id);
        }

        // Then process the entire object graph starting from posterior
        processObjectGraph(context.getPosterior(), context);

        logger.info("Identified " + context.getObjectToIdMap().size() + " objects");
    }

    private void processObjectGraph(BEASTInterface obj, ConversionContext context) {
        if (obj == null || context.getProcessedObjects().contains(obj)) {
            return;
        }

        context.getProcessedObjects().add(obj);

        // Generate identifier if not already present
        if (!context.getObjectToIdMap().containsKey(obj)) {
            String id = context.generateIdentifier(obj);
            context.getObjectToIdMap().put(obj, id);
        }

        // Recursively process all inputs
        for (Input<?> input : obj.getInputs().values()) {
            if (input.get() != null) {
                if (input.get() instanceof BEASTInterface) {
                    processObjectGraph((BEASTInterface) input.get(), context);
                } else if (input.get() instanceof List) {
                    for (Object item : (List<?>) input.get()) {
                        if (item instanceof BEASTInterface) {
                            processObjectGraph((BEASTInterface) item, context);
                        }
                    }
                }
            }
        }
    }

    @Override
    public String getName() {
        return "Object Identification";
    }

    @Override
    public String getDescription() {
        return "Traverses the BEAST object graph and assigns unique identifiers to all objects";
    }
}
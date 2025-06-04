package org.beast2.modelLanguage.converter.pipeline;

import org.beast2.modelLanguage.converter.StatementSorter;
import java.util.logging.Logger;

/**
 * Phase 10: Sort statements based on dependencies
 */
public class DependencySortingPhase implements ConversionPhase {
    private static final Logger logger = Logger.getLogger(DependencySortingPhase.class.getName());

    @Override
    public void execute(ConversionContext context) {
        logger.info("Sorting statements based on dependencies...");

        int beforeCount = context.getModel().getStatements().size();

        StatementSorter sorter = new StatementSorter();
        sorter.sortStatements(context.getModel());

        int afterCount = context.getModel().getStatements().size();

        if (beforeCount != afterCount) {
            logger.warning("Statement count changed during sorting: " + beforeCount + " -> " + afterCount);
        }

        logger.info("Statements sorted successfully");
    }

    @Override
    public String getName() {
        return "Dependency Sorting";
    }

    @Override
    public String getDescription() {
        return "Sorts statements to ensure dependencies are declared before use";
    }
}
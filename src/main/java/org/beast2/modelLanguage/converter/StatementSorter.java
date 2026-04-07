package org.beast2.modelLanguage.converter;

import org.beast2.modelLanguage.model.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * Handles sorting of statements based on dependencies
 */
public class StatementSorter {

    private static final Logger logger = Logger.getLogger(StatementSorter.class.getName());

    public void sortStatements(Beast2Model model) {
        List<Statement> sortedStatements = new ArrayList<>();
        Set<String> declaredIds = new HashSet<>();
        List<Statement> remainingStatements = new ArrayList<>(model.getStatements());

        // First pass: Add all @data statements (they have no dependencies)
        Iterator<Statement> iter = remainingStatements.iterator();
        while (iter.hasNext()) {
            Statement stmt = iter.next();
            if (stmt instanceof AnnotatedStatement annotatedStmt) {
                for (Annotation annotation : annotatedStmt.getAnnotations()) {
                    if ("data".equals(annotation.getName())) {
                        sortedStatements.add(stmt);
                        declaredIds.add(getStatementId(stmt));
                        iter.remove();
                        break;
                    }
                }
            }
        }

        // Repeatedly process remaining statements until all are sorted
        boolean progress = true;
        while (!remainingStatements.isEmpty() && progress) {
            progress = false;
            iter = remainingStatements.iterator();

            while (iter.hasNext()) {
                Statement stmt = iter.next();
                Set<String> dependencies = getStatementDependencies(stmt);

                if (declaredIds.containsAll(dependencies)) {
                    sortedStatements.add(stmt);
                    declaredIds.add(getStatementId(stmt));
                    iter.remove();
                    progress = true;
                }
            }
        }

        if (!remainingStatements.isEmpty()) {
            logger.warning("Circular dependency detected. Adding remaining statements in original order.");
            sortedStatements.addAll(remainingStatements);
        }

        model.clearStatements();
        for (Statement stmt : sortedStatements) {
            model.addStatement(stmt);
        }
    }

    private String getStatementId(Statement stmt) {
        if (stmt instanceof VariableDeclaration) {
            return ((VariableDeclaration) stmt).getVariableName();
        } else if (stmt instanceof DistributionAssignment) {
            return ((DistributionAssignment) stmt).getVariableName();
        } else if (stmt instanceof AnnotatedStatement) {
            return getStatementId(((AnnotatedStatement) stmt).getStatement());
        }
        return null;
    }

    private Set<String> getStatementDependencies(Statement stmt) {
        Set<String> dependencies = new HashSet<>();

        if (stmt instanceof AnnotatedStatement annotatedStmt) {
            for (Annotation annotation : annotatedStmt.getAnnotations()) {
                Map<String, Expression> params = annotation.getParameters();
                for (Expression value : params.values()) {
                    if (value instanceof Identifier id) {
                        dependencies.add(id.getName());
                    }
                }
            }
            dependencies.addAll(getStatementDependencies(annotatedStmt.getStatement()));
        } else if (stmt instanceof VariableDeclaration varDecl) {
            collectExpressionDependencies(varDecl.getValue(), dependencies);
        } else if (stmt instanceof DistributionAssignment distAssign) {
            collectExpressionDependencies(distAssign.getDistribution(), dependencies);
        }

        return dependencies;
    }

    private void collectExpressionDependencies(Expression expr, Set<String> dependencies) {
        if (expr instanceof Identifier) {
            dependencies.add(((Identifier) expr).getName());
        } else if (expr instanceof FunctionCall funcCall) {
            for (Argument arg : funcCall.getArguments()) {
                collectExpressionDependencies(arg.getValue(), dependencies);
            }
        } else if (expr instanceof ArrayLiteral array) {
            for (Expression element : array.getElements()) {
                collectExpressionDependencies(element, dependencies);
            }
        }
    }
}
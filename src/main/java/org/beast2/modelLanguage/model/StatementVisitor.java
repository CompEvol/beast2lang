package org.beast2.modelLanguage.model;

/**
 * Visitor interface for Statement objects
 */
public interface StatementVisitor {
    void visit(VariableDeclaration varDecl);
    void visit(DistributionAssignment distAssign);
    void visit(AnnotatedStatement annotatedStmt);
}
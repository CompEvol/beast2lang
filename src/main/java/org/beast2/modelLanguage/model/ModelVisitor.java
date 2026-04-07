package org.beast2.modelLanguage.model;

/**
 * Visitor interface for the Beast2 model
 */
public interface ModelVisitor<T> {
    T visit(Beast2Model model);
    T visit(VariableDeclaration varDecl);
    T visit(DistributionAssignment distAssign);
    T visit(FunctionCall funcCall);
    T visit(Identifier identifier);
    T visit(Literal literal);
    T visit(ArrayLiteral arrayLiteral);
    T visit(NexusFunction nexusFunction);
    T visit(AlignmentFunction alignmentFunction);
    T visit(MapExpression mapExpression);
}
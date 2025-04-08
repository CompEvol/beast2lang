package org.beast2.modelLanguage.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
}

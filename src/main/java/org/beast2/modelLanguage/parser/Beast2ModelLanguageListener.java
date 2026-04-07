package org.beast2.modelLanguage.parser;// Generated from Beast2ModelLanguage.g4 by ANTLR 4.10.1
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link Beast2ModelLanguageParser}.
 */
public interface Beast2ModelLanguageListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#program}.
	 * @param ctx the parse tree
	 */
	void enterProgram(Beast2ModelLanguageParser.ProgramContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#program}.
	 * @param ctx the parse tree
	 */
	void exitProgram(Beast2ModelLanguageParser.ProgramContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#importStatement}.
	 * @param ctx the parse tree
	 */
	void enterImportStatement(Beast2ModelLanguageParser.ImportStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#importStatement}.
	 * @param ctx the parse tree
	 */
	void exitImportStatement(Beast2ModelLanguageParser.ImportStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#requiresStatement}.
	 * @param ctx the parse tree
	 */
	void enterRequiresStatement(Beast2ModelLanguageParser.RequiresStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#requiresStatement}.
	 * @param ctx the parse tree
	 */
	void exitRequiresStatement(Beast2ModelLanguageParser.RequiresStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#importName}.
	 * @param ctx the parse tree
	 */
	void enterImportName(Beast2ModelLanguageParser.ImportNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#importName}.
	 * @param ctx the parse tree
	 */
	void exitImportName(Beast2ModelLanguageParser.ImportNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#pluginName}.
	 * @param ctx the parse tree
	 */
	void enterPluginName(Beast2ModelLanguageParser.PluginNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#pluginName}.
	 * @param ctx the parse tree
	 */
	void exitPluginName(Beast2ModelLanguageParser.PluginNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterStatement(Beast2ModelLanguageParser.StatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitStatement(Beast2ModelLanguageParser.StatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#annotation}.
	 * @param ctx the parse tree
	 */
	void enterAnnotation(Beast2ModelLanguageParser.AnnotationContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#annotation}.
	 * @param ctx the parse tree
	 */
	void exitAnnotation(Beast2ModelLanguageParser.AnnotationContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#annotationName}.
	 * @param ctx the parse tree
	 */
	void enterAnnotationName(Beast2ModelLanguageParser.AnnotationNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#annotationName}.
	 * @param ctx the parse tree
	 */
	void exitAnnotationName(Beast2ModelLanguageParser.AnnotationNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#annotationBody}.
	 * @param ctx the parse tree
	 */
	void enterAnnotationBody(Beast2ModelLanguageParser.AnnotationBodyContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#annotationBody}.
	 * @param ctx the parse tree
	 */
	void exitAnnotationBody(Beast2ModelLanguageParser.AnnotationBodyContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#annotationParameter}.
	 * @param ctx the parse tree
	 */
	void enterAnnotationParameter(Beast2ModelLanguageParser.AnnotationParameterContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#annotationParameter}.
	 * @param ctx the parse tree
	 */
	void exitAnnotationParameter(Beast2ModelLanguageParser.AnnotationParameterContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#variableDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterVariableDeclaration(Beast2ModelLanguageParser.VariableDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#variableDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitVariableDeclaration(Beast2ModelLanguageParser.VariableDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#distributionAssignment}.
	 * @param ctx the parse tree
	 */
	void enterDistributionAssignment(Beast2ModelLanguageParser.DistributionAssignmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#distributionAssignment}.
	 * @param ctx the parse tree
	 */
	void exitDistributionAssignment(Beast2ModelLanguageParser.DistributionAssignmentContext ctx);
	/**
	 * Enter a parse tree produced by the {@code FunctionCallExpr}
	 * labeled alternative in {@link Beast2ModelLanguageParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterFunctionCallExpr(Beast2ModelLanguageParser.FunctionCallExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code FunctionCallExpr}
	 * labeled alternative in {@link Beast2ModelLanguageParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitFunctionCallExpr(Beast2ModelLanguageParser.FunctionCallExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code NexusFunctionExpr}
	 * labeled alternative in {@link Beast2ModelLanguageParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterNexusFunctionExpr(Beast2ModelLanguageParser.NexusFunctionExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code NexusFunctionExpr}
	 * labeled alternative in {@link Beast2ModelLanguageParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitNexusFunctionExpr(Beast2ModelLanguageParser.NexusFunctionExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code AlignmentFunctionExpr}
	 * labeled alternative in {@link Beast2ModelLanguageParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterAlignmentFunctionExpr(Beast2ModelLanguageParser.AlignmentFunctionExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code AlignmentFunctionExpr}
	 * labeled alternative in {@link Beast2ModelLanguageParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitAlignmentFunctionExpr(Beast2ModelLanguageParser.AlignmentFunctionExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code MapExpr}
	 * labeled alternative in {@link Beast2ModelLanguageParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterMapExpr(Beast2ModelLanguageParser.MapExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code MapExpr}
	 * labeled alternative in {@link Beast2ModelLanguageParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitMapExpr(Beast2ModelLanguageParser.MapExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code IdentifierExpr}
	 * labeled alternative in {@link Beast2ModelLanguageParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterIdentifierExpr(Beast2ModelLanguageParser.IdentifierExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code IdentifierExpr}
	 * labeled alternative in {@link Beast2ModelLanguageParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitIdentifierExpr(Beast2ModelLanguageParser.IdentifierExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code LiteralExpr}
	 * labeled alternative in {@link Beast2ModelLanguageParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterLiteralExpr(Beast2ModelLanguageParser.LiteralExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code LiteralExpr}
	 * labeled alternative in {@link Beast2ModelLanguageParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitLiteralExpr(Beast2ModelLanguageParser.LiteralExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ArrayLiteralExpr}
	 * labeled alternative in {@link Beast2ModelLanguageParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterArrayLiteralExpr(Beast2ModelLanguageParser.ArrayLiteralExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ArrayLiteralExpr}
	 * labeled alternative in {@link Beast2ModelLanguageParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitArrayLiteralExpr(Beast2ModelLanguageParser.ArrayLiteralExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#functionCall}.
	 * @param ctx the parse tree
	 */
	void enterFunctionCall(Beast2ModelLanguageParser.FunctionCallContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#functionCall}.
	 * @param ctx the parse tree
	 */
	void exitFunctionCall(Beast2ModelLanguageParser.FunctionCallContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#nexusFunction}.
	 * @param ctx the parse tree
	 */
	void enterNexusFunction(Beast2ModelLanguageParser.NexusFunctionContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#nexusFunction}.
	 * @param ctx the parse tree
	 */
	void exitNexusFunction(Beast2ModelLanguageParser.NexusFunctionContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#alignmentFunction}.
	 * @param ctx the parse tree
	 */
	void enterAlignmentFunction(Beast2ModelLanguageParser.AlignmentFunctionContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#alignmentFunction}.
	 * @param ctx the parse tree
	 */
	void exitAlignmentFunction(Beast2ModelLanguageParser.AlignmentFunctionContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#mapExpression}.
	 * @param ctx the parse tree
	 */
	void enterMapExpression(Beast2ModelLanguageParser.MapExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#mapExpression}.
	 * @param ctx the parse tree
	 */
	void exitMapExpression(Beast2ModelLanguageParser.MapExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#mapEntry}.
	 * @param ctx the parse tree
	 */
	void enterMapEntry(Beast2ModelLanguageParser.MapEntryContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#mapEntry}.
	 * @param ctx the parse tree
	 */
	void exitMapEntry(Beast2ModelLanguageParser.MapEntryContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#argumentList}.
	 * @param ctx the parse tree
	 */
	void enterArgumentList(Beast2ModelLanguageParser.ArgumentListContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#argumentList}.
	 * @param ctx the parse tree
	 */
	void exitArgumentList(Beast2ModelLanguageParser.ArgumentListContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#argument}.
	 * @param ctx the parse tree
	 */
	void enterArgument(Beast2ModelLanguageParser.ArgumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#argument}.
	 * @param ctx the parse tree
	 */
	void exitArgument(Beast2ModelLanguageParser.ArgumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#argumentName}.
	 * @param ctx the parse tree
	 */
	void enterArgumentName(Beast2ModelLanguageParser.ArgumentNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#argumentName}.
	 * @param ctx the parse tree
	 */
	void exitArgumentName(Beast2ModelLanguageParser.ArgumentNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#argumentValue}.
	 * @param ctx the parse tree
	 */
	void enterArgumentValue(Beast2ModelLanguageParser.ArgumentValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#argumentValue}.
	 * @param ctx the parse tree
	 */
	void exitArgumentValue(Beast2ModelLanguageParser.ArgumentValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#arrayLiteral}.
	 * @param ctx the parse tree
	 */
	void enterArrayLiteral(Beast2ModelLanguageParser.ArrayLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#arrayLiteral}.
	 * @param ctx the parse tree
	 */
	void exitArrayLiteral(Beast2ModelLanguageParser.ArrayLiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#arrayElement}.
	 * @param ctx the parse tree
	 */
	void enterArrayElement(Beast2ModelLanguageParser.ArrayElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#arrayElement}.
	 * @param ctx the parse tree
	 */
	void exitArrayElement(Beast2ModelLanguageParser.ArrayElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#className}.
	 * @param ctx the parse tree
	 */
	void enterClassName(Beast2ModelLanguageParser.ClassNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#className}.
	 * @param ctx the parse tree
	 */
	void exitClassName(Beast2ModelLanguageParser.ClassNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#qualifiedName}.
	 * @param ctx the parse tree
	 */
	void enterQualifiedName(Beast2ModelLanguageParser.QualifiedNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#qualifiedName}.
	 * @param ctx the parse tree
	 */
	void exitQualifiedName(Beast2ModelLanguageParser.QualifiedNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#identifier}.
	 * @param ctx the parse tree
	 */
	void enterIdentifier(Beast2ModelLanguageParser.IdentifierContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#identifier}.
	 * @param ctx the parse tree
	 */
	void exitIdentifier(Beast2ModelLanguageParser.IdentifierContext ctx);
	/**
	 * Enter a parse tree produced by {@link Beast2ModelLanguageParser#literal}.
	 * @param ctx the parse tree
	 */
	void enterLiteral(Beast2ModelLanguageParser.LiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link Beast2ModelLanguageParser#literal}.
	 * @param ctx the parse tree
	 */
	void exitLiteral(Beast2ModelLanguageParser.LiteralContext ctx);
}
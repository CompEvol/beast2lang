package org.beast2.modelLanguage.parser;// Generated from Beast2ModelLanguage.g4 by ANTLR 4.10.1

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class Beast2ModelLanguageParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.10.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		TILDE=1, EQUALS=2, SEMICOLON=3, COMMA=4, LPAREN=5, RPAREN=6, LBRACE=7, 
		RBRACE=8, LBRACKET=9, RBRACKET=10, DOT=11, AT=12, STAR=13, COLON=14, IMPORT=15, 
		REQUIRES=16, NEXUS=17, ALIGNMENT=18, BOOLEAN_LITERAL=19, IDENTIFIER=20, 
		INTEGER_LITERAL=21, FLOAT_LITERAL=22, STRING_LITERAL=23, LINE_COMMENT=24, 
		BLOCK_COMMENT=25, WS=26;
	public static final int
		RULE_program = 0, RULE_importStatement = 1, RULE_requiresStatement = 2, 
		RULE_importName = 3, RULE_pluginName = 4, RULE_statement = 5, RULE_annotation = 6, 
		RULE_annotationName = 7, RULE_annotationBody = 8, RULE_annotationParameter = 9, 
		RULE_variableDeclaration = 10, RULE_distributionAssignment = 11, RULE_expression = 12, 
		RULE_functionCall = 13, RULE_nexusFunction = 14, RULE_alignmentFunction = 15, 
		RULE_mapExpression = 16, RULE_mapEntry = 17, RULE_argumentList = 18, RULE_argument = 19, 
		RULE_argumentName = 20, RULE_argumentValue = 21, RULE_arrayLiteral = 22, 
		RULE_arrayElement = 23, RULE_className = 24, RULE_qualifiedName = 25, 
		RULE_identifier = 26, RULE_literal = 27;
	private static String[] makeRuleNames() {
		return new String[] {
			"program", "importStatement", "requiresStatement", "importName", "pluginName", 
			"statement", "annotation", "annotationName", "annotationBody", "annotationParameter", 
			"variableDeclaration", "distributionAssignment", "expression", "functionCall", 
			"nexusFunction", "alignmentFunction", "mapExpression", "mapEntry", "argumentList", 
			"argument", "argumentName", "argumentValue", "arrayLiteral", "arrayElement", 
			"className", "qualifiedName", "identifier", "literal"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'~'", "'='", "';'", "','", "'('", "')'", "'{'", "'}'", "'['", 
			"']'", "'.'", "'@'", "'*'", "':'", "'import'", "'requires'", "'nexus'", 
			"'alignment'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "TILDE", "EQUALS", "SEMICOLON", "COMMA", "LPAREN", "RPAREN", "LBRACE", 
			"RBRACE", "LBRACKET", "RBRACKET", "DOT", "AT", "STAR", "COLON", "IMPORT", 
			"REQUIRES", "NEXUS", "ALIGNMENT", "BOOLEAN_LITERAL", "IDENTIFIER", "INTEGER_LITERAL", 
			"FLOAT_LITERAL", "STRING_LITERAL", "LINE_COMMENT", "BLOCK_COMMENT", "WS"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "Beast2ModelLanguage.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public Beast2ModelLanguageParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	public static class ProgramContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(Beast2ModelLanguageParser.EOF, 0); }
		public List<ImportStatementContext> importStatement() {
			return getRuleContexts(ImportStatementContext.class);
		}
		public ImportStatementContext importStatement(int i) {
			return getRuleContext(ImportStatementContext.class,i);
		}
		public List<RequiresStatementContext> requiresStatement() {
			return getRuleContexts(RequiresStatementContext.class);
		}
		public RequiresStatementContext requiresStatement(int i) {
			return getRuleContext(RequiresStatementContext.class,i);
		}
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public ProgramContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_program; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterProgram(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitProgram(this);
		}
	}

	public final ProgramContext program() throws RecognitionException {
		ProgramContext _localctx = new ProgramContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_program);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(60);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==IMPORT || _la==REQUIRES) {
				{
				setState(58);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case IMPORT:
					{
					setState(56);
					importStatement();
					}
					break;
				case REQUIRES:
					{
					setState(57);
					requiresStatement();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(62);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(64); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(63);
				statement();
				}
				}
				setState(66); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==AT || _la==IDENTIFIER );
			setState(68);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ImportStatementContext extends ParserRuleContext {
		public TerminalNode IMPORT() { return getToken(Beast2ModelLanguageParser.IMPORT, 0); }
		public ImportNameContext importName() {
			return getRuleContext(ImportNameContext.class,0);
		}
		public TerminalNode SEMICOLON() { return getToken(Beast2ModelLanguageParser.SEMICOLON, 0); }
		public ImportStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_importStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterImportStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitImportStatement(this);
		}
	}

	public final ImportStatementContext importStatement() throws RecognitionException {
		ImportStatementContext _localctx = new ImportStatementContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_importStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(70);
			match(IMPORT);
			setState(71);
			importName();
			setState(72);
			match(SEMICOLON);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class RequiresStatementContext extends ParserRuleContext {
		public TerminalNode REQUIRES() { return getToken(Beast2ModelLanguageParser.REQUIRES, 0); }
		public PluginNameContext pluginName() {
			return getRuleContext(PluginNameContext.class,0);
		}
		public TerminalNode SEMICOLON() { return getToken(Beast2ModelLanguageParser.SEMICOLON, 0); }
		public RequiresStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_requiresStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterRequiresStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitRequiresStatement(this);
		}
	}

	public final RequiresStatementContext requiresStatement() throws RecognitionException {
		RequiresStatementContext _localctx = new RequiresStatementContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_requiresStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(74);
			match(REQUIRES);
			setState(75);
			pluginName();
			setState(76);
			match(SEMICOLON);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ImportNameContext extends ParserRuleContext {
		public QualifiedNameContext qualifiedName() {
			return getRuleContext(QualifiedNameContext.class,0);
		}
		public TerminalNode DOT() { return getToken(Beast2ModelLanguageParser.DOT, 0); }
		public TerminalNode STAR() { return getToken(Beast2ModelLanguageParser.STAR, 0); }
		public ImportNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_importName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterImportName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitImportName(this);
		}
	}

	public final ImportNameContext importName() throws RecognitionException {
		ImportNameContext _localctx = new ImportNameContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_importName);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(78);
			qualifiedName();
			setState(81);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==DOT) {
				{
				setState(79);
				match(DOT);
				setState(80);
				match(STAR);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PluginNameContext extends ParserRuleContext {
		public List<TerminalNode> IDENTIFIER() { return getTokens(Beast2ModelLanguageParser.IDENTIFIER); }
		public TerminalNode IDENTIFIER(int i) {
			return getToken(Beast2ModelLanguageParser.IDENTIFIER, i);
		}
		public List<TerminalNode> DOT() { return getTokens(Beast2ModelLanguageParser.DOT); }
		public TerminalNode DOT(int i) {
			return getToken(Beast2ModelLanguageParser.DOT, i);
		}
		public PluginNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pluginName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterPluginName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitPluginName(this);
		}
	}

	public final PluginNameContext pluginName() throws RecognitionException {
		PluginNameContext _localctx = new PluginNameContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_pluginName);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(83);
			match(IDENTIFIER);
			setState(88);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==DOT) {
				{
				{
				setState(84);
				match(DOT);
				setState(85);
				match(IDENTIFIER);
				}
				}
				setState(90);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class StatementContext extends ParserRuleContext {
		public VariableDeclarationContext variableDeclaration() {
			return getRuleContext(VariableDeclarationContext.class,0);
		}
		public List<AnnotationContext> annotation() {
			return getRuleContexts(AnnotationContext.class);
		}
		public AnnotationContext annotation(int i) {
			return getRuleContext(AnnotationContext.class,i);
		}
		public DistributionAssignmentContext distributionAssignment() {
			return getRuleContext(DistributionAssignmentContext.class,0);
		}
		public StatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitStatement(this);
		}
	}

	public final StatementContext statement() throws RecognitionException {
		StatementContext _localctx = new StatementContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_statement);
		int _la;
		try {
			setState(105);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,7,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(94);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==AT) {
					{
					{
					setState(91);
					annotation();
					}
					}
					setState(96);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(97);
				variableDeclaration();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(101);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==AT) {
					{
					{
					setState(98);
					annotation();
					}
					}
					setState(103);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(104);
				distributionAssignment();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AnnotationContext extends ParserRuleContext {
		public TerminalNode AT() { return getToken(Beast2ModelLanguageParser.AT, 0); }
		public AnnotationNameContext annotationName() {
			return getRuleContext(AnnotationNameContext.class,0);
		}
		public AnnotationBodyContext annotationBody() {
			return getRuleContext(AnnotationBodyContext.class,0);
		}
		public AnnotationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_annotation; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterAnnotation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitAnnotation(this);
		}
	}

	public final AnnotationContext annotation() throws RecognitionException {
		AnnotationContext _localctx = new AnnotationContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_annotation);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(107);
			match(AT);
			setState(108);
			annotationName();
			setState(110);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LPAREN) {
				{
				setState(109);
				annotationBody();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AnnotationNameContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public AnnotationNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_annotationName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterAnnotationName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitAnnotationName(this);
		}
	}

	public final AnnotationNameContext annotationName() throws RecognitionException {
		AnnotationNameContext _localctx = new AnnotationNameContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_annotationName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(112);
			identifier();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AnnotationBodyContext extends ParserRuleContext {
		public TerminalNode LPAREN() { return getToken(Beast2ModelLanguageParser.LPAREN, 0); }
		public List<AnnotationParameterContext> annotationParameter() {
			return getRuleContexts(AnnotationParameterContext.class);
		}
		public AnnotationParameterContext annotationParameter(int i) {
			return getRuleContext(AnnotationParameterContext.class,i);
		}
		public TerminalNode RPAREN() { return getToken(Beast2ModelLanguageParser.RPAREN, 0); }
		public List<TerminalNode> COMMA() { return getTokens(Beast2ModelLanguageParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(Beast2ModelLanguageParser.COMMA, i);
		}
		public AnnotationBodyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_annotationBody; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterAnnotationBody(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitAnnotationBody(this);
		}
	}

	public final AnnotationBodyContext annotationBody() throws RecognitionException {
		AnnotationBodyContext _localctx = new AnnotationBodyContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_annotationBody);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(114);
			match(LPAREN);
			setState(115);
			annotationParameter();
			setState(120);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(116);
				match(COMMA);
				setState(117);
				annotationParameter();
				}
				}
				setState(122);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(123);
			match(RPAREN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AnnotationParameterContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode EQUALS() { return getToken(Beast2ModelLanguageParser.EQUALS, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public AnnotationParameterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_annotationParameter; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterAnnotationParameter(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitAnnotationParameter(this);
		}
	}

	public final AnnotationParameterContext annotationParameter() throws RecognitionException {
		AnnotationParameterContext _localctx = new AnnotationParameterContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_annotationParameter);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(125);
			identifier();
			setState(126);
			match(EQUALS);
			setState(127);
			expression();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class VariableDeclarationContext extends ParserRuleContext {
		public ClassNameContext className() {
			return getRuleContext(ClassNameContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode EQUALS() { return getToken(Beast2ModelLanguageParser.EQUALS, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode SEMICOLON() { return getToken(Beast2ModelLanguageParser.SEMICOLON, 0); }
		public VariableDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_variableDeclaration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterVariableDeclaration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitVariableDeclaration(this);
		}
	}

	public final VariableDeclarationContext variableDeclaration() throws RecognitionException {
		VariableDeclarationContext _localctx = new VariableDeclarationContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_variableDeclaration);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(129);
			className();
			setState(130);
			identifier();
			setState(131);
			match(EQUALS);
			setState(132);
			expression();
			setState(133);
			match(SEMICOLON);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class DistributionAssignmentContext extends ParserRuleContext {
		public ClassNameContext className() {
			return getRuleContext(ClassNameContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode TILDE() { return getToken(Beast2ModelLanguageParser.TILDE, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode SEMICOLON() { return getToken(Beast2ModelLanguageParser.SEMICOLON, 0); }
		public DistributionAssignmentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_distributionAssignment; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterDistributionAssignment(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitDistributionAssignment(this);
		}
	}

	public final DistributionAssignmentContext distributionAssignment() throws RecognitionException {
		DistributionAssignmentContext _localctx = new DistributionAssignmentContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_distributionAssignment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(135);
			className();
			setState(136);
			identifier();
			setState(137);
			match(TILDE);
			setState(138);
			expression();
			setState(139);
			match(SEMICOLON);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ExpressionContext extends ParserRuleContext {
		public ExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression; }
	 
		public ExpressionContext() { }
		public void copyFrom(ExpressionContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class MapExprContext extends ExpressionContext {
		public MapExpressionContext mapExpression() {
			return getRuleContext(MapExpressionContext.class,0);
		}
		public MapExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterMapExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitMapExpr(this);
		}
	}
	public static class FunctionCallExprContext extends ExpressionContext {
		public FunctionCallContext functionCall() {
			return getRuleContext(FunctionCallContext.class,0);
		}
		public FunctionCallExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterFunctionCallExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitFunctionCallExpr(this);
		}
	}
	public static class NexusFunctionExprContext extends ExpressionContext {
		public NexusFunctionContext nexusFunction() {
			return getRuleContext(NexusFunctionContext.class,0);
		}
		public NexusFunctionExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterNexusFunctionExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitNexusFunctionExpr(this);
		}
	}
	public static class AlignmentFunctionExprContext extends ExpressionContext {
		public AlignmentFunctionContext alignmentFunction() {
			return getRuleContext(AlignmentFunctionContext.class,0);
		}
		public AlignmentFunctionExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterAlignmentFunctionExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitAlignmentFunctionExpr(this);
		}
	}
	public static class IdentifierExprContext extends ExpressionContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public IdentifierExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterIdentifierExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitIdentifierExpr(this);
		}
	}
	public static class ArrayLiteralExprContext extends ExpressionContext {
		public ArrayLiteralContext arrayLiteral() {
			return getRuleContext(ArrayLiteralContext.class,0);
		}
		public ArrayLiteralExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterArrayLiteralExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitArrayLiteralExpr(this);
		}
	}
	public static class LiteralExprContext extends ExpressionContext {
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public LiteralExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterLiteralExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitLiteralExpr(this);
		}
	}

	public final ExpressionContext expression() throws RecognitionException {
		ExpressionContext _localctx = new ExpressionContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_expression);
		try {
			setState(148);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,10,_ctx) ) {
			case 1:
				_localctx = new FunctionCallExprContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(141);
				functionCall();
				}
				break;
			case 2:
				_localctx = new NexusFunctionExprContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(142);
				nexusFunction();
				}
				break;
			case 3:
				_localctx = new AlignmentFunctionExprContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(143);
				alignmentFunction();
				}
				break;
			case 4:
				_localctx = new MapExprContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(144);
				mapExpression();
				}
				break;
			case 5:
				_localctx = new IdentifierExprContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(145);
				identifier();
				}
				break;
			case 6:
				_localctx = new LiteralExprContext(_localctx);
				enterOuterAlt(_localctx, 6);
				{
				setState(146);
				literal();
				}
				break;
			case 7:
				_localctx = new ArrayLiteralExprContext(_localctx);
				enterOuterAlt(_localctx, 7);
				{
				setState(147);
				arrayLiteral();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class FunctionCallContext extends ParserRuleContext {
		public ClassNameContext className() {
			return getRuleContext(ClassNameContext.class,0);
		}
		public TerminalNode LPAREN() { return getToken(Beast2ModelLanguageParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(Beast2ModelLanguageParser.RPAREN, 0); }
		public ArgumentListContext argumentList() {
			return getRuleContext(ArgumentListContext.class,0);
		}
		public FunctionCallContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionCall; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterFunctionCall(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitFunctionCall(this);
		}
	}

	public final FunctionCallContext functionCall() throws RecognitionException {
		FunctionCallContext _localctx = new FunctionCallContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_functionCall);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(150);
			className();
			setState(151);
			match(LPAREN);
			setState(153);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==IDENTIFIER) {
				{
				setState(152);
				argumentList();
				}
			}

			setState(155);
			match(RPAREN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class NexusFunctionContext extends ParserRuleContext {
		public TerminalNode NEXUS() { return getToken(Beast2ModelLanguageParser.NEXUS, 0); }
		public TerminalNode LPAREN() { return getToken(Beast2ModelLanguageParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(Beast2ModelLanguageParser.RPAREN, 0); }
		public ArgumentListContext argumentList() {
			return getRuleContext(ArgumentListContext.class,0);
		}
		public NexusFunctionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_nexusFunction; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterNexusFunction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitNexusFunction(this);
		}
	}

	public final NexusFunctionContext nexusFunction() throws RecognitionException {
		NexusFunctionContext _localctx = new NexusFunctionContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_nexusFunction);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(157);
			match(NEXUS);
			setState(158);
			match(LPAREN);
			setState(160);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==IDENTIFIER) {
				{
				setState(159);
				argumentList();
				}
			}

			setState(162);
			match(RPAREN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AlignmentFunctionContext extends ParserRuleContext {
		public TerminalNode ALIGNMENT() { return getToken(Beast2ModelLanguageParser.ALIGNMENT, 0); }
		public TerminalNode LPAREN() { return getToken(Beast2ModelLanguageParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(Beast2ModelLanguageParser.RPAREN, 0); }
		public ArgumentListContext argumentList() {
			return getRuleContext(ArgumentListContext.class,0);
		}
		public AlignmentFunctionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_alignmentFunction; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterAlignmentFunction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitAlignmentFunction(this);
		}
	}

	public final AlignmentFunctionContext alignmentFunction() throws RecognitionException {
		AlignmentFunctionContext _localctx = new AlignmentFunctionContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_alignmentFunction);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(164);
			match(ALIGNMENT);
			setState(165);
			match(LPAREN);
			setState(167);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==IDENTIFIER) {
				{
				setState(166);
				argumentList();
				}
			}

			setState(169);
			match(RPAREN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class MapExpressionContext extends ParserRuleContext {
		public TerminalNode LBRACE() { return getToken(Beast2ModelLanguageParser.LBRACE, 0); }
		public List<MapEntryContext> mapEntry() {
			return getRuleContexts(MapEntryContext.class);
		}
		public MapEntryContext mapEntry(int i) {
			return getRuleContext(MapEntryContext.class,i);
		}
		public TerminalNode RBRACE() { return getToken(Beast2ModelLanguageParser.RBRACE, 0); }
		public List<TerminalNode> COMMA() { return getTokens(Beast2ModelLanguageParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(Beast2ModelLanguageParser.COMMA, i);
		}
		public MapExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mapExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterMapExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitMapExpression(this);
		}
	}

	public final MapExpressionContext mapExpression() throws RecognitionException {
		MapExpressionContext _localctx = new MapExpressionContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_mapExpression);
		int _la;
		try {
			setState(184);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,15,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(171);
				match(LBRACE);
				setState(172);
				mapEntry();
				setState(177);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(173);
					match(COMMA);
					setState(174);
					mapEntry();
					}
					}
					setState(179);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(180);
				match(RBRACE);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(182);
				match(LBRACE);
				setState(183);
				match(RBRACE);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class MapEntryContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode COLON() { return getToken(Beast2ModelLanguageParser.COLON, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public MapEntryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mapEntry; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterMapEntry(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitMapEntry(this);
		}
	}

	public final MapEntryContext mapEntry() throws RecognitionException {
		MapEntryContext _localctx = new MapEntryContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_mapEntry);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(186);
			identifier();
			setState(187);
			match(COLON);
			setState(188);
			expression();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ArgumentListContext extends ParserRuleContext {
		public List<ArgumentContext> argument() {
			return getRuleContexts(ArgumentContext.class);
		}
		public ArgumentContext argument(int i) {
			return getRuleContext(ArgumentContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(Beast2ModelLanguageParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(Beast2ModelLanguageParser.COMMA, i);
		}
		public ArgumentListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_argumentList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterArgumentList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitArgumentList(this);
		}
	}

	public final ArgumentListContext argumentList() throws RecognitionException {
		ArgumentListContext _localctx = new ArgumentListContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_argumentList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(190);
			argument();
			setState(195);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(191);
				match(COMMA);
				setState(192);
				argument();
				}
				}
				setState(197);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ArgumentContext extends ParserRuleContext {
		public ArgumentNameContext argumentName() {
			return getRuleContext(ArgumentNameContext.class,0);
		}
		public TerminalNode EQUALS() { return getToken(Beast2ModelLanguageParser.EQUALS, 0); }
		public ArgumentValueContext argumentValue() {
			return getRuleContext(ArgumentValueContext.class,0);
		}
		public ArgumentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_argument; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterArgument(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitArgument(this);
		}
	}

	public final ArgumentContext argument() throws RecognitionException {
		ArgumentContext _localctx = new ArgumentContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_argument);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(198);
			argumentName();
			setState(199);
			match(EQUALS);
			setState(200);
			argumentValue();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ArgumentNameContext extends ParserRuleContext {
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public List<TerminalNode> DOT() { return getTokens(Beast2ModelLanguageParser.DOT); }
		public TerminalNode DOT(int i) {
			return getToken(Beast2ModelLanguageParser.DOT, i);
		}
		public ArgumentNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_argumentName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterArgumentName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitArgumentName(this);
		}
	}

	public final ArgumentNameContext argumentName() throws RecognitionException {
		ArgumentNameContext _localctx = new ArgumentNameContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_argumentName);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(202);
			identifier();
			setState(207);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==DOT) {
				{
				{
				setState(203);
				match(DOT);
				setState(204);
				identifier();
				}
				}
				setState(209);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ArgumentValueContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public ArrayLiteralContext arrayLiteral() {
			return getRuleContext(ArrayLiteralContext.class,0);
		}
		public ArgumentValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_argumentValue; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterArgumentValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitArgumentValue(this);
		}
	}

	public final ArgumentValueContext argumentValue() throws RecognitionException {
		ArgumentValueContext _localctx = new ArgumentValueContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_argumentValue);
		try {
			setState(213);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,18,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(210);
				expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(211);
				literal();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(212);
				arrayLiteral();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ArrayLiteralContext extends ParserRuleContext {
		public TerminalNode LBRACKET() { return getToken(Beast2ModelLanguageParser.LBRACKET, 0); }
		public TerminalNode RBRACKET() { return getToken(Beast2ModelLanguageParser.RBRACKET, 0); }
		public List<ArrayElementContext> arrayElement() {
			return getRuleContexts(ArrayElementContext.class);
		}
		public ArrayElementContext arrayElement(int i) {
			return getRuleContext(ArrayElementContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(Beast2ModelLanguageParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(Beast2ModelLanguageParser.COMMA, i);
		}
		public ArrayLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterArrayLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitArrayLiteral(this);
		}
	}

	public final ArrayLiteralContext arrayLiteral() throws RecognitionException {
		ArrayLiteralContext _localctx = new ArrayLiteralContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_arrayLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(215);
			match(LBRACKET);
			setState(224);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << BOOLEAN_LITERAL) | (1L << IDENTIFIER) | (1L << INTEGER_LITERAL) | (1L << FLOAT_LITERAL) | (1L << STRING_LITERAL))) != 0)) {
				{
				setState(216);
				arrayElement();
				setState(221);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(217);
					match(COMMA);
					setState(218);
					arrayElement();
					}
					}
					setState(223);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(226);
			match(RBRACKET);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ArrayElementContext extends ParserRuleContext {
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public FunctionCallContext functionCall() {
			return getRuleContext(FunctionCallContext.class,0);
		}
		public ArrayElementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayElement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterArrayElement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitArrayElement(this);
		}
	}

	public final ArrayElementContext arrayElement() throws RecognitionException {
		ArrayElementContext _localctx = new ArrayElementContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_arrayElement);
		try {
			setState(231);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,21,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(228);
				literal();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(229);
				identifier();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(230);
				functionCall();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ClassNameContext extends ParserRuleContext {
		public QualifiedNameContext qualifiedName() {
			return getRuleContext(QualifiedNameContext.class,0);
		}
		public List<TerminalNode> LBRACKET() { return getTokens(Beast2ModelLanguageParser.LBRACKET); }
		public TerminalNode LBRACKET(int i) {
			return getToken(Beast2ModelLanguageParser.LBRACKET, i);
		}
		public List<TerminalNode> RBRACKET() { return getTokens(Beast2ModelLanguageParser.RBRACKET); }
		public TerminalNode RBRACKET(int i) {
			return getToken(Beast2ModelLanguageParser.RBRACKET, i);
		}
		public ClassNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_className; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterClassName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitClassName(this);
		}
	}

	public final ClassNameContext className() throws RecognitionException {
		ClassNameContext _localctx = new ClassNameContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_className);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(233);
			qualifiedName();
			setState(238);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LBRACKET) {
				{
				{
				setState(234);
				match(LBRACKET);
				setState(235);
				match(RBRACKET);
				}
				}
				setState(240);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class QualifiedNameContext extends ParserRuleContext {
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public List<TerminalNode> DOT() { return getTokens(Beast2ModelLanguageParser.DOT); }
		public TerminalNode DOT(int i) {
			return getToken(Beast2ModelLanguageParser.DOT, i);
		}
		public QualifiedNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_qualifiedName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterQualifiedName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitQualifiedName(this);
		}
	}

	public final QualifiedNameContext qualifiedName() throws RecognitionException {
		QualifiedNameContext _localctx = new QualifiedNameContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_qualifiedName);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(241);
			identifier();
			setState(246);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,23,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(242);
					match(DOT);
					setState(243);
					identifier();
					}
					} 
				}
				setState(248);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,23,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class IdentifierContext extends ParserRuleContext {
		public TerminalNode IDENTIFIER() { return getToken(Beast2ModelLanguageParser.IDENTIFIER, 0); }
		public IdentifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identifier; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterIdentifier(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitIdentifier(this);
		}
	}

	public final IdentifierContext identifier() throws RecognitionException {
		IdentifierContext _localctx = new IdentifierContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_identifier);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(249);
			match(IDENTIFIER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class LiteralContext extends ParserRuleContext {
		public TerminalNode FLOAT_LITERAL() { return getToken(Beast2ModelLanguageParser.FLOAT_LITERAL, 0); }
		public TerminalNode INTEGER_LITERAL() { return getToken(Beast2ModelLanguageParser.INTEGER_LITERAL, 0); }
		public TerminalNode STRING_LITERAL() { return getToken(Beast2ModelLanguageParser.STRING_LITERAL, 0); }
		public TerminalNode BOOLEAN_LITERAL() { return getToken(Beast2ModelLanguageParser.BOOLEAN_LITERAL, 0); }
		public LiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_literal; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).enterLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof Beast2ModelLanguageListener ) ((Beast2ModelLanguageListener)listener).exitLiteral(this);
		}
	}

	public final LiteralContext literal() throws RecognitionException {
		LiteralContext _localctx = new LiteralContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_literal);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(251);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << BOOLEAN_LITERAL) | (1L << INTEGER_LITERAL) | (1L << FLOAT_LITERAL) | (1L << STRING_LITERAL))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\u0004\u0001\u001a\u00fe\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001"+
		"\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004"+
		"\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007"+
		"\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b"+
		"\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007"+
		"\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007"+
		"\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007"+
		"\u0015\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007"+
		"\u0018\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007"+
		"\u001b\u0001\u0000\u0001\u0000\u0005\u0000;\b\u0000\n\u0000\f\u0000>\t"+
		"\u0000\u0001\u0000\u0004\u0000A\b\u0000\u000b\u0000\f\u0000B\u0001\u0000"+
		"\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0003"+
		"\u0003\u0003R\b\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0005\u0004"+
		"W\b\u0004\n\u0004\f\u0004Z\t\u0004\u0001\u0005\u0005\u0005]\b\u0005\n"+
		"\u0005\f\u0005`\t\u0005\u0001\u0005\u0001\u0005\u0005\u0005d\b\u0005\n"+
		"\u0005\f\u0005g\t\u0005\u0001\u0005\u0003\u0005j\b\u0005\u0001\u0006\u0001"+
		"\u0006\u0001\u0006\u0003\u0006o\b\u0006\u0001\u0007\u0001\u0007\u0001"+
		"\b\u0001\b\u0001\b\u0001\b\u0005\bw\b\b\n\b\f\bz\t\b\u0001\b\u0001\b\u0001"+
		"\t\u0001\t\u0001\t\u0001\t\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001"+
		"\n\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0003\f\u0095"+
		"\b\f\u0001\r\u0001\r\u0001\r\u0003\r\u009a\b\r\u0001\r\u0001\r\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0003\u000e\u00a1\b\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000f\u0001\u000f\u0001\u000f\u0003\u000f\u00a8\b\u000f\u0001\u000f"+
		"\u0001\u000f\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0005\u0010"+
		"\u00b0\b\u0010\n\u0010\f\u0010\u00b3\t\u0010\u0001\u0010\u0001\u0010\u0001"+
		"\u0010\u0001\u0010\u0003\u0010\u00b9\b\u0010\u0001\u0011\u0001\u0011\u0001"+
		"\u0011\u0001\u0011\u0001\u0012\u0001\u0012\u0001\u0012\u0005\u0012\u00c2"+
		"\b\u0012\n\u0012\f\u0012\u00c5\t\u0012\u0001\u0013\u0001\u0013\u0001\u0013"+
		"\u0001\u0013\u0001\u0014\u0001\u0014\u0001\u0014\u0005\u0014\u00ce\b\u0014"+
		"\n\u0014\f\u0014\u00d1\t\u0014\u0001\u0015\u0001\u0015\u0001\u0015\u0003"+
		"\u0015\u00d6\b\u0015\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0005"+
		"\u0016\u00dc\b\u0016\n\u0016\f\u0016\u00df\t\u0016\u0003\u0016\u00e1\b"+
		"\u0016\u0001\u0016\u0001\u0016\u0001\u0017\u0001\u0017\u0001\u0017\u0003"+
		"\u0017\u00e8\b\u0017\u0001\u0018\u0001\u0018\u0001\u0018\u0005\u0018\u00ed"+
		"\b\u0018\n\u0018\f\u0018\u00f0\t\u0018\u0001\u0019\u0001\u0019\u0001\u0019"+
		"\u0005\u0019\u00f5\b\u0019\n\u0019\f\u0019\u00f8\t\u0019\u0001\u001a\u0001"+
		"\u001a\u0001\u001b\u0001\u001b\u0001\u001b\u0000\u0000\u001c\u0000\u0002"+
		"\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018\u001a\u001c\u001e"+
		" \"$&(*,.0246\u0000\u0001\u0002\u0000\u0013\u0013\u0015\u0017\u0100\u0000"+
		"<\u0001\u0000\u0000\u0000\u0002F\u0001\u0000\u0000\u0000\u0004J\u0001"+
		"\u0000\u0000\u0000\u0006N\u0001\u0000\u0000\u0000\bS\u0001\u0000\u0000"+
		"\u0000\ni\u0001\u0000\u0000\u0000\fk\u0001\u0000\u0000\u0000\u000ep\u0001"+
		"\u0000\u0000\u0000\u0010r\u0001\u0000\u0000\u0000\u0012}\u0001\u0000\u0000"+
		"\u0000\u0014\u0081\u0001\u0000\u0000\u0000\u0016\u0087\u0001\u0000\u0000"+
		"\u0000\u0018\u0094\u0001\u0000\u0000\u0000\u001a\u0096\u0001\u0000\u0000"+
		"\u0000\u001c\u009d\u0001\u0000\u0000\u0000\u001e\u00a4\u0001\u0000\u0000"+
		"\u0000 \u00b8\u0001\u0000\u0000\u0000\"\u00ba\u0001\u0000\u0000\u0000"+
		"$\u00be\u0001\u0000\u0000\u0000&\u00c6\u0001\u0000\u0000\u0000(\u00ca"+
		"\u0001\u0000\u0000\u0000*\u00d5\u0001\u0000\u0000\u0000,\u00d7\u0001\u0000"+
		"\u0000\u0000.\u00e7\u0001\u0000\u0000\u00000\u00e9\u0001\u0000\u0000\u0000"+
		"2\u00f1\u0001\u0000\u0000\u00004\u00f9\u0001\u0000\u0000\u00006\u00fb"+
		"\u0001\u0000\u0000\u00008;\u0003\u0002\u0001\u00009;\u0003\u0004\u0002"+
		"\u0000:8\u0001\u0000\u0000\u0000:9\u0001\u0000\u0000\u0000;>\u0001\u0000"+
		"\u0000\u0000<:\u0001\u0000\u0000\u0000<=\u0001\u0000\u0000\u0000=@\u0001"+
		"\u0000\u0000\u0000><\u0001\u0000\u0000\u0000?A\u0003\n\u0005\u0000@?\u0001"+
		"\u0000\u0000\u0000AB\u0001\u0000\u0000\u0000B@\u0001\u0000\u0000\u0000"+
		"BC\u0001\u0000\u0000\u0000CD\u0001\u0000\u0000\u0000DE\u0005\u0000\u0000"+
		"\u0001E\u0001\u0001\u0000\u0000\u0000FG\u0005\u000f\u0000\u0000GH\u0003"+
		"\u0006\u0003\u0000HI\u0005\u0003\u0000\u0000I\u0003\u0001\u0000\u0000"+
		"\u0000JK\u0005\u0010\u0000\u0000KL\u0003\b\u0004\u0000LM\u0005\u0003\u0000"+
		"\u0000M\u0005\u0001\u0000\u0000\u0000NQ\u00032\u0019\u0000OP\u0005\u000b"+
		"\u0000\u0000PR\u0005\r\u0000\u0000QO\u0001\u0000\u0000\u0000QR\u0001\u0000"+
		"\u0000\u0000R\u0007\u0001\u0000\u0000\u0000SX\u0005\u0014\u0000\u0000"+
		"TU\u0005\u000b\u0000\u0000UW\u0005\u0014\u0000\u0000VT\u0001\u0000\u0000"+
		"\u0000WZ\u0001\u0000\u0000\u0000XV\u0001\u0000\u0000\u0000XY\u0001\u0000"+
		"\u0000\u0000Y\t\u0001\u0000\u0000\u0000ZX\u0001\u0000\u0000\u0000[]\u0003"+
		"\f\u0006\u0000\\[\u0001\u0000\u0000\u0000]`\u0001\u0000\u0000\u0000^\\"+
		"\u0001\u0000\u0000\u0000^_\u0001\u0000\u0000\u0000_a\u0001\u0000\u0000"+
		"\u0000`^\u0001\u0000\u0000\u0000aj\u0003\u0014\n\u0000bd\u0003\f\u0006"+
		"\u0000cb\u0001\u0000\u0000\u0000dg\u0001\u0000\u0000\u0000ec\u0001\u0000"+
		"\u0000\u0000ef\u0001\u0000\u0000\u0000fh\u0001\u0000\u0000\u0000ge\u0001"+
		"\u0000\u0000\u0000hj\u0003\u0016\u000b\u0000i^\u0001\u0000\u0000\u0000"+
		"ie\u0001\u0000\u0000\u0000j\u000b\u0001\u0000\u0000\u0000kl\u0005\f\u0000"+
		"\u0000ln\u0003\u000e\u0007\u0000mo\u0003\u0010\b\u0000nm\u0001\u0000\u0000"+
		"\u0000no\u0001\u0000\u0000\u0000o\r\u0001\u0000\u0000\u0000pq\u00034\u001a"+
		"\u0000q\u000f\u0001\u0000\u0000\u0000rs\u0005\u0005\u0000\u0000sx\u0003"+
		"\u0012\t\u0000tu\u0005\u0004\u0000\u0000uw\u0003\u0012\t\u0000vt\u0001"+
		"\u0000\u0000\u0000wz\u0001\u0000\u0000\u0000xv\u0001\u0000\u0000\u0000"+
		"xy\u0001\u0000\u0000\u0000y{\u0001\u0000\u0000\u0000zx\u0001\u0000\u0000"+
		"\u0000{|\u0005\u0006\u0000\u0000|\u0011\u0001\u0000\u0000\u0000}~\u0003"+
		"4\u001a\u0000~\u007f\u0005\u0002\u0000\u0000\u007f\u0080\u0003\u0018\f"+
		"\u0000\u0080\u0013\u0001\u0000\u0000\u0000\u0081\u0082\u00030\u0018\u0000"+
		"\u0082\u0083\u00034\u001a\u0000\u0083\u0084\u0005\u0002\u0000\u0000\u0084"+
		"\u0085\u0003\u0018\f\u0000\u0085\u0086\u0005\u0003\u0000\u0000\u0086\u0015"+
		"\u0001\u0000\u0000\u0000\u0087\u0088\u00030\u0018\u0000\u0088\u0089\u0003"+
		"4\u001a\u0000\u0089\u008a\u0005\u0001\u0000\u0000\u008a\u008b\u0003\u0018"+
		"\f\u0000\u008b\u008c\u0005\u0003\u0000\u0000\u008c\u0017\u0001\u0000\u0000"+
		"\u0000\u008d\u0095\u0003\u001a\r\u0000\u008e\u0095\u0003\u001c\u000e\u0000"+
		"\u008f\u0095\u0003\u001e\u000f\u0000\u0090\u0095\u0003 \u0010\u0000\u0091"+
		"\u0095\u00034\u001a\u0000\u0092\u0095\u00036\u001b\u0000\u0093\u0095\u0003"+
		",\u0016\u0000\u0094\u008d\u0001\u0000\u0000\u0000\u0094\u008e\u0001\u0000"+
		"\u0000\u0000\u0094\u008f\u0001\u0000\u0000\u0000\u0094\u0090\u0001\u0000"+
		"\u0000\u0000\u0094\u0091\u0001\u0000\u0000\u0000\u0094\u0092\u0001\u0000"+
		"\u0000\u0000\u0094\u0093\u0001\u0000\u0000\u0000\u0095\u0019\u0001\u0000"+
		"\u0000\u0000\u0096\u0097\u00030\u0018\u0000\u0097\u0099\u0005\u0005\u0000"+
		"\u0000\u0098\u009a\u0003$\u0012\u0000\u0099\u0098\u0001\u0000\u0000\u0000"+
		"\u0099\u009a\u0001\u0000\u0000\u0000\u009a\u009b\u0001\u0000\u0000\u0000"+
		"\u009b\u009c\u0005\u0006\u0000\u0000\u009c\u001b\u0001\u0000\u0000\u0000"+
		"\u009d\u009e\u0005\u0011\u0000\u0000\u009e\u00a0\u0005\u0005\u0000\u0000"+
		"\u009f\u00a1\u0003$\u0012\u0000\u00a0\u009f\u0001\u0000\u0000\u0000\u00a0"+
		"\u00a1\u0001\u0000\u0000\u0000\u00a1\u00a2\u0001\u0000\u0000\u0000\u00a2"+
		"\u00a3\u0005\u0006\u0000\u0000\u00a3\u001d\u0001\u0000\u0000\u0000\u00a4"+
		"\u00a5\u0005\u0012\u0000\u0000\u00a5\u00a7\u0005\u0005\u0000\u0000\u00a6"+
		"\u00a8\u0003$\u0012\u0000\u00a7\u00a6\u0001\u0000\u0000\u0000\u00a7\u00a8"+
		"\u0001\u0000\u0000\u0000\u00a8\u00a9\u0001\u0000\u0000\u0000\u00a9\u00aa"+
		"\u0005\u0006\u0000\u0000\u00aa\u001f\u0001\u0000\u0000\u0000\u00ab\u00ac"+
		"\u0005\u0007\u0000\u0000\u00ac\u00b1\u0003\"\u0011\u0000\u00ad\u00ae\u0005"+
		"\u0004\u0000\u0000\u00ae\u00b0\u0003\"\u0011\u0000\u00af\u00ad\u0001\u0000"+
		"\u0000\u0000\u00b0\u00b3\u0001\u0000\u0000\u0000\u00b1\u00af\u0001\u0000"+
		"\u0000\u0000\u00b1\u00b2\u0001\u0000\u0000\u0000\u00b2\u00b4\u0001\u0000"+
		"\u0000\u0000\u00b3\u00b1\u0001\u0000\u0000\u0000\u00b4\u00b5\u0005\b\u0000"+
		"\u0000\u00b5\u00b9\u0001\u0000\u0000\u0000\u00b6\u00b7\u0005\u0007\u0000"+
		"\u0000\u00b7\u00b9\u0005\b\u0000\u0000\u00b8\u00ab\u0001\u0000\u0000\u0000"+
		"\u00b8\u00b6\u0001\u0000\u0000\u0000\u00b9!\u0001\u0000\u0000\u0000\u00ba"+
		"\u00bb\u00034\u001a\u0000\u00bb\u00bc\u0005\u000e\u0000\u0000\u00bc\u00bd"+
		"\u0003\u0018\f\u0000\u00bd#\u0001\u0000\u0000\u0000\u00be\u00c3\u0003"+
		"&\u0013\u0000\u00bf\u00c0\u0005\u0004\u0000\u0000\u00c0\u00c2\u0003&\u0013"+
		"\u0000\u00c1\u00bf\u0001\u0000\u0000\u0000\u00c2\u00c5\u0001\u0000\u0000"+
		"\u0000\u00c3\u00c1\u0001\u0000\u0000\u0000\u00c3\u00c4\u0001\u0000\u0000"+
		"\u0000\u00c4%\u0001\u0000\u0000\u0000\u00c5\u00c3\u0001\u0000\u0000\u0000"+
		"\u00c6\u00c7\u0003(\u0014\u0000\u00c7\u00c8\u0005\u0002\u0000\u0000\u00c8"+
		"\u00c9\u0003*\u0015\u0000\u00c9\'\u0001\u0000\u0000\u0000\u00ca\u00cf"+
		"\u00034\u001a\u0000\u00cb\u00cc\u0005\u000b\u0000\u0000\u00cc\u00ce\u0003"+
		"4\u001a\u0000\u00cd\u00cb\u0001\u0000\u0000\u0000\u00ce\u00d1\u0001\u0000"+
		"\u0000\u0000\u00cf\u00cd\u0001\u0000\u0000\u0000\u00cf\u00d0\u0001\u0000"+
		"\u0000\u0000\u00d0)\u0001\u0000\u0000\u0000\u00d1\u00cf\u0001\u0000\u0000"+
		"\u0000\u00d2\u00d6\u0003\u0018\f\u0000\u00d3\u00d6\u00036\u001b\u0000"+
		"\u00d4\u00d6\u0003,\u0016\u0000\u00d5\u00d2\u0001\u0000\u0000\u0000\u00d5"+
		"\u00d3\u0001\u0000\u0000\u0000\u00d5\u00d4\u0001\u0000\u0000\u0000\u00d6"+
		"+\u0001\u0000\u0000\u0000\u00d7\u00e0\u0005\t\u0000\u0000\u00d8\u00dd"+
		"\u0003.\u0017\u0000\u00d9\u00da\u0005\u0004\u0000\u0000\u00da\u00dc\u0003"+
		".\u0017\u0000\u00db\u00d9\u0001\u0000\u0000\u0000\u00dc\u00df\u0001\u0000"+
		"\u0000\u0000\u00dd\u00db\u0001\u0000\u0000\u0000\u00dd\u00de\u0001\u0000"+
		"\u0000\u0000\u00de\u00e1\u0001\u0000\u0000\u0000\u00df\u00dd\u0001\u0000"+
		"\u0000\u0000\u00e0\u00d8\u0001\u0000\u0000\u0000\u00e0\u00e1\u0001\u0000"+
		"\u0000\u0000\u00e1\u00e2\u0001\u0000\u0000\u0000\u00e2\u00e3\u0005\n\u0000"+
		"\u0000\u00e3-\u0001\u0000\u0000\u0000\u00e4\u00e8\u00036\u001b\u0000\u00e5"+
		"\u00e8\u00034\u001a\u0000\u00e6\u00e8\u0003\u001a\r\u0000\u00e7\u00e4"+
		"\u0001\u0000\u0000\u0000\u00e7\u00e5\u0001\u0000\u0000\u0000\u00e7\u00e6"+
		"\u0001\u0000\u0000\u0000\u00e8/\u0001\u0000\u0000\u0000\u00e9\u00ee\u0003"+
		"2\u0019\u0000\u00ea\u00eb\u0005\t\u0000\u0000\u00eb\u00ed\u0005\n\u0000"+
		"\u0000\u00ec\u00ea\u0001\u0000\u0000\u0000\u00ed\u00f0\u0001\u0000\u0000"+
		"\u0000\u00ee\u00ec\u0001\u0000\u0000\u0000\u00ee\u00ef\u0001\u0000\u0000"+
		"\u0000\u00ef1\u0001\u0000\u0000\u0000\u00f0\u00ee\u0001\u0000\u0000\u0000"+
		"\u00f1\u00f6\u00034\u001a\u0000\u00f2\u00f3\u0005\u000b\u0000\u0000\u00f3"+
		"\u00f5\u00034\u001a\u0000\u00f4\u00f2\u0001\u0000\u0000\u0000\u00f5\u00f8"+
		"\u0001\u0000\u0000\u0000\u00f6\u00f4\u0001\u0000\u0000\u0000\u00f6\u00f7"+
		"\u0001\u0000\u0000\u0000\u00f73\u0001\u0000\u0000\u0000\u00f8\u00f6\u0001"+
		"\u0000\u0000\u0000\u00f9\u00fa\u0005\u0014\u0000\u0000\u00fa5\u0001\u0000"+
		"\u0000\u0000\u00fb\u00fc\u0007\u0000\u0000\u0000\u00fc7\u0001\u0000\u0000"+
		"\u0000\u0018:<BQX^einx\u0094\u0099\u00a0\u00a7\u00b1\u00b8\u00c3\u00cf"+
		"\u00d5\u00dd\u00e0\u00e7\u00ee\u00f6";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
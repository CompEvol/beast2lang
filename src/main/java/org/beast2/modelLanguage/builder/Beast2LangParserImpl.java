package org.beast2.modelLanguage.builder;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.beast2.modelLanguage.model.*;
import org.beast2.modelLanguage.parser.Beast2ModelLanguageLexer;
import org.beast2.modelLanguage.parser.Beast2ModelLanguageParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Implementation of Beast2LangParser that uses ANTLR to parse Beast2Lang syntax.
 * This implementation has been updated to support @data and @observed annotations.
 */
public class Beast2LangParserImpl implements Beast2LangParser {

    private static final Logger logger = Logger.getLogger(Beast2LangParserImpl.class.getName());

    @Override
    public Beast2Model parseFromStream(InputStream inputStream) throws IOException {
        // Read the entire content into a buffer so we can debug it
        byte[] data = inputStream.readAllBytes();
        String fileContent = new String(data, StandardCharsets.UTF_8);

        logger.info("======= FILE CONTENT =======");
        logger.info(fileContent);
        logger.info("============================");

        // Create a CharStream from the file content
        CharStream input = CharStreams.fromString(fileContent);

        // Create lexer with explicit error reporting
        Beast2ModelLanguageLexer lexer = new Beast2ModelLanguageLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                    int charPositionInLine, String msg, RecognitionException e) {
                logger.severe("Lexer error at line " + line + ":" + charPositionInLine + " - " + msg);
                if (e != null) {
                    logger.severe("Exception type: " + e.getClass().getName());
                }
                if (offendingSymbol != null) {
                    logger.severe("Offending symbol: " + offendingSymbol);
                }
                throw new RuntimeException("Lexer error at line " + line + ":" + charPositionInLine + " - " + msg);
            }
        });

        // Create token stream and ensure all tokens are buffered immediately
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        // Create parser with explicit error reporting
        Beast2ModelLanguageParser parser = new Beast2ModelLanguageParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new ParserErrorListener());

        // Set a higher error recovery limit to help with debugging
        parser.setErrorHandler(new DefaultErrorStrategy() {
            @Override
            public void reportError(Parser recognizer, RecognitionException e) {
                // Get input near error
                TokenStream input = recognizer.getInputStream();
                String context = "";
                if (input instanceof CommonTokenStream) {
                    int errorIndex = e.getOffendingToken().getTokenIndex();
                    int startIndex = Math.max(0, errorIndex - 5);
                    int endIndex = Math.min(input.size() - 1, errorIndex + 5);

                    StringBuilder sb = new StringBuilder();
                    sb.append("Context: ");
                    for (int i = startIndex; i <= endIndex; i++) {
                        Token token = ((CommonTokenStream) input).get(i);
                        if (i == errorIndex) {
                            sb.append(" >>> ");
                            sb.append(token.getText());
                            sb.append(" <<< ");
                        } else {
                            sb.append(token.getText());
                            sb.append(" ");
                        }
                    }
                    context = sb.toString();
                }

                logger.severe("Parser error: " + e.getMessage());
                logger.severe(context);
                super.reportError(recognizer, e);
            }
        });

        // Parse the program
        Beast2ModelLanguageParser.ProgramContext programContext = parser.program();

        // Create a model builder listener
        ModelBuilderListener listener = new ModelBuilderListener();

        // Walk the parse tree with our listener
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, programContext);

        // Get the constructed model
        Beast2Model model = listener.getModel();

        return model;
    }

    @Override
    public Beast2Model parseFromString(String input) {
        try {
            // Create a CharStream from the string
            CharStream charStream = CharStreams.fromString(input);

            // Create lexer with explicit error reporting
            Beast2ModelLanguageLexer lexer = new Beast2ModelLanguageLexer(charStream);
            lexer.removeErrorListeners();
            lexer.addErrorListener(new ParserErrorListener());

            // Create token stream
            CommonTokenStream tokens = new CommonTokenStream(lexer);

            // Create parser with explicit error reporting
            Beast2ModelLanguageParser parser = new Beast2ModelLanguageParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(new ParserErrorListener());

            // Parse the input
            Beast2ModelLanguageParser.ProgramContext programContext = parser.program();

            // Create a model builder listener
            ModelBuilderListener listener = new ModelBuilderListener();

            // Walk the parse tree with our listener
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(listener, programContext);

            // Get the constructed model
            Beast2Model model = listener.getModel();

            return model;
        } catch (Exception e) {
            throw new RuntimeException("Error parsing Beast2Lang string: " + e.getMessage(), e);
        }
    }

    /**
     * Error listener for ANTLR parsing
     */
    public static class ParserErrorListener extends BaseErrorListener {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                int charPositionInLine, String msg, RecognitionException e) {
            Logger logger = Logger.getLogger(ParserErrorListener.class.getName());

            // Get more detailed information about the error
            String detailedMessage = msg;

            // If we have an offending token, add its info
            if (offendingSymbol instanceof Token) {
                Token token = (Token) offendingSymbol;
                detailedMessage += " (token type: " + token.getType() +
                        ", text: '" + token.getText() + "')";
            }

            logger.severe("Parser error at line " + line + ":" + charPositionInLine + " - " + detailedMessage);

            if (e != null) {
                logger.severe("Exception type: " + e.getClass().getName());
                if (e.getMessage() != null) {
                    logger.severe("Exception message: " + e.getMessage());
                }
            }

            throw new RuntimeException("Syntax error at line " + line + ":" + charPositionInLine + " - " + msg);
        }
    }
}
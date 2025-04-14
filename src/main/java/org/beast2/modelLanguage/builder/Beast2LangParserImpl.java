package org.beast2.modelLanguage.builder;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.beast2.modelLanguage.model.*;
import org.beast2.modelLanguage.parser.Beast2ModelLanguageLexer;
import org.beast2.modelLanguage.parser.Beast2ModelLanguageParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Implementation of Beast2LangParser that uses ANTLR to parse Beast2Lang syntax.
 */
public class Beast2LangParserImpl implements Beast2LangParser {

    @Override
    public Beast2Model parseFromStream(InputStream inputStream) throws IOException {
        // Read the entire content into a buffer so we can debug it
        byte[] data = inputStream.readAllBytes();
        String fileContent = new String(data, StandardCharsets.UTF_8);

        System.out.println("======= FILE CONTENT =======");
        System.out.println(fileContent);
        System.out.println("============================");

        // Create lexer and parser from the buffered content
        Beast2ModelLanguageLexer lexer = new Beast2ModelLanguageLexer(
                CharStreams.fromString(fileContent));
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        // Print tokens for debugging
        tokens.fill(); // Make sure all tokens are buffered
        System.out.println("========= TOKENS ===========");
        for (int i = 0; i < tokens.size(); i++) {
            System.out.println(tokens.get(i));
        }
        System.out.println("===========================");

        // Reset token stream position
        tokens.seek(0);

        // Parse the input
        Beast2ModelLanguageParser parser = new Beast2ModelLanguageParser(tokens);
        Beast2ModelLanguageParser.ProgramContext programContext = parser.program();

        // Create a model builder listener
        ModelBuilderListener listener = new ModelBuilderListener();

        // Walk the parse tree with our listener
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, programContext);

        // Return the constructed model
        return listener.getModel();
    }

    @Override
    public Beast2Model parseFromString(String input) {
        // Create lexer and parser
        Beast2ModelLanguageLexer lexer = new Beast2ModelLanguageLexer(CharStreams.fromString(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        Beast2ModelLanguageParser parser = new Beast2ModelLanguageParser(tokens);
        
        // Parse the input
        Beast2ModelLanguageParser.ProgramContext programContext = parser.program();
        
        // Create a model builder listener
        ModelBuilderListener listener = new ModelBuilderListener();
        
        // Walk the parse tree with our listener
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, programContext);
        
        // Return the constructed model
        return listener.getModel();
    }
}
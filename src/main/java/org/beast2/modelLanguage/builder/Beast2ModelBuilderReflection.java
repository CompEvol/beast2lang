package org.beast2.modelLanguage.builder;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.beast2.modelLanguage.model.*;
import org.beast2.modelLanguage.parser.Beast2ModelLanguageBaseListener;
import org.beast2.modelLanguage.parser.Beast2ModelLanguageLexer;
import org.beast2.modelLanguage.parser.Beast2ModelLanguageParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Main entry point for Beast2Lang model building operations.
 * This class orchestrates the process of parsing Beast2Lang files and
 * building BEAST2 objects, but delegates the actual implementation to specialized classes.
 */
public class Beast2ModelBuilderReflection {
    
    private final Beast2LangParser parser;
    private final Beast2ObjectFactory objectFactory;
    
    /**
     * Constructor that initializes the parser and object factory.
     */
    public Beast2ModelBuilderReflection() {
        this.parser = new Beast2LangParserImpl();
        this.objectFactory = new ReflectionBeast2ObjectFactory();
    }
    
    /**
     * Parse an input stream and build a Beast2Model
     * 
     * @param inputStream the input stream to parse
     * @return the constructed Beast2Model
     * @throws IOException if an I/O error occurs
     */
    public Beast2Model buildFromStream(InputStream inputStream) throws IOException {
        return parser.parseFromStream(inputStream);
    }
    
    /**
     * Parse a string and build a Beast2Model
     * 
     * @param input the input string to parse
     * @return the constructed Beast2Model
     */
    public Beast2Model buildFromString(String input) {
        return parser.parseFromString(input);
    }
    
    /**
     * Build BEAST2 objects from the Beast2Model using reflection
     * 
     * @param model the Beast2Model to convert to BEAST2 objects
     * @return the root BEAST2 object (typically the MCMC object)
     * @throws Exception if construction fails
     */
    public Object buildBeast2Objects(Beast2Model model) throws Exception {
        return objectFactory.buildFromModel(model);
    }
    
    /**
     * Get all created BEAST2 objects
     */
    public Map<String, Object> getBeastObjects() {
        return objectFactory.getAllObjects();
    }
    
    /**
     * Get a specific BEAST2 object by name
     */
    public Object getBeastObject(String name) {
        return objectFactory.getObject(name);
    }
}
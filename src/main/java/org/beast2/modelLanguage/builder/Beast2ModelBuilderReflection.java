package org.beast2.modelLanguage.builder;

import beast.base.inference.Distribution;
import beast.base.inference.StateNode;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.beast2.modelLanguage.model.*;
import org.beast2.modelLanguage.parser.Beast2ModelLanguageBaseListener;
import org.beast2.modelLanguage.parser.Beast2ModelLanguageLexer;
import org.beast2.modelLanguage.parser.Beast2ModelLanguageParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Main entry point for Beast2Lang model building operations.
 * This class orchestrates the process of parsing Beast2Lang files and
 * building BEAST2 objects, but delegates the actual implementation to specialized classes.
 */
public class Beast2ModelBuilderReflection {
    
    private static final Logger logger = Logger.getLogger(Beast2ModelBuilderReflection.class.getName());
    
    private final Beast2LangParser parser;
    private final ReflectionBeast2ObjectFactory objectFactory;
    
    /**
     * Constructor that initializes the parser and object factory.
     */
    public Beast2ModelBuilderReflection() {
        this.parser = new Beast2LangParserImpl();
        this.objectFactory = new ReflectionBeast2ObjectFactory();
    }

    /**
     * After calling buildBeast2Objects(model), this returns
     * all of the BEAST2 objects that implement StateNode
     * and are random variables (i.e., have distributions),
     * excluding those that are observed.
     * These are the things that should be in the MCMC state.
     */
    public List<StateNode> getCreatedStateNodes() {
        // Get only the state nodes that are random variables and not observed
        List<String> randomVars = objectFactory.getRandomVariables();
        List<String> observedVars = objectFactory.getObservedVariables();
        
        return randomVars.stream()
            .filter(varName -> !observedVars.contains(varName))
            .map(varName -> objectFactory.getObject(varName))
            .filter(obj -> obj instanceof StateNode)
            .map(obj -> (StateNode) obj)
            .collect(Collectors.toList());
    }

    /**
     * After calling buildBeast2Objects(model), this returns
     * all of the BEAST2 objects that implement Distribution,
     * i.e. the priors, tree‐likelihood, etc.
     */
    public List<Distribution> getCreatedDistributions() {
        return objectFactory.getAllObjects().values().stream()
                .filter(o -> o instanceof Distribution)
                .map(o -> (Distribution)o)
                .collect(Collectors.toList());
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
    public Map<String, Object> getAllObjects() {
        return objectFactory.getAllObjects();
    }
    
    /**
     * Get a specific BEAST2 object by name
     */
    public Object getObject(String name) {
        return objectFactory.getObject(name);
    }
    
    /**
     * Get all random variables (variables with distributions)
     */
    public List<String> getRandomVariables() {
        return objectFactory.getRandomVariables();
    }
    
    /**
     * Get all observed variables
     */
    public List<String> getObservedVariables() {
        return objectFactory.getObservedVariables();
    }
    
    /**
     * Check if a variable is observed
     */
    public boolean isObserved(String variableName) {
        return objectFactory.getObservedVariables().contains(variableName);
    }
}
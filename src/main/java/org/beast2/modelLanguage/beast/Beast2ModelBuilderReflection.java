package org.beast2.modelLanguage.beast;

import beast.base.inference.Distribution;
import beast.base.inference.StateNode;
import org.beast2.modelLanguage.builder.Beast2LangParser;
import org.beast2.modelLanguage.builder.Beast2LangParserImpl;
import org.beast2.modelLanguage.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.HashMap;

/**
 * Main entry point for Beast2Lang model building operations.
 * This class orchestrates the process of parsing Beast2Lang files and
 * building BEAST2 objects, but delegates the actual implementation to specialized classes.
 * Updated to support @data and @observed annotations.
 */
public class Beast2ModelBuilderReflection {

    private static final Logger logger = Logger.getLogger(Beast2ModelBuilderReflection.class.getName());

    private final Beast2LangParser parser;
    private final ReflectionBeast2ObjectFactory objectFactory;
    private final Map<String, String> observedDataRefs;

    /**
     * Constructor that initializes the parser and object factory.
     */
    public Beast2ModelBuilderReflection() {
        this.parser = new Beast2LangParserImpl();
        this.objectFactory = new ReflectionBeast2ObjectFactory();
        this.observedDataRefs = new HashMap<>();
    }

    /**
     * After calling buildModel(model), this returns
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
     * After calling buildModel(model), this returns
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
    public Object buildModel(Beast2Model model) throws Exception {
        logger.info("Building Beast2 model with annotation support...");

        // First pass: process all statements to collect data and observed annotations
        processAnnotations(model);

        // Second pass: build the actual Beast2 objects
        Object rootObject = objectFactory.buildFromModel(model);

        return rootObject;
    }

    /**
     * Build BEAST2 objects and return them as a Beast2Analysis
     *
     * @param model the Beast2Model to convert to a Beast2Analysis
     * @return a Beast2Analysis containing the constructed objects
     * @throws Exception if construction fails
     */
    public Beast2Analysis buildModel(Beast2Model model, Map<String, Object> analysisParams) throws Exception {
        // Build the model
        buildModel(model);

        // Create a Beast2Analysis with the built objects
        Beast2Analysis analysis = new Beast2Analysis(model);

        // Apply any analysis parameters
        if (analysisParams != null) {
            if (analysisParams.containsKey("chainLength")) {
                analysis.setChainLength((Integer) analysisParams.get("chainLength"));
            }
            if (analysisParams.containsKey("logEvery")) {
                analysis.setLogEvery((Integer) analysisParams.get("logEvery"));
            }
            if (analysisParams.containsKey("traceFileName")) {
                analysis.setTraceFileName((String) analysisParams.get("traceFileName"));
            }
        }

        return analysis;
    }

    /**
     * Process annotations in the model to collect data references
     */
    private void processAnnotations(Beast2Model model) {
        // Map to store variables with @data annotations
        Map<String, Boolean> dataAnnotatedVars = new HashMap<>();

        // First pass: find all variables with @data annotations
        for (Statement stmt : model.getStatements()) {
            if (stmt instanceof AnnotatedStatement) {
                AnnotatedStatement annotatedStmt = (AnnotatedStatement) stmt;
                Annotation annotation = annotatedStmt.getAnnotation();
                Statement innerStmt = annotatedStmt.getStatement();

                if ("data".equals(annotation.getName())) {
                    if (innerStmt instanceof VariableDeclaration) {
                        String varName = ((VariableDeclaration) innerStmt).getVariableName();
                        dataAnnotatedVars.put(varName, Boolean.TRUE);
                        logger.info("Found @data annotation for variable: " + varName);
                    } else {
                        logger.warning("@data annotation can only be applied to variable declarations");
                    }
                }
            }
        }

        // Second pass: process @observed annotations and validate data references
        for (Statement stmt : model.getStatements()) {
            if (stmt instanceof AnnotatedStatement) {
                AnnotatedStatement annotatedStmt = (AnnotatedStatement) stmt;
                Annotation annotation = annotatedStmt.getAnnotation();
                Statement innerStmt = annotatedStmt.getStatement();

                if ("observed".equals(annotation.getName())) {
                    // Validate that it's applied to a distribution assignment
                    if (!(innerStmt instanceof DistributionAssignment)) {
                        logger.warning("@observed annotation can only be applied to distribution assignments");
                        continue;
                    }

                    // Get the variable name
                    String varName = ((DistributionAssignment) innerStmt).getVariableName();

                    // Validate that it has a data parameter
                    if (!annotation.hasParameter("data")) {
                        logger.warning("@observed annotation requires a 'data' parameter");
                        continue;
                    }

                    // Get the data reference
                    String dataRef = annotation.getParameterAsString("data");

                    // Validate that the data reference points to a variable with @data annotation
                    if (!dataAnnotatedVars.containsKey(dataRef)) {
                        throw new IllegalArgumentException(
                                "data parameter '" + dataRef + "' in @observed annotation must reference " +
                                        "a variable previously annotated with @data"
                        );
                    }

                    // Store the reference for later use
                    observedDataRefs.put(varName, dataRef);
                    logger.info("Found @observed annotation for variable: " + varName +
                            " with data reference: " + dataRef);
                }
            }
        }
    }

    /**
     * Add a BEAST2 object to the model with the specified ID
     *
     * @param id the ID to use for the object
     * @param object the BEAST2 object to add
     */
    public void addObjectToModel(String id, Object object) {
        objectFactory.addObjectToModel(id, object);
        logger.info("Added object to model via model builder: " + id);
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
     * Get all data-annotated variables
     */
    public List<String> getDataAnnotatedVariables() {
        return objectFactory.getDataAnnotatedVariables();
    }

    /**
     * Check if a variable is observed
     */
    public boolean isObserved(String variableName) {
        return objectFactory.isObserved(variableName);
    }

    /**
     * Check if a variable has a data annotation
     */
    public boolean isDataAnnotated(String variableName) {
        return objectFactory.isDataAnnotated(variableName);
    }

    /**
     * Get the data reference for an observed variable
     */
    public String getDataReference(String variableName) {
        return observedDataRefs.get(variableName);
    }
}
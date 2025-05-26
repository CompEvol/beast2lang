package org.beast2.modelLanguage.beast;

import beast.base.inference.Distribution;
import beast.base.inference.StateNode;
import org.beast2.modelLanguage.builder.Beast2LangParser;
import org.beast2.modelLanguage.builder.Beast2LangParserImpl;
import org.beast2.modelLanguage.builder.ModelStatementProcessor;
import org.beast2.modelLanguage.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Main entry point for Beast2Lang model building operations.
 * Refactored to use BeastObjectRegistry to eliminate circular dependencies.
 */
public class Beast2ModelBuilder {

    private static final Logger logger = Logger.getLogger(Beast2ModelBuilder.class.getName());

    private final Beast2LangParser parser;
    private final BeastObjectRegistry registry;
    private final ModelStatementProcessor objectFactory;

    /**
     * Constructor that initializes the parser, registry, and object factory.
     */
    public Beast2ModelBuilder() {
        this.parser = new Beast2LangParserImpl();
        this.registry = new BeastObjectRegistry();
        this.objectFactory = new ModelStatementProcessor(registry);
    }

    /**
     * Get the shared registry (useful for testing and debugging)
     */
    public BeastObjectRegistry getRegistry() {
        return registry;
    }

    /**
     * After calling buildModel(model), this returns
     * all of the BEAST2 objects that implement StateNode
     * and are random variables (i.e., have distributions),
     * excluding those that are observed.
     * These are the things that should be in the MCMC state.
     */
    public List<StateNode> getCreatedStateNodes() {
        List<StateNode> stateNodes = registry.getRandomStateNodes();
        // sort by alphabetic
        stateNodes.sort(Comparator.comparing(StateNode::getID));
        return stateNodes;
    }

    /**
     * After calling buildModel(model), this returns
     * all of the BEAST2 objects that implement Distribution,
     * i.e. the priors, tree‐likelihood, etc.
     */
    public List<Distribution> getCreatedDistributions() {
        return registry.getDistributions();
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
    public void buildModel(Beast2Model model) throws Exception {
        logger.info("Building Beast2 model with annotation support...");

        // Clear registry for a fresh start
        registry.clear();

        // First pass: process all statements to collect data and observed annotations
        processAnnotations(model);

        // Second pass: build the actual Beast2 objects
        objectFactory.buildFromModel(model);

        // Log registry statistics
        logger.info(registry.getStatistics());
    }

    /**
     * Build BEAST2 objects and return them as a Beast2Analysis
     *
     * @param model the Beast2Model to convert to a Beast2Analysis
     * @param analysisParams Analysis parameters to apply
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
                analysis.setChainLength(((Number) analysisParams.get("chainLength")).longValue());
            }
            if (analysisParams.containsKey("logEvery")) {
                analysis.setLogEvery(((Number) analysisParams.get("logEvery")).intValue());
            }
            if (analysisParams.containsKey("traceFileName")) {
                analysis.setTraceFileName((String) analysisParams.get("traceFileName"));
            }
        }

        return analysis;
    }

    /**
     * Process annotations in the model to collect metadata
     */
    private void processAnnotations(Beast2Model model) {
        // First pass: find all variables with @data annotations
        for (Statement stmt : model.getStatements()) {
            if (stmt instanceof AnnotatedStatement) {
                AnnotatedStatement annotatedStmt = (AnnotatedStatement) stmt;
                Annotation annotation = annotatedStmt.getAnnotation();
                Statement innerStmt = annotatedStmt.getStatement();

                if ("data".equals(annotation.getName())) {
                    if (innerStmt instanceof VariableDeclaration) {
                        String varName = ((VariableDeclaration) innerStmt).getVariableName();
                        registry.markAsDataAnnotated(varName);
                        logger.info("Found @data annotation for variable: " + varName);
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
                    if (!registry.isDataAnnotated(dataRef)) {
                        throw new IllegalArgumentException(
                                "data parameter '" + dataRef + "' in @observed annotation must reference " +
                                        "a variable previously annotated with @data"
                        );
                    }

                    // Store the reference in the registry
                    registry.markAsObservedVariable(varName, dataRef);
                    logger.info("Found @observed annotation for variable: " + varName +
                            " with data reference: " + dataRef);
                }
            }
        }
    }

    /**
     * Add a BEAST2 object to the model with the specified ID
     * This method delegates to the registry
     *
     * @param id the ID to use for the object
     * @param object the BEAST2 object to add
     */
    public void addObjectToModel(String id, Object object) {
        registry.register(id, object);
    }

    /**
     * Get all created BEAST2 objects
     */
    public Map<String, Object> getAllObjects() {
        return registry.getAllObjects();
    }

    /**
     * Get a specific BEAST2 object by name
     */
    public Object getObject(String name) {
        return registry.get(name);
    }

    /**
     * Get all random variables (variables with distributions)
     */
    public List<String> getRandomVariables() {
        return registry.getRandomVariables();
    }

    /**
     * Get all observed variables
     */
    public List<String> getObservedVariables() {
        return registry.getObservedVariables();
    }

    /**
     * Get all data-annotated variables
     */
    public List<String> getDataAnnotatedVariables() {
        return registry.getDataAnnotatedVariables();
    }

    /**
     * Check if a variable is observed
     */
    public boolean isObserved(String variableName) {
        return registry.isObservedVariable(variableName);
    }

    /**
     * Check if a variable has a data annotation
     */
    public boolean isDataAnnotated(String variableName) {
        return registry.isDataAnnotated(variableName);
    }

    /**
     * Get the data reference for an observed variable
     */
    public String getDataReference(String variableName) {
        return registry.getDataReference(variableName);
    }
}
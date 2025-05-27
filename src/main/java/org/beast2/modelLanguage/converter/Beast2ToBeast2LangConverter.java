package org.beast2.modelLanguage.converter;

import beast.base.core.BEASTInterface;
import beast.base.core.BEASTObject;
import beast.base.core.Input;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Sequence;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.likelihood.GenericTreeLikelihood;
import beast.base.evolution.likelihood.TreeLikelihood;
import beast.base.evolution.tree.TreeDistribution;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.inference.distribution.Dirichlet;
import beast.base.inference.distribution.LogNormalDistributionModel;
import beast.base.inference.parameter.Parameter;
import beast.base.inference.distribution.Prior;
import beast.base.inference.parameter.RealParameter;
import org.beast2.modelLanguage.beast.BeastObjectFactory;
import org.beast2.modelLanguage.builder.ModelObjectFactory;
import org.beast2.modelLanguage.model.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Converts a BEAST2 object model to a Beast2Lang model.
 *
 * This converter properly handles the distinction between primary inputs (which appear
 * on the left side of ~ statements) and regular inputs (which appear in function calls).
 */
public class Beast2ToBeast2LangConverter {

    private static final Logger logger = Logger.getLogger(Beast2ToBeast2LangConverter.class.getName());

    // Maps BEAST2 objects to their corresponding identifiers in the Beast2Lang model
    private final Map<BEASTInterface, String> objectToIdMap = new HashMap<>();

    // Maps BEAST2 objects to their corresponding statements in the Beast2Lang model
    private final Map<BEASTInterface, Statement> objectToStatementMap = new HashMap<>();

    // Keeps track of already processed objects
    private final Set<BEASTInterface> processedObjects = new HashSet<>();

    // Stores import statements
    private final Set<ImportStatement> imports = new HashSet<>();

    // Counter for generating unique variable names
    private State state = null;

    // The object factory for BEAST-specific operations
    private final ModelObjectFactory objectFactory;

    // Track distributions used in ~ statements
    private final Set<BEASTInterface> usedDistributions = new HashSet<>();

    // Track distributions that are inlined (unwrapped from Prior)
    private final Set<BEASTInterface> inlinedDistributions = new HashSet<>();

    public Beast2ToBeast2LangConverter() {
        this.objectFactory = new BeastObjectFactory();
    }

    /**
     * Convert a BEAST2 analysis to a Beast2Lang model.
     *
     * @param posterior The posterior distribution (usually CompoundDistribution)
     * @param state The MCMC state containing all state nodes
     * @return A Beast2Model representing the analysis
     */
    public Beast2Model convertToBeast2Model(Distribution posterior, State state) {
        Beast2Model model = new Beast2Model();
        this.state = state;

        // Add common imports
        addCommonImports(model);

        // First pass: identify all objects and generate identifiers
        identifyObjects(posterior, state);

        // Pre-pass: identify all distributions used in ~ statements
        identifyUsedDistributions();

        // Pre-pass: identify all inlined distributions (from Prior unwrapping)
        identifyInlinedDistributions();

        // Second pass: create statements for all objects
        Set<BEASTInterface> statementProcessed = new HashSet<>();
        for (BEASTInterface object : objectToIdMap.keySet()) {
            boolean shouldCreate = shouldCreateStatement(object);
            if (object instanceof Sequence) {
                logger.info("Sequence " + object.getID() + " shouldCreate: " + shouldCreate);
            }
            if (shouldCreate) {
                processObject(object, model, statementProcessed);
            }
        }

        // Sort statements based on dependencies
        sortStatements(model);

        // Handle observed alignments
        for (BEASTInterface obj : objectToIdMap.keySet()) {
            if (obj instanceof TreeLikelihood) {
                TreeLikelihood likelihood = (TreeLikelihood) obj;
                Alignment data = likelihood.dataInput.get();
                if (data != null) {
                    // Create an observed alignment statement
                    String alignmentId = "alignment";

                    // Create the TreeLikelihood expression inline
                    Expression likelihoodExpr = createExpressionForObject(likelihood);

                    DistributionAssignment obsAlignment = new DistributionAssignment(
                            "Alignment", alignmentId, likelihoodExpr
                    );

                    Map<String, Object> params = new HashMap<>();
                    params.put("data", objectToIdMap.get(data));
                    Annotation annotation = new Annotation("observed", params);

                    model.addStatement(new AnnotatedStatement(annotation, obsAlignment));
                }
            }
        }

        return model;
    }

    /**
     * Identify all distributions that will be inlined (unwrapped from Prior)
     */
    private void identifyInlinedDistributions() {
        // Clear the set first
        inlinedDistributions.clear();

        // Check all Prior objects to see which distributions will be inlined
        for (BEASTInterface obj : objectToIdMap.keySet()) {
            if (obj instanceof Prior) {
                Prior prior = (Prior) obj;
                BEASTInterface innerDist = prior.distInput.get();
                if (innerDist != null && objectFactory.isParametricDistribution(innerDist)) {
                    inlinedDistributions.add(innerDist);
                    logger.info("Found inlined distribution: " + innerDist.getID() +
                            " (from Prior " + prior.getID() + ")");
                }
            }
        }
    }

    private void identifyUsedDistributions() {
        // Clear the set first
        usedDistributions.clear();

        // Check all state nodes to find their distributions
        for (StateNode node : state.stateNodeInput.get()) {
            // Find all distributions that reference this state node
            for (BEASTInterface obj : objectToIdMap.keySet()) {
                if (objectFactory.isDistribution(obj)) {
                    Distribution dist = (Distribution) obj;

                    // Use the object factory to get the primary input name
                    String primaryInputName = objectFactory.getPrimaryInputName(dist);

                    if (primaryInputName != null) {
                        // Check if this distribution's primary input matches our state node
                        try {
                            Object primaryInputValue = objectFactory.getInputValue(dist, primaryInputName);
                            if (primaryInputValue == node) {
                                usedDistributions.add(dist);
                                logger.info("Found used distribution: " + dist.getID() +
                                        " for state node " + node.getID() +
                                        " via input '" + primaryInputName + "'");
                            }
                        } catch (Exception e) {
                            logger.warning("Failed to get input value for " + primaryInputName +
                                    " from " + dist.getClass().getSimpleName() + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private boolean isDefaultValue(BEASTInterface obj, Input<?> input) {
        Object value = input.get();
        Object defaultValue = input.defaultValue;

        // If no default value is set, we can't determine if this is default
        if (defaultValue == null) {
            return false;
        }

        // For simple types, direct comparison
        if (defaultValue.equals(value)) {
            return true;
        }

        // For arrays, need special handling
        if (defaultValue.getClass().isArray() && value.getClass().isArray()) {
            return Arrays.equals((Object[]) defaultValue, (Object[]) value);
        }

        // For primitive arrays
        if (defaultValue instanceof double[] && value instanceof double[]) {
            return Arrays.equals((double[]) defaultValue, (double[]) value);
        }
        if (defaultValue instanceof int[] && value instanceof int[]) {
            return Arrays.equals((int[]) defaultValue, (int[]) value);
        }

        return false;
    }

    private boolean shouldSkipInput(BEASTInterface obj, Input<?> input) {
        // Skip if it's at default value
        if (isDefaultValue(obj, input)) {
            return true;
        }

        // Skip if it's optional and empty
        if (input.getRule() == Input.Validate.OPTIONAL &&
                (input.get() == null ||
                        (input.get() instanceof List && ((List<?>) input.get()).isEmpty()))) {
            return true;
        }

        // Still skip certain verbose inputs even if they're not at default
        Set<String> alwaysSkip = new HashSet<>(Arrays.asList(
                "sequence", // Individual sequences in alignment
                "*"         // Wildcard inputs
        ));

        if (obj instanceof TreeLikelihood && input.getName().equals("data")) {
            return true;
        }

        return alwaysSkip.contains(input.getName());
    }

    /**
     * Find the distribution that samples a given state node.
     * Uses the object factory to determine the primary input name.
     */
    private BEASTInterface findDistributionFor(StateNode node) {
        for (BEASTInterface obj : objectToIdMap.keySet()) {
            if (objectFactory.isDistribution(obj)) {
                Distribution dist = (Distribution) obj;

                // Get the primary input name for this distribution type
                String primaryInputName = objectFactory.getPrimaryInputName(dist);

                if (primaryInputName != null) {
                    try {
                        Object primaryInputValue = objectFactory.getInputValue(dist, primaryInputName);
                        if (primaryInputValue == node) {
                            usedDistributions.add(dist);
                            return dist;
                        }
                    } catch (Exception e) {
                        // Log but continue searching
                        logger.fine("Failed to check primary input for " +
                                dist.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                }
            }
        }
        return null;
    }

    private boolean shouldCreateStatement(BEASTInterface obj) {
        // Skip individual sequences
        if (obj instanceof Sequence) return false;

        // Skip Prior objects (we use them indirectly)
        if (obj instanceof Prior) return false;

        // Skip fixed parameters (non-state-node RealParameters)
        if (isFixedParameter(obj)) return false;

        // Skip empty RealParameter with no meaningful ID
        if (obj instanceof RealParameter &&
                "RealParameter".equals(obj.getID()) &&
                ((RealParameter)obj).getDimension() == 0) {
            return false;
        }

        // Skip empty TaxonSets with null ID
        if (obj instanceof TaxonSet) {
            TaxonSet ts = (TaxonSet) obj;
            if (ts.getID() == null && ts.getTaxonCount() == 0 && ts.alignmentInput.get() == null) {
                return false;
            }
        }

        // Skip TreeLikelihood - it will be inlined in the observed alignment
        if (obj instanceof TreeLikelihood) {
            return false;
        }

        // Skip distributions that are used in ~ statements
        if (usedDistributions.contains(obj)) {
            return false;
        }

        // Skip distributions that are inlined (unwrapped from Prior)
        if (inlinedDistributions.contains(obj)) {
            return false;
        }

        // Skip CompoundDistribution with generic names
        if (obj instanceof CompoundDistribution) {
            String id = obj.getID();
            if (id != null && (id.equals("prior") || id.equals("likelihood") || id.equals("posterior"))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Add common imports used in most BEAST2 analyses
     */
    private void addCommonImports(Beast2Model model) {
        model.addImport(new ImportStatement("beast.base.inference.parameter", true));
        model.addImport(new ImportStatement("beast.base.inference.distribution", true));
        model.addImport(new ImportStatement("beast.base.evolution.tree", true));
        model.addImport(new ImportStatement("beast.base.evolution.speciation", true));
        model.addImport(new ImportStatement("beast.base.evolution.substitutionmodel", true));
        model.addImport(new ImportStatement("beast.base.evolution.alignment", true));
        model.addImport(new ImportStatement("beast.base.evolution.likelihood", true));
        model.addImport(new ImportStatement("beast.base.evolution.branchratemodel", true));
    }

    /**
     * First pass: identify all objects in the model and generate identifiers
     */
    private void identifyObjects(Distribution posterior, State state) {
        // Process state nodes first
        for (StateNode node : state.stateNodeInput.get()) {
            String id = generateIdentifier(node);
            objectToIdMap.put(node, id);
        }

        // Process posterior and its components
        processObjectGraph(posterior);
    }

    /**
     * Traverse the object graph to identify all objects
     */
    private void processObjectGraph(BEASTInterface obj) {
        if (obj == null || processedObjects.contains(obj)) {
            return;
        }

        processedObjects.add(obj);

        // Add logging for TaxonSet objects
        if (obj instanceof TaxonSet) {
            TaxonSet ts = (TaxonSet) obj;
            logger.info("Found TaxonSet: ID='" + ts.getID() +
                    "', taxonCount=" + ts.getTaxonCount() +
                    ", alignment=" + (ts.alignmentInput.get() != null) +
                    ", hashCode=" + ts.hashCode());
        }

        // Generate an identifier if not already done
        if (!objectToIdMap.containsKey(obj)) {
            String id = generateIdentifier(obj);
            objectToIdMap.put(obj, id);

            // Log what ID was generated
            if (obj instanceof TaxonSet) {
                logger.info("Generated ID '" + id + "' for TaxonSet with original ID '" + obj.getID() + "'");
            }
        }

        // Process inputs
        for (Input<?> input : obj.getInputs().values()) {
            if (input.get() != null) {
                if (input.get() instanceof BEASTInterface) {
                    processObjectGraph((BEASTInterface) input.get());
                } else if (input.get() instanceof List) {
                    for (Object item : (List<?>) input.get()) {
                        if (item instanceof BEASTInterface) {
                            processObjectGraph((BEASTInterface) item);
                        }
                    }
                }
            }
        }
    }

    /**
     * Generate a unique identifier for a BEAST object
     */
    private String generateIdentifier(BEASTInterface obj) {
        // Try to use a sensible name based on the object type
        String className = obj.getClass().getSimpleName();
        String baseName = className.substring(0, 1).toLowerCase() + className.substring(1);

        // If the object has an ID, use that instead
        if (obj.getID() != null && !obj.getID().isEmpty()) {
            baseName = obj.getID();
            // Remove any non-identifier characters
            baseName = baseName.replaceAll("[^a-zA-Z0-9_]", "");
            if (!Character.isJavaIdentifierStart(baseName.charAt(0))) {
                baseName = "_" + baseName;
            }
        }

        // Ensure uniqueness
        String uniqueName = baseName;
        int counter = 1;
        while (objectToIdMap.containsValue(uniqueName)) {
            uniqueName = baseName + counter++;
        }

        return uniqueName;
    }

    /**
     * Process an object to create a corresponding statement in the Beast2Lang model
     */
    private void processObject(BEASTInterface obj, Beast2Model model, Set<BEASTInterface> processed) {
        if (processed.contains(obj)) {
            return; // Already processed
        }
        processed.add(obj);

        // Process dependencies first
        for (Input<?> input : obj.getInputs().values()) {
            if (input.get() != null) {
                if (input.get() instanceof BEASTInterface) {
                    BEASTInterface dependency = (BEASTInterface) input.get();
                    // Only process if it should create a statement
                    if (shouldCreateStatement(dependency) && !objectToStatementMap.containsKey(dependency)) {
                        processObject(dependency, model, processed);
                    }
                } else if (input.get() instanceof List) {
                    for (Object item : (List<?>) input.get()) {
                        if (item instanceof BEASTInterface) {
                            BEASTInterface dependency = (BEASTInterface) item;
                            // Only process if it should create a statement
                            if (shouldCreateStatement(dependency) && !objectToStatementMap.containsKey(dependency)) {
                                processObject(dependency, model, processed);
                            }
                        }
                    }
                }
            }
        }

        // Create statement
        Statement statement = createStatement(obj);
        objectToStatementMap.put(obj, statement);
        model.addStatement(statement);

        // Ensure the class is imported
        addImportForClass(obj.getClass(), model);
    }

    /**
     * Add an import for a class to the model
     */
    private void addImportForClass(Class<?> clazz, Beast2Model model) {
        String packageName = clazz.getPackage().getName();

        // Check if we already have a wildcard import for this package
        for (ImportStatement existing : model.getImports()) {
            if (existing.getPackageName().equals(packageName) && existing.isWildcard()) {
                return; // Already covered by wildcard import
            }
        }

        // For consistency, use wildcard imports
        ImportStatement importStmt = new ImportStatement(packageName, true);
        model.addImport(importStmt);
    }

    /**
     * Create a Beast2Lang statement for a BEAST object
     */
    private Statement createStatement(BEASTInterface obj) {
        String id = objectToIdMap.get(obj);

        // Handle state nodes with distributions first
        // BUT skip if this is a data source (like AlignmentFromNexus)
        if (obj instanceof StateNode && !isDataSource(obj)) {
            BEASTInterface dist = findDistributionFor((StateNode) obj);
            if (dist != null) {
                return createDistributionAssignment(obj, dist);
            }
        }

        // Then handle other objects with variable declarations
        Statement decl = createVariableDeclaration(obj);

        // Add annotations if needed
        if (obj instanceof Alignment && !(obj instanceof StateNode)) {
            if (isDataSource(obj)) {
                Map<String, Object> params = new HashMap<>();
                Annotation annotation = new Annotation("data", params);
                return new AnnotatedStatement(annotation, decl);
            }
        }

        return decl;
    }

    /**
     * Check if a distribution is sampling a particular object
     */
    private boolean isDistributionSampling(Distribution dist, BEASTInterface obj) {
        // Use the object factory to get the primary input name
        String primaryInputName = objectFactory.getPrimaryInputName(dist);

        if (primaryInputName != null) {
            try {
                Object primaryInputValue = objectFactory.getInputValue(dist, primaryInputName);
                return primaryInputValue == obj;
            } catch (Exception e) {
                logger.warning("Failed to check if distribution is sampling object: " + e.getMessage());
            }
        }

        return false;
    }

    /**
     * Create a distribution assignment statement (using ~)
     */
    private DistributionAssignment createDistributionAssignment(BEASTInterface param, BEASTInterface distribution) {
        String id = objectToIdMap.get(param);
        String className = param.getClass().getSimpleName();

        // Special handling for Prior - unwrap the inner distribution
        BEASTInterface actualDistribution = distribution;
        if (distribution instanceof Prior) {
            Prior prior = (Prior) distribution;
            BEASTInterface innerDist = prior.distInput.get();
            if (innerDist != null && objectFactory.isParametricDistribution(innerDist)) {
                actualDistribution = innerDist;
                // No need to mark as inlined here - we do it in identifyInlinedDistributions()
            }
        }

        // Create the expression for the distribution
        Expression expr;

        // Check if we need to add secondary inputs
        List<Argument> additionalArgs = new ArrayList<>();
        if (param instanceof StateNode) {
            logger.info("Checking secondary inputs for StateNode: " + param.getID());

            // Collect any inputs set on the state node that should be secondary inputs
            for (Input<?> input : param.getInputs().values()) {
                logger.info("  Input '" + input.getName() + "' has value: " + input.get());

                if (input.get() != null && !shouldSkipSecondaryInput(param, input)) {
                    String inputName = input.getName();
                    Expression inputValue = createExpressionForInput(input);
                    if (inputValue != null) {
                        logger.info("  Adding secondary input: " + inputName);
                        additionalArgs.add(new Argument(inputName, inputValue));
                    }
                }
            }
        }

        // If we have additional args, we need to create a modified function call
        if (!additionalArgs.isEmpty()) {
            // Get the regular arguments for the distribution
            List<Argument> distArgs = createArgumentsForObject(actualDistribution);

            // Combine them with the secondary inputs
            List<Argument> allArgs = new ArrayList<>(distArgs);
            allArgs.addAll(additionalArgs);

            // Create a new function call with all arguments
            expr = new FunctionCall(actualDistribution.getClass().getSimpleName(), allArgs);
        } else {
            // No secondary inputs, use the regular expression
            expr = createExpressionForObject(actualDistribution);
        }

        return new DistributionAssignment(className, id, expr);
    }

    /**
     * Check if an input should be skipped as a secondary input
     */
    private boolean shouldSkipSecondaryInput(BEASTInterface stateNode, Input<?> input) {
        String inputName = input.getName();

        // Skip calculated inputs
        if (isCalculatedInput(stateNode, input)) {
            logger.info("    Skipping '" + inputName + "' - calculated input");
            return true;
        }

        // Skip empty inputs
        if (input.get() == null ||
                (input.get() instanceof List && ((List<?>) input.get()).isEmpty())) {
            logger.info("    Skipping '" + inputName + "' - empty");
            return true;
        }

        // Skip if it's at default value
        if (isDefaultValue(stateNode, input)) {
            logger.info("    Skipping '" + inputName + "' - at default value");
            return true;
        }

        // Skip certain standard StateNode inputs that aren't meant to be secondary
        Set<String> skipInputs = new HashSet<>(Arrays.asList(
                "id", "spec", "name", "estimate"
        ));

        if (skipInputs.contains(inputName)) {
            logger.info("    Skipping '" + inputName + "' - standard StateNode input");
            return true;
        }

        logger.info("    Including '" + inputName + "' as secondary input");
        return false;
    }

    /**
     * Create an expression for an input value
     */
    private Expression createExpressionForInput(Input<?> input) {
        Object value = input.get();

        if (value instanceof BEASTInterface) {
            BEASTInterface obj = (BEASTInterface) value;
            if (objectToIdMap.containsKey(obj)) {
                return new Identifier(objectToIdMap.get(obj));
            } else {
                return createExpressionForObject(obj);
            }
        } else if (value instanceof List) {
            List<Expression> elements = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item instanceof BEASTInterface) {
                    BEASTInterface listObj = (BEASTInterface) item;
                    if (objectToIdMap.containsKey(listObj)) {
                        elements.add(new Identifier(objectToIdMap.get(listObj)));
                    } else {
                        elements.add(createExpressionForObject(listObj));
                    }
                } else {
                    elements.add(createLiteralForValue(item));
                }
            }
            return new ArrayLiteral(elements);
        } else {
            return createLiteralForValue(value);
        }
    }

    /**
     * Create a variable declaration statement (using =)
     */
    private Statement createVariableDeclaration(BEASTInterface obj) {
        String id = objectToIdMap.get(obj);
        String className = obj.getClass().getSimpleName();
        Expression expr = createExpressionForObject(obj);

        VariableDeclaration decl = new VariableDeclaration(className, id, expr);

        // Check if this needs annotations
        if (obj instanceof Alignment) {
            logger.info("Checking annotations for alignment: " + id);

            // Check if it's a data source (not sampled)
            if (isDataSource(obj)) {
                logger.info("Creating @data annotation for: " + id);
                Map<String, Object> params = new HashMap<>();
                Annotation annotation = new Annotation("data", params);
                return new AnnotatedStatement(annotation, decl);
            }

            // Check if it's observed (only for non-state nodes that aren't data sources)
            if (!isInState(obj) && isObservedVariable(obj)) {
                logger.info("Creating @observed annotation for: " + id);
                Map<String, Object> params = new HashMap<>();
                BEASTInterface dataSource = findDataSource(obj);
                if (dataSource != null) {
                    params.put("data", objectToIdMap.get(dataSource));
                }
                Annotation annotation = new Annotation("observed", params);
                return new AnnotatedStatement(annotation, decl);
            }
        }

        return decl;
    }

    // Add this helper method
    private boolean isInState(BEASTInterface obj) {
        if (state == null || !(obj instanceof StateNode)) {
            return false;
        }

        for (StateNode stateNode : state.stateNodeInput.get()) {
            if (stateNode == obj) {
                return true;
            }
        }

        return false;
    }

    // Add method to check if object is a data source
    private boolean isDataSource(BEASTInterface obj) {
        // Check the actual class name
        String className = obj.getClass().getName();
        logger.info("Checking if " + className + " is a data source");

        // AlignmentFromNexus is always a data source
        if (className.contains("AlignmentFromNexus")) {
            logger.info("Found AlignmentFromNexus - it's a data source");
            return true;
        }

        // Check for fileName input
        if (obj instanceof Alignment) {
            for (Input<?> input : obj.getInputs().values()) {
                if (input.getName().equals("fileName") && input.get() != null) {
                    logger.info("Found fileName input - it's a data source");
                    return true;
                }
            }
        }

        logger.info("Not a data source");
        return false;
    }

    // Fix createExpressionForObject to actually include arguments
    private Expression createExpressionForObject(BEASTInterface obj) {
        // For RealParameter with generic IDs, return the actual value
        if (obj instanceof RealParameter && obj.getID() != null && obj.getID().matches("RealParameter\\d+")) {
            RealParameter param = (RealParameter) obj;
            if (param.getDimension() == 1) {
                return new Literal(param.getValue(), Literal.LiteralType.FLOAT);
            } else {
                // Return array of values
                List<Expression> values = new ArrayList<>();
                for (int i = 0; i < param.getDimension(); i++) {
                    values.add(new Literal(param.getValue(i), Literal.LiteralType.FLOAT));
                }
                return new ArrayLiteral(values);
            }
        }

        // Otherwise create function call
        List<Argument> args = createArgumentsForObject(obj);
        return new FunctionCall(obj.getClass().getSimpleName(), args);
    }

    private boolean isFixedParameter(BEASTInterface obj) {
        if (!(obj instanceof RealParameter)) {
            return false;
        }

        // Check if it's in the state
        for (StateNode stateNode : state.stateNodeInput.get()) {
            if (stateNode == obj) {
                return false; // It's a state node
            }
        }

        // Check if it has a prior
        for (BEASTInterface other : objectToIdMap.keySet()) {
            if (other instanceof Prior) {
                Prior prior = (Prior) other;
                if (prior.m_x.get() == obj) {
                    return false; // It has a prior
                }
            }
        }

        return true; // It's a fixed parameter
    }

    /**
     * Create arguments for a function call representing a BEAST object
     */
    private List<Argument> createArgumentsForObject(BEASTInterface obj) {
        List<Argument> args = new ArrayList<>();

        // If this is a distribution being used in a ~ statement, get its primary input name
        String primaryInputName = null;
        if (objectFactory.isDistribution(obj) && usedDistributions.contains(obj)) {
            primaryInputName = objectFactory.getPrimaryInputName(obj);
        }

        for (Input<?> input : obj.getInputs().values()) {
            // Skip empty inputs
            if (input.get() == null || (input.get() instanceof List && ((List<?>) input.get()).isEmpty())) {
                continue;
            }

            // Skip inputs we should filter out
            if (shouldSkipInput(obj, input)) {
                continue;
            }

            // Skip calculated inputs
            if (isCalculatedInput(obj, input)) {
                continue;
            }

            String inputName = input.getName();

            // Skip the primary input for distributions used in ~ statements
            if (primaryInputName != null && inputName.equals(primaryInputName)) {
                continue;
            }

            Expression value;

            if (input.get() instanceof BEASTInterface) {
                BEASTInterface inputObj = (BEASTInterface) input.get();

                // Check if this is a fixed parameter that should be inlined
                if (isFixedParameter(inputObj)) {
                    // Convert to literal
                    if (inputObj instanceof RealParameter) {
                        RealParameter param = (RealParameter) inputObj;
                        if (param.getDimension() == 1) {
                            value = new Literal(param.getValue(), Literal.LiteralType.FLOAT);
                        } else {
                            // Array of values
                            List<Expression> values = new ArrayList<>();
                            for (int i = 0; i < param.getDimension(); i++) {
                                values.add(new Literal(param.getValue(i), Literal.LiteralType.FLOAT));
                            }
                            value = new ArrayLiteral(values);
                        }
                    } else {
                        // Shouldn't happen, but fallback
                        value = new Identifier(objectToIdMap.get(inputObj));
                    }
                } else if (objectToIdMap.containsKey(inputObj)) {
                    // Reference to another object
                    value = new Identifier(objectToIdMap.get(inputObj));
                } else {
                    // Inline the object
                    value = createExpressionForObject(inputObj);
                }
            } else if (input.get() instanceof List) {
                // Create an array literal for list inputs
                List<Expression> elements = new ArrayList<>();
                for (Object item : (List<?>) input.get()) {
                    if (item instanceof BEASTInterface) {
                        BEASTInterface listObj = (BEASTInterface) item;
                        if (objectToIdMap.containsKey(listObj)) {
                            elements.add(new Identifier(objectToIdMap.get(listObj)));
                        } else {
                            elements.add(createExpressionForObject(listObj));
                        }
                    } else {
                        elements.add(createLiteralForValue(item));
                    }
                }
                value = new ArrayLiteral(elements);
            } else {
                // Create a literal for primitive values
                value = createLiteralForValue(input.get());
            }

            args.add(new Argument(inputName, value));
        }

        return args;
    }

    /**
     * Create a literal expression for a primitive value
     */
    private Literal createLiteralForValue(Object value) {
        if (value == null) {
            return new Literal("null", Literal.LiteralType.STRING);
        } else if (value instanceof Double || value instanceof Float) {
            return new Literal(value, Literal.LiteralType.FLOAT);
        } else if (value instanceof Integer || value instanceof Long) {
            return new Literal(value, Literal.LiteralType.INTEGER);
        } else if (value instanceof Boolean) {
            return new Literal(value, Literal.LiteralType.BOOLEAN);
        } else {
            return new Literal(value.toString(), Literal.LiteralType.STRING);
        }
    }

    /**
     * Find distributions that use a given state node.
     * Uses the object factory to check primary inputs.
     */
    private List<BEASTInterface> findDistributionsFor(StateNode node) {
        return objectToIdMap.keySet().stream()
                .filter(obj -> objectFactory.isDistribution(obj))
                .filter(dist -> {
                    String primaryInputName = objectFactory.getPrimaryInputName(dist);
                    if (primaryInputName != null) {
                        try {
                            Object primaryInputValue = objectFactory.getInputValue(dist, primaryInputName);
                            return primaryInputValue == node;
                        } catch (Exception e) {
                            return false;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    /**
     * Check if an object is an observed variable
     */
    private boolean isObservedVariable(BEASTInterface obj) {
        return obj instanceof GenericTreeLikelihood;
    }

    /**
     * Find the data source for an observed variable
     */
    private BEASTInterface findDataSource(BEASTInterface obj) {
        if (obj instanceof GenericTreeLikelihood) {
            GenericTreeLikelihood likelihood = (GenericTreeLikelihood) obj;
            return likelihood.dataInput.get();
        }
        return null;
    }

    /**
     * Sort statements in the model based on dependencies
     */
    private void sortStatements(Beast2Model model) {
        List<Statement> sortedStatements = new ArrayList<>();
        Set<String> declaredIds = new HashSet<>();
        List<Statement> remainingStatements = new ArrayList<>(model.getStatements());

        // First pass: Add all @data statements (they have no dependencies)
        Iterator<Statement> iter = remainingStatements.iterator();
        while (iter.hasNext()) {
            Statement stmt = iter.next();
            if (stmt instanceof AnnotatedStatement) {
                AnnotatedStatement annotatedStmt = (AnnotatedStatement) stmt;
                if ("data".equals(annotatedStmt.getAnnotation().getName())) {
                    sortedStatements.add(stmt);
                    declaredIds.add(getStatementId(stmt));
                    iter.remove();
                }
            }
        }

        // Repeatedly process remaining statements until all are sorted
        boolean progress = true;
        while (!remainingStatements.isEmpty() && progress) {
            progress = false;
            iter = remainingStatements.iterator();

            while (iter.hasNext()) {
                Statement stmt = iter.next();
                Set<String> dependencies = getStatementDependencies(stmt);

                // If all dependencies have been declared, we can add this statement
                if (declaredIds.containsAll(dependencies)) {
                    sortedStatements.add(stmt);
                    declaredIds.add(getStatementId(stmt));
                    iter.remove();
                    progress = true;
                }
            }
        }

        // If there are remaining statements, it means there's a circular dependency
        // Just add them in the original order
        if (!remainingStatements.isEmpty()) {
            logger.warning("Circular dependency detected. Adding remaining statements in original order.");
            sortedStatements.addAll(remainingStatements);
        }

        // Clear only the statements
        model.clearStatements();

        // Add the sorted statements back
        for (Statement stmt : sortedStatements) {
            model.addStatement(stmt);
        }
    }

    /**
     * Get the ID declared by a statement
     */
    private String getStatementId(Statement stmt) {
        if (stmt instanceof VariableDeclaration) {
            return ((VariableDeclaration) stmt).getVariableName();
        } else if (stmt instanceof DistributionAssignment) {
            return ((DistributionAssignment) stmt).getVariableName();
        } else if (stmt instanceof AnnotatedStatement) {
            return getStatementId(((AnnotatedStatement) stmt).getStatement());
        }
        return null;
    }

    /**
     * Get the IDs that a statement depends on
     */
    private Set<String> getStatementDependencies(Statement stmt) {
        Set<String> dependencies = new HashSet<>();

        if (stmt instanceof AnnotatedStatement) {
            AnnotatedStatement annotatedStmt = (AnnotatedStatement) stmt;
            // Check annotation parameters for dependencies
            Map<String, Object> params = annotatedStmt.getAnnotation().getParameters();
            for (Object value : params.values()) {
                if (value instanceof String) {
                    // This might be a reference to another object
                    dependencies.add((String) value);
                }
            }
            // Also check the wrapped statement
            dependencies.addAll(getStatementDependencies(annotatedStmt.getStatement()));
        } else if (stmt instanceof VariableDeclaration) {
            VariableDeclaration varDecl = (VariableDeclaration) stmt;
            collectExpressionDependencies(varDecl.getValue(), dependencies);
        } else if (stmt instanceof DistributionAssignment) {
            DistributionAssignment distAssign = (DistributionAssignment) stmt;
            collectExpressionDependencies(distAssign.getDistribution(), dependencies);
        }

        return dependencies;
    }

    /**
     * Collect dependencies from an expression
     */
    private void collectExpressionDependencies(Expression expr, Set<String> dependencies) {
        if (expr instanceof Identifier) {
            dependencies.add(((Identifier) expr).getName());
        } else if (expr instanceof FunctionCall) {
            FunctionCall funcCall = (FunctionCall) expr;
            for (Argument arg : funcCall.getArguments()) {
                collectExpressionDependencies(arg.getValue(), dependencies);
            }
        } else if (expr instanceof ArrayLiteral) {
            ArrayLiteral array = (ArrayLiteral) expr;
            for (Expression element : array.getElements()) {
                collectExpressionDependencies(element, dependencies);
            }
        }
        // Literals don't have dependencies
    }

    /**
     * Check if an input is calculated (rather than explicitly set)
     */
    private boolean isCalculatedInput(BEASTInterface obj, Input<?> input) {
        // For Tree objects, taxonset is explicitly set, not calculated
        if (obj instanceof beast.base.evolution.tree.Tree && input.getName().equals("taxonset")) {
            return false;
        }

        // For StateNode objects, 'value' and 'dimension' are often calculated from other inputs
        if (obj instanceof StateNode) {
            String inputName = input.getName();
            if (inputName.equals("value") || inputName.equals("dimension")) {
                return true;
            }
        }

        // For other cases, we can't reliably determine if it's calculated
        return false;
    }
}
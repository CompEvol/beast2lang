package org.beast2.modelLanguage.converter;

import beast.base.core.BEASTInterface;
import beast.base.core.BEASTObject;
import beast.base.core.Input;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.likelihood.GenericTreeLikelihood;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.inference.parameter.Parameter;
import beast.base.inference.distribution.Prior;
import org.beast2.modelLanguage.model.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Converts a BEAST2 object model to a Beast2Lang model.
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
    private int uniqueCounter = 0;

    /**
     * Convert a BEAST2 analysis to a Beast2Lang model.
     *
     * @param posterior The posterior distribution (usually CompoundDistribution)
     * @param state The MCMC state containing all state nodes
     * @return A Beast2Model representing the analysis
     */
    public Beast2Model convertToBeast2Model(Distribution posterior, State state) {
        Beast2Model model = new Beast2Model();

        // Add common imports
        addCommonImports(model);

        // First pass: identify all objects and generate identifiers
        identifyObjects(posterior, state);

        // Second pass: create statements for all objects
        for (BEASTInterface object : objectToIdMap.keySet()) {
            if (!processedObjects.contains(object)) {
                processObject(object, model);
            }
        }

        // Sort statements based on dependencies
        sortStatements(model);

        return model;
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

        // Generate an identifier if not already done
        if (!objectToIdMap.containsKey(obj)) {
            String id = generateIdentifier(obj);
            objectToIdMap.put(obj, id);
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
    private void processObject(BEASTInterface obj, Beast2Model model) {
        if (objectToStatementMap.containsKey(obj)) {
            return; // Already processed
        }

        // Process dependencies first
        for (Input<?> input : obj.getInputs().values()) {
            if (input.get() != null) {
                if (input.get() instanceof BEASTInterface) {
                    BEASTInterface dependency = (BEASTInterface) input.get();
                    if (!objectToStatementMap.containsKey(dependency)) {
                        processObject(dependency, model);
                    }
                } else if (input.get() instanceof List) {
                    for (Object item : (List<?>) input.get()) {
                        if (item instanceof BEASTInterface) {
                            BEASTInterface dependency = (BEASTInterface) item;
                            if (!objectToStatementMap.containsKey(dependency)) {
                                processObject(dependency, model);
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
        ImportStatement importStmt = new ImportStatement(packageName, false);
        model.addImport(importStmt);
    }

    /**
     * Create a Beast2Lang statement for a BEAST object
     */
    private Statement createStatement(BEASTInterface obj) {
        String id = objectToIdMap.get(obj);
        String className = obj.getClass().getSimpleName();

        if (obj instanceof StateNode) {
            // For state nodes, determine if they're part of a distribution
            List<BEASTInterface> distributions = findDistributionsFor((StateNode) obj);
            if (!distributions.isEmpty()) {
                // Create a distribution assignment
                BEASTInterface distribution = distributions.get(0);
                return createDistributionAssignment(obj, distribution);
            }
        }

        if (obj instanceof Alignment) {
            // Check if this is data loaded from a file
            // For simplicity, assume all alignments are from Nexus files
            return createDataAnnotatedDeclaration((Alignment) obj);
        }

        if (obj instanceof Distribution) {
            // For distributions that don't have a direct state node
            // (e.g., TreeLikelihood), create a variable declaration
            return createVariableDeclaration(obj);
        }

        // Default: create a variable declaration
        return createVariableDeclaration(obj);
    }

    /**
     * Create a distribution assignment statement (using ~)
     */
    private DistributionAssignment createDistributionAssignment(BEASTInterface param, BEASTInterface distribution) {
        String id = objectToIdMap.get(param);
        String className = param.getClass().getSimpleName();
        Expression expr = createExpressionForObject(distribution);

        return new DistributionAssignment(className, id, expr);
    }

    /**
     * Create a variable declaration statement (using =)
     */
    private Statement createVariableDeclaration(BEASTInterface obj) {
        String id = objectToIdMap.get(obj);
        String className = obj.getClass().getSimpleName();
        Expression expr = createExpressionForObject(obj);

        VariableDeclaration decl = new VariableDeclaration(className, id, expr);

        // Check if this is an observed variable
        if (isObservedVariable(obj)) {
            Map<String, Object> params = new HashMap<>();
            // Find the data source
            BEASTInterface dataSource = findDataSource(obj);
            if (dataSource != null) {
                params.put("data", objectToIdMap.get(dataSource));
            }

            Annotation annotation = new Annotation("observed", params);
            return new AnnotatedStatement(annotation, decl);
        }

        return decl;
    }

    /**
     * Create a data-annotated declaration for an alignment
     */
    private Statement createDataAnnotatedDeclaration(Alignment alignment) {
        String id = objectToIdMap.get(alignment);
        String className = alignment.getClass().getSimpleName();

        // Assume the alignment is from a Nexus file
        List<Argument> args = new ArrayList<>();
        args.add(new Argument("file", new Literal("primates.nex", Literal.LiteralType.STRING)));

        NexusFunction nexusFunc = new NexusFunction(args);
        VariableDeclaration decl = new VariableDeclaration(className, id, nexusFunc);

        // Add @data annotation
        Annotation annotation = new Annotation("data", new HashMap<>());
        return new AnnotatedStatement(annotation, decl);
    }

    /**
     * Create an expression for a BEAST object
     */
    private Expression createExpressionForObject(BEASTInterface obj) {
        // If the object is already processed and has an ID, use an identifier
        if (objectToIdMap.containsKey(obj) && !(obj instanceof Prior)) {
            return new Identifier(objectToIdMap.get(obj));
        }

        // Otherwise, create a function call
        List<Argument> args = createArgumentsForObject(obj);
        return new FunctionCall(obj.getClass().getSimpleName(), args);
    }

    /**
     * Create arguments for a function call representing a BEAST object
     */
    private List<Argument> createArgumentsForObject(BEASTInterface obj) {
        List<Argument> args = new ArrayList<>();

        for (Input<?> input : obj.getInputs().values()) {
            // Skip empty inputs
            if (input.get() == null || (input.get() instanceof List && ((List<?>) input.get()).isEmpty())) {
                continue;
            }

            // Skip calculated inputs
            if (isCalculatedInput(obj, input)) {
                continue;
            }

            String inputName = input.getName();
            Expression value;

            if (input.get() instanceof BEASTInterface) {
                BEASTInterface inputObj = (BEASTInterface) input.get();
                if (objectToIdMap.containsKey(inputObj)) {
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
     * Find distributions that use a given state node
     */
    private List<BEASTInterface> findDistributionsFor(StateNode node) {
        return objectToIdMap.keySet().stream()
                .filter(obj -> obj instanceof Distribution)
                .filter(dist -> isStateNodeUsedInDistribution(node, dist))
                .collect(Collectors.toList());
    }

    /**
     * Check if a state node is used in a distribution
     */
    private boolean isStateNodeUsedInDistribution(StateNode node, BEASTInterface dist) {
        // Check direct inputs
        for (Input<?> input : dist.getInputs().values()) {
            if (input.get() == node) {
                return true;
            }

            // Check list inputs
            if (input.get() instanceof List) {
                for (Object item : (List<?>) input.get()) {
                    if (item == node) {
                        return true;
                    }
                }
            }
        }

        return false;
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
        // This is a simple implementation that assumes no circular dependencies
        // A more robust solution would use a topological sort

        // For now, we just put data nodes first, followed by parameters,
        // and then the rest of the statements

        List<Statement> sortedStatements = new ArrayList<>();

        // First: statements with @data annotation
        for (Statement stmt : model.getStatements()) {
            if (stmt instanceof AnnotatedStatement) {
                AnnotatedStatement annotatedStmt = (AnnotatedStatement) stmt;
                if ("data".equals(annotatedStmt.getAnnotation().getName())) {
                    sortedStatements.add(stmt);
                }
            }
        }

        // Second: parameter declarations
        for (Statement stmt : model.getStatements()) {
            if (!sortedStatements.contains(stmt)) {
                if (stmt instanceof DistributionAssignment) {
                    sortedStatements.add(stmt);
                }
            }
        }

        // Third: everything else
        for (Statement stmt : model.getStatements()) {
            if (!sortedStatements.contains(stmt)) {
                sortedStatements.add(stmt);
            }
        }

        // Update the model with sorted statements
        // Instead of calling clearStatements(), we'll create a new model
        // and copy the imports and sorted statements to it
        List<ImportStatement> imports = new ArrayList<>(model.getImports());

        // Clear the original model
        model.getImports().clear();
        model.getStatements().clear();

        // Add the imports back
        for (ImportStatement importStmt : imports) {
            model.addImport(importStmt);
        }

        // Add the sorted statements
        for (Statement stmt : sortedStatements) {
            model.addStatement(stmt);
        }
    }

    /**
     * Check if an input is calculated (rather than explicitly set)
     */
    private boolean isCalculatedInput(BEASTInterface obj, Input<?> input) {
        try {
            // Try to find a getter method for this input
            String inputName = input.getName();
            String getterName = "get" + Character.toUpperCase(inputName.charAt(0)) + inputName.substring(1);
            Method getter = obj.getClass().getMethod(getterName);

            // If the getter exists and the input is not explicitly set, it's calculated
            return getter != null && input.get() != null;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
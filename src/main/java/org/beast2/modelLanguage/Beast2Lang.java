package org.beast2.modelLanguage;

import beast.base.core.BEASTInterface;
import beast.base.parser.XMLProducer;
import beast.base.parser.XMLParser;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.State;
import beast.pkgmgmt.PackageManager;
import org.beast2.modelLanguage.beast.Beast2ModelBuilder;
import org.beast2.modelLanguage.model.RequiresStatement;
import org.beast2.modelLanguage.phylospec.Beast2LangParserWithPhyloSpec;
import org.beast2.modelLanguage.builder.Beast2LangParser;
import org.beast2.modelLanguage.builder.Beast2LangParserImpl;
import org.beast2.modelLanguage.converter.*;
import org.beast2.modelLanguage.model.Beast2Model;
import org.beast2.modelLanguage.model.Beast2Analysis;
import org.beast2.modelLanguage.beast.Beast2AnalysisBuilder;
import beast.base.inference.MCMC;

import org.beast2.modelLanguage.schema.BEAST2ModelLibraryGenerator;
import org.beast2.modelLanguage.schema.validation.ValidationResult;
import org.json.JSONArray;
import org.json.JSONObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main application class for Beast2Lang
 */
@Command(name = "beast2lang",
        mixinStandardHelpOptions = true,
        version = "beast2lang 0.1.0",
        description = "Provides utilities for working with Beast2 model definition language")
public class Beast2Lang implements Callable<Integer> {

    private static final Logger logger = Logger.getLogger(Beast2Lang.class.getName());

    @Command(name = "run", description = "Run a Beast2 model after conversion")
    public Integer runModel(
            @Option(names = {"-i", "--input"}, description = "Input Beast2Lang file", required = true) File inputFile,
            @Option(names = {"-o", "--output"}, description = "Output Beast2 XML file", defaultValue="model.xml") File outputFile,
            @Option(names = {"--chainLength"}, defaultValue = "10000000", description = "MCMC chain length") long chainLength,
            @Option(names = {"--logEvery"}, defaultValue = "1000", description = "Logging interval") int logEvery,
            @Option(names = {"--traceFileName"}, defaultValue = "trace.log", description = "Trace log file name") String traceFileName,
            @Option(names = {"--treeFileName"}, defaultValue = "tree.trees", description = "Tree log file name") String treeFileName,
            @Option(names = {"--debug"}, description = "Enable debug logging", defaultValue = "false") boolean debug,
            @Option(names = {"--seed"}, description = "Random seed for MCMC run") Long seed,
            @Option(names = {"--threads"}, defaultValue = "1", description = "Number of threads") int threads,
            @Option(names = {"--resume"}, description = "Resume from previous run", defaultValue = "false") boolean resume,
            @Option(names = {"--phylospec"}, description = "Use PhyloSpec syntax", defaultValue = "false") boolean usePhyloSpec) {

        // Set debug level if requested
        if (debug) {
            java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
            rootLogger.setLevel(Level.FINE);
            for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
                handler.setLevel(Level.FINE);
            }
        }

        try {
            System.out.println("Running Beast2 model from file: " + inputFile.getPath());

            // First convert the model to BEAST2 objects
            Beast2ModelBuilder modelBuilder = new Beast2ModelBuilder();

            // Use appropriate parser based on PhyloSpec flag
            Beast2LangParser parser = usePhyloSpec
                    ? new Beast2LangParserWithPhyloSpec()
                    : new Beast2LangParserImpl();

            try (FileInputStream fis = new FileInputStream(inputFile)) {
                // Parse the model with the selected parser
                Beast2Model model = usePhyloSpec
                        ? parser.parseFromStream(fis)
                        : modelBuilder.buildFromStream(fis);

                // Create analysis parameters
                Beast2Analysis analysis = new Beast2Analysis(
                        model,
                        chainLength,
                        logEvery,
                        traceFileName
                );

                // Set additional parameters
                analysis.setTreeLogFileName(treeFileName);
                if (seed != null) {
                    analysis.setSeed(seed);
                }
                analysis.setThreadCount(threads);

                // Build the MCMC run object
                Beast2AnalysisBuilder analysisBuilder = new Beast2AnalysisBuilder(modelBuilder);
                MCMC mcmc = analysisBuilder.buildRun(analysis);

                // Before running, dump the model structure for debugging
                if (debug) {
                    System.out.println("\nDumping model structure before running...");
                    dumpModelStructure(modelBuilder.getAllObjects());
                }

                System.out.println("Writing XML...");

                // Generate XML
                String xml = generateXML(mcmc);
                // Write XML to output file
                writeOutput(outputFile, xml);

                System.out.println("XML written to " + outputFile.getPath());

                // Now instead of running the existing MCMC object, we'll load from the XML
                System.out.println("Loading the model from XML...");

                try {
                    // Use BEAST2's XMLParser to read the XML back in
                    beast.base.parser.XMLParser parser2 = new beast.base.parser.XMLParser();
                    Object loadedObject = parser2.parseFile(outputFile);

                    if (loadedObject instanceof MCMC) {
                        MCMC loadedMCMC = (MCMC) loadedObject;

                        // Run the MCMC loaded from XML
                        System.out.println("Starting MCMC run from loaded XML...");
                        loadedMCMC.run();

                        System.out.println("MCMC run completed successfully.");
                        return 0;
                    } else {
                        throw new RuntimeException("Loaded object is not an MCMC instance: " +
                                (loadedObject != null ? loadedObject.getClass().getName() : "null"));
                    }
                } catch (Exception e) {
                    System.err.println("Error loading or running from XML: " + e.getMessage());
                    if (debug) {
                        e.printStackTrace();
                    }
                    return 1;
                }
            }
        } catch (Exception e) {
            System.err.println("Error running BEAST2 model: " + e.getMessage());

            if (debug) {
                e.printStackTrace();
            } else {
                // Print a more useful stack trace
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                logger.severe("Detailed error: " + sw.toString());
            }

            return 1;
        }
    }

    @Command(name = "validate", description = "Validate a Beast2Lang file")
    public Integer validate(
            @Parameters(paramLabel = "FILE", description = "Beast2Lang file to validate") File file,
            @Option(names = {"--phylospec"}, description = "Use PhyloSpec syntax", defaultValue = "false") boolean usePhyloSpec) {
        try {
            System.out.println("Validating " + file.getPath() + "...");

            // Use appropriate parser based on PhyloSpec flag
            Beast2LangParser parser = usePhyloSpec
                    ? new Beast2LangParserWithPhyloSpec()
                    : new Beast2LangParserImpl();

            try (FileInputStream fis = new FileInputStream(file)) {
                Beast2Model model = parser.parseFromStream(fis);
                System.out.println("Model is valid. Contains " + model.getStatements().size() + " statements.");
                return 0;
            }
        } catch (Exception e) {
            System.err.println("Error validating file: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    @Command(name = "lphy", description = "Convert Beast2Lang to LinguaPhylo (LPHY)")
    public Integer convertToLPHY(
            @Option(names = {"-i", "--input"}, description = "Input Beast2Lang file", required = true) File inputFile,
            @Option(names = {"-o", "--output"}, description = "Output LPHY file") File outputFile,
            @Option(names = {"--debug"}, description = "Enable debug logging", defaultValue = "false") boolean debug) {

        // Set debug level if requested
        if (debug) {
            java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
            rootLogger.setLevel(Level.FINE);
            for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
                handler.setLevel(Level.FINE);
            }
        }

        try {
            System.out.println("Converting Beast2Lang to LPHY: " + inputFile.getPath());

            // If output file is not specified, derive from input
            if (outputFile == null) {
                String baseName = inputFile.getName();
                if (baseName.endsWith(".b2l")) {
                    baseName = baseName.substring(0, baseName.length() - 4);
                }
                outputFile = new File(baseName + ".lphy");
            }

            // Create the converter
            Beast2ToLPHYConverter converter = new Beast2ToLPHYConverter();

            // Convert the file
            converter.convertToFile(inputFile.getPath(), outputFile.getPath());

            System.out.println("Beast2Lang file successfully converted to LPHY: " + outputFile);
            return 0;

        } catch (Exception e) {
            System.err.println("Error converting Beast2Lang to LPHY: " + e.getMessage());

            if (debug) {
                e.printStackTrace();
            } else {
                // Print a more useful stack trace
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                logger.severe("Detailed error: " + sw.toString());
            }

            return 1;
        }
    }

    @Command(name = "convert", description = "Convert between Beast2Lang and other formats")
    public Integer convert(
            @Option(names = {"-i", "--input"}, description = "Input file", required = true) File inputFile,
            @Option(names = {"--from"}, description = "Source format: beast2, phylospec, lphy, xml", defaultValue = "beast2") String fromFormat,
            @Option(names = {"--to"}, description = "Target format: beast2, phylospec, lphy, xml", defaultValue = "phylospec") String toFormat,
            @Option(names = {"-o", "--output"}, description = "Output file") File outputFile,
            @Option(names = {"--debug"}, description = "Enable debug logging", defaultValue = "false") boolean debug,
            @Option(names="--chainLength", defaultValue="10000000", description="Default MCMC chain length") long chainLength,
            @Option(names="--logEvery", defaultValue="1000", description="Default logging interval") int logEvery,
            @Option(names="--traceFileName", defaultValue="trace.log", description="Default trace log file name") String traceFileName) {

        // Set debug level if requested
        if (debug) {
            java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
            rootLogger.setLevel(Level.FINE);
            for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
                handler.setLevel(Level.FINE);
            }
        }

        try {
            System.out.println("Converting from " + fromFormat + " to " + toFormat + "...");

            // Initialize converters
            Beast2ToPhyloSpecConverter toPhyloSpecConverter = new Beast2ToPhyloSpecConverter();
            PhyloSpecToBeast2Converter toBeast2Converter = new PhyloSpecToBeast2Converter();
            Beast2ModelBuilder reflectionBuilder = new Beast2ModelBuilder();
            Beast2ToLPHYConverter toLPHYConverter = new Beast2ToLPHYConverter();

            // Perform conversion
            if ("beast2".equals(fromFormat) && "phylospec".equals(toFormat)) {
                try (FileInputStream fis = new FileInputStream(inputFile)) {
                    // Convert Beast2Lang to PhyloSpec
                    Beast2Model model = reflectionBuilder.buildFromStream(fis);
                    JSONObject phyloSpec = toPhyloSpecConverter.convert(model);

                    // Output result
                    writeOutput(outputFile, phyloSpec.toString(2));
                    return 0;
                }
            } else if ("phylospec".equals(fromFormat) && "beast2".equals(toFormat)) {
                // Read PhyloSpec JSON
                String content = new String(Files.readAllBytes(inputFile.toPath()));
                JSONObject phyloSpec = new JSONObject(content);

                // Convert PhyloSpec to Beast2Lang
                Beast2Model model = toBeast2Converter.convert(phyloSpec);
                String beast2Lang = generateBeast2Lang(model);

                // Output result
                writeOutput(outputFile, beast2Lang);
                return 0;
            } else if ("beast2".equals(fromFormat) && "xml".equals(toFormat)) {
                try (FileInputStream fis = new FileInputStream(inputFile)) {
                    // Parse the pure model
                    Beast2Model model = reflectionBuilder.buildFromStream(fis);

                    // Wrap in analysis
                    Beast2Analysis analysis = new Beast2Analysis(
                            model,
                            chainLength,      // from CLI option --chainLength
                            logEvery,         // from CLI option --logEvery
                            traceFileName     // from CLI option --traceFileName
                    );

                    // Build the run
                    Beast2AnalysisBuilder analysisBuilder =
                            new Beast2AnalysisBuilder(reflectionBuilder);

                    String xml;
                    MCMC rootRun = analysisBuilder.buildRun(analysis);

                    // Add this before generating XML
                    try {
                        System.out.println("\nDumping model structure before XML generation...");
                        dumpModelStructure(reflectionBuilder.getAllObjects());
                    } catch (Exception e) {
                        System.out.println("Error dumping model structure: " + e.getMessage());
                    }

                    // Then continue with XML generation
                    try {
                        xml = generateXML(rootRun);
                        // Generate XML
                        writeOutput(outputFile, xml);
                    } catch (Exception e) {
                        throw new RuntimeException("XML generation failure!", e);
                    }
                    return 0;
                }
            } else if ("xml".equals(fromFormat) && "beast2".equals(toFormat)) {
                // Handle XML to Beast2Lang conversion
                System.out.println("Converting BEAST2 XML to Beast2Lang...");

                // Parse the XML file
                XMLParser parser = new XMLParser();
                BEASTInterface beast = parser.parseFile(inputFile);

                if (!(beast instanceof MCMC)) {
                    throw new IllegalArgumentException("Input XML does not contain an MCMC analysis");
                }

                MCMC mcmc = (MCMC) beast;

                // Extract the posterior distribution and state
                CompoundDistribution posterior = (CompoundDistribution) mcmc.posteriorInput.get();
                State state = mcmc.startStateInput.get();

                // Convert to Beast2Lang model
                Beast2ToBeast2LangConverter converter = new Beast2ToBeast2LangConverter();
                Beast2Model model = converter.convertToBeast2Model(posterior, state, mcmc);

                // Write the model to a file
                Beast2ModelWriter writer = new Beast2ModelWriter();
                String scriptContent = writer.writeModel(model);

                // If output file is not specified, derive from input
                if (outputFile == null) {
                    String baseName = inputFile.getName();
                    if (baseName.endsWith(".xml")) {
                        baseName = baseName.substring(0, baseName.length() - 4);
                    }
                    outputFile = new File(baseName + ".b2l");
                }

                // Save to file
                writeOutput(outputFile, scriptContent);
                System.out.println("BEAST2 XML file successfully converted to Beast2Lang script: " + outputFile);
                return 0;
            } else if ("beast2".equals(fromFormat) && "lphy".equals(toFormat)) {
                // Convert Beast2Lang to LPHY
                try {
                    toLPHYConverter.convertToFile(inputFile.getPath(), outputFile.getPath());
                    System.out.println("Beast2Lang file successfully converted to LPHY: " + outputFile);
                    return 0;
                } catch (IOException e) {
                    throw new RuntimeException("Error converting to LPHY", e);
                }
            } else if ("lphy".equals(fromFormat)) {
                // Handle LinguaPhylo format conversion if needed
                System.err.println("LinguaPhylo conversion not yet implemented");
                return 1;
            } else {
                System.err.println("Unsupported conversion: " + fromFormat + " to " + toFormat);
                return 1;
            }
        } catch (Exception e) {
            System.err.println("Error converting file: " + e.getMessage());

            if (debug) {
                e.printStackTrace();
            } else {
                // Print a more useful stack trace
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                logger.severe("Detailed error: " + sw.toString());
            }

            return 1;
        }
    }

    @Command(name = "decompile", description = "Decompile BEAST2 XML to Beast2Lang")
    public Integer decompile(
            @Option(names = {"-i", "--input"}, description = "Input BEAST2 XML file", required = true) File inputFile,
            @Option(names = {"-o", "--output"}, description = "Output Beast2Lang file") File outputFile,
            @Option(names = {"--debug"}, description = "Enable debug logging", defaultValue = "false") boolean debug) {

        // Set debug level if requested
        if (debug) {
            java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
            rootLogger.setLevel(Level.FINE);
            for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
                handler.setLevel(Level.FINE);
            }
        }

        try {
            System.out.println("Decompiling BEAST2 XML file: " + inputFile.getPath());

            // If output file is not specified, derive from input
            if (outputFile == null) {
                String baseName = inputFile.getName();
                if (baseName.endsWith(".xml")) {
                    baseName = baseName.substring(0, baseName.length() - 4);
                }
                outputFile = new File(baseName + ".b2l");
            }

            PackageManager.loadExternalJars();

            // Parse the XML file
            XMLParser parser = new XMLParser();
            BEASTInterface beast = parser.parseFile(inputFile);

            if (!(beast instanceof MCMC)) {
                throw new IllegalArgumentException("Input XML does not contain an MCMC analysis");
            }

            MCMC mcmc = (MCMC) beast;

            // Extract the posterior distribution and state
            CompoundDistribution posterior = (CompoundDistribution) mcmc.posteriorInput.get();
            State state = mcmc.startStateInput.get();

            // Convert to Beast2Lang model - PASS THE MCMC OBJECT TOO
            Beast2ToBeast2LangConverter converter = new Beast2ToBeast2LangConverter();
            Beast2Model model = converter.convertToBeast2Model(posterior, state, mcmc);  // ADD mcmc PARAMETER

            String required = extractRequiredPackages(inputFile);
            if (required != null) {
                addRequiresFromString(model, required);
            }

            // Write the model to a file
            Beast2ModelWriter writer = new Beast2ModelWriter();
            String scriptContent = writer.writeModel(model);

            // Save to file
            writeOutput(outputFile, scriptContent);

            System.out.println("BEAST2 XML file successfully decompiled to Beast2Lang script: " + outputFile);
            return 0;

        } catch (Exception e) {
            System.err.println("Error decompiling BEAST2 XML: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            } else {
                // Print a more useful stack trace
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                logger.severe("Detailed error: " + sw.toString());
            }
            return 1;
        }
    }

    @Command(name = "schema", description = "Generate BEAST2 engine library schema")
    public Integer generateSchema(
            @Option(names = {"-o", "--output"}, description = "Output JSON file", defaultValue = "beast2-model-library.json")
            File outputFile,
            @Option(names = {"--packages"}, description = "Additional packages to include (comma-separated)")
            String packages,
            @Option(names = {"--debug"}, description = "Enable debug logging", defaultValue = "false")
            boolean debug,
            @Option(names = {"--pretty"}, description = "Pretty print JSON output", defaultValue = "true")
            boolean prettyPrint,
            @Option(names = {"--test-closure"}, description = "Test type closure after generation", defaultValue = "false")
            boolean testClosure) {

        // Set debug level if requested
        if (debug) {
            java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
            rootLogger.setLevel(Level.FINE);
            for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
                handler.setLevel(Level.FINE);
            }
        }

        try {
            System.out.println("Generating BEAST2 model library schema...");

            // Load external packages if needed
            PackageManager.loadExternalJars();

            BEAST2ModelLibraryGenerator generator = new BEAST2ModelLibraryGenerator();

            // Generate the schema
            String schema = generator.generateModelLibrary();

            // Write to file
            writeOutput(outputFile, schema, prettyPrint);

            // Print summary
            JSONObject schemaObj = new JSONObject(schema);
            JSONObject modelLibrary = schemaObj.getJSONObject("modelLibrary");

            // NEW FORMAT: Get types and generators instead of components
            JSONArray types = modelLibrary.getJSONArray("types");
            JSONArray generators = modelLibrary.getJSONArray("generators");

            System.out.println("\nSchema generation complete!");
            System.out.println("Engine: " + modelLibrary.getString("engine") + " " + modelLibrary.getString("engineVersion"));
            System.out.println("Total types: " + types.length());
            System.out.println("Total generators: " + generators.length());

            // Count distributions vs functions in generators
            int distributionCount = 0;
            int functionCount = 0;

            for (int i = 0; i < generators.length(); i++) {
                JSONObject generatorJSON = generators.getJSONObject(i);
                if (generatorJSON.getString("generatorType").equals("distribution")) {
                    distributionCount++;
                } else {
                    functionCount++;
                }
            }

            System.out.println("\nGenerators:");
            System.out.println("  Distributions: " + distributionCount);
            System.out.println("  Functions: " + functionCount);

            // Count type categories
            int abstractCount = 0;
            int interfaceCount = 0;
            int concreteCount = 0;
            int primitiveCount = 0;

            for (int i = 0; i < types.length(); i++) {
                JSONObject type = types.getJSONObject(i);
                if (type.optBoolean("primitiveAssignable", false)) {
                    primitiveCount++;
                } else if (type.optBoolean("isInterface", false)) {
                    interfaceCount++;
                } else if (type.optBoolean("isAbstract", false)) {
                    abstractCount++;
                } else {
                    concreteCount++;
                }
            }

            System.out.println("\nType categories:");
            System.out.println("  Primitives/Assignable: " + primitiveCount);
            System.out.println("  Interfaces: " + interfaceCount);
            System.out.println("  Abstract classes: " + abstractCount);
            System.out.println("  Concrete classes: " + concreteCount);

            System.out.println("\nSchema written to: " + outputFile.getPath());

            if (testClosure) {
                System.out.println("\nRunning closure test...");
                ValidationResult result = generator.validateSchema(schema);
                System.out.println(result.generateReport());
            }

            return 0;

        } catch (Exception e) {
            System.err.println("Error generating schema: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            } else {
                // Print a more useful stack trace
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                logger.severe("Detailed error: " + sw.toString());
            }
            return 1;
        }
    }

    /**
     * Write output with optional pretty printing
     */
    private void writeOutput(File outputFile, String schema, boolean prettyPrint) throws IOException {
        if (prettyPrint) {
            // Re-parse and pretty print with proper indentation
            JSONObject schemaObj = new JSONObject(schema);
            Files.write(outputFile.toPath(), schemaObj.toString(2).getBytes(StandardCharsets.UTF_8));
        } else {
            Files.write(outputFile.toPath(), schema.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Capitalize first letter of a string
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    // Helper method to extract required attribute from XML
    private String extractRequiredPackages(File xmlFile) throws IOException {
        // Simple regex or XML parsing to extract the required attribute
        String content = Files.readString(xmlFile.toPath());
        Pattern pattern = Pattern.compile("required=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // Helper method to parse required string and add to model
    private void addRequiresFromString(Beast2Model model, String required) {
        // Parse "BEAST.base v2.7.7:feast v10.4.0"
        String[] packages = required.split(":");
        for (String pkg : packages) {
            // Remove version info
            String packageName = pkg.trim().split("\\s+")[0];
            model.addRequires(new RequiresStatement(packageName));
        }
    }
    
    private void dumpModelStructure(Map<String, Object> objects) {
        System.out.println("\n----- MODEL STRUCTURE DUMP -----");

        // Print all objects
        System.out.println("\nObjects:");
        for (Map.Entry<String, Object> entry : objects.entrySet()) {
            String id = entry.getKey();
            Object obj = entry.getValue();
            String className = obj.getClass().getName();

            System.out.println(id + " (" + className + ")");
        }

        // Print connections (inputs)
        System.out.println("\nConnections:");
        for (Map.Entry<String, Object> entry : objects.entrySet()) {
            String id = entry.getKey();
            Object obj = entry.getValue();

            // Only process BEAST objects with inputs
            if (obj instanceof beast.base.core.BEASTInterface) {
                beast.base.core.BEASTInterface beastObj = (beast.base.core.BEASTInterface) obj;
                System.out.println("\n" + id + " (" + obj.getClass().getSimpleName() + ") connections:");

                // Print all inputs and their values
                for (String inputName : beastObj.getInputs().keySet()) {
                    try {
                        beast.base.core.Input<?> input = beastObj.getInput(inputName);
                        Object value = input.get();

                        // Format the value based on its type
                        String valueStr;
                        if (value == null) {
                            valueStr = "null";
                        } else if (value instanceof beast.base.core.BEASTInterface) {
                            valueStr = ((beast.base.core.BEASTInterface) value).getID()
                                    + " (" + value.getClass().getSimpleName() + ")";
                        } else if (value instanceof Collection) {
                            valueStr = formatCollection((Collection<?>) value);
                        } else {
                            valueStr = value.toString();
                        }

                        System.out.println("  " + inputName + " = " + valueStr);
                    } catch (Exception e) {
                        System.out.println("  " + inputName + " = [Error: " + e.getMessage() + "]");
                    }
                }
            }
        }

        // Check for obvious issues
        System.out.println("\nPotential Issues:");
        checkForIssues(objects);

        System.out.println("\n----- END MODEL STRUCTURE DUMP -----\n");
    }

    private String formatCollection(Collection<?> collection) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        for (Object item : collection) {
            if (!first) {
                sb.append(", ");
            }

            if (item instanceof beast.base.core.BEASTInterface) {
                beast.base.core.BEASTInterface beastItem = (beast.base.core.BEASTInterface) item;
                sb.append(beastItem.getID())
                        .append(" (")
                        .append(item.getClass().getSimpleName())
                        .append(")");
            } else {
                sb.append(item.toString());
            }

            first = false;
        }

        sb.append("]");
        return sb.toString();
    }

    private void checkForIssues(Map<String, Object> objects) {
        // Check for missing connections in TreeLikelihood
        for (Object obj : objects.values()) {
            if (obj instanceof beast.base.evolution.likelihood.TreeLikelihood) {
                beast.base.evolution.likelihood.TreeLikelihood likelihood =
                        (beast.base.evolution.likelihood.TreeLikelihood) obj;

                // Check data input
                try {
                    Object data = likelihood.getInput("data").get();
                    if (data == null) {
                        System.out.println("WARNING: TreeLikelihood has null data input");
                    } else if (!(data instanceof beast.base.evolution.alignment.Alignment)) {
                        System.out.println("WARNING: TreeLikelihood has non-Alignment data input: "
                                + data.getClass().getName());
                    }
                } catch (Exception e) {
                    System.out.println("WARNING: Unable to check TreeLikelihood data input: " + e.getMessage());
                }

                // Check tree input
                try {
                    Object tree = likelihood.getInput("tree").get();
                    if (tree == null) {
                        System.out.println("WARNING: TreeLikelihood has null tree input");
                    }
                } catch (Exception e) {
                    System.out.println("WARNING: Unable to check TreeLikelihood tree input: " + e.getMessage());
                }

                // Check siteModel input
                try {
                    Object siteModel = likelihood.getInput("siteModel").get();
                    if (siteModel == null) {
                        System.out.println("WARNING: TreeLikelihood has null siteModel input");
                    }
                } catch (Exception e) {
                    System.out.println("WARNING: Unable to check TreeLikelihood siteModel input: " + e.getMessage());
                }
            }
        }

        // Check for DummyBEASTObjects being used where real objects are expected
        for (Object obj : objects.values()) {
            if (obj != null && obj.getClass().getSimpleName().contains("Dummy")) {
                System.out.println("WARNING: Using stub/dummy object: " +
                        (obj instanceof beast.base.core.BEASTInterface ?
                                ((beast.base.core.BEASTInterface)obj).getID() : obj));
            }
        }
    }

    /**
     * Generate Beast2Lang syntax from a Beast2Model
     */
    private String generateBeast2Lang(Beast2Model model) {
        StringBuilder sb = new StringBuilder();

        // Generate Beast2Lang syntax for each statement
        model.getStatements().forEach(statement -> {
            sb.append(statement.toString()).append("\n");
        });

        return sb.toString();
    }

    /**
     * Generate XML from a BEAST2 object
     */
    private String generateXML(Object beastObject) throws Exception {
        if (beastObject == null) {
            throw new IllegalArgumentException("Cannot generate XML from null object");
        }

        try {
            // Use reflection to invoke BEAST2's XMLProducer
            XMLProducer xmlProducer = new XMLProducer();

            // Invoke toXML method
            String objectXml = xmlProducer.toXML(beastObject);

            // Add the object XML and closing tag
            if (objectXml != null) {
                return objectXml;
            } else {
                throw new RuntimeException("Failed to generate XML");
            }
        } catch (Exception e) {
            logger.severe("Error generating XML: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Write output to a file or stdout
     */
    private void writeOutput(File outputFile, String content) throws IOException {
        if (content == null) {
            throw new IOException("Cannot write null content");
        }

        if (outputFile != null) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
                writer.write(content);
            }
            System.out.println("Output written to " + outputFile.getPath());
        } else {
            // Write to stdout
            System.out.println(content);
        }
    }

    @Override
    public Integer call() {
        // Show help by default
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Beast2Lang()).execute(args);
        System.exit(exitCode);
    }
}
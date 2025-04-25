package org.beast2.modelLanguage;

import beast.base.parser.XMLProducer;
import org.beast2.modelLanguage.builder.Beast2ModelBuilderReflection;
import org.beast2.modelLanguage.builder.Beast2LangParserWithPhyloSpec;
import org.beast2.modelLanguage.builder.Beast2LangParser;
import org.beast2.modelLanguage.builder.Beast2LangParserImpl;
import org.beast2.modelLanguage.converter.Beast2ToPhyloSpecConverter;
import org.beast2.modelLanguage.converter.PhyloSpecToBeast2Converter;
import org.beast2.modelLanguage.model.Beast2Model;
import org.beast2.modelLanguage.model.Beast2Analysis;
import org.beast2.modelLanguage.builder.Beast2AnalysisBuilder;
import beast.base.inference.MCMC;


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
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application class for Beast2Lang
 */
@Command(name = "beast2lang",
        mixinStandardHelpOptions = true,
        version = "beast2lang 0.1.0",
        description = "Provides utilities for working with Beast2 model definition language")
public class Beast2Lang implements Callable<Integer> {

    private static final Logger logger = Logger.getLogger(Beast2Lang.class.getName());

    // Flag to track BEAST2 initialization status
    private static boolean beast2Initialized = false;

    /**
     * Initialize BEAST2 classes and environment
     */
    private static void initializeBEAST2() {
        if (beast2Initialized) {
            return;
        }

        try {
            // Set program name for BEAST
            Class<?> programStatusClass = Class.forName("beast.base.core.ProgramStatus");
            java.lang.reflect.Field nameField = programStatusClass.getField("name");
            nameField.set(null, "Beast2Lang");

            // Set BEAST2 directory to ensure resources are loaded properly
            try {
                System.out.println("Looking for BEAST2 installation directory...");

                // Try common environment variables
                String beastHome = System.getenv("BEAST_HOME");
                if (beastHome == null || beastHome.isEmpty()) {
                    beastHome = System.getenv("BEAST2_HOME");
                }

                // If found, set it as a system property
                if (beastHome != null && !beastHome.isEmpty()) {
                    System.out.println("Found BEAST installation at: " + beastHome);
                    System.setProperty("beast.dir", beastHome);
                    System.setProperty("beast.dir.site", beastHome + "/site");
                    System.setProperty("beast.dir.user", beastHome + "/site");
                }
            } catch (Exception e) {
                System.out.println("Warning: Could not set BEAST2 directory: " + e.getMessage());
            }

            // Initialize BEASTClassLoader and its service loading mechanism
            try {
                // Get the BEASTClassLoader class
                Class<?> loaderClass = Class.forName("beast.base.core.BEASTClassLoader");

                // First try to initialize the registry explicitly
                try {
                    Method initRegistryMethod = loaderClass.getMethod("initServices");
                    initRegistryMethod.invoke(null);
                    logger.info("Explicitly initialized BEAST service registry");
                } catch (NoSuchMethodException e) {
                    // If that method doesn't exist, try to load services directly
                    logger.info("No initServices method found, trying to initialize services indirectly");
                }

                // Pre-load and register key data types
                Class<?> dataTypeClass = Class.forName("beast.base.evolution.datatype.DataType");

                // Try to access the registry to see if it's working
                try {
                    Method loadServiceMethod = loaderClass.getMethod("loadService", Class.class);
                    Set<?> services = (Set<?>) loadServiceMethod.invoke(null, dataTypeClass);

                    if (services != null) {
                        logger.info("DataType service registry working, found " + services.size() + " services");
                    } else {
                        logger.warning("DataType service registry returned null");

                        // Try to manually register key data types
                        try {
                            Method registerClassMethod = loaderClass.getMethod("registerClass", Class.class);
                            registerClassMethod.invoke(null, Class.forName("beast.base.evolution.datatype.Nucleotide"));
                            registerClassMethod.invoke(null, Class.forName("beast.base.evolution.datatype.Amino"));
                            registerClassMethod.invoke(null, Class.forName("beast.base.evolution.datatype.StandardData"));
                            logger.info("Manually registered key data types");
                        } catch (Exception ex) {
                            logger.warning("Failed to manually register data types: " + ex.getMessage());
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Could not access service registry: " + e.getMessage());
                }
            } catch (Exception e) {
                logger.warning("Could not initialize BEAST service loader: " + e.getMessage());
            }

            // Initialize key BEAST2 classes that might need special handling
            try {
                // Force load some key classes to ensure they're properly initialized
                Class.forName("beast.base.evolution.datatype.DataType");
                Class.forName("beast.base.evolution.datatype.StandardData");
                Class.forName("beast.base.evolution.datatype.UserDataType");
            } catch (Exception e) {
                logger.warning("Could not preload some BEAST2 classes: " + e.getMessage());
            }

            // Load BEAST packages
            try {
                Class<?> packageManagerClass = Class.forName("beast.pkgmgmt.PackageManager");
                System.out.println("Loading package     ");
                java.lang.reflect.Method loadExternalJarsMethod =
                        packageManagerClass.getMethod("loadExternalJars");
                loadExternalJarsMethod.invoke(null);
                logger.info("BEAST2 packages initialized successfully");
            } catch (Exception e) {
                // Log but continue - core functionality should still work
                logger.warning("Could not load some external packages: " + e.getMessage());
            }

            // Log success
            logger.info("BEAST2 environment initialized");
            beast2Initialized = true;
        } catch (Exception e) {
            logger.severe("Error initializing BEAST2: " + e.getMessage());
            e.printStackTrace();
        }

        fixDataTypeRegistration();
    }

    private static void fixDataTypeRegistration() {
        try {
            // Get the BEASTClassLoader class
            Class<?> beastClassLoaderClass = Class.forName("beast.pkgmgmt.BEASTClassLoader");

            // Access its services field (the map that stores services)
            java.lang.reflect.Field servicesField = beastClassLoaderClass.getDeclaredField("services");
            servicesField.setAccessible(true);
            Map<String, Set<String>> services = (Map<String, Set<String>>) servicesField.get(null);

            // Check if services map exists
            if (services == null) {
                services = new HashMap<>();
                servicesField.set(null, services);
                logger.info("Created services map in BEASTClassLoader");
            }

            // Check if DataType entry exists
            String dataTypeClass = "beast.base.evolution.datatype.DataType";
            if (!services.containsKey(dataTypeClass)) {
                services.put(dataTypeClass, new HashSet<>());
                logger.info("Created DataType entry in services map");
            }

            // Add data type class names as strings
            Set<String> dataTypes = services.get(dataTypeClass);
            dataTypes.add("beast.base.evolution.datatype.Nucleotide");
            dataTypes.add("beast.base.evolution.datatype.StandardData");
            dataTypes.add("beast.base.evolution.datatype.UserDataType");
            logger.info("Added data type class names to services");

            // Now manually load the actual data type classes
            try {
                Class.forName("beast.base.evolution.datatype.Nucleotide");
                Class.forName("beast.base.evolution.datatype.StandardData");
                Class.forName("beast.base.evolution.datatype.UserDataType");
                logger.info("Loaded data type classes");
            } catch (Exception e) {
                logger.warning("Could not load data type classes: " + e.getMessage());
            }

        } catch (Exception e) {
            logger.warning("Could not fix data type registration: " + e.getMessage());
            e.printStackTrace();
        }
    }

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

            // Initialize BEAST2 environment
            initializeBEAST2();

            // First convert the model to BEAST2 objects
            Beast2ModelBuilderReflection reflectionBuilder = new Beast2ModelBuilderReflection();

            // Use appropriate parser based on PhyloSpec flag
            Beast2LangParser parser = usePhyloSpec
                    ? new Beast2LangParserWithPhyloSpec()
                    : new Beast2LangParserImpl();

            try (FileInputStream fis = new FileInputStream(inputFile)) {
                // Parse the model with the selected parser
                Beast2Model model = usePhyloSpec
                        ? parser.parseFromStream(fis)
                        : reflectionBuilder.buildFromStream(fis);

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
                Beast2AnalysisBuilder analysisBuilder = new Beast2AnalysisBuilder(reflectionBuilder);
                MCMC mcmc = analysisBuilder.buildRun(analysis);

                // Before running, dump the model structure for debugging
                if (debug) {
                    System.out.println("\nDumping model structure before running...");
                    dumpModelStructure(reflectionBuilder.getAllObjects());
                }

                // Run the MCMC
                System.out.println("Starting MCMC run...");

                // Execute the MCMC
                mcmc.run();

                String xml = generateXML(mcmc);
                // Generate XML
                writeOutput(outputFile, xml);

                System.out.println("MCMC run completed successfully.");
                return 0;
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

    @Command(name = "convert", description = "Convert between Beast2Lang and other formats")
    public Integer convert(
            @Option(names = {"--from"}, description = "Source format: beast2, phylospec, lphy", defaultValue = "beast2") String fromFormat,
            @Option(names = {"--to"}, description = "Target format: beast2, phylospec, lphy, xml", defaultValue = "phylospec") String toFormat,
            @Option(names = {"-o", "--output"}, description = "Output file") File outputFile,
            @Option(names = {"--debug"}, description = "Enable debug logging", defaultValue = "false") boolean debug,
            @Option(names="--chainLength", defaultValue="10000000", description="Default MCMC chain length") long chainLength,
            @Option(names="--logEvery", defaultValue="1000", description="Default logging interval") int logEvery,
            @Option(names="--traceFileName", defaultValue="trace.log", description="Default trace log file name") String traceFileName,
            @Parameters(paramLabel = "FILE", description = "Input file to convert") File inputFile) {

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
            Beast2ModelBuilderReflection reflectionBuilder = new Beast2ModelBuilderReflection();

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
                // Initialize BEAST2 environment
                initializeBEAST2();

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
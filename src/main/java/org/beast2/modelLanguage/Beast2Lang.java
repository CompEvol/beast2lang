package org.beast2.modelLanguage;

import beast.base.core.BEASTInterface;
import beast.base.core.Log;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.MCMC;
import beast.base.inference.State;
import beast.base.parser.XMLParser;
import beast.pkgmgmt.PackageManager;
import org.beast2.modelLanguage.beast.Beast2AnalysisBuilder;
import org.beast2.modelLanguage.beast.Beast2ModelBuilder;
import org.beast2.modelLanguage.builder.Beast2LangParser;
import org.beast2.modelLanguage.builder.Beast2LangParserImpl;
import org.beast2.modelLanguage.converter.*;
import org.beast2.modelLanguage.model.Beast2Analysis;
import org.beast2.modelLanguage.model.Beast2Model;
import org.beast2.modelLanguage.phylospec.Beast2LangParserWithPhyloSpec;
import org.beast2.modelLanguage.schema.BEAST2ModelLibraryGenerator;
import org.beast2.modelLanguage.schema.validation.ValidationResult;
import org.json.JSONArray;
import org.json.JSONObject;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.Callable;

import static org.beast2.modelLanguage.Beast2LangUtils.*;

/**
 * Main CLI application for Beast2Lang.
 */
@Command(name = "beast2lang",
        mixinStandardHelpOptions = true,
        version = "beast2lang 0.2.0",
        description = "BEAST model definition language tools.",
        subcommands = {
                Beast2Lang.RunCmd.class,
                Beast2Lang.ValidateCmd.class,
                Beast2Lang.ConvertCmd.class,
                Beast2Lang.DecompileCmd.class,
                Beast2Lang.LPhyCmd.class,
                Beast2Lang.SchemaCmd.class,
                HelpCommand.class
        })
public class Beast2Lang implements Runnable {

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Beast2Lang()).execute(args);
        System.exit(exitCode);
    }

    // ──────────────────────────────────────────────
    // Subcommand: run
    // ──────────────────────────────────────────────

    @Command(name = "run",
            description = "Run a Beast2 model after conversion to XML.",
            mixinStandardHelpOptions = true)
    static class RunCmd implements Callable<Integer> {

        @Parameters(paramLabel = "FILE", description = "Input Beast2Lang file.")
        File inputFile;

        @Option(names = {"-o", "--output"}, defaultValue = "model.xml",
                description = "Output Beast2 XML file.")
        File outputFile;

        @Option(names = {"--chainLength"}, defaultValue = "10000000",
                description = "MCMC chain length.")
        long chainLength;

        @Option(names = {"--logEvery"}, defaultValue = "1000",
                description = "Logging interval.")
        int logEvery;

        @Option(names = {"--traceFileName"}, defaultValue = "trace.log",
                description = "Trace log file name.")
        String traceFileName;

        @Option(names = {"--treeFileName"}, defaultValue = "tree.trees",
                description = "Tree log file name.")
        String treeFileName;

        @Option(names = {"--debug"},
                description = "Enable debug logging.")
        boolean debug;

        @Option(names = {"--seed"},
                description = "Random seed for MCMC run.")
        Long seed;

        @Option(names = {"--threads"}, defaultValue = "1",
                description = "Number of threads.")
        int threads;

        @Option(names = {"--resume"},
                description = "Resume from previous run.")
        boolean resume;

        @Option(names = {"--phylospec"},
                description = "Use PhyloSpec syntax.")
        boolean usePhyloSpec;

        @Override
        public Integer call() {
            if (debug) Log.setLevel(Log.Level.debug);

            try {
                System.out.println("Running Beast2 model from file: " + inputFile.getPath());

                Beast2ModelBuilder modelBuilder = new Beast2ModelBuilder();
                Beast2LangParser parser = usePhyloSpec
                        ? new Beast2LangParserWithPhyloSpec()
                        : new Beast2LangParserImpl();

                try (FileInputStream fis = new FileInputStream(inputFile)) {
                    Beast2Model model = usePhyloSpec
                            ? parser.parseFromStream(fis)
                            : modelBuilder.buildFromStream(fis);

                    Beast2Analysis analysis = new Beast2Analysis(
                            model, chainLength, logEvery, traceFileName);
                    analysis.setTreeLogFileName(treeFileName);
                    if (seed != null) analysis.setSeed(seed);
                    analysis.setThreadCount(threads);

                    Beast2AnalysisBuilder analysisBuilder = new Beast2AnalysisBuilder(modelBuilder);
                    MCMC mcmc = analysisBuilder.buildRun(analysis);

                    if (debug) {
                        System.out.println("\nDumping model structure before running...");
                        dumpModelStructure(modelBuilder.getAllObjects());
                    }

                    System.out.println("Writing XML...");
                    String xml = generateXML(mcmc);
                    writeOutput(outputFile, xml);
                    System.out.println("XML written to " + outputFile.getPath());

                    System.out.println("Loading the model from XML...");
                    XMLParser xmlParser = new XMLParser();
                    Object loaded = xmlParser.parseFile(outputFile);

                    if (loaded instanceof MCMC loadedMCMC) {
                        System.out.println("Starting MCMC run from loaded XML...");
                        loadedMCMC.run();
                        System.out.println("MCMC run completed successfully.");
                    } else {
                        throw new RuntimeException("Loaded object is not an MCMC instance: " +
                                (loaded != null ? loaded.getClass().getName() : "null"));
                    }
                }
                return 0;
            } catch (Exception e) {
                System.err.println("Error running BEAST2 model: " + e.getMessage());
                if (debug) e.printStackTrace();
                return 1;
            }
        }
    }

    // ──────────────────────────────────────────────
    // Subcommand: validate
    // ──────────────────────────────────────────────

    @Command(name = "validate",
            description = "Validate a Beast2Lang file.",
            mixinStandardHelpOptions = true)
    static class ValidateCmd implements Callable<Integer> {

        @Parameters(paramLabel = "FILE", description = "Beast2Lang file to validate.")
        File inputFile;

        @Option(names = {"--phylospec"},
                description = "Use PhyloSpec syntax.")
        boolean usePhyloSpec;

        @Override
        public Integer call() {
            try {
                System.out.println("Validating " + inputFile.getPath() + "...");

                Beast2LangParser parser = usePhyloSpec
                        ? new Beast2LangParserWithPhyloSpec()
                        : new Beast2LangParserImpl();

                try (FileInputStream fis = new FileInputStream(inputFile)) {
                    Beast2Model model = parser.parseFromStream(fis);
                    System.out.println("Model is valid. Contains " + model.getStatements().size() + " statements.");
                }
                return 0;
            } catch (Exception e) {
                System.err.println("Error validating file: " + e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }
    }

    // ──────────────────────────────────────────────
    // Subcommand: convert
    // ──────────────────────────────────────────────

    @Command(name = "convert",
            description = "Convert between Beast2Lang and other formats.",
            mixinStandardHelpOptions = true)
    static class ConvertCmd implements Callable<Integer> {

        @Parameters(paramLabel = "FILE", description = "Input file.")
        File inputFile;

        @Option(names = {"--from"}, defaultValue = "beast2",
                description = "Source format: beast2, phylospec, lphy, xml.")
        String fromFormat;

        @Option(names = {"--to"}, defaultValue = "phylospec",
                description = "Target format: beast2, phylospec, lphy, xml.")
        String toFormat;

        @Option(names = {"-o", "--output"},
                description = "Output file.")
        File outputFile;

        @Option(names = {"--chainLength"}, defaultValue = "10000000",
                description = "MCMC chain length (for XML output).")
        long chainLength;

        @Option(names = {"--logEvery"}, defaultValue = "1000",
                description = "Logging interval (for XML output).")
        int logEvery;

        @Option(names = {"--traceFileName"}, defaultValue = "trace.log",
                description = "Trace log file name (for XML output).")
        String traceFileName;

        @Option(names = {"--debug"},
                description = "Enable debug logging.")
        boolean debug;

        @Override
        public Integer call() {
            if (debug) Log.setLevel(Log.Level.debug);

            try {
                Log.info("Converting from " + fromFormat + " to " + toFormat + "...");

                Beast2ToPhyloSpecConverter toPhyloSpecConverter = new Beast2ToPhyloSpecConverter();
                PhyloSpecToBeast2Converter toBeast2Converter = new PhyloSpecToBeast2Converter();
                Beast2ModelBuilder reflectionBuilder = new Beast2ModelBuilder();
                Beast2ToLPHYConverter toLPHYConverter = new Beast2ToLPHYConverter();

                if ("beast2".equals(fromFormat) && "phylospec".equals(toFormat)) {
                    try (FileInputStream fis = new FileInputStream(inputFile)) {
                        Beast2Model model = reflectionBuilder.buildFromStream(fis);
                        JSONObject phyloSpec = toPhyloSpecConverter.convert(model);
                        writeOutput(outputFile, phyloSpec.toString(2));
                    }
                } else if ("phylospec".equals(fromFormat) && "beast2".equals(toFormat)) {
                    String content = new String(java.nio.file.Files.readAllBytes(inputFile.toPath()));
                    JSONObject phyloSpec = new JSONObject(content);
                    Beast2Model model = toBeast2Converter.convert(phyloSpec);
                    String beast2Lang = generateBeast2Lang(model);
                    writeOutput(outputFile, beast2Lang);
                } else if ("beast2".equals(fromFormat) && "xml".equals(toFormat)) {
                    try (FileInputStream fis = new FileInputStream(inputFile)) {
                        Beast2Model model = reflectionBuilder.buildFromStream(fis);
                        Beast2Analysis analysis = new Beast2Analysis(
                                model, chainLength, logEvery, traceFileName);
                        Beast2AnalysisBuilder analysisBuilder = new Beast2AnalysisBuilder(reflectionBuilder);
                        MCMC rootRun = analysisBuilder.buildRun(analysis);

                        if (debug) {
                            Log.info("\nDumping model structure before XML generation...");
                            dumpModelStructure(reflectionBuilder.getAllObjects());
                        }

                        String xml = generateXML(rootRun);
                        writeOutput(outputFile, xml);
                    }
                } else if ("xml".equals(fromFormat) && "beast2".equals(toFormat)) {
                    Log.info("Converting BEAST2 XML to Beast2Lang...");
                    XMLParser parser = new XMLParser();
                    BEASTInterface beast = parser.parseFile(inputFile);

                    if (!(beast instanceof MCMC mcmc)) {
                        throw new IllegalArgumentException("Input XML does not contain an MCMC analysis");
                    }

                    CompoundDistribution posterior = (CompoundDistribution) mcmc.posteriorInput.get();
                    State state = mcmc.startStateInput.get();

                    Beast2ToBeast2LangConverter converter = new Beast2ToBeast2LangConverter();
                    Beast2Model model = converter.convertToBeast2Model(posterior, state, mcmc);

                    Beast2ModelWriter writer = new Beast2ModelWriter();
                    String scriptContent = writer.writeModel(model);

                    if (outputFile == null) {
                        String baseName = inputFile.getName();
                        if (baseName.endsWith(".xml")) {
                            baseName = baseName.substring(0, baseName.length() - 4);
                        }
                        outputFile = new File(baseName + ".b2l");
                    }

                    writeOutput(outputFile, scriptContent);
                    Log.info("BEAST2 XML file successfully converted to Beast2Lang script: " + outputFile);
                } else if ("beast2".equals(fromFormat) && "lphy".equals(toFormat)) {
                    toLPHYConverter.convertToFile(inputFile.getPath(),
                            outputFile != null ? outputFile.getPath() : null);
                    Log.info("Beast2Lang file successfully converted to LPHY: " + outputFile);
                } else if ("lphy".equals(fromFormat)) {
                    Log.err("LinguaPhylo conversion not yet implemented");
                    return 1;
                } else {
                    Log.err("Unsupported conversion: " + fromFormat + " to " + toFormat);
                    return 1;
                }

                return 0;
            } catch (Exception e) {
                Log.err("Error converting file: " + e.getMessage());
                if (debug) e.printStackTrace();
                return 1;
            }
        }
    }

    // ──────────────────────────────────────────────
    // Subcommand: decompile
    // ──────────────────────────────────────────────

    @Command(name = "decompile",
            description = "Decompile BEAST2 XML to Beast2Lang.",
            mixinStandardHelpOptions = true)
    static class DecompileCmd implements Callable<Integer> {

        @Parameters(paramLabel = "FILE", description = "Input BEAST2 XML file.")
        File inputFile;

        @Option(names = {"-o", "--output"},
                description = "Output Beast2Lang file (derived from input if omitted).")
        File outputFile;

        @Option(names = {"--debug"},
                description = "Enable debug logging.")
        boolean debug;

        @Override
        public Integer call() {
            if (debug) Log.setLevel(Log.Level.debug);

            try {
                Log.info("Decompiling BEAST2 XML file: " + inputFile.getPath());

                if (outputFile == null) {
                    String baseName = inputFile.getName();
                    if (baseName.endsWith(".xml")) {
                        baseName = baseName.substring(0, baseName.length() - 4);
                    }
                    outputFile = new File(baseName + ".b2l");
                }

                PackageManager.loadExternalJars();

                XMLParser parser = new XMLParser();
                BEASTInterface beast = parser.parseFile(inputFile);

                if (!(beast instanceof MCMC mcmc)) {
                    throw new IllegalArgumentException("Input XML does not contain an MCMC analysis");
                }

                CompoundDistribution posterior = (CompoundDistribution) mcmc.posteriorInput.get();
                State state = mcmc.startStateInput.get();

                Beast2ToBeast2LangConverter converter = new Beast2ToBeast2LangConverter();
                Beast2Model model = converter.convertToBeast2Model(posterior, state, mcmc);

                String required = extractRequiredPackages(inputFile);
                if (required != null) {
                    addRequiresFromString(model, required);
                }

                Beast2ModelWriter writer = new Beast2ModelWriter();
                String scriptContent = writer.writeModel(model);

                writeOutput(outputFile, scriptContent);
                System.out.println("BEAST2 XML file successfully decompiled to Beast2Lang script: " + outputFile);
                return 0;

            } catch (Exception e) {
                Log.err("Error decompiling BEAST2 XML: " + e.getMessage());
                if (debug) e.printStackTrace();
                return 1;
            }
        }
    }

    // ──────────────────────────────────────────────
    // Subcommand: lphy
    // ──────────────────────────────────────────────

    @Command(name = "lphy",
            description = "Convert Beast2Lang to LinguaPhylo (LPHY).",
            mixinStandardHelpOptions = true)
    static class LPhyCmd implements Callable<Integer> {

        @Parameters(paramLabel = "FILE", description = "Input Beast2Lang file.")
        File inputFile;

        @Option(names = {"-o", "--output"},
                description = "Output LPhy file (derived from input if omitted).")
        File outputFile;

        @Option(names = {"--debug"},
                description = "Enable debug logging.")
        boolean debug;

        @Override
        public Integer call() {
            if (debug) Log.setLevel(Log.Level.debug);

            try {
                Log.info("Converting Beast2Lang to LPHY: " + inputFile.getPath());

                if (outputFile == null) {
                    String baseName = inputFile.getName();
                    if (baseName.endsWith(".b2l")) {
                        baseName = baseName.substring(0, baseName.length() - 4);
                    }
                    outputFile = new File(baseName + ".lphy");
                }

                Beast2ToLPHYConverter converter = new Beast2ToLPHYConverter();
                converter.convertToFile(inputFile.getPath(), outputFile.getPath());

                Log.info("Beast2Lang file successfully converted to LPHY: " + outputFile);
                return 0;

            } catch (Exception e) {
                Log.err("Error converting Beast2Lang to LPHY: " + e.getMessage());
                if (debug) e.printStackTrace();
                return 1;
            }
        }
    }

    // ──────────────────────────────────────────────
    // Subcommand: schema
    // ──────────────────────────────────────────────

    @Command(name = "schema",
            description = "Generate BEAST2 engine library schema.",
            mixinStandardHelpOptions = true)
    static class SchemaCmd implements Callable<Integer> {

        @Option(names = {"-o", "--output"}, defaultValue = "beast2-model-library.json",
                description = "Output JSON file.")
        File outputFile;

        @Option(names = {"--packages"},
                description = "Additional packages to include (comma-separated).")
        String packages;

        @Option(names = {"--pretty"}, defaultValue = "true",
                description = "Pretty print JSON output.")
        boolean prettyPrint;

        @Option(names = {"--test-closure"},
                description = "Test type closure after generation.")
        boolean testClosure;

        @Option(names = {"--debug"},
                description = "Enable debug logging.")
        boolean debug;

        @Override
        public Integer call() {
            if (debug) Log.setLevel(Log.Level.debug);

            try {
                Log.info("Generating BEAST2 model library schema...");

                PackageManager.loadExternalJars();

                BEAST2ModelLibraryGenerator generator = new BEAST2ModelLibraryGenerator();
                String schema = generator.generateModelLibrary();

                writeOutput(outputFile, schema, prettyPrint);

                JSONObject schemaObj = new JSONObject(schema);
                JSONObject modelLibrary = schemaObj.getJSONObject("modelLibrary");
                JSONArray types = modelLibrary.getJSONArray("types");
                JSONArray generators = modelLibrary.getJSONArray("generators");

                Log.info("\nSchema generation complete!");
                Log.info("Engine: " + modelLibrary.getString("engine") + " " + modelLibrary.getString("engineVersion"));
                Log.info("Total types: " + types.length());
                Log.info("Total generators: " + generators.length());

                int distributionCount = 0;
                int functionCount = 0;
                for (int i = 0; i < generators.length(); i++) {
                    JSONObject gen = generators.getJSONObject(i);
                    if (gen.getString("generatorType").equals("distribution")) {
                        distributionCount++;
                    } else {
                        functionCount++;
                    }
                }

                Log.info("\nGenerators:");
                Log.info("  Distributions: " + distributionCount);
                Log.info("  Functions: " + functionCount);

                int abstractCount = 0, interfaceCount = 0, concreteCount = 0, primitiveCount = 0;
                for (int i = 0; i < types.length(); i++) {
                    JSONObject type = types.getJSONObject(i);
                    if (type.optBoolean("primitiveAssignable", false)) primitiveCount++;
                    else if (type.optBoolean("isInterface", false)) interfaceCount++;
                    else if (type.optBoolean("isAbstract", false)) abstractCount++;
                    else concreteCount++;
                }

                Log.info("\nType categories:");
                Log.info("  Primitives/Assignable: " + primitiveCount);
                Log.info("  Interfaces: " + interfaceCount);
                Log.info("  Abstract classes: " + abstractCount);
                Log.info("  Concrete classes: " + concreteCount);

                Log.info("\nSchema written to: " + outputFile.getPath());

                if (testClosure) {
                    Log.info("\nRunning closure test...");
                    ValidationResult result = generator.validateSchema(schema);
                    Log.info(result.generateReport());
                }

                return 0;

            } catch (Exception e) {
                Log.err("Error generating schema: " + e.getMessage());
                if (debug) e.printStackTrace();
                return 1;
            }
        }
    }
}

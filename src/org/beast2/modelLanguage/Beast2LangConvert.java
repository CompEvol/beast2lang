package org.beast2.modelLanguage;

import beast.base.core.BEASTInterface;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.MCMC;
import beast.base.inference.State;
import beast.base.parser.XMLParser;
import beastfx.app.util.OutFile;
import org.beast2.modelLanguage.beast.Beast2AnalysisBuilder;
import org.beast2.modelLanguage.beast.Beast2ModelBuilder;
import org.beast2.modelLanguage.converter.*;
import org.beast2.modelLanguage.model.Beast2Analysis;
import org.beast2.modelLanguage.model.Beast2Model;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;

import static org.beast2.modelLanguage.Beast2LangUtils.*;

//TODO only work in command line

@Description("Convert between Beast2Lang and other formats")
public class Beast2LangConvert extends beast.base.inference.Runnable {
    public static final String version = "v0.0.1";
    public static final String FILE_INIT = "[[*.b2l]]";

    public Input<File> inputFileInput = new Input<>("file", "Input Beast2Lang file",
            new File(FILE_INIT));
    public Input<String> fromInput = new Input<>("from",
            "Source format: beast2, phylospec, lphy, xml", "org/beast2");
    public Input<String> toInput = new Input<>("to",
            "Target format: beast2, phylospec, lphy, xml", "phylospec");
    public Input<OutFile> outputFileInput = new Input<>("output", "Output file",
            new OutFile(""));
    public Input<Long> chainLengthInput = new Input<>("chainLength", "MCMC chain length", 10000000L);
    public Input<Integer> logEveryInput = new Input<>("logEvery", "Logging interval", 1000);
    public Input<String> traceFileNameInput = new Input<>("traceFileName",
            "Trace log file name", "trace.log");
    public Input<Boolean> debugInput = new Input<>("debug",
            "Enable debug logging", false);
//    public Input<Boolean> usePhyloSpecInput = new Input<>("usePhyloSpec",
//            "Use PhyloSpec syntax", false);

    private File inputFile;
    private String fromFormat,toFormat;
    private File outputFile;
    private Long chainLength;
    private Integer logEvery;
    private String traceFileName;
    private Boolean debug;
//    private Boolean usePhyloSpec;

    @Override
    public void initAndValidate() {
        inputFile = inputFileInput.get();
        if (inputFile.getName().contains(FILE_INIT))
            throw new IllegalArgumentException("Input file is required ! " + inputFile);
        fromFormat = fromInput.get();
        toFormat = toInput.get();
        outputFile = outputFileInput.get();
        chainLength = chainLengthInput.get();
        logEvery = logEveryInput.get();
        traceFileName = traceFileNameInput.get();
        debug = debugInput.get();
//        usePhyloSpec = usePhyloSpecInput.get();
    }


    @Override
    public void run() {
        // Set debug level if requested
        if (debug) {
//            Logger rootLogger = Logger.getLogger("");
//            rootLogger.setLevel(Level.FINE);
//            for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
//                handler.setLevel(Level.FINE);
//            }
            Log.setLevel(Log.Level.debug);
        }

        try {
            Log.info("Converting from " + fromFormat + " to " + toFormat + "...");

            // Initialize converters
            Beast2ToPhyloSpecConverter toPhyloSpecConverter = new Beast2ToPhyloSpecConverter();
            PhyloSpecToBeast2Converter toBeast2Converter = new PhyloSpecToBeast2Converter();
            Beast2ModelBuilder reflectionBuilder = new Beast2ModelBuilder();
            Beast2ToLPHYConverter toLPHYConverter = new Beast2ToLPHYConverter();

            // Perform conversion
            if ("org/beast2".equals(fromFormat) && "phylospec".equals(toFormat)) {
                try (FileInputStream fis = new FileInputStream(inputFile)) {
                    // Convert Beast2Lang to PhyloSpec
                    Beast2Model model = reflectionBuilder.buildFromStream(fis);
                    JSONObject phyloSpec = toPhyloSpecConverter.convert(model);

                    // Output result
                    writeOutput(outputFile, phyloSpec.toString(2));
                }
            } else if ("phylospec".equals(fromFormat) && "org/beast2".equals(toFormat)) {
                // Read PhyloSpec JSON
                String content = new String(Files.readAllBytes(inputFile.toPath()));
                JSONObject phyloSpec = new JSONObject(content);

                // Convert PhyloSpec to Beast2Lang
                Beast2Model model = toBeast2Converter.convert(phyloSpec);
                String beast2Lang = generateBeast2Lang(model);

                // Output result
                writeOutput(outputFile, beast2Lang);
            } else if ("org/beast2".equals(fromFormat) && "xml".equals(toFormat)) {
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
                        Log.info("\nDumping model structure before XML generation...");
                        dumpModelStructure(reflectionBuilder.getAllObjects());
                    } catch (Exception e) {
                        Log.err("Error dumping model structure: " + e.getMessage());
                    }

                    // Then continue with XML generation
                    try {
                        xml = generateXML(rootRun);
                        // Generate XML
                        writeOutput(outputFile, xml);
                    } catch (Exception e) {
                        throw new RuntimeException("XML generation failure!", e);
                    }
                }
            } else if ("xml".equals(fromFormat) && "org/beast2".equals(toFormat)) {
                // Handle XML to Beast2Lang conversion
                Log.info("Converting BEAST2 XML to Beast2Lang...");

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
                Log.info("BEAST2 XML file successfully converted to Beast2Lang script: " + outputFile);
            } else if ("org/beast2".equals(fromFormat) && "lphy".equals(toFormat)) {
                // Convert Beast2Lang to LPHY
                try {
                    toLPHYConverter.convertToFile(inputFile.getPath(), outputFile.getPath());
                    Log.info("Beast2Lang file successfully converted to LPHY: " + outputFile);
                } catch (IOException e) {
                    throw new RuntimeException("Error converting to LPHY", e);
                }
            } else if ("lphy".equals(fromFormat)) {
                // Handle LinguaPhylo format conversion if needed
                Log.err("LinguaPhylo conversion not yet implemented");
            } else {
                Log.err("Unsupported conversion: " + fromFormat + " to " + toFormat);
            }
        } catch (Exception e) {
            Log.err("Error converting file: " + e.getMessage());

            if (debug) {
                e.printStackTrace();
            } else {
                // Print a more useful stack trace
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                Log.err("Detailed error: " + sw.toString());
            }

        }
    }

    public static void main(String[] args) throws Exception {
        String title = "Convert " + version;

        new beastfx.app.tools.Application(new Beast2LangConvert(), title, args);
    }
}
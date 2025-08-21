package org.beast2.modelLanguage;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.inference.MCMC;
import beast.base.parser.XMLParser;
import beastfx.app.util.OutFile;
import org.beast2.modelLanguage.beast.Beast2AnalysisBuilder;
import org.beast2.modelLanguage.beast.Beast2ModelBuilder;
import org.beast2.modelLanguage.builder.Beast2LangParser;
import org.beast2.modelLanguage.builder.Beast2LangParserImpl;
import org.beast2.modelLanguage.model.Beast2Analysis;
import org.beast2.modelLanguage.model.Beast2Model;
import org.beast2.modelLanguage.phylospec.Beast2LangParserWithPhyloSpec;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**TODO only work in command line
 * Main application class for Beast2Lang
 * Provides utilities for working with Beast2 model definition language
 */
@Description("Run a Beast2 model using the definition language")
public class Beast2LangRun extends beast.base.inference.Runnable {
    public static final String version = "v0.0.1";
    public static final String FILE_INIT = "[[*.b2l]]";
//    private static final Logger logger = Logger.getLogger(Beast2LangRun.class.getName());

    public Input<File> inputFileInput = new Input<>("file", "Input Beast2Lang file",
            new File("FILE_INIT"));
    public Input<OutFile> outputFileInput = new Input<>("output", "Output Beast2 XML file",
            new OutFile("model.xml"));
    public Input<Long> chainLengthInput = new Input<>("chainLength", "MCMC chain length", 10000000L);
    public Input<Integer> logEveryInput = new Input<>("logEvery", "Logging interval", 1000);
    public Input<String> traceFileNameInput = new Input<>("traceFileName",
            "Trace log file name", "trace.log");
    public Input<String> treeFileNameInput = new Input<>("treeFileName",
            "Tree log file name", "tree.trees");
    public Input<Boolean> debugInput = new Input<>("debug",
            "Enable debug logging", false);
    public Input<Long> seedInput = new Input<>("seed", "Random seed for MCMC run");
    public Input<Integer> threadsInput = new Input<>("threads", "Number of threads", 1);
    public Input<Boolean> resumeInput = new Input<>("resume",
            "Resume from previous run", false);
    public Input<Boolean> usePhyloSpecInput = new Input<>("usePhyloSpec",
            "Use PhyloSpec syntax", false);

    private File inputFile;
    private File outputFile;
    private Long chainLength;
    private Integer logEvery;
    private String traceFileName;
    private String treeFileName;

    private Boolean debug;
    private Long seed;
    private Integer threads;
    private Boolean resume;
    private Boolean usePhyloSpec;

    @Override
    public void initAndValidate() {
        inputFile = inputFileInput.get();
        if (inputFile.getName().contains("FILE_INIT"))
            throw new IllegalArgumentException("Input file is required ! " + inputFile);
        outputFile = outputFileInput.get();
        chainLength = chainLengthInput.get();
        logEvery = logEveryInput.get();
        traceFileName = traceFileNameInput.get();
        treeFileName = treeFileNameInput.get();
        debug = debugInput.get();
        seed = seedInput.get();
        threads = threadsInput.get();
        resume = resumeInput.get();
        usePhyloSpec = usePhyloSpecInput.get();
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
                    Beast2LangUtils.dumpModelStructure(modelBuilder.getAllObjects());
                }

                System.out.println("Writing XML...");

                // Generate XML
                String xml = Beast2LangUtils.generateXML(mcmc);
                // Write XML to output file
                Beast2LangUtils.writeOutput(outputFile, xml);

                System.out.println("XML written to " + outputFile.getPath());

                // Now instead of running the existing MCMC object, we'll load from the XML
                System.out.println("Loading the model from XML...");

                try {
                    // Use BEAST2's XMLParser to read the XML back in
                    XMLParser parser2 = new XMLParser();
                    Object loadedObject = parser2.parseFile(outputFile);

                    if (loadedObject instanceof MCMC) {
                        MCMC loadedMCMC = (MCMC) loadedObject;

                        // Run the MCMC loaded from XML
                        System.out.println("Starting MCMC run from loaded XML...");
                        loadedMCMC.run();

                        System.out.println("MCMC run completed successfully.");
                    } else {
                        throw new RuntimeException("Loaded object is not an MCMC instance: " +
                                (loadedObject != null ? loadedObject.getClass().getName() : "null"));
                    }
                } catch (Exception e) {
                    System.err.println("Error loading or running from XML: " + e.getMessage());
                    if (debug) {
                        e.printStackTrace();
                    }
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
                Log.err("Detailed error: " + sw.toString());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String title = "Run Beast2Lang " + version;

        new beastfx.app.tools.Application(new Beast2LangRun(), title, args);
    }
}
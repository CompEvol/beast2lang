package org.beast2.modelLanguage;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beastfx.app.util.OutFile;
import org.beast2.modelLanguage.converter.Beast2ToLPHYConverter;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

//TODO only work in command line

@Description("Convert Beast2Lang to LinguaPhylo (LPHY)")
public class Beast2LangLPhy extends beast.base.inference.Runnable {
    public static final String version = "v0.0.1";
    public static final String FILE_INIT = "[[*.b2l]]";

    public Input<File> inputFileInput = new Input<>("file", "Input Beast2Lang file",
            new File(FILE_INIT));
    public Input<OutFile> outputFileInput = new Input<>("output", "Output LPhy file",
            new OutFile(""));
    public Input<Boolean> debugInput = new Input<>("debug",
            "Enable debug logging", false);
//    public Input<Boolean> usePhyloSpecInput = new Input<>("usePhyloSpec",
//            "Use PhyloSpec syntax", false);

    private File inputFile;
    private File outputFile;
    private Boolean debug;
//    private Boolean usePhyloSpec;

    @Override
    public void initAndValidate() {
        inputFile = inputFileInput.get();
        if (inputFile.getName().contains(FILE_INIT))
            throw new IllegalArgumentException("Input file is required ! " + inputFile);
        outputFile = outputFileInput.get();
        debug = debugInput.get();
//        usePhyloSpec = usePhyloSpecInput.get();

        // If output file is not specified, derive from input
        if (outputFile == null || outputFile.getName().isEmpty()) {
            String baseName = inputFile.getName();
            if (baseName.endsWith(".b2l")) {
                baseName = baseName.substring(0, baseName.length() - 4);
            }
            outputFile = new File(baseName + ".lphy");
        }
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
            Log.info("Converting Beast2Lang to LPHY: " + inputFile.getPath());

            // Create the converter
            Beast2ToLPHYConverter converter = new Beast2ToLPHYConverter();

            // Convert the file
            converter.convertToFile(inputFile.getPath(), outputFile.getPath());

            Log.info("Beast2Lang file successfully converted to LPHY: " + outputFile);

        } catch (Exception e) {
            Log.err("Error converting Beast2Lang to LPHY: " + e.getMessage());

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

        new beastfx.app.tools.Application(new Beast2LangLPhy(), title, args);
    }
}
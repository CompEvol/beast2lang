package org.beast2.modelLanguage;

import beast.base.core.BEASTInterface;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.MCMC;
import beast.base.inference.State;
import beast.base.parser.XMLParser;
import beast.pkgmgmt.PackageManager;
import beastfx.app.util.OutFile;
import org.beast2.modelLanguage.converter.Beast2ModelWriter;
import org.beast2.modelLanguage.converter.Beast2ToBeast2LangConverter;
import org.beast2.modelLanguage.model.Beast2Model;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.beast2.modelLanguage.Beast2LangUtils.*;

//TODO only work in command line

@Description("Decompile BEAST2 XML to Beast2Lang")
public class Beast2LangDecompile extends beast.base.inference.Runnable {
    public static final String version = "v0.0.1";
    public static final String FILE_INIT = "[[*.xml]]";

    public Input<File> inputFileInput = new Input<>("file", "Input BEAST2 XML file",
            new File(FILE_INIT));
    public Input<OutFile> outputFileInput = new Input<>("output", "Output Beast2Lang file",
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
            if (baseName.endsWith(".xml")) {
                baseName = baseName.substring(0, baseName.length() - 4);
            }
            outputFile = new File(baseName + ".b2l");
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
            Log.info("Decompiling BEAST2 XML file: " + inputFile.getPath());

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

        } catch (Exception e) {
            Log.err("Error decompiling BEAST2 XML: " + e.getMessage());
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

        new beastfx.app.tools.Application(new Beast2LangDecompile(), title, args);
    }
}
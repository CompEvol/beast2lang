package org.beast2.modelLanguage;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import org.beast2.modelLanguage.builder.Beast2LangParser;
import org.beast2.modelLanguage.builder.Beast2LangParserImpl;
import org.beast2.modelLanguage.model.Beast2Model;
import org.beast2.modelLanguage.phylospec.Beast2LangParserWithPhyloSpec;

import java.io.File;
import java.io.FileInputStream;

/**TODO only work in command line
 * Main application class for Beast2Lang
 * Provides utilities for working with Beast2 model definition language
 */
@Description("Validate a Beast2 model written in the definition language")
public class Beast2LangValidate extends beast.base.inference.Runnable {
    public static final String version = "v0.0.1";
    public static final String FILE_INIT = "[[*.b2l]]";

    public Input<File> inputFileInput = new Input<>("file", "Input Beast2Lang file",
            new File(FILE_INIT));
    public Input<Boolean> usePhyloSpecInput = new Input<>("usePhyloSpec",
            "Use PhyloSpec syntax", false);

    private File inputFile;
    private Boolean usePhyloSpec;

    @Override
    public void initAndValidate() {
        inputFile = inputFileInput.get();
        if (inputFile.getName().contains(FILE_INIT))
            throw new IllegalArgumentException("Input file is required ! " + inputFile);
        usePhyloSpec = usePhyloSpecInput.get();
    }


    @Override
    public void run() {
        try {
            System.out.println("Validating " + inputFile.getPath() + "...");

            // Use appropriate parser based on PhyloSpec flag
            Beast2LangParser parser = usePhyloSpec
                    ? new Beast2LangParserWithPhyloSpec()
                    : new Beast2LangParserImpl();

            try (FileInputStream fis = new FileInputStream(inputFile)) {
                Beast2Model model = parser.parseFromStream(fis);
                Log.info("Model is valid. Contains " + model.getStatements().size() + " statements.");
            }
        } catch (Exception e) {
            Log.err("Error validating file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        String title = "Validate Beast2Lang " + version;

        new beastfx.app.tools.Application(new Beast2LangValidate(), title, args);
    }
}
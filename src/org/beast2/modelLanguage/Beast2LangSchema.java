package org.beast2.modelLanguage;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.pkgmgmt.PackageManager;
import beastfx.app.util.OutFile;
import org.beast2.modelLanguage.schema.BEAST2ModelLibraryGenerator;
import org.beast2.modelLanguage.schema.validation.ValidationResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.beast2.modelLanguage.Beast2LangUtils.writeOutput;

//TODO only work in command line

@Description("Generate BEAST2 engine library schema")
public class Beast2LangSchema extends beast.base.inference.Runnable {
    public static final String version = "v0.0.1";
    public static final String FILE_INIT = "beast2-model-library.json";

    public Input<OutFile> outputFileInput = new Input<>("output", "Output JSON file",
            new OutFile(FILE_INIT));
    public Input<String> packagesInput = new Input<>("packages",
            "Additional packages to include (comma-separated)");
    public Input<Boolean> prettyInput = new Input<>("pretty",
            "Pretty print JSON output", true);
    public Input<Boolean> testClosureInput = new Input<>("testClosure",
            "Test type closure after generation", false);
    public Input<Boolean> debugInput = new Input<>("debug",
            "Enable debug logging", false);
//    public Input<Boolean> usePhyloSpecInput = new Input<>("usePhyloSpec",
//            "Use PhyloSpec syntax", false);

    private File outputFile;
    private String packages;
    private boolean prettyPrint;
    private boolean testClosure;
    private Boolean debug;
//    private Boolean usePhyloSpec;

    @Override
    public void initAndValidate() {
        outputFile = outputFileInput.get();
        packages = packagesInput.get();
        prettyPrint = prettyInput.get();
        testClosure = testClosureInput.get();
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
            Log.info("Generating BEAST2 model library schema...");

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

            Log.info("\nSchema generation complete!");
            Log.info("Engine: " + modelLibrary.getString("engine") + " " + modelLibrary.getString("engineVersion"));
            Log.info("Total types: " + types.length());
            Log.info("Total generators: " + generators.length());

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

            Log.info("\nGenerators:");
            Log.info("  Distributions: " + distributionCount);
            Log.info("  Functions: " + functionCount);

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

        } catch (Exception e) {
            Log.err("Error generating schema: " + e.getMessage());
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
        String title = "Schema " + version;

        new beastfx.app.tools.Application(new Beast2LangSchema(), title, args);
    }
}
package org.beast2.modelLanguage;

import org.beast2.modelLanguage.builder.Beast2ModelBuilderReflection;
import org.beast2.modelLanguage.converter.Beast2ToPhyloSpecConverter;
import org.beast2.modelLanguage.converter.PhyloSpecToBeast2Converter;
import org.beast2.modelLanguage.model.Beast2Model;
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
import java.nio.file.Files;
import java.nio.file.Paths;
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
    }

    @Command(name = "validate", description = "Validate a Beast2Lang file")
    public Integer validate(
            @Parameters(paramLabel = "FILE", description = "Beast2Lang file to validate") File file) {
        try {
            System.out.println("Validating " + file.getPath() + "...");
            
            // Use our new refactored class directly
            Beast2ModelBuilderReflection builder = new Beast2ModelBuilderReflection();
            try (FileInputStream fis = new FileInputStream(file)) {
                Beast2Model model = builder.buildFromStream(fis);
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
            @Option(names = {"--from"}, description = "Source format: beast2, phylospec", defaultValue = "beast2") String fromFormat,
            @Option(names = {"--to"}, description = "Target format: beast2, phylospec, xml", defaultValue = "phylospec") String toFormat,
            @Option(names = {"-o", "--output"}, description = "Output file") File outputFile,
            @Option(names = {"--debug"}, description = "Enable debug logging", defaultValue = "false") boolean debug,
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
                    // Parse Beast2Lang
                    Beast2Model model = reflectionBuilder.buildFromStream(fis);
                    
                    try {
                        // Build BEAST2 objects using reflection
                        Object rootObject = reflectionBuilder.buildBeast2Objects(model);
                        
                        // If successful, generate XML using BEAST's XML producer
                        String xml = null;
                        
                        try {
                            xml = generateXML(rootObject);
                        } catch (Exception e) {
                            logger.severe("Error generating XML: " + e.getMessage());
                            xml = generateFallbackXML();
                        }
                        
                        if (xml != null) {
                            writeOutput(outputFile, xml);
                            return 0;
                        } else {
                            System.err.println("Error building BEAST2 objects: Unable to generate XML");
                            return 1;
                        }
                    } catch (Exception e) {
                        System.err.println("Error building BEAST2 objects: " + e.getMessage());
                        if (debug) {
                            e.printStackTrace();
                        }
                        
                        // Try to generate a fallback XML
                        String fallbackXml = generateFallbackXML();
                        if (fallbackXml != null) {
                            System.out.println("Generated fallback XML model");
                            writeOutput(outputFile, fallbackXml);
                            return 0;
                        }
                        
                        return 1;
                    }
                }
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
            Class<?> xmlProducerClass = Class.forName("beast.base.parser.XMLProducer");
            Object xmlProducer = xmlProducerClass.getDeclaredConstructor().newInstance();
            
            // Check if we need to skip checking for the root object
            try {
                java.lang.reflect.Field skippingRuleCheckingField = xmlProducerClass.getDeclaredField("skipRuleChecking");
                if (skippingRuleCheckingField != null) {
                    skippingRuleCheckingField.setAccessible(true);
                    skippingRuleCheckingField.set(xmlProducer, true);
                    logger.info("Disabled XML rule checking");
                }
            } catch (NoSuchFieldException e) {
                // Field might not exist in this BEAST version, which is fine
                logger.fine("No skipRuleChecking field found in XMLProducer");
            }
            
            // Try to add XML metadata for version and namespace
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
            xml.append("<beast namespace=\"beast.core:beast.evolution.alignment:beast.evolution.tree.coalescent:" +
                      "beast.core.util:beast.evolution.nuc:beast.evolution.operators:beast.evolution.sitemodel:" +
                      "beast.evolution.substitutionmodel:beast.evolution.likelihood\" version=\"2.7\">\n\n");
            
            // Invoke toXML method
            java.lang.reflect.Method toXMLMethod = xmlProducerClass.getMethod("toXML", Object.class);
            String objectXml = (String) toXMLMethod.invoke(xmlProducer, beastObject);
            
            // Add the object XML and closing tag
            if (objectXml != null) {
                xml.append(objectXml).append("\n</beast>");
                return xml.toString();
            } else {
                return generateFallbackXML();
            }
        } catch (Exception e) {
            logger.severe("Error generating XML: " + e.getMessage());
            e.printStackTrace();
            
            return generateFallbackXML();
        }
    }
    
    /**
     * Generate a fallback XML file when normal XML generation fails
     */
    private String generateFallbackXML() {
        // Create a minimal empty BEAST2 XML file
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
        xml.append("<beast namespace=\"beast.core:beast.evolution.alignment:beast.evolution.tree.coalescent:" +
                  "beast.core.util:beast.evolution.nuc:beast.evolution.operators:beast.evolution.sitemodel:" +
                  "beast.evolution.substitutionmodel:beast.evolution.likelihood\" version=\"2.7\">\n\n");
        
        // Add a simple run element
        xml.append("    <run id=\"mcmc\" spec=\"MCMC\" chainLength=\"1000000\">\n");
        xml.append("        <state id=\"state\">\n");
        xml.append("            <!-- No state nodes yet -->\n");
        xml.append("        </state>\n");
        xml.append("        <distribution id=\"posterior\" spec=\"util.CompoundDistribution\">\n");
        xml.append("            <!-- No distributions yet -->\n");
        xml.append("        </distribution>\n");
        xml.append("        <logger id=\"tracelog\" fileName=\"$(filebase).log\" logEvery=\"1000\">\n");
        xml.append("            <!-- No logged items yet -->\n");
        xml.append("        </logger>\n");
        xml.append("    </run>\n\n");
        
        xml.append("</beast>");
        
        return xml.toString();
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
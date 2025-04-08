package org.beast2.modelLanguage;

import org.beast2.modelLanguage.builder.Beast2ModelBuilder;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Main application class for Beast2Lang
 */
@Command(name = "beast2lang", 
         mixinStandardHelpOptions = true, 
         version = "beast2lang 0.1.0",
         description = "Provides utilities for working with Beast2 model definition language")
public class Beast2Lang implements Callable<Integer> {

    @Command(name = "validate", description = "Validate a Beast2Lang file")
    public Integer validate(
            @Parameters(paramLabel = "FILE", description = "Beast2Lang file to validate") File file) {
        try {
            System.out.println("Validating " + file.getPath() + "...");
            
            Beast2ModelBuilder builder = new Beast2ModelBuilder();
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
            @Parameters(paramLabel = "FILE", description = "Input file to convert") File inputFile) {
        
        try {
            System.out.println("Converting from " + fromFormat + " to " + toFormat + "...");
            
            // Initialize converters
            Beast2ModelBuilder builder = new Beast2ModelBuilder();
            Beast2ToPhyloSpecConverter toPhyloSpecConverter = new Beast2ToPhyloSpecConverter();
            PhyloSpecToBeast2Converter toBeast2Converter = new PhyloSpecToBeast2Converter();
            Beast2ModelBuilderReflection reflectionBuilder = new Beast2ModelBuilderReflection();
            
            // Perform conversion
            if ("beast2".equals(fromFormat) && "phylospec".equals(toFormat)) {
                try (FileInputStream fis = new FileInputStream(inputFile)) {
                    // Convert Beast2Lang to PhyloSpec
                    Beast2Model model = builder.buildFromStream(fis);
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
                    // Parse Beast2Lang
                    Beast2Model model = reflectionBuilder.buildFromString(
                            new String(Files.readAllBytes(inputFile.toPath())));
                    
                    // Convert to BEAST2 objects
                    Object rootObject = reflectionBuilder.buildBeast2Objects(model);
                    
                    // Generate XML
                    String xml = generateXML(rootObject);
                    
                    // Output result
                    writeOutput(outputFile, xml);
                    return 0;
                }
            } else {
                System.err.println("Unsupported conversion: " + fromFormat + " to " + toFormat);
                return 1;
            }
        } catch (Exception e) {
            System.err.println("Error converting file: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    @Command(name = "create", description = "Create BEAST2 objects from Beast2Lang and optionally write XML")
    public Integer create(
            @Option(names = {"-o", "--output"}, description = "Output XML file") File outputFile,
            @Parameters(paramLabel = "FILE", description = "Beast2Lang file to process") File inputFile) {
        
        try {
            System.out.println("Creating BEAST2 objects from " + inputFile.getPath() + "...");
            
            // Parse the Beast2Lang file
            Beast2ModelBuilderReflection builder = new Beast2ModelBuilderReflection();
            String modelContent = new String(Files.readAllBytes(inputFile.toPath()));
            Beast2Model model = builder.buildFromString(modelContent);
            
            // Create BEAST2 objects
            Object rootObject = builder.buildBeast2Objects(model);
            
            // Print summary of created objects
            Map<String, Object> beastObjects = builder.getBeastObjects();
            System.out.println("Created " + beastObjects.size() + " BEAST2 objects:");
            for (String key : beastObjects.keySet()) {
                Object obj = beastObjects.get(key);
                System.out.println("  - " + key + ": " + obj.getClass().getSimpleName());
            }
            
            // If output file specified, write XML
            if (outputFile != null) {
                String xml = generateXML(rootObject);
                writeOutput(outputFile, xml);
                System.out.println("XML written to " + outputFile.getPath());
            }
            
            return 0;
        } catch (Exception e) {
            System.err.println("Error creating BEAST2 objects: " + e.getMessage());
            e.printStackTrace();
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
            sb.append(statement.toString()).append(";\n");
        });
        
        return sb.toString();
    }

    /**
     * Generate XML from a BEAST2 object
     */
    private String generateXML(Object beastObject) throws Exception {
        // Use reflection to invoke BEAST2's XMLProducer
        Class<?> xmlProducerClass = Class.forName("beast.base.parser.XMLProducer");
        Object xmlProducer = xmlProducerClass.getDeclaredConstructor().newInstance();
        
        // Invoke toXML method
        java.lang.reflect.Method toXMLMethod = xmlProducerClass.getMethod("toXML", Object.class);
        String xml = (String) toXMLMethod.invoke(xmlProducer, beastObject);
        
        return xml;
    }

    /**
     * Write output to a file or stdout
     */
    private void writeOutput(File outputFile, String content) throws IOException {
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
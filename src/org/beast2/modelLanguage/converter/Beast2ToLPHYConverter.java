package org.beast2.modelLanguage.converter;

import org.beast2.modelLanguage.builder.Beast2LangParser;
import org.beast2.modelLanguage.builder.Beast2LangParserImpl;
import org.beast2.modelLanguage.model.Beast2Model;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Converter for transforming Beast2 Model Language (B2L) to LinguaPhylo (LPHY).
 */
public class Beast2ToLPHYConverter {
    private final Beast2LangParser parser;
    private final LPHYMappingProvider mappingProvider;
    private final LPHYGenerator generator;

    /**
     * Default constructor.
     */
    public Beast2ToLPHYConverter() {
        this.parser = new Beast2LangParserImpl();
        this.mappingProvider = new LPHYMappingProvider();
        this.generator = new LPHYGenerator(mappingProvider);
    }

    /**
     * Constructor with custom mapping provider.
     *
     * @param mappingProvider Custom mapping provider
     */
    public Beast2ToLPHYConverter(LPHYMappingProvider mappingProvider) {
        this.parser = new Beast2LangParserImpl();
        this.mappingProvider = mappingProvider;
        this.generator = new LPHYGenerator(mappingProvider);
    }

    /**
     * Convert a B2L file to LPHY.
     *
     * @param inputFile Path to the B2L input file
     * @return LPHY code as a string
     * @throws IOException If file cannot be read or parsed
     */
    public String convert(String inputFile) throws IOException {
        // Use FileInputStream to read the file
        try (FileInputStream fileStream = new FileInputStream(inputFile)) {
            // Parse B2L file using the existing parser
            Beast2Model model = parser.parseFromStream(fileStream);

            // Generate LPHY code from the model
            return generator.generate(model);
        }
    }

    /**
     * Convert B2L code from a string to LPHY.
     *
     * @param b2lCode The B2L code as a string
     * @return LPHY code as a string
     */
    public String convertFromString(String b2lCode) {
        // Parse B2L code using the existing parser
        Beast2Model model = parser.parseFromString(b2lCode);

        // Generate LPHY code from the model
        return generator.generate(model);
    }

    /**
     * Convert a B2L model object to LPHY.
     *
     * @param model The Beast2Model to convert
     * @return LPHY code as a string
     */
    public String convertModel(Beast2Model model) {
        return generator.generate(model);
    }

    /**
     * Convert a B2L file to LPHY and write to output file.
     *
     * @param inputFile Path to the B2L input file
     * @param outputFile Path to the LPHY output file
     * @throws IOException If file operations fail
     */
    public void convertToFile(String inputFile, String outputFile) throws IOException {
        String lphyCode = convert(inputFile);
        Files.writeString(Paths.get(outputFile), lphyCode);
    }

    /**
     * Get the mapping provider used by this converter.
     *
     * @return The mapping provider
     */
    public LPHYMappingProvider getMappingProvider() {
        return mappingProvider;
    }

    /**
     * Main method for command-line execution.
     *
     * Usage: java org.beast2.modelLanguage.converter.Beast2ToLPHYConverter input.b2l output.lphy
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Beast2ToLPHYConverter <input_b2l_file> <output_lphy_file>");
            System.exit(1);
        }

        try {
            Beast2ToLPHYConverter converter = new Beast2ToLPHYConverter();
            converter.convertToFile(args[0], args[1]);
            System.out.println("Conversion successful: " + args[0] + " -> " + args[1]);
        } catch (IOException e) {
            System.err.println("Error during conversion: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
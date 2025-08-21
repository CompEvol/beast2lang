package org.beast2.modelLanguage;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.parser.XMLProducer;
import org.beast2.modelLanguage.model.Beast2Model;
import org.beast2.modelLanguage.model.RequiresStatement;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main application class for Beast2Lang
 * Provides utilities for working with Beast2 model definition language
 */
public class Beast2LangUtils {

    /**
     * Write output with optional pretty printing
     */
    public static void writeOutput(File outputFile, String schema, boolean prettyPrint) throws IOException, JSONException {
        if (prettyPrint) {
            // Reparse and pretty print with proper indentation
            JSONObject schemaObj = new JSONObject(schema);
            Files.write(outputFile.toPath(), schemaObj.toString(2).getBytes(StandardCharsets.UTF_8));
        } else {
            Files.write(outputFile.toPath(), schema.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Capitalize first letter of a string
     */
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    // Helper method to extract required attribute from XML
    public static String extractRequiredPackages(File xmlFile) throws IOException {
        // Simple regex or XML parsing to extract the required attribute
        String content = Files.readString(xmlFile.toPath());
        Pattern pattern = Pattern.compile("required=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // Helper method to parse required string and add to model
    public static void addRequiresFromString(Beast2Model model, String required) {
        // Parse "BEAST.base v2.7.7:feast v10.4.0"
        String[] packages = required.split(":");
        for (String pkg : packages) {
            // Remove version info
            String packageName = pkg.trim().split("\\s+")[0];
            model.addRequires(new RequiresStatement(packageName));
        }
    }

    public static void dumpModelStructure(Map<String, Object> objects) {
        System.out.println("\n----- MODEL STRUCTURE DUMP -----");

        // Print all objects
        System.out.println("\nObjects:");
        for (Map.Entry<String, Object> entry : objects.entrySet()) {
            String id = entry.getKey();
            Object obj = entry.getValue();
            String className = obj.getClass().getName();

            System.out.println(id + " (" + className + ")");
        }

        // Print connections (inputs)
        System.out.println("\nConnections:");
        for (Map.Entry<String, Object> entry : objects.entrySet()) {
            String id = entry.getKey();
            Object obj = entry.getValue();

            // Only process BEAST objects with inputs
            if (obj instanceof BEASTInterface) {
                BEASTInterface beastObj = (BEASTInterface) obj;
                System.out.println("\n" + id + " (" + obj.getClass().getSimpleName() + ") connections:");

                // Print all inputs and their values
                for (String inputName : beastObj.getInputs().keySet()) {
                    try {
                        Input<?> input = beastObj.getInput(inputName);
                        Object value = input.get();

                        // Format the value based on its type
                        String valueStr;
                        if (value == null) {
                            valueStr = "null";
                        } else if (value instanceof BEASTInterface) {
                            valueStr = ((BEASTInterface) value).getID()
                                    + " (" + value.getClass().getSimpleName() + ")";
                        } else if (value instanceof Collection) {
                            valueStr = formatCollection((Collection<?>) value);
                        } else {
                            valueStr = value.toString();
                        }

                        System.out.println("  " + inputName + " = " + valueStr);
                    } catch (Exception e) {
                        System.out.println("  " + inputName + " = [Error: " + e.getMessage() + "]");
                    }
                }
            }
        }

        // Check for obvious issues
        System.out.println("\nPotential Issues:");
        checkForIssues(objects);

        System.out.println("\n----- END MODEL STRUCTURE DUMP -----\n");
    }

    public static String formatCollection(Collection<?> collection) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        for (Object item : collection) {
            if (!first) {
                sb.append(", ");
            }

            if (item instanceof BEASTInterface) {
                BEASTInterface beastItem = (BEASTInterface) item;
                sb.append(beastItem.getID())
                        .append(" (")
                        .append(item.getClass().getSimpleName())
                        .append(")");
            } else {
                sb.append(item.toString());
            }

            first = false;
        }

        sb.append("]");
        return sb.toString();
    }

    public static void checkForIssues(Map<String, Object> objects) {
        // Check for missing connections in TreeLikelihood
        for (Object obj : objects.values()) {
            if (obj instanceof beast.base.evolution.likelihood.TreeLikelihood) {
                beast.base.evolution.likelihood.TreeLikelihood likelihood =
                        (beast.base.evolution.likelihood.TreeLikelihood) obj;

                // Check data input
                try {
                    Object data = likelihood.getInput("data").get();
                    if (data == null) {
                        System.out.println("WARNING: TreeLikelihood has null data input");
                    } else if (!(data instanceof beast.base.evolution.alignment.Alignment)) {
                        System.out.println("WARNING: TreeLikelihood has non-Alignment data input: "
                                + data.getClass().getName());
                    }
                } catch (Exception e) {
                    System.out.println("WARNING: Unable to check TreeLikelihood data input: " + e.getMessage());
                }

                // Check tree input
                try {
                    Object tree = likelihood.getInput("tree").get();
                    if (tree == null) {
                        System.out.println("WARNING: TreeLikelihood has null tree input");
                    }
                } catch (Exception e) {
                    System.out.println("WARNING: Unable to check TreeLikelihood tree input: " + e.getMessage());
                }

                // Check siteModel input
                try {
                    Object siteModel = likelihood.getInput("siteModel").get();
                    if (siteModel == null) {
                        System.out.println("WARNING: TreeLikelihood has null siteModel input");
                    }
                } catch (Exception e) {
                    System.out.println("WARNING: Unable to check TreeLikelihood siteModel input: " + e.getMessage());
                }
            }
        }

        // Check for DummyBEASTObjects being used where real objects are expected
        for (Object obj : objects.values()) {
            if (obj != null && obj.getClass().getSimpleName().contains("Dummy")) {
                System.out.println("WARNING: Using stub/dummy object: " +
                        (obj instanceof BEASTInterface ?
                                ((BEASTInterface)obj).getID() : obj));
            }
        }
    }

    /**
     * Generate Beast2Lang syntax from a Beast2Model
     */
    public static String generateBeast2Lang(Beast2Model model) {
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
    public static String generateXML(Object beastObject) {
        if (beastObject == null) {
            throw new IllegalArgumentException("Cannot generate XML from null object");
        }

        try {
            // Use reflection to invoke BEAST2's XMLProducer
            XMLProducer xmlProducer = new XMLProducer();

            // Invoke toXML method
            String objectXml = xmlProducer.toXML(beastObject);

            // Add the object XML and closing tag
            if (objectXml != null) {
                return objectXml;
            } else {
                throw new RuntimeException("Failed to generate XML");
            }
        } catch (Exception e) {
            Log.err("Error generating XML: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Write output to a file or stdout
     */
    public static void writeOutput(File outputFile, String content) throws IOException {
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

}
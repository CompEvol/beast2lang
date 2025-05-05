package org.beast2.modelLanguage.converter;

import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides mappings between B2L and LPHY constructs.
 */
public class LPHYMappingProvider {
    // Map from B2L function/class name to LPHY function name
    private final Map<String, String> functionMappings = new HashMap<>();

    // Map from (LPHY function name, parameter name) to LPHY parameter name
    private final Map<String, Map<String, String>> parameterMappings = new HashMap<>();

    /**
     * Constructor that initializes default mappings.
     */
    public LPHYMappingProvider() {
        initializeDefaultMappings();
    }

    /**
     * Initialize with default mappings between B2L and LPHY.
     */
    private void initializeDefaultMappings() {
        // Class/Function name mappings
        addFunctionMapping("LogNormalDistributionModel", "LogNormal");
        addFunctionMapping("YuleModel", "Yule");
        addFunctionMapping("BirthDeathModel", "BirthDeath");
        addFunctionMapping("JukesCantor", "jukesCantor");
        addFunctionMapping("HKY", "hky");
        addFunctionMapping("GTR", "gtr");
        addFunctionMapping("TreeLikelihood", "PhyloCTMC");
        addFunctionMapping("nexus", "readNexus");

        // Parameter name mappings for distributions
        addParameterMapping("LogNormal", "M", "meanlog");
        addParameterMapping("LogNormal", "S", "sdlog");
        addParameterMapping("LogNormal", "offset", "offset");

        // Parameter mappings for Yule model
        addParameterMapping("Yule", "birthDiffRate", "lambda");
        addParameterMapping("Yule", "taxonset", "taxa");

        // Parameter mappings for Birth-Death model
        addParameterMapping("BirthDeath", "birthDiffRate", "lambda");
        addParameterMapping("BirthDeath", "relativeDeathRate", "mu");
        addParameterMapping("BirthDeath", "taxonset", "taxa");

        // Parameter mappings for PhyloCTMC (TreeLikelihood)
        addParameterMapping("PhyloCTMC", "siteModel", "Q");
        addParameterMapping("PhyloCTMC", "tree", "tree");
        addParameterMapping("PhyloCTMC", "branchRateModel", "branchRates");
    }

    /**
     * Add a function name mapping.
     *
     * @param b2lName B2L function/class name
     * @param lphyName LPHY function name
     */
    public void addFunctionMapping(String b2lName, String lphyName) {
        functionMappings.put(b2lName, lphyName);
    }

    /**
     * Add a parameter name mapping.
     *
     * @param functionName LPHY function name
     * @param b2lParamName B2L parameter name
     * @param lphyParamName LPHY parameter name
     */
    public void addParameterMapping(String functionName, String b2lParamName, String lphyParamName) {
        parameterMappings
                .computeIfAbsent(functionName, k -> new HashMap<>())
                .put(b2lParamName, lphyParamName);
    }

    /**
     * Map a B2L function/class name to LPHY function name.
     *
     * @param b2lName The B2L function/class name
     * @return The corresponding LPHY function name
     */
    public String mapFunctionName(String b2lName) {
        // Extract simple name if qualified
        String simpleName = b2lName;
        int lastDot = b2lName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < b2lName.length() - 1) {
            simpleName = b2lName.substring(lastDot + 1);
        }

        // Use mapping if available
        if (functionMappings.containsKey(simpleName)) {
            return functionMappings.get(simpleName);
        }

        // Format as LPHY style (camelCase without "Model" suffix)
        return formatLPHYName(simpleName);
    }

    /**
     * Map a B2L parameter name to LPHY parameter name.
     *
     * @param functionName LPHY function name
     * @param b2lParamName B2L parameter name
     * @return The corresponding LPHY parameter name
     */
    public String mapParameterName(String functionName, String b2lParamName) {
        Map<String, String> paramMap = parameterMappings.get(functionName);
        if (paramMap != null && paramMap.containsKey(b2lParamName)) {
            return paramMap.get(b2lParamName);
        }
        return b2lParamName;
    }

    /**
     * Format a B2L name as LPHY style (camelCase without "Model" suffix).
     *
     * @param name The B2L name
     * @return Formatted LPHY name
     */
    private String formatLPHYName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        // Remove "Model" suffix if present
        if (name.endsWith("Model")) {
            name = name.substring(0, name.length() - "Model".length());
        }

        // Convert first character to lowercase if it's uppercase
        if (Character.isUpperCase(name.charAt(0))) {
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }

        return name;
    }

    /**
     * Load mappings from a properties file.
     *
     * @param resourcePath Path to the properties file
     */
    public void loadMappingsFromResource(String resourcePath) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                System.err.println("Unable to find resource: " + resourcePath);
                return;
            }

            Properties props = new Properties();
            props.load(input);

            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);

                if (key.contains(".")) {
                    // Parameter mapping: "function.parameter=mappedName"
                    String[] parts = key.split("\\.", 2);
                    if (parts.length == 2) {
                        String functionName = parts[0];
                        String paramName = parts[1];
                        addParameterMapping(functionName, paramName, value);
                    }
                } else {
                    // Function mapping: "b2lName=lphyName"
                    addFunctionMapping(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading mappings from " + resourcePath + ": " + e.getMessage());
        }
    }
}
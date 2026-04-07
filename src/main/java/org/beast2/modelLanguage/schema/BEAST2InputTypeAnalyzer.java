package org.beast2.modelLanguage.schema;

import beast.base.core.BEASTInterface;
import beast.base.core.Function;
import beast.base.core.Input;
import beast.base.inference.parameter.RealParameter;
import beast.pkgmgmt.Package;
import org.beast2.modelLanguage.beast.BEASTUtils;
import org.beast2.modelLanguage.beast.BeastObjectFactory;
import org.beast2.modelLanguage.schema.core.ComponentInfo;
import org.beast2.modelLanguage.schema.core.KnownTypes;
import org.beast2.modelLanguage.schema.scanner.ComponentFilter;
import org.beast2.modelLanguage.schema.scanner.ComponentScanner;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Logger;

/**
 * Analyzes BEAST2 classes to find all Input<RealParameter> and Input<Function> instances
 * and generates a report for planning migration to a strong type system.
 */
public class BEAST2InputTypeAnalyzer {
    private static final Logger logger = Logger.getLogger(BEAST2InputTypeAnalyzer.class.getName());

    private final BeastObjectFactory factory;
    private final ComponentFilter filter;
    private final ComponentScanner scanner;

    // Store results
    private final Map<String, List<InputInfo>> realParameterInputs = new TreeMap<>();
    private final Map<String, List<InputInfo>> functionInputs = new TreeMap<>();

    // Statistics
    private int totalRealParameterInputs = 0;
    private int totalFunctionInputs = 0;
    private int totalClassesAnalyzed = 0;
    private int failedInstantiations = 0;

    public BEAST2InputTypeAnalyzer() {
        this.factory = new BeastObjectFactory();
        this.filter = new ComponentFilter();
        this.scanner = new ComponentScanner(factory, filter);
    }

    /**
     * Information about an input field
     */
    public static class InputInfo {
        public final String className;
        public final String inputName;
        public final String description;
        public final boolean isRequired;
        public final boolean isList;
        public final String defaultValue;
        public final String suggestedType; // Suggested strong type

        public InputInfo(String className, String inputName, String description,
                         boolean isRequired, boolean isList, String defaultValue, String suggestedType) {
            this.className = className;
            this.inputName = inputName;
            this.description = description;
            this.isRequired = isRequired;
            this.isList = isList;
            this.defaultValue = defaultValue;
            this.suggestedType = suggestedType;
        }
    }

    /**
     * Main analysis method
     */
    public void analyzeAllComponents() {
        logger.info("Starting BEAST2 input type analysis...");

        // Scan for all components
        List<ComponentInfo> allComponents = scanAllComponents();

        // Analyze each component
        for (ComponentInfo component : allComponents) {
            analyzeComponent(component);
        }

        logger.info("Analysis complete. Analyzed " + totalClassesAnalyzed + " classes.");
        logger.info("Found " + totalRealParameterInputs + " Input<RealParameter> instances");
        logger.info("Found " + totalFunctionInputs + " Input<Function> instances");
        logger.info("Failed to instantiate " + failedInstantiations + " classes");
    }

    /**
     * Scan for all components using ComponentScanner
     */
    private List<ComponentInfo> scanAllComponents() {
        List<ComponentInfo> allComponents = new ArrayList<>();

        // 1. Scan important base types
        allComponents.addAll(scanner.scanImportantTypes(
                KnownTypes.IMPORTANT_BASE_TYPES, "beast.base"));

        // 2. Scan all packages
        Map<String, Package> packages = getAllPackages();
        allComponents.addAll(scanner.scanPackages(packages));

        logger.info("Found " + allComponents.size() + " total components to analyze");
        return allComponents;
    }

    /**
     * Get all packages as a map
     */
    private Map<String, Package> getAllPackages() {
        Map<String, Package> packages = new HashMap<>();
        Map<String, Object> allPlugins = factory.getAllPlugins();

        for (Map.Entry<String, Object> entry : allPlugins.entrySet()) {
            if (entry.getValue() instanceof Package) {
                Package pkg = (Package) entry.getValue();
                packages.put(pkg.getName(), pkg);
            }
        }

        return packages;
    }

    /**
     * Analyze a single component
     */
    private void analyzeComponent(ComponentInfo component) {
        Class<?> clazz = component.getClazz();

        // Only analyze concrete BEASTInterface classes
        if (!BEASTInterface.class.isAssignableFrom(clazz) ||
                component.isInterface() ||
                component.isAbstract() ||
                component.isEnum()) {
            return;
        }

        totalClassesAnalyzed++;

        // Try to create instance
        BEASTInterface instance = null;
        try {
            instance = (BEASTInterface) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            failedInstantiations++;
            return; // Skip classes we can't instantiate
        }

        // Get all inputs
        Map<String, Input<?>> inputMap = BEASTUtils.buildInputMap(instance, clazz);

        // Analyze each input
        for (Map.Entry<String, Input<?>> entry : inputMap.entrySet()) {
            String inputName = entry.getKey();
            Input<?> input = entry.getValue();

            analyzeInput(component.getClassName(), inputName, input, instance);
        }
    }

    /**
     * Analyze a single input
     */
    private void analyzeInput(String className, String inputName, Input<?> input, BEASTInterface instance) {
        // Get the expected type
        Type expectedType = BEASTUtils.getInputExpectedType(input, instance, inputName);

        if (expectedType == null) {
            return;
        }

        // Check if it's Input<RealParameter> or Input<Function>
        if (isRealParameterInput(expectedType)) {
            handleRealParameterInput(className, inputName, input, expectedType);
        } else if (isFunctionInput(expectedType)) {
            handleFunctionInput(className, inputName, input, expectedType);
        }
    }

    /**
     * Check if the type is RealParameter or List<RealParameter>
     */
    private boolean isRealParameterInput(Type type) {
        if (type instanceof Class) {
            return RealParameter.class.isAssignableFrom((Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) type;
            Type rawType = pType.getRawType();
            if (rawType instanceof Class && List.class.isAssignableFrom((Class<?>) rawType)) {
                Type[] args = pType.getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class) {
                    return RealParameter.class.isAssignableFrom((Class<?>) args[0]);
                }
            }
        }
        return false;
    }

    /**
     * Check if the type is Function or List<Function>
     */
    private boolean isFunctionInput(Type type) {
        if (type instanceof Class) {
            return Function.class.isAssignableFrom((Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) type;
            Type rawType = pType.getRawType();
            if (rawType instanceof Class && List.class.isAssignableFrom((Class<?>) rawType)) {
                Type[] args = pType.getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class) {
                    return Function.class.isAssignableFrom((Class<?>) args[0]);
                }
            }
        }
        return false;
    }

    /**
     * Handle RealParameter input
     */
    private void handleRealParameterInput(String className, String inputName, Input<?> input, Type type) {
        totalRealParameterInputs++;

        boolean isList = type instanceof ParameterizedType &&
                ((ParameterizedType) type).getRawType() instanceof Class &&
                List.class.isAssignableFrom((Class<?>) ((ParameterizedType) type).getRawType());

        String suggestedType = suggestStrongType(inputName, input.getTipText(), className, isList);

        InputInfo info = new InputInfo(
                className,
                inputName,
                input.getTipText(),
                input.getRule() == Input.Validate.REQUIRED,
                isList,
                input.defaultValue != null ? input.defaultValue.toString() : null,
                suggestedType
        );

        realParameterInputs.computeIfAbsent(className, k -> new ArrayList<>()).add(info);
    }

    /**
     * Handle Function input
     */
    private void handleFunctionInput(String className, String inputName, Input<?> input, Type type) {
        totalFunctionInputs++;

        boolean isList = type instanceof ParameterizedType &&
                ((ParameterizedType) type).getRawType() instanceof Class &&
                List.class.isAssignableFrom((Class<?>) ((ParameterizedType) type).getRawType());

        String suggestedType = suggestStrongTypeForFunction(inputName, input.getTipText(), className, isList);

        InputInfo info = new InputInfo(
                className,
                inputName,
                input.getTipText(),
                input.getRule() == Input.Validate.REQUIRED,
                isList,
                input.defaultValue != null ? input.defaultValue.toString() : null,
                suggestedType
        );

        functionInputs.computeIfAbsent(className, k -> new ArrayList<>()).add(info);
    }

    /**
     * Suggest a strong type based on input name and description
     */
    private String suggestStrongType(String inputName, String description, String className, boolean isList) {
        String baseType = "Real";

        // Analyze input name and description for clues
        String lowerName = inputName.toLowerCase();
        String lowerDesc = description != null ? description.toLowerCase() : "";

        // Check for frequencies - these should be Simplex (sum to 1)
        // Note: frequencies are often multi-dimensional even as a single RealParameter
        if (lowerName.contains("frequencies") || lowerName.equals("freqs")) {
            return "Simplex";  // Multi-dimensional parameter that sums to 1
        }

        // Check for heights - these should be positive
        else if (lowerName.contains("height") || lowerName.equals("heights")) {
            // Heights might be a vector if multi-dimensional
            baseType = "PositiveReal";
            // If it contains "heights" (plural) it's likely multi-dimensional
            if (lowerName.equals("heights") || lowerName.endsWith("heights")) {
                return "Vector<PositiveReal>";
            }
        }

        // Check for population sizes - these must be positive
        else if (lowerName.contains("popsize") || lowerName.startsWith("pop") ||
                lowerName.contains("population") || lowerName.equals("ne")) {
            baseType = "PositiveReal";
        }

        // Check for positive constraints - including rates mentioned in description
        else if (lowerName.contains("rate") || lowerName.contains("scale") ||
                lowerName.contains("sigma") || lowerName.contains("variance") ||
                lowerName.contains("kappa") || // kappa is typically a rate parameter
                lowerName.equals("alpha") || lowerName.equals("beta") ||
                lowerName.equals("gamma") ||
                lowerDesc.contains("positive") || lowerDesc.contains("must be > 0") ||
                lowerDesc.contains("scale parameter") ||
                lowerDesc.contains("shape parameter") ||  lowerDesc.contains("rate")) {
            baseType = "PositiveReal";
        }

        // Check for probability/proportion (single values)
        else if (lowerName.contains("prob") || lowerName.contains("proportion") ||
                lowerDesc.contains("between 0 and 1") || lowerDesc.contains("probability")) {
            baseType = "Probability";
        }

        // Check for parameters that are likely vectors (multi-dimensional)
        else if (lowerName.contains("values") || lowerName.contains("vector") ||
                (lowerName.endsWith("s") && !lowerName.endsWith("ss"))) { // plural often indicates vector
            return "Vector<" + baseType + ">";
        }

        // Check for dimension hints
        else if (lowerName.contains("dim") || lowerName.contains("dimension")) {
            baseType = "PositiveInteger";
        }

        // Check for specific domain knowledge
        else if (className.contains("Birth") && lowerName.contains("rate")) {
            baseType = "PositiveReal";
        }
        else if (className.contains("Clock") && lowerName.contains("rate")) {
            baseType = "PositiveReal";
        }

        // Handle lists/vectors (actual List<RealParameter>)
        if (isList) {
            return "Vector<" + baseType + ">";
        }

        return baseType;
    }

    /**
     * Suggest a strong type for Function inputs
     */
    private String suggestStrongTypeForFunction(String inputName, String description, String className, boolean isList) {
        // Functions are more general - might be Real, Integer, or stay as Function
        String baseType = "Real"; // Default assumption

        String lowerName = inputName.toLowerCase();
        String lowerDesc = description != null ? description.toLowerCase() : "";

        // Check for heights - these should be positive
        if (lowerName.contains("height") || lowerName.equals("heights")) {
            baseType = "PositiveReal";
        }

        // Check for frequencies that should be Simplex
        else if (lowerName.contains("frequencies") || lowerName.equals("freqs")) {
            return "Simplex";  // Multi-dimensional parameter that sums to 1
        }

        // Check for population sizes
        else if (lowerName.contains("popsize") || lowerName.startsWith("pop") ||
                lowerName.contains("population") || lowerName.equals("ne")) {
            baseType = "PositiveReal";
        }

        // Check for positive constraints in description
        else if (lowerDesc.contains("scale parameter") || lowerDesc.contains("positive") ||
                lowerDesc.contains("must be > 0")) {
            baseType = "PositiveReal";
        }

        // Check for integer hints
        else if (lowerName.contains("count") || lowerName.contains("size") ||
                lowerName.contains("number") || lowerDesc.contains("integer")) {
            baseType = "Integer";
        }

        // Some functions might need to stay as Function (e.g., tree statistics)
        else if (lowerName.contains("statistic") || className.contains("Logger")) {
            baseType = "Function"; // Keep as Function
        }

        // Handle lists/vectors (but not for Simplex which is already a vector type)
        if (isList && !baseType.equals("Simplex")) {
            return "Vector<" + baseType + ">";
        }

        return baseType;
    }

    /**
     * Generate reports
     */
    public void generateReports(String outputDir) throws IOException {
        // Generate CSV report
        generateCSVReport(outputDir + "/input_analysis.csv");

        // Generate detailed text report
        generateTextReport(outputDir + "/input_analysis_detailed.txt");

        // Generate summary report
        generateSummaryReport(outputDir + "/input_analysis_summary.txt");

        logger.info("Reports generated in " + outputDir);
    }

    /**
     * Generate CSV report
     */
    private void generateCSVReport(String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("Type,ClassName,InputName,Description,Required,IsList,DefaultValue,SuggestedType");

            // RealParameter inputs
            for (Map.Entry<String, List<InputInfo>> entry : realParameterInputs.entrySet()) {
                for (InputInfo info : entry.getValue()) {
                    writer.printf("RealParameter,%s,%s,\"%s\",%s,%s,%s,%s%n",
                            info.className,
                            info.inputName,
                            info.description.replace("\"", "\"\""),
                            info.isRequired,
                            info.isList,
                            info.defaultValue != null ? info.defaultValue : "",
                            info.suggestedType
                    );
                }
            }

            // Function inputs
            for (Map.Entry<String, List<InputInfo>> entry : functionInputs.entrySet()) {
                for (InputInfo info : entry.getValue()) {
                    writer.printf("Function,%s,%s,\"%s\",%s,%s,%s,%s%n",
                            info.className,
                            info.inputName,
                            info.description.replace("\"", "\"\""),
                            info.isRequired,
                            info.isList,
                            info.defaultValue != null ? info.defaultValue : "",
                            info.suggestedType
                    );
                }
            }
        }
    }

    /**
     * Generate detailed text report
     */
    private void generateTextReport(String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("BEAST2 Input Type Analysis - Detailed Report");
            writer.println("===========================================");
            writer.println();

            writer.println("Classes with Input<RealParameter>:");
            writer.println("----------------------------------");
            for (Map.Entry<String, List<InputInfo>> entry : realParameterInputs.entrySet()) {
                writer.println("\n" + entry.getKey() + ":");
                for (InputInfo info : entry.getValue()) {
                    writer.printf("  - %s: %s\n", info.inputName, info.suggestedType);
                    writer.printf("    Description: %s\n", info.description);
                    writer.printf("    Required: %s, List: %s\n", info.isRequired, info.isList);
                    if (info.defaultValue != null) {
                        writer.printf("    Default: %s\n", info.defaultValue);
                    }
                }
            }

            writer.println("\n\nClasses with Input<Function>:");
            writer.println("-----------------------------");
            for (Map.Entry<String, List<InputInfo>> entry : functionInputs.entrySet()) {
                writer.println("\n" + entry.getKey() + ":");
                for (InputInfo info : entry.getValue()) {
                    writer.printf("  - %s: %s\n", info.inputName, info.suggestedType);
                    writer.printf("    Description: %s\n", info.description);
                    writer.printf("    Required: %s, List: %s\n", info.isRequired, info.isList);
                    if (info.defaultValue != null) {
                        writer.printf("    Default: %s\n", info.defaultValue);
                    }
                }
            }
        }
    }

    /**
     * Generate summary report
     */
    private void generateSummaryReport(String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("BEAST2 Input Type Analysis - Summary");
            writer.println("===================================");
            writer.println();
            writer.printf("Total classes analyzed: %d\n", totalClassesAnalyzed);
            writer.printf("Failed instantiations: %d\n", failedInstantiations);
            writer.println();

            writer.printf("Total Input<RealParameter> instances: %d\n", totalRealParameterInputs);
            writer.printf("Classes with RealParameter inputs: %d\n", realParameterInputs.size());
            writer.println();

            writer.printf("Total Input<Function> instances: %d\n", totalFunctionInputs);
            writer.printf("Classes with Function inputs: %d\n", functionInputs.size());
            writer.println();

            // Type distribution
            Map<String, Integer> typeDistribution = new HashMap<>();

            for (List<InputInfo> infos : realParameterInputs.values()) {
                for (InputInfo info : infos) {
                    typeDistribution.merge(info.suggestedType, 1, Integer::sum);
                }
            }

            for (List<InputInfo> infos : functionInputs.values()) {
                for (InputInfo info : infos) {
                    typeDistribution.merge(info.suggestedType, 1, Integer::sum);
                }
            }

            writer.println("\nSuggested Type Distribution:");
            writer.println("---------------------------");
            typeDistribution.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> writer.printf("%s: %d\n", e.getKey(), e.getValue()));
        }
    }

    /**
     * Main method
     */
    public static void main(String[] args) {
        try {
            BEAST2InputTypeAnalyzer analyzer = new BEAST2InputTypeAnalyzer();

            // Run analysis
            analyzer.analyzeAllComponents();

            // Generate reports
            String outputDir = args.length > 0 ? args[0] : ".";
            analyzer.generateReports(outputDir);

            System.out.println("Analysis complete! Reports generated in " + outputDir);

        } catch (Exception e) {
            logger.severe("Error during analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
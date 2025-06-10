package org.beast2.modelLanguage.schema.scanner;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Filters classes to determine which ones are model components
 */
public class ComponentFilter {
    private static final Logger logger = Logger.getLogger(ComponentFilter.class.getName());

    private final FilterReport filterReport = new FilterReport();

    private static final Set<String> EXCLUDED_PACKAGES = Set.of(
            "beastfx.app"
    );

    private static final Set<String> INFERENCE_TYPES = Set.of(
            "Logger", "Operator", "MCMC", "OperatorSchedule",
            "Runnable", "DistanceProvider","TreeLogFileState", "TraceLogFileState", "LogFileState"
    );

    private static final Set<String> GUI_TYPES = Set.of(
            "LogFile", "TreeFile", "OutFile", "BeautiSubTemplate"
    );

    private static final Set<String> INFERENCE_CLASSES = Set.of(
            "beast.base.inference.Runnable",
            "beast.base.inference.Operator",
            "beast.base.inference.OperatorSchedule",
            "beast.base.inference.Logger"
    );

    public FilterReport getFilterReport() {
        return filterReport;
    }

    /**
     * Check if a class represents a model component (not inference/logging)
     */
    public boolean isModelClass(Class<?> clazz) {
        String className = clazz.getName();
        String packageName = clazz.getPackage() != null ? clazz.getPackage().getName() : "";

        // Exclude BEAUti GUI components
        if (isGUIPackage(packageName)) {
            String reason = "GUI package: " + packageName;
            logger.fine("Excluding BEAUti/GUI class: " + clazz.getSimpleName());
            filterReport.addFiltered(className, reason);
            return false;
        }

        // Check if extends GUI classes
        if (extendsGUIClass(clazz)) {
            String reason = "Extends GUI class";
            logger.fine("Excluding subclass of BEAUti/GUI class: " + clazz.getSimpleName());
            filterReport.addFiltered(className, reason);
            return false;
        }

        // Exclude inference machinery
        if (isInferenceClass(clazz)) {
            String reason = "Inference class (Runnable/Operator/Logger/OperatorSchedule)";
            logger.fine("Excluding inference class: " + clazz.getSimpleName());
            filterReport.addFiltered(className, reason);
            return false;
        }

        // Exclude operator packages
        if (isOperatorPackage(packageName)) {
            String reason = "Operator package: " + packageName;
            logger.fine("Excluding operator package class: " + clazz.getSimpleName());
            filterReport.addFiltered(className, reason);
            return false;
        }

        // Check inputs for inference/GUI types
        String inputCheckResult = checkInputsForInferenceOrGUI(clazz);
        if (inputCheckResult != null) {
            filterReport.addFiltered(className, inputCheckResult);
            return false;
        }

        // Exclude test classes
        if (isTestClass(className)) {
            String reason = "Test class";
            logger.fine("Excluding test class: " + clazz.getSimpleName());
            filterReport.addFiltered(className, reason);
            return false;
        }

        // Exclude BEAUti-related classes
        if (isBEAutiRelated(className)) {
            String reason = "BEAUti-related class name";
            logger.fine("Excluding BEAUti-related class: " + clazz.getSimpleName());
            filterReport.addFiltered(className, reason);
            return false;
        }

        // Exclude logger-like classes (except TreeLogger)
        if (isLoggerClass(className)) {
            String reason = "Logger class (except TreeLogger)";
            logger.fine("Excluding logger-like class: " + clazz.getSimpleName());
            filterReport.addFiltered(className, reason);
            return false;
        }

        return true;
    }

    /**
     * Check if a type name is an inference-related type
     */
    public boolean isInferenceType(String typeName) {
        return INFERENCE_TYPES.contains(typeName) ||
                typeName.contains("Logger") ||
                typeName.contains("Operator") ||
                typeName.contains("OperatorSchedule");
    }

    /**
     * Check if a type name is a BEAUti/GUI-related type
     */
    public boolean isGUIType(String typeName) {
        return GUI_TYPES.contains(typeName) ||
                typeName.contains("Beauti");
    }

    private boolean isGUIPackage(String packageName) {
        return EXCLUDED_PACKAGES.stream().anyMatch(packageName::startsWith);
    }

    private boolean extendsGUIClass(Class<?> clazz) {
        Class<?> current = clazz.getSuperclass();
        while (current != null) {
            if (current.getPackage() != null &&
                    isGUIPackage(current.getPackage().getName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private boolean isInferenceClass(Class<?> clazz) {
        for (String inferenceClassName : INFERENCE_CLASSES) {
            try {
                Class<?> inferenceClass = Class.forName(inferenceClassName);
                if (inferenceClass.isAssignableFrom(clazz)) {
                    return true;
                }
            } catch (ClassNotFoundException e) {
                // Class not found, continue
            }
        }
        return false;
    }

    private boolean isOperatorPackage(String packageName) {
        return packageName.contains(".operators") ||
                packageName.contains(".operator");
    }

    private String checkInputsForInferenceOrGUI(Class<?> clazz) {
        if (BEASTInterface.class.isAssignableFrom(clazz) &&
                !clazz.isInterface() &&
                !Modifier.isAbstract(clazz.getModifiers())) {
            try {
                BEASTInterface instance = (BEASTInterface) clazz.getDeclaredConstructor().newInstance();
                for (Input<?> input : instance.listInputs()) {
                    String typeName = getInputTypeName(input);
                    if (isInferenceType(typeName)) {
                        String reason = "Has inference input '" + input.getName() + "' of type " + typeName;
                        logger.fine("Excluding " + clazz.getSimpleName() +
                                " - uses inference type: " + typeName);
                        return reason;
                    }
                    if (isGUIType(typeName)) {
                        String reason = "Has GUI input '" + input.getName() + "' of type " + typeName;
                        logger.fine("Excluding " + clazz.getSimpleName() +
                                " - uses GUI type: " + typeName);
                        return reason;
                    }
                }
            } catch (Exception e) {
                // Can't check inputs, continue
            }
        }
        return null;
    }

    private String getInputTypeName(Input<?> input) {
        Type type = input.getType();
        if (type == null) {
            return "Object";
        }

        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;
            return clazz.getSimpleName();
        }

        return "Object";
    }

    private boolean isTestClass(String className) {
        return className.endsWith("Test") || className.endsWith("Tests");
    }

    private boolean isBEAutiRelated(String className) {
        return className.contains("Beauti") || className.contains("BEAUti");
    }

    private boolean isLoggerClass(String className) {
        return className.contains("Logger") && !className.contains("TreeLogger");
    }
}
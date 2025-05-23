package org.beast2.modelLanguage.builder;

import org.beast2.modelLanguage.beast.BeastObjectFactoryImpl;
import org.beast2.modelLanguage.beast.PackageUtils;
import org.beast2.modelLanguage.model.ImportStatement;

import java.util.*;
import java.util.logging.Logger;

/**
 * Resolves short class names to fully qualified names based on import statements.
 * Enhanced to support 'requires' statements for BEAST2 packages.
 */
public class NameResolver {

    private static final Logger logger = Logger.getLogger(NameResolver.class.getName());

    private final TypeSystem typeSystem = new BeastObjectFactoryImpl();
    private final DependencyManager dependencyManager  = new BeastObjectFactoryImpl();

    private final Map<String, String> explicitImports;
    private final List<String> wildcardImports;
    private final Map<String, String> resolvedCache;
    private final Set<String> processedPackages;

    /**
     * Constructor that initializes empty import collections
     */
    public NameResolver() {
        this.explicitImports = new HashMap<>();
        this.wildcardImports = new ArrayList<>();
        this.resolvedCache = new HashMap<>();
        this.processedPackages = new HashSet<>();
    }

    /**
     * Constructor that initializes with a list of import statements
     *
     * @param imports the list of import statements
     */
    public NameResolver(List<ImportStatement> imports) {
        this();

        if (imports != null) {
            for (ImportStatement importStmt : imports) {
                if (importStmt.isWildcard()) {
                    wildcardImports.add(importStmt.getPackageName());
                } else {
                    String packageName = importStmt.getPackageName();
                    String simpleName = getSimpleName(packageName);
                    explicitImports.put(simpleName, packageName);
                }
            }
        }
    }

    /**
     * Add an explicit import (e.g., "import java.util.List")
     *
     * @param packageName the fully qualified name
     */
    public void addExplicitImport(String packageName) {
        String simpleName = getSimpleName(packageName);
        explicitImports.put(simpleName, packageName);
        logger.fine("Added explicit import: " + simpleName + " → " + packageName);
    }

    /**
     * Add a wildcard import (e.g., "import java.util.*")
     *
     * @param packageName the package name (without ".*")
     */
    public void addWildcardImport(String packageName) {
        wildcardImports.add(packageName);
        logger.fine("Added wildcard import: " + packageName + ".*");
    }

    /**
     * Add all BEASTInterface classes from a required BEAST plugin.
     * This method is called when processing a 'requires' statement.
     *
     * @param pluginName the BEAST2 plugin name to require (e.g., "SNAPP", "ORC")
     */
    public void addRequiredPackage(String pluginName) {

        if (processedPackages.contains(pluginName)) {
            logger.fine("BEAST plugin already processed: " + pluginName);
            return;
        }

        processedPackages.add(pluginName);
        logger.info("Processing required BEAST plugin: " + pluginName);

        PackageUtils.printBEASTInterfacesByPackage();

        // Search for BEASTInterface classes directly in the plugin -- don't forget plugin name must be lowercase for this method!
        List<String> beastClasses = dependencyManager.findModelObjectClasses(pluginName);

        if (!beastClasses.isEmpty()) {
            // Get the unique Java packages from the found classes
            Set<String> javaPackages = new HashSet<>();
            for (String className : beastClasses) {
                int lastDot = className.lastIndexOf('.');
                if (lastDot > 0) {
                    String javaPackage = className.substring(0, lastDot);
                    javaPackages.add(javaPackage);
                }
            }

            // Add wildcard imports for each Java package found
            for (String javaPackage : javaPackages) {
                wildcardImports.add(javaPackage);
                logger.info("Added wildcard import for Java package: " + javaPackage);
            }

            logger.info("Found " + beastClasses.size() + " BEAST2 classes in plugin: " + pluginName);
            return;
        }

        // If no BEASTInterface classes were found, log a warning
        logger.warning("Could not locate any BEASTInterface classes in plugin: " + pluginName);

    }

    /**
     * Extract the simple name from a fully qualified name
     *
     * @param fullyQualifiedName the fully qualified name
     * @return the simple name (part after the last dot)
     */
    private String getSimpleName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        if (lastDot >= 0) {
            return fullyQualifiedName.substring(lastDot + 1);
        }
        return fullyQualifiedName;
    }

    /**
     * Resolve a class name, which could be either simple or fully qualified
     *
     * @param className the class name to resolve
     * @return the fully qualified class name, or the input if it cannot be resolved
     */
    public String resolveClassName(String className) {
        // Handle array types by resolving the component type
        if (className.endsWith("[]")) {
            String componentTypeName = className.substring(0, className.length() - 2);
            String resolvedComponentName = resolveClassName(componentTypeName);
            return resolvedComponentName + "[]";
        }

        // If it already contains dots, assume it's fully qualified
        if (className.contains(".")) {
            return className;
        }

        // Check cache first
        if (resolvedCache.containsKey(className)) {
            return resolvedCache.get(className);
        }

        // Special cases for common built-in Java types
        if ("String".equals(className)) {
            return "java.lang.String";
        } else if ("Integer".equals(className)) {
            return "java.lang.Integer";
        } else if ("Double".equals(className)) {
            return "java.lang.Double";
        } else if ("Boolean".equals(className)) {
            return "java.lang.Boolean";
        }

        // Check explicit imports first
        if (explicitImports.containsKey(className)) {
            String resolved = explicitImports.get(className);
            logger.fine("Resolved " + className + " to " + resolved + " via explicit import");
            resolvedCache.put(className, resolved);
            return resolved;
        }

        // Try wildcard imports - check each possible class but safely
        for (String wildcardPackage : wildcardImports) {
            String qualifiedName = wildcardPackage + "." + className;
            logger.fine("Trying wildcard resolution: " + qualifiedName);

            if (typeSystem.classExists(qualifiedName)) {
                logger.fine("Successfully resolved " + className + " to " + qualifiedName);
                resolvedCache.put(className, qualifiedName);
                return qualifiedName;
            }
        }
        
        // If all else fails, return the original name
        logger.warning("Could not resolve " + className + " - will use unqualified name");

        // Cache the "failure" case too
        resolvedCache.put(className, className);
        return className;
    }
}
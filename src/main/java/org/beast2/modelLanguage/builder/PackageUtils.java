package org.beast2.modelLanguage.builder;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.pkgmgmt.BEASTClassLoader;
import beast.pkgmgmt.Package;
import beast.pkgmgmt.PackageManager;
import beast.pkgmgmt.PackageVersion;
import beast.pkgmgmt.PackageManager.PackageListRetrievalException;
import org.beast2.modelLanguage.builder.util.BEASTUtils;

import java.lang.reflect.Type;
import java.util.*;

/**
 * Utility class for managing BEAST packages
 */
public class PackageUtils {

    /**
     * Get a map of all packages (both installed and available)
     */
    public static Map<String, Package> getAllPackages() {
        Map<String, Package> packageMap = new TreeMap<>(PackageManager::comparePackageNames);

        // Add installed packages
        PackageManager.addInstalledPackages(packageMap);

        // Try to add available packages
        try {
            PackageManager.addAvailablePackages(packageMap);
        } catch (PackageListRetrievalException e) {
            // Unable to retrieve available packages, just continue with what we have
        }

        return packageMap;
    }

    /**
     * Get a map of installed packages
     */
    public static Map<String, Package> getInstalledPackages() {
        Map<String, Package> allPackages = getAllPackages();
        Map<String, Package> installedPackages = new TreeMap<>(PackageManager::comparePackageNames);

        for (Map.Entry<String, Package> entry : allPackages.entrySet()) {
            if (entry.getValue().isInstalled()) {
                installedPackages.put(entry.getKey(), entry.getValue());
            }
        }

        return installedPackages;
    }

    /**
     * Get a map of packages with available updates
     */
    public static Map<String, Package> getPackagesWithUpdates() {
        Map<String, Package> allPackages = getAllPackages();
        Map<String, Package> updatablePackages = new TreeMap<>(PackageManager::comparePackageNames);

        for (Map.Entry<String, Package> entry : allPackages.entrySet()) {
            Package pkg = entry.getValue();
            if (pkg.isInstalled() && pkg.newVersionAvailable()) {
                updatablePackages.put(entry.getKey(), pkg);
            }
        }

        return updatablePackages;
    }

    /**
     * Check if a specific package is installed
     */
    public static boolean isPackageInstalled(String packageName) {
        packageName = packageName.toLowerCase();
        Map<String, Package> packages = getInstalledPackages();
        return packages.containsKey(packageName);
    }

    /**
     * Get the installed version of a package
     */
    public static PackageVersion getInstalledVersion(String packageName) {
        packageName = packageName.toLowerCase();
        Map<String, Package> packages = getInstalledPackages();
        return packages.containsKey(packageName) ? packages.get(packageName).getInstalledVersion() : null;
    }

    /**
     * Get the latest available version of a package
     */
    public static PackageVersion getLatestVersion(String packageName) {

        packageName = packageName.toLowerCase();
        Map<String, Package> packages = getAllPackages();
        return packages.containsKey(packageName) ? packages.get(packageName).getLatestVersion() : null;
    }

    /**
     * Prints all BEASTInterface classes organized by package with their Input signatures
     */
    public static void printBEASTInterfacesByPackage() {
        try {
            // Make sure external jars are loaded
            PackageManager.loadExternalJars();

            // Get all installed packages
            Map<String, Package> packages = getInstalledPackages();

            System.out.println("BEASTInterface implementations by package:");
            System.out.println("==========================================");

            // For each installed BEAST2 package, find its BEASTInterface classes
            for (Map.Entry<String, Package> entry : packages.entrySet()) {
                String beast2PackageName = entry.getKey();
                Package pkg = entry.getValue();

                try {
                    // Use PackageManager.find with the BEAST2 package name
                    List<String> beastClasses = PackageManager.find(
                            beast.base.core.BEASTInterface.class,
                            beast2PackageName.toLowerCase()
                    );

                    if (!beastClasses.isEmpty()) {
                        System.out.println("\nPackage: " + beast2PackageName);
                        System.out.println("Version: " + pkg.getInstalledVersion());
                        System.out.println("Classes (" + beastClasses.size() + "):");

                        Collections.sort(beastClasses);
                        for (String className : beastClasses) {
                            String signature = getClassSignature(className);
                            System.out.println("  " + className + signature);
                        }
                    } else {
                        System.out.println("\nPackage: " + beast2PackageName);
                        System.out.println("Version: " + pkg.getInstalledVersion());
                        System.out.println("  No BEASTInterfaces");
                    }

                } catch (Exception e) {
                    System.err.println("Error finding classes in package " + beast2PackageName + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("Error discovering BEASTInterface classes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get BEASTInterface classes for a specific BEAST2 package
     */
    public static List<String> getBEASTInterfacesForPackage(String beast2PackageName) {
        try {
            PackageManager.loadExternalJars();

            List<String> beastClasses = PackageManager.find(
                    beast.base.core.BEASTInterface.class,
                    beast2PackageName
            );

            Collections.sort(beastClasses);
            return beastClasses;

        } catch (Exception e) {
            System.err.println("Error getting BEASTInterface classes for package " + beast2PackageName + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Generate a signature for a BEASTInterface class based on its Input fields
     */
    private static String getClassSignature(String className) {
        try {
            Class<?> clazz = BEASTClassLoader.forName(className);

            // Create a dummy instance to use BEASTUtils methods
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (!(instance instanceof BEASTInterface)) {
                return " (not a BEASTInterface)";
            }

            BEASTInterface beastObject = (BEASTInterface) instance;

            // Use BEASTUtils to build the input map
            Map<String, Input<?>> inputMap = BEASTUtils.buildInputMap(instance, clazz);

            if (inputMap.isEmpty()) {
                return "";
            }

            List<String> inputSignatures = new ArrayList<>();

            for (Map.Entry<String, Input<?>> entry : inputMap.entrySet()) {
                String inputName = entry.getKey();
                Input<?> input = entry.getValue();

                // Use BEASTUtils to get the expected type
                Type expectedType = BEASTUtils.getInputExpectedType(input, beastObject, inputName);
                String typeSignature = getSimpleTypeName(expectedType);

                inputSignatures.add(inputName + ": " + typeSignature);
            }

            Collections.sort(inputSignatures);
            return " (" + String.join(", ", inputSignatures) + ")";

        } catch (Exception e) {
            return " (error: " + e.getMessage() + ")";
        }
    }

    /**
     * Convert a Type to a simple, readable type name
     */
    private static String getSimpleTypeName(Type type) {
        if (type == null) {
            return "Object";
        }

        String typeName = type.getTypeName();

        // Handle generic types like List<String>
        if (typeName.contains("<")) {
            // Extract the main type and first generic parameter
            String mainType = typeName.substring(0, typeName.indexOf("<"));
            String genericPart = typeName.substring(typeName.indexOf("<") + 1, typeName.lastIndexOf(">"));

            // Simplify both parts
            mainType = simplifyClassName(mainType);
            genericPart = simplifyClassName(genericPart);

            return mainType + "<" + genericPart + ">";
        }

        return simplifyClassName(typeName);
    }

    /**
     * Simplify a class name by removing package prefixes
     */
    private static String simplifyClassName(String className) {
        if (className == null || className.isEmpty()) {
            return "Object";
        }

        // Handle array types
        if (className.endsWith("[]")) {
            String baseType = className.substring(0, className.length() - 2);
            return simplifyClassName(baseType) + "[]";
        }

        // Remove package names
        if (className.contains(".")) {
            className = className.substring(className.lastIndexOf(".") + 1);
        }

        return className;
    }
}

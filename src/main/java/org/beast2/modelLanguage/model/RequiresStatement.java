package org.beast2.modelLanguage.model;

/**
 * Represents a 'requires' statement in a Beast2 model which imports
 * all BEASTInterface classes from a specified BEAST2 package.
 */
public class RequiresStatement {
    private final String packageName;

    /**
     * Constructor for a requires statement
     *
     * @param packageName the name of the BEAST2 package to import
     */
    public RequiresStatement(String packageName) {
        this.packageName = packageName;
    }

    /**
     * Get the package name being imported
     *
     * @return the package name
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * Create a string representation of this requires statement
     */
    @Override
    public String toString() {
        return "requires " + packageName + ";";
    }
}
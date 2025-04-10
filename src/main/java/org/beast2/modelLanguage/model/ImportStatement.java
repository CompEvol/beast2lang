package org.beast2.modelLanguage.model;

/**
 * Represents an import statement in a Beast2 model.
 */
public class ImportStatement {
    private final String packageName;
    private final boolean isWildcard;
    
    /**
     * Constructor for an import statement
     * 
     * @param packageName the package or class name to import
     * @param isWildcard true if this is a wildcard import (ends with .*)
     */
    public ImportStatement(String packageName, boolean isWildcard) {
        this.packageName = packageName;
        this.isWildcard = isWildcard;
    }
    
    /**
     * Get the package or class name being imported
     * 
     * @return the package or class name
     */
    public String getPackageName() {
        return packageName;
    }
    
    /**
     * Check if this is a wildcard import
     * 
     * @return true if this is a wildcard import (ends with .*)
     */
    public boolean isWildcard() {
        return isWildcard;
    }
    
    /**
     * Create a string representation of this import statement
     */
    @Override
    public String toString() {
        return "import " + packageName + (isWildcard ? ".*" : "") + ";";
    }
}
package org.beast2.modelLanguage.model;

/**
 * Represents a 'requires' statement in a Beast2 model which imports
 * all BEASTInterface classes from a specified BEAST2 package.
 */
public class RequiresStatement {
    private final String pluginName;

    /**
     * Constructor for a requires statement
     *
     * @param pluginName the name of the BEAST2 package to import
     */
    public RequiresStatement(String pluginName) {
        this.pluginName = pluginName;
    }

    /**
     * Get the package name being imported
     *
     * @return the package name
     */
    public String getPluginName() {
        return pluginName;
    }

    /**
     * Create a string representation of this requires statement
     */
    @Override
    public String toString() {
        return "requires " + pluginName + ";";
    }
}
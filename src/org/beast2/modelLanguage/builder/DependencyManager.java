package org.beast2.modelLanguage.builder;

import java.util.List;
import java.util.Map;

/**
 * Interface for package discovery and management.
 */
public interface DependencyManager {
    /**
     * Get all available plugins (both installed and available).
     */
    Map<String, Object> getAllPlugins();

    /**
     * Find all model object classes in a package.
     */
    List<String> findModelObjectClasses(String pluginName);
}
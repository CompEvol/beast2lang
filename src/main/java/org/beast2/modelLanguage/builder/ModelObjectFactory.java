package org.beast2.modelLanguage.builder;

/**
 * Composite interface that combines all factory capabilities.
 * This can be used where the full factory functionality is needed.
 */
public interface ModelObjectFactory extends
        ObjectCreator,
        TypeSystem,
        InputManager,
        ParameterFactory,
        AlignmentFactory,
        TypeConverter,
        DistributionManager,
        DependencyManager {
    // This interface combines all the factory interfaces
    // No additional methods needed
}
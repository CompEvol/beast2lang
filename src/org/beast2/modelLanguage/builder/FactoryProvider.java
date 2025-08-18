package org.beast2.modelLanguage.builder;

public class FactoryProvider {
    private static ModelObjectFactory instance;
    private static final Object lock = new Object();

    public static void setFactory(ModelObjectFactory factory) {
        synchronized (lock) {
            if (instance != null) {
                throw new IllegalStateException("Factory already initialized");
            }
            instance = factory;
        }
    }

    public static ModelObjectFactory getFactory() {
        synchronized (lock) {
            if (instance == null) {
                // Auto-initialize with default implementation
                // This makes migration easier
                try {
                    Class<?> implClass = Class.forName(
                            "org.beast2.modelLanguage.beast.BeastObjectFactory"
                    );
                    instance = (ModelObjectFactory) implClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new IllegalStateException("Factory not initialized and default not available", e);
                }
            }
            return instance;
        }
    }

    // Convenience methods for specific interfaces
    public static TypeSystem getTypeSystem() {
        return getFactory();
    }

    public static DependencyManager getDependencyManager() {
        return getFactory();
    }

    // For testing - package private
    static void reset() {
        synchronized (lock) {
            instance = null;
        }
    }
}
package org.beast2.modelLanguage.schema.core;

import beast.base.core.Description;

/**
 * Information about a component discovered during scanning
 */
public class ComponentInfo {
    private final Class<?> clazz;
    private final String packageName;
    private final boolean isDistribution;
    private final boolean isAbstract;
    private final boolean isInterface;
    private final boolean isEnum;
    private final String description;
    
    public ComponentInfo(Class<?> clazz, String packageName) {
        this.clazz = clazz;
        this.packageName = packageName;
        this.isDistribution = determineIfDistribution(clazz);
        this.isAbstract = java.lang.reflect.Modifier.isAbstract(clazz.getModifiers());
        this.isInterface = clazz.isInterface();
        this.isEnum = clazz.isEnum();
        this.description = generateDescription(clazz);
    }
    
    private boolean determineIfDistribution(Class<?> clazz) {
        try {
            Class<?> distributionClass = Class.forName("beast.base.inference.Distribution");
            Class<?> parametricDistClass = Class.forName("beast.base.inference.distribution.ParametricDistribution");
            return distributionClass.isAssignableFrom(clazz) || 
                   parametricDistClass.isAssignableFrom(clazz);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private String generateDescription(Class<?> clazz) {
        // First check for @Description annotation
        if (clazz.isAnnotationPresent(Description.class)) {
            Description desc = clazz.getAnnotation(Description.class);
            return desc.value();
        }

        // Fallback to generated descriptions
        if (isEnum) {
            return "Enum type for " + (clazz.getDeclaringClass() != null ?
                    clazz.getDeclaringClass().getSimpleName() : clazz.getSimpleName());
        } else if (clazz.isMemberClass()) {
            return "Inner type of " + clazz.getDeclaringClass().getSimpleName();
        } else {
            return "BEAST2 " + clazz.getSimpleName();
        }
    }
    // Getters
    public Class<?> getClazz() { return clazz; }
    public String getPackageName() { return packageName; }
    public boolean isDistribution() { return isDistribution; }
    public boolean isAbstract() { return isAbstract; }
    public boolean isInterface() { return isInterface; }
    public boolean isEnum() { return isEnum; }
    public String getDescription() { return description; }
    public String getClassName() { return clazz.getName(); }
    public String getSimpleName() { return clazz.getSimpleName(); }
}
package org.beast2.modelLanguage.schema.scanner;

import beast.base.core.BEASTInterface;
import beast.pkgmgmt.Package;
import org.beast2.modelLanguage.beast.BeastObjectFactory;
import org.beast2.modelLanguage.schema.core.ComponentInfo;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Scans for BEAST2 components in packages and specific types
 */
public class ComponentScanner {
    private static final Logger logger = Logger.getLogger(ComponentScanner.class.getName());
    
    private final BeastObjectFactory factory;
    private final ComponentFilter filter;
    
    public ComponentScanner(BeastObjectFactory factory, ComponentFilter filter) {
        this.factory = factory;
        this.filter = filter;
    }
    
    /**
     * Scan all packages for components
     */
    public List<ComponentInfo> scanPackages(Map<String, Package> packages) {
        List<ComponentInfo> components = new ArrayList<>();
        
        for (Map.Entry<String, Package> entry : packages.entrySet()) {
            String packageName = entry.getKey();
            Package pkg = entry.getValue();
            
            List<String> classes = factory.findModelObjectClasses(pkg.getName());
            
            for (String className : classes) {
                try {
                    Class<?> clazz = factory.loadClass(className);
                    
                    if (BEASTInterface.class.isAssignableFrom(clazz) && filter.isModelClass(clazz)) {
                        components.add(new ComponentInfo(clazz, pkg.getName()));
                        
                        // Also scan for public inner BEASTInterfaces
                        components.addAll(scanInnerBEASTInterfaces(clazz, pkg.getName()));
                    }
                } catch (Exception e) {
                    logger.fine("Could not process class: " + className + " - " + e.getMessage());
                }
            }
        }
        
        return components;
    }
    
    /**
     * Scan specific important types
     */
    public List<ComponentInfo> scanImportantTypes(String[] typeNames, String defaultPackage) {
        List<ComponentInfo> components = new ArrayList<>();
        
        for (String className : typeNames) {
            try {
                Class<?> clazz = factory.loadClass(className);
                
                if (shouldIncludeType(clazz)) {
                    String packageName = determinePackageName(clazz, defaultPackage);
                    components.add(new ComponentInfo(clazz, packageName));
                    logger.info("Added important type: " + clazz.getSimpleName());
                    
                    // Also scan inner classes for BEASTInterface types
                    if (BEASTInterface.class.isAssignableFrom(clazz)) {
                        components.addAll(scanInnerBEASTInterfaces(clazz, packageName));
                    }
                }
            } catch (Exception e) {
                logger.warning("Could not load important type: " + className + " - " + e.getMessage());
            }
        }
        
        return components;
    }
    
    /**
     * Scan for enum types
     */
    public List<ComponentInfo> scanEnums(String[] enumNames) {
        List<ComponentInfo> components = new ArrayList<>();
        
        for (String enumName : enumNames) {
            try {
                Class<?> enumClass = factory.loadClass(enumName);
                if (enumClass.isEnum()) {
                    String packageName = enumClass.getPackage() != null ? 
                                       enumClass.getPackage().getName() : "";
                    components.add(new ComponentInfo(enumClass, packageName));
                    logger.info("Added enum: " + getSimpleClassName(enumClass));
                }
            } catch (Exception e) {
                logger.warning("Could not load enum: " + enumName + " - " + e.getMessage());
            }
        }
        
        return components;
    }
    
    /**
     * Scan for inner types referenced in components
     */
    public List<ComponentInfo> scanReferencedInnerTypes(Set<String> innerTypeNames, 
                                                       String[] packagePrefixes) {
        List<ComponentInfo> components = new ArrayList<>();
        
        for (String innerTypeName : innerTypeNames) {
            ComponentInfo found = findInnerType(innerTypeName, packagePrefixes);
            if (found != null) {
                components.add(found);
            }
        }
        
        return components;
    }
    
    /**
     * Scan for public inner classes that are BEASTInterfaces
     */
    private List<ComponentInfo> scanInnerBEASTInterfaces(Class<?> outerClass, String packageName) {
        List<ComponentInfo> components = new ArrayList<>();
        
        try {
            Class<?>[] innerClasses = outerClass.getDeclaredClasses();
            
            for (Class<?> innerClass : innerClasses) {
                if (Modifier.isPublic(innerClass.getModifiers()) &&
                    BEASTInterface.class.isAssignableFrom(innerClass) &&
                    filter.isModelClass(innerClass)) {
                    
                    components.add(new ComponentInfo(innerClass, packageName));
                    logger.info("Added public inner BEASTInterface: " + getSimpleClassName(innerClass));
                }
            }
        } catch (Exception e) {
            logger.fine("Error checking inner classes for " + outerClass.getName() + ": " + e.getMessage());
        }
        
        return components;
    }
    
    /**
     * Determine if a type should be included
     */
    private boolean shouldIncludeType(Class<?> clazz) {
        if (BEASTInterface.class.isAssignableFrom(clazz)) {
            return filter.isModelClass(clazz);
        }
        // For non-BEASTInterface types (like Function, Parameter), include them
        return true;
    }
    
    /**
     * Determine package name for a class
     */
    private String determinePackageName(Class<?> clazz, String defaultPackage) {
        if (clazz.getPackage() != null) {
            return clazz.getPackage().getName();
        }
        return defaultPackage;
    }
    
    /**
     * Try to find an inner type with various package prefixes
     */
    private ComponentInfo findInnerType(String innerTypeName, String[] packagePrefixes) {
        // Convert inner class notation to proper class name
        String className = innerTypeName.replace('.', '$');
        
        // Try direct loading first
        try {
            Class<?> innerClass = factory.loadClass(className);
            return createComponentInfoForInnerType(innerClass);
        } catch (ClassNotFoundException e) {
            // Try with package prefixes
        }
        
        // Try with common package prefixes
        for (String prefix : packagePrefixes) {
            try {
                Class<?> innerClass = factory.loadClass(prefix + className);
                return createComponentInfoForInnerType(innerClass);
            } catch (ClassNotFoundException e) {
                // Continue trying
            }
        }
        
        logger.fine("Could not find inner type: " + innerTypeName);
        return null;
    }
    
    /**
     * Create ComponentInfo for an inner type
     */
    private ComponentInfo createComponentInfoForInnerType(Class<?> innerClass) {
        String packageName = "";
        if (innerClass.getDeclaringClass() != null) {
            java.lang.Package pkg = innerClass.getDeclaringClass().getPackage();
            if (pkg != null) {
                packageName = pkg.getName();
            }
        }
        return new ComponentInfo(innerClass, packageName);
    }
    
    /**
     * Get simple class name handling inner classes
     */
    private String getSimpleClassName(Class<?> clazz) {
        if (clazz.isMemberClass()) {
            return clazz.getDeclaringClass().getSimpleName() + "." + clazz.getSimpleName();
        }
        return clazz.getSimpleName();
    }
}
package org.beast2.modelLanguage.builder.util;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.evolution.alignment.Taxon;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Registry of autoboxing rules for BEAST2Lang.
 * This allows automatic conversion between compatible types.
 */
public class AutoboxingRegistry {
    private static final Logger logger = Logger.getLogger(AutoboxingRegistry.class.getName());

    // Singleton instance
    private static AutoboxingRegistry instance;

    // Registry of autoboxing rules
    private final List<AutoboxingRule> rules = new ArrayList<>();

    /**
     * Get the singleton instance
     */
    public static synchronized AutoboxingRegistry getInstance() {
        if (instance == null) {
            instance = new AutoboxingRegistry();
            instance.initializeDefaultRules();
        }
        return instance;
    }

    /**
     * Private constructor to enforce singleton pattern
     */
    private AutoboxingRegistry() {
        // Private constructor
    }

    /**
     * Initialize default autoboxing rules
     */
    private void initializeDefaultRules() {
        // Rule 1: Literal to Parameter autoboxing
        addRule(new LiteralToParameterRule());

        // Rule 2: ParametricDistribution to Prior autoboxing
        addRule(new ParametricDistributionToPriorRule());

        // Rule 3: SubstitutionModel to SiteModel autoboxing
        addRule(new SubstitutionModelToSiteModelRule());

        // Rule 4: Alignment to TaxonSet autoboxing
        addRule(new AlignmentToTaxonSetRule());
    }

    /**
     * Add an autoboxing rule to the registry
     */
    public void addRule(AutoboxingRule rule) {
        rules.add(rule);
    }

    /**
     * Attempt to autobox a value based on the target Type
     */
    public Object autobox(Object value, Type targetType, Map<String, Object> objectRegistry) {
        if (value == null || targetType == null) {
            return value;
        }

        // Try each rule in order
        for (AutoboxingRule rule : rules) {
            if (rule.canAutobox(value, targetType)) {
                try {
                    Object result = rule.autobox(value, targetType, objectRegistry);
                    if (result != null) {
                        logger.info("Autoboxed " + value.getClass().getSimpleName() +
                                " to " + result.getClass().getSimpleName() +
                                " using " + rule.getClass().getSimpleName());
                        return result;
                    }
                } catch (Exception e) {
                    logger.warning("Error during autoboxing with " + rule.getClass().getSimpleName() +
                            ": " + e.getMessage());
                }
            }
        }

        // Return original value if no autoboxing could be applied
        return value;
    }

    /**
     * Interface for autoboxing rules with Type information
     */
    public interface AutoboxingRule {
        /**
         * Check if this rule can autobox the given value to the target type
         */
        boolean canAutobox(Object value, Type targetType);

        /**
         * Perform the autoboxing
         */
        Object autobox(Object value, Type targetType, Map<String, Object> objectRegistry) throws Exception;
    }

    /**
     * Helper methods for working with Types
     */
    public static class TypeUtils {
        /**
         * Find the field in the owner class that corresponds to this Input
         */
        public static Field findInputField(Input<?> input, Object owner) {
            if (input == null || owner == null) {
                return null;
            }

            String inputName = input.getName();
            Class<?> ownerClass = owner.getClass();

            // Search in this class and all superclasses
            while (ownerClass != null) {
                try {
                    for (Field field : ownerClass.getDeclaredFields()) {
                        field.setAccessible(true);

                        // Check if this is an Input field
                        if (Input.class.isAssignableFrom(field.getType())) {
                            try {
                                // Get the Input object from this field
                                Input<?> fieldInput = (Input<?>) field.get(owner);

                                // Check if it's the one we're looking for
                                if (fieldInput == input ||
                                        (fieldInput != null && fieldInput.getName().equals(inputName))) {
                                    return field;
                                }
                            } catch (IllegalAccessException e) {
                                // Continue to next field
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.fine("Error examining fields: " + e.getMessage());
                }

                // Move up to superclass
                ownerClass = ownerClass.getSuperclass();
            }

            return null;
        }

        /**
         * Get the Type for an Input using reflection
         */
        public static Type getInputType(Input<?> input, Object owner) {
            Field field = findInputField(input, owner);
            if (field == null) {
                return null;
            }

            // Get the generic type
            return field.getGenericType();
        }

        /**
         * Get the raw Class from a Type
         */
        public static Class<?> getRawType(Type type) {
            if (type instanceof Class) {
                return (Class<?>) type;
            } else if (type instanceof ParameterizedType) {
                return (Class<?>) ((ParameterizedType) type).getRawType();
            } else if (type instanceof GenericArrayType) {
                Type componentType = ((GenericArrayType) type).getGenericComponentType();
                Class<?> componentClass = getRawType(componentType);
                if (componentClass != null) {
                    return Array.newInstance(componentClass, 0).getClass();
                }
            } else if (type instanceof TypeVariable) {
                Type[] bounds = ((TypeVariable<?>) type).getBounds();
                if (bounds != null && bounds.length > 0) {
                    return getRawType(bounds[0]);
                }
            } else if (type instanceof WildcardType) {
                Type[] upperBounds = ((WildcardType) type).getUpperBounds();
                if (upperBounds != null && upperBounds.length > 0) {
                    return getRawType(upperBounds[0]);
                }
            }
            return null;
        }

        /**
         * Check if a type represents a Collection
         */
        public static boolean isCollection(Type type) {
            Class<?> rawType = getRawType(type);
            return rawType != null && Collection.class.isAssignableFrom(rawType);
        }

        /**
         * Get the element type for a Collection
         */
        public static Type getCollectionElementType(Type collectionType) {
            if (collectionType instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType) collectionType;
                Type[] typeArgs = paramType.getActualTypeArguments();
                if (typeArgs.length > 0) {
                    return typeArgs[0];
                }
            }
            return null;
        }

        /**
         * Check if a type is, extends, or implements another type
         */
        public static boolean isAssignableFrom(Type baseType, Type testType) {
            Class<?> baseClass = getRawType(baseType);
            Class<?> testClass = getRawType(testType);

            if (baseClass == null || testClass == null) {
                return false;
            }

            return baseClass.isAssignableFrom(testClass);
        }
    }

    /**
     * Rule for autoboxing literal values to Parameter objects
     */
    public static class LiteralToParameterRule implements AutoboxingRule {
        @Override
        public boolean canAutobox(Object value, Type targetType) {
            System.out.println("LiteralToParameterRule checking: value=" + value +
                    " (" + (value != null ? value.getClass().getName() : "null") + "), " +
                    "targetType=" + targetType);

            if (!(value instanceof Number || value instanceof Boolean || value instanceof String)) {
                return false;
            }

            Class<?> targetClass = TypeUtils.getRawType(targetType);
            if (targetClass == null) {
                return false;
            }

            boolean result = isParameterType(targetClass);
            System.out.println("LiteralToParameterRule result: " + result +
                    " for targetClass=" + targetClass.getName());
            return result;
        }

        @Override
        public Object autobox(Object value, Type targetType, Map<String, Object> objectRegistry) throws Exception {
            Class<?> targetClass = TypeUtils.getRawType(targetType);
            return BEASTUtils.createParameterForType(value, targetClass);
        }

        private boolean isParameterType(Class<?> type) {
            try {
                Class<?> parameterClass = Class.forName("beast.base.inference.parameter.Parameter");
                // Check if Parameter is assignable to the target type
                return type.isAssignableFrom(parameterClass);
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
    }

    /**
     * Rule for autoboxing ParametricDistribution to Prior
     */
    public static class ParametricDistributionToPriorRule implements AutoboxingRule {
        @Override
        public boolean canAutobox(Object value, Type targetType) {
            try {
                Class<?> priorClass = Class.forName("beast.base.inference.distribution.Prior");
                Class<?> paramDistClass = Class.forName("beast.base.inference.distribution.ParametricDistribution");

                Class<?> targetClass = TypeUtils.getRawType(targetType);
                if (targetClass == null) {
                    return false;
                }

                return paramDistClass.isAssignableFrom(value.getClass()) &&
                        priorClass.isAssignableFrom(targetClass);
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        @Override
        public Object autobox(Object value, Type targetType, Map<String, Object> objectRegistry) throws Exception {
            // Create Prior
            Class<?> priorClass = Class.forName("beast.base.inference.distribution.Prior");
            Object prior = priorClass.getDeclaredConstructor().newInstance();

            // Set distribution
            BEASTInterface priorObj = (BEASTInterface) prior;
            Input<?> distrInput = priorObj.getInput("distr");

            if (distrInput != null) {
                BEASTUtils.setInputValue(distrInput, value, priorObj);
                BEASTUtils.callInitAndValidate(prior, priorClass);
                return prior;
            }

            return null;
        }
    }

    /**
     * Rule for autoboxing SubstitutionModel to SiteModel
     */
    public static class SubstitutionModelToSiteModelRule implements AutoboxingRule {
        @Override
        public boolean canAutobox(Object value, Type targetType) {
            try {
                Class<?> siteModelInterfaceClass = Class.forName("beast.base.evolution.sitemodel.SiteModelInterface");
                Class<?> substModelClass = Class.forName("beast.base.evolution.substitutionmodel.SubstitutionModel");

                Class<?> targetClass = TypeUtils.getRawType(targetType);
                if (targetClass == null) {
                    return false;
                }

                return substModelClass.isAssignableFrom(value.getClass()) &&
                        siteModelInterfaceClass.isAssignableFrom(targetClass);
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        @Override
        public Object autobox(Object value, Type targetType, Map<String, Object> objectRegistry) throws Exception {
            // Create SiteModel
            Class<?> siteModelClass = Class.forName("beast.base.evolution.sitemodel.SiteModel");
            Object siteModel = siteModelClass.getDeclaredConstructor().newInstance();

            // Set substitution model
            BEASTInterface siteModelObj = (BEASTInterface) siteModel;
            Input<?> substModelInput = siteModelObj.getInput("substModel");

            if (substModelInput != null) {
                BEASTUtils.setInputValue(substModelInput, value, siteModelObj);
                BEASTUtils.callInitAndValidate(siteModel, siteModelClass);
                return siteModel;
            }

            return null;
        }
    }

    /**
     * Rule for autoboxing Alignment to TaxonSet
     */
    public static class AlignmentToTaxonSetRule implements AutoboxingRule {
        @Override
        public boolean canAutobox(Object value, Type targetType) {
            try {
                Class<?> taxonSetClass = Class.forName("beast.base.evolution.alignment.TaxonSet");
                Class<?> alignmentClass = Class.forName("beast.base.evolution.alignment.Alignment");

                Class<?> targetClass = TypeUtils.getRawType(targetType);
                if (targetClass == null) {
                    return false;
                }

                return alignmentClass.isAssignableFrom(value.getClass()) &&
                        taxonSetClass.isAssignableFrom(targetClass);
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        @Override
        public Object autobox(Object value, Type targetType, Map<String, Object> objectRegistry) throws Exception {
            // Create TaxonSet
            Class<?> taxonSetClass = Class.forName("beast.base.evolution.alignment.TaxonSet");
            Object taxonSet = taxonSetClass.getDeclaredConstructor().newInstance();

            // Set alignment
            BEASTInterface taxonSetObj = (BEASTInterface) taxonSet;
            Input<?> alignmentInput = taxonSetObj.getInput("alignment");

            if (alignmentInput != null) {
                BEASTUtils.setInputValue(alignmentInput, value, taxonSetObj);
                BEASTUtils.callInitAndValidate(taxonSet, taxonSetClass);
                return taxonSet;
            }

            return null;
        }
    }

}
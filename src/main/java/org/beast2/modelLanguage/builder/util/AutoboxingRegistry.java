package org.beast2.modelLanguage.builder.util;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.evolution.alignment.Taxon;
import beast.base.inference.parameter.RealParameter;

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
     * Check if a value is directly assignable to a target type without needing autoboxing
     */
    public static boolean isDirectlyAssignable(Object value, Type targetType) {
        if (value == null || targetType == null) {
            return true; // null is assignable to any type
        }

        Class<?> valueClass = value.getClass();
        Class<?> targetClass = TypeUtils.getRawType(targetType);

        if (targetClass == null) {
            return false;
        }

        return targetClass.isAssignableFrom(valueClass);
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
        addRule(new ArrayToListAutoboxingRule());
        addRule(new TreeToTreeIntervalsRule());

        addRule(new LiteralToParameterRule());
        addRule(new ParametricDistributionToPriorRule());
        addRule(new SubstitutionModelToSiteModelRule());
        addRule(new AlignmentToTaxonSetRule());
        addRule(new StringArrayToTaxonListRule());
        addRule(new DoubleArrayToRealParameterRule());
        addRule(new RealParameterToFrequenciesRule());
        addRule(new DoubleArrayToFrequenciesRule());
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

        // Check if the value is already assignable to the target type
        if (isDirectlyAssignable(value, targetType)) {
            return value; // No autoboxing needed
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
            Logger logger = Logger.getLogger(TypeUtils.class.getName());
            logger.info("TypeUtils.isCollection: Checking if " + type + " is a Collection");

            Class<?> rawType = getRawType(type);
            boolean result = rawType != null && Collection.class.isAssignableFrom(rawType);

            logger.info("TypeUtils.isCollection: Raw type is " +
                    (rawType != null ? rawType.getName() : "null") +
                    ", result: " + result);

            return result;
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
     * Rule for autoboxing any Array to a corresponding List
     */
    public static class ArrayToListAutoboxingRule implements AutoboxingRule {
        @Override
        public boolean canAutobox(Object value, Type targetType) {
            // Check if value is an array
            if (value == null || !value.getClass().isArray()) {
                logger.fine("Not an array: " + (value != null ? value.getClass().getName() : "null"));
                return false;
            }

            // Check if target is a Collection
            if (!TypeUtils.isCollection(targetType)) {
                logger.fine("Target is not a Collection: " + targetType);
                return false;
            }

            logger.info("ArrayToListAutoboxingRule checking: array of " +
                    value.getClass().getComponentType().getName() +
                    " to " + targetType);

            // Get element type of the collection
            Type targetElementType = TypeUtils.getCollectionElementType(targetType);
            if (targetElementType == null) {
                logger.fine("Could not determine collection element type for " + targetType);
                return false;
            }

            logger.info("Collection element type: " + targetElementType);

            // Get element type of the array
            Class<?> arrayElementType = value.getClass().getComponentType();

            // Check if array element type is assignable to collection element type
            Class<?> targetElementClass = TypeUtils.getRawType(targetElementType);

            // Return true if array elements can be directly assigned to the list
            if (targetElementClass != null && targetElementClass.isAssignableFrom(arrayElementType)) {
                logger.info("Direct assignment possible: " + arrayElementType.getName() +
                        " to " + targetElementClass.getName());
                return true;
            }

            // Check for autoboxing rules for array elements
            if (Array.getLength(value) > 0) {
                for (AutoboxingRule rule : getInstance().rules) {
                    if (rule != this && rule.canAutobox(Array.get(value, 0), targetElementType)) {
                        logger.info("Element autoboxing possible with rule: " +
                                rule.getClass().getSimpleName());
                        return true;
                    }
                }
            }

            logger.fine("Cannot autobox array to collection");
            return false;
        }

        @Override
        public Object autobox(Object value, Type targetType, Map<String, Object> objectRegistry) throws Exception {
            if (!value.getClass().isArray()) {
                throw new IllegalArgumentException("Expected an array but got " + value.getClass().getName());
            }

            int length = Array.getLength(value);
            List<Object> result = new ArrayList<>(length);

            // Get element type of the collection
            Type targetElementType = TypeUtils.getCollectionElementType(targetType);
            Class<?> targetElementClass = TypeUtils.getRawType(targetElementType);

            // Get component type of the array
            Class<?> arrayComponentType = value.getClass().getComponentType();

            // Check if we need to autobox individual elements
            boolean needsElementAutoboxing = targetElementClass != null &&
                    !targetElementClass.isAssignableFrom(arrayComponentType);

            // Convert each array element to appropriate list element
            for (int i = 0; i < length; i++) {
                Object element = Array.get(value, i);

                // If needed, try to autobox the individual element
                if (needsElementAutoboxing) {
                    element = getInstance().autobox(element, targetElementType, objectRegistry);
                }

                result.add(element);
            }

            return result;
        }
    }

    /**
     * Rule for autoboxing String[] to List<Taxon>
     */
    public static class StringArrayToTaxonListRule implements AutoboxingRule {
        @Override
        public boolean canAutobox(Object value, Type targetType) {
            // Check if value is a String array
            if (!(value instanceof String[])) {
                return false;
            }

            // Check if target is a Collection
            if (!TypeUtils.isCollection(targetType)) {
                return false;
            }

            // Get element type of the collection
            Type elementType = TypeUtils.getCollectionElementType(targetType);
            if (elementType == null) {
                return false;
            }

            // Check if element type is Taxon
            Class<?> elementClass = TypeUtils.getRawType(elementType);
            return elementClass != null && Taxon.class.isAssignableFrom(elementClass);
        }

        @Override
        public Object autobox(Object value, Type targetType, Map<String, Object> objectRegistry) throws Exception {
            String[] taxonNames = (String[]) value;
            List<Taxon> taxonList = new ArrayList<>();

            // Convert each string to a Taxon object
            for (String name : taxonNames) {
                Taxon taxon = new Taxon(name);
                taxonList.add(taxon);
            }

            return taxonList;
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
        public Object autobox(Object value, Type targetType, Map<String, Object> objectRegistry) {
            Class<?> targetClass = TypeUtils.getRawType(targetType);
            return BEASTUtils.createParameterForType(value, targetClass);
        }

        private boolean isParameterType(Class<?> type) {
            return type.isAssignableFrom(beast.base.inference.parameter.Parameter.class);
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

    /**
     * Enhanced rule for autoboxing Double[] to RealParameter or Function
     */
    public static class DoubleArrayToRealParameterRule implements AutoboxingRule {
        @Override
        public boolean canAutobox(Object value, Type targetType) {
            // Check if value is a Double array
            if (!(value instanceof Double[])) {
                return false;
            }

            // Check if target is a RealParameter or Function
            Class<?> targetClass = TypeUtils.getRawType(targetType);
            if (targetClass == null) {
                return false;
            }

            try {
                Class<?> realParamClass = Class.forName("beast.base.inference.parameter.RealParameter");
                Class<?> functionClass = Class.forName("beast.base.core.Function");

                // Return true if target is either RealParameter or Function
                return realParamClass.isAssignableFrom(targetClass) ||
                        functionClass.isAssignableFrom(targetClass);
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        @Override
        public Object autobox(Object value, Type targetType, Map<String, Object> objectRegistry) throws Exception {
            Double[] doubleArray = (Double[]) value;

            logger.info("Constructed real parameter from double array: " + Arrays.toString(doubleArray));
            RealParameter realParameter = new RealParameter(doubleArray);
            logger.info("  Dimension of realParameter is: " + realParameter.getDimension());

            return realParameter;
        }
    }

    /**
     * Rule for autoboxing RealParameter to Frequencies
     */
    public static class RealParameterToFrequenciesRule implements AutoboxingRule {
        @Override
        public boolean canAutobox(Object value, Type targetType) {
            try {
                // Check if value is a RealParameter
                Class<?> realParamClass = Class.forName("beast.base.inference.parameter.RealParameter");
                if (!realParamClass.isAssignableFrom(value.getClass())) {
                    return false;
                }

                // Check if target is Frequencies
                Class<?> freqsClass = Class.forName("beast.base.evolution.substitutionmodel.Frequencies");
                Class<?> targetClass = TypeUtils.getRawType(targetType);

                return targetClass != null && freqsClass.isAssignableFrom(targetClass);
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        @Override
        public Object autobox(Object value, Type targetType, Map<String, Object> objectRegistry) throws Exception {
            // Create Frequencies object
            Class<?> freqsClass = Class.forName("beast.base.evolution.substitutionmodel.Frequencies");
            Object freqs = freqsClass.getDeclaredConstructor().newInstance();

            // Set frequencies input
            BEASTInterface freqsObj = (BEASTInterface) freqs;
            Input<?> freqsInput = freqsObj.getInput("frequencies");

            if (freqsInput != null) {
                BEASTUtils.setInputValue(freqsInput, value, freqsObj);
                BEASTUtils.callInitAndValidate(freqs, freqsClass);
                return freqs;
            }

            return null;
        }
    }

    /**
     * Rule for direct autoboxing Double[] to Frequencies (combines both rules)
     */
    public static class DoubleArrayToFrequenciesRule implements AutoboxingRule {
        @Override
        public boolean canAutobox(Object value, Type targetType) {
            // Check if value is a Double array
            if (!(value instanceof Double[])) {
                return false;
            }

            // Check if target is Frequencies
            try {
                Class<?> freqsClass = Class.forName("beast.base.evolution.substitutionmodel.Frequencies");
                Class<?> targetClass = TypeUtils.getRawType(targetType);

                return targetClass != null && freqsClass.isAssignableFrom(targetClass);
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        @Override
        public Object autobox(Object value, Type targetType, Map<String, Object> objectRegistry) throws Exception {
            // First convert Double[] to RealParameter
            DoubleArrayToRealParameterRule paramRule = new DoubleArrayToRealParameterRule();
            Object realParam = paramRule.autobox(value,
                    Class.forName("beast.base.inference.parameter.RealParameter"),
                    objectRegistry);

            // Then convert RealParameter to Frequencies
            RealParameterToFrequenciesRule freqRule = new RealParameterToFrequenciesRule();
            return freqRule.autobox(realParam, targetType, objectRegistry);
        }
    }

    /**
     * Rule for autoboxing Tree to TreeIntervals
     */
    public static class TreeToTreeIntervalsRule implements AutoboxingRule {
        @Override
        public boolean canAutobox(Object value, Type targetType) {
            try {
                // Check if value is a Tree or TreeInterface
                Class<?> treeClass = Class.forName("beast.base.evolution.tree.Tree");
                Class<?> treeInterfaceClass = Class.forName("beast.base.evolution.tree.TreeInterface");
                if (!(treeClass.isInstance(value) || treeInterfaceClass.isInstance(value))) {
                    return false;
                }

                // Check if target is TreeIntervals
                Class<?> treeIntervalsClass = Class.forName("beast.base.evolution.tree.TreeIntervals");
                Class<?> targetClass = TypeUtils.getRawType(targetType);

                return targetClass != null && treeIntervalsClass.isAssignableFrom(targetClass);
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        @Override
        public Object autobox(Object value, Type targetType, Map<String, Object> objectRegistry) throws Exception {
            try {
                // Create TreeIntervals object
                Class<?> treeIntervalsClass = Class.forName("beast.base.evolution.tree.TreeIntervals");
                Object treeIntervals = treeIntervalsClass.getDeclaredConstructor().newInstance();

                // Set the tree on the TreeIntervals
                BEASTInterface treeIntervalsInterface = (BEASTInterface) treeIntervals;
                Input<?> treeInput = treeIntervalsInterface.getInput("tree");

                if (treeInput != null) {
                    // Connect the tree to the TreeIntervals
                    BEASTUtils.setInputValue(treeInput, value, treeIntervalsInterface);

                    // Initialize the TreeIntervals object
                    BEASTUtils.callInitAndValidate(treeIntervals, treeIntervalsClass);

                    return treeIntervals;
                }

                return null;
            } catch (Exception e) {
                logger.warning("Error creating TreeIntervals: " + e.getMessage());
                return null;
            }
        }
    }
}
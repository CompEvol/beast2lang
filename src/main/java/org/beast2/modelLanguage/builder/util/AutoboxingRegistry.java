package org.beast2.modelLanguage.builder.util;

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

        // Rule $: Alignment to TaxonSet autoboxing
        addRule(new AlignmentToTaxonSetRule());
    }

    /**
     * Add an autoboxing rule to the registry
     */
    public void addRule(AutoboxingRule rule) {
        rules.add(rule);
    }

    /**
     * Attempt to autobox a value based on the expected type
     */
    public Object autobox(Object value, Class<?> expectedType, Map<String, Object> objectRegistry) {
        if (value == null || expectedType == null) {
            return value;
        }

        // Try each rule in order
        for (AutoboxingRule rule : rules) {
            if (rule.canAutobox(value, expectedType)) {
                try {
                    Object result = rule.autobox(value, expectedType, objectRegistry);
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
                System.out.println("Matched autoboxing rule: " + rule);
            }
        }

        // Return original value if no autoboxing could be applied
        return value;
    }

    /**
     * Interface for autoboxing rules
     */
    public interface AutoboxingRule {
        /**
         * Check if this rule can autobox the given value to the expected type
         */
        boolean canAutobox(Object value, Class<?> expectedType);

        /**
         * Perform the autoboxing
         */
        Object autobox(Object value, Class<?> expectedType, Map<String, Object> objectRegistry) throws Exception;
    }

    /**
     * Rule for autoboxing literal values to Parameter objects
     */
    public static class LiteralToParameterRule implements AutoboxingRule {
        @Override
        public boolean canAutobox(Object value, Class<?> expectedType) {
            return (value instanceof Number || value instanceof Boolean || value instanceof String) &&
                    expectedType != null &&
                    isParameterType(expectedType);
        }

        @Override
        public Object autobox(Object value, Class<?> expectedType, Map<String, Object> objectRegistry) throws Exception {
            return BEASTUtils.createParameterForType(value, expectedType);
        }

        private boolean isParameterType(Class<?> type) {
            try {
                Class<?> parameterClass = Class.forName("beast.base.inference.parameter.Parameter");
                return parameterClass.isAssignableFrom(type);
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
        public boolean canAutobox(Object value, Class<?> expectedType) {
            try {
                Class<?> priorClass = Class.forName("beast.base.inference.distribution.Prior");
                Class<?> paramDistClass = Class.forName("beast.base.inference.distribution.ParametricDistribution");

                return paramDistClass.isAssignableFrom(value.getClass()) &&
                        priorClass.isAssignableFrom(expectedType);
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        @Override
        public Object autobox(Object value, Class<?> expectedType, Map<String, Object> objectRegistry) throws Exception {
            // Create Prior
            Class<?> priorClass = Class.forName("beast.base.inference.distribution.Prior");
            Object prior = priorClass.getDeclaredConstructor().newInstance();

            // Set distribution
            beast.base.core.BEASTInterface priorObj = (beast.base.core.BEASTInterface) prior;
            beast.base.core.Input<?> input = priorObj.getInput("distr");

            if (input != null) {
                BEASTUtils.setInputValue(input, value, priorObj);
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
        public boolean canAutobox(Object value, Class<?> expectedType) {
            try {
                Class<?> siteModelClass = Class.forName("beast.base.evolution.sitemodel.SiteModelInterface");
                Class<?> substModelClass = Class.forName("beast.base.evolution.substitutionmodel.SubstitutionModel");

                return substModelClass.isAssignableFrom(value.getClass()) &&
                        expectedType.isAssignableFrom(siteModelClass);
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        @Override
        public Object autobox(Object value, Class<?> expectedType, Map<String, Object> objectRegistry) throws Exception {
            // Create SiteModel
            Class<?> siteModelClass = Class.forName("beast.base.evolution.sitemodel.SiteModel");
            Object siteModel = siteModelClass.getDeclaredConstructor().newInstance();

            // Set substitution model
            beast.base.core.BEASTInterface siteModelObj = (beast.base.core.BEASTInterface) siteModel;
            beast.base.core.Input<?> input = siteModelObj.getInput("substModel");

            if (input != null) {
                BEASTUtils.setInputValue(input, value, siteModelObj);
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
        public boolean canAutobox(Object value, Class<?> expectedType) {
            try {
                Class<?> taxonSetClass = Class.forName("beast.base.evolution.alignment.TaxonSet");
                Class<?> alignmentClass = Class.forName("beast.base.evolution.alignment.Alignment");

                return alignmentClass.isAssignableFrom(value.getClass()) &&
                        expectedType.isAssignableFrom(taxonSetClass);
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        @Override
        public Object autobox(Object value, Class<?> expectedType, Map<String, Object> objectRegistry) throws Exception {
            // Create TaxonSet
            Class<?> taxonSetClass = Class.forName("beast.base.evolution.alignment.TaxonSet");
            Object taxonSet = taxonSetClass.getDeclaredConstructor().newInstance();

            // Set alignment
            beast.base.core.BEASTInterface taxonSetObj = (beast.base.core.BEASTInterface) taxonSet;
            beast.base.core.Input<?> input = taxonSetObj.getInput("alignment");

            if (input != null) {
                BEASTUtils.setInputValue(input, value, taxonSetObj);
                BEASTUtils.callInitAndValidate(taxonSet, taxonSetClass);
                return taxonSet;
            }

            return null;
        }
    }
}
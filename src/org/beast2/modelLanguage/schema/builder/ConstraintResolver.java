package org.beast2.modelLanguage.schema.builder;

import java.util.*;

/**
 * Resolves constraints for arguments based on their name and type
 */
public class ConstraintResolver {
    private final Map<String, String> exactConstraints;
    private final List<ConstraintRule> patternRules;

    public ConstraintResolver() {
        this.exactConstraints = initializeExactConstraints();
        this.patternRules = initializePatternRules();
    }

    /**
     * Resolve constraint for a given class and argument
     */
    public Optional<String> resolveConstraint(String className, String argName) {
        String fullKey = className + "." + argName;

        // Check exact matches first
        if (exactConstraints.containsKey(fullKey)) {
            return Optional.of(exactConstraints.get(fullKey));
        }

        // Check pattern-based rules
        for (ConstraintRule rule : patternRules) {
            if (rule.matches(argName)) {
                return Optional.of(rule.constraint);
            }
        }

        return Optional.empty();
    }

    private Map<String, String> initializeExactConstraints() {
        Map<String, String> constraints = new HashMap<>();

        // Frequencies and simplex constraints
        constraints.put("Frequencies.frequencies", "simplex");

        // HKY model parameters
        constraints.put("HKY.kappa", "positive");

        // LogNormalDistributionModel parameters
        constraints.put("LogNormalDistributionModel.S", "positive");

        // Gamma distribution parameters
        constraints.put("Gamma.alpha", "positive");
        constraints.put("Gamma.beta", "positive");
        constraints.put("Gamma.shape", "positive");
        constraints.put("Gamma.scale", "positive");

        // Beta distribution parameters
        constraints.put("Beta.alpha", "positive");
        constraints.put("Beta.beta", "positive");

        // Exponential distribution parameters
        constraints.put("Exponential.mean", "positive");
        constraints.put("Exponential.lambda", "positive");
        constraints.put("Exponential.offset", "non-negative");

        // InverseGamma distribution parameters
        constraints.put("InverseGamma.alpha", "positive");
        constraints.put("InverseGamma.beta", "positive");

        // Dirichlet distribution parameters
        constraints.put("Dirichlet.alpha", "positive");

        // Tree prior parameters
        constraints.put("BirthDeathGernhard08Model.sampleProbability", "0-1");
        constraints.put("BirthDeathGernhard08Model.originHeight", "positive");

        // Substitution model parameters
        constraints.put("GTR.rateAC", "positive");
        constraints.put("GTR.rateAG", "positive");
        constraints.put("GTR.rateAT", "positive");
        constraints.put("GTR.rateCG", "positive");
        constraints.put("GTR.rateGT", "positive");
        constraints.put("GTR.rateCT", "positive");

        // TN93 model parameters
        constraints.put("TN93.kappa1", "positive");
        constraints.put("TN93.kappa2", "positive");

        // SiteModel parameters
        constraints.put("SiteModel.shape", "positive");

        // Population size parameters
        constraints.put("ConstantPopulation.popSize", "positive");
        constraints.put("ExponentialGrowth.popSize", "positive");
        constraints.put("ExponentialGrowth.growthRate", "real"); // Can be negative for decline

        // Prior parameters
        constraints.put("Prior.x", "real"); // Generic prior can be on any real parameter

        // Add more exact constraints as discovered

        return constraints;
    }

    private List<ConstraintRule> initializePatternRules() {
        List<ConstraintRule> rules = new ArrayList<>();

        // Rate parameters should be positive (but not death rates or relative rates)
        rules.add(new ConstraintRule(
                name -> {
                    String lower = name.toLowerCase();
                    return lower.contains("rate") &&
                            !lower.contains("death") &&
                            !lower.contains("relative") &&
                            !lower.contains("growth"); // growth rate can be negative
                },
                "positive"
        ));

        // Scale parameters should be positive
        rules.add(new ConstraintRule(
                name -> name.toLowerCase().contains("scale") &&
                        !name.toLowerCase().contains("scalefactor"), // scaleFactor handled explicitly
                "positive"
        ));

        // Variance, standard deviation, and precision parameters should be positive
        rules.add(new ConstraintRule(
                name -> {
                    String lower = name.toLowerCase();
                    return lower.contains("variance") ||
                            lower.contains("stddev") ||
                            lower.contains("standarddeviation") ||
                            lower.contains("precision");
                },
                "positive"
        ));

        // Probability and proportion parameters should be in [0,1]
        rules.add(new ConstraintRule(
                name -> {
                    String lower = name.toLowerCase();
                    return lower.contains("probability") ||
                            lower.contains("prob") ||
                            lower.contains("proportion") ||
                            lower.contains("fraction");
                },
                "0-1"
        ));

        // Frequency parameters (plural) often need simplex constraint
        rules.add(new ConstraintRule(
                name -> name.toLowerCase().equals("frequencies") ||
                        name.toLowerCase().endsWith("frequencies"),
                "simplex"
        ));

        // Size, count, and dimension parameters should be positive integers
        rules.add(new ConstraintRule(
                name -> {
                    String lower = name.toLowerCase();
                    return (lower.contains("size") && !lower.contains("popsize") && !lower.contains("populationsize")) ||
                            lower.contains("count") ||
                            lower.contains("dimension") ||
                            lower.equals("n") ||
                            lower.equals("k");
                },
                "positive-integer"
        ));

        // Population size parameters should be positive
        rules.add(new ConstraintRule(
                name -> {
                    String lower = name.toLowerCase();
                    return lower.contains("popsize") ||
                            lower.contains("populationsize") ||
                            (lower.contains("population") && lower.contains("size"));
                },
                "positive"
        ));

        // Height and length parameters should be positive
        rules.add(new ConstraintRule(
                name -> {
                    String lower = name.toLowerCase();
                    return lower.contains("height") ||
                            lower.contains("length") ||
                            lower.contains("distance");
                },
                "positive"
        ));

        // Weight parameters that aren't part of "weighted" should be positive
        rules.add(new ConstraintRule(
                name -> {
                    String lower = name.toLowerCase();
                    return lower.equals("weight") ||
                            (lower.contains("weight") && !lower.contains("weighted"));
                },
                "positive"
        ));

        // Offset parameters should be non-negative
        rules.add(new ConstraintRule(
                name -> name.toLowerCase().equals("offset"),
                "non-negative"
        ));

        // Boolean flags
        rules.add(new ConstraintRule(
                name -> {
                    String lower = name.toLowerCase();
                    return lower.startsWith("is") ||
                            lower.startsWith("has") ||
                            lower.startsWith("use") ||
                            lower.startsWith("include") ||
                            lower.startsWith("exclude");
                },
                "boolean"
        ));

        return rules;
    }

    /**
     * A rule for applying constraints based on patterns
     */
    private static class ConstraintRule {
        private final java.util.function.Predicate<String> matcher;
        private final String constraint;

        ConstraintRule(java.util.function.Predicate<String> matcher, String constraint) {
            this.matcher = matcher;
            this.constraint = constraint;
        }

        boolean matches(String argName) {
            return matcher.test(argName);
        }
    }
}
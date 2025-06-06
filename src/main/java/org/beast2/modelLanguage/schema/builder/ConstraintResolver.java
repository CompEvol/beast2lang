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
        
        // Known exact constraints
        constraints.put("Frequencies.frequencies", "simplex");
        
        // Add more exact constraints as needed
        
        return constraints;
    }
    
    private List<ConstraintRule> initializePatternRules() {
        List<ConstraintRule> rules = new ArrayList<>();
        
        // Rate and scale parameters should be positive
        rules.add(new ConstraintRule(
            name -> name.toLowerCase().contains("rate") || 
                    name.toLowerCase().contains("scale"),
            "positive"
        ));
        
        // Probability parameters should be in [0,1]
        rules.add(new ConstraintRule(
            name -> name.toLowerCase().contains("probability") || 
                    name.toLowerCase().contains("prob"),
            "probability"
        ));
        
        // Dimension/size parameters should be positive integers
        rules.add(new ConstraintRule(
            name -> name.toLowerCase().contains("dimension") || 
                    name.toLowerCase().contains("size") ||
                    name.toLowerCase().contains("count"),
            "positive-integer"
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
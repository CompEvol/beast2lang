package org.beast2.modelLanguage.converter;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.evolution.likelihood.TreeLikelihood;

import java.util.*;

/**
 * Utility class for validating and filtering BEAST inputs
 */
public class InputValidator {

    /**
     * Check if an input should be skipped
     */
    public static boolean shouldSkipInput(BEASTInterface obj, Input<?> input) {
        // Skip if it's at default value
        if (isDefaultValue(obj, input)) {
            return true;
        }

        // Skip if it's optional and empty
        if (input.getRule() == Input.Validate.OPTIONAL &&
                (input.get() == null ||
                        (input.get() instanceof List && ((List<?>) input.get()).isEmpty()))) {
            return true;
        }

        // Still skip certain verbose inputs even if they're not at default
        Set<String> alwaysSkip = new HashSet<>(Arrays.asList(
                "sequence", // Individual sequences in alignment
                "*"         // Wildcard inputs
        ));

        if (obj instanceof TreeLikelihood && input.getName().equals("data")) {
            return true;
        }

        return alwaysSkip.contains(input.getName());
    }

    /**
     * Check if an input is at its default value
     */
    public static boolean isDefaultValue(BEASTInterface obj, Input<?> input) {
        Object value = input.get();
        Object defaultValue = input.defaultValue;

        // If no default value is set, we can't determine if this is default
        if (defaultValue == null) {
            return false;
        }

        // For simple types, direct comparison
        if (defaultValue.equals(value)) {
            return true;
        }

        // For arrays, need special handling
        if (defaultValue.getClass().isArray() && value.getClass().isArray()) {
            return Arrays.equals((Object[]) defaultValue, (Object[]) value);
        }

        // For primitive arrays
        if (defaultValue instanceof double[] && value instanceof double[]) {
            return Arrays.equals((double[]) defaultValue, (double[]) value);
        }
        if (defaultValue instanceof int[] && value instanceof int[]) {
            return Arrays.equals((int[]) defaultValue, (int[]) value);
        }

        return false;
    }
}
package org.beast2.modelLanguage.schema.validation;

import java.util.*;

/**
 * Result of schema validation including missing types and usage information
 */
public class ValidationResult {
    private final Set<String> missingTypes = new TreeSet<>();
    private final Set<String> inferenceRelatedTypes = new TreeSet<>();
    private final Set<String> guiRelatedTypes = new TreeSet<>();
    private final Map<String, Set<String>> typeUsage = new HashMap<>();
    private int totalComponents = 0;

    public void setTotalComponents(int totalComponents) {
        this.totalComponents = totalComponents;
    }

    public void addMissingType(String type) {
        missingTypes.add(type);
    }

    public void addInferenceType(String type) {
        inferenceRelatedTypes.add(type);
    }

    public void addGUIType(String type) {
        guiRelatedTypes.add(type);
    }

    public void addTypeUsage(String type, String usedBy) {
        typeUsage.computeIfAbsent(type, k -> new TreeSet<>()).add(usedBy);
    }

    public boolean hasErrors() {
        return !missingTypes.isEmpty();
    }

    public boolean hasWarnings() {
        return !inferenceRelatedTypes.isEmpty() || !guiRelatedTypes.isEmpty();
    }

    public boolean isValid() {
        return !hasErrors() && !hasWarnings();
    }

    public Set<String> getMissingTypes() {
        return Collections.unmodifiableSet(missingTypes);
    }

    public Set<String> getInferenceRelatedTypes() {
        return Collections.unmodifiableSet(inferenceRelatedTypes);
    }

    public Set<String> getGuiRelatedTypes() {
        return Collections.unmodifiableSet(guiRelatedTypes);
    }

    public Set<String> getTypeUsage(String type) {
        return typeUsage.getOrDefault(type, Collections.emptySet());
    }

    public Map<String, Set<String>> getAllTypeUsage() {
        return Collections.unmodifiableMap(typeUsage);
    }

    /**
     * Generate a report of the validation results
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("\n=== CLOSURE TEST RESULTS ===\n");
        report.append("Total components: ").append(totalComponents).append("\n");
        report.append("Total unique types referenced: ").append(typeUsage.size()).append("\n");

        if (isValid()) {
            report.append("✓ CLOSURE TEST PASSED: All argument types are defined!\n");
        } else {
            if (hasErrors()) {
                report.append("\n✗ MISSING MODEL TYPES: ").append(missingTypes.size())
                        .append(" types are not defined:\n");
                for (String missing : missingTypes) {
                    report.append("  - ").append(missing).append(" (used by: ")
                            .append(String.join(", ", getTypeUsage(missing))).append(")\n");
                }
            }

            if (!inferenceRelatedTypes.isEmpty()) {
                report.append("\n⚠ INFERENCE-RELATED TYPES (excluded by design): ")
                        .append(inferenceRelatedTypes.size()).append(" types\n");
                for (String inferenceType : inferenceRelatedTypes) {
                    report.append("  - ").append(inferenceType).append(" (used by: ")
                            .append(String.join(", ", getTypeUsage(inferenceType))).append(")\n");
                }
            }

            if (!guiRelatedTypes.isEmpty()) {
                report.append("\n⚠ GUI/BEAUTI-RELATED TYPES (excluded by design): ")
                        .append(guiRelatedTypes.size()).append(" types\n");
                for (String guiType : guiRelatedTypes) {
                    report.append("  - ").append(guiType).append(" (used by: ")
                            .append(String.join(", ", getTypeUsage(guiType))).append(")\n");
                }
            }

            if (hasWarnings()) {
                report.append("\nComponents excluded from model library:\n");
                Set<String> excludedComponents = new TreeSet<>();

                for (String type : inferenceRelatedTypes) {
                    for (String usage : getTypeUsage(type)) {
                        String componentName = usage.substring(0, usage.lastIndexOf('.'));
                        excludedComponents.add(componentName + " (uses inference type)");
                    }
                }

                for (String type : guiRelatedTypes) {
                    for (String usage : getTypeUsage(type)) {
                        String componentName = usage.substring(0, usage.lastIndexOf('.'));
                        excludedComponents.add(componentName + " (uses GUI type)");
                    }
                }

                for (String excluded : excludedComponents) {
                    report.append("  - ").append(excluded).append("\n");
                }
            }

            if (hasErrors()) {
                report.append("\nSuggestions for missing model types:\n");
                for (String missing : missingTypes) {
                    if (missing.contains(".")) {
                        report.append("  - ").append(missing)
                                .append(" might be an inner class or from another package\n");
                    } else {
                        report.append("  - ").append(missing)
                                .append(" might need to be added to importantBaseTypes\n");
                    }
                }
            }
        }

        return report.toString();
    }
}
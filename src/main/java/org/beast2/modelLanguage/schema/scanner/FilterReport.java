package org.beast2.modelLanguage.schema.scanner;

import java.util.*;

/**
 * Tracks components that were filtered out and the reasons why
 */
public class FilterReport {
    private final Map<String, List<String>> filteredComponents = new TreeMap<>();
    private final Map<String, Integer> reasonCounts = new TreeMap<>();

    public void addFiltered(String className, String reason) {
        filteredComponents.computeIfAbsent(className, k -> new ArrayList<>()).add(reason);
        reasonCounts.merge(reason, 1, Integer::sum);
    }

    public Map<String, List<String>> getFilteredComponents() {
        return Collections.unmodifiableMap(filteredComponents);
    }

    public Map<String, Integer> getReasonCounts() {
        return Collections.unmodifiableMap(reasonCounts);
    }

    public int getTotalFiltered() {
        return filteredComponents.size();
    }

    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("\n=== COMPONENT FILTER REPORT ===\n");
        report.append("Total components filtered: ").append(getTotalFiltered()).append("\n\n");

        // Summary by reason
        report.append("Filter reasons summary:\n");
        reasonCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    report.append(String.format("  %-50s: %d\n", entry.getKey(), entry.getValue()));
                });

        // Detailed list
        report.append("\nDetailed filtered components:\n");
        filteredComponents.forEach((className, reasons) -> {
            report.append("\n").append(className).append(":\n");
            reasons.forEach(reason -> report.append("  - ").append(reason).append("\n"));
        });

        return report.toString();
    }
}
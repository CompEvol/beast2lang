import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BeastImportFinder {

    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+beast\\.[^;]+;");
    private static final String EXCLUDED_FILE = "Beast2AnalysisBuilder.java";
    private final Set<String> foundDependencies = new HashSet<>();

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please provide a directory path to scan");
            return;
        }

        String directoryPath = args[0];
        BeastImportFinder finder = new BeastImportFinder();

        try {
            finder.scanDirectory(directoryPath);
            finder.printResults();
        } catch (IOException e) {
            System.err.println("Error scanning directory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void scanDirectory(String directoryPath) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
            List<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals(EXCLUDED_FILE))
                    .collect(Collectors.toList());

            for (Path file : javaFiles) {
                scanJavaFile(file);
            }
        }
    }

    private void scanJavaFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        Matcher matcher = IMPORT_PATTERN.matcher(content);

        while (matcher.find()) {
            String dependency = matcher.group().replace("import ", "").replace(";", "").trim();
            foundDependencies.add(dependency);
            System.out.println("Found in " + filePath + ": " + dependency);
        }
    }

    public void printResults() {
        System.out.println("\n=== Beast Dependencies Found in Java Source Files ===");
        System.out.println("(Excluding " + EXCLUDED_FILE + ")");
        List<String> sortedDependencies = new ArrayList<>(foundDependencies);
        sortedDependencies.sort(String::compareTo);

        for (String dependency : sortedDependencies) {
            System.out.println(dependency);
        }
        System.out.println("\nTotal unique beast dependencies: " + foundDependencies.size());
    }
}
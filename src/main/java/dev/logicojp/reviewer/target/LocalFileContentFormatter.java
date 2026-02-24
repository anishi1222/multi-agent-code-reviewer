package dev.logicojp.reviewer.target;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// Formats collected local files into markdown review content and summary output.
final class LocalFileContentFormatter {

    private static final Map<String, String> LANGUAGE_MAP = Map.ofEntries(
        Map.entry("js", "javascript"), Map.entry("mjs", "javascript"), Map.entry("cjs", "javascript"),
        Map.entry("ts", "typescript"), Map.entry("jsx", "jsx"), Map.entry("tsx", "tsx"),
        Map.entry("py", "python"), Map.entry("rb", "ruby"), Map.entry("rs", "rust"),
        Map.entry("kt", "kotlin"), Map.entry("kts", "kotlin"),
        Map.entry("cs", "csharp"), Map.entry("fs", "fsharp"),
        Map.entry("sh", "bash"), Map.entry("bash", "bash"), Map.entry("zsh", "bash"),
        Map.entry("ps1", "powershell"), Map.entry("psm1", "powershell"),
        Map.entry("yml", "yaml"), Map.entry("md", "markdown")
    );

    private final Path baseDirectory;
    private final LocalFileSelectionConfig selectionConfig;

    LocalFileContentFormatter(Path baseDirectory, LocalFileSelectionConfig selectionConfig) {
        this.baseDirectory = baseDirectory;
        this.selectionConfig = selectionConfig;
    }

    String generateReviewContent(List<LocalFileProvider.LocalFile> files) {
        if (files == null || files.isEmpty()) return "(no source files found)";
        long estimatedSize = files.stream().mapToLong(LocalFileProvider.LocalFile::sizeBytes).sum();
        var sb = new StringBuilder((int) Math.min(
            estimatedSize + files.size() * 30L, selectionConfig.maxTotalSize() + 4096));
        for (LocalFileProvider.LocalFile file : files) {
            appendFileBlock(sb, file.relativePath(), file.content());
        }
        return sb.toString();
    }

    String generateDirectorySummary(List<LocalFileProvider.LocalFile> files) {
        if (files == null || files.isEmpty()) return noSourceFilesSummary();
        long totalSize = files.stream().mapToLong(LocalFileProvider.LocalFile::sizeBytes).sum();
        var fileListBuilder = new StringBuilder();
        for (LocalFileProvider.LocalFile file : files) {
            fileListBuilder.append("  - ").append(file.relativePath())
                .append(" (").append(file.sizeBytes()).append(" bytes)\n");
        }
        return generateDirectorySummary(files.size(), totalSize, fileListBuilder);
    }

    String generateDirectorySummary(int fileCount, long totalSize, StringBuilder fileListBuilder) {
        if (fileCount == 0) return noSourceFilesSummary();
        return new StringBuilder()
            .append("Directory: ").append(baseDirectory).append("\n")
            .append("Files: ").append(fileCount).append("\n")
            .append("Total size: ").append(totalSize).append(" bytes\n\n")
            .append("File list:\n").append(fileListBuilder)
            .toString();
    }

    int estimateReviewContentCapacity(List<LocalFileCandidateCollector.Candidate> candidates) {
        long estimatedSize = 0;
        for (var candidate : candidates) {
            estimatedSize += candidate.size() + 64L;
            if (estimatedSize >= selectionConfig.maxTotalSize()) break;
        }
        return (int) Math.min(estimatedSize + 1024L, selectionConfig.maxTotalSize() + 4096);
    }

    void appendFileBlock(StringBuilder sb, String relativePath, String content) {
        String lang = detectLanguage(relativePath);
        sb.append("### ").append(relativePath).append("\n\n");
        sb.append("```").append(lang).append("\n");
        sb.append(content);
        if (!content.endsWith("\n")) sb.append("\n");
        sb.append("```\n\n");
    }

    private String noSourceFilesSummary() {
        return "No source files found in: " + baseDirectory;
    }

    private String detectLanguage(String relativePath) {
        int dotIndex = relativePath.lastIndexOf('.');
        if (dotIndex < 0) return "";
        String ext = relativePath.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return LANGUAGE_MAP.getOrDefault(ext, ext);
    }
}

package dev.logicojp.reviewer.infrastructure.file;

import dev.logicojp.reviewer.domain.review.LocalFileCandidate;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/// Formats collected source file candidates into review-ready Markdown content.
final class LocalFileContentFormatter {

    private static final Map<String, String> LANGUAGE_MAP = Map.ofEntries(
        Map.entry("js", "javascript"), Map.entry("mjs", "javascript"), Map.entry("cjs", "javascript"),
        Map.entry("ts", "typescript"), Map.entry("jsx", "jsx"), Map.entry("tsx", "tsx"),
        Map.entry("py", "python"), Map.entry("rb", "ruby"), Map.entry("rs", "rust"),
        Map.entry("kt", "kotlin"), Map.entry("kts", "kotlin"), Map.entry("cs", "csharp"),
        Map.entry("fs", "fsharp"), Map.entry("sh", "bash"), Map.entry("bash", "bash"),
        Map.entry("zsh", "bash"), Map.entry("ps1", "powershell"), Map.entry("psm1", "powershell"),
        Map.entry("yml", "yaml"), Map.entry("md", "markdown")
    );

    private final long maxTotalSize;

    LocalFileContentFormatter(long maxTotalSize) {
        this.maxTotalSize = maxTotalSize;
    }

    int estimateReviewContentCapacity(List<LocalFileCandidate> candidates) {
        long estimatedSize = 0;
        for (LocalFileCandidate candidate : candidates) {
            estimatedSize += candidate.size() + 64L;
            if (estimatedSize >= maxTotalSize) break;
        }
        return (int) Math.min(estimatedSize + 1024L, maxTotalSize + 4096);
    }

    void appendFileBlock(StringBuilder sb, String relativePath, String content) {
        String lang = detectLanguage(relativePath);
        sb.append("### ").append(relativePath).append("\n\n");
        sb.append("```").append(lang).append("\n");
        sb.append(content);
        if (!content.endsWith("\n")) sb.append("\n");
        sb.append("```\n\n");
    }

    private String detectLanguage(String relativePath) {
        int dotIndex = relativePath.lastIndexOf('.');
        if (dotIndex < 0) return "";
        String ext = relativePath.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return LANGUAGE_MAP.getOrDefault(ext, ext);
    }
}

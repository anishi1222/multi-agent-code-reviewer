package dev.logicojp.reviewer.report.finding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReviewFindingParser {

    private static final Pattern FINDING_HEADER = Pattern.compile(
        "(?m)^###\\s+\\[?(\\d+)\\]?\\.\\s+(.+?)\\s*$"
    );
    private static final Pattern TRAILING_GLOBAL_SECTION = Pattern.compile(
        "(?im)^##\\s+.+$|^###\\s*(?:総評|総合評価|総括|まとめ|overall\\s+assessment|overall\\s+summary|overall|summary)\\s*$|^\\*\\*(?:総評|総合評価|総括|まとめ|overall\\s+assessment|overall\\s+summary|overall|summary)\\*\\*\\s*$"
    );
    private static final Pattern NO_FINDINGS_BODY = Pattern.compile(
        "(?is)^\\s*(?:確認した範囲では\\s*)?指摘事項なし(?:。)?\\s*$"
    );
    private static final Pattern SEPARATOR_LINE = Pattern.compile("^\\s*---\\s*$");
    private static final Pattern OVERALL_HEADER = Pattern.compile(
        "(?im)^(?:##+\\s*(?:総評|総合評価|総括|まとめ|overall\\s+assessment|overall\\s+summary|overall|summary)\\s*$|\\*\\*(?:総評|総合評価|総括|まとめ|overall\\s+assessment|overall\\s+summary|overall|summary)\\*\\*\\s*$)"
    );
    private static final Pattern TABLE_ROW_TEMPLATE = Pattern.compile(
        "(?m)^\\|\\s*\\*{0,2}%s\\*{0,2}\\s*\\|\\s*(.*?)\\s*\\|\\s*$");
    private static final Map<String, Pattern> TABLE_VALUE_PATTERNS = Map.of(
        "Priority", compileTablePattern("Priority"),
        "指摘の概要", compileTablePattern("指摘の概要"),
        "該当箇所", compileTablePattern("該当箇所")
    );
    private ReviewFindingParser() {
    }

    public static List<FindingBlock> extractFindingBlocks(String content) {
        Matcher matcher = FINDING_HEADER.matcher(content);
        List<HeaderMatch> headers = new ArrayList<>();
        while (matcher.find()) {
            headers.add(new HeaderMatch(matcher.start(), matcher.end(), matcher.group(2).trim()));
        }

        if (headers.isEmpty()) {
            return List.of();
        }

        List<FindingBlock> blocks = new ArrayList<>(headers.size());
        for (int i = 0; i < headers.size(); i++) {
            HeaderMatch current = headers.get(i);
            int bodyEnd = i + 1 < headers.size() ? headers.get(i + 1).startIndex() : content.length();
            String body = normalizeFindingBody(content.substring(current.endIndex(), bodyEnd));
            if (!body.isEmpty() && !isNoFindingsBody(body)) {
                blocks.add(new FindingBlock(current.title(), body));
            }
        }
        return blocks;
    }

    private static boolean isNoFindingsBody(String body) {
        return NO_FINDINGS_BODY.matcher(body).matches();
    }

    private static String normalizeFindingBody(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return "";
        }

        int boundaryIndex = findTrailingGlobalSectionStart(rawBody);
        String body = rawBody.substring(0, boundaryIndex).trim();

        return trimTrailingSeparators(body);
    }

    /// Extracts trailing overall-summary text from review content.
    ///
    /// Supported headers include variants such as `## 総評`, `### Summary`, and `**総評**`.
    public static String extractOverallSummary(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        Matcher matcher = OVERALL_HEADER.matcher(content);
        int summaryBodyStart = -1;
        while (matcher.find()) {
            summaryBodyStart = matcher.end();
        }

        if (summaryBodyStart < 0) {
            return "";
        }

        String summary = content.substring(summaryBodyStart).trim();
        return trimTrailingSeparators(summary);
    }

    /// Removes trailing overall-summary section from review content.
    public static String stripOverallSummary(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        Matcher matcher = OVERALL_HEADER.matcher(content);
        int summaryStart = -1;
        while (matcher.find()) {
            summaryStart = matcher.start();
        }

        if (summaryStart < 0) {
            return trimTrailingSeparators(content.trim());
        }
        return trimTrailingSeparators(content.substring(0, summaryStart).trim());
    }

    private static String trimTrailingSeparators(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        List<String> lines = new ArrayList<>(body.lines().toList());
        while (!lines.isEmpty() && SEPARATOR_LINE.matcher(lines.getLast()).matches()) {
            lines.removeLast();
        }
        // Also remove trailing blank lines left after separator removal
        while (!lines.isEmpty() && lines.getLast().isBlank()) {
            lines.removeLast();
        }
        return String.join("\n", lines).trim();
    }

    private static int findTrailingGlobalSectionStart(String rawBody) {
        Matcher matcher = TRAILING_GLOBAL_SECTION.matcher(rawBody);
        return matcher.find() ? matcher.start() : rawBody.length();
    }

    public static String extractTableValue(String body, String key) {
        Pattern pattern = TABLE_VALUE_PATTERNS.get(key);
        if (pattern == null) {
            pattern = compileTablePattern(key);
        }
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static Pattern compileTablePattern(String key) {
        return Pattern.compile(TABLE_ROW_TEMPLATE.pattern().formatted(Pattern.quote(key)));
    }

    private record HeaderMatch(int startIndex, int endIndex, String title) {
    }

    public record FindingBlock(String title, String body) {
    }
}

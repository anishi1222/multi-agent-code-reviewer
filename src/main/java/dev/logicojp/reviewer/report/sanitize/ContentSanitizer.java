package dev.logicojp.reviewer.report.sanitize;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Sanitizes review content returned by LLM models.
///
/// Removes artifacts that should not appear in the final review output:
/// - Chain-of-Thought (CoT) reasoning blocks (`<thinking>`, `<antThinking>`, etc.)
/// - Model preamble / filler text before the actual review content
/// - Excessive whitespace
public final class ContentSanitizer {

    @FunctionalInterface
    interface SanitizationStrategy {
        String apply(String content);
    }

    private ContentSanitizer() {
        // Utility class — not instantiable
    }

    /// Pattern to match common CoT / thinking XML-style blocks.
    /// Handles `<thinking>`, `<antThinking>`, `<reflection>`, `<inner_monologue>`, etc.
    /// Uses possessive quantifier (`*+`) to prevent catastrophic backtracking on large inputs.
    private static final Pattern THINKING_BLOCK_PATTERN = Pattern.compile(
        "<(thinking|antThinking|reflection|inner_monologue|scratchpad)>" +
        "(?:(?!</\\1>).)*+" +
        "</\\1>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    /// Pattern to match `<details>` blocks often used to hide reasoning.
    private static final Pattern DETAILS_THINKING_PATTERN = Pattern.compile(
        "<details>\\s*<summary>\\s*(?:Thinking|思考|推論).*?</summary>.*?</details>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    /// Combined pattern to strip CoT-like blocks in a single pass.
    private static final Pattern COT_BLOCK_PATTERN = Pattern.compile(
        "(?:" + THINKING_BLOCK_PATTERN.pattern() + ")|(?:" + DETAILS_THINKING_PATTERN.pattern() + ")",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    /// Pattern to collapse three or more consecutive blank lines into two.
    private static final Pattern EXCESSIVE_BLANK_LINES = Pattern.compile(
        "\\n{3,}"
    );

    /// Pattern to match dangerous HTML elements that could enable XSS when rendered.
    private static final Pattern DANGEROUS_HTML_PATTERN = Pattern.compile(
        "<\\s*(script|iframe|object|embed|form|input|base|link|meta|style|svg|math|audio|video|source)\\b[^>]*>.*?</\\s*\\1\\s*>|" +
        "<\\s*(script|iframe|object|embed|form|input|base|link|meta|style|svg|math|audio|video|source)\\b[^>]*/?>|" +
        "\\bon\\w+\\s*=|" +
        "data\\s*:[^,]*;base64",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    /// Pattern to decode numeric HTML entities before URI sanitization.
    private static final Pattern HTML_ENTITY_PATTERN = Pattern.compile(
        "&#x([0-9a-fA-F]+);|&#(\\d+);",
        Pattern.CASE_INSENSITIVE
    );

    /// Pattern to remove URI-based script execution in common HTML attributes.
    private static final Pattern DANGEROUS_URI_ATTRIBUTE_PATTERN = Pattern.compile(
        "(?:href|src|action|formaction|poster|background)\\s*=\\s*[\"']?\\s*"
            + "(?:j\\s*a\\s*v\\s*a\\s*s\\s*c\\s*r\\s*i\\s*p\\s*t|"
            + "v\\s*b\\s*s\\s*c\\s*r\\s*i\\s*p\\s*t|"
            + "data)\\s*:",
        Pattern.CASE_INSENSITIVE
    );

    /// Anchors with dangerous href protocols are removed entirely.
    private static final Pattern DANGEROUS_HREF_ANCHOR_PATTERN = Pattern.compile(
        "<a\\b[^>]*\\bhref\\s*=\\s*[\"']?\\s*"
            + "(?:j\\s*a\\s*v\\s*a\\s*s\\s*c\\s*r\\s*i\\s*p\\s*t|"
            + "v\\s*b\\s*s\\s*c\\s*r\\s*i\\s*p\\s*t|"
            + "data)\\s*:[^>]*>(?:(?!</a>).)*</a>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    private static final ContentSanitizationPipeline PIPELINE = new ContentSanitizationPipeline(
        List.of(
            new ContentSanitizationRule(COT_BLOCK_PATTERN, "",
                List.of("<thinking", "<antthinking", "<reflection", "<inner_monologue",
                    "<scratchpad", "<details>")),
            new ContentSanitizationRule(DANGEROUS_HREF_ANCHOR_PATTERN, "",
                List.of("<a", "href", "javascript", "vbscript", "data:")),
            new ContentSanitizationRule(DANGEROUS_URI_ATTRIBUTE_PATTERN, "",
                List.of("href", "src", "action", "formaction", "poster", "background",
                    "javascript", "vbscript", "data:")),
            new ContentSanitizationRule(DANGEROUS_HTML_PATTERN, "",
                List.of("<script", "<iframe", "<object", "<embed", "<form", "<input", "<img",
                    "<base", "<link", "<meta", "<style", "<svg", "<math",
                    "data:")),
            new ContentSanitizationRule(EXCESSIVE_BLANK_LINES, "\n\n")
        )
    );

    /// Sanitizes the given review content.
    ///
    /// @param content Raw content from the LLM
    /// @return Sanitized content suitable for reports, or null if input is null
    public static String sanitize(String content) {
        return sanitize(content, PIPELINE::apply);
    }

    static String sanitize(String content, SanitizationStrategy strategy) {
        if (content == null) {
            return null;
        }

        return strategy.apply(decodeNumericHtmlEntities(content)).strip();
    }

    private static String decodeNumericHtmlEntities(String content) {
        Matcher matcher = HTML_ENTITY_PATTERN.matcher(content);
        StringBuilder result = null;
        while (matcher.find()) {
            int codePoint = parseEntityCodePoint(matcher.group(1), matcher.group(2));
            if (codePoint < 0 || !Character.isValidCodePoint(codePoint)) {
                continue;
            }
            if (result == null) {
                result = new StringBuilder(content.length());
            }
            String decoded = new String(Character.toChars(codePoint));
            matcher.appendReplacement(result, Matcher.quoteReplacement(decoded));
        }
        if (result == null) {
            return content;
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static int parseEntityCodePoint(String hexPart, String decimalPart) {
        try {
            if (hexPart != null) {
                return Integer.parseInt(hexPart, 16);
            }
            if (decimalPart != null) {
                return Integer.parseInt(decimalPart, 10);
            }
        } catch (NumberFormatException _) {
            return -1;
        }
        return -1;
    }
}

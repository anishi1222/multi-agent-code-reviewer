package dev.logicojp.reviewer.report;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Sanitizes review content returned by LLM models.
///
/// Removes artifacts that should not appear in the final review output:
/// - Chain-of-Thought (CoT) reasoning blocks (`<thinking>`, `<antThinking>`, etc.)
/// - Dangerous HTML elements (XSS prevention)
/// - Excessive whitespace
///
/// Merges v1 ContentSanitizationPipeline and ContentSanitizationRule inline.
public final class ContentSanitizer {

    private ContentSanitizer() {
    }

    /// Pattern to match common CoT / thinking XML-style blocks.
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
    private static final Pattern EXCESSIVE_BLANK_LINES = Pattern.compile("\\n{3,}");

    /// Pattern to match numeric HTML entities (decimal/hex), e.g. &#97; or &#x61;.
    private static final Pattern NUMERIC_HTML_ENTITY_PATTERN = Pattern.compile("&#(x[0-9a-fA-F]+|\\d+);");
    private static final Pattern NAMED_HTML_ENTITY_PATTERN = Pattern.compile(
        "&(lt|gt|amp|quot|apos);",
        Pattern.CASE_INSENSITIVE
    );
    private static final Map<String, String> NAMED_ENTITIES = Map.of(
        "lt", "<",
        "gt", ">",
        "amp", "&",
        "quot", "\"",
        "apos", "'"
    );

    /// Pattern to match dangerous HTML elements that could enable XSS when rendered.
    private static final Pattern DANGEROUS_HTML_PATTERN = Pattern.compile(
        "<\\s*(script|iframe|object|embed|form|input|base|link|meta|style)\\b[^>]*>.*?</\\s*\\1\\s*>|" +
        "<\\s*(script|iframe|object|embed|form|input|base|link|meta|style)\\b[^>]*/?>|" +
        "\\bon\\w+\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|" +
        "javascript\\s*:[^\\s\"'>]+|" +
        "vbscript\\s*:[^\\s\"'>]+|" +
        "data\\s*:[^,]*;base64",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile("[\\p{Cf}\\x00-\\x08\\x0B\\x0E-\\x1F\\x7F]");
    private static final int MAX_SANITIZE_ITERATIONS = 3;

    /// Combined pattern to remove CoT blocks and dangerous HTML in a single pass.
    /// Note: CoT and dangerous HTML patterns are applied separately to avoid
    /// backreference collisions between the thinking block and HTML tag groups.

    private record Rule(Pattern pattern, String replacement) {
        String apply(String input) {
            Matcher matcher = pattern.matcher(input);
            if (!matcher.find()) {
                return input;
            }
            matcher.reset();
            return matcher.replaceAll(replacement);
        }
    }

    private static final List<Rule> RULES = List.of(
        new Rule(COT_BLOCK_PATTERN, ""),
        new Rule(DANGEROUS_HTML_PATTERN, ""),
        new Rule(EXCESSIVE_BLANK_LINES, "\n\n")
    );

    /// Sanitizes the given review content.
    public static String sanitize(String content) {
        if (content == null) {
            return null;
        }
        String result = decodeNumericHtmlEntities(content);
        result = decodeNamedHtmlEntities(result);
        result = Normalizer.normalize(result, Normalizer.Form.NFKC);
        result = CONTROL_CHARS_PATTERN.matcher(result).replaceAll("");
        for (int iteration = 0; iteration < MAX_SANITIZE_ITERATIONS; iteration++) {
            String previous = result;
            for (Rule rule : RULES) {
                result = rule.apply(result);
            }
            if (result.equals(previous)) break;
        }
        return result.strip();
    }

    private static String decodeNumericHtmlEntities(String input) {
        Matcher matcher = NUMERIC_HTML_ENTITY_PATTERN.matcher(input);
        StringBuilder decoded = new StringBuilder(input.length());

        while (matcher.find()) {
            String entityValue = matcher.group(1);
            String replacement = matcher.group();
            try {
                int codePoint = entityValue.startsWith("x") || entityValue.startsWith("X")
                    ? Integer.parseInt(entityValue.substring(1), 16)
                    : Integer.parseInt(entityValue, 10);
                if (Character.isValidCodePoint(codePoint)) {
                    replacement = new String(Character.toChars(codePoint));
                }
            } catch (IllegalArgumentException _) {
                replacement = matcher.group();
            }
            matcher.appendReplacement(decoded, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(decoded);
        return decoded.toString();
    }

    private static String decodeNamedHtmlEntities(String input) {
        Matcher matcher = NAMED_HTML_ENTITY_PATTERN.matcher(input);
        StringBuilder decoded = new StringBuilder(input.length());

        while (matcher.find()) {
            String entityName = matcher.group(1).toLowerCase(Locale.ROOT);
            String replacement = NAMED_ENTITIES.getOrDefault(entityName, matcher.group());
            matcher.appendReplacement(decoded, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(decoded);
        return decoded.toString();
    }
}

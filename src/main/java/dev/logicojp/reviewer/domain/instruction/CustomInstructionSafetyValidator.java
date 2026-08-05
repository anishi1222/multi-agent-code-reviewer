package dev.logicojp.reviewer.domain.instruction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// Validates custom instruction content for basic prompt-injection safeguards.
///
/// SLF4J logging has been removed from this domain class. Resource-loading
/// fallbacks proceed silently; callers that need diagnostics should wrap the
/// public API and log at the infrastructure layer.
public final class CustomInstructionSafetyValidator {

    private static final String SUSPICIOUS_PATTERNS_RESOURCE = "safety/suspicious-patterns.txt";

    private static final int MAX_INSTRUCTION_SIZE = 32 * 1024;
    private static final int MAX_UNTRUSTED_INSTRUCTION_SIZE = 8 * 1024;
    private static final int MAX_INSTRUCTION_LINES = 300;

    private static final List<String> DEFAULT_SUSPICIOUS_PATTERN_TEXTS = List.of(
        "ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions?",
        "disregard\\s+(all\\s+)?(previous|prior|above)",
        "forget\\s+(all\\s+)?(previous|prior)\\s+instructions?",
        "(ignore|forget|discard)\\s+(the\\s+)?(rules|guardrails|policy|constraints)",
        "(bypass|disable|turn\\s+off)\\s+(the\\s+)?(safety|guardrails|restrictions)",
        "(override|replace)\\s+(the\\s+)?(system|developer)\\s+prompt",
        "(you\\s+are\\s+now|act\\s+as\\s+if\\s+you\\s+are)",
        "(follow\\s+only|prioritize\\s+only)\\s+(the\\s+)?(next|following)\\s+instructions?",
        "(以下|上記|これまで|前|以前)\\s*の?\\s*指示\\s*を\\s*無視",
        "(ルール|方針|制約)\\s*を\\s*(忘れて|無視して)",
        "システム\\s*プロンプト\\s*(を)?\\s*(上書き|無視|無効化)",
        "(모든|이전)\\s*지시\\s*(를)?\\s*무시",
        "(忽略|无视)\\s*(所有)?\\s*(之前|以上)\\s*的?\\s*指[示令]"
    );

    private static final List<Pattern> SUSPICIOUS_PATTERNS = loadSuspiciousPatterns();
    private static final Pattern SUSPICIOUS_COMBINED_PATTERN = Pattern.compile(
        SUSPICIOUS_PATTERNS.stream().map(Pattern::pattern).collect(Collectors.joining("|")),
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile("[\\p{Cf}\\p{Cc}]");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern DELIMITER_INJECTION_PATTERN = Pattern.compile(
        "---\\s*(BEGIN|END|SYSTEM|OVERRIDE)(\\s+PROJECT\\s+INSTRUCTIONS)?"
            + "|BEGIN\\s+PROJECT\\s+INSTRUCTIONS"
            + "|END\\s+PROJECT\\s+INSTRUCTIONS"
            + "|</user_provided_instruction>",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern ALLOWED_CHAR_RANGE = Pattern.compile(
        "^[\\x20-\\x7E"
            + "\\u3000-\\u303F"
            + "\\u3040-\\u309F"
            + "\\u30A0-\\u30FF"
            + "\\u4E00-\\u9FFF"
            + "\\uFF00-\\uFFEF"
            + "\\uAC00-\\uD7AF"
            + "\\u2000-\\u206F"
            + "\\u2190-\\u21FF"
            + "\\u2500-\\u257F"
            + "\\u2580-\\u259F"
            + "\\u25A0-\\u25FF"
            + "\\u2600-\\u26FF"
            + "\\t\\n\\r"
            + "]*$", Pattern.DOTALL);

    private static List<Pattern> loadSuspiciousPatterns() {
        return loadPatternTextsFromResource().stream()
            .map(text -> Pattern.compile(text, Pattern.CASE_INSENSITIVE))
            .toList();
    }

    private static List<String> loadPatternTextsFromResource() {
        InputStream stream = CustomInstructionSafetyValidator.class.getClassLoader()
            .getResourceAsStream(SUSPICIOUS_PATTERNS_RESOURCE);
        if (stream == null) {
            return DEFAULT_SUSPICIOUS_PATTERN_TEXTS;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            List<String> patterns = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    patterns.add(trimmed);
                }
            }

            if (patterns.isEmpty()) {
                return DEFAULT_SUSPICIOUS_PATTERN_TEXTS;
            }

            return List.copyOf(patterns);
        } catch (IOException e) {
            return DEFAULT_SUSPICIOUS_PATTERN_TEXTS;
        }
    }

    public record ValidationResult(boolean safe, String reason) {}

    private CustomInstructionSafetyValidator() {
    }

    public static boolean containsSuspiciousPattern(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String normalized = normalize(content);
        return SUSPICIOUS_COMBINED_PATTERN.matcher(normalized).find()
            || DELIMITER_INJECTION_PATTERN.matcher(normalized).find();
    }

    private static String normalize(String content) {
        String normalized = Normalizer.normalize(content, Normalizer.Form.NFKC);
        String withoutControlChars = CONTROL_CHARS_PATTERN.matcher(normalized).replaceAll("");
        String homoglyphNormalized = normalizeHomoglyphs(withoutControlChars);
        return WHITESPACE_PATTERN.matcher(homoglyphNormalized).replaceAll(" ");
    }

    private static final Map<Character, Character> HOMOGLYPH_MAP = Map.ofEntries(
        Map.entry('\u0406', 'I'),  Map.entry('\u0410', 'A'),  Map.entry('\u0412', 'B'),
        Map.entry('\u0415', 'E'),  Map.entry('\u041A', 'K'),  Map.entry('\u041C', 'M'),
        Map.entry('\u041D', 'H'),  Map.entry('\u041E', 'O'),  Map.entry('\u0420', 'P'),
        Map.entry('\u0421', 'C'),  Map.entry('\u0422', 'T'),  Map.entry('\u0425', 'X'),
        Map.entry('\u0456', 'i'),  Map.entry('\u0430', 'a'),  Map.entry('\u0435', 'e'),
        Map.entry('\u043E', 'o'),  Map.entry('\u0440', 'p'),  Map.entry('\u0441', 'c'),
        Map.entry('\u0443', 'y'),  Map.entry('\u0445', 'x'),  Map.entry('\u03BF', 'o'),
        Map.entry('\u03B1', 'a'),  Map.entry('\u03B5', 'e'),  Map.entry('\u03B9', 'i'),
        Map.entry('\u03C1', 'p'),  Map.entry('\u03C7', 'x'),  Map.entry('\u0391', 'A'),
        Map.entry('\u0392', 'B'),  Map.entry('\u0395', 'E'),  Map.entry('\u0397', 'H'),
        Map.entry('\u0399', 'I'),  Map.entry('\u039A', 'K'),  Map.entry('\u039C', 'M'),
        Map.entry('\u039D', 'N'),  Map.entry('\u039F', 'O'),  Map.entry('\u03A1', 'P'),
        Map.entry('\u03A4', 'T'),  Map.entry('\u03A5', 'Y'),  Map.entry('\u03A7', 'X')
    );

    private static String normalizeHomoglyphs(String text) {
        char[] chars = text.toCharArray();
        boolean modified = false;
        for (int i = 0; i < chars.length; i++) {
            Character replacement = HOMOGLYPH_MAP.get(chars[i]);
            if (replacement != null) {
                chars[i] = replacement;
                modified = true;
            }
        }
        return modified ? new String(chars) : text;
    }
}

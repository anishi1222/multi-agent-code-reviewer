package dev.logicojp.reviewer.instruction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/// Validates custom instruction content for basic prompt-injection safeguards.
public final class CustomInstructionSafetyValidator {

    private static final Logger logger = LoggerFactory.getLogger(CustomInstructionSafetyValidator.class);
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

    /// Allowed Unicode blocks for untrusted instructions.
    /// Rejects exotic scripts that could hide homoglyph-based prompt injection bypasses.
    private static final Pattern ALLOWED_CHAR_RANGE = Pattern.compile(
        "^[\\x20-\\x7E" +           // ASCII printable
        "\\u3000-\\u303F" +          // CJK Symbols and Punctuation
        "\\u3040-\\u309F" +          // Hiragana
        "\\u30A0-\\u30FF" +          // Katakana
        "\\u4E00-\\u9FFF" +          // CJK Unified Ideographs
        "\\uFF00-\\uFFEF" +          // Halfwidth and Fullwidth Forms
        "\\uAC00-\\uD7AF" +          // Hangul Syllables
        "\\u2000-\\u206F" +          // General Punctuation
        "\\u2190-\\u21FF" +          // Arrows
        "\\u2500-\\u257F" +          // Box Drawing
        "\\u2580-\\u259F" +          // Block Elements
        "\\u25A0-\\u25FF" +          // Geometric Shapes
        "\\u2600-\\u26FF" +          // Miscellaneous Symbols
        "\\t\\n\\r" +               // Whitespace
        "]*$", Pattern.DOTALL);

    private static List<Pattern> loadSuspiciousPatterns() {
        return loadPatternTextsFromResource().stream()
            .map(text -> Pattern.compile(text, Pattern.CASE_INSENSITIVE))
            .toList();
    }

    private static List<String> loadPatternTextsFromResource() {
        InputStream stream = CustomInstructionSafetyValidator.class.getClassLoader()
            .getResourceAsStream(SUSPICIOUS_PATTERNS_RESOURCE);
        if (stream == null) {
            logger.debug("Suspicious pattern resource not found: {}", SUSPICIOUS_PATTERNS_RESOURCE);
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
                logger.debug("Suspicious pattern resource is empty: {}", SUSPICIOUS_PATTERNS_RESOURCE);
                return DEFAULT_SUSPICIOUS_PATTERN_TEXTS;
            }

            return List.copyOf(patterns);
        } catch (IOException e) {
            logger.debug("Failed to read suspicious patterns resource '{}': {}",
                SUSPICIOUS_PATTERNS_RESOURCE, e.getMessage());
            return DEFAULT_SUSPICIOUS_PATTERN_TEXTS;
        }
    }

    public record ValidationResult(boolean safe, String reason) {}

    private CustomInstructionSafetyValidator() {
    }

    static ValidationResult validate(CustomInstruction instruction) {
        return validate(instruction, false);
    }

    public static ValidationResult validate(CustomInstruction instruction, boolean trusted) {
        if (instruction == null || instruction.isEmpty()) {
            return new ValidationResult(true, "empty");
        }

        String content = instruction.content();
        int maxSize = trusted ? MAX_INSTRUCTION_SIZE : MAX_UNTRUSTED_INSTRUCTION_SIZE;
        if (content.length() > maxSize) {
            return new ValidationResult(false, "size limit exceeded");
        }
        if (content.lines().count() > MAX_INSTRUCTION_LINES) {
            return new ValidationResult(false, "line count limit exceeded");
        }

        String normalized = normalize(content);
        if (SUSPICIOUS_COMBINED_PATTERN.matcher(normalized).find()) {
            return new ValidationResult(false, "potential prompt-injection pattern");
        }
        if (DELIMITER_INJECTION_PATTERN.matcher(normalized).find()) {
            return new ValidationResult(false, "potential delimiter injection pattern");
        }

        if (!trusted && !ALLOWED_CHAR_RANGE.matcher(content).matches()) {
            return new ValidationResult(false, "contains characters outside allowed Unicode ranges");
        }

        return new ValidationResult(true, "ok");
    }

    public static List<CustomInstruction> filterSafe(List<CustomInstruction> instructions, String logPrefix) {
        return filterSafe(instructions, logPrefix, false);
    }

    public static List<CustomInstruction> filterSafe(List<CustomInstruction> instructions,
                                                     String logPrefix,
                                                     boolean trusted) {
        if (instructions == null || instructions.isEmpty()) {
            return List.of();
        }
        return instructions.stream()
            .filter(instruction -> {
                ValidationResult validation = validate(instruction, trusted);
                if (!validation.safe()) {
                    logger.warn("{} {}: {}", logPrefix, instruction.sourcePath(), validation.reason());
                }
                return validation.safe();
            })
            .toList();
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
        Map.entry('\u0406', 'I'),  // Cyrillic І → I
        Map.entry('\u0410', 'A'),  // Cyrillic А → A
        Map.entry('\u0412', 'B'),  // Cyrillic В → B
        Map.entry('\u0415', 'E'),  // Cyrillic Е → E
        Map.entry('\u041A', 'K'),  // Cyrillic К → K
        Map.entry('\u041C', 'M'),  // Cyrillic М → M
        Map.entry('\u041D', 'H'),  // Cyrillic Н → H
        Map.entry('\u041E', 'O'),  // Cyrillic О → O
        Map.entry('\u0420', 'P'),  // Cyrillic Р → P
        Map.entry('\u0421', 'C'),  // Cyrillic С → C
        Map.entry('\u0422', 'T'),  // Cyrillic Т → T
        Map.entry('\u0425', 'X'),  // Cyrillic Х → X
        Map.entry('\u0456', 'i'),  // Cyrillic і → i
        Map.entry('\u0430', 'a'),  // Cyrillic а → a
        Map.entry('\u0435', 'e'),  // Cyrillic е → e
        Map.entry('\u043E', 'o'),  // Cyrillic о → o
        Map.entry('\u0440', 'p'),  // Cyrillic р → p
        Map.entry('\u0441', 'c'),  // Cyrillic с → c
        Map.entry('\u0443', 'y'),  // Cyrillic у → y
        Map.entry('\u0445', 'x'),  // Cyrillic х → x
        Map.entry('\u03BF', 'o'),  // Greek ο → o
        Map.entry('\u03B1', 'a'),  // Greek α → a
        Map.entry('\u03B5', 'e'),  // Greek ε → e
        Map.entry('\u03B9', 'i'),  // Greek ι → i
        Map.entry('\u03C1', 'p'),  // Greek ρ → p
        Map.entry('\u03C7', 'x'),  // Greek χ → x
        Map.entry('\u0391', 'A'),  // Greek Α → A
        Map.entry('\u0392', 'B'),  // Greek Β → B
        Map.entry('\u0395', 'E'),  // Greek Ε → E
        Map.entry('\u0397', 'H'),  // Greek Η → H
        Map.entry('\u0399', 'I'),  // Greek Ι → I
        Map.entry('\u039A', 'K'),  // Greek Κ → K
        Map.entry('\u039C', 'M'),  // Greek Μ → M
        Map.entry('\u039D', 'N'),  // Greek Ν → N
        Map.entry('\u039F', 'O'),  // Greek Ο → O
        Map.entry('\u03A1', 'P'),  // Greek Ρ → P
        Map.entry('\u03A4', 'T'),  // Greek Τ → T
        Map.entry('\u03A5', 'Y'),  // Greek Υ → Y
        Map.entry('\u03A7', 'X')   // Greek Χ → X
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

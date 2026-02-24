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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// Validates custom instruction content for basic prompt-injection safeguards.
public final class CustomInstructionSafetyValidator {

    private static final Logger logger = LoggerFactory.getLogger(CustomInstructionSafetyValidator.class);
    private static final String SUSPICIOUS_PATTERNS_RESOURCE = "safety/suspicious-patterns.txt";

    /// Externalized safety limits for instruction validation.
    public record SafetyLimits(
        int maxInstructionSize,
        int maxUntrustedInstructionSize,
        int maxInstructionLines
    ) {
        public static final int DEFAULT_MAX_INSTRUCTION_SIZE = 32 * 1024;
        public static final int DEFAULT_MAX_UNTRUSTED_INSTRUCTION_SIZE = 8 * 1024;
        public static final int DEFAULT_MAX_INSTRUCTION_LINES = 300;

        public SafetyLimits {
            if (maxInstructionSize <= 0) maxInstructionSize = DEFAULT_MAX_INSTRUCTION_SIZE;
            if (maxUntrustedInstructionSize <= 0) maxUntrustedInstructionSize = DEFAULT_MAX_UNTRUSTED_INSTRUCTION_SIZE;
            if (maxInstructionLines <= 0) maxInstructionLines = DEFAULT_MAX_INSTRUCTION_LINES;
        }

        public static SafetyLimits defaults() {
            return new SafetyLimits(0, 0, 0);
        }
    }

    private static final SafetyLimits DEFAULT_LIMITS = SafetyLimits.defaults();
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
        "---\\s*(BEGIN|END|SYSTEM|OVERRIDE)|</user_provided_instruction>",
        Pattern.CASE_INSENSITIVE);

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
        return validate(instruction, false, DEFAULT_LIMITS);
    }

    public static ValidationResult validate(CustomInstruction instruction, boolean trusted) {
        return validate(instruction, trusted, DEFAULT_LIMITS);
    }

    public static ValidationResult validate(CustomInstruction instruction, boolean trusted, SafetyLimits limits) {
        if (instruction == null || instruction.isEmpty()) {
            return new ValidationResult(true, "empty");
        }

        String content = instruction.content();
        int maxSize = trusted ? limits.maxInstructionSize() : limits.maxUntrustedInstructionSize();
        if (content.length() > maxSize) {
            return new ValidationResult(false, "size limit exceeded");
        }
        if (exceedsLineLimit(content, limits.maxInstructionLines())) {
            return new ValidationResult(false, "line count limit exceeded");
        }

        String normalized = normalize(content);
        if (SUSPICIOUS_COMBINED_PATTERN.matcher(normalized).find()) {
            return new ValidationResult(false, "potential prompt-injection pattern");
        }
        if (DELIMITER_INJECTION_PATTERN.matcher(normalized).find()) {
            return new ValidationResult(false, "potential delimiter injection pattern");
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

    private static boolean exceedsLineLimit(String content, int maxLines) {
        if (content.isEmpty()) {
            return false;
        }

        int lines = 1;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '\n') {
                if (++lines > maxLines) {
                    return true;
                }
            } else if (ch == '\r') {
                if (++lines > maxLines) {
                    return true;
                }
                if (i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
            }
        }
        return false;
    }

    private static String normalize(String content) {
        String normalized = Normalizer.normalize(content, Normalizer.Form.NFKC);
        String withoutControlChars = CONTROL_CHARS_PATTERN.matcher(normalized).replaceAll("");
        String homoglyphNormalized = normalizeHomoglyphs(withoutControlChars);
        return WHITESPACE_PATTERN.matcher(homoglyphNormalized).replaceAll(" ");
    }

    private static String normalizeHomoglyphs(String text) {
        char[] chars = text.toCharArray();
        boolean modified = false;
        for (int i = 0; i < chars.length; i++) {
            char normalized = normalizeHomoglyph(chars[i]);
            if (normalized != chars[i]) {
                chars[i] = normalized;
                modified = true;
            }
        }
        return modified ? new String(chars) : text;
    }

    private static char normalizeHomoglyph(char value) {
        return switch (value) {
            case '\u0456', '\u03B9' -> 'i'; // Cyrillic і, Greek ι
            case '\u0430', '\u03B1' -> 'a'; // Cyrillic а, Greek α
            case '\u0435', '\u03B5' -> 'e'; // Cyrillic е, Greek ε
            case '\u043E', '\u03BF' -> 'o'; // Cyrillic о, Greek ο
            case '\u0440' -> 'p'; // Cyrillic р
            case '\u0441' -> 'c'; // Cyrillic с
            case '\u0443' -> 'y'; // Cyrillic у
            case '\u0445' -> 'x'; // Cyrillic х
            case '\u0391' -> 'A'; // Greek Α
            case '\u0392' -> 'B'; // Greek Β
            case '\u0395' -> 'E'; // Greek Ε
            case '\u0397' -> 'H'; // Greek Η
            case '\u0399' -> 'I'; // Greek Ι
            case '\u039A' -> 'K'; // Greek Κ
            case '\u039C' -> 'M'; // Greek Μ
            case '\u039D' -> 'N'; // Greek Ν
            case '\u039F' -> 'O'; // Greek Ο
            case '\u03A1' -> 'P'; // Greek Ρ
            case '\u03A4' -> 'T'; // Greek Τ
            case '\u03A5' -> 'Y'; // Greek Υ
            case '\u03A7' -> 'X'; // Greek Χ
            case '\u2014' -> '-'; // em-dash
            case '\u2013' -> '-'; // en-dash
            case '\u2015' -> '-'; // horizontal bar
            case '\uFE58' -> '-'; // small em-dash
            case '\uFE63' -> '-'; // small hyphen-minus
            case '\uFF0D' -> '-'; // fullwidth hyphen-minus
            default -> value;
        };
    }
}

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

    private static String normalizeHomoglyphs(String text) {
        return text
            .replace('\u0456', 'i')  // Cyrillic і → i
            .replace('\u0430', 'a')  // Cyrillic а → a
            .replace('\u0435', 'e')  // Cyrillic е → e
            .replace('\u043E', 'o')  // Cyrillic о → o
            .replace('\u0440', 'p')  // Cyrillic р → p
            .replace('\u0441', 'c')  // Cyrillic с → c
            .replace('\u0443', 'y')  // Cyrillic у → y
            .replace('\u0445', 'x')  // Cyrillic х → x
            .replace('\u03BF', 'o')  // Greek ο → o
            .replace('\u03B1', 'a')  // Greek α → a
            .replace('\u03B5', 'e')  // Greek ε → e
            .replace('\u03B9', 'i')  // Greek ι → i
            .replace('\u0391', 'A')  // Greek Α → A
            .replace('\u0392', 'B')  // Greek Β → B
            .replace('\u0395', 'E')  // Greek Ε → E
            .replace('\u0397', 'H')  // Greek Η → H
            .replace('\u0399', 'I')  // Greek Ι → I
            .replace('\u039A', 'K')  // Greek Κ → K
            .replace('\u039C', 'M')  // Greek Μ → M
            .replace('\u039D', 'N')  // Greek Ν → N
            .replace('\u039F', 'O')  // Greek Ο → O
            .replace('\u03A1', 'P')  // Greek Ρ → P
            .replace('\u03A4', 'T')  // Greek Τ → T
            .replace('\u03A5', 'Y')  // Greek Υ → Y
            .replace('\u03A7', 'X'); // Greek Χ → X
    }
}

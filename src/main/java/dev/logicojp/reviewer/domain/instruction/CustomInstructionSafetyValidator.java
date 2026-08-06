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

/// Pattern-matching component for prompt-injection safeguards on free-text content.
///
/// ## Scope (narrowed by ADR-0007 D2)
///
/// This class used to hold both *patterns* and *size limits*. The size limits
/// (`MAX_INSTRUCTION_SIZE`, `MAX_UNTRUSTED_INSTRUCTION_SIZE`, `MAX_INSTRUCTION_LINES`) were
/// private and unreferenced — a second, invisible policy home that had drifted out of use
/// while [dev.logicojp.reviewer.domain.agent.AgentDefinitionPolicy] enforced a different,
/// looser set. That split is exactly what SEC-H1 was: nobody could tell which file decided
/// the limit, so the strict one silently decided nothing.
///
/// Those limits now live in `AgentDefinitionPolicy`, which is the single policy owner. What
/// remains here is pattern matching: suspicious-phrase detection, delimiter-injection
/// detection, and the allowed-character range. `AgentDefinitionPolicy` calls into this class
/// for the character check rather than duplicating the range, so there is still exactly one
/// definition of each rule — it is just no longer the case that a *decision* lives here.
///
/// SLF4J logging has been removed from this domain class. Resource-loading
/// fallbacks proceed silently; callers that need diagnostics should wrap the
/// public API and log at the infrastructure layer.
public final class CustomInstructionSafetyValidator {

    private static final String SUSPICIOUS_PATTERNS_RESOURCE = "safety/suspicious-patterns.txt";

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
            // General Punctuation, split to exclude the deception characters this range
            // exists to keep out. The block as a whole contains U+200B-U+200F (zero-width and
            // directional marks), U+202A-U+202E (bidirectional embedding and override),
            // U+2060-U+2064 (invisible operators) and U+2066-U+2069 (bidirectional isolates).
            // Whitelisting U+2000-U+206F wholesale therefore permitted exactly the characters
            // that let a definition read one way to a reviewer and another to the model.
            // U+203B and the other Japanese-relevant marks live above U+202F and are retained.
            + "\\u2000-\\u200A"
            + "\\u2010-\\u2027"
            + "\\u202F-\\u205F"
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

    private CustomInstructionSafetyValidator() {
    }

    /// Reports whether every character in `content` falls inside the allowed ranges.
    ///
    /// The range covers printable ASCII, CJK (kana, ideographs, full-width forms), Hangul,
    /// common punctuation/arrow/box-drawing blocks, and the usual whitespace. It deliberately
    /// excludes bidirectional-override and other invisible formatting characters, which are
    /// the classic way to make a definition read differently to a human than to a model.
    ///
    /// This check is applied only to repository-supplied definitions
    /// ([dev.logicojp.reviewer.domain.agent.AgentTrustProfile#enforcesCharset()]). Operator
    /// input is exempt: an operator who wants an unusual character in their own prompt has
    /// no one to deceive but themselves, and rejecting it only breaks legitimate use.
    ///
    /// @param content text to inspect; null and blank are vacuously allowed
    /// @return true when all characters are permitted
    public static boolean containsOnlyAllowedCharacters(String content) {
        if (content == null || content.isEmpty()) {
            return true;
        }
        return ALLOWED_CHAR_RANGE.matcher(content).matches();
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

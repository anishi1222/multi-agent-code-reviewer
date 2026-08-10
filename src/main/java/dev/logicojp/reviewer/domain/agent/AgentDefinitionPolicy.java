package dev.logicojp.reviewer.domain.agent;

import dev.logicojp.reviewer.domain.instruction.CustomInstructionSafetyValidator;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/// Enforces trust-boundary and safety policies on agent definitions loaded
/// from {@code .agent.md} files.
///
/// Agent definition files live inside the repository being reviewed and are
/// therefore <b>semi-trusted</b>. This class limits the blast radius by:
/// - Model allowlist
/// - Name/identifier constraints
/// - Agent-level kill switch (enabled: false)
/// - Content-size limits
/// - Frontmatter-required policy
///
/// All checks are fast, deterministic, and side-effect-free.
public final class AgentDefinitionPolicy {

    private static final Logger logger = Logger.getLogger(AgentDefinitionPolicy.class.getName());

    // ── size limits (single policy owner, ADR-0007 D2) ──────────
    //
    // MAX_INSTRUCTION_SIZE / MAX_UNTRUSTED_INSTRUCTION_SIZE / MAX_INSTRUCTION_LINES were
    // previously private and unreferenced in CustomInstructionSafetyValidator, so the
    // "untrusted" limit decided nothing (SEC-H1). They live here now, are public, and are
    // consumed by AgentTrustProfile — the one place that maps provenance to limits.
    //
    // All limits count UTF-16 characters, not bytes. The pre-existing checks already
    // measured characters; re-expressing them as bytes would tighten them roughly 3x for the
    // Japanese text these definitions are largely written in, silently rejecting definitions
    // that are legal today. AgentTrustProfileTest pins this with a multi-byte fixture.

    public static final int MAX_AGENT_FILE_SIZE = 64 * 1024;
    public static final int MAX_UNTRUSTED_AGENT_FILE_SIZE = 16 * 1024;
    public static final int MAX_INSTRUCTION_SIZE = 32 * 1024;
    public static final int MAX_UNTRUSTED_INSTRUCTION_SIZE = 8 * 1024;
    public static final int MAX_INSTRUCTION_LINES = 300;
    public static final int MAX_AGENT_NAME_LENGTH = 64;
    public static final int MAX_DISPLAY_NAME_LENGTH = 200;
    public static final int MAX_FOCUS_AREAS = 50;
    public static final int MAX_FOCUS_AREA_LENGTH = 200;
    public static final int MAX_DIALOGUE_ROUNDS = 10;

    /// Languages a definition may request.
    ///
    /// `language` is not decorative: it reaches
    /// `RubberDuckDialogueRunner.loadTemplateForLanguage`, which builds a template key as
    /// `"rubber-duck-initial-" + language` and loads it. An unconstrained value is therefore
    /// attacker-influenced input to a resource lookup. The allowlist matches the templates
    /// that actually exist on disk (`rubber-duck-{initial,counter}-{ja,en}.md`), so it closes
    /// SEC-L2 without removing any working configuration.
    public static final Set<String> ALLOWED_LANGUAGES = Set.of("ja", "en");

    private static final List<String> ALLOWED_MODEL_PREFIXES = List.of(
        "claude-", "gpt-", "o3", "o4-mini", "gemini-"
    );

    private static final Pattern VALID_NAME_PATTERN = Pattern.compile(
        "^[a-z0-9][a-z0-9-]{0," + (MAX_AGENT_NAME_LENGTH - 1) + "}$"
    );

    public static final Set<String> KNOWN_FRONTMATTER_KEYS = Set.of(
        "name", "description", "displayName", "model",
        "peer-model", "rubber-duck", "dialogue-rounds",
        "language", "enabled"
    );

    // ── rule identifiers ────────────────────────────────────────
    // Every rejection names the rule that fired (ADR-0007 D4). Operators grep these;
    // they are part of the observable contract and must not be renamed casually.

    public static final String RULE_FILE_EMPTY = "AGENT-FILE-EMPTY";
    public static final String RULE_FILE_SIZE = "AGENT-FILE-SIZE";
    public static final String RULE_FRONTMATTER_DELIMITER = "AGENT-FRONTMATTER-DELIMITER";
    public static final String RULE_FRONTMATTER_MISSING = "AGENT-FRONTMATTER-MISSING";
    public static final String RULE_FRONTMATTER_UNKNOWN_KEY = "AGENT-FRONTMATTER-UNKNOWN-KEY";
    public static final String RULE_AGENT_DISABLED = "AGENT-DISABLED";
    public static final String RULE_NAME = "AGENT-NAME";
    public static final String RULE_MODEL = "AGENT-MODEL";
    public static final String RULE_FOCUS_AREAS = "AGENT-FOCUS-AREAS";
    public static final String RULE_FIELD_SIZE = "AGENT-FIELD-SIZE";
    public static final String RULE_FIELD_LINES = "AGENT-FIELD-LINES";
    public static final String RULE_FIELD_CHARSET = "AGENT-FIELD-CHARSET";
    public static final String RULE_DISPLAY_NAME = "AGENT-DISPLAY-NAME";
    public static final String RULE_LANGUAGE = "AGENT-LANGUAGE";
    public static final String RULE_DIALOGUE_ROUNDS = "AGENT-DIALOGUE-ROUNDS";

    private AgentDefinitionPolicy() {
    }

    /// Result of policy validation.
    ///
    /// @param accepted whether the definition passed
    /// @param ruleId   identifier of the rule that rejected it, or null when accepted
    /// @param reason   human-readable reason, or null when accepted
    public record PolicyResult(boolean accepted, String ruleId, String reason) {
        public static PolicyResult accept() {
            return new PolicyResult(true, null, null);
        }

        public static PolicyResult reject(String ruleId, String reason) {
            return new PolicyResult(false, ruleId, reason);
        }
    }

    /// Validates raw agent file content before parsing, under the trust profile for
    /// `source`.
    ///
    /// @param content  raw file content
    /// @param filename source filename, used in messages
    /// @param source   provenance of the directory the file was found in
    /// @return accept, or reject naming the violated rule
    public static PolicyResult validateRawContent(String content, String filename, AgentSource source) {
        AgentTrustProfile profile = AgentTrustProfile.forSource(source);
        if (content == null || content.isBlank()) {
            return PolicyResult.reject(RULE_FILE_EMPTY, "empty agent file");
        }
        if (content.length() > profile.maxFileChars()) {
            return PolicyResult.reject(RULE_FILE_SIZE,
                "agent file exceeds maximum size for %s definitions (%d characters > %d)"
                    .formatted(profile.describe(source), content.length(), profile.maxFileChars()));
        }
        if (!content.startsWith("---")) {
            return PolicyResult.reject(RULE_FRONTMATTER_DELIMITER,
                "agent file '%s' does not start with frontmatter delimiter (---)".formatted(filename));
        }
        return PolicyResult.accept();
    }

    /// Validates a parsed [AgentConfig] against the trust profile selected by its
    /// [AgentConfig#source()].
    ///
    /// ## Every element is covered on purpose
    ///
    /// ADR-0007 D3 requires a stated verdict for *every* component of [AgentConfig], not just
    /// the ones that happened to be checked. An element with no rule is not "safe by
    /// default" — it is an unreviewed input path, and `language` (SEC-L2) is the proof: it
    /// looked inert but reached a template lookup. [AgentSchemaCoverage] enumerates the
    /// record components reflectively and fails if one is added without a decision here, so
    /// this method cannot silently fall behind the type it validates.
    ///
    /// @param config the parsed definition; its `source` selects the profile
    /// @return accept, or reject naming the violated rule
    public static PolicyResult validateParsed(AgentConfig config) {
        AgentSource source = config.source();
        AgentTrustProfile profile = AgentTrustProfile.forSource(source);

        PolicyResult nameResult = validateName(config.name());
        if (!nameResult.accepted()) return nameResult;

        PolicyResult displayNameResult = validateDisplayName(config.displayName(), profile);
        if (!displayNameResult.accepted()) return displayNameResult;

        PolicyResult modelResult = validateModel(config.model(), "model");
        if (!modelResult.accepted()) return modelResult;

        if (config.peerModel() != null) {
            PolicyResult peerResult = validateModel(config.peerModel(), "peer-model");
            if (!peerResult.accepted()) return peerResult;
        }

        PolicyResult freeTextResult = validateFreeTextFields(config, profile, source);
        if (!freeTextResult.accepted()) return freeTextResult;

        PolicyResult focusResult = validateFocusAreas(config.focusAreas(), profile);
        if (!focusResult.accepted()) return focusResult;

        PolicyResult languageResult = validateLanguage(config.language());
        if (!languageResult.accepted()) return languageResult;

        if (config.dialogueRounds() < 0 || config.dialogueRounds() > MAX_DIALOGUE_ROUNDS) {
            return PolicyResult.reject(RULE_DIALOGUE_ROUNDS,
                "dialogue-rounds out of range (0-%d): %d".formatted(MAX_DIALOGUE_ROUNDS, config.dialogueRounds()));
        }
        return PolicyResult.accept();
    }

    /// Applies the size, line-count, and charset bounds to the three free-text fields.
    ///
    /// These are the fields that become prompt text, so they are the ones the strict profile
    /// actually needs to bound: a repository that can make them arbitrarily long controls
    /// most of what the model reads.
    private static PolicyResult validateFreeTextFields(AgentConfig config, AgentTrustProfile profile,
                                                       AgentSource source) {
        record Field(String name, String value) {}
        List<Field> fields = List.of(
            new Field("systemPrompt", config.systemPrompt()),
            new Field("instruction", config.instruction()),
            new Field("outputFormat", config.outputFormat())
        );

        for (Field field : fields) {
            if (field.value() == null) continue;

            if (field.value().length() > profile.maxInstructionChars()) {
                return PolicyResult.reject(RULE_FIELD_SIZE,
                    "%s exceeds maximum size for %s definitions (%d characters > %d)"
                        .formatted(field.name(), profile.describe(source),
                            field.value().length(), profile.maxInstructionChars()));
            }

            long lines = field.value().lines().count();
            if (lines > profile.maxInstructionLines()) {
                return PolicyResult.reject(RULE_FIELD_LINES,
                    "%s exceeds maximum line count for %s definitions (%d lines > %d)"
                        .formatted(field.name(), profile.describe(source),
                            lines, profile.maxInstructionLines()));
            }

            if (profile.enforcesCharset()
                && !CustomInstructionSafetyValidator.containsOnlyAllowedCharacters(field.value())) {
                return PolicyResult.reject(RULE_FIELD_CHARSET,
                    "%s contains characters outside the allowed range; %s definitions may not "
                        .formatted(field.name(), profile.describe(source))
                        + "use invisible or bidirectional formatting characters");
            }
        }
        return PolicyResult.accept();
    }

    /// Bounds the display name, and applies the charset rule to it under the strict profile.
    ///
    /// The display name is echoed into report headings, so an unbounded or
    /// bidirectional-override-laden value is a presentation-spoofing vector even though it
    /// never reaches the model.
    public static PolicyResult validateDisplayName(String displayName, AgentTrustProfile profile) {
        if (displayName == null) return PolicyResult.accept();
        if (displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            return PolicyResult.reject(RULE_DISPLAY_NAME,
                "displayName exceeds maximum length (%d > %d)"
                    .formatted(displayName.length(), MAX_DISPLAY_NAME_LENGTH));
        }
        if (profile.enforcesCharset()
            && !CustomInstructionSafetyValidator.containsOnlyAllowedCharacters(displayName)) {
            return PolicyResult.reject(RULE_DISPLAY_NAME,
                "displayName contains characters outside the allowed range");
        }
        return PolicyResult.accept();
    }

    /// Constrains `language` to the allowlist.
    ///
    /// See [#ALLOWED_LANGUAGES] for why this is a security rule and not a formatting
    /// preference. Null is accepted because the field is optional and a downstream default
    /// applies; a *present but unknown* value is rejected.
    public static PolicyResult validateLanguage(String language) {
        if (language == null || language.isBlank()) {
            return PolicyResult.accept();
        }
        if (!ALLOWED_LANGUAGES.contains(language.toLowerCase(Locale.ROOT))) {
            return PolicyResult.reject(RULE_LANGUAGE,
                "language '%s' is not supported (allowed: %s)"
                    .formatted(language, ALLOWED_LANGUAGES.stream().sorted().toList()));
        }
        return PolicyResult.accept();
    }

    public static PolicyResult validateName(String name) {
        if (name == null || name.isBlank()) {
            return PolicyResult.reject(RULE_NAME, "agent name is blank");
        }
        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            return PolicyResult.reject(RULE_NAME,
                "agent name contains invalid characters or is too long: '%s'".formatted(name));
        }
        return PolicyResult.accept();
    }

    public static PolicyResult validateModel(String model, String fieldName) {
        if (model == null || model.isBlank()) {
            return PolicyResult.accept();
        }
        String lower = model.toLowerCase(Locale.ROOT);
        boolean allowed = ALLOWED_MODEL_PREFIXES.stream().anyMatch(lower::startsWith);
        if (!allowed) {
            return PolicyResult.reject(RULE_MODEL,
                "%s '%s' is not in the allowed model list".formatted(fieldName, model));
        }
        return PolicyResult.accept();
    }

    public static PolicyResult validateFocusAreas(List<String> focusAreas) {
        return validateFocusAreas(focusAreas, AgentTrustProfile.forSource(AgentSource.USER_SUPPLIED));
    }

    /// Bounds focus areas, and applies the charset rule per element under the strict profile.
    ///
    /// Focus areas are rendered into the prompt as bullet items, so they carry the same
    /// deception risk as the free-text fields and get the same character treatment.
    ///
    /// @param focusAreas the declared focus areas, may be null
    /// @param profile    limits selected by provenance
    /// @return accept, or reject naming the violated rule
    public static PolicyResult validateFocusAreas(List<String> focusAreas, AgentTrustProfile profile) {
        if (focusAreas == null) return PolicyResult.accept();
        if (focusAreas.size() > MAX_FOCUS_AREAS) {
            return PolicyResult.reject(RULE_FOCUS_AREAS,
                "too many focus areas (%d > %d)".formatted(focusAreas.size(), MAX_FOCUS_AREAS));
        }
        for (String area : focusAreas) {
            if (area == null) continue;
            if (area.length() > MAX_FOCUS_AREA_LENGTH) {
                return PolicyResult.reject(RULE_FOCUS_AREAS,
                    "focus area text exceeds maximum length (%d > %d)"
                        .formatted(area.length(), MAX_FOCUS_AREA_LENGTH));
            }
            if (profile.enforcesCharset()
                && !CustomInstructionSafetyValidator.containsOnlyAllowedCharacters(area)) {
                return PolicyResult.reject(RULE_FOCUS_AREAS,
                    "focus area contains characters outside the allowed range");
            }
        }
        return PolicyResult.accept();
    }

    /// Audits frontmatter keys against the known set.
    ///
    /// @param metadata frontmatter key-value pairs
    /// @param filename source filename, used in messages
    /// @param source   provenance of the directory the file was found in
    /// @return accept, or reject naming the violated rule
    public static PolicyResult auditFrontmatterKeys(Map<String, String> metadata, String filename,
                                                    AgentSource source) {
        AgentTrustProfile profile = AgentTrustProfile.forSource(source);
        for (String key : metadata.keySet()) {
            if (KNOWN_FRONTMATTER_KEYS.contains(key)) continue;

            if (profile.rejectsUnknownFrontmatterKeys()) {
                // Closed schema for untrusted definitions: an unrecognised key is a request
                // for behaviour this version does not implement. Warning and continuing would
                // mean a definition written against a newer or imagined schema loads with the
                // key silently dropped, which is precisely the state where a reader believes a
                // constraint is in effect and it is not.
                return PolicyResult.reject(RULE_FRONTMATTER_UNKNOWN_KEY,
                    "agent '%s' declares unrecognized frontmatter key '%s'; %s definitions use a "
                        .formatted(filename, key, profile.describe(source))
                        + "closed schema (allowed: %s)".formatted(
                            KNOWN_FRONTMATTER_KEYS.stream().sorted().toList()));
            }
            logger.warning("Agent '" + filename + "' contains unrecognized frontmatter key: '" + key + "'");
        }
        return PolicyResult.accept();
    }

    /// Checks the {@code enabled} frontmatter flag.
    public static boolean isAgentEnabled(Map<String, String> metadata) {
        String enabled = metadata.get("enabled");
        if (enabled == null) return true;
        return Boolean.parseBoolean(enabled);
    }
}

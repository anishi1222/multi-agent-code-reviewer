package dev.logicojp.reviewer.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/// Enforces trust-boundary and safety policies on agent definitions loaded
/// from `.agent.md` files.
///
/// Agent definition files live inside the repository being reviewed and are
/// therefore **semi-trusted** — an attacker who can land a malicious PR can
/// inject arbitrary content.  This class limits the blast radius by:
///
/// - **Model allowlist** — prevents agent definitions from requesting
///   unexpected / expensive / dangerous model identifiers.
/// - **Name / identifier constraints** — rejects names that contain
///   path-traversal or control characters.
/// - **Agent-level kill switch** — agents can declare `enabled: false`
///   in their frontmatter to be skipped during loading.
/// - **Content-size limits** — prevents denial-of-service through
///   extremely large agent definition files.
/// - **Frontmatter-required policy** — agent files without valid
///   frontmatter are rejected rather than silently accepted with defaults.
///
/// All checks are fast, deterministic, and side-effect-free.
final class AgentDefinitionPolicy {

    private static final Logger logger = LoggerFactory.getLogger(AgentDefinitionPolicy.class);

    /// Maximum allowed size for an agent definition file (64 KiB).
    static final int MAX_AGENT_FILE_SIZE = 64 * 1024;

    /// Maximum allowed agent name length.
    static final int MAX_AGENT_NAME_LENGTH = 64;

    /// Maximum number of focus areas an agent can declare.
    static final int MAX_FOCUS_AREAS = 50;

    /// Maximum length for a single focus-area text.
    static final int MAX_FOCUS_AREA_LENGTH = 200;

    /// Allowed model prefixes.  Only models that start with one of these
    /// prefixes are accepted.  The comparison is case-insensitive.
    private static final List<String> ALLOWED_MODEL_PREFIXES = List.of(
        "claude-",
        "gpt-",
        "o3",
        "o4-mini",
        "gemini-"
    );

    /// Pattern for valid agent names: lowercase alphanumeric + hyphens, 1-64 chars.
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile(
        "^[a-z0-9][a-z0-9-]{0," + (MAX_AGENT_NAME_LENGTH - 1) + "}$"
    );

    /// Known frontmatter keys.  Any key not in this set is flagged (warn, not reject).
    static final Set<String> KNOWN_FRONTMATTER_KEYS = Set.of(
        "name", "description", "displayName", "model",
        "peer-model", "rubber-duck", "dialogue-rounds",
        "language", "enabled"
    );

    private AgentDefinitionPolicy() {
    }

    // ── validation entry point ──────────────────────────────────

    /// Result of policy validation.
    record PolicyResult(boolean accepted, String reason) {
        static PolicyResult accept() {
            return new PolicyResult(true, null);
        }

        static PolicyResult reject(String reason) {
            return new PolicyResult(false, reason);
        }
    }

    /// Validates a raw agent file content **before** parsing.
    /// Checks file-level invariants that do not require full parsing.
    static PolicyResult validateRawContent(String content, String filename) {
        if (content == null || content.isBlank()) {
            return PolicyResult.reject("empty agent file");
        }
        if (content.length() > MAX_AGENT_FILE_SIZE) {
            return PolicyResult.reject(
                "agent file exceeds maximum size (%d bytes > %d)"
                    .formatted(content.length(), MAX_AGENT_FILE_SIZE));
        }
        if (!content.startsWith("---")) {
            return PolicyResult.reject(
                "agent file '%s' does not start with frontmatter delimiter (---)"
                    .formatted(filename));
        }
        return PolicyResult.accept();
    }

    /// Validates a parsed AgentConfig against trust-boundary policies.
    static PolicyResult validateParsed(AgentConfig config) {
        // 1. Name validation
        PolicyResult nameResult = validateName(config.name());
        if (!nameResult.accepted()) {
            return nameResult;
        }

        // 2. Model allowlist
        PolicyResult modelResult = validateModel(config.model(), "model");
        if (!modelResult.accepted()) {
            return modelResult;
        }

        // 3. Peer-model allowlist (if set)
        if (config.peerModel() != null) {
            PolicyResult peerResult = validateModel(config.peerModel(), "peer-model");
            if (!peerResult.accepted()) {
                return peerResult;
            }
        }

        // 4. Focus area limits
        PolicyResult focusResult = validateFocusAreas(config.focusAreas());
        if (!focusResult.accepted()) {
            return focusResult;
        }

        // 5. Dialogue rounds sanity
        if (config.dialogueRounds() < 0 || config.dialogueRounds() > 10) {
            return PolicyResult.reject(
                "dialogue-rounds out of range (0-10): " + config.dialogueRounds());
        }

        return PolicyResult.accept();
    }

    // ── individual checks ───────────────────────────────────────

    static PolicyResult validateName(String name) {
        if (name == null || name.isBlank()) {
            return PolicyResult.reject("agent name is blank");
        }
        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            return PolicyResult.reject(
                "agent name contains invalid characters or is too long: '%s'"
                    .formatted(name));
        }
        return PolicyResult.accept();
    }

    static PolicyResult validateModel(String model, String fieldName) {
        if (model == null || model.isBlank()) {
            // Null/blank models default elsewhere; allow here.
            return PolicyResult.accept();
        }
        String lower = model.toLowerCase(Locale.ROOT);
        boolean allowed = ALLOWED_MODEL_PREFIXES.stream().anyMatch(lower::startsWith);
        if (!allowed) {
            return PolicyResult.reject(
                "%s '%s' is not in the allowed model list".formatted(fieldName, model));
        }
        return PolicyResult.accept();
    }

    static PolicyResult validateFocusAreas(List<String> focusAreas) {
        if (focusAreas == null) {
            return PolicyResult.accept();
        }
        if (focusAreas.size() > MAX_FOCUS_AREAS) {
            return PolicyResult.reject(
                "too many focus areas (%d > %d)".formatted(focusAreas.size(), MAX_FOCUS_AREAS));
        }
        for (String area : focusAreas) {
            if (area != null && area.length() > MAX_FOCUS_AREA_LENGTH) {
                return PolicyResult.reject(
                    "focus area text exceeds maximum length (%d > %d)"
                        .formatted(area.length(), MAX_FOCUS_AREA_LENGTH));
            }
        }
        return PolicyResult.accept();
    }

    // ── frontmatter key audit ───────────────────────────────────

    /// Logs warnings for unrecognized frontmatter keys.
    /// Does NOT reject the agent — unknown keys are informational only.
    static void auditFrontmatterKeys(java.util.Map<String, String> metadata, String filename) {
        for (String key : metadata.keySet()) {
            if (!KNOWN_FRONTMATTER_KEYS.contains(key)) {
                logger.warn("Agent '{}' contains unrecognized frontmatter key: '{}'",
                    filename, key);
            }
        }
    }

    /// Checks the `enabled` frontmatter flag.  Agents that declare
    /// `enabled: false` are silently skipped.
    static boolean isAgentEnabled(java.util.Map<String, String> metadata) {
        String enabled = metadata.get("enabled");
        if (enabled == null) {
            return true;  // default: enabled
        }
        return Boolean.parseBoolean(enabled);
    }
}

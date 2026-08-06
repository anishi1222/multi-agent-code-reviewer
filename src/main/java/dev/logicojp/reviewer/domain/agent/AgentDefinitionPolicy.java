package dev.logicojp.reviewer.domain.agent;

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

    public static final int MAX_AGENT_FILE_SIZE = 64 * 1024;
    public static final int MAX_AGENT_NAME_LENGTH = 64;
    public static final int MAX_FOCUS_AREAS = 50;
    public static final int MAX_FOCUS_AREA_LENGTH = 200;

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

    private AgentDefinitionPolicy() {
    }

    /// Result of policy validation.
    public record PolicyResult(boolean accepted, String reason) {
        public static PolicyResult accept() {
            return new PolicyResult(true, null);
        }

        public static PolicyResult reject(String reason) {
            return new PolicyResult(false, reason);
        }
    }

    /// Validates a raw agent file content before parsing.
    public static PolicyResult validateRawContent(String content, String filename) {
        if (content == null || content.isBlank()) {
            return PolicyResult.reject("empty agent file");
        }
        if (content.length() > MAX_AGENT_FILE_SIZE) {
            return PolicyResult.reject(
                "agent file exceeds maximum size (%d bytes > %d)".formatted(content.length(), MAX_AGENT_FILE_SIZE));
        }
        if (!content.startsWith("---")) {
            return PolicyResult.reject(
                "agent file '%s' does not start with frontmatter delimiter (---)".formatted(filename));
        }
        return PolicyResult.accept();
    }

    /// Validates a parsed AgentConfig against trust-boundary policies.
    public static PolicyResult validateParsed(AgentConfig config) {
        PolicyResult nameResult = validateName(config.name());
        if (!nameResult.accepted()) return nameResult;

        PolicyResult modelResult = validateModel(config.model(), "model");
        if (!modelResult.accepted()) return modelResult;

        if (config.peerModel() != null) {
            PolicyResult peerResult = validateModel(config.peerModel(), "peer-model");
            if (!peerResult.accepted()) return peerResult;
        }

        PolicyResult focusResult = validateFocusAreas(config.focusAreas());
        if (!focusResult.accepted()) return focusResult;

        if (config.dialogueRounds() < 0 || config.dialogueRounds() > 10) {
            return PolicyResult.reject("dialogue-rounds out of range (0-10): " + config.dialogueRounds());
        }
        return PolicyResult.accept();
    }

    public static PolicyResult validateName(String name) {
        if (name == null || name.isBlank()) {
            return PolicyResult.reject("agent name is blank");
        }
        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            return PolicyResult.reject(
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
            return PolicyResult.reject("%s '%s' is not in the allowed model list".formatted(fieldName, model));
        }
        return PolicyResult.accept();
    }

    public static PolicyResult validateFocusAreas(List<String> focusAreas) {
        if (focusAreas == null) return PolicyResult.accept();
        if (focusAreas.size() > MAX_FOCUS_AREAS) {
            return PolicyResult.reject(
                "too many focus areas (%d > %d)".formatted(focusAreas.size(), MAX_FOCUS_AREAS));
        }
        for (String area : focusAreas) {
            if (area != null && area.length() > MAX_FOCUS_AREA_LENGTH) {
                return PolicyResult.reject(
                    "focus area text exceeds maximum length (%d > %d)".formatted(area.length(), MAX_FOCUS_AREA_LENGTH));
            }
        }
        return PolicyResult.accept();
    }

    /// Logs warnings for unrecognized frontmatter keys (does NOT reject the agent).
    public static void auditFrontmatterKeys(Map<String, String> metadata, String filename) {
        for (String key : metadata.keySet()) {
            if (!KNOWN_FRONTMATTER_KEYS.contains(key)) {
                logger.warning("Agent '" + filename + "' contains unrecognized frontmatter key: '" + key + "'");
            }
        }
    }

    /// Checks the {@code enabled} frontmatter flag.
    public static boolean isAgentEnabled(Map<String, String> metadata) {
        String enabled = metadata.get("enabled");
        if (enabled == null) return true;
        return Boolean.parseBoolean(enabled);
    }
}

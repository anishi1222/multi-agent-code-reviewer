package dev.logicojp.reviewer.domain.report;

import dev.logicojp.reviewer.domain.agent.AgentConfig;

import java.time.Instant;
import java.util.regex.Pattern;

/// Factory for constructing {@link ReviewResult} instances from raw agent output.
///
/// Purified from {@code agent.ReviewResultFactory}:
/// - Fixed imports to {@code domain.agent.AgentConfig} and {@code domain.report.ReviewResult}.
/// - No infrastructure or framework dependencies.
public final class ReviewResultFactory {

    private static final Pattern TOOL_ACCESS_FAILURE_HINT = Pattern.compile(
        "(?is)(権限エラー|アクセス権限|permission\\s+error|permission\\s+denied|access\\s+denied|"
            + "ファイルアクセス.*権限|tool.*permission|ツール.*(権限|アクセス))"
    );

    public ReviewResult fromException(AgentConfig config, String repository, Exception e) {
        return baseBuilder(config, repository)
            .success(false)
            .errorMessage(e.getMessage())
            .timestamp(Instant.now())
            .build();
    }

    public ReviewResult emptyContentFailure(AgentConfig config, String repository, boolean usedMcp) {
        String errorMsg = usedMcp
            ? "Agent returned empty review content — model may have timed out during MCP tool calls"
            : "Agent returned empty review content";
        return baseBuilder(config, repository)
            .success(false)
            .errorMessage(errorMsg)
            .timestamp(Instant.now())
            .build();
    }

    public ReviewResult invalidContentFailure(AgentConfig config, String repository, String reason) {
        return baseBuilder(config, repository)
            .success(false)
            .errorMessage(reason)
            .timestamp(Instant.now())
            .build();
    }

    public ReviewResult fromContent(AgentConfig config, String repository, String content, boolean usedMcp) {
        if (content == null || content.isBlank()) {
            return emptyContentFailure(config, repository, usedMcp);
        }
        if (looksLikeToolAccessFailure(content)) {
            return invalidContentFailure(
                config,
                repository,
                "Agent returned non-review content (tool access/permission diagnostics)"
            );
        }
        return success(config, repository, content);
    }

    private boolean looksLikeToolAccessFailure(String content) {
        if (!TOOL_ACCESS_FAILURE_HINT.matcher(content).find()) {
            return false;
        }
        return !(content.contains("**Priority**") || content.contains("| Priority |"));
    }

    public ReviewResult success(AgentConfig config, String repository, String content) {
        return baseBuilder(config, repository)
            .content(content)
            .success(true)
            .timestamp(Instant.now())
            .build();
    }

    private ReviewResult.Builder baseBuilder(AgentConfig config, String repository) {
        return ReviewResult.builder()
            .agentConfig(config)
            .repository(repository);
    }
}

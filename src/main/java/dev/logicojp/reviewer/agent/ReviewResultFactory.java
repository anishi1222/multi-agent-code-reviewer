package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.report.core.ReviewResult;

import java.time.Instant;
import java.util.regex.Pattern;

final class ReviewResultFactory {

    private static final Pattern TOOL_ACCESS_FAILURE_HINT = Pattern.compile(
        "(?is)(権限エラー|アクセス権限|permission\\s+error|permission\\s+denied|access\\s+denied|"
            + "ファイルアクセス.*権限|tool.*permission|ツール.*(権限|アクセス))"
    );

    ReviewResult fromException(AgentConfig config, String repository, Exception e) {
        return baseBuilder(config, repository)
            .success(false)
            .errorMessage(e.getMessage())
            .timestamp(Instant.now())
            .build();
    }

    ReviewResult emptyContentFailure(AgentConfig config, String repository, boolean usedMcp) {
        String errorMsg = usedMcp
            ? "Agent returned empty review content — model may have timed out during MCP tool calls"
            : "Agent returned empty review content";
        return baseBuilder(config, repository)
            .success(false)
            .errorMessage(errorMsg)
            .timestamp(Instant.now())
            .build();
    }

    ReviewResult invalidContentFailure(AgentConfig config, String repository, String reason) {
        return baseBuilder(config, repository)
            .success(false)
            .errorMessage(reason)
            .timestamp(Instant.now())
            .build();
    }

    ReviewResult fromContent(AgentConfig config, String repository, String content, boolean usedMcp) {
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
        // Accept valid structured findings that include explicit priority markers.
        return !(content.contains("**Priority**") || content.contains("| Priority |"));
    }

    ReviewResult success(AgentConfig config, String repository, String content) {
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
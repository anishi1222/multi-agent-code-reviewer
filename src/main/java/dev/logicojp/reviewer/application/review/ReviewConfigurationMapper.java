package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.application.port.inbound.ReviewRequest;
import dev.logicojp.reviewer.application.port.outbound.ResolveReviewSettingsPort.ReviewSettings;
import dev.logicojp.reviewer.domain.review.PromptTexts;

import java.util.Objects;

/// Converts the outbound configuration boundary DTO into the application's internal configuration.
final class ReviewConfigurationMapper {

    private ReviewConfigurationMapper() {
    }

    static OrchestratorConfig toOrchestratorConfig(ReviewSettings settings, ReviewRequest request) {
        Objects.requireNonNull(settings, "settings must not be null");
        Objects.requireNonNull(request, "request must not be null");
        return OrchestratorConfig.builder()
            .githubToken(request.githubToken())
            .orchestratorTimeoutMinutes(settings.orchestratorTimeoutMinutes())
            .agentTimeoutMinutes(settings.agentTimeoutMinutes())
            .reviewPasses(settings.reviewPasses())
            .maxRetries(settings.maxRetries())
            .sharedSessionEnabled(settings.sharedSessionEnabled())
            .reasoningEffort(settings.reasoningEffort())
            .outputConstraints(null)
            .invocationTimestamp(request.invocationTimestamp())
            .promptTexts(new PromptTexts(null, null, null))
            .rubberDuckEnabled(settings.rubberDuckEnabled())
            .rubberDuckRounds(settings.rubberDuckRounds())
            .promptBudget(settings.promptBudget())
            .build();
    }
}

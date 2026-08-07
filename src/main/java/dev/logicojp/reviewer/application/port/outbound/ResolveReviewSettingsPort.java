package dev.logicojp.reviewer.application.port.outbound;

import dev.logicojp.reviewer.shared.PromptBudget;

/// Outbound port for resolving framework-bound settings for one review invocation.
///
/// Request credentials and correlation values deliberately stay in the application; this boundary
/// carries only externally configured settings and the request-scoped reasoning override needed to
/// resolve them.
public interface ResolveReviewSettingsPort {

    ReviewSettings resolve(ReviewSettingsInput input);

    record ReviewSettingsInput(String reasoningEffortOverride) {
    }

    record ReviewSettings(
        long orchestratorTimeoutMinutes,
        long agentTimeoutMinutes,
        int reviewPasses,
        int maxRetries,
        boolean sharedSessionEnabled,
        String reasoningEffort,
        boolean rubberDuckEnabled,
        int rubberDuckRounds,
        PromptBudget promptBudget
    ) {
    }
}

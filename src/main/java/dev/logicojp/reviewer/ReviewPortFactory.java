package dev.logicojp.reviewer;

import dev.logicojp.reviewer.application.port.inbound.RunReviewPort;
import dev.logicojp.reviewer.application.port.outbound.CollectLocalSourcePort;
import dev.logicojp.reviewer.application.port.outbound.CreateReviewSessionPortsPort;
import dev.logicojp.reviewer.application.port.outbound.LoadTemplatePort;
import dev.logicojp.reviewer.application.port.outbound.ManageCopilotClientPort;
import dev.logicojp.reviewer.application.port.outbound.PropagateCorrelationPort;
import dev.logicojp.reviewer.application.port.outbound.ResolveReviewSettingsPort;
import dev.logicojp.reviewer.application.review.ReviewOrchestrator;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/// Composition-root wiring for the primary review use case.
///
/// This root-package factory is layer zero: it may name both the application implementation and
/// outbound ports, but contains no configuration mapping, SDK construction, or business policy.
@Factory
public class ReviewPortFactory {

    /// Binds the inbound review port directly to its application-layer implementation.
    @Singleton
    RunReviewPort runReviewPort(ManageCopilotClientPort copilotClient,
                                CollectLocalSourcePort collectLocalSource,
                                LoadTemplatePort loadTemplate,
                                ResolveReviewSettingsPort resolveReviewSettings,
                                CreateReviewSessionPortsPort createReviewSessionPorts,
                                PropagateCorrelationPort propagateCorrelation) {
        return new ReviewOrchestrator(
            copilotClient,
            collectLocalSource,
            loadTemplate,
            resolveReviewSettings,
            createReviewSessionPorts,
            propagateCorrelation
        );
    }
}

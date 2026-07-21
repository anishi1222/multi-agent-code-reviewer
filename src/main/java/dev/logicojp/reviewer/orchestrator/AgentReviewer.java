package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.config.RubberDuckConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.service.TemplateService;
import dev.logicojp.reviewer.target.ReviewTarget;

@FunctionalInterface
interface AgentReviewer {
    ReviewResult review(ReviewTarget target);

    default ReviewResult reviewRubberDuck(ReviewTarget target,
                                          RubberDuckConfig rubberDuckConfig,
                                          TemplateService templateService) {
        return review(target);
    }
}
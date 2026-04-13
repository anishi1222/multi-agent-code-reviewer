package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.config.RubberDuckConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.service.TemplateService;
import dev.logicojp.reviewer.target.ReviewTarget;

import java.util.List;

@FunctionalInterface
interface AgentReviewer {
    ReviewResult review(ReviewTarget target);

    default List<ReviewResult> reviewPasses(ReviewTarget target, int reviewPasses) {
        var results = new java.util.ArrayList<ReviewResult>(reviewPasses);
        for (int pass = 0; pass < reviewPasses; pass++) {
            results.add(review(target));
        }
        return results;
    }

    default ReviewResult reviewRubberDuck(ReviewTarget target,
                                          RubberDuckConfig rubberDuckConfig,
                                          TemplateService templateService) {
        return review(target);
    }
}
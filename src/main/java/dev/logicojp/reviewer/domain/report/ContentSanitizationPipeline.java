package dev.logicojp.reviewer.domain.report;

import java.util.List;

/// A pipeline of {@link ContentSanitizationRule} instances applied in order.
///
/// Pure {@code java.*} — no framework dependencies.
final class ContentSanitizationPipeline {

    private final List<ContentSanitizationRule> rules;

    ContentSanitizationPipeline(List<ContentSanitizationRule> rules) {
        this.rules = List.copyOf(rules);
    }

    String apply(String content) {
        String result = content;
        for (ContentSanitizationRule rule : rules) {
            result = rule.apply(result);
        }
        return result;
    }
}

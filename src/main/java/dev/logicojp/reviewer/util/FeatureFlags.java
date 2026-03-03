package dev.logicojp.reviewer.util;

import io.micronaut.context.annotation.ConfigurationProperties;

/// Centralized feature flags backed by Micronaut configuration binding.
/// 機能フラグ設定。プレビュー機能の有効化を制御する。
@ConfigurationProperties("reviewer.feature-flags")
public record FeatureFlags(
    boolean structuredConcurrency,
    boolean structuredConcurrencySkills
) {
    public FeatureFlags {
        // boolean flags intentionally default to false when unspecified.
    }
}

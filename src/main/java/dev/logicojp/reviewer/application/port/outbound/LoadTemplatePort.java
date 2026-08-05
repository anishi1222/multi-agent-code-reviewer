package dev.logicojp.reviewer.application.port.outbound;

import java.util.Map;

/// Outbound port: load and render templates.
///
/// Implementer: {@code infrastructure.template.TemplateRepository}
/// Callers:     {@code application.review.*}, {@code application.report.*}
///
/// Cycle resolution: breaks cycles 2, 5, 7, 8, 10 — all caused by direct
/// {@code TemplateService} imports across 4 packages.
public interface LoadTemplatePort {

    /// Load a template by key and render it with the given placeholders.
    ///
    /// @param templateKey  logical template identifier (e.g. "review/system-prompt")
    /// @param placeholders placeholder name-to-value substitutions
    /// @return the rendered template string
    String render(String templateKey, Map<String, String> placeholders);

    /// Load raw (unrendered) template content.
    ///
    /// @param templateKey logical template identifier
    /// @return the raw template string
    String loadRaw(String templateKey);
}

package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.util.PlaceholderUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/// Service for loading and processing report/summary templates.
///
/// Uses `${placeholder}` syntax for files under the `templates/` directory.
/// Supports loading from external files with fallback to classpath resources.
@Singleton
public class TemplateService {

    private static final Logger logger = LoggerFactory.getLogger(TemplateService.class);

    private final TemplateConfig config;
    private final TemplateRepository templateRepository;

    @Inject
    public TemplateService(TemplateConfig config) {
        this.config = config;
        this.templateRepository = new TemplateRepository(config);
    }

    /// Loads a template by name, applying placeholder substitutions.
    ///
    /// @param templateName The template name (without directory prefix)
    /// @param placeholders Map of placeholder names to values (e.g., "repository" -> "owner/repo")
    /// @return The processed template content
     String loadTemplate(String templateName, Map<String, String> placeholders) {
        String content = loadTemplateContent(templateName);
        return applyPlaceholders(content, placeholders);
    }

    /// Loads raw template content without placeholder substitution.
    /// Results are cached in memory — each template is read from disk at most once.
    ///
    /// @param templateName The template name
    /// @return The raw template content
    public String loadTemplateContent(String templateName) {
        return templateRepository.loadTemplateContent(templateName);
    }

    /// Forces pending cache evictions to complete. Visible for testing.
    void cleanUp() {
        templateRepository.cleanUp();
    }

    /// Applies `${key}` placeholder substitutions to a template in a single pass.
    ///
    /// @param template The template content
    /// @param placeholders Map of placeholder names to values
    /// @return The processed content
    public String applyPlaceholders(String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return template;
        }

        return PlaceholderUtils.replaceDollarPlaceholders(template, placeholders);
    }

    // Convenience methods for specific templates

    /// Loads the default output format template.
     String getDefaultOutputFormat() {
        return loadTemplateContent(config.defaultOutputFormat());
    }

    /// Loads the report template with placeholders applied.
    public String getReportTemplate(Map<String, String> placeholders) {
        return loadTemplate(config.report(), placeholders);
    }

    /// Loads the executive summary template with placeholders applied.
    public String getExecutiveSummaryTemplate(Map<String, String> placeholders) {
        return loadTemplate(config.summary().executiveSummary(), placeholders);
    }

    /// Loads the fallback summary template with placeholders applied.
    public String getFallbackSummaryTemplate(Map<String, String> placeholders) {
        return loadTemplate(config.fallback().summary(), placeholders);
    }

    /// Loads the local review content template with placeholders applied.
     String getLocalReviewContent(Map<String, String> placeholders) {
        return loadTemplate(config.localReviewContent(), placeholders);
    }

    /// Loads the summary system prompt template.
    public String getSummarySystemPrompt() {
        return loadTemplateContent(config.summary().systemPrompt());
    }

    /// Loads the summary user prompt template with placeholders applied.
    public String getSummaryUserPrompt(Map<String, String> placeholders) {
        return loadTemplate(config.summary().userPrompt(), placeholders);
    }

    /// Loads the summary result entry template (per-agent success) with placeholders applied.
     String getSummaryResultEntry(Map<String, String> placeholders) {
        return loadTemplate(config.summary().resultEntry(), placeholders);
    }

    /// Loads the summary result error entry template (per-agent failure) with placeholders applied.
     String getSummaryResultErrorEntry(Map<String, String> placeholders) {
        return loadTemplate(config.summary().resultErrorEntry(), placeholders);
    }

    /// Loads the fallback agent row template (table row) with placeholders applied.
    public String getFallbackAgentRow(Map<String, String> placeholders) {
        return loadTemplate(config.fallback().agentRow(), placeholders);
    }

    /// Loads the fallback agent success detail template with placeholders applied.
    public String getFallbackAgentSuccess(Map<String, String> placeholders) {
        return loadTemplate(config.fallback().agentSuccess(), placeholders);
    }

    /// Loads the fallback agent failure detail template with placeholders applied.
    public String getFallbackAgentFailure(Map<String, String> placeholders) {
        return loadTemplate(config.fallback().agentFailure(), placeholders);
    }

    /// Loads the report link entry template with placeholders applied.
    public String getReportLinkEntry(Map<String, String> placeholders) {
        return loadTemplate(config.reportLinkEntry(), placeholders);
    }

    /// Loads the output constraints template.
    /// Contains constraints such as CoT suppression, output format enforcement,
    /// and language requirements for review output.
    /// Also appends the review quality constraints (fact-vs-inference,
    /// partial-review guidance) when available.
     String getOutputConstraints() {
        String base = loadTemplateContent(config.outputConstraints());
        String qualityConstraints = loadReviewQualityConstraints();
        if (qualityConstraints != null && !qualityConstraints.isBlank()) {
            return base + "\n\n" + qualityConstraints.trim();
        }
        return base;
    }

    /// Loads the review quality constraints template (fact-vs-inference guidance,
    /// partial-review rules). Returns null if the template file does not exist.
     String loadReviewQualityConstraints() {
        try {
            return loadTemplateContent(config.reviewQualityConstraints());
        } catch (IllegalStateException | IllegalArgumentException e) {
            logger.debug("Review quality constraints template not found; skipping: {}",
                config.reviewQualityConstraints());
            return null;
        }
    }

    /// Gets the template configuration.
    public TemplateConfig getConfig() {
        return config;
    }
}

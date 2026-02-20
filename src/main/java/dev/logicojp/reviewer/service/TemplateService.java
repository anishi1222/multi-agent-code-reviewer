package dev.logicojp.reviewer.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.logicojp.reviewer.config.TemplateConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Service for loading and processing templates.
/// Supports loading from external files with fallback to classpath resources.
@Singleton
public class TemplateService {

    private static final Logger logger = LoggerFactory.getLogger(TemplateService.class);
    private static final int MAX_TEMPLATE_CACHE_SIZE = 64;

    private final TemplateConfig config;
    private final Cache<String, String> templateCache;
    private static final Pattern TEMPLATE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]+\\.md");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    @Inject
    public TemplateService(TemplateConfig config) {
        this.config = config;
        this.templateCache = Caffeine.newBuilder()
            .maximumSize(MAX_TEMPLATE_CACHE_SIZE)
            .build();
    }

    /// Loads a template by name, applying placeholder substitutions.
    String loadTemplate(String templateName, Map<String, String> placeholders) {
        String content = loadTemplateContent(templateName);
        return applyPlaceholders(content, placeholders);
    }

    /// Loads raw template content without placeholder substitution.
    public String loadTemplateContent(String templateName) {
        return templateCache.get(templateName, this::loadTemplateFromSource);
    }

    private String loadTemplateFromSource(String templateName) {
        if (!isValidTemplateName(templateName)) {
            throw new IllegalArgumentException("Invalid template name: " + templateName);
        }
        Path templatePath = resolveTemplatePath(templateName);
        if (templatePath == null) {
            throw new IllegalArgumentException("Template path traversal rejected: " + templateName);
        }
        String content = loadTemplateByPath(templateName, templatePath);
        if (content != null) {
            return content;
        }
        warnTemplateNotFound(templateName, templatePath);
        throw new IllegalStateException("Template not found: " + templateName);
    }

    private String loadTemplateByPath(String templateName, Path templatePath) {
        String fromFile = loadTemplateFromFile(templatePath);
        if (fromFile != null) {
            return fromFile;
        }
        String resourcePath = toResourcePath(templateName);
        String fromClasspath = loadTemplateFromClasspath(resourcePath);
        if (fromClasspath != null) {
            return fromClasspath;
        }
        return null;
    }

    private void warnTemplateNotFound(String templateName, Path templatePath) {
        String resourcePath = toResourcePath(templateName);
        logger.warn("Template not found: {} (checked {} and classpath:{})",
            templateName, templatePath, resourcePath);
    }

    private boolean isValidTemplateName(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            logger.warn("Invalid template name rejected: blank");
            return false;
        }
        if (!TEMPLATE_NAME_PATTERN.matcher(templateName).matches()) {
            logger.warn("Invalid template name rejected: {}", templateName);
            return false;
        }
        return true;
    }

    private Path resolveTemplatePath(String templateName) {
        Path baseDirectory = resolveBaseDirectory();
        Path templatePath = baseDirectory.resolve(templateName).normalize();
        if (isPathTraversal(templatePath, baseDirectory)) {
            logger.warn("Template path traversal rejected: {}", templateName);
            return null;
        }
        return templatePath;
    }

    private Path resolveBaseDirectory() {
        return Path.of(config.directory()).toAbsolutePath().normalize();
    }

    private boolean isPathTraversal(Path templatePath, Path baseDirectory) {
        return !templatePath.startsWith(baseDirectory);
    }

    private String loadTemplateFromFile(Path templatePath) {
        if (!Files.exists(templatePath)) {
            return null;
        }
        try {
            logger.debug("Loading template from file: {}", templatePath);
            return Files.readString(templatePath);
        } catch (IOException e) {
            logger.warn("Failed to read template file {}: {}", templatePath, e.getMessage(), e);
            return null;
        }
    }

    private String toResourcePath(String templateName) {
        return config.directory() + "/" + templateName;
    }

    private String loadTemplateFromClasspath(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                logger.debug("Loading template from classpath: {}", resourcePath);
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            logger.warn("Failed to read template from classpath {}: {}", resourcePath, e.getMessage(), e);
        }
        return null;
    }

    public String applyPlaceholders(String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        var sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = placeholders.getOrDefault(key, matcher.group());
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // Convenience methods for specific templates

    String getDefaultOutputFormat() {
        return loadTemplateContent(config.defaultOutputFormat());
    }

    public String getReportTemplate(Map<String, String> placeholders) {
        return loadTemplate(config.report(), placeholders);
    }

    public String getExecutiveSummaryTemplate(Map<String, String> placeholders) {
        return loadTemplate(config.summary().executiveSummary(), placeholders);
    }

    public String getFallbackSummaryTemplate(Map<String, String> placeholders) {
        return loadTemplate(config.fallback().summary(), placeholders);
    }

    String getLocalReviewContent(Map<String, String> placeholders) {
        return loadTemplate(config.localReviewContent(), placeholders);
    }

    public String getSummarySystemPrompt() {
        return loadTemplateContent(config.summary().systemPrompt());
    }

    public String getSummaryUserPrompt(Map<String, String> placeholders) {
        return loadTemplate(config.summary().userPrompt(), placeholders);
    }

    String getSummaryResultEntry(Map<String, String> placeholders) {
        return loadTemplate(config.summary().resultEntry(), placeholders);
    }

    String getSummaryResultErrorEntry(Map<String, String> placeholders) {
        return loadTemplate(config.summary().resultErrorEntry(), placeholders);
    }

    public String getFallbackAgentRow(Map<String, String> placeholders) {
        return loadTemplate(config.fallback().agentRow(), placeholders);
    }

    public String getFallbackAgentSuccess(Map<String, String> placeholders) {
        return loadTemplate(config.fallback().agentSuccess(), placeholders);
    }

    public String getFallbackAgentFailure(Map<String, String> placeholders) {
        return loadTemplate(config.fallback().agentFailure(), placeholders);
    }

    public String getReportLinkEntry(Map<String, String> placeholders) {
        return loadTemplate(config.reportLinkEntry(), placeholders);
    }

    String getOutputConstraints() {
        return loadTemplateContent(config.outputConstraints());
    }

    public TemplateConfig getConfig() {
        return config;
    }
}

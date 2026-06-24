package dev.logicojp.reviewer.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.logicojp.reviewer.config.TemplateConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;

final class TemplateRepository {

    private static final Logger logger = LoggerFactory.getLogger(TemplateRepository.class);
    private static final int MAX_TEMPLATE_CACHE_SIZE = 64;
    private static final Duration TEMPLATE_CACHE_TTL = Duration.ofMinutes(30);
    private static final Pattern TEMPLATE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]+\\.md");

    private final TemplateConfig config;
    private final Cache<String, String> templateCache;

    TemplateRepository(TemplateConfig config) {
        this.config = config;
        this.templateCache = Caffeine.newBuilder()
            .maximumSize(MAX_TEMPLATE_CACHE_SIZE)
            .expireAfterWrite(TEMPLATE_CACHE_TTL)
            .build();
    }

    String loadTemplateContent(String templateName) {
        return templateCache.get(templateName, this::loadTemplateFromSource);
    }

    void cleanUp() {
        templateCache.cleanUp();
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
        if (Files.exists(templatePath)) {
            try {
                Path realBase = baseDirectory.toRealPath();
                Path realTemplate = templatePath.toRealPath();
                if (!realTemplate.startsWith(realBase)) {
                    logger.warn("Template symlink traversal rejected: {}", templateName);
                    return null;
                }
            } catch (IOException e) {
                logger.warn("Failed to resolve real path for template {}: {}", templateName, e.getMessage());
                return null;
            }
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
}

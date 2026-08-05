package dev.logicojp.reviewer.infrastructure.parsing;

import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// Parses Agent Skills specification files (SKILL.md).
///
/// Follows the Agent Skills spec: each skill lives in its own directory
/// and has a SKILL.md file with YAML frontmatter and a markdown body.
public class SkillMarkdownParser {

    private static final Logger logger = LoggerFactory.getLogger(SkillMarkdownParser.class);
    static final String DEFAULT_SKILL_FILENAME = "SKILL.md";

    private final String skillFilename;

    public SkillMarkdownParser() {
        this(DEFAULT_SKILL_FILENAME);
    }

    public SkillMarkdownParser(String skillFilename) {
        this.skillFilename = (skillFilename == null || skillFilename.isBlank())
            ? DEFAULT_SKILL_FILENAME : skillFilename;
    }

    String getSkillFilename() {
        return skillFilename;
    }

    public SkillDefinition parse(Path skillFile) throws IOException {
        String filename = skillFile.getFileName().toString();
        if (!filename.equals(skillFilename)) {
            throw new IllegalArgumentException(
                "Unsupported skill file format: " + filename + ". Only " + skillFilename + " is supported.");
        }
        String content = Files.readString(skillFile);
        String dirName = skillFile.getParent().getFileName().toString();
        return parseContent(content, dirName);
    }

    SkillDefinition parseContent(String content, String skillId) {
        FrontmatterParser.FrontmatterResult parsed = FrontmatterParser.parse(content);
        if (!parsed.hasFrontmatter()) {
            logger.warn("No valid frontmatter found in {}; using entire content as prompt.", skillId);
            return SkillDefinition.of(skillId, skillId, "", content.trim());
        }

        Map<String, String> simpleFields = parsed.fields();
        Map<String, String> metadataMap = parsed.rawFrontmatter() != null
            ? FrontmatterParser.parseNestedBlock(parsed.rawFrontmatter(), "metadata")
            : Map.of();

        String name = simpleFields.getOrDefault("name", skillId);
        String description = simpleFields.getOrDefault("description", "");
        String body = parsed.body().trim();

        if (body.isBlank()) {
            throw new IllegalArgumentException("Skill file " + skillId + " has no prompt content after frontmatter.");
        }

        return new SkillDefinition(skillId, name, description, body, List.of(), metadataMap);
    }

    boolean isSkillFile(Path path) {
        if (path == null) return false;
        return path.getFileName().toString().equals(skillFilename);
    }

    public List<Path> discoverSkills(Path skillsRoot) {
        if (skillsRoot == null || !Files.isDirectory(skillsRoot)) {
            return List.of();
        }
        try {
            Path realSkillsRoot = skillsRoot.toRealPath();
            try (Stream<Path> dirs = Files.list(realSkillsRoot)) {
                return dirs
                    .filter(Files::isDirectory)
                    .map(dir -> dir.resolve(skillFilename))
                    .filter(path -> isSkillFileWithinRoot(path, realSkillsRoot))
                    .sorted()
                    .toList();
            }
        } catch (IOException e) {
            logger.error("Failed to discover skills in {}: {}", skillsRoot, e.getMessage(), e);
            return List.of();
        }
    }

    private boolean isSkillFileWithinRoot(Path path, Path realSkillsRoot) {
        if (!Files.isRegularFile(path)) return false;
        try {
            return path.toRealPath().startsWith(realSkillsRoot);
        } catch (IOException e) {
            logger.debug("Failed to resolve real path for skill file {}: {}", path, e.getMessage());
            return false;
        }
    }
}

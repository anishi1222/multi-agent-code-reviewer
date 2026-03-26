package dev.logicojp.reviewer.skill;

import dev.logicojp.reviewer.util.FrontmatterParser;
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
/// Follows the Agent Skills spec (https://agentskills.io/specification):
/// ```
/// .github/skills/skill-name/SKILL.md
/// ```
/// - name must be lowercase alphanumeric + hyphens, matching directory name
/// - required frontmatter: name, description
/// - optional: license, compatibility, metadata, allowed-tools
public class SkillMarkdownParser {

    private static final Logger logger = LoggerFactory.getLogger(SkillMarkdownParser.class);

    /// Default filename for Agent Skills spec
    static final String DEFAULT_SKILL_FILENAME = "SKILL.md";

    /// Configured skill filename
    private final String skillFilename;

    /// Creates a parser with the default skill filename (SKILL.md).
    public SkillMarkdownParser() {
        this(DEFAULT_SKILL_FILENAME);
    }

    /// Creates a parser with a custom skill filename.
    /// @param skillFilename The filename to use for skill files (e.g. "SKILL.md")
    public SkillMarkdownParser(String skillFilename) {
        this.skillFilename = (skillFilename == null || skillFilename.isBlank())
            ? DEFAULT_SKILL_FILENAME : skillFilename;
    }

    /// Returns the configured skill filename.
     String getSkillFilename() {
        return skillFilename;
    }

    /// Parses a skill file and returns a SkillDefinition.
    ///
    /// The skill ID is derived from the parent directory name.
    ///
    /// @param skillFile Path to the skill file
    /// @return SkillDefinition parsed from the file
    /// @throws IllegalArgumentException if the file does not match the configured skill filename
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

    /// Parses skill markdown content and returns a SkillDefinition.
    /// @param content The full content of the SKILL.md file
    /// @param skillId The skill ID (directory name)
    /// @return SkillDefinition parsed from the content
     SkillDefinition parseContent(String content, String skillId) {
        String id = skillId;

        FrontmatterParser.Parsed parsed = FrontmatterParser.parse(content);
        if (!parsed.hasFrontmatter()) {
            logger.warn("No valid frontmatter found in {}; using entire content as prompt.", skillId);
            return SkillDefinition.of(id, id, "", content.trim());
        }

        Map<String, String> simpleFields = parsed.metadata();
        Map<String, String> metadataMap = parsed.rawFrontmatter() != null
            ? FrontmatterParser.parseNestedBlock(parsed.rawFrontmatter(), "metadata")
            : Map.of();

        String name = simpleFields.getOrDefault("name", id);
        String description = simpleFields.getOrDefault("description", "");
        String body = parsed.body().trim();

        if (body.isBlank()) {
            throw new IllegalArgumentException("Skill file " + skillId + " has no prompt content after frontmatter.");
        }

        return new SkillDefinition(id, name, description, body, List.of(), metadataMap);
    }

    /// Checks whether the given path matches the configured skill filename.
     boolean isSkillFile(Path path) {
        if (path == null) return false;
        return path.getFileName().toString().equals(skillFilename);
    }

    /// Discovers all skill directories under the given skills root.
    /// Each skill directory must contain a file matching the configured skill filename.
    ///
    /// @param skillsRoot The root directory to scan (e.g. .github/skills/)
    /// @return List of paths to skill files found
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
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try {
            return path.toRealPath().startsWith(realSkillsRoot);
        } catch (IOException e) {
            logger.debug("Failed to resolve real path for skill file {}: {}", path, e.getMessage());
            return false;
        }
    }
}

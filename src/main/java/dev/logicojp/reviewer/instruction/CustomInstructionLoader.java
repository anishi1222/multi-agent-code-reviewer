package dev.logicojp.reviewer.instruction;

import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.FrontmatterParser;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/// Loads custom instructions from repository or local directory.
/// Merges the former ScopedInstructionLoader, PromptLoader, and InstructionFrontmatter.
///
/// Supports the following instruction file locations (in priority order):
/// - .github/copilot-instructions.md
/// - .github/instructions/*.instructions.md (per-scope format with YAML frontmatter)
/// - .github/prompts/*.prompt.md (reusable prompt files)
/// - .copilot/instructions.md
/// - copilot-instructions.md (root)
/// - INSTRUCTIONS.md (root)
/// - .instructions.md (root)
@Singleton
public class CustomInstructionLoader {

    private static final Logger logger = LoggerFactory.getLogger(CustomInstructionLoader.class);

    private static final List<String> INSTRUCTION_PATHS = List.of(
        ".github/copilot-instructions.md",
        ".copilot/instructions.md",
        "copilot-instructions.md",
        "INSTRUCTIONS.md",
        ".instructions.md"
    );

    private static final String INSTRUCTIONS_DIRECTORY = ".github/instructions";
    private static final String INSTRUCTIONS_EXTENSION = ".instructions.md";
    private static final String PROMPTS_DIRECTORY = ".github/prompts";
    private static final String PROMPT_EXTENSION = ".prompt.md";

    private final List<Path> additionalInstructionPaths;
    private final boolean loadPrompts;

    @Inject
    public CustomInstructionLoader() {
        this(null, true);
    }

    public static CustomInstructionLoader withSettings(List<Path> additionalInstructionPaths, boolean loadPrompts) {
        return new CustomInstructionLoader(additionalInstructionPaths, loadPrompts);
    }

    CustomInstructionLoader(List<Path> additionalInstructionPaths, boolean loadPrompts) {
        this.additionalInstructionPaths = additionalInstructionPaths != null
            ? List.copyOf(additionalInstructionPaths)
            : List.of();
        this.loadPrompts = loadPrompts;
    }

    /// Loads custom instructions for the given review target.
    /// For local targets, loads from the directory.
    /// For GitHub targets, returns empty (instructions should be fetched via MCP).
    public List<CustomInstruction> loadForTarget(ReviewTarget target) {
        if (target.isLocal()) {
            return target.localPath()
                .map(this::loadFromLocalDirectory)
                .orElse(List.of());
        }
        return List.of();
    }

    /// Loads custom instructions from a local directory.
    List<CustomInstruction> loadFromLocalDirectory(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }

        Path resolvedDirectory = directory.toAbsolutePath().normalize();
        List<CustomInstruction> instructions = new ArrayList<>();

        // Standard locations
        for (String relativePath : INSTRUCTION_PATHS) {
            Path instructionPath = resolvedDirectory.resolve(relativePath);
            loadInstructionFile(instructionPath).ifPresent(instructions::add);
        }

        // Per-scope instruction files (.github/instructions/*.instructions.md)
        instructions.addAll(loadScopedInstructions(resolvedDirectory));

        // Reusable prompt files (.github/prompts/*.prompt.md)
        if (loadPrompts) {
            instructions.addAll(loadPromptFiles(resolvedDirectory));
        }

        // Additional paths
        for (Path additionalPath : additionalInstructionPaths) {
            Path resolvedPath = additionalPath.isAbsolute()
                ? additionalPath
                : resolvedDirectory.resolve(additionalPath);
            loadInstructionFile(resolvedPath).ifPresent(instructions::add);
        }

        if (instructions.isEmpty()) {
            logger.debug("No custom instructions found in: {}", resolvedDirectory);
        }

        return List.copyOf(instructions);
    }

    // --- Standard instruction file loading ---

    private Optional<CustomInstruction> loadInstructionFile(Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        if (Files.isSymbolicLink(path)) {
            logger.warn("Skipping symbolic link instruction file: {}", path);
            return Optional.empty();
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                logger.debug("Instruction file is empty: {}", path);
                return Optional.empty();
            }
            logger.info("Loaded custom instruction from: {}", path);
            return Optional.of(new CustomInstruction(
                path.toString(), content.trim(), CustomInstruction.Source.LOCAL_FILE, null, null));
        } catch (IOException e) {
            logger.warn("Failed to read instruction file {}: {}", path, e.getMessage(), e);
            return Optional.empty();
        }
    }

    // --- Per-scope instruction loading (formerly ScopedInstructionLoader) ---

    private List<CustomInstruction> loadScopedInstructions(Path baseDirectory) {
        Path instructionsDir = baseDirectory.resolve(INSTRUCTIONS_DIRECTORY);
        if (!Files.isDirectory(instructionsDir)) {
            return List.of();
        }

        List<CustomInstruction> instructions = new ArrayList<>();
        try (Stream<Path> stream = Files.list(instructionsDir)) {
            List<Path> instructionFiles = stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(INSTRUCTIONS_EXTENSION))
                .sorted()
                .toList();
            for (Path path : instructionFiles) {
                loadScopedInstruction(path).ifPresent(instructions::add);
            }
        } catch (IOException e) {
            logger.warn("Failed to scan instructions directory {}: {}", instructionsDir, e.getMessage(), e);
        }
        return instructions;
    }

    private Optional<CustomInstruction> loadScopedInstruction(Path path) {
        if (Files.isSymbolicLink(path)) {
            logger.warn("Skipping symbolic link scoped instruction: {}", path);
            return Optional.empty();
        }
        try {
            String rawContent = Files.readString(path, StandardCharsets.UTF_8);
            if (rawContent.isBlank()) {
                logger.debug("Scoped instruction file is empty: {}", path);
                return Optional.empty();
            }
            var parsed = parseFrontmatter(rawContent);
            logger.info("Loaded scoped instruction from: {} (applyTo: {})", path, parsed.applyTo());
            return Optional.of(new CustomInstruction(
                path.toString(), parsed.content().trim(),
                CustomInstruction.Source.LOCAL_FILE, parsed.applyTo(), parsed.description()));
        } catch (IOException e) {
            logger.warn("Failed to read instruction file {}: {}", path, e.getMessage(), e);
            return Optional.empty();
        }
    }

    // --- Prompt file loading (formerly PromptLoader) ---

    private List<CustomInstruction> loadPromptFiles(Path baseDirectory) {
        Path promptsDir = baseDirectory.resolve(PROMPTS_DIRECTORY);
        if (!Files.isDirectory(promptsDir)) {
            logger.debug("Prompts directory not found: {}", promptsDir);
            return List.of();
        }

        List<Path> promptFiles;
        try (Stream<Path> stream = Files.list(promptsDir)) {
            promptFiles = stream.filter(Files::isRegularFile)
                  .filter(p -> p.getFileName().toString().endsWith(PROMPT_EXTENSION))
                  .sorted()
                  .toList();
        } catch (IOException e) {
            logger.warn("Failed to scan prompts directory {}: {}", promptsDir, e.getMessage(), e);
            return List.of();
        }

        List<CustomInstruction> prompts = new ArrayList<>();
        for (Path path : promptFiles) {
            if (Files.isSymbolicLink(path)) {
                logger.warn("Skipping symbolic link prompt file: {}", path);
                continue;
            }
            try {
                String rawContent = Files.readString(path, StandardCharsets.UTF_8);
                if (rawContent.isBlank()) {
                    logger.debug("Prompt file is empty: {}", path);
                    continue;
                }
                var parsed = parsePromptFrontmatter(rawContent);
                prompts.add(new CustomInstruction(
                    path.toString(), parsed.content().trim(),
                    CustomInstruction.Source.LOCAL_FILE, null, parsed.description()));
                logger.info("Loaded prompt from: {} (description: {})",
                    path.getFileName(), parsed.description());
            } catch (IOException e) {
                logger.warn("Failed to read prompt file {}: {}", path, e.getMessage(), e);
            }
        }
        return prompts;
    }

    // --- Frontmatter parsing (formerly InstructionFrontmatter) ---

    record ParsedInstruction(String content, String applyTo, String description) {}

    static ParsedInstruction parseFrontmatter(String rawContent) {
        var parsed = FrontmatterParser.parse(rawContent);
        if (!parsed.hasFrontmatter()) {
            return new ParsedInstruction(rawContent, null, null);
        }
        String content = bodyOrRaw(parsed, rawContent);
        return new ParsedInstruction(content, parsed.get("applyTo"), parsed.get("description"));
    }

    private record ParsedPrompt(String content, String description, String agent) {}

    private static ParsedPrompt parsePromptFrontmatter(String rawContent) {
        var parsed = FrontmatterParser.parse(rawContent);
        if (!parsed.hasFrontmatter()) {
            return new ParsedPrompt(rawContent, null, null);
        }
        String content = bodyOrRaw(parsed, rawContent);
        return new ParsedPrompt(content, parsed.get("description"), parsed.get("agent"));
    }

    private static String bodyOrRaw(FrontmatterParser.Parsed parsed, String rawContent) {
        if (!parsed.hasFrontmatter()) {
            return rawContent;
        }
        String body = parsed.body().trim();
        return body.isEmpty() ? rawContent : body;
    }
}

package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.instruction.CustomInstructionLoader;
import dev.logicojp.reviewer.instruction.CustomInstructionSafetyValidator;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.SecurityAuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Resolves and validates custom instructions for review execution.
final class CustomInstructionResolver {

    private static final Logger logger = LoggerFactory.getLogger(CustomInstructionResolver.class);

    private final CustomInstructionLoader instructionLoader;
    private final CliOutput output;

    CustomInstructionResolver(CustomInstructionLoader instructionLoader, CliOutput output) {
        this.instructionLoader = instructionLoader;
        this.output = output;
    }

    List<CustomInstruction> resolve(ReviewCommand.ParsedOptions options, ReviewTarget target) {
        if (options.noInstructions()) {
            logger.info("Custom instructions disabled by --no-instructions flag");
            return List.of();
        }

        List<CustomInstruction> instructions = new ArrayList<>();
        loadExplicitInstructions(options.instructionPaths(), instructions);
        loadTargetInstructions(target, options, instructions);
        return List.copyOf(instructions);
    }

    private void loadExplicitInstructions(List<Path> paths, List<CustomInstruction> instructions) {
        if (paths == null || paths.isEmpty()) return;
        for (Path path : paths) {
            loadInstructionFromPath(path).ifPresent(instruction ->
                addIfSafe(instruction, instructions, "  ✓ Loaded instructions: ", true));
        }
    }

    private Optional<CustomInstruction> loadInstructionFromPath(Path path) {
        try {
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                logger.warn("Instruction file not found: {}", path);
                return Optional.empty();
            }
            String content = Files.readString(path);
            if (content.isBlank()) return Optional.empty();
            return Optional.of(new CustomInstruction(
                path.toString(), content.trim(), CustomInstruction.Source.LOCAL_FILE, null, null));
        } catch (IOException | SecurityException e) {
            logger.warn("Failed to read instruction file {}: {}", path, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private void loadTargetInstructions(ReviewTarget target,
                                        ReviewCommand.ParsedOptions options,
                                        List<CustomInstruction> instructions) {
        if (!target.isLocal()) return;
        if (!options.trustTarget()) {
            output.println("ℹ  Target instructions skipped (use --trust to load from review target).");
            return;
        }

        output.println("⚠  --trust enabled: loading custom instructions from the review target.");
        logger.warn("[SECURITY AUDIT] Trust boundary relaxed: loading instructions from target={}",
            target.displayName());
        SecurityAuditLogger.log("trust-boundary", "instruction-load",
            "Trust mode enabled for target instruction loading",
            Map.of("target", target.displayName()));

        CustomInstructionLoader targetLoader = options.noPrompts()
            ? CustomInstructionLoader.withSettings(null, false)
            : instructionLoader;
        List<CustomInstruction> targetInstructions = targetLoader.loadForTarget(target);

        for (CustomInstruction instruction : targetInstructions) {
            String sourcePath = instruction.sourcePath() != null ? instruction.sourcePath() : "unknown";
            logger.warn("[SECURITY AUDIT] Loaded trusted instruction from: {} (size: {} bytes)",
                sourcePath, instruction.content() != null ? instruction.content().length() : 0);
            SecurityAuditLogger.log("trust-boundary", "instruction-load",
                "Trusted instruction loaded",
                Map.of("source", sourcePath,
                    "size", Integer.toString(instruction.content() != null ? instruction.content().length() : 0)));
            addIfSafe(instruction, instructions, "  ✓ Loaded instructions from target: ", false);
        }
    }

    private void addIfSafe(CustomInstruction instruction,
                           List<CustomInstruction> instructions,
                           String loadedPrefix,
                           boolean trusted) {
        var validation = CustomInstructionSafetyValidator.validate(instruction, trusted);
        if (!validation.safe()) {
            String sourcePath = instruction.sourcePath() != null ? instruction.sourcePath() : "unknown";
            logger.warn("Skipped unsafe instruction {}: {}", sourcePath, validation.reason());
            SecurityAuditLogger.log("instruction-validation", "instruction-rejected",
                "Unsafe instruction rejected",
                Map.of("source", sourcePath, "reason", validation.reason(),
                    "trusted", Boolean.toString(trusted)));
            return;
        }
        instructions.add(instruction);
        output.println(loadedPrefix + instruction.sourcePath());
    }
}

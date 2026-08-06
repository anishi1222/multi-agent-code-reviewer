package dev.logicojp.reviewer.presentation.command;

import dev.logicojp.reviewer.presentation.CliOutput;
import dev.logicojp.reviewer.presentation.CliValidationException;
import dev.logicojp.reviewer.presentation.ExitCodes;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/// Shared command execution logic for all CLI commands.
final class CommandExecutor {

    private CommandExecutor() {}

    public static <T> int execute(
            String[] args,
            Function<String[], Optional<T>> parser,
            Function<T, Integer> executor,
            Consumer<CliOutput> usagePrinter,
            Logger logger,
            CliOutput output) {
        try {
            Optional<T> options = parser.apply(args);
            if (options.isEmpty()) {
                return ExitCodes.OK;
            }
            return executor.apply(options.get());
        } catch (CliValidationException e) {
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                output.errorln(e.getMessage());
            }
            if (e.showUsage()) {
                usagePrinter.accept(output);
            }
            return ExitCodes.USAGE;
        } catch (Exception e) {
            logger.error("Execution failed: {}", e.getMessage(), e);
            output.errorln("Error: " + e.getMessage());
            return ExitCodes.SOFTWARE;
        }
    }
}

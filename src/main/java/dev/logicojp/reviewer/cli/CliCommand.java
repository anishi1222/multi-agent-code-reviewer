package dev.logicojp.reviewer.cli;

/// Extensible command interface for CLI subcommands.
///
/// All CLI commands implement this contract, enabling registry-based
/// command dispatch instead of rigid switch-case routing.
///
/// @see ReviewCommand
/// @see ListAgentsCommand
/// @see SkillCommand
/// @see DoctorCommand
public interface CliCommand {

    /// Returns the subcommand name as used on the command line (e.g., "run", "list").
    String name();

    /// Executes the command with the given arguments.
    ///
    /// @param args command-specific arguments (after the subcommand name)
    /// @return an {@link ExitCodes} value
    int execute(String[] args);
}

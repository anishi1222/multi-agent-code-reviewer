package dev.logicojp.reviewer.presentation;

public final class CliUsage {
    private CliUsage() {}

    private static final String GENERAL_USAGE = """
            Usage: review <command> [options]

            Global options:
                -v, --verbose               Enable verbose logging
                --version                   Show version

            Commands:
                run    Execute a multi-agent code review
                list   List available agents
                skill  Execute a specific agent skill
                doctor Check runtime dependencies and configuration

            Use 'review <command> --help' for command options.
            """;

    public static void printGeneral(CliOutput output) {
        output.out().print(GENERAL_USAGE);
    }

    public static void printGeneralError(CliOutput output) {
        output.err().print(GENERAL_USAGE);
    }

    public static void printRun(CliOutput output) {
        output.out().print("""
            Usage: review run [options]

            Target options (required):
                -r, --repo <owner/repo>     Target GitHub repository
                -l, --local <path>          Target local directory

            Agent options (required):
                --all                       Run all available agents
                -a, --agents <a,b,c>        Comma-separated agent names

            Other options:
                -o, --output <path>         Output directory (default: ./reports)
                --agents-dir <path...>      Additional agent definition directories
                --token -                   Read GitHub token from stdin
                --parallelism <n>           Number of agents to run in parallel
                --no-summary                Skip executive summary generation
                --no-shared-session         Use isolated sessions for all review passes
                --rubber-duck               Enable peer-discussion review mode
                --dialogue-rounds <n>       Override rubber-duck dialogue rounds
                --peer-model <model>        Override peer model for rubber-duck mode
                --review-model <model>      Model for review stage
                --report-model <model>      Model for report stage
                --summary-model <model>     Model for summary stage
                --model <model>             Default model for all stages
            """);
    }

    public static void printList(CliOutput output) {
        output.out().print("""
            Usage: review list [options]

            Options:
                --agents-dir <path...>      Additional agent definition directories
            """);
    }

    public static void printSkill(CliOutput output) {
        output.out().print("""
            Usage: review skill [skill-id] [options]

            Options:
                -p, --param <key=value>     Skill parameters (repeatable or comma-separated)
                --token -                   Read GitHub token from stdin
                --model <model>             Model for skill execution
                --agents-dir <path...>      Additional agent definition directories
                --list                      List available skills
            """);
    }

    public static void printDoctor(CliOutput output) {
        output.out().print("""
            Usage: review doctor

            Checks runtime dependencies and configuration:
              - Java runtime version
              - Copilot connectivity and authentication
              - Configuration values

            Exit codes:
                0  All checks passed
                4  One or more issues found
            """);
    }
}

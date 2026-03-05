package dev.logicojp.reviewer.cli;

public final class CliUsage {
    private CliUsage() {
    }

    private static final String GENERAL_USAGE = """
            Usage: review <command> [options]

            Global options:
                -v, --verbose               Enable verbose logging
                --version                   Show version

            Feature flags (env / JVM properties):
                REVIEWER_STRUCTURED_CONCURRENCY=true
                REVIEWER_STRUCTURED_CONCURRENCY_SKILLS=true
                -Dreviewer.structuredConcurrency=true
                -Dreviewer.structuredConcurrency.skills=true

            Commands:
                run    Execute a multi-agent code review
                list   List available agents
                skill  Execute a specific agent skill

            Use 'review <command> --help' for command options.
            """;

    public static void printGeneral(CliOutput output) {
        output.out().print(GENERAL_USAGE);
    }

    public static void printGeneralError(CliOutput output) {
        output.err().print(GENERAL_USAGE);
    }

     static void printRun(CliOutput output) {
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
                    --token -                   Read GitHub token from stdin (default: GITHUB_TOKEN env var)
                    --parallelism <n>           Number of agents to run in parallel
                    --no-summary                Skip executive summary generation
                    --review-model <model>      Model for review stage
                    --report-model <model>      Model for report stage
                    --summary-model <model>     Model for summary stage
                    --model <model>             Default model for all stages
                """);
    }

     static void printList(CliOutput output) {
        output.out().print("""
                Usage: review list [options]

                Options:
                    --agents-dir <path...>      Additional agent definition directories
                """);
    }

     static void printSkill(CliOutput output) {
        output.out().print("""
                Usage: review skill [skill-id] [options]

                Options:
                    -p, --param <key=value>     Skill parameters (repeatable or comma-separated)
                    --token -                   Read GitHub token from stdin (default: GITHUB_TOKEN env var)
                    --model <model>             Model for skill execution
                    --agents-dir <path...>      Additional agent definition directories
                    --list                      List available skills
                """);
    }
}

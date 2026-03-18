package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.service.CopilotService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Singleton
class ReviewExecutionCoordinator {

    @FunctionalInterface
    interface Initializer {
        void initializeOrThrow();
    }

    @FunctionalInterface
    interface Executor {
        int execute(String resolvedToken, ReviewRunExecutor.ReviewRunRequest runRequest);
    }

    @FunctionalInterface
    interface Shutdowner {
        void shutdown();
    }

    private final Initializer initializer;
    private final Executor executor;
    private final Shutdowner shutdowner;
    private final CliOutput output;

    @Inject
    public ReviewExecutionCoordinator(CopilotService copilotService,
                                      ReviewRunExecutor reviewRunExecutor,
                                      CliOutput output) {
        this(copilotService::initializeOrThrow, reviewRunExecutor::execute, copilotService::shutdown, output);
    }

    ReviewExecutionCoordinator(Initializer initializer,
                               Executor executor,
                               Shutdowner shutdowner,
                               CliOutput output) {
        this.initializer = initializer;
        this.executor = executor;
        this.shutdowner = shutdowner;
        this.output = output;
    }

    public int execute(Map<String, AgentConfig> agentConfigs,
                       List<Path> agentDirs,
                       String resolvedToken,
                       ReviewRunExecutor.ReviewRunRequest runRequest) {
        if (hasNoAgents(agentConfigs)) {
            printNoAgentsError(agentDirs);
            return ExitCodes.SOFTWARE;
        }

        return executeReviewRun(resolvedToken, runRequest);
    }

    private int executeReviewRun(String resolvedToken,
                                 ReviewRunExecutor.ReviewRunRequest runRequest) {
        return LifecycleRunner.executeWithLifecycle(
            initializer::initializeOrThrow,
            () -> executor.execute(resolvedToken, runRequest),
            shutdowner::shutdown
        );
    }

    private boolean hasNoAgents(Map<String, AgentConfig> agentConfigs) {
        return agentConfigs.isEmpty();
    }

    private void printNoAgentsError(List<Path> agentDirs) {
        output.errorln("Error: No agents found. Check the agents directories:");
        for (Path dir : agentDirs) {
            output.errorln("  - " + dir);
        }
    }
}

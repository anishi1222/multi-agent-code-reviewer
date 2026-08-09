package dev.logicojp.reviewer.infrastructure.parsing;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.logicojp.reviewer.domain.agent.AgentRejection;
import dev.logicojp.reviewer.domain.agent.AgentSource;
import dev.logicojp.reviewer.domain.agent.AgentSourceDirectory;
import dev.logicojp.reviewer.domain.agent.AgentTrustProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Rejection must be survivable and visible (ADR-0007 D4).
///
/// ## Why "survivable" and "visible" are one concern
///
/// The failure mode this guards against is a repository that ships one malformed definition
/// and thereby disables review of the whole project — a denial of service achieved by being
/// slightly wrong. So a rejected definition must not stop the others loading.
///
/// But "continue on error" without reporting is worse than failing: the operator sees a
/// successful run and a smaller set of findings, with nothing to indicate that an agent was
/// dropped. Continuing is only safe if the drop is stated. That is why the summary line is
/// emitted unconditionally — including when nothing was rejected, so its absence is itself
/// evidence that the accounting did not run.
@DisplayName("agent rejection reporting (ADR-0007 D4)")
class AgentLoadRejectionReportingTest {

    private ListAppender<ILoggingEvent> appender;
    private ch.qos.logback.classic.Logger loaderLogger;

    @BeforeEach
    void attachAppender() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        loaderLogger = context.getLogger(AgentConfigLoader.class);
        appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        loaderLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        loaderLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("a rejected definition does not prevent the others from loading")
    void rejectionDoesNotBlockOtherAgents(@TempDir Path dir) throws IOException {
        writeValidAgent(dir, "good-one");
        writeValidAgent(dir, "good-two");
        writeOversizedAgent(dir, "bad-one");

        AgentConfigLoader.AgentLoadReport report = load(dir);

        assertThat(report.agents().keySet())
            .as("one bad definition must not disable review of the whole repository")
            .containsExactlyInAnyOrder("good-one", "good-two");
        assertThat(report.rejections()).hasSize(1);
    }

    @Test
    @DisplayName("the rejection names the rule and the provenance")
    void rejectionNamesRuleAndProvenance(@TempDir Path dir) throws IOException {
        writeOversizedAgent(dir, "bad-one");

        AgentRejection rejection = load(dir).rejections().getFirst();

        assertThat(rejection.filename()).contains("bad-one");
        assertThat(rejection.source())
            .as("without provenance the operator cannot tell whether the rejected file was "
                + "their own or came from the repository under review")
            .isEqualTo(AgentSource.REPOSITORY_SUPPLIED);
        assertThat(rejection.ruleId()).isNotBlank();
        assertThat(rejection.describe())
            .contains(rejection.ruleId())
            .contains("bad-one")
            .contains(AgentSource.REPOSITORY_SUPPLIED.name());
    }

    @Test
    @DisplayName("the summary is emitted when definitions are rejected")
    void summaryEmittedOnRejection(@TempDir Path dir) throws IOException {
        writeValidAgent(dir, "good-one");
        writeOversizedAgent(dir, "bad-one");

        load(dir);

        List<ILoggingEvent> summaries = summaryEvents();
        assertThat(summaries)
            .as("one agent load must emit exactly one summary event")
            .hasSize(1);
        ILoggingEvent summary = summaries.getFirst();
        assertThat(summary.getFormattedMessage()).contains("1").contains("rejected");
        assertThat(summary.getLevel())
            .as("a run that silently dropped an agent must not look like a clean run")
            .isEqualTo(Level.WARN);
    }

    /// The zero-rejection case matters more than it looks. If the summary were emitted only
    /// when something was rejected, an operator could never distinguish "nothing was
    /// rejected" from "the accounting never ran".
    @Test
    @DisplayName("the summary is emitted even when nothing is rejected")
    void summaryEmittedWithNoRejections(@TempDir Path dir) throws IOException {
        writeValidAgent(dir, "good-one");

        load(dir);

        List<ILoggingEvent> summaries = summaryEvents();
        assertThat(summaries)
            .as("one agent load must emit exactly one summary event")
            .hasSize(1);
        ILoggingEvent summary = summaries.getFirst();
        assertThat(summary.getFormattedMessage()).contains("0 rejected");
        assertThat(summary.getLevel()).isEqualTo(Level.INFO);
    }

    /// INFO is the configured level for this logger in `logback.xml`, so the summary is
    /// visible on a default run rather than requiring a verbosity flag.
    @Test
    @DisplayName("the summary is visible at the level the application ships with")
    void summaryVisibleAtDefaultLevel(@TempDir Path dir) throws IOException {
        writeValidAgent(dir, "good-one");

        assertThat(loaderLogger.isInfoEnabled())
            .as("if this logger were below INFO by default the summary would be invisible "
                + "in ordinary use, which is the same as not reporting it")
            .isTrue();

        load(dir);
        assertThat(summaryEvents())
            .as("the default logger level must make the summary observable")
            .isNotEmpty();
    }

    private List<ILoggingEvent> summaryEvents() {
        return appender.list.stream()
            .filter(e -> e.getFormattedMessage().contains(AgentConfigLoader.AGENT_LOAD_SUMMARY_PREFIX))
            .toList();
    }

    private static AgentConfigLoader.AgentLoadReport load(Path dir) throws IOException {
        return AgentConfigLoader
            .builder(List.of(AgentSourceDirectory.repositorySupplied(dir)))
            .build()
            .loadAllAgentsWithReport();
    }

    private static void writeValidAgent(Path dir, String name) throws IOException {
        String content = """
            ---
            name: %s
            model: claude-sonnet-4
            ---
            ## Role
            Reviews code.

            ## Instruction
            Review the diff and report issues.
            """.formatted(name);
        Files.writeString(dir.resolve(name + ".agent.md"), content, StandardCharsets.UTF_8);
    }

    /// Exceeds the repository file-size bound, which is one of the limits activated by D2.
    private static void writeOversizedAgent(Path dir, String name) throws IOException {
        String header = """
            ---
            name: %s
            model: claude-sonnet-4
            ---
            ## Role
            Reviews code.

            ## Instruction
            """.formatted(name);
        int limit = AgentTrustProfile.REPOSITORY_SUPPLIED_PROFILE.maxFileChars();
        String content = header + "a".repeat(limit + 1 - header.length());
        Files.writeString(dir.resolve(name + ".agent.md"), content, StandardCharsets.UTF_8);
    }
}

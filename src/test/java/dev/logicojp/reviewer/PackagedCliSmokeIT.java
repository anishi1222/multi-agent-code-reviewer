package dev.logicojp.reviewer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("配布JARのCLIスモーク")
class PackagedCliSmokeIT {

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);

    @TempDir
    Path workingDirectory;

    @ParameterizedTest(name = "{0}")
    @MethodSource("safeEntryPoints")
    @DisplayName("ネットワーク不要のエントリポイントが配布JARから起動する")
    void safeEntryPointStartsFromPackagedJar(
        String scenario,
        List<String> arguments,
        List<String> expectedOutput
    ) throws Exception {
        String output = runPackagedCli(scenario, arguments);

        assertThat(output)
            .as("%s must reach its expected CLI surface", scenario)
            .containsAnyOf(expectedOutput.toArray(String[]::new));
    }

    @Test
    @DisplayName("追加指定なしでconfigured defaultのエージェントを発見する")
    void discoversAgentsFromConfiguredDefaultDirectory() throws Exception {
        writeAgentFixture();

        String output = runPackagedCli("configured default agent discovery", List.of("list"));

        assertThat(output)
            .contains("Available agents:", "packaged-default-agent")
            .doesNotContain("No agents found.");
    }

    @Test
    @DisplayName("発見済みスキルを実行系と共有する単一カタログから一覧する")
    void listsDiscoveredSkillsFromCanonicalExecutionCatalog() throws Exception {
        writeAgentFixture();
        writeSkillFixture();

        String output = runPackagedCli(
            "canonical discovered-skill catalog",
            List.of("skill", "--list", "--agents-dir", "./agents")
        );

        assertThat(output)
            .contains("Available Skills:", "packaged-catalog-skill")
            .doesNotContain("No skills found.");
    }

    private String runPackagedCli(String scenario, List<String> arguments) throws Exception {
        Path jar = Path.of(requiredProperty("packaged.cli.jar")).toAbsolutePath().normalize();
        assertThat(jar)
            .as("Failsafe must receive the shaded JAR produced in the package phase")
            .isRegularFile();

        Path outputFile = Files.createTempFile(workingDirectory, "packaged-cli-", ".log");
        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.add("--enable-preview");
        command.add("-jar");
        command.add(jar.toString());
        command.addAll(arguments);

        ProcessBuilder processBuilder = new ProcessBuilder(command)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(outputFile.toFile());
        // Startup/help must remain usable before an external Copilot CLI or authentication is provisioned.
        processBuilder.environment().remove("COPILOT_CLI_PATH");
        processBuilder.environment().remove("GH_CLI_PATH");
        processBuilder.environment().put("PATH", workingDirectory.toString());

        Process process = processBuilder.start();

        boolean finished = process.waitFor(STARTUP_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }

        String output = Files.readString(outputFile, StandardCharsets.UTF_8);
        assertThat(finished)
            .as("%s timed out after %s. Output:%n%s", scenario, STARTUP_TIMEOUT, output)
            .isTrue();
        assertThat(process.exitValue())
            .as("%s must exit successfully. Output:%n%s", scenario, output)
            .isZero();
        assertThat(output)
            .doesNotContain(
                "Exception in thread",
                "Failed to instantiate [ch.qos.logback.classic.LoggerContext]",
                "Template not found:");
        return output;
    }

    private static Stream<Arguments> safeEntryPoints() {
        return Stream.of(
            Arguments.of("general help", List.of("--help"), List.of("Usage: review <command> [options]")),
            Arguments.of("version", List.of("--version"), List.of("Multi-Agent Reviewer")),
            Arguments.of("doctor help", List.of("doctor", "--help"), List.of("Usage: review doctor"))
        );
    }

    private void writeAgentFixture() throws IOException {
        Path agentsDirectory = Files.createDirectories(workingDirectory.resolve("agents"));
        Files.writeString(agentsDirectory.resolve("packaged-default-agent.agent.md"), """
            ---
            name: packaged-default-agent
            description: "Packaged default discovery fixture"
            model: claude-sonnet-4
            ---

            ## Role
            Review source changes.

            ## Instruction
            Review the repository and report findings.

            ## Output Format
            Findings.
            """, StandardCharsets.UTF_8);
    }

    private void writeSkillFixture() throws IOException {
        Path skillDirectory = Files.createDirectories(
            workingDirectory.resolve(".github/skills/packaged-catalog-skill")
        );
        Files.writeString(skillDirectory.resolve("SKILL.md"), """
            ---
            name: packaged-catalog-skill
            description: Packaged skill discovery fixture
            ---

            Review source changes and summarize findings.
            """, StandardCharsets.UTF_8);
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name", "")
            .toLowerCase()
            .contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }

    private static String requiredProperty(String name) throws IOException {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IOException("Required system property is missing: " + name);
        }
        return value;
    }
}

package dev.logicojp.reviewer.report;

import org.assertj.core.api.Assertions;
import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.config.TemplateConfig;
import dev.logicojp.reviewer.service.TemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;


@DisplayName("ReportGenerator")
class ReportGeneratorTest {

    @Nested
    @DisplayName("sanitizeAgentName")
    class SanitizeAgentName {

        @Test
        @DisplayName("英数字と._-以外はアンダースコアに変換する")
        void replacesUnsafeCharacters() {
            String sanitized = ReportGenerator.sanitizeAgentName("sec/agent:*? name");

            Assertions.assertThat(sanitized).isEqualTo("sec_agent____name");
        }
    }

    @Nested
    @DisplayName("writeSecureString")
    class WriteSecureString {

        @Test
        @DisplayName("親ディレクトリを作成して内容を書き込む")
        void createsParentDirectoryAndWritesContent(@TempDir Path tempDir) throws IOException {
            Path nestedFile = tempDir.resolve("reports").resolve("nested").resolve("result.md");

            ReportGenerator.writeSecureString(nestedFile, "hello-report");

            Assertions.assertThat(Files.exists(nestedFile)).isTrue();
            Assertions.assertThat(Files.readString(nestedFile)).isEqualTo("hello-report");
        }
    }

    @Nested
    @DisplayName("generateReport")
    class GenerateReport {

        @Test
        @DisplayName("成功結果からレポートファイルを生成する")
        void generatesReportFileForSuccess(@TempDir Path tempDir) throws IOException {
            var generator = newGenerator(tempDir.resolve("out"));
            var result = successResult("security/agent", "owner/repo", "review content");

            Path report = generator.generateReport(result);

            Assertions.assertThat(report.getFileName().toString()).isEqualTo("security_agent-report.md");
            Assertions.assertThat(Files.exists(report)).isTrue();
            Assertions.assertThat(Files.readString(report)).contains("review content");
            Assertions.assertThat(Files.readString(report)).contains("owner/repo");
        }

        @Test
        @DisplayName("失敗結果はエラー内容を含むレポートを生成する")
        void generatesFailureReportContent(@TempDir Path tempDir) throws IOException {
            var generator = newGenerator(tempDir.resolve("out"));
            var result = failureResult("agent-fail", "owner/repo", "timeout reached");

            Path report = generator.generateReport(result);

            Assertions.assertThat(Files.exists(report)).isTrue();
            String content = Files.readString(report);
            Assertions.assertThat(content).contains("⚠️ **レビュー失敗**");
            Assertions.assertThat(content).contains("timeout reached");
        }
    }

    @Nested
    @DisplayName("generateReports")
    class GenerateReports {

        @Test
        @DisplayName("個別失敗があっても他レポート生成を継続する")
        void continuesWhenSingleReportFails(@TempDir Path tempDir) throws IOException {
            Path fileAsDirectory = tempDir.resolve("blocked");
            Files.writeString(fileAsDirectory, "I am file");
            var generator = newGenerator(fileAsDirectory);

            var results = List.of(
                successResult("agent-a", "owner/repo", "ok-a"),
                successResult("agent-b", "owner/repo", "ok-b")
            );

            List<Path> generated = generator.generateReports(results);

            Assertions.assertThat(generated).isEmpty();
        }
    }

    private static ReportGenerator newGenerator(Path outputDirectory) {
        TemplateService templateService = new TemplateService(new TemplateConfig(null, null, null, null, null, null, null, null));
        Clock fixedClock = Clock.fixed(Instant.parse("2026-02-24T13:27:25Z"), ZoneOffset.UTC);
        return new ReportGenerator(outputDirectory, templateService, fixedClock);
    }

    private static ReviewResult successResult(String agentName, String repository, String content) {
        return ReviewResult.builder()
            .agentConfig(agentConfig(agentName))
            .repository(repository)
            .content(content)
            .success(true)
            .build();
    }

    private static ReviewResult failureResult(String agentName, String repository, String errorMessage) {
        return ReviewResult.builder()
            .agentConfig(agentConfig(agentName))
            .repository(repository)
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }

    private static AgentConfig agentConfig(String name) {
        return new AgentConfig(
            name,
            name,
            "gpt-5",
            "system",
            "instruction",
            "output",
            List.of("security"),
            List.of()
        );
    }
}

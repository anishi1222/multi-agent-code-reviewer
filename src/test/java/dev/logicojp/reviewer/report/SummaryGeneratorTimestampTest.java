package dev.logicojp.reviewer.report;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;


@DisplayName("SummaryGenerator timestamp resolution")
class SummaryGeneratorTimestampTest {

    @Test
    @DisplayName("出力ディレクトリ末尾がinvocation timestampならそれを採用する")
    void usesInvocationTimestampFromOutputDirectory() {
        Clock clock = Clock.fixed(Instant.parse("2026-02-24T23:59:59Z"), ZoneOffset.UTC);
        Path outputDirectory = Path.of("reports", "owner", "repo", "2026-02-24-13-15-18");

        String timestamp = SummaryGenerator.resolveInvocationTimestamp(outputDirectory, clock);

        Assertions.assertThat(timestamp).isEqualTo("2026-02-24-13-15-18");
    }

    @Test
    @DisplayName("出力ディレクトリ末尾がtimestampでない場合は現在時刻を使う")
    void fallsBackToCurrentTimeWhenNoInvocationTimestampDirectory() {
        Clock clock = Clock.fixed(Instant.parse("2026-02-24T13:27:25Z"), ZoneOffset.UTC);
        Path outputDirectory = Path.of("reports", "owner", "repo");

        String timestamp = SummaryGenerator.resolveInvocationTimestamp(outputDirectory, clock);

        Assertions.assertThat(timestamp).isEqualTo("2026-02-24-13-27-25");
    }
}

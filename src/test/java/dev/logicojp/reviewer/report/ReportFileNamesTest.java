package dev.logicojp.reviewer.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportFileNames")
class ReportFileNamesTest {

    @Test
    @DisplayName("エージェントレポートのファイル名を生成する")
    void generatesAgentReportFileName() {
        String filename = ReportFileNames.agentReportFileName("security");
        assertThat(filename).isEqualTo("security-report.md");
    }
}

package dev.logicojp.reviewer.report;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


@DisplayName("ReportFileNames")
class ReportFileNamesTest {

    @Test
    @DisplayName("エージェントレポートのファイル名を生成する")
    void generatesAgentReportFileName() {
        String filename = ReportFileNames.agentReportFileName("security");
        Assertions.assertThat(filename).isEqualTo("security-report.md");
    }
}

package dev.logicojp.reviewer.report;

/// Shared report filename helpers.
public final class ReportFileNames {

    private ReportFileNames() {
    }

    public static String agentReportFileName(String sanitizedAgentName) {
        return sanitizedAgentName + "-report.md";
    }
}
package dev.logicojp.reviewer.shared;

/// Utilities for report output file-naming.
public final class ReportFilenameUtils {

    private ReportFilenameUtils() {
    }

    public static String sanitizeAgentName(String agentName) {
        return agentName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

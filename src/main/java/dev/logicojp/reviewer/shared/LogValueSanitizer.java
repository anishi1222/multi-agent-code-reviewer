package dev.logicojp.reviewer.shared;

/// Neutralises CR/LF in values that are about to be written to a log or audit record.
///
/// Extracted so that the audit emitters in different layers cannot drift apart: log-injection
/// defence is a security control, and two independent copies of it is exactly how one copy
/// silently stops matching the other.
public final class LogValueSanitizer {

    private LogValueSanitizer() {
    }

    /// Returns `value` with carriage returns and line feeds replaced by spaces, or `""` when null.
    public static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ');
    }
}

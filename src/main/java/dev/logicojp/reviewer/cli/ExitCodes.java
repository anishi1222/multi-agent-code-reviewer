package dev.logicojp.reviewer.cli;

/// CLI exit codes using simplified values commonly used in CLI tools.
///
/// Note: These values are NOT the actual sysexits.h codes but simplified
/// values commonly used in CLI tools (0=success, 1=error, 2=usage, etc.).
///
/// | Code | Meaning                              | sysexits.h reference |
/// |------|--------------------------------------|----------------------|
/// | 0    | Successful execution                 | EX_OK (0)            |
/// | 1    | Internal software error              | cf. EX_SOFTWARE (70) |
/// | 2    | Invalid command-line usage            | cf. EX_USAGE (64)    |
/// | 3    | Configuration error                  | cf. EX_CONFIG (78)   |
/// | 4    | Required external dependency missing | cf. EX_UNAVAILABLE (69) |
///
/// Future additions may reference sysexits.h:
/// - EX_DATAERR (65): input data error
/// - EX_NOINPUT (66): input file not found
/// - EX_IOERR (74): I/O error
/// - EX_TEMPFAIL (75): temporary failure, retry later
public final class ExitCodes {
    /// Successful execution.
    public static final int OK = 0;

    /// Internal software error (unexpected failures).
    public static final int SOFTWARE = 1;

    /// Invalid command-line usage (bad arguments, unknown options).
    public static final int USAGE = 2;

    /// Configuration error (missing/invalid configuration files or values).
    public static final int CONFIG = 3;

    /// Required external dependency is missing or unavailable
    /// (e.g., Copilot CLI not installed, authentication not configured).
    public static final int UNAVAILABLE = 4;

    private ExitCodes() {
    }
}


package dev.logicojp.reviewer.presentation;

/// CLI exit codes using simplified values commonly used in CLI tools.
public final class ExitCodes {
    public static final int OK = 0;
    public static final int SOFTWARE = 1;
    public static final int USAGE = 2;
    public static final int CONFIG = 3;
    public static final int UNAVAILABLE = 4;
    /// One or more review agents failed; reports may be incomplete.
    public static final int PARTIAL_FAILURE = 5;

    private ExitCodes() {
    }
}

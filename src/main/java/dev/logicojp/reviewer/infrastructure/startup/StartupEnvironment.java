package dev.logicojp.reviewer.infrastructure.startup;

/// Pre-container startup abstraction for host-level process hardening.
public interface StartupEnvironment {

    /// Prepares the host environment before Micronaut and Logback start.
    void prepare();
}

package dev.logicojp.reviewer.application.port.inbound;

import java.util.List;

/// Inbound port: run system diagnostics and report their outcomes.
///
/// Implementer: {@code application.review.RunDiagnosticsUseCase}
/// Callers:     {@code presentation.command.DoctorCommand}
///
/// Covers behaviors: AUTH-09
public interface RunDiagnosticsPort {

    /// Run all available diagnostic checks.
    ///
    /// All checks are always executed — a single failure does not abort the
    /// remaining checks. Callers inspect individual {@link DiagnosticResult}s
    /// to determine overall health.
    ///
    /// @return list of diagnostic results in the order checks were run
    List<DiagnosticResult> runAll();
}

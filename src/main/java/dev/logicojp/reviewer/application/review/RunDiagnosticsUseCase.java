package dev.logicojp.reviewer.application.review;

import dev.logicojp.reviewer.application.port.inbound.DiagnosticResult;
import dev.logicojp.reviewer.application.port.inbound.RunDiagnosticsPort;
import dev.logicojp.reviewer.application.port.outbound.ManageCopilotClientPort;

import java.util.ArrayList;
import java.util.List;

/// Application use-case: run system diagnostics and report their outcomes.
///
/// Implements {@link RunDiagnosticsPort}. Each check is always executed — a single
/// failure does not abort the remaining checks, matching the contract defined in
/// {@link RunDiagnosticsPort#runAll()}.
///
/// Currently checks:
/// <ol>
///   <li>Copilot client health ({@link ManageCopilotClientPort#isHealthy()})</li>
/// </ol>
///
/// Additional checks (CLI path, SDK auth status) will be added once the
/// corresponding outbound ports are defined in T009/T011.
///
/// No framework annotations — DI is handled by the infrastructure configuration layer.
///
/// Application layer: imports only {@code application.port.*}, {@code java.*}.
public final class RunDiagnosticsUseCase implements RunDiagnosticsPort {

    private static final String CHECK_CLIENT_HEALTH = "copilot-client-health";

    private final ManageCopilotClientPort copilotClient;

    public RunDiagnosticsUseCase(ManageCopilotClientPort copilotClient) {
        this.copilotClient = copilotClient;
    }

    /// {@inheritDoc}
    @Override
    public List<DiagnosticResult> runAll() {
        List<DiagnosticResult> results = new ArrayList<>();
        checkClientHealth(results);
        return List.copyOf(results);
    }

    private void checkClientHealth(List<DiagnosticResult> results) {
        try {
            boolean healthy = copilotClient.isHealthy();
            if (healthy) {
                results.add(DiagnosticResult.pass(CHECK_CLIENT_HEALTH,
                    "Copilot client is healthy and ready"));
            } else {
                results.add(DiagnosticResult.fail(CHECK_CLIENT_HEALTH,
                    "Copilot client is not healthy — try restarting or re-authenticating"));
            }
        } catch (Exception e) {
            results.add(DiagnosticResult.fail(CHECK_CLIENT_HEALTH,
                "Failed to probe Copilot client health",
                e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }
}

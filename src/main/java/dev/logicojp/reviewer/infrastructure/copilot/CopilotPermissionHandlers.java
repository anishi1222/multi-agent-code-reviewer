package dev.logicojp.reviewer.infrastructure.copilot;

import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.PermissionRequestResult;
import com.github.copilot.rpc.PermissionRequestResultKind;

import java.util.concurrent.CompletableFuture;

/// Centralized permission handlers for Copilot sessions.
///
/// Security baseline is deny-by-default to avoid unconstrained MCP tool execution.
final class CopilotPermissionHandlers {

    private CopilotPermissionHandlers() {
    }

    static final PermissionHandler DENY_ALL = (request, invocation) ->
        CompletableFuture.completedFuture(
            new PermissionRequestResult().setKind(PermissionRequestResultKind.REJECTED));
}

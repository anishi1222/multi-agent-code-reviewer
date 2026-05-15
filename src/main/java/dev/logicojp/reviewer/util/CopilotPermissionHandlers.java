package dev.logicojp.reviewer.util;

import com.github.copilot.sdk.json.PermissionHandler;
import com.github.copilot.sdk.json.PermissionRequestResult;
import com.github.copilot.sdk.json.PermissionRequestResultKind;

import java.util.concurrent.CompletableFuture;

/// Centralized permission handlers for Copilot sessions.
///
/// Security baseline is deny-by-default to avoid unconstrained MCP tool execution.
public final class CopilotPermissionHandlers {

    private CopilotPermissionHandlers() {
    }

    public static final PermissionHandler DENY_ALL = (request, invocation) ->
        CompletableFuture.completedFuture(new PermissionRequestResult().setKind(PermissionRequestResultKind.REJECTED));
}

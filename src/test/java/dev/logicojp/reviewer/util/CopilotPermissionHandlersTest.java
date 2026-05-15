package dev.logicojp.reviewer.util;

import com.github.copilot.sdk.json.PermissionInvocation;
import com.github.copilot.sdk.json.PermissionRequest;
import com.github.copilot.sdk.json.PermissionRequestResultKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CopilotPermissionHandlers")
class CopilotPermissionHandlersTest {

    @Test
    @DisplayName("DENY_ALLはSDK互換の拒否kindを返す")
    void denyAllReturnsSdkCompatibleRejectedKind() throws ExecutionException, InterruptedException {
        PermissionRequest request = new PermissionRequest();
        PermissionInvocation invocation = new PermissionInvocation();

        var result = CopilotPermissionHandlers.DENY_ALL.handle(request, invocation).get();

        assertThat(result.getKind()).isEqualTo(PermissionRequestResultKind.REJECTED.getValue());
    }
}

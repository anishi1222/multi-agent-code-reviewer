package dev.logicojp.reviewer.cli;

import com.github.copilot.sdk.ConnectionState;
import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.json.GetAuthStatusResponse;
import com.github.copilot.sdk.json.GetStatusResponse;
import dev.logicojp.reviewer.config.CopilotConfig;
import dev.logicojp.reviewer.service.CopilotCliException;
import dev.logicojp.reviewer.service.CopilotCliPathResolver;
import dev.logicojp.reviewer.service.CopilotClientStarter;
import dev.logicojp.reviewer.service.CopilotHealthProbe;
import dev.logicojp.reviewer.service.CopilotService;
import dev.logicojp.reviewer.service.CopilotStartupErrorFormatter;
import dev.logicojp.reviewer.service.CopilotTimeoutResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DoctorCommand")
class DoctorCommandTest {

    @Test
    @DisplayName("全チェック成功時は終了コード0を返す")
    void returnsOkWhenAllChecksPass() {
        TestFixture fixture = TestFixture.builder()
            .cliPath("/usr/local/bin/github-copilot")
            .connectionState(ConnectionState.CONNECTED)
            .status(statusResponse("1.2.3", 4))
            .authStatus(authResponse(true, "octocat", "github.com", null))
            .build();
        DoctorCommand command = fixture.command();

        int exit = command.execute(new String[0]);

        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(fixture.stdout())
            .contains("All checks passed")
            .contains("\u2713")
            .contains("v1.2.3")
            .contains("(protocol 4)")
            .contains("octocat@github.com");
    }

    @Test
    @DisplayName("CLIが見つからない場合はSDK初期化を試みずUNAVAILABLEを返す")
    void returnsUnavailableWhenCliNotFound() {
        TestFixture fixture = TestFixture.builder()
            .cliPath(null)
            .build();
        DoctorCommand command = fixture.command();

        int exit = command.execute(new String[0]);

        assertThat(exit).isEqualTo(ExitCodes.UNAVAILABLE);
        assertThat(fixture.stdout()).contains("NOT FOUND");
        assertThat(fixture.stderr()).contains("issue(s) found");
        assertThat(fixture.initializeInvocations()).isZero();
    }

    @Test
    @DisplayName("SDK初期化失敗時はステータス取得をスキップしてUNAVAILABLEを返す")
    void returnsUnavailableWhenInitializationFails() {
        TestFixture fixture = TestFixture.builder()
            .cliPath("/usr/local/bin/github-copilot")
            .initializationException(new CopilotCliException("client start failed"))
            .build();
        DoctorCommand command = fixture.command();

        int exit = command.execute(new String[0]);

        assertThat(exit).isEqualTo(ExitCodes.UNAVAILABLE);
        assertThat(fixture.stdout()).contains("Init:    FAILED");
        assertThat(fixture.stdout()).contains("client start failed");
        assertThat(fixture.statusInvocations()).isZero();
        assertThat(fixture.authInvocations()).isZero();
    }

    @Test
    @DisplayName("接続状態がCONNECTEDでない場合はFAILEDとして集計する")
    void reportsConnectionStateFailure() {
        TestFixture fixture = TestFixture.builder()
            .cliPath("/usr/local/bin/github-copilot")
            .connectionState(ConnectionState.DISCONNECTED)
            .status(statusResponse("1.0.0", 4))
            .authStatus(authResponse(true, "octocat", null, null))
            .build();
        DoctorCommand command = fixture.command();

        int exit = command.execute(new String[0]);

        assertThat(exit).isEqualTo(ExitCodes.UNAVAILABLE);
        assertThat(fixture.stdout()).contains("State:   DISCONNECTED");
    }

    @Test
    @DisplayName("認証されていないauthレスポンスはNOT AUTHENTICATEDとして集計する")
    void reportsAuthFailureWhenNotAuthenticated() {
        TestFixture fixture = TestFixture.builder()
            .cliPath("/usr/local/bin/github-copilot")
            .connectionState(ConnectionState.CONNECTED)
            .status(statusResponse("1.0.0", 4))
            .authStatus(authResponse(false, null, null, "Run gh auth login"))
            .build();
        DoctorCommand command = fixture.command();

        int exit = command.execute(new String[0]);

        assertThat(exit).isEqualTo(ExitCodes.UNAVAILABLE);
        assertThat(fixture.stdout())
            .contains("Auth:    NOT AUTHENTICATED")
            .contains("Run gh auth login");
    }

    @Test
    @DisplayName("ヘルプ指定時は終了コード0を返す")
    void returnsOkWhenHelpRequested() {
        TestFixture fixture = TestFixture.builder()
            .cliPath("/usr/local/bin/github-copilot")
            .build();
        DoctorCommand command = fixture.command();

        int exit = command.execute(new String[]{"--help"});

        assertThat(exit).isEqualTo(ExitCodes.OK);
        assertThat(fixture.stdout()).contains("Usage: review doctor");
    }

    @Test
    @DisplayName("不正オプション時はUSAGEを返す")
    void returnsUsageOnUnknownOption() {
        TestFixture fixture = TestFixture.builder()
            .cliPath("/usr/local/bin/github-copilot")
            .build();
        DoctorCommand command = fixture.command();

        int exit = command.execute(new String[]{"--unknown"});

        assertThat(exit).isEqualTo(ExitCodes.USAGE);
        assertThat(fixture.stderr()).contains("Unknown option");
    }

    @Test
    @DisplayName("Java実行環境情報および設定情報が表示される")
    void printsRuntimeAndConfiguration() {
        TestFixture fixture = TestFixture.builder()
            .cliPath("/usr/local/bin/github-copilot")
            .connectionState(ConnectionState.CONNECTED)
            .status(statusResponse("1.0.0", 4))
            .authStatus(authResponse(true, "octocat", null, null))
            .build();
        DoctorCommand command = fixture.command();

        command.execute(new String[0]);

        assertThat(fixture.stdout())
            .contains("Runtime Environment")
            .contains("Java:")
            .contains("Configuration")
            .contains("Start timeout:")
            .contains("SDK status timeout:")
            .contains("SDK auth-status timeout:");
    }

    private static GetStatusResponse statusResponse(String version, int protocolVersion) {
        GetStatusResponse response = new GetStatusResponse();
        response.setVersion(version);
        response.setProtocolVersion(protocolVersion);
        return response;
    }

    private static GetAuthStatusResponse authResponse(boolean authenticated,
                                                       String login,
                                                       String host,
                                                       String statusMessage) {
        GetAuthStatusResponse response = new GetAuthStatusResponse();
        response.setAuthenticated(authenticated);
        response.setLogin(login);
        response.setHost(host);
        response.setStatusMessage(statusMessage);
        return response;
    }

    /// Test fixture for constructing DoctorCommand with controlled SDK behavior.
    private static final class TestFixture {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();
        private final CliOutput output = new CliOutput(new PrintStream(out), new PrintStream(err));
        private final String cliPath;
        private final RuntimeException initializationException;
        private final ConnectionState connectionState;
        private final GetStatusResponse status;
        private final GetAuthStatusResponse authStatus;
        private final AtomicReference<Integer> initializeCount = new AtomicReference<>(0);
        private final AtomicReference<Integer> statusCount = new AtomicReference<>(0);
        private final AtomicReference<Integer> authCount = new AtomicReference<>(0);

        private TestFixture(Builder builder) {
            this.cliPath = builder.cliPath;
            this.initializationException = builder.initializationException;
            this.connectionState = builder.connectionState;
            this.status = builder.status;
            this.authStatus = builder.authStatus;
        }

        static Builder builder() {
            return new Builder();
        }

        DoctorCommand command() {
            CopilotConfig copilotConfig = new CopilotConfig(null, null, 60, 10, 15);
            CopilotTimeoutResolver timeoutResolver = new CopilotTimeoutResolver(copilotConfig);
            CopilotService copilotService = stubService(copilotConfig, timeoutResolver);
            CopilotHealthProbe healthProbe = stubHealthProbe(timeoutResolver);
            return new DoctorCommand(stubPathResolver(), copilotService, healthProbe, timeoutResolver, output);
        }

        String stdout() {
            return out.toString();
        }

        String stderr() {
            return err.toString();
        }

        int initializeInvocations() {
            return initializeCount.get();
        }

        int statusInvocations() {
            return statusCount.get();
        }

        int authInvocations() {
            return authCount.get();
        }

        private CopilotCliPathResolver stubPathResolver() {
            CopilotConfig config = new CopilotConfig(cliPath, null, 60, 10, 15);
            return new CopilotCliPathResolver(config, System.getenv("PATH")) {
                @Override
                public String resolveCliPath() {
                    if (cliPath == null) {
                        throw new CopilotCliException("CLI not found in PATH");
                    }
                    return cliPath;
                }
            };
        }

        private CopilotService stubService(CopilotConfig copilotConfig,
                                           CopilotTimeoutResolver timeoutResolver) {
            return new CopilotService(
                stubPathResolver(),
                new CopilotHealthProbe(timeoutResolver),
                copilotConfig,
                new CopilotStartupErrorFormatter(),
                new CopilotClientStarter()
            ) {
                @Override
                public void initializeOrThrow() {
                    initializeCount.updateAndGet(c -> c + 1);
                    if (initializationException != null) {
                        throw initializationException;
                    }
                }

                @Override
                public CopilotClient getClient() {
                    return null;
                }
            };
        }

        private CopilotHealthProbe stubHealthProbe(CopilotTimeoutResolver timeoutResolver) {
            return new CopilotHealthProbe(timeoutResolver) {
                @Override
                public ConnectionState getConnectionState(CopilotClient client) {
                    return connectionState;
                }

                @Override
                public GetStatusResponse fetchStatus(CopilotClient client) {
                    statusCount.updateAndGet(c -> c + 1);
                    if (status == null) {
                        throw new CopilotCliException("status unavailable");
                    }
                    return status;
                }

                @Override
                public GetAuthStatusResponse fetchAuthStatus(CopilotClient client) {
                    authCount.updateAndGet(c -> c + 1);
                    if (authStatus == null) {
                        throw new CopilotCliException("auth status unavailable");
                    }
                    return authStatus;
                }
            };
        }

        private static final class Builder {
            private String cliPath;
            private RuntimeException initializationException;
            private ConnectionState connectionState;
            private GetStatusResponse status;
            private GetAuthStatusResponse authStatus;

            Builder cliPath(String cliPath) {
                this.cliPath = cliPath;
                return this;
            }

            Builder initializationException(RuntimeException initializationException) {
                this.initializationException = initializationException;
                return this;
            }

            Builder connectionState(ConnectionState connectionState) {
                this.connectionState = connectionState;
                return this;
            }

            Builder status(GetStatusResponse status) {
                this.status = status;
                return this;
            }

            Builder authStatus(GetAuthStatusResponse authStatus) {
                this.authStatus = authStatus;
                return this;
            }

            TestFixture build() {
                return new TestFixture(this);
            }
        }
    }
}

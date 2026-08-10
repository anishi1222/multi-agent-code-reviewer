package dev.logicojp.reviewer;

import dev.logicojp.reviewer.application.agent.LoadAgentUseCase;
import dev.logicojp.reviewer.application.port.inbound.ConfigureLoggingPort;
import dev.logicojp.reviewer.application.port.inbound.LoadAgentPort;
import dev.logicojp.reviewer.application.port.outbound.LoadAgentDefinitionsPort;
import dev.logicojp.reviewer.application.port.outbound.ResolveApplicationSettingsPort;
import dev.logicojp.reviewer.application.port.outbound.SetLogLevelPort;
import dev.logicojp.reviewer.application.startup.ConfigureLoggingUseCase;
import dev.logicojp.reviewer.infrastructure.config.ApplicationSettingsAdapter;
import dev.logicojp.reviewer.infrastructure.logging.LogbackLoggingAdapter;
import dev.logicojp.reviewer.infrastructure.parsing.AgentDefinitionLoaderAdapter;
import dev.logicojp.reviewer.presentation.CliApplication;
import io.micronaut.context.env.Environment;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Runtime proof that the split factories retain a complete, directionally correct bean graph.
///
/// Static architecture rules prove dependencies point in the right direction. These assertions
/// additionally prove that Micronaut discovers the root composition factory and each focused
/// infrastructure adapter after the former infrastructure-wide factory was removed.
@MicronautTest(environments = Environment.CLI)
@DisplayName("ApplicationPortFactory分割後のDI配線")
class ApplicationPortSplitWiringTest {

    @Inject
    CliApplication cliApplication;

    @Inject
    LoadAgentPort loadAgentPort;

    @Inject
    ConfigureLoggingPort configureLoggingPort;

    @Inject
    LoadAgentDefinitionsPort loadAgentDefinitionsPort;

    @Inject
    ResolveApplicationSettingsPort resolveApplicationSettingsPort;

    @Inject
    SetLogLevelPort setLogLevelPort;

    @Test
    @DisplayName("root factoryがinbound application use casesを配線する")
    void rootFactoryWiresApplicationUseCases() {
        assertThat(cliApplication).isNotNull();
        assertThat(loadAgentPort).isInstanceOf(LoadAgentUseCase.class);
        assertThat(configureLoggingPort).isInstanceOf(ConfigureLoggingUseCase.class);
    }

    @Test
    @DisplayName("focused infrastructure adaptersがoutbound portsを実装する")
    void focusedFactoriesWireOutboundAdapters() {
        assertThat(loadAgentDefinitionsPort).isInstanceOf(AgentDefinitionLoaderAdapter.class);
        assertThat(resolveApplicationSettingsPort).isInstanceOf(ApplicationSettingsAdapter.class);
        assertThat(setLogLevelPort).isInstanceOf(LogbackLoggingAdapter.class);
    }
}

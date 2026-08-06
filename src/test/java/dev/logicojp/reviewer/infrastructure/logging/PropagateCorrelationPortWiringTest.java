package dev.logicojp.reviewer.infrastructure.logging;

import dev.logicojp.reviewer.application.port.outbound.PropagateCorrelationPort;
import io.micronaut.context.env.Environment;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Proves the correlation port is actually bound in the running container.
///
/// The gap this closes is specific: t13 removed MDC propagation from the application layer
/// and nothing failed, because no test asserted the capability existed. Unit tests on the
/// executors prove they *use* whatever port they are handed; only resolving the bean from a
/// real `ApplicationContext` proves production hands them an implementation that writes to
/// the MDC the logback pattern actually reads (`%X{execution.id}`).
@MicronautTest(environments = Environment.CLI)
@DisplayName("PropagateCorrelationPort のDI配線")
class PropagateCorrelationPortWiringTest {

    @Inject
    PropagateCorrelationPort propagateCorrelation;

    @Test
    @DisplayName("DIコンテナがPropagateCorrelationPortをMdcCorrelationAdapterとして解決する")
    void diResolvesPortBackedByMdcAdapter() {
        assertThat(propagateCorrelation)
            .as("PropagateCorrelationPort must be resolvable, or ReviewCommand and "
                + "ReviewOrchestratorFactory cannot be constructed at all")
            .isNotNull()
            .isInstanceOf(MdcCorrelationAdapter.class);
    }
}

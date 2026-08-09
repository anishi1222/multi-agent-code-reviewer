package dev.logicojp.reviewer.application.skill;

import dev.logicojp.reviewer.application.port.outbound.RunCopilotSessionPort;
import dev.logicojp.reviewer.domain.resilience.SharedCircuitBreaker;
import dev.logicojp.reviewer.domain.skill.SkillDefinition;
import dev.logicojp.reviewer.domain.skill.SkillResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Executable parity contracts for SKL-07 and RTY-04.
@DisplayName("skill execution resilience contract")
class SkillExecutionResilienceContractTest {

    @BeforeEach
    @AfterEach
    void resetSharedSkillCircuitBreaker() {
        try {
            Method reset = SharedCircuitBreaker.class.getDeclaredMethod("reset");
            if (!reset.trySetAccessible()) {
                throw new AssertionError("cannot access skill circuit-breaker reset hook");
            }
            reset.invoke(SharedCircuitBreaker.forSkillDomain());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to reset shared skill circuit breaker", e);
        }
    }

    @Test
    @DisplayName("一時障害の後に1回だけ再試行して成功する")
    void retriesTransientFailureOnceAndReturnsRecovery() {
        AtomicInteger attempts = new AtomicInteger();
        ExecuteSkillUseCase useCase = useCase(_ -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("connection reset by peer");
            }
            return "recovered";
        });

        SkillResult result = useCase.execute("resilient-skill", Map.of());

        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("recovered");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("一時障害が継続しても最大1回の再試行で停止する")
    void capsTransientFailureAtOneRetry() {
        AtomicInteger attempts = new AtomicInteger();
        ExecuteSkillUseCase useCase = useCase(_ -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("service temporarily unavailable");
        });

        SkillResult result = useCase.execute("resilient-skill", Map.of());

        assertThat(result.success()).isFalse();
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("恒久的な認証失敗は再試行しない")
    void doesNotRetryPermanentAuthenticationFailure() {
        AtomicInteger attempts = new AtomicInteger();
        ExecuteSkillUseCase useCase = useCase(_ -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("invalid token");
        });

        SkillResult result = useCase.execute("resilient-skill", Map.of());

        assertThat(result.success()).isFalse();
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("連続失敗が閾値に達するとskill用circuit breakerを開く")
    void opensSkillCircuitAfterConsecutiveFailures() {
        AtomicInteger attempts = new AtomicInteger();
        ExecuteSkillUseCase useCase = useCase(_ -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("connection reset by peer");
        });

        SkillResult lastResult = null;
        for (int invocation = 0; invocation < 5; invocation++) {
            lastResult = useCase.execute("resilient-skill", Map.of());
        }

        assertThat(attempts.get())
            .as("four invocations make two attempts each; the fifth must fail fast")
            .isEqualTo(8);
        assertThat(lastResult).isNotNull();
        assertThat(lastResult.errorMessage()).containsIgnoringCase("circuit breaker");
    }

    @Test
    @DisplayName("timeout例外は汎用失敗ではなくtimeout固有メッセージへ変換する")
    void mapsTimeoutToSpecificFailureMessage() {
        ExecuteSkillUseCase useCase = useCase(_ ->
            sneakyThrow(new TimeoutException("deadline exceeded")));

        SkillResult result = useCase.execute("resilient-skill", Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).containsIgnoringCase("timed out");
    }

    @Test
    @DisplayName("空応答は固有メッセージを持つ失敗結果にする")
    void mapsEmptyResponseToSpecificFailureMessage() {
        ExecuteSkillUseCase useCase = useCase(_ -> "   ");

        SkillResult result = useCase.execute("resilient-skill", Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("Empty response from Copilot");
    }

    private static ExecuteSkillUseCase useCase(RunCopilotSessionPort runner) {
        SkillDefinition skill = SkillDefinition.of(
            "resilient-skill",
            "Resilient skill",
            "Exercises retry, circuit-breaker, and timeout behavior",
            "Perform the skill"
        );
        return new ExecuteSkillUseCase(
            runner,
            TestSkillCatalog.of(skill),
            "model"
        );
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable, R> R sneakyThrow(Throwable throwable) throws E {
        throw (E) throwable;
    }
}

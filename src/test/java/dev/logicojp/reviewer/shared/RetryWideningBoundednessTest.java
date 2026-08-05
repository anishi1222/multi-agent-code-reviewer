package dev.logicojp.reviewer.shared;

import dev.logicojp.reviewer.domain.resilience.SharedCircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/// Proves the safety envelope around the `RetryPolicyUtils` marker widening performed in
/// T014.1.
///
/// [RetryPolicyConsolidationTest] shows that the widened marker set *can* misclassify a
/// permanent failure as transient (naive substring matching). These tests establish that
/// the consequence is bounded and non-destructive:
///
///  * the retry budget is a hard `maxRetries + 1` ceiling — a misclassification costs
///    extra attempts, it can never become an unbounded retry loop, and
///  * the original exception is still handed to the observer and the mapped failure is
///    still returned — a misclassification delays the error, it never masks it.
///
/// Every test wires the **real** [RetryPolicyUtils#isTransientException] classifier rather
/// than a stub, so the production classification path is what is under test.
@DisplayName("RetryPolicyUtils marker widening — boundedness and error surfacing")
class RetryWideningBoundednessTest {

    /// High threshold so the circuit breaker never trips and mask the attempt-count
    /// assertions — the retry ceiling itself is what is under test here.
    private static SharedCircuitBreaker permissiveCircuitBreaker() {
        return new SharedCircuitBreaker(100, 1_000L);
    }

    private record Outcome(int attempts, int sleeps, String result, Exception finalException, int finalCalls) {}

    /// Drives [RetryExecutor] with `maxRetries = 2` (so a ceiling of 3 attempts) against an
    /// attempt body that always throws `failure`, classified by the production classifier.
    private static Outcome runUntilExhausted(Exception failure) {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger sleeps = new AtomicInteger();
        AtomicInteger finalCalls = new AtomicInteger();
        AtomicReference<Exception> finalException = new AtomicReference<>();

        RetryExecutor<String> executor = new RetryExecutor<>(
            2,
            1,
            1,
            _ -> sleeps.incrementAndGet(),
            permissiveCircuitBreaker()
        );

        String result = executor.execute(
            () -> {
                attempts.incrementAndGet();
                throw failure;
            },
            exception -> "mapped:" + exception.getMessage(),
            "ok"::equals,
            _ -> false,
            RetryPolicyUtils::isTransientException,
            new RetryExecutor.RetryObserver<>() {
                @Override
                public void onFinalException(int attempt, int totalAttempts, Exception exception, boolean transientFailure) {
                    finalCalls.incrementAndGet();
                    finalException.set(exception);
                }
            }
        );

        return new Outcome(attempts.get(), sleeps.get(), result, finalException.get(), finalCalls.get());
    }

    @Test
    @DisplayName("拡大マーカーで誤判定された恒久的障害もmaxRetries+1回で必ず打ち切られる")
    void misclassifiedPermanentFailureIsStillHardBounded() {
        // "429" matches a line number, not an HTTP status — a permanent config error that
        // the widened marker set now classifies as transient.
        Exception permanentButMisclassified = new RuntimeException("agent config parse error at line 429");

        assertThat(RetryPolicyUtils.isTransientException(permanentButMisclassified))
            .as("precondition: the widened marker set misclassifies this permanent failure")
            .isTrue();

        Outcome outcome = runUntilExhausted(permanentButMisclassified);

        assertThat(outcome.attempts())
            .as("retry budget is a hard ceiling of maxRetries + 1, never an unbounded loop")
            .isEqualTo(3);
        assertThat(outcome.sleeps())
            .as("exactly one backoff between consecutive attempts")
            .isEqualTo(2);
    }

    @Test
    @DisplayName("誤判定されてもエラーは握りつぶされず呼び出し元に伝播する")
    void misclassifiedFailureIsDelayedButNeverMasked() {
        Exception permanentButMisclassified = new RuntimeException("network policy denies access to this repository");

        Outcome outcome = runUntilExhausted(permanentButMisclassified);

        assertThat(outcome.finalCalls())
            .as("the failure must be reported exactly once after the budget is exhausted")
            .isEqualTo(1);
        assertThat(outcome.finalException())
            .as("the observer receives the original exception, not a substitute")
            .isSameAs(permanentButMisclassified);
        assertThat(outcome.result())
            .as("the caller still receives the mapped failure so the CLI can report it")
            .isEqualTo("mapped:network policy denies access to this repository");
    }

    /// Quantifies the exact cost of the widening: the same class of permanent failure
    /// costs one attempt when it matches no marker and three when it collides with one.
    /// This is the user-visible price of the union merge — a delayed error, not a lost one.
    @Test
    @DisplayName("マーカー非一致の恒久的障害は1回で即失敗する(拡大コストの対比)")
    void unmarkedPermanentFailureStillFailsFast() {
        Exception unmarked = new RuntimeException("agent definition is malformed");

        assertThat(RetryPolicyUtils.isTransientException(unmarked))
            .as("precondition: this permanent failure matches no marker")
            .isFalse();

        Outcome outcome = runUntilExhausted(unmarked);

        assertThat(outcome.attempts())
            .as("a non-transient failure must not consume the retry budget at all")
            .isEqualTo(1);
        assertThat(outcome.sleeps()).isZero();
        assertThat(outcome.finalException()).isSameAs(unmarked);
    }

    @Test
    @DisplayName("割り込みは一時的障害と判定されずリトライ予算を消費しない")
    void interruptDoesNotConsumeRetryBudget() {
        Exception interrupt = new InterruptedException("connection reset");

        Outcome outcome = runUntilExhausted(interrupt);

        assertThat(outcome.attempts())
            .as("the InterruptedException guard must short-circuit before the message check")
            .isEqualTo(1);
        assertThat(outcome.sleeps()).isZero();
        assertThat(outcome.finalException()).isSameAs(interrupt);
    }

    /// [RetryExecutor#waitRetryBackoff] swallows an [InterruptedException] raised by the
    /// sleep strategy and re-asserts the thread's interrupt flag instead of breaking out of
    /// the loop. Cancellation is therefore *preserved* but not *immediate*: the executor
    /// runs out its remaining budget before the interrupt can be observed by the caller.
    /// This test pins that behaviour so a future change to it is a deliberate one.
    @Test
    @DisplayName("バックオフ中の割り込みはフラグとして保存されるが即座には打ち切らない")
    void interruptDuringBackoffPreservesFlagWithoutBreakingLoop() {
        Thread.interrupted(); // clear any inherited flag so the assertion is meaningful

        AtomicInteger attempts = new AtomicInteger();
        RetryExecutor<String> executor = new RetryExecutor<>(
            2,
            1,
            1,
            _ -> {
                throw new InterruptedException("backoff interrupted");
            },
            permissiveCircuitBreaker()
        );

        try {
            String result = executor.execute(
                () -> {
                    attempts.incrementAndGet();
                    throw new IOException("connection reset");
                },
                exception -> "mapped",
                "ok"::equals,
                _ -> false,
                RetryPolicyUtils::isTransientException,
                new RetryExecutor.RetryObserver<>() {
                }
            );

            assertThat(result).isEqualTo("mapped");
            assertThat(attempts.get())
                .as("the loop runs its full budget rather than short-circuiting on interrupt")
                .isEqualTo(3);
            assertThat(Thread.currentThread().isInterrupted())
                .as("the interrupt flag must be re-asserted so the caller can still observe cancellation")
                .isTrue();
        } finally {
            Thread.interrupted(); // clear so the flag does not leak into sibling tests
        }
    }
}

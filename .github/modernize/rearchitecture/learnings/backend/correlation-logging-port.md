# Cross-Thread Correlation Belongs In A Port, Never A Static Helper

Thread-local logging context (MDC) must be declared as an outbound port and proven by a DI wiring test, or a refactor will delete it silently.

## What Happened

`multi-agent-code-reviewer` / t13 → t13.1. During the layered rewrite, t13 deleted the MDC
propagation helpers from `util.ExecutionCorrelation` instead of migrating them. **877 tests
still passed.** `%X{execution.id}` silently rendered blank for every log line emitted from an
agent-execution or rubber-duck thread — the exact lines where correlation matters most.

Root cause: MDC is thread-local. `ExecutorService.submit()` and `StructuredTaskScope.fork()`
start children with an *empty* MDC. The capability only exists if something explicitly captures
on the parent and re-installs in the child, and **no test asserted the capability existed** —
only tests that asserted results were correct, which they were.

t13.1 restored it as `PropagateCorrelationPort` (in `application.port.outbound`, importing only
`shared` + `java.util`) implemented by `MdcCorrelationAdapter` (`@Singleton` in
`infrastructure.logging`). This keeps the application layer free of any logging-framework import
while the framework coupling lives where it belongs.

## Takeaway

1. **Declare it as a port.** A logging/tracing context that must cross a thread boundary is a
   capability, not a utility. Static helpers in a `util`/`shared` package are invisible to
   architecture rules and to reviewers.
2. **Give the port no `noOp()` factory.** A no-op default is exactly how the capability vanished:
   code compiles, tests pass, logs go blank. One implementation; tests use the real adapter.
3. **Write a DI wiring test.** Unit tests only prove a collaborator *uses* whatever port it is
   handed. Only resolving the bean from a real `ApplicationContext` proves production hands it
   an implementation. This is the test that stops the regression recurring.
4. **Assert thread identity in propagation tests.** Without asserting the child ran on a
   different thread than the caller, the test passes vacuously if the work is ever inlined.
5. **Restore the previous context in a `finally`.** Virtual/pooled carrier threads must never be
   left carrying another task's correlation ID. Test the exceptional path explicitly.
6. `MDC.getCopyOfContextMap()` returns **`null`** when empty, not an empty map — the re-install
   helper must treat null as "clear", not skip.

## Example

```java
// application.port.outbound — no logging framework import
public interface PropagateCorrelationPort {
    void bindExecutionId(String executionId);
    void clearExecutionId();
    Map<String, String> captureContext();                       // may return null
    <T> T callWithContext(Map<String, String> ctx, CheckedSupplier<T> body) throws Exception;
}

// at every thread boundary
Map<String, String> parent = propagateCorrelation.captureContext();
executor.submit(() -> propagateCorrelation.callWithContext(parent, () -> doWork()));
```

## History
- 2026-08-05 (multi-agent-code-reviewer/t13.1): initial — written after t13 deleted MDC propagation with zero test failures.

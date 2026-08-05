# Test Conventions for This Repository

The established test style: JUnit 5 + AssertJ, Japanese `@DisplayName`, no mocking framework, package mirrors main.

## What Happened

Project: `anishi1222/multi-agent-code-reviewer`, task `t14` (tester). Before adding 3 new test
files I read the existing suite to match its conventions rather than impose my own. Recording them
so future agents don't fork the style.

## Takeaway

**Conventions to follow when adding tests here:**

- **Package mirrors main.** Test package == production package. Several production types are
  package-private by design (e.g. `CopilotClientStarter.StartableClient`) and are only testable
  from the same package. Don't "fix" this by widening production visibility.
- **JUnit 5 + AssertJ.** The `junit-jupiter` *aggregate* artifact is on the classpath, so
  `junit-jupiter-params` is available — `@ParameterizedTest` needs no extra dependency
  (already used in `CopilotConfigTest`).
- **`@DisplayName` in Japanese**, at both class and method level. This matches the whole existing
  suite; English display names would look grafted on.
- **`@Nested` classes to group scenarios**, each with its own `@DisplayName`.
- **No mocking framework.** There is none on the classpath and that is deliberate. Use hand-written
  fakes/stubs and package-private seams.
- **`_` for unused lambda parameters** (Java 22+ unnamed variables) — the codebase targets Java 27.

**Two traps specific to this codebase:**

- `SharedCircuitBreaker(int failureThreshold, long resetTimeoutMs)` — in retry tests, pass a
  **high** threshold (e.g. `100`). At a low threshold the breaker trips mid-test and swallows
  attempts, so an attempt-count assertion silently measures the breaker instead of the retry loop.
- **Never** use `SharedCircuitBreaker.forReviewDomain()` in tests — it is a shared singleton and
  leaks state across test classes, producing order-dependent failures.

Also: if a test asserts on the thread interrupt flag, clear it in a `finally` block. A leaked
interrupt flag will fail an unrelated sibling test later in the same JVM fork, and the failure
points at the wrong file.

## History
- 2026-08-05 (multi-agent-code-reviewer/t14): initial

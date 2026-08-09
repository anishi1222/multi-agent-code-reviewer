# Skill Resilience Belongs At The Use-Case Boundary

When a layered rewrite removes an SDK-specific executor, preserve retry and circuit policy in the application use case around the outbound port.

## What Happened

In `multi-agent-code-reviewer` task t14.2, the old `SkillExecutor` had owned one retry,
transient classification, timeout mapping, and the skill circuit breaker. The rewrite
replaced it with a direct `ExecuteSkillUseCase -> RunCopilotSessionPort` call, so every
policy silently disappeared even though the shared retry mechanism and domain breaker
still existed.

The fix composed `RetryExecutor<SkillResult>` and `SharedCircuitBreaker.forSkillDomain()`
inside the use case. The Copilot adapter remained a mechanism-only outbound-port
implementation.

## Takeaway

- Put retry count, failure classification, and result mapping at the use-case boundary;
  keep SDK calls behind the outbound port.
- Reuse a shared retry mechanism and domain-owned breaker rather than duplicating loops
  in infrastructure.
- Recover source policy constants before coding; here the deleted executor established
  one retry, 500 ms base backoff, and a 30 s cap.
- Prove restoration with the original red contracts: attempt counts, permanent-failure
  control, circuit fail-fast, timeout mapping, and blank-result mapping.

## History

- 2026-08-08 (multi-agent-code-reviewer/t14.2): initial

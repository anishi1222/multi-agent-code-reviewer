# t14 — RetryPolicyUtils Widening: Boundedness & Non-Masking Verdict

Answer to the coordinator's mandate in `team/tester/inbox.md` (2026-08-05T10:45Z):

> Prove the `RetryPolicyUtils` behaviour widening cannot convert a hard failure into a retry loop
> or mask an error the CLI should report, and that the `InterruptedException` guard still
> short-circuits correctly.

**Verdict: both halves CONFIRMED.** One residual risk is real and reported in §6.

## 1. Method

I did not take the t13.1 report's delta table on trust. I extracted **both** pre-consolidation
copies from `git show 5c767ef^` into `/tmp/t14-retry/` and diffed them against the merged file, so
the delta below is derived from source. The report's table turned out to be accurate; a provenance
comment in the merged file did not (§7).

## 2. The actual consolidation delta

Two independent `RetryPolicyUtils` classes were merged into one in `shared`:

| | `infrastructure.auth` copy | `shared` copy | merged result |
|---|---|---|---|
| markers | `connection reset`, `timeout`, `unavailable`, `stream closed`, `broken pipe` | `timeout`, `temporarily`, `rate limit`, `too many requests`, `429`, `503`, `connection reset`, `network` | **union of all 11** |
| `InterruptedException` | explicit → `false` | **absent** | explicit → `false` |
| null root cause | null-safe | **NPEs** | null-safe |
| `isRetryableFailureMessage` | absent | present | present |

Consequences per consumer group:

- **Group A** — former `shared` consumers (`RetryExecutor`, `ReviewRetryExecutor`): gained
  `unavailable`, `stream closed`, `broken pipe`; **gained the interrupt guard** (a correct
  *narrowing* — previously an interrupt carrying a transient-looking message would have been
  retried); gained null-safety (the old copy would NPE on a null root cause).
- **Group B** — former `infrastructure.auth` consumers (`CopilotClientStarter`,
  `GhAuthTokenProvider`): gained `temporarily`, `rate limit`, `too many requests`, `429`, `503`,
  `network`.

So the widening is real for both groups, and it is the Group B widening that the mandate is
concerned with.

## 3. Mandate (a-i) — widening cannot create a retry loop

Every consumer's retry count is a **compile-time constant loop bound**, not a function of the
classifier. The classifier can only decide *whether to use* the remaining budget; it can never
extend it.

| Consumer | Bound | Location |
|---|---|---|
| `RetryExecutor` | `totalAttempts = maxRetries + 1` | `RetryExecutor.java` L78, loop L80–120 |
| `CopilotClientStarter` | `MAX_START_ATTEMPTS = 3` | `CopilotClientStarter.java` L27 |
| `GhAuthTokenProvider` | `GH_AUTH_MAX_ATTEMPTS = 3` | — |

Pinned by tests:

- `RetryWideningBoundednessTest` — feeds a **permanent** failure carrying a newly-widened marker
  through the real `RetryPolicyUtils::isTransientException` and asserts termination at exactly
  3 attempts / 2 sleeps.
- `CopilotClientStarterRetryBoundTest` — same on the CLI startup path, asserting exactly
  `MAX_START_ATTEMPTS` attempts, that the client is still closed, and that `CopilotCliException`
  is still thrown.
- A contrast test quantifies the exact cost of the widening: an **unmarked** permanent failure
  terminates in **1** attempt, a widened-marker one in **3**. The widening's entire blast radius is
  2 extra attempts plus backoff.

**Unbounded retry is structurally impossible.** ✔

## 4. Mandate (a-ii) — widening cannot mask an error

The error object survives misclassification intact:

- `RetryExecutor` calls `observer.onFinalException(...)` with the **original** exception on
  exhaustion, then returns `exceptionMapper.map(e)`.
- `CopilotClientStarter` throws `mapExecutionException(...)`.

`RetryWideningBoundednessTest` asserts the observer receives the original exception via
`isSameAs` — reference identity, not message equality — so a wrapper or a substituted exception
would fail the test.

**The error is delayed, never lost.** ✔

## 5. Mandate (b) — the InterruptedException guard still short-circuits

Guard ordering in `RetryPolicyUtils.java` L42–61:

```
null → TimeoutException → IOException → InterruptedException → isTransientMessage
```

The interrupt guard sits **before** the message check. That ordering is the whole point: an
`InterruptedException` whose message happens to contain a transient marker must still be
non-transient.

Proving this without mutating production source (charter forbids it) needed a **negative control
inside the test**:

```java
// same message, different type — the only differing code path is the instanceof guard
assertThat(RetryPolicyUtils.isTransientException(new RuntimeException("connection reset"))).isTrue();
assertThat(RetryPolicyUtils.isTransientException(new InterruptedException("connection reset"))).isFalse();
```

If the guard were deleted, the second assertion **must** flip to `true` — the message alone would
classify it transient. So the test cannot pass vacuously. This is a proof, not a hope, and it
satisfies the `backend/architecture-rule-negative-control` learning.

Also covered: `ExecutionException`-wrapped interrupts, bare interrupts, and that an interrupt does
not consume retry budget. ✔

## 6. Residual risk — naive substring matching (REPORT, not a blocker)

`containsAny` is plain `String.contains` over a lowercased message, so the bare numeric markers
`429` and `503` match **any** message containing those digits — line numbers, byte offsets, file
paths, model IDs — and `network` / `unavailable` match permanent configuration and authorization
errors.

Concrete consequence: a permanent failure such as
`"model gpt-503-turbo not available for this account"` is classified transient and burns the full
3-attempt budget before surfacing. On the CLI startup path that is roughly **6–7.5 s** of added
latency before the user sees the real error.

This is a **latency and diagnosability** cost, not a correctness one — §3 and §4 prove the error
still arrives, bounded and unmodified. I characterised it in
`RetryPolicyConsolidationTest.SubstringCollisionCharacterisation` rather than asserting it is
correct, so if someone later tightens matching to word-boundary or type-based classification, those
tests will fail loudly and prompt a deliberate decision.

**Asymmetry worth knowing:** `isTransientException` (exception path) has **no** deny-list, while
`isRetryableFailureMessage` (result-message path) **does** (`unauthorized`, `forbidden`,
`invalid token`, `authentication`, `invalid model`, `bad request`, `400`, `401`, `403`, `404`).
The same message can therefore be transient on one path and non-retryable on the other. Tested
explicitly so nobody rediscovers it the hard way.

## 7. LOW — provenance comment is wrong

`RetryPolicyUtils.java` (~L91) annotates `"timeout"` as "originally only in
`shared.RetryPolicyUtils`". Git disproves this: `timeout` was present in **both** copies and belongs
in the shared-by-both group alongside `connection reset`. No behavioural impact, but a provenance
comment that is wrong is worse than absent — the next person auditing this consolidation will
mis-attribute the marker. → `[notify:backend]`

## 8. Observation — cancellation is preserved but not immediate

`RetryExecutor.waitRetryBackoff` (L122–129) catches `InterruptedException`, re-asserts the
interrupt flag, then **continues the loop** rather than breaking. Cancellation is therefore
preserved but deferred: the executor runs out its remaining attempts before returning.
`CopilotClientStarter.retryWithBackoff` by contrast declares `throws InterruptedException` and
propagates immediately.

Not a defect at current budgets (max 3 attempts), but if `maxRetries` is ever raised this becomes a
user-visible "Ctrl-C doesn't stop it" complaint. Pinned by a test that also clears the interrupt
flag in a `finally` block so it cannot leak into sibling tests.

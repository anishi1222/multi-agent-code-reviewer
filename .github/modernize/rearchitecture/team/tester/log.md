# tester — session log

## [t14] Full regression green (937/0/0/0); retry-widening mandate confirmed; 11 behavior-ID gaps + non-executable jar found

**Codebase / domain discoveries**

- `mvn clean verify` yields a jar with **no `Main-Class`**. The `maven-shade-plugin` execution
  `default-shade` (pom.xml L242–265) declares configuration only — no `<phase>`, no `<goals>` — so
  it never binds and never runs (zero shade lines in the build log). The `<mainClass>` at L320
  belongs to the **native-image profile**, which is easy to misread as shade's config because of
  the distance between them. The app itself is fine: run from a classpath it starts and exits 0.
- `exec:java` is unusable with `-Dexec.args` here — the pom already defines `<arguments>`, and the
  two collide with a confusing `Cannot store value into array: element type mismatch`. Use
  `dependency:build-classpath` + plain `java -cp` instead.
- `GhAuthTokenProvider` imports `RetryPolicyUtils` but only for `computeBackoffWithJitter` (L65),
  **not** `isTransientException` — its retry is unconditional over 3 attempts. Easy to assume it
  shares the classifier just from the import.
- `isTransientException` has **no** deny-list; `isRetryableFailureMessage` **does**
  (`unauthorized`, `forbidden`, `401`, `403`, …). Same message, different verdict depending on
  which path you're on. Not documented anywhere before this task.
- `RetryExecutor.waitRetryBackoff` (L122–129) catches `InterruptedException`, re-asserts the flag,
  then **continues the loop** rather than breaking — cancellation is preserved but deferred.
  `CopilotClientStarter.retryWithBackoff` propagates properly. Inconsistent, harmless at 3 attempts.

**Wrong assumptions and corrections**

- Assumed t16 (architect) had done the Tier 3 CLI smoke because `t5` assigns it there. It hadn't —
  t16 became an ADR/docs task that explicitly touched no source or config. **Startup was unowned.**
  Lesson: verify what a task *became*, not what the strategy document said it would be.
- Nearly trusted the t13.1 consolidation delta table. Re-derived it from `git show 5c767ef^`
  instead; the table was accurate, but a **provenance comment in the merged source was not**
  (`"timeout"` labelled as originating in one copy when it was in both). Re-deriving from git found
  a bug that reading the report never would have.
- Assumed the `run-tests` skill would apply. It is **.NET-only** (`dotnet test`) — wrong ecosystem
  for this Maven/Java project. `runtime-validation` was the right skill.

**Debugging dead-ends and what actually worked**

- Dead end: proving the interrupt guard is load-bearing by asserting
  `isTransientException(new InterruptedException("boom")) == false`. Vacuous — passes with the guard
  deleted. What worked: an in-test negative control pairing `RuntimeException("connection reset")`
  → `true` with `InterruptedException("connection reset")` → `false`. Same message, only the type
  differs, so the guard is the only possible cause. → `learnings/tester/negative-control-inside-the-test.md`
- Dead end: `./mvnw ... | tail -250`. Reported `tail`'s exit code, and truncated the `[arch]` lines.
  → redirect to file + capture `$?` on the same line.
- Dead end: `grep -E 'a\|b'` returned zero hits for every pattern in the first gap sweep, which
  looks exactly like "no coverage exists". `-E` wants bare `|`. Nearly reported every behavior ID
  as uncovered.
- My first build shell was killed mid-run by **another agent's** `stop_bash` — this bash session is
  shared. Use `detach: true` for long builds, and never run concurrent Maven against the same
  `target/`.

**Techniques worth reusing**

- 4 parallel read-only explore agents, one per behavior-ID cluster, to map 69 IDs → tests. Fast,
  but I re-verified **every NONE claim myself** before reporting; they held up on all spot-checks.
  Delegate the sweep, own the negative results — a false "no coverage" claim is expensive.
- Test-count reconciliation as a regression check: 892 baseline + 45 new = 937 actual. An exact
  match proves nothing was silently dropped or skipped, which a bare "BUILD SUCCESS" does not.
- `Rule 0: parsed 333/333 classes` — always make bytecode-inspecting tools print their parsed count
  against the total. This is the guard against the ArchUnit false-green class of bug.

**Learnings consumed:** devops/dual-jdk-build-activation, backend/duplicate-utility-consolidation-semantic-drift, backend/architecture-rule-negative-control, backend/archunit-java27-bytecode-ceiling, backend/correlation-logging-port

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

## [t25] Restored 2 merge-dropped assertions as mutant-proven negative controls (+5 tests, 942→962)

**Discoveries**

- `PromptContentCompactor.compactKeepingTail(s, max)` emits **no** omission marker when
  `max <= ~43`, because the marker string itself (`"\n\n... (N chars omitted for token budget)\n\n"`)
  is ~43 chars, so `available = max - marker.length()` goes non-positive and it returns a bare tail
  slice. Any test picking a small budget must assert the bare slice, not a marker. Budget must
  exceed ~43 + head + tail before the marker appears at all.
- `ReviewFindingParser.extractFindingBlocks` uses only `matcher.group(2)`; `group(1)` (the finding
  number) is captured but never read. That means a regex mutant making the number optional does
  **not** blow up on `parseInt` — it silently mis-counts. Worth knowing before designing mutants.
- `extractFindingBlocks` discards blocks whose body is empty. A fixture section with a header but no
  body is invisible even to a loosened regex — so an "extra section" negative control needs a body
  line or the control is weaker than it looks. Caught this only because M3's first design would have
  produced 3件 instead of the intended 4件.

**Wrong assumptions, corrected**

- I assumed a clean worktree for the whole task because it was clean at start. It was not: a
  concurrent agent wrote production source throughout. **Re-check `git status` immediately before
  any mutate/restore step**, not just at preflight — I nearly ran `git checkout --` on a file in a
  tree holding someone else's uncommitted work.
- I read `Tests run: 8` against the nested class display name and briefly thought I'd added 8 tests.
  Surefire attributes the outer class's tests to the `@Nested` display name and reports the outer as
  `0`. Trust the **aggregate** line and the per-class before/after arithmetic, not the per-display-name rows.

**Dead ends**

- A `perl -0pi -e 's/.../.../'` mutation of the `FINDING_HEADER` literal silently no-op'd because of
  the double-backslash escaping in the Java string. It reported success and the tests "passed",
  which would have been a **false mutant-survived** conclusion. Only the empty `git diff` I printed
  right after applying caught it. Switched to `python3` + literal `str.replace()` with an
  `assert s2 != s`. **Always assert the mutation actually landed before trusting the run.**

**Techniques worth reusing**

- Mutation testing under a no-touching-production charter: `cp -p` backup → mutate → run → restore
  from backup → verify with `shasum -c` **and** `git diff --quiet`. Never `git checkout --`, which
  is unsafe when another agent holds uncommitted work in the same worktree.
- Design mutants so their *kill sets differ*: M1 (drop the call) killed only the enabled arms; M2
  (invert the guard) additionally killed the control arm. The differing kill sets are what proves
  the control is a real control rather than a test that passes for free.
- 184 `NoClassDefFoundError`s for classes whose `.java` and `.class` both exist on disk = concurrent
  Maven on a shared `target/`, not a regression. Check `ps` for a competing build before believing
  a catastrophic result.

**Learnings consumed:** tester/test-conventions, tester/never-pipe-a-verification-build, tester/negative-control-inside-the-test, backend/merging-upstream-into-restructured-tree, backend/surefire-declared-vs-actual-test-counts, backend/one-knob-many-budgets-erases-provenance

## [t34] Closed SEC-L10 with matched model-prefix behavior (1,090/0/0/0)

- Codebase/domain discoveries: the existing model tests sampled one value per family but never
  named `ALLOWED_MODEL_PREFIXES` or measured its exact boundaries. The shipped `startsWith`
  contract admits the bare prefix, arbitrary suffixes, and case-folded variants; it rejects
  truncated, non-leading, and whitespace-prefixed lookalikes.
- Wrong assumptions and corrections: the inbox said adding a name to the liveness enumeration
  would turn it red because no test cited the constant. The scanner counts its own `@ValueSource`
  line, so that claim is false; a name-only addition is self-satisfying.
- Debugging dead-ends and what worked: no production mutation was needed or permitted. Matched
  accept/reject pairs isolate prefix length and start anchoring, while an independent expected set
  prevents reflection-driven tests from accepting newly added families automatically.
- Techniques/patterns worth reusing: for a finite private allowlist, reflect the configured set,
  assert exact membership independently, then exercise every member at both accepted and rejected
  boundaries through the public API.
- Learnings consumed: [tester/test-conventions, tester/negative-control-inside-the-test,
  tester/never-pipe-a-verification-build, tester/concurrent-agents-shared-worktree-build-races,
  security/dead-security-controls, security/charset-allowlist-block-ranges,
  backend/derive-and-sweep-finite-security-domains,
  backend/constant-variables-defeat-bytecode-liveness,
  backend/surefire-declared-vs-actual-test-counts]

## [t14.1] Added 20 PM behavior contracts; 5 exposed production defects

- Codebase/domain discoveries: TGT-07, SKL-05/06, INS-03, ORC-05, and OUT-03/09 were already
  implemented and now have direct passing evidence. `ExecuteSkillUseCase` does not carry forward
  the legacy one-retry/circuit-breaker/timeout policy, and `CopilotService.start(String)` emits no
  deprecated-API warning.
- Wrong assumptions and corrections: Java 28 is the active target (the old t14 report's Java 27
  evidence is stale). The current shaded jar is executable, so the old non-executable-jar finding
  is also stale.
- Debugging dead-ends and what worked: a future-not-done assertion alone was too scheduler-sensitive
  for ORC-05. An observing semaphore plus entry/release latches proved that the second caller had
  actually reached `acquire()` before queueing was asserted. Timeout deadline enforcement cannot
  be accelerated through the current use-case API; the executable contract therefore pins
  timeout-specific exception mapping while the missing policy seam is escalated.
- Techniques/patterns worth reusing: pair every normalization attack with a safe control; prove a
  symlink fixture is genuinely a link and keep a safe sibling as a non-vacuity control; reconcile
  the baseline and added test counts before attributing full-suite failures.
- Learnings consumed: [tester/test-conventions, tester/negative-control-inside-the-test,
  tester/never-pipe-a-verification-build, tester/concurrent-agents-shared-worktree-build-races]

## [t14.1 re-pass] All 20 unchanged PM contracts green after t14.2; full 1,106 + 4 passes

- Codebase/domain discoveries: t14.2 closes the original gaps at the intended boundaries:
  `ExecuteSkillUseCase` now preserves exact one-retry/circuit/timeout/empty-result semantics,
  while `CopilotService.start(String)` emits the redacted warning before initialization.
- Wrong assumptions and corrections: summing Surefire XML root `tests` attributes initially
  produced 1,097 and looked like nine tests disappeared. Counting actual `<testcase>` nodes and
  checking Maven's clean-run aggregate both produce 1,106; this is the known parameterized-test
  serialization offset, not coverage loss.
- Debugging dead-ends and what actually worked: report-root arithmetic was misleading. A clean
  full run plus testcase-node count, per-contract XML rows, and 15 plain + 5 parameterized source
  reconciliation independently established the real total.
- Techniques/patterns worth reusing: hash every contract file before and after a remediation
  re-pass, inspect exact assertions for weakening, run the focused closure set first, then require
  the unscoped clean build and packaged-JAR Failsafe tier.
- Learnings consumed: [tester/test-conventions, tester/negative-control-inside-the-test,
  tester/observe-before-asserting-concurrency,
  tester/concurrent-agents-shared-worktree-build-races,
  tester/never-pipe-a-verification-build,
  tester/mutation-testing-without-touching-git-state,
  backend/surefire-declared-vs-actual-test-counts]

## [t20] Final Java 28, packaged-JAR, and GraalVM 25 runtime gates pass

- Codebase/domain discoveries: native-image prunes test fixtures reached only through string
  resource paths and does not expose unregistered methods through reflection, even when the class
  itself is present. The final source tree passes 1,106 JVM tests, 1,106 native tests, 4 packaged-JAR
  tests, five isolated JAR flows, and five isolated native flows.
- Wrong assumptions and corrections: the first native 4/1,106 failure was not a production
  regression. All four assertions depended on test-harness observation mechanisms that differ under
  native image; the Java/JAR behavior and architecture predicates remained sound.
- Debugging dead-ends and what actually worked: classloader resources and runtime reflection were
  unsuitable for artifact inspection. Parsing `target/classes` and `target/test-classes` with
  `java.lang.classfile` preserved the exact owner/target and thin-entry-point contracts across JVM
  and native execution.
- Techniques/patterns worth reusing: hash the shared build inputs and each isolated copy; run the
  final Java and native commands serially; count XML `<testcase>` nodes; then execute packaged and
  native artifacts from fresh CWDs with isolated `PATH`.
- Learnings consumed: [tester/test-conventions, tester/never-pipe-a-verification-build,
  tester/concurrent-agents-shared-worktree-build-races,
  backend/surefire-declared-vs-actual-test-counts, devops/dual-jdk-build-activation,
  devops/exact-member-native-reflection]

## [t22.2] C-004/C-005 authoritative JVM/native revalidation passed

- Codebase/domain discoveries: the remediation adds one production class to the structural
  denominator (365 → 366), one JVM/native test (1,106 → 1,107), and one packaged-JAR case
  (4 → 5); both clean builds reconcile exactly, and Rule 0 parses 366/366 on JVM and native.
- Wrong assumptions and corrections: an isolated `PATH` is correct for offline CLI startup
  surfaces but deliberately makes live `doctor` return 4 because the external Copilot CLI is
  absent. Live doctor validation must retain the real prerequisite path.
- Debugging dead-ends and what actually worked: the first live retry returned process code 0 and a
  healthy client, but the harness expected `All diagnostics passed.` instead of the shipped
  `All checks passed.`. Correcting the assertion made both JAR and native probes pass on iteration 3.
- Techniques/patterns worth reusing: create two digest-matched, target-free copies; run Java and
  native clean gates serially; then execute identical populated-agent and populated-skill fixtures
  against both artifacts so empty-inventory false positives cannot recur.
- Learnings consumed: [tester/concurrent-agents-shared-worktree-build-races,
  tester/native-architecture-tests-use-filesystem-bytecode,
  tester/never-pipe-a-verification-build, tester/test-conventions,
  devops/dual-jdk-build-activation, devops/exact-member-native-reflection,
  devops/packaged-cli-smoke-at-verify, backend/canonical-discovered-skill-catalog,
  backend/surefire-declared-vs-actual-test-counts, backend/architecture-rule-negative-control]

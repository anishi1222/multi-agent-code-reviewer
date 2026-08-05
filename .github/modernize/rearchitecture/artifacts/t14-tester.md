# t14 — Full Regression Test Run (T015)

## Summary

Full Tier 2 regression is **green**: `937 tests, 0 failures, 0 errors, 0 skipped, exit code 0`.
Baseline at t13.1 was 892; I added **45 new tests** in 3 new test files, so 892 + 45 = 937 exactly —
**no pre-existing test regressed and none were silently dropped**.

Beyond the regression run this task answered the coordinator's standing mandate on the
`RetryPolicyUtils` consolidation (t13.1) and produced a behavior-ID traceability sweep over all
**69** PM behavior IDs from `t3-pm.md` §5.1–5.8.

Three things worth the coordinator's attention:

1. **Retry mandate: both halves CONFIRMED.** The behaviour widening cannot convert a hard failure
   into a retry loop, and cannot mask an error the CLI should report. The `InterruptedException`
   guard still short-circuits correctly, proven by an in-test negative control. Details in
   [t14-tester-retry-widening.md](./t14-tester-retry-widening.md).
2. **HIGH — 11 of 69 behavior IDs have no test coverage at all**, three of which are
   security controls (`SKL-06` prompt-injection scan, `INS-03` control-char/NFKC normalization,
   `TGT-07` symlink traversal prevention for review targets). Details in
   [t14-tester-traceability.md](./t14-tester-traceability.md).
3. **MEDIUM — the distributable jar is not executable, and CLI smoke is currently unowned.**
   `mvn clean verify` produces a jar with no `Main-Class`; the app itself starts fine when run
   from a classpath. t5 assigned Tier 3 smoke to "architect (T016)", but t16 became a docs/ADR
   task that explicitly touched no source or config — so nobody has been verifying that the
   shipped artifact runs. I verified startup myself to close the gap for this phase.

## Deliverables

- [t14-tester-retry-widening.md](./t14-tester-retry-widening.md) — answer to the coordinator's
  RetryPolicyUtils mandate: consolidation delta from git, boundedness proof, non-masking proof,
  interrupt-guard negative control, and the residual substring-collision risk.
- [t14-tester-traceability.md](./t14-tester-traceability.md) — 69 behavior IDs mapped to tests,
  gap register with severity, and the partial-coverage watchlist.
- [runtime-validation-report.md](./runtime-validation-report.md) — environment capability matrix,
  startup verification, tier verdicts.
- 3 new test files (source of the +45 tests) — see `## Test Results`.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/clarification.md` — confirmed CLI-only scope with no HTTP/HTML
  surface, which is why the browser/E2E tier is N/A **by design** rather than a capability downgrade.
- `.github/modernize/rearchitecture/artifacts/project-profile.yaml` — module/LOC baseline used to
  sanity-check that the regression run actually covers the whole tree (333/333 classes parsed).
- `.github/modernize/rearchitecture/artifacts/t3-pm.md` §5.1–5.8 — the 69 behavior IDs; sole source
  of the traceability matrix.
- `.github/modernize/rearchitecture/artifacts/t5-teamlead-teststrategy.md` — binding tier ownership
  (Tier 2 regression = tester = mine; Tier 1 ArchUnit-equivalent = backend; Tier 3 smoke = architect)
  and the quality gate "missing behavior-ID coverage → HIGH finding".
- `.github/modernize/rearchitecture/artifacts/t7-devops.md` — dual-JDK build precondition.
- `.github/modernize/rearchitecture/artifacts/t13-backend.md` — architecture-rule baseline
  (Rule 3: 2 violators/2 exempt, Rule 4: 3/3, Rules 6a/6b: 0 cycles) used as the no-regression
  comparison for this run.
- `.github/modernize/rearchitecture/artifacts/t13.1-backend.md` — the `RetryPolicyUtils`
  consolidation report and the 892-test baseline.
- `.github/modernize/rearchitecture/artifacts/t16-architect.md` — read to avoid duplicating Tier 3
  scope; discovered it is docs-only, which is how the unowned-smoke gap surfaced.
- `.github/modernize/rearchitecture/team/tester/inbox.md` — the coordinator's retry mandate
  (2026-08-05T10:45Z) and the devops dual-JDK precondition.

## Evidence Mapping

| Upstream artifact / section | This task's output or evidence |
|---|---|
| `inbox.md` coordinator mandate (a) "widening must not convert a hard failure into a retry loop" | `RetryWideningBoundednessTest` — misclassified permanent failure bounded at exactly `maxRetries+1` = 3 attempts / 2 sleeps; `CopilotClientStarterRetryBoundTest` bounds the CLI startup path at `MAX_START_ATTEMPTS` = 3. Verdict **CONFIRMED**, retry-widening §3 |
| `inbox.md` coordinator mandate (a) "must not mask an error the CLI should report" | `RetryWideningBoundednessTest` asserts the observer receives the **original** exception via `isSameAs` and the caller still receives the mapped failure → error is *delayed*, never *lost*. Verdict **CONFIRMED**, retry-widening §4 |
| `inbox.md` coordinator mandate (b) "InterruptedException guard still short-circuits" | `RetryPolicyConsolidationTest.InterruptGuard` — in-test negative control: same message `"connection reset"` returns `true` as `RuntimeException` and `false` as `InterruptedException`; the only differing path is the `instanceof` guard. Verdict **CONFIRMED**, retry-widening §5 |
| `t13.1-backend.md` consolidation delta table | Independently re-derived from `git show 5c767ef^` rather than trusting the report; the report's table is **accurate**, but a provenance comment in the merged file is **not** (LOW finding below). retry-widening §2 |
| `t13-backend.md` architecture-rule baseline | This run: Rule 1 `0/0`, Rule 2 `0/0`, Rule 3 `2 violators/2 exempt`, Rule 4 `3/3`, Rule 5b `0/0`, Rules 6a/6b `0 cycles`, `Rule 0: parsed 333/333 classes` — **byte-identical to baseline, no architecture regression** |
| `t5-teamlead-teststrategy.md` Tier 2 definition + behavior-ID traceability requirement | `## Test Results` below (937/0/0/0) and the full 69-ID matrix in traceability |
| `t5-teamlead-teststrategy.md` "missing coverage → HIGH finding" | 11 uncovered IDs raised as HIGH, sub-triaged by severity in traceability §3 |
| `t3-pm.md` §5.1–5.8 behavior IDs | 69-row traceability matrix; 37 DIRECT / 21 PARTIAL / 11 NONE |
| `t7-devops.md` + `devops/dual-jdk-build-activation` learning | Regression executed with explicit `JAVA_HOME=27.ea.32-open`; without it the default GraalVM 25.0.4 cannot compile `--release 27` |
| `t16-architect.md` "Docs-only task. No source or configuration files were touched." | Established that Tier 3 CLI smoke is unowned → I performed startup verification myself; see runtime-validation-report and MEDIUM finding below |

## Test Results

- Command: `JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open ./mvnw -B clean verify`
- Exit code: **0**
- Passed: **937**
- Failed: **0**
- Errors: **0**
- Skipped: **0**
- Test classes executed: 325
- Result: `BUILD SUCCESS`
- Full log: `/tmp/t14-full-regression.log`

Baseline reconciliation: 892 (t13.1) + 45 (new, this task) = 937. Exact match — no regressions,
no silently-dropped tests.

### New tests added (test files only — no production source modified, per charter)

| File | Tests | Purpose |
|---|---:|---|
| `src/test/java/dev/logicojp/reviewer/shared/RetryPolicyConsolidationTest.java` | 38 | Pins the 11-marker union, the interrupt guard (with in-test negative control), null-safety, permanent-failure classification, and characterises the substring-collision risk |
| `src/test/java/dev/logicojp/reviewer/shared/RetryWideningBoundednessTest.java` | 5 | Proves retries stay bounded and errors are never masked, wiring the **real** production classifier |
| `src/test/java/dev/logicojp/reviewer/infrastructure/copilot/CopilotClientStarterRetryBoundTest.java` | 2 | Bounds the CLI startup path at `MAX_START_ATTEMPTS`; closes the `AUTH-03` traceability gap |

All 45 passed on first run. Two of these files were independently identified during the traceability
sweep as the **DIRECT** coverage for `RTY-03` and `AUTH-03`, which previously had none.

## Findings

### HIGH — 11 of 69 behavior IDs have zero test coverage

Per `t5-teamlead-teststrategy.md`, missing behavior-ID coverage is a HIGH finding. Sub-triaged:

**Security controls with no tests (most serious):**
- `SKL-06` — skill parameter prompt-injection scan
- `INS-03` — control-character stripping / NFKC normalization
- `TGT-07` — symlink traversal prevention for **source targets** (symlink handling *is* tested for
  CLI paths and skill files, so the gap is specifically the review-target path)

**Resilience:** `SKL-07` (skill retry/circuit-breaker/timeout), `RTY-04` (skill retry max 1),
`ORC-05` (concurrency permit semaphore)

**Functional:** `SKL-05` (parameter length limit), `AUTH-10` (deprecated token API warning),
`OUT-03` (multi-pass report filename), `OUT-09` (dual-output no-suppress)

**Not a defect:** `AUTH-01` (OAuth device flow) delegates to interactive `gh auth login` and is not
unit-testable — classify as untestable-at-unit-level, not as a gap.

Every NONE was verified by me directly, not merely inherited from the sweep. Evidence per ID in
traceability §3.

Also worth flagging: `INS-01` specifies 4 languages but only EN/JA are exercised (no KO/ZH), and
`INS-02` tests Greek but not Cyrillic. Both are counted as PARTIAL, but for
injection-detection controls a partial alphabet is close to an untested one.

### MEDIUM — `mvn clean verify` produces a non-executable jar; CLI smoke unowned

`target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar` fails with `no main manifest attribute`. Cause:
the `maven-shade-plugin` execution `default-shade` in `pom.xml` (L242–265) declares **configuration
only — no `<phase>` and no `<goals>`**, so it never binds and never runs (confirmed: zero shade
lines in the build log). The `mainClass` at L320 belongs to the **native-image profile**, not shade.

The application itself is healthy — run from a classpath it starts, prints its version, and shuts
down cleanly (exit 0). So this is a packaging defect, not an application defect.

This matters because it was invisible: t5 assigned Tier 3 smoke to architect/T016, but t16 became a
documentation task that explicitly touched no source or configuration. If the intended distribution
channel is the GraalVM native image (t19), this may be by design and only needs to be written down;
if a runnable fat-jar is expected, it is broken today.

### LOW — provenance comment drift in `RetryPolicyUtils`

`RetryPolicyUtils.java` (~L91) annotates `"timeout"` as "originally only in
`shared.RetryPolicyUtils`", but `git show 5c767ef^` proves `timeout` existed in **both**
pre-consolidation copies. No behavioural impact, but an inaccurate provenance annotation is worse
than none — it is exactly the trap described in the `duplicate-utility-consolidation-semantic-drift`
learning. Routed to backend.

## Verdict

```
environment: docker: UNAVAILABLE — daemon not responding; node: AVAILABLE v26.5.1; java: 27.ea.32-open; playwright: N/A (CLI app, no HTML surface); infra-tier: N/A by design; browser-tier: N/A by design
startup:     PASS — exit_code: 0, ReviewApp --version → "Multi-Agent Reviewer dev"; Micronaut env [cli] established, Copilot client start/stop clean, correlation-ID + SECURITY_AUDIT logging active. NOTE: launched via classpath — the packaged jar has no Main-Class (MEDIUM finding).
integration: PASS — exit_code: 0, 937 passed / 0 failed / 0 errors / 0 skipped, 325 test classes
architecture: PASS — exit_code: 0, Rule 0 parsed 333/333 classes; Rules 1/2/5b 0 violators; Rule 3 2 violators/2 exempt; Rule 4 3 violators/3 exempt; Rules 6a/6b 0 cycles — identical to t13 baseline
e2e:         N/A by design — CLI application, no browser surface
overall:     PASS (with 1 HIGH finding: 11 uncovered behavior IDs; 1 MEDIUM: non-executable jar / unowned CLI smoke; 1 LOW: provenance comment drift)
```

All tiers passed with exit code 0. The HIGH finding is a **coverage gap to be scheduled**, not a
tier failure — nothing that is tested is failing.

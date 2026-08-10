# t28 — F3: reviewPasses banner reads a different config key than the executor

**Role:** backend · **Classification:** brownfield-rewrite · **Phase:** Upstream Merge
**Status:** ✅ complete — 990 tests, 0 failures (baseline 980)

---

## Summary

`presentation.formatter.ReviewOutputFormatter` bound `@Value("${reviewer.execution.review-passes:1}")`
while the executor resolves `reviewer.execution.concurrency.review-passes`. The startup banner and
the run it describes read **two different configuration keys**, so they could disagree in both
directions.

Fixed structurally, as t24 §6 and the architect's follow-up require: the pass count now reaches
`presentation` through a new **inbound port**, not through a string-named infrastructure key.
A negative-control test was added and **verified by mutation** — it goes red when the defect is
reintroduced.

---

## Upstream Artifacts Consumed

| Artifact | Used for |
|---|---|
| `.github/modernize/rearchitecture/artifacts/t24-architect.md` | §5A.5 (L456–475) and §6 F3 (L534–551) — the finding, its severity, and the "held" reasoning |
| `.github/modernize/rearchitecture/artifacts/t30-architect.md` | Layer-rule state after `36ea1bc` (the hold this task was sequenced behind); shared-worktree build isolation practice |
| `.github/modernize/rearchitecture/clarification.md` | Config-contract change expectations — whether retiring a key needs an ADR |
| `.github/modernize/rearchitecture/team/backend/inbox.md` (L1170–1230) | Coordinator's brief: port-routing is mandatory, and the anti-vacuity test requirement |
| `docs/adr/0006-ports-and-adapters-layering.md` | D1 import matrix (L32), D2 port direction (L55), D5 rule/row pairing (L119), D6 single-owner defaults (L146) |
| `learnings/backend/derived-exemptions-for-generated-beans.md` | Confirmed a new `@Singleton` factory method needs no `LayerDependencyRulesTest` edit |
| `learnings/backend/micronaut-factory-port-binding.md` | `@Factory` makes the *return type* the bean → a mis-binding is invisible to static rules, so it must be asserted against the container |

## Evidence Mapping

| Upstream claim | This task's output / evidence |
|---|---|
| t24 §6 F3 — "`ReviewOutputFormatter:26` reads `reviewer.execution.review-passes`, bound key is `reviewer.execution.concurrency.review-passes`" | `ReviewOutputFormatter` no longer names any config key; `@Value` import removed. `git diff` shows the field and annotation deleted. |
| t24 §6 F3 — "the banner can misreport the pass count" | `ReviewPassesSingleSourceTest#legacyBannerKeyNoLongerReachesTheBanner` sets the two keys to **3 and 7** and asserts port == executor. Mutation run: `expected: 3 but was: 7`. |
| Architect follow-up — "route through the inbound port, not merely correct the key" | New `DescribeReviewPlanPort` (`application.port.inbound`) implemented by `DescribeReviewPlanUseCase` (`application.review`), bound in `ApplicationPortFactory`. |
| ADR-0006 D2 — inbound port implemented in infrastructure is a layer defect | Use case lives in `application.review`; `ReviewPassesSingleSourceTest#describeReviewPlanPortIsServedByTheApplicationUseCase` asserts the container resolves it there. |
| ADR-0006 D6 — cross-layer defaults have a single owner | `ReviewPlan` **throws** on `reviewPasses < 1` instead of normalising; normalisation stays in `ExecutionConfig`. Parameterized cases `-4` and `0` prove the port inherits that normalisation rather than duplicating it. |
| t30 — shared worktree, concurrent `mvn clean` corrupts builds | Verified in an isolated `rsync` copy at `/tmp/t28iso`; `diff -r` confirms it matched the worktree byte-for-byte. |
| clarification.md — config-contract changes | Assessed below (§ Config contract); no ADR required. |

---

## The defect

| key | pre-t28 reader | effect when set |
|---|---|---|
| `reviewer.execution.concurrency.review-passes` | executor only | N passes ran, banner said **1** |
| `reviewer.execution.review-passes` | banner only | banner said **N**, **1** pass ran |

Neither key appears in `src/main/resources/application.yml`, so shipped defaults happen to agree
at 1 — the defect is **latent** until a user sets either key. Confirmed not merge-introduced:
`origin/main` contains no `review-passes` key at all.

The root cause is not the string. It is that a `presentation` class named an `infrastructure`
configuration key by string: ADR-0006 D1 forbids `presentation → infrastructure` *imports*, but a
`@Value` key is the same coupling with **none of the compile-time safety**. Nothing — not the
compiler, not `LayerDependencyRulesTest` — could observe the two keys drifting apart.

---

## The fix

```
presentation.ReviewPreparationService
    └── DescribeReviewPlanPort            (application.port.inbound)   ← NEW
            ↑ implemented by
        DescribeReviewPlanUseCase         (application.review)         ← NEW
            ↑ bound in
        ApplicationPortFactory#describeReviewPlanPort                  ← composition root
            └── ExecutionConfig::reviewPasses   ← the executor's own accessor
```

**Added (3):**
- `application/port/inbound/ReviewPlan.java` — DTO `record ReviewPlan(int reviewPasses)`; throws on
  `< 1`; `isMultiPass()`.
- `application/port/inbound/DescribeReviewPlanPort.java` — `ReviewPlan describePlan()`.
- `application/review/DescribeReviewPlanUseCase.java` — implements the port from an `IntSupplier`.
  No framework annotations.

**Changed (3 main):**
- `ApplicationPortFactory` — new `@Singleton` factory method appended (method order is load-bearing:
  bean-definition names are index-based). Binds the **method reference** `executionConfig::reviewPasses`.
- `ReviewOutputFormatter` — `@Value` import and `reviewPasses` field deleted; ctor is now
  `(CliOutput)`; `printBanner` takes a 7th parameter `ReviewPlan plan`.
- `ReviewPreparationService` — injects `DescribeReviewPlanPort`, calls `describePlan()` at print time.

### Why `IntSupplier` and not `int`

Binding `executionConfig::reviewPasses` makes the wiring itself read as *"the same accessor the
executor uses"*. Snapshotting an `int` in the factory would behave identically at runtime but would
place a **second independent read** of configuration in the composition root — precisely the shape
that produced F3.

### Rejected alternatives

| Alternative | Why not |
|---|---|
| Just fix the key string | Leaves the structural gap; the next drift is equally invisible. Explicitly ruled out by the architect. |
| Put `reviewPasses` on `ReviewRequest` | The banner prints **before** `ReviewRequest` is built (`ReviewCommand` L124 vs L128). Would require reordering the command. |
| Implement the inbound port in `infrastructure` | Repeats the ADR-0006 D2 defect. A lambda inside `ApplicationPortFactory` has the same problem — its synthetic class is in `infrastructure.copilot`. |
| Also change `ReviewContextFactory` | Value-identical already; touching it would extend blast radius onto the executor path for no gain. Deliberately untouched. |

---

## Test Results

- **Command:** `JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify`
- **Passed: 990** · **Failed: 0** · **Errors: 0** · **Skipped: 0** · BUILD SUCCESS (41s)
- Baseline 980 → **+10**, exactly the new class's 7 parameterized + 3 discrete cases.

### Anti-vacuity: verified by mutation, not by assertion

The obvious test — *"set the property to 3, assert the banner prints 3"* — passes **identically**
against the broken code, because the broken code also printed whatever its own key said. Every
assertion in `ReviewPassesSingleSourceTest` therefore compares **two independently-derived values**,
never a value against a literal:

1. what `DescribeReviewPlanPort` reports to the banner, versus
2. what `ReviewOrchestratorFactory#buildConfig(...)` — the exact call `execute()` makes — puts into
   `OrchestratorConfig`.

| # | Test | What it pins |
|---|---|---|
| 1 | `bannerAndExecutorAgreeForEveryConfiguredValue` (7 cases: `-4, 0, 1, 2, 3, 7, 64`) | Banner and executor agree across the whole input domain, **including** the normalisation corners `-4`/`0` |
| 2 | `legacyBannerKeyNoLongerReachesTheBanner` | **Negative control** — the two keys set to contradictory values (3 vs 7) |
| 3 | `absentKeysLeaveBannerAndExecutorAgreeing` | Defaults agree; single-pass banner stays silent |
| 4 | `describeReviewPlanPortIsServedByTheApplicationUseCase` | ADR-0006 D2 — container resolves the port into `application`, not `infrastructure` |

**Mutation proof.** In the isolated copy only, `ApplicationPortFactory` was reverted to the pre-t28
behaviour (bind `@Value("${reviewer.execution.review-passes:1}")`) and the class re-run:

```
[ERROR] Tests run: 10, Failures: 1
  legacyBannerKeyNoLongerReachesTheBanner:112
  [port and executor must resolve the same key]
  expected: 3
   but was: 7
```

Restoring the fix returned `Tests run: 10, Failures: 0`. The test is a real control, not a
tautology. (Test 1 stays green under this mutation by design — it exercises the mapping, not the
wiring; test 4 covers the layer, test 2 covers the key.)

---

## Config contract

Retiring `reviewer.execution.review-passes` is **not** a breaking change and needs no ADR:

- The key was never present in `application.yml`.
- It was read by exactly one consumer, whose only use was `if (reviewPasses > 1)` on a **cosmetic
  banner line** — it never influenced execution.
- Any user who set it was already getting behaviour that contradicted the banner. Removing it makes
  the banner correct rather than changing what the tool does.

The surviving key, `reviewer.execution.concurrency.review-passes`, is unchanged.

---

## Handoff / flagged, not fixed

1. **Doc drift (LOW).** `RELEASE_NOTES_en.md:1276` and `RELEASE_NOTES_ja.md:1227` document the
   **retired** key `reviewer.execution.review-passes`. Left alone — historical release notes; a docs
   owner should decide whether to correct or annotate.
2. **Stale native-image metadata (LOW, pre-existing).**
   `src/main/resources/META-INF/native-image/**/reachability-metadata.json` still references
   `dev.logicojp.reviewer.cli.$ReviewOutputFormatter$Definition` — the **pre-migration** package.
   Tree-wide staleness, affects `pom-native.xml` only, not `mvn verify`. Not touched here.
3. **Possible rule gap → architect.** `presentation` binding an infrastructure config key by string
   is invisible to `LayerDependencyRulesTest` Rule 5b, which only sees imports. Precedent still
   exists: `presentation/ReviewModelConfigResolver` uses `@Value("${reviewer.model.review:}")`.
   Raised to the architect; **no rule added by this task**, per instruction.

## Notes for downstream

- `ReviewOutputFormatter`'s constructor is now `(CliOutput)` and `printBanner` takes a 7th
  `ReviewPlan` argument. Five test call sites were updated (`ReviewOutputFormatterTest`,
  `ReviewPreparationServiceTest`, `ReviewRunExecutorTest`, `ReviewExecutionCoordinatorTest`,
  `ReviewCommandTest`). A stub is simply `() -> new ReviewPlan(1)`.
- `LayerDependencyRulesTest` needed **no** edits — generated `$…$Definition` exemptions are derived
  via `withGeneratedBeanDefinitions`, and Rule 5b's exemption set correctly stays empty.
- New factory methods in `ApplicationPortFactory` must be **appended**; bean-definition names are
  index-based.

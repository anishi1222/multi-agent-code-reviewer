# t14 — Behavior-ID Traceability Matrix (69 IDs)

Source of IDs: `t3-pm.md` §5.1–5.8. Requirement: `t5-teamlead-teststrategy.md` — Tier 2 must
demonstrate behavior-ID traceability; **missing coverage is a HIGH finding**.

Status values:
- **DIRECT** — a test asserts the specified behaviour itself.
- **PARTIAL** — the area is exercised but the specific guarantee is not fully asserted (narrower
  input space, or the observable effect isn't checked).
- **NONE** — no test asserts this behaviour.

## 1. Coverage summary

| Group | IDs | DIRECT | PARTIAL | NONE |
|---|---:|---:|---:|---:|
| AGT — agent lifecycle | 13 | 8 | 5 | 0 |
| SKL — skills | 8 | 4 | 1 | 3 |
| INS — instruction safety | 5 | 2 | 2 | 1 |
| TGT — review targets | 9 | 7 | 1 | 1 |
| ORC — orchestration | 10 | 4 | 5 | 1 |
| AUTH — authentication | 11 | 5 | 4 | 2 |
| RTY — retry | 4 | 2 | 1 | 1 |
| OUT — output/reporting | 9 | 5 | 2 | 2 |
| **Total** | **69** | **37** | **21** | **11** |

**Covered (DIRECT + PARTIAL): 58/69 = 84%. Uncovered: 11/69 = 16%.**

## 2. Per-group status

Method: four parallel read-only sweeps over `src/test/**`, one per ID cluster. I then
**independently re-verified every NONE myself** with targeted greps rather than accepting the
sweep's claim — evidence in §3. Spot-checks of the sweeps' DIRECT/PARTIAL calls agreed in every
case I sampled.

| Group | Status by ID |
|---|---|
| AGT | 01 P · 02 D · 03 D · 04 D · 05 D · 06 D · 07 P · 08 D · 09 D · 10 D · 11 P · 12 P · 13 P |
| SKL | 01 P · 02 D · 03 D · 04 D · **05 N** · **06 N** · **07 N** · 08 D |
| INS | 01 P · 02 P · **03 N** · 04 D · 05 D |
| TGT | 01 D · 02 D · 03 P · 04 D · 05 D · 06 D · **07 N** · 08 D · 09 D |
| ORC | 01 P · 02 P · 03 P · 04 P · **05 N** · 06 D · 07 D · 08 P · 09 D · 10 D |
| AUTH | **01 N** · 02 D · 03 D · 04 P · 05 D · 06 P · 07 P · 08 D · 09 D · **10 N** · 11 P |
| RTY | 01 P · 02 D · 03 D · **04 N** |
| OUT | 01 P · 02 D · **03 N** · 04 D · 05 P · 06 D · 07 D · 08 D · **09 N** |

Two IDs moved to DIRECT **because of this task**: `RTY-03` (now covered by
`RetryPolicyConsolidationTest`) and `AUTH-03` (now covered by
`CopilotClientStarterRetryBoundTest`). Both were NONE before t14.

## 3. Gap register — the 11 NONEs, with verification evidence

### 3.1 Security controls — highest severity

| ID | Behaviour | How I verified the gap |
|---|---|---|
| **SKL-06** | Skill parameter prompt-injection scan | `SkillParameterTest` asserts only construction, defaults, and equality — no injection-pattern input is ever fed to it |
| **INS-03** | Control-character stripping / NFKC normalization | Zero NFKC/normalization/control-char assertions anywhere in `CustomInstructionSafetyValidatorTest` |
| **TGT-07** | Symlink traversal prevention for **source targets** | `symlink` appears only in `CliPathResolverTest` (CLI executable path) and `SkillMarkdownParserTest` (skill files) — never on the review-target path |

`TGT-07` is the one I'd escalate first: symlink defence is demonstrably implemented and tested for
two *other* path categories, so the review-target path looks protected at a glance while actually
having no test. That asymmetry is how a regression slips in unnoticed.

### 3.2 Resilience

| ID | Behaviour | How I verified the gap |
|---|---|---|
| **SKL-07** | Skill execution retry / circuit-breaker / timeout | `SkillExecutorTest` contains exactly 2 tests: unknown-skill and happy path |
| **RTY-04** | Skill retry capped at max 1 | same file — no attempt-count assertion exists |
| **ORC-05** | Concurrency permit semaphore | `ExecutorResourcesTest` only *constructs* `new Semaphore(1)` as a fixture; no test asserts queuing or permit limiting |

### 3.3 Functional

| ID | Behaviour | How I verified the gap |
|---|---|---|
| **SKL-05** | Parameter value length limit | `SkillParameterTest` — no over-length input tested |
| **AUTH-10** | Deprecated token API warning | Zero hits for `deprecat` / `非推奨` across the whole test tree |
| **OUT-03** | Multi-pass report filename | `ReportFilenameUtilsTest` covers only `sanitizeAgentName`; the `{agent}-pass-{n}-report.md` form never appears |
| **OUT-09** | Dual-output no-suppress | no test asserts both sinks receive output |

### 3.4 Not a defect

| ID | Behaviour | Reason |
|---|---|---|
| **AUTH-01** | OAuth device flow | Delegates to interactive `gh auth login`, an external process requiring human input. Not unit-testable; would need Tier 3 manual or a recorded-fixture harness. Classify as *untestable at unit level*, not as a coverage gap. |

## 4. Partial-coverage watchlist

These count as PARTIAL, but two are close enough to NONE for security purposes that they deserve
naming:

| ID | Specified | Actually tested | Risk |
|---|---|---|---|
| `INS-01` | injection detection across **4 languages** | EN + JA only — **no KO, no ZH** | Half the specified alphabet is unexercised on a security control |
| `INS-02` | homoglyph / mixed-script detection | Greek only — **no Cyrillic** | Cyrillic is the single most common homoglyph attack alphabet in practice; its absence is conspicuous |
| `TGT-03` | special filename handling | area exercised, no special-filename assertion | moderate |
| `TGT-05` / `TGT-06` | 256 KB / 2 MB size limits | tests use **custom** limits, not the documented defaults; no log assertions | a default-value regression would pass |
| `ORC-01/02/03/04/08` | orchestration sequencing | happy paths exercised; ordering/failure-interaction guarantees not asserted | moderate |
| `AUTH-04/06/07/11` | token lifecycle edges | partially exercised | moderate |

`TGT-05`/`TGT-06` is a subtle one: testing with injected custom limits proves the *mechanism* works
but not that the *shipped defaults* are correct. A one-line change to a default constant would go
green.

## 5. Recommendation

I did not fix these — the charter scopes t14 to running the regression and reporting, and closing
11 behaviour gaps is a task-sized piece of work, not a footnote. Suggested follow-up task, in
priority order:

1. `TGT-07`, `SKL-06`, `INS-03` + Cyrillic for `INS-02` + KO/ZH for `INS-01` — security controls.
2. `SKL-07`, `RTY-04`, `ORC-05` — resilience behaviours; `ORC-05` in particular is the kind of
   thing that only fails under load, i.e. in production.
3. `SKL-05`, `AUTH-10`, `OUT-03`, `OUT-09` + default-value assertions for `TGT-05`/`TGT-06`.
4. Record `AUTH-01` as deliberately manual-tier so it stops being re-flagged every sweep.

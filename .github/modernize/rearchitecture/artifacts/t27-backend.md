# t27 — F2: remove duplicated defaults in PromptBudgetConfig

**Status:** DONE — with a material correction to the finding and to the remedy the finding prescribed.

## Summary

F2 said `PromptBudgetConfig`'s eight `@Bindable(defaultValue = "…")` literals duplicate
`PromptBudget.DEFAULT_*`, and prescribed deleting the annotations so unbound `int`s arrive
as `0` and `PromptBudget`'s compact constructor normalises them.

Both halves of that were wrong, and I verified each empirically before acting:

1. **The prescribed remedy is unsafe.** Micronaut 5.1.2 does not bind an absent key to `0`
   for a primitive record component — it refuses to construct the bean at all. Deleting the
   annotations would have converted a graceful default into a startup crash.
2. **The finding counted two sources; there are three,** and it named the wrong winner.
   `src/main/resources/application.yml` restated all eight values, and *that* is what won at
   runtime. The `@Bindable` literals were unreachable dead code in every shipped
   configuration, so removing only them would have deleted the harmless duplicate and left
   the harmful one in place.

Delivered instead: defaults now have exactly one owner (`PromptBudget`), absent-key
fall-through is preserved, and both former sources are held down by tests that fail if
either returns.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/t24-architect.md` — F2 statement (§5A.5, §6),
  the `PromptBudget` split rationale (§3 #1), and ADR-0006 Rule 1 (`domain` may not see
  Micronaut types), which constrained the remedy to keep `PromptBudget` framework-free.
- `.github/modernize/rearchitecture/artifacts/t29-backend.md` — the `SkillBudget` precedent:
  normalise `null` to a defaults instance inside a compact constructor. Reused directly here.
- `.github/modernize/rearchitecture/clarification.md` — Java 28 target; `application.yml`
  keys may change "when justified by architecture quality"; existing tests must pass.
- `.github/modernize/rearchitecture/team/backend/inbox.md` (t27 brief) — the explicit
  "unverified — do not treat as fact" clause on `@Bindable` fall-through, the negative-control
  mandate, the F3/t28 exclusion, and the co-tenancy phantom-failure warning.

## Evidence Mapping

| Upstream claim | This task's evidence |
|---|---|
| t24 §5A.5 "all eight `@Bindable` literals match `DEFAULT_*`" | Confirmed by reading both files; all eight agreed. |
| t24 §6 F2 fix: "unbound ints then arrive as `0` … identical results" | **Refuted.** Probe: `DependencyInjectionException: Property doesn't exist`. See binding matrix below. |
| t24 §6 F2: "the `@Bindable` default silently wins at runtime" | **Refuted.** Mutant M0: set the annotation to `424242`, bean still bound `12000` from `application.yml`. |
| brief: "verify Micronaut's absent-key behaviour before removing" | Binding matrix below, from a throwaway probe (`T27BindingProbeTest`, since deleted). |
| brief: "compare the two sources, not assert a literal; show the mutant" | `PromptBudgetConfigBindingTest` + six-mutant kill matrix below. |
| t29 `SkillBudget` null-normalising compact ctor | Same idiom applied to `PromptBudgetConfig`. |

## Empirical binding matrix (Micronaut 5.1.2, Java 28, `@ConfigurationProperties` record)

| Component shape | Key absent | Key present |
|---|---|---|
| `int x`, no `@Bindable` | **throws** `DependencyInjectionException` | binds |
| `int x`, no `@Bindable`, compact ctor normalises | **throws** — ctor never runs | binds |
| `@Bindable(defaultValue="7") int x` | binds `7` | binds |
| `@Nullable Integer x` + compact-ctor normalisation | binds `null` → normalised ✓ | binds ✓ |

The third row is why the current code works; the second row is why the prescribed fix does
not. No override value (`""`, `" "`) can simulate an absent key while `application.yml`
supplies it — which is also why the fall-through path was untestable before this change.

## Change

- **`infrastructure/config/PromptBudgetConfig`** — components boxed and `@Nullable`, all
  eight `@Bindable` literals removed, compact constructor substitutes `PromptBudget.DEFAULT_*`
  for `null`. The no-arg constructor now passes all `null`s, so it states no default either.
  `toPromptBudget()` and the record's public shape are unchanged; callers autobox.
- **`src/main/resources/application.yml`** — the eight literal values removed, replaced by a
  comment naming every overridable key and where the defaults live. **This is the judgement
  call in the task** (see Escalations): without it, the duplication F2 targets survives in the
  source that actually wins, and the fall-through path stays unreachable and untestable.
- **`PromptBudgetConfigBindingTest`** (new) — the negative control.
- **`PromptBudgetConfigTest`** — unchanged behaviour; the tautological default test now
  carries a comment stating what it does *not* prove and pointing at the real control.

`PromptBudget` itself is untouched, so `domain` is unaffected and ADR-0006 Rule 1 still holds.

## Negative control

Three guards, because the two former sources fail differently:

- `absentKeysFallThroughToPromptBudgetDefaults` — binds through a real `ApplicationContext`
  with no keys set and compares the binder's output against `new PromptBudget()`. Compares
  two sources; asserts no literal.
- `recordDeclaresNoBindableDefaults` — reflection over record component, accessor **and
  canonical-constructor parameter**. The first draft checked only the record component and
  the mutant survived; `@Bindable` is retained on the constructor parameter.
- `applicationYamlDeclaresNoPromptBudgetValues` — scans the shipped yaml for any of the eight
  keys, ignoring comments.

Plus `explicitConfigurationStillBinds` and `nonPositiveOverrideIsNormalised` as behaviour-
preservation guards for the boxing change.

### Mutant kill matrix

| # | Mutant | Killed by | Note |
|---|---|---|---|
| M0 | `@Bindable` literal → `424242` (pre-fix code) | **survived** | The finding's premise. Proves yaml, not the annotation, won. |
| M2 | re-add `@Bindable(defaultValue="424242")` | `recordDeclaresNoBindableDefaults` | Value is inert once components are `@Nullable`, so *only* the structural guard can catch it. |
| M2b | re-add `@Bindable(defaultValue="12000")` | `recordDeclaresNoBindableDefaults` | Pure duplication, zero behaviour change — invisible to any behavioural test. |
| M3 | re-add yaml key, drifted value | `applicationYamlDeclaresNoPromptBudgetValues`, `absentKeysFallThrough…` | 2 kills |
| M4 | re-add yaml key, **same** value | `applicationYamlDeclaresNoPromptBudgetValues` | This is exactly the state that shipped before t27, and the suite was green in it — so the old suite provably could not detect it. |
| M5 | break null-normalisation on one field | `absentKeysFallThrough…` (NPE), `explicitConfigurationStillBinds`, both tests in `PromptBudgetConfigTest` | 4 kills |
| M6 | transpose two fields in `toPromptBudget()` | 4 tests incl. the pre-existing `mapsAllFieldsToPromptBudget` | Pre-existing guard still effective. |

M2b and M4 are the important rows: both are *behaviour-neutral* reintroductions of the
duplication, so nothing but a structural guard rejects them.

## Test Results

- Command: `JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify`
- Passed: **969**
- Failed: **0**
- Skipped: 0

Baseline was 962; +5 from `PromptBudgetConfigBindingTest`, +2 from co-tenant t30's concurrent
edits to `LayerDependencyRulesTest`. Above baseline, so not a phantom run.

Runtime smoke (the actual risk of removing the yaml keys — startup binding in the real `[cli]`
environment, not the test one): `java --enable-preview -jar target/…-SNAPSHOT.jar --help`
→ exit 0, no `DependencyInjectionException`.

The temporary probe `T27BindingProbeTest` was deleted; the binding matrix above is its residue.

## Escalations

1. **t24 §6's F2 remedy is empirically unsafe** and should not be applied elsewhere as written.
   Any other config record following that advice would crash on an absent key. The `@Nullable`
   boxed + compact-ctor shape is the safe equivalent, and matches `LocalFileConfig` and
   `ExecutionConfig.sharedSessionEnabled` already in-tree.
2. **The `application.yml` edit is a shipped-config change** and therefore the one part of this
   task that warrants an explicit ruling. No key contract is broken — every key still binds if
   supplied, and defaults are unchanged — and it is trivially reversible. I made it because the
   alternative is a fix that does not fix. Flagging for architect confirmation.
3. **F2's severity rationale needs amending in the record:** the `@Bindable` literals were dead
   code, so the live drift risk came from the yaml, which F2 did not mention. Same defect, but
   the stated mechanism was wrong — worth correcting so the ADR history does not enshrine it.
4. **Other config records may have the same third source.** I did not audit whether
   `application.yml` restates defaults for the other eleven records in `infrastructure/config/`.
   Recommend a follow-up task; out of scope here.

## Out of scope (confirmed untouched)

`ReviewOutputFormatter` / F3 — held for t28 as instructed.

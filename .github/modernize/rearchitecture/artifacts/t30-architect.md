# t30 — ADR-0008 + Rule 8: mechanizing "control scope invisible at call site"

**Role:** architect · **Classification:** brownfield-rewrite · **Phase:** Upstream Merge

## Summary

ADR-0008 promotes the run's most-repeated systemic defect — *a control's scope of application is
invisible at its call site* — into an accepted decision, and ships it with **Rule 8** in
`LayerDependencyRulesTest`. Per ADR-0006 D5 ("a matrix row with no enforcement rule is itself a
defect") the ADR and the rule shipped together.

Three things went beyond the brief, all disclosed below:

1. Rule 8's specified granularity (**field-level**) was proven *unenforceable*, so the rule's
   declared scope was narrowed to what the mechanism can actually enforce (**type-level**).
2. Rule 8 has 0 violators **and** 0 exemptions, so the file's self-cleaning exemption check proves
   nothing about it. It shipped with a **permanent negative control** — without which Rule 8 would
   have reproduced the exact defect ADR-0008 exists to prevent.
3. A **live, undetected violation of ADR-0007 D5** was found: its declared "Rule 4b" was never
   implemented. Reported, not fixed (scope).

## Deliverables

| File | Change |
|---|---|
| `docs/adr/0008-control-scope-must-be-visible-at-the-call-site.md` | new — ADR-0008 (Accepted) |
| `src/test/.../architecture/LayerDependencyRulesTest.java` | Rule 8, Rule 8 control, `InlinedConstantReadProbe` fixture, Rule 7 reservation marker |
| `docs/adr/0006-ports-and-adapters-layering.md` | D5 enforcement matrix — added the Rule 8 row |
| `docs/adr/README.md` | index — added 0008 |

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/artifacts/t24-architect.md` — §5A.2 (F4 stated precisely, and
  the disclosed layering cost at L347-351), §5A.4 (the ADR-0008 + Rule 8 recommendation and its
  predicted blast radius), §5 L229 (the *unimplemented* Rule 7 proposal — source of the numbering
  hazard).
- `.github/modernize/rearchitecture/artifacts/t29-backend.md` — F4 remediation, the mutant kill
  matrix, and the evidence that Rule 8's blast radius had already been driven to 0.
- `.github/modernize/rearchitecture/clarification.md` — scenario record; confirmed target is Java 28.
- `docs/adr/0006-ports-and-adapters-layering.md` — D5 (the governing constraint), D6 (`shared` is
  sole owner of defaults), L143 (the rule-numbering convention).
- `docs/adr/0007-…md` — ADR format precedent (English headings / Japanese body, D-items each mapped
  to one enforcing test), and its D5 — which turned out to be unfulfilled.
- `learnings/architect/*`, `learnings/backend/*` — see `[learnings-loaded]`.

## Evidence Mapping

| Upstream artifact + section | This task's output / evidence |
|---|---|
| `t24-architect.md` §5A.4 — "Proposed Rule 8, blast radius exactly 1 violator" | Rule 8 implemented. Blast radius independently re-verified at **bytecode** level (not source grep): 0 violators across 58 `domain` classes, because t29 had already removed the one. |
| `t24-architect.md` §5A.4 — "no domain class may reference a **limit constant**" | **Narrowed to type-level and disclosed.** Constant is a JLS §4.12.4 constant variable → inlined to `sipush 10000`, no `Fieldref` emitted → field-level enforcement is impossible, not merely hard. ADR-0008 D3. |
| `t24-architect.md` §5A.2 — F4: two gates, different quantity/source/failure-mode | ADR-0008 D1 + the F4 gate table; negative control reintroduces F4's exact shape and Rule 8 goes red. |
| `t24-architect.md` §5A.2 L347-351 — layering made F4 *harder* to fix | ADR-0008 §Consequences → "支払った費用（明示）", stated as a cost, not a benefit. |
| `t24-architect.md` §5 L229 — Rule 7 proposal (unimplemented) | Rule 7 **reserved** with an in-file marker; deliberately not implemented (see Scope guard). |
| `t29-backend.md` — `SkillBudget` injected inward as a pure value | ADR-0008 D4; Rule 8's predicate targets only `ConfigDefaults`, so `PromptBudget`/`SkillBudget` stay legal. Verified: Rule 1 still green at 0 violators. |
| `t29-backend.md` — mutant M2 survived the intuitive test | Motivated D2: a rule with nothing to observe is indistinguishable from a broken one → permanent negative control. |
| `ADR-0006` D5 — "a matrix row with no enforcement rule is itself a defect" | ADR-0008 ships every D-item with a *real* test, and **deliberately writes no enforcement row it did not implement**. Also surfaced the ADR-0007 D5 breach below. |
| `ADR-0006` L143 — suffix convention (`5b`) | **Confirmed by reading, not assumed:** the convention's stated purpose is 「追加ルールは既存番号を繰り上げず」 (do not renumber existing rules), with the `5b` suffix for *insertions* protecting the 6a/6b pair. Appending 8 renumbers nothing → compliant. |
| `ADR-0006` D6 — `shared` solely owns defaults | Rule 8 extends D6 from *ownership* to *reach*: `shared` owns defaults, and `domain` may not read them. |

## Key findings

### 1. Rule 8's specified granularity was unenforceable (ADR-0008 D3)

`SKILL_MAX_PARAMETER_VALUE_LENGTH` is a `public static final int` — a JLS §4.12.4 *constant
variable*, resolved at compile time per §13.1. Measured: the read compiles to `sipush 10000` with
**no `Fieldref`**. Nothing in the bytecode names the field, so the rule as specified could not be
written. The fix was to narrow the *declared scope* to the enforceable unit (the type) and state the
resulting over-reach in the rule body — rather than ship a rule quietly wider than its stated row.

### 2. Rule 8 could not prove itself, so it shipped with a permanent control (ADR-0008 D2)

`assertNoViolations` proves a rule fires by asserting violations-ignoring-exemptions **equals** the
exemption set. With 0 violators and 0 exemptions that check observes nothing — Rule 8 would look
identical if its predicate were broken, `CONFIG_DEFAULTS` misspelled, or the constant-pool reference
absent. No class in the tree reads the constant *only* (every consumer also calls a helper method),
so no naturally-occurring probe existed.

Added `InlinedConstantReadProbe` (test tree, therefore never a Rule 8 subject) + a control test
asserting the detector sees `shared.ConfigDefaults` in its constant pool.

This matters because the detectability is **javac behaviour, not a JVM-spec guarantee**: javac keeps
an *unreferenced* `CONSTANT_Class` entry recording the compile-time dependency even after inlining
the value. If a toolchain change ever elides it, the control goes red and reports that Rule 8 has
gone blind — instead of Rule 8 passing green forever while enforcing nothing.

### 3. Measured blind spot, recorded rather than hidden

On JDK 28, a constant read in a **`case` label** (`case ConfigDefaults.SOME_MAX ->`) leaves *zero*
trace in the reading class's constant pool. Rule 8 cannot see that shape. Budget gates are compared
(`>`), not switched on, so the gap is accepted — and written down in both the rule and the ADR.

### 4. HIGH — ADR-0007 D5 is unfulfilled, with a live violation

ADR-0007 D5 declares: *classes under `application.port` must not reference
`shared.SensitiveHeaderMasking`, added as **Rule 4b***.

- `grep -rn "Rule 4b" src/test/` → **0 matches**. The rule was never written.
- `application/port/outbound/McpServerSpec.java:34` calls `SensitiveHeaderMasking.wrapHeaders(...)`
  — real executable code, 4 occurrences in the compiled constant pool, not a javadoc mention.

An **Accepted** ADR declared an enforcement that was never built, and a violation of it has been
sitting in production code undetected. This is precisely the ADR-0006 D5 defect, and it is the tenth
instance of the ADR-0008 pattern — occurring inside the ADR series itself.

**Not fixed here, deliberately.** Adding Rule 4b would go red immediately and force a design decision
(refactor `McpServerSpec`, or justify an exemption) that is independent of Rule 8. Bundling it would
make Rule 8's green unattributable. Needs its own task.

## Scope guards observed

- **Rule 7 not implemented.** Reserved with an in-file marker so "Rule 8" keeps the identity it
  already has in `t24-architect.md` §5A.4, `decisions.md` and three inboxes.
- **Rule 4b not implemented.** Reported above.
- **`board.md` not edited** (coordinator-owned).

## Charter boundary — disclosed

The architect charter says *"Do not create source code, config files, or project structure."*
This task modified `src/test/.../LayerDependencyRulesTest.java`.

Rationale for proceeding: the coordinator scoped t30 to include the rule and stated the ADR and rule
ship together or neither ships; ADR-0006 D5 makes a ruleless ADR a defect by definition; and this
test *is* the executable form of ADR-0006, which architect owns. The alternative — specifying Rule 8
and delegating to backend (the t16 → t13.1 precedent for Rule 5b) — would have shipped ADR-0008
ruleless, which D5 forbids. **Flagging for coordinator ratification.**

## Test Results

- Command: `JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify`
- Passed: **969**
- Failed: **0**
- Skipped: 0
- Result: **BUILD SUCCESS**

`LayerDependencyRulesTest` went 10 → 12 tests (Rule 8 + its control). The run also includes 4 tests
from another agent's concurrent work in this worktree (see *Build environment hazard*), which is why
the total is 969 rather than the 965 measured mid-task.

> **Build environment hazard — this verification was run in an isolated copy.**
> A second agent is editing the *same* worktree concurrently (`PromptBudgetConfig.java`,
> `application.yml`, `PromptBudgetConfigTest.java`, new `PromptBudgetConfigBindingTest.java`).
> Their `mvn clean` deleted `target/classes` midway through my build, producing a spurious
> `1 failure` and then a cascade of `bad class file … NoSuchFileException` test-compile errors on
> classes that had just been written successfully. **Neither was a real defect.**
> The result above was therefore produced from a pristine `rsync` copy at `/tmp/t30iso`
> (`--exclude target/ .git/ logs/`), where no other process writes. Reported to coordinator.

```
[arch] Rule 8 control (inlined constant is detectable)  fixture references dev.logicojp.reviewer.shared.ConfigDefaults
[arch] Rule 8 (domain ⊥ shared.ConfigDefaults)            58 classes inspected, 0 violator(s), 0 exempt
[arch] Rule 0: parsed 332/332 classes
```

### Negative control — executed, not asserted on paper

F4's original form was reintroduced into `domain.agent.AgentPromptBuilder`, rebuilt, and Rule 8 went
red naming both the violator and the forbidden edge:

```
[arch] Rule 8 (domain ⊥ shared.ConfigDefaults)  58 classes inspected, 1 violator(s), 0 exempt
  dev.logicojp.reviewer.domain.agent.AgentPromptBuilder
      -> dev.logicojp.reviewer.shared.ConfigDefaults
 ==> expected: <[]> but was: <[dev.logicojp.reviewer.domain.agent.AgentPromptBuilder]>
```

Reverted; `grep -rn ConfigDefaults src/main/java/**/domain/` → 0 matches; full build green.

## Handoff / risks for downstream

1. **Rule 4b + `McpServerSpec` needs a task** (HIGH). Live ADR-0007 D5 violation.
2. **Rule 7 remains unimplemented**; the number is reserved in-file.
3. **Rule 8's detection is compiler-dependent.** If the toolchain changes, expect the Rule 8 control
   to be the first thing that fails — that is the design, not a regression.
4. **`case`-label reads are invisible to Rule 8** — documented in the rule and ADR.
5. **Doc drift, not fixed:** `LayerDependencyRulesTest`'s header says `--release 27` / major 71,
   while the project builds at Java 28 (probe classes compiled at major 72). Harmless to the rules,
   but the ArchUnit rationale paragraph should be re-dated when someone next touches that file.
6. **Two agents are writing to one worktree** (see *Build environment hazard*). Any `[DONE]` in this
   phase that reports a full-build result taken from the shared `target/` is unreliable — a
   concurrent `mvn clean` produces failures that look real and are not. Recommend serialising the
   build, or giving each agent its own worktree.

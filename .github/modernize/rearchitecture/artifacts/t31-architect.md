# t31 — ADR-0007 D5 Rule 4b, and the `McpServerSpec` violation

**Verdict: RESOLVED.** Rule 4b exists, was observed RED against the real violator, and is now
GREEN. The violation is gone. Masking for the same inputs is proven still to happen — by a
canary that reads the shipped configuration and that I verified can fail.

`980 tests, 0 failures` (baseline 969 + the 11 added here). `BUILD SUCCESS`.

---

## 1. What the defect actually was

ADR-0007's Enforcement table (line 240) promised a **Rule 4b** forbidding `application.port`
from referencing `shared.SensitiveHeaderMasking`. The rule was never written. `McpServerSpec`
was calling `SensitiveHeaderMasking.wrapHeaders(...)` the whole time, undetected.

So this is the *same* defect the matrix-row learning already names: **an ADR reached `Accepted`
while one of its D-items pointed at a rule that did not exist.** The row was the promise; nothing
made the promise executable. I have proposed a guard for this in §7.

## 2. The premise I had to check first — and the one that was missing

Before writing the rule I verified both stated premises, and found a third that the task framing
had omitted.

| Premise | Source | Verified? |
|---|---|---|
| No Rule 4b exists | task | ✅ `grep "Rule 4b" src/test/` → 0 matches |
| `McpServerSpec:34` calls `wrapHeaders` | task | ✅ real |
| **D5 must not be done before D6** | **ADR-0007 migration risk (HIGH), not the task** | ⚠️ **omitted from the task; D6 was unimplemented** |

Deleting the wrapper to make Rule 4b green — the obvious reading of "resolve the violation" —
is exactly the sequence ADR-0007 marks as a HIGH-risk regression. **The ADR's own ordering
constraint outranks the task framing**, so I executed D6 → D5 → Rule 4b green.

## 3. Measuring the exposure instead of assuming it

ADR-0007 recorded the SDK's behaviour as *unverified*. I verified it, at bytecode level, against
`copilot-sdk-java:1.0.8`:

- `McpHttpServerConfig.setHeaders` — a plain `putfield`. **No defensive copy.**
- **Neither `McpHttpServerConfig` nor `McpServerConfig` overrides `toString()`.**
- **Zero** call sites in `src/main` log an `McpServerSpec`, its headers, or a request.
  (`ReviewOrchestratorFactory:107` logs `ExecutionConfig`, which does not contain one.)

The wrapper guarded only `toString()`; `get()`/`getValue()` returned raw **by design**, so the
SDK path was never covered. Combined with the above: **the one surface the wrapper guarded was
unreachable past the boundary, and nothing traverses it today.** The wrapper was not a weak
defence — it was not a defence.

That *strengthens* the case for D5, but it does **not** dissolve the ordering requirement,
because of the one genuine coverage difference in §4.

## 4. The coverage difference — and why it decided the design

The pre-existing logback `%replace` (commit `8d8fec1`, **predating ADR-0007** and recorded in
neither the ADR nor any learning) masks by **value shape** (`Bearer …`, `ghp_…`, `sk-…`).
The wrapper masked by **header name**. These are not the same set.

The wrapper's only real extra coverage was **opaque custom header values** — e.g.
`X-API-Key: <no recognizable prefix>` arriving via `reviewer.mcp.github.headers`. D6 closes
exactly that, with a second, name-based pattern:

```
MASK_PATTERN        (pre-existing, value-shape)
HEADER_MASK_PATTERN (added, header-name)   →  both replace with $1***
```

**Nesting order is load-bearing**: `%replace(%replace(%msg){MASK_PATTERN}){HEADER_MASK_PATTERN}`.
Value-shape must run **innermost** — `HEADER_MASK_PATTERN`'s value class stops at whitespace, so
in the reverse order it consumes only the word `Bearer` and **leaves the token exposed**. In
`logback-json.xml` both mask passes sit **inside** the two JSON-escaping passes. I validated the
composed regexes in a standalone program *before* touching either config.

## 5. Evidence: red first, then green, then proven falsifiable

**Rule 4b, on introduction (RED — the real violator, not a synthetic one):**
```
Rule 4b (application.port ⊥ shared.SensitiveHeaderMasking)
Violations found:
  dev.logicojp.reviewer.application.port.outbound.McpServerSpec
      -> dev.logicojp.reviewer.shared.SensitiveHeaderMasking
 ==> expected: <[]> but was: <[...McpServerSpec]>
Tests run: 13, Failures: 1
```

**After D5 (GREEN), with its subject count asserted:**
```
[arch] Rule 4b (application.port ⊥ shared.SensitiveHeaderMasking)
       22 classes inspected, 0 violator(s), 0 exempt
[arch] Rule 4b control: 22 subject(s), fixture references
       dev.logicojp.reviewer.shared.SensitiveHeaderMasking
```

**The canary, mutation-tested** (I broke `HEADER_MASK_PATTERN` and re-ran):

| Canary case | Under the break | Reading |
|---|---|---|
| opaque custom header (`X-API-Key`) | **RED** — `X-API-Key=CANARY…LEAKED!v@lue#` rendered | D6 is what carries the wrapper's real coverage |
| `Bearer` token in an `McpServerSpec` | green | pre-existing shape pattern already covered it |
| raw token by shape | green | ditto |
| every sensitive header name | **RED** | name-based coverage is live |
| both profiles identical | **RED** | config-shape control is live |

The mutation **independently confirms the §4 coverage analysis**: precisely the cases I claimed
were newly covered are the ones that fail when the new pattern is removed. A green I never saw
red would have proven nothing; this is the same discipline applied twice.

## 6. Vacuity control (per t24's standing pattern)

Rule 4b lands at **0 violators + 0 exemptions** — structurally blind. t24 §4 names the failure
mode exactly: *a control's scope of application is invisible at the call site*. So the control
asserts two independent things:

1. `classesIn(APPLICATION_PORT)` is **non-empty** — a typo'd prefix would inspect 0 classes and
   pass silently. Currently **22** subjects.
2. A `MaskingReferenceProbe` fixture's constant pool **does** contain `SensitiveHeaderMasking` —
   so the detection mechanism itself is proven live.

Mirrors the existing Rule 8 control's technique. Rule numbering follows ADR-0006 D5: alphabetic
suffix at the logical position, **nothing renumbered**; Rule 7 stays reserved.

## 7. Recommended amendment (not applied — needs ratification)

Add to ADR-0006's process rules: **an ADR must not reach `Accepted` while any D-item names an
enforcement rule absent from the test tree.** This defect is the third instance of a promise
recorded in a matrix row with nothing making it executable. The row is not the control.

## 8. Charter disclosure — scope I exceeded

The inbox brief granted me `LayerDependencyRulesTest.java` explicitly. I also changed:

- `McpServerSpec.java`, `SensitiveHeaderMasking.java` (production source — D5)
- `logback.xml`, `logback-json.xml` (production config — D6)
- `GithubMcpConfigTest.java`, `SensitiveHeaderMaskingTest.java` (tests asserting removed behaviour)
- `SensitiveHeaderMaskingSinkCanaryTest.java` (new — the brief's mandatory control)

D5 and D6 *are* the ADR items I was asked to fulfil and cannot be done from the test tree, so I
judged this in scope and follow t30's disclosure precedent. **Flagging for ratification.**
`security` owns the masking semantics and was notified before finalising.

## 9. Residual limits (recorded in the ADR)

Sink masking is a control over **text shape**. It covers log/diagnostic output; it does not cover
serialized JSON bodies, heap/core dumps, debuggers, or any SDK-internal output path that bypasses
our logback. Object-level masking covered none of those either — **this is not a regression** —
but the secret still exists in process memory in plaintext. Token lifetime and least-privilege
remain separate concerns.

---

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/clarification.md` — run-wide constraints.
- `.github/modernize/rearchitecture/artifacts/t30-architect.md` — disclosure precedent for
  architect edits to production source; carried forward its open doc-drift item (header still says
  `--release 27` / major 71 while the project builds at Java 28 — **still unaddressed**, not in scope here).
- `.github/modernize/rearchitecture/artifacts/t24-architect.md` — the vacuity/scope pattern in §4
  and the rule-reporting convention (`[arch] Rule N … N classes inspected`).
- `.github/modernize/rearchitecture/team/architect/inbox.md` — the coordinator's 4-point t31 brief.
- `docs/adr/0007-…md`, `docs/adr/0006-…md` — the decisions under enforcement.

## Evidence Mapping

| Upstream | → This task |
|---|---|
| ADR-0007 Enforcement table, **line 240** (D5 row) | Rule 4b implemented at `LayerDependencyRulesTest.java:~246`; row updated to ✅ |
| ADR-0007 **D6** (sink-side masking) | `HEADER_MASK_PATTERN` in both logback profiles + `SensitiveHeaderMaskingSinkCanaryTest` |
| ADR-0007 **migration risk (HIGH)**, "D5 must not precede D6" | execution order ①Rule 4b RED ②D6 ③D5 ④GREEN; risk marked discharged |
| ADR-0007 "`toString()` override unverified" caveat | replaced with measured bytecode facts (§3) |
| ADR-0006 **D5** (alphabetic suffix, never renumber) | `4b`, not `9`; Rule 7 untouched |
| t24 §4 — control scope invisible at call site | Rule 4b subject-count + probe control (§6) |
| t30 — architect production-source disclosure | §8 disclosure |
| inbox brief pt. 3 — "ship a control proving masking still occurs" | §5 mutation table |

## Test Results

- Command: `JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify`
- **Passed: 980** · **Failed: 0** · Errors: 0 · Skipped: 0 · `BUILD SUCCESS`
- Baseline 969 → +11: Rule 4b, its control, and 9 canary tests. No pre-existing test lost.
- Targeted set (`LayerDependencyRulesTest`, `SensitiveHeaderMaskingSinkCanaryTest`,
  `SensitiveHeaderMaskingTest`, `GithubMcpConfigTest`): 42 passed, 0 failed.
- Mutation runs are recorded in §5; both configs restored and re-verified afterwards.

## Issues for downstream

1. **Ratification needed** for the production-source/config edits in §8.
2. **ADR-0006 amendment proposed** in §7 — not applied.
3. `GithubMcpConfigTest.masksAuthorizationInHeadersToString` was **renamed**
   (`exposesRawAuthorizationBecauseMaskingMovedToTheSink`) and now asserts the inverse. Anyone
   holding that old name in a checklist should update it.
4. **Tooling hazard for every agent on this run**: the tool-output pipeline redacts `Bearer …`
   literals to `******` in *all* output — `cat`, `grep`, `view`, `sed`, `repr`. Source lines look
   like they contain broken `"******"` defaults when they do not. **Only `base64` reveals the
   truth.** This came close to corrupting `GithubMcpConfig.java:52` and `application.yml:88`.
   Written up as a learning.
5. t30's Java-28 doc-drift item in `LayerDependencyRulesTest`'s header comment is still open.

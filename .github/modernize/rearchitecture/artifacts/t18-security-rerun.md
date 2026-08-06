# t18 (re-run) — Security review after t18.2 remediation

**Verdict: GATE NOT PASSED — 1 HIGH open.**

SEC-H1 and SEC-H2 are genuinely closed. F1 is genuinely closed. But the F1 *defect class*
is not: the same "block range named for what it admits, never checked for what else it
admits" mistake survives in a **different** range of the same constant, and the character
it lets through defeats **both** the charset allowlist and the prompt-injection denylist.

> Naming note: this file does not overwrite `t18-security.md`. That artifact is cited by
> `t18.1-architect.md` and ADR-0007 by name; destroying it would break their evidence
> chain. This re-run is additive and reconciles against it finding-by-finding.

## Deliverables

- [t18-security-rerun-charset-audit.md](./t18-security-rerun-charset-audit.md) — full
  codepoint-level evidence: the exhaustive sweep, the two-layer bypass proof, and the
  per-range attribution table.

## Answers to the three inputs I was given

| # | Input | Answer |
|---|---|---|
| 1 | F1 (`ALLOWED_CHAR_RANGE` block range) — claimed closed | **Confirmed closed.** All 21 codepoints F1 set out to exclude (U+200B–200F, U+202A–202E, U+2060–2064, U+2066–2069, U+2028, U+2029) are rejected. 0 leaks. Japanese typography (U+203B, dashes, curly quotes, ellipsis) still admitted — no regression. |
| 2 | Backend's suggestion — audit for other block ranges | **Done, and it found a HIGH.** See SEC-H3 below. Backend's instinct was right and the defect was still live. |
| 3 | Coordinator's LOW — `ALLOWED_CHAR_RANGE` not in the liveness test | **Confirmed, and it is two constants, not one.** See SEC-L10. |

## Findings

| ID | Sev | Boundary | Location | Summary |
|---|---|---|---|---|
| **SEC-H3** | **HIGH** | B3 | `CustomInstructionSafetyValidator.java:69-93` (range `\uFF00-\uFFEF`) | `U+FFA0` is admitted by the allowlist **and** invisible to the denylist. Both layers bypassed by one character. |
| SEC-M7 | MEDIUM | B3 | same constant, ranges 3 / 6 / 7 | 30 **unassigned** (`Cn`) codepoints admitted. Same silent-passthrough behaviour as SEC-H3; future Unicode assignment decides what they become. |
| SEC-L10 | LOW | — | `AgentPolicyConstantsAreLiveTest.java:48-54` | `ALLOWED_CHAR_RANGE` **and** `ALLOWED_MODEL_PREFIXES` are absent from the enumeration and have **0** `src/test` references. |
| SEC-L11 | LOW | — | ADR-0007 D4 | "not suppressible by `--quiet`" is **vacuously true** — no `quiet` flag exists in `src/main/java`. A guarantee with no subject. |

Deliberately **not** raised as new findings: `ALLOWED_MODEL_PREFIXES` prefix matching and
`ModelConfig` `contains`/`startsWith` (a sub-agent proposed CRITICAL/HIGH; both are wrong —
see the audit file's recalibration section). `CliPathResolver` and `SensitiveHeaderMasking`
were checked and are correct.

### SEC-H3 (HIGH) — `U+FFA0` defeats the allowlist and the denylist together

`\uFF00-\uFFEF` ("Halfwidth and Fullwidth Forms") was included for fullwidth ASCII and
halfwidth katakana. It also contains **U+FFA0 HALFWIDTH HANGUL FILLER**, which renders as
blank. Measured, with the real shipped `safety/suspicious-patterns.txt`:

```
charset=ADMIT  denylist=FLAGGED  "ignore all previous instructions"        BLOCKED (baseline works)
charset=REJECT denylist=FLAGGED  "ig<U+200B>nore all previous..."          BLOCKED (F1's fix works)
charset=ADMIT  denylist=silent   "ig<U+FFA0>nore all previous..."          **** REACHES THE MODEL ****
```

Why the denylist misses it — `normalize()` is not at fault, the character class is:

```
raw        : ig\uFFA0nore
NFKC       : ig\u1160nore      <- one invisible filler maps to another
post-strip : ig\u1160nore      <- CONTROL_CHARS_PATTERN is [\p{Cf}\p{Cc}]
U+FFA0 category = Lo (OTHER_LETTER), not Cf, not Cc -> never stripped
```

Confirmed against Japanese, Korean and Chinese denylist patterns too — all bypassed.

**Reachable at B3**, the boundary the whole control exists for: `AgentDefinitionPolicy:218`
(`instruction`, `outputFormat`), `:241` (`displayName`), `:315` (`focusAreas`), each gated
on `profile.enforcesCharset()`, which is `true` exactly for `REPOSITORY_SUPPLIED`. So the
attack is: open a PR adding `.github/agents/foo.md` to the repo under review, with an
injection whose keywords are broken up by an invisible character. It renders clean to a
human reviewer, passes both controls, and reaches the model as instructions.

This is the same severity as F1 and for the same reason, so I am rating it the same. It is
not a regression introduced by t18.2 — it was always there, and F1's narrowing simply
removed the *other* way in, leaving this one as the shortest path.

**Owner: backend.** The fix should not be "also exclude U+FFA0" — that repeats the mistake
one codepoint later. Two durable options, in preference order:

1. Subtract a category mask from whatever the ranges admit, so hygiene stops depending on
   anyone's memory of block contents — e.g. reject any admitted codepoint that is
   `Cf`/`Cc`/`Cn`/`Co`/`Zl`/`Zp`, plus the known invisible non-`Cf` fillers
   (`U+115F`, `U+1160`, `U+3164`, `U+FFA0`, `U+2800`).
2. Failing that, narrow range 6 to the sub-blocks actually wanted (`\uFF01-\uFF9F`,
   `\uFFE0-\uFFE6`) and ranges 3/7 to their assigned extents (`\u3041-\u309F`,
   `\uAC00-\uD7A3`).

Either way it needs the negative control ADR-0007 **D7** already mandates — a test that
asserts a *blank-rendering* character is rejected, not only that `U+202E` is.

## Reconciliation with the original t18

| Original | Status now |
|---|---|
| SEC-H1 (limits declared but dead) | **Closed.** All 5 constants live in `src/main` (4/4/5/2/3 refs) and each is named by ≥1 test. The liveness test carries its own non-vacuity control (`NO_SUCH_CONSTANT_ANYWHERE` must read 0, `MAX_UNTRUSTED_INSTRUCTION_SIZE` must read non-zero) — it can fail, so its passing means something. |
| SEC-H2 (provenance not carried) | **Closed.** `AgentSource` is carried as a type; `enforcesCharset()` differentiates `REPOSITORY_SUPPLIED` from `USER_SUPPLIED` at all three charset sites; no defaulting overload re-trusts a repository file. |
| SEC-M3 / SEC-M4 (masking wrapper) | **Closed by removal**, per D5. `McpServerSpec` holds headers unmasked *by design* and says so. No wrapper has been re-added. |
| Secret sink (D6) | **Verified.** Both `logback.xml` appenders and `logback-json.xml` carry both passes, nested `%replace(%replace(%msg){MASK_PATTERN}){HEADER_MASK_PATTERN}` — value-shape inner, name-based outer, i.e. the documented order. |
| SEC-M1/M5/M6, SEC-L1–L9 | Out of t18.2's scope, still open, none HIGH — they do not block this gate. |

## Dependency CVEs — clean, and provably non-vacuous

31 runtime coordinates, **0 vulnerabilities** (OSV coordinate-based batch query).
Per `cve-scan-non-vacuity-controls`, three known-vulnerable controls in the same batch:

| Control | Vulns returned |
|---|---|
| `log4j-core:2.14.1` | 7 |
| `jackson-databind:2.9.8` | 55 |
| `spring-web:5.3.20` | 6 |

The scan can detect. The zero is real.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/team/security/inbox.md` — the three re-run inputs
- `.github/modernize/rearchitecture/artifacts/t18-security.md` — my original findings, reconciled above
- `.github/modernize/rearchitecture/artifacts/t18.1-architect.md` — remediation design, D1–D7
- `.github/modernize/rearchitecture/artifacts/t18.2-backend.md` — remediation claims
- `.github/modernize/rearchitecture/artifacts/t18.2-backend-findings.md` — F1/F2/F3
- `docs/adr/0007-agent-definition-trust-model-and-secret-sink-boundary.md` — D1–D7 + Enforcement
- `.github/modernize/rearchitecture/clarification.md` — single Maven module, existing tests must pass
- `learnings/security/{trust-boundary-severity-calibration, dead-security-controls, cve-scan-non-vacuity-controls, masked-map-accessor-matrix}.md`

## Evidence Mapping

| Upstream claim | How I checked it | Result |
|---|---|---|
| `t18.2-backend-findings.md` F1 — narrowed range excludes bidi/zero-width | Ran the verbatim shipped regex against all 21 target codepoints | **Confirmed.** 0 leaks |
| `t18.2-backend.md` — Japanese typography retained | Same probe, positive cases (U+203B, dashes, quotes, ellipsis, Hangul) | **Confirmed.** No regression |
| `t18.2-backend.md` — 5 constants now live | Source-text ref count per symbol, `src/main` + `src/test` | **Confirmed** (4/4/5/2/3) |
| ADR-0007 D2 — single policy owner | Read all 3 `containsOnlyAllowedCharacters` call sites | **Confirmed**, all in `AgentDefinitionPolicy` |
| ADR-0007 D5 — no `toString()` masking in port DTOs | Read `McpServerSpec.java` | **Confirmed**, unmasked by design + documented |
| ADR-0007 D6 — both `%replace` passes, documented nesting | Decoded `logback.xml`/`logback-json.xml` via base64 | **Confirmed**, correct order |
| ADR-0007 D4 — violations "not suppressible by `--quiet`" | `grep quiet src/main/java` | **Vacuous** — no such flag exists (SEC-L11) |
| ADR-0007 D3 — `AgentConfig` has 13 elements | Backend reports 14 | **ADR is stale** — architect to correct |
| Coordinator — `ALLOWED_CHAR_RANGE` unguarded by liveness test | Read the test's 7-name enumeration + ref counts | **Confirmed, and worse:** `ALLOWED_MODEL_PREFIXES` too |
| Backend — "audit other block ranges" | Exhaustive sweep of all 65,536 BMP codepoints against the shipped regex | **Found SEC-H3 + SEC-M7** |

## Test Results

Read-only audit — no production code changed, so no build was run and none is claimed.
Verification was by executable probe against the verbatim shipped constant and the real
shipped denylist resource; probes live in `/tmp/t18rerun/` and are not part of the repo.
Every probe batch included controls that must fail, and they did (see the audit file).

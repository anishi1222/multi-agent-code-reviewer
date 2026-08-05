# t18 — Security Review (auth flows, secrets handling, input validation)

**Role:** security · **Type:** read-only audit · **Production code modified: none**

## Summary

Audited 175 main-source files across the 6 rearchitected layers. **2 HIGH, 6 MEDIUM, 9 LOW.**
No finding blocks the build; all 877 tests remain green (t15 baseline, unchanged by this task).

The headline result is not the count — it is *where* the weaknesses are. The rewrite's
token-handling and process-execution hygiene is genuinely strong (see
[Verified SAFE](#verified-safe), which is a long list and was checked, not assumed). The
weaknesses cluster in one place: **the path where untrusted content from the repository under
review becomes instructions to the LLM.** Both HIGH findings live there.

Two findings are **proven at runtime**, not inferred from reading code — see
[t18-security-secrets.md](./t18-security-secrets.md#runtime-proof).

## Deliverables

- [t18-security-auth.md](./t18-security-auth.md) — auth flows, default-deny proof, process execution
- [t18-security-secrets.md](./t18-security-secrets.md) — token lifetime, header masking (runtime proof), report redaction
- [t18-security-input-validation.md](./t18-security-input-validation.md) — untrusted-input handling, prompt injection, ReDoS, dependencies/CVE

## Trust-boundary model

Severity here is calibrated against trust boundaries, not against pattern names. This matters:
several patterns that *look* like classic vulnerabilities are not, because the input never
crosses a boundary.

| # | Boundary | Trusted? | Notes |
|---|---|---|---|
| B1 | User → CLI flags | **Trusted** | The user already runs the binary with their own privileges |
| B2 | Reviewed repository content → tool | **UNTRUSTED** | The tool's entire purpose is to ingest this |
| B3 | Agent/skill markdown → tool | **UNTRUSTED** | Default dirs are `./agents`, `./.github/agents` — *inside the reviewed repo* (`AgentPathConfig.java:11`) |
| B4 | LLM output → report file | **UNTRUSTED** | Influenced by B2 |
| B5 | MCP server responses → tool | Semi-trusted | HTTPS + host allowlist enforced |

**Consequence:** `--output`, `--local`, `--parallelism`, `--dialogue-rounds` are all B1. Their
lack of bounds is a robustness/UX issue, **not** a vulnerability — a user cannot escalate
privilege against themselves. I have deliberately *not* filed these as HIGH path-traversal
findings; doing so would be a false positive. They are recorded once as SEC-L9.

Conversely **B3 is the dangerous one**, and it is the boundary the codebase most under-defends.

## Findings

| ID | Sev | Boundary | Location | Issue | Owner |
|---|---|---|---|---|---|
| **SEC-H1** | **HIGH** | B3 | `domain/instruction/CustomInstructionSafetyValidator.java:24,25,26,58,108` | Size caps, line cap, charset allowlist and `ValidationResult` are **declared and never referenced**. Only the denylist executes. The class name promises validation it does not perform | backend + architect |
| **SEC-H2** | **HIGH** | B3 | `infrastructure/parsing/AgentConfigLoader.java:234-241` | Prompt-injection defence is **denylist-only**, and covers 7 of 12 `AgentConfig` fields | architect |
| **SEC-M1** | MED | B4 | `domain/report/ContentSanitizer.java:73-90` | Sanitizer pipeline has **zero** secret-redaction rules; AI output quoting repo source is written verbatim | backend |
| **SEC-M2** | MED | — | `shared/SensitiveHeaderMasking.java:101-188` | `forEach()`, `getOrDefault()`, `entrySet().getValue()` return **raw** values — *runtime-proven* | backend |
| **SEC-M3** | MED | — | `SensitiveHeaderMasking.java` + `ReviewSessionConfigFactory.java:56` | Any defensive copy (`Map.copyOf`, `new HashMap<>`) strips masking — *runtime-proven*; recurrence of the t13 learning | architect |
| **SEC-M4** | MED | — | `SensitiveHeaderMasking.java:81-99` | `MaskedToStringMap` masks `toString()` only; every accessor raw. **No production caller** → latent trap for the next caller | architect |
| **SEC-M5** | MED | B4 | `ContentSanitizer.java:33,45,69` | Greedy/lazy `DOTALL` regexes over unbounded untrusted LLM output → quadratic blowup | backend |
| **SEC-M6** | MED | — | `TokenInputReader.java:45`, `GhAuthTokenProvider.java:107` | Token becomes an unwipeable `String`, copied into ≥5 further fields; only buffers are wiped | backend |
| SEC-L1 | LOW | B2 | `LocalFileCandidateCollector.java:34-56` | No file-count or depth cap during walk (byte budget applies only later) | backend |
| SEC-L2 | LOW | B3 | `RubberDuckDialogueRunner.java:100-105` | `language` frontmatter is unvalidated and concatenated into a template key; exception fallback bounds impact | backend |
| SEC-L3 | LOW | — | `ReviewRequest.java:38`, `ReviewOptions.java:13`, `SkillOptions.java:15` | Records carrying `githubToken` lack a `toString()` override that `OrchestratorConfig.java:88-91` *does* have — inconsistent hardening | backend |
| SEC-L4 | LOW | B1 | `CopilotService.java:174-179` | `COPILOT_SDK_LOG_LEVEL` can raise SDK verbosity (interacts with SEC-M2/M3) | devops |
| SEC-L5 | LOW | — | `SensitiveHeaderMasking.java:63-67` | Text before the first space is preserved in cleartext | backend |
| SEC-L6 | LOW | — | `pom.xml:86-91` | SnakeYAML on `compile` scope but **never instantiated** — pure CVE/gadget surface for zero benefit | architect |
| SEC-L7 | LOW | B4 | `ContentSanitizer.java:104` | Single-pass HTML entity decode → double-encoding bypass | backend |
| SEC-L8 | LOW | — | `TokenHashUtils.java:33-35` | Unsalted SHA-256; no production caller; `MessageDigest.isEqual` unused repo-wide | architect |
| SEC-L9 | LOW | B1 | `ReviewOptionsParser.java:131,157,158,161,202` | `--output` / `--local` / `--parallelism` / `--dialogue-rounds` unbounded. **Robustness, not vulnerability** (see trust-boundary note) | backend |

## Verified SAFE

Recorded explicitly so reviewers can distinguish *audited-clean* from *not-audited*.

- **Default-deny proven, not assumed** — both token gates fail closed
  (`ReviewTargetResolver.java:43-53`, `SkillExecutionPreparation.java:70-79`); the
  unauthenticated local path is an *explicit* whitelist branch, not a fallthrough.
- **No command injection** — exactly one `ProcessBuilder` in all of `src/main`
  (`GhAuthTokenProvider.java:83`): literal args, no shell, path re-validated with a
  `realPath.equals(normalized)` TOCTOU check against a trusted-dir allowlist.
- **No YAML deserialization anywhere** — SnakeYAML is never instantiated; frontmatter is a
  hand-written regex parser. Gadget chains and billion-laughs are structurally impossible.
- **No template injection** — `PlaceholderUtils.java:22-26` is single-pass with
  `Matcher.quoteReplacement`. (Note: `project-profile.yaml:19` describing "Mustache 風
  `{{placeholder}}`" is **stale** — real syntax is `${key}` and no Mustache engine exists.)
- **Symlinks not followed** in file collection; explicit rejection + TOCTOU re-check.
- **Token never on the command line** — `CliParsing.java:130-142` rejects `--token <value>`,
  permitting only `--token -` (stdin, 256-byte cap).
- **Child-process env scrubbed** of `GITHUB_TOKEN`/`GH_TOKEN`/`GH_ENTERPRISE_TOKEN`.
- **MCP hardened** — HTTPS enforced, host allowlist, CRLF guards on header name *and* value.
- **Log injection defended** — `LogValueSanitizer` CRLF-neutralises MDC keys, values, message.
- **Report files 0600** via atomic move.
- **Repo hygiene** — `logs/`, `*.log`, `/reports/`, `application.yml` all gitignored; no secrets
  tracked; `logs/security-audit.log` contains **0** token-shaped strings.
- **CVE state clean** — all 5 pinned override coordinates return 0 OSV findings, validated with
  negative controls (see below).
- **No suppressed CVEs** — `osv-scanner.toml` contains no active ignores.

## Methodology notes

**Non-vacuity control.** A clean result from a scanner that scanned nothing is indistinguishable
from a genuinely clean result. Every CVE query therefore included known-vulnerable negative
controls, which returned **6** and **9** findings respectively — proving the scan was live.

**Tooling constraint honoured.** Per the coordinator's t12.1 note, any bytecode-inspecting tool
shading pre-Java-27 ASM fails *silently and partially* on this codebase. I therefore used no
bytecode SAST. The OSV path queries by Maven **coordinate**, not by scanning class files, so it
is structurally immune to that failure mode. Scanning the *override target* named in each
`<!-- Security: -->` comment (rather than only the resolved tree) follows the
`cve-pin-can-itself-be-vulnerable` learning.

**Runtime verification over reading.** The `defensive-copy-strips-security-wrapper` learning
warns that masking must be verified at runtime. I did so, with a canary constructed at runtime so
no secret-shaped literal exists in source. It found two leaks that source reading alone
under-rates. Method and output: [t18-security-secrets.md](./t18-security-secrets.md#runtime-proof).

**Not verified / stated as uncertain.** I did not decompile the Copilot SDK's `setHeaders`
internals, so SEC-M3's *live* exploitability depends on unconfirmed SDK behaviour.
`McpHttpServerConfig` has **no** `toString()` override (verified via `javap`), which bounds the
immediate risk. SEC-M3 is rated on the control being unreliable by construction — not on a
confirmed live sink. I also did not read `t16-architect.md` before starting; it landed
concurrently and I have since reconciled against it (see below).

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/clarification.md` — confirmed rewrite scope is structural; no auth/authz behaviour change was requested, so any auth *regression* would be out-of-contract.
- `.github/modernize/rearchitecture/artifacts/project-profile.yaml` — stack/entrypoint inventory; also the source of the stale Mustache claim corrected above.
- `.github/modernize/rearchitecture/artifacts/t3-pm.md` — behaviour IDs, used to check no security-relevant behaviour was silently dropped.
- `.github/modernize/rearchitecture/artifacts/t4-architect.md` — 6-layer model and port catalog; used to assign owning layer for each finding.
- `.github/modernize/rearchitecture/artifacts/t13-backend.md` + `t13.1-backend.md` — the masking-wrapper and MDC-deletion incidents; directly predicted SEC-M2/M3/M4.
- `.github/modernize/rearchitecture/artifacts/t15-backend.md` — CVE baseline and the 877-test green baseline I did not re-run.
- `.github/modernize/rearchitecture/team/security/inbox.md` — t12.1 tooling constraint (shaped my tool choice) and t16 ADR-0006 **D4**.

## Evidence Mapping

| Upstream source | → | This task's output / evidence |
|---|---|---|
| `t13-backend.md` — masking wrapper stripped by `Map.copyOf` | → | **SEC-M3**, reproduced at runtime; wrapper still stripped by both `Map.copyOf` and `new HashMap<>` |
| `learnings/backend/defensive-copy-strips-security-wrapper.md` | → | **SEC-M2/M3/M4**; motivated the full accessor matrix instead of spot-checking `toString()` |
| `learnings/backend/cve-pin-can-itself-be-vulnerable.md` | → | Methodology: scanned the coordinate in each `<!-- Security: -->` comment; all 5 clean |
| `learnings/backend/redacted-literals-compare-by-hash.md` | → | Canary built at runtime so the probe adds no secret-shaped literal to the repo |
| `t13.1-backend.md` G2 — MDC deleted, not migrated | → | Checked git history for a *lost* redaction capability; found none ever existed → **SEC-M1 is a pre-existing gap, not a rewrite regression**. This distinction changes its priority |
| `t16` ADR-0006 **D4** (displaced capability must return as a port) | → | **SEC-M1** is the D4-shaped question for security: redaction is cross-cutting and currently belongs to no layer or port |
| `t12.1` tooling constraint (ASM < Java 27 fails silently) | → | Rejected bytecode SAST; used coordinate-based OSV + source analysis |
| `t15-backend.md` CVE baseline | → | Re-verified independently; confirms t15's `3.1.4 → 3.1.5` fix holds |
| `t4-architect.md` layer model | → | Owner column: `domain` findings → backend; cross-layer contracts → architect |
| `AgentPathConfig.java:11` (`./.github/agents`) | → | Established **B3 is untrusted**, which is what elevates SEC-H1/H2 to HIGH |

## Test Results

Not applicable — read-only audit. No production code, test code, or configuration was modified.
The 877-test suite from t15 was **not** re-run and is **not** claimed as re-verified by this task.

# t21 — PM Feature-Parity Sign-off

## Verdict

**PASS — all 69/69 inventoried PM behaviors are accepted for the final layered CLI runtime.**

- **CRITICAL:** 0
- **HIGH:** 0
- **Behavior disposition:** 68 executable-covered PASS + 1 coordinator-approved
  MANUAL-TIER PASS (`AUTH-01`)
- **Evidence strength:** 49 DIRECT, 19 COVERED-PARTIAL, 1 MANUAL-TIER
- **Release recommendation:** PM feature-parity gate is signed off.

This verdict means that every behavior in `t3-pm.md` §5.1–5.8 is accounted for, every automatable
behavior has at least one passing test as required by the approved t5 strategy, the complete suite
passes on the final Java/JAR/native tree, and no architecture or security gate reports a blocking
finding. It does **not** silently relabel the 19 behaviors originally graded PARTIAL by t14 as
isolated direct assertions; their evidence strength remains visible in the matrix below.

## Acceptance Basis

| Gate | Final evidence | PM ruling |
|---|---|---|
| Behavior denominator | `t3-pm.md` §5.1–5.8 enumerates 69 unique IDs | **69 is authoritative** |
| Coverage | t14 baseline 37 DIRECT / 21 PARTIAL / 11 NONE; t14.1 closes 10 NONEs, accepts `AUTH-01` as manual-tier, and upgrades `INS-01/02` | **No uncovered automatable ID remains** |
| Coverage policy | t5 requires at least one passing test per behavior; only missing coverage is HIGH | **68 executable-covered + 1 accepted manual-tier satisfy the gate** |
| Final runtime | t20: Java 28 1,106 + 4, native path 1,106 + 1,106 + 4, and 5 JAR + 5 native CLI flows; all green | **PASS** |
| Layered architecture | t17: inward dependencies, port direction, SDK isolation, DI ownership, zero cycles, and non-vacuous rules | **PASS, 0 HIGH / 0 CRITICAL** |
| Security | t18: SEC-H1/H2/H3 closed; 31 runtime coordinates, 0 known vulnerabilities; bounded Unicode residual accepted LOW | **PASS, 0 HIGH / 0 CRITICAL** |
| Final-tree identity | t20 source/build-input digest `67e0f81ae4f2def1328e305177fb4fc08902d3c28d4af57aee476720107e789f` across both clean copies | **Evidence applies to one coherent final tree** |

### Evidence-grade legend

- **D — DIRECT:** t14 classified the behavior DIRECT and t20 reran the complete final suite.
- **D+ — DIRECT CLOSURE:** direct evidence was added after t14 by t14.1 or t34 and is included in
  t20's final suite.
- **C — COVERED-PARTIAL:** t14 found a passing test that exercises the behavior but not every exact
  observable guarantee in isolation. This meets t5's explicit coverage threshold and t20 reproduced
  the suite; the weaker assertion granularity is retained rather than obscured.
- **M — MANUAL-TIER:** the interactive external OAuth flow is not a unit-test candidate and was
  explicitly accepted by the coordinator.

## Category Reconciliation

| Category | IDs | D/D+ | C | M | PM PASS |
|---|---:|---:|---:|---:|---:|
| Agent loading & validation | 13 | 8 | 5 | 0 | 13 |
| Skill system | 8 | 7 | 1 | 0 | 8 |
| Custom instructions & safety | 5 | 5 | 0 | 0 | 5 |
| Review target collection | 9 | 8 | 1 | 0 | 9 |
| Orchestration | 10 | 5 | 5 | 0 | 10 |
| Authentication & SDK | 11 | 6 | 4 | 1 | 11 |
| Retry & circuit breaker | 4 | 3 | 1 | 0 | 4 |
| Output & reporting | 9 | 7 | 2 | 0 | 9 |
| **Total** | **69** | **49** | **19** | **1** | **69** |

## 69-Behavior Acceptance Matrix

### Agent loading and validation

| ID | Observable behavior / acceptance | Grade and final evidence | PM verdict |
|---|---|---|---|
| `AGT-01` | Discover agents from all configured directories | **C** — t14 §2; focused `AgentPathConfigTest` + `LoadAgentPortWiringTest`; t20 full suite | PASS |
| `AGT-02` | Reject invalid or over-length agent names with a diagnostic | **D** — t14 §2; t20 full suite | PASS |
| `AGT-03` | Admit only configured model prefixes and reject the rest | **D+** — t14 §2 plus t34's matched 30-boundary `AgentModelPrefixPolicyTest`; t20 full suite | PASS |
| `AGT-04` | Reject agent files over 64 KiB with byte-count context | **D** — t14 §2; t20 full suite | PASS |
| `AGT-05` | Reject agent definitions without YAML frontmatter | **D** — t14 §2; t20 full suite | PASS |
| `AGT-06` | Require `name`, `systemPrompt`, and `instruction` | **D** — t14 §2; t20 full suite | PASS |
| `AGT-07` | Warn, but continue loading, when recommended output sections are absent | **C** — t14 §2; focused `AgentConfigValidatorTest`; t20 full suite | PASS |
| `AGT-08` | Scan all agent fields for prompt injection and skip suspicious agents | **D** — t14 §2; t20 full suite | PASS |
| `AGT-09` | Exclude an agent whose `enabled` flag is false | **D** — t14 §2; t20 full suite | PASS |
| `AGT-10` | Enforce focus-area count and length limits | **D** — t14 §2; t20 full suite | PASS |
| `AGT-11` | Enforce dialogue-round range 0–10 | **C** — t14 §2; focused trust/schema policy tests; t20 full suite | PASS |
| `AGT-12` | Audit unknown frontmatter keys without rejecting the agent | **C** — t14 §2; focused `AgentDefinitionPolicyTest` / trust-boundary tests; t20 full suite | PASS |
| `AGT-13` | Report a warning when an agent name cannot be resolved | **C** — t14 §2; final load/selection suite in t20 | PASS |

### Skill system

| ID | Observable behavior / acceptance | Grade and final evidence | PM verdict |
|---|---|---|---|
| `SKL-01` | Discover valid `.github/skills/<name>/SKILL.md` files and skip logged parse failures | **C** — t14 §2; focused `SkillMarkdownParserTest`; t20 full suite | PASS |
| `SKL-02` | Parse frontmatter and use the body as prompt; warn/error on invalid shape | **D** — t14 §2; t20 full suite | PASS |
| `SKL-03` | Require non-empty skill ID and prompt | **D** — t14 §2; t20 full suite | PASS |
| `SKL-04` | Reject invocation with missing required parameters | **D** — t14 §2; t20 full suite | PASS |
| `SKL-05` | Reject parameter values above the configured limit | **D+** — t14.1 `SkillParameterSafetyContractTest`; t20 full suite | PASS |
| `SKL-06` | Reject prompt-injection patterns in skill parameters | **D+** — t14.1 `SkillParameterSafetyContractTest`; t20 full suite | PASS |
| `SKL-07` | Apply skill retry, circuit breaker, and timeout with specific failure messages | **D+** — t14.1 `SkillExecutionResilienceContractTest`; t20 full suite | PASS |
| `SKL-08` | Exclude skill symlinks that escape the configured root | **D** — t14 §2; t20 full suite | PASS |

### Custom instructions and safety

| ID | Observable behavior / acceptance | Grade and final evidence | PM verdict |
|---|---|---|---|
| `INS-01` | Reject prompt injection in English, Japanese, Korean, and Chinese | **D+** — t14.1 combines existing EN/JA with direct KO/ZH controls; t20 full suite | PASS |
| `INS-02` | Detect Greek/Cyrillic homoglyph bypasses after normalization | **D+** — t14.1 adds Cyrillic matched controls to existing Greek coverage; t20 full suite | PASS |
| `INS-03` | Strip control characters and apply NFKC so invisible input cannot bypass safety | **D+** — t14.1 `InstructionNormalizationDefenseTest`; t18 external Unicode ruling; t20 full suite | PASS |
| `INS-04` | Reject delimiter-injection markers such as system delimiters | **D** — t14 §2; t20 full suite | PASS |
| `INS-05` | Separate scalar frontmatter metadata and fall back to raw content when absent | **D** — t14 §2 plus focused `InstructionFrontmatterTest` / `FrontmatterParserTest`; t20 full suite | PASS |

### Review target collection

| ID | Observable behavior / acceptance | Grade and final evidence | PM verdict |
|---|---|---|---|
| `TGT-01` | Walk a local directory and error when it is missing | **D** — t14 §2; t20 full suite | PASS |
| `TGT-02` | Skip ignored directories such as `.git`, `node_modules`, and `target` | **D** — t14 §2; t20 full suite | PASS |
| `TGT-03` | Collect recognized source extensions and special filenames only | **C** — t14 §2; focused `LocalFileProviderTest`; t20 full suite | PASS |
| `TGT-04` | Silently exclude sensitive files such as `.env`, `.key`, and `.pem` | **D** — t14 §2; t20 full suite | PASS |
| `TGT-05` | Skip files above the 256 KiB default and log the decision | **D** — t14 §2; t20 full suite | PASS |
| `TGT-06` | Stop collection at the 2 MiB total default and warn | **D** — t14 §2; t20 full suite | PASS |
| `TGT-07` | Reject symlinks that traverse outside the review base | **D+** — t14.1 `LocalReviewSymlinkDefenseTest`; t20 full suite | PASS |
| `TGT-08` | Continue with `"(no source files found)"` when nothing matches | **D** — t14 §2; t20 full suite | PASS |
| `TGT-09` | Detect files changed during read and skip them | **D** — t14 §2; t20 full suite | PASS |

### Orchestration

| ID | Observable behavior / acceptance | Grade and final evidence | PM verdict |
|---|---|---|---|
| `ORC-01` | Let `--parallelism` override configured parallelism | **C** — t14 §2; focused `ReviewOptionsParserTest`; t20 full suite | PASS |
| `ORC-02` | Execute agents on named virtual threads | **C** — t14 §2; focused `AgentReviewExecutorTest`; t20 full suite | PASS |
| `ORC-03` | Convert a per-agent timeout into a failed result | **C** — t14 §2; executor failure/cancellation area covered in t20 | PASS |
| `ORC-04` | Cancel unfinished work when the orchestrator timeout elapses | **C** — t14 §2; orchestration/cancellation area covered in t20 | PASS |
| `ORC-05` | Queue agents when the concurrency semaphore is exhausted | **D+** — t14.1 `AgentReviewSemaphoreBehaviorTest`; t20 full suite | PASS |
| `ORC-06` | Run each agent for N passes and merge results | **D** — t14 §2; t20 full suite | PASS |
| `ORC-07` | Collect and log duration, wait, and outcome metrics | **D** — t14 §2; t20 full suite | PASS |
| `ORC-08` | Run two-model rubber-duck dialogue with a dynamic timeout | **C** — t14 §2; focused runner/executor rubber-duck tests; t20 full suite | PASS |
| `ORC-09` | Return a failed result rather than crash on agent execution failure | **D** — t14 §2; t20 full suite | PASS |
| `ORC-10` | Cancel gracefully on interruption | **D** — t14 §2; t20 full suite | PASS |

### Authentication and SDK

| ID | Observable behavior / acceptance | Grade and final evidence | PM verdict |
|---|---|---|---|
| `AUTH-01` | Pre-authenticate through interactive `gh auth login` OAuth device flow | **M** — external interactive flow; t14.1 records coordinator-approved MANUAL-TIER | PASS |
| `AUTH-02` | Resolve CLI paths from environment/PATH and guide when absent | **D** — t14 §2; isolated t20 CLI flows | PASS |
| `AUTH-03` | Retry transient client startup three times with exponential backoff | **D** — t14 direct `CopilotClientStarterRetryBoundTest`; t20 full suite | PASS |
| `AUTH-04` | Time out startup and suggest `review doctor` | **C** — t14 §2; SDK startup area covered in t20 | PASS |
| `AUTH-05` | Diagnose protocol/ping timeout with guidance | **D** — t14 §2; t20 full suite | PASS |
| `AUTH-06` | Probe health and attempt reinitialization when unhealthy | **C** — t14 §2; client health area covered in t20 | PASS |
| `AUTH-07` | Resolve token in CLI argument → environment → `gh auth token` order | **C** — t14 §2; focused `ResolveTokenUseCaseTest`; t20 full suite | PASS |
| `AUTH-08` | Read `--token -` from stdin and fall through when blank | **D** — t14 §2; t20 full suite | PASS |
| `AUTH-09` | Report every doctor diagnostic as success/failure | **D** — t14 §2; JAR/native `doctor --help` smoke plus full suite | PASS |
| `AUTH-10` | Log a redacted warning for the deprecated token API | **D+** — t14.1 `CopilotDeprecatedTokenWarningContractTest`; t20 full suite | PASS |
| `AUTH-11` | Emit security-audit events for authentication activity | **C** — t14 §2; security/audit logging area covered in t20 | PASS |

### Retry and circuit breaker

| ID | Observable behavior / acceptance | Grade and final evidence | PM verdict |
|---|---|---|---|
| `RTY-01` | Retry transient review failures with exponential backoff and logging | **C** — t14 §2; focused `RetryExecutorTest` / `ReviewRetryExecutorTest`; t20 full suite | PASS |
| `RTY-02` | Open and reset independent review, skill, and summary circuit breakers | **D** — t14 §2; t20 full suite | PASS |
| `RTY-03` | Retry only errors classified as transient | **D** — t14 direct `RetryPolicyConsolidationTest`; t20 full suite | PASS |
| `RTY-04` | Retry a failed skill at most once | **D+** — t14.1 `SkillExecutionResilienceContractTest`; t20 full suite | PASS |

### Output and reporting

| ID | Observable behavior / acceptance | Grade and final evidence | PM verdict |
|---|---|---|---|
| `OUT-01` | Produce report artifacts only as Markdown files | **C** — t14 §2; focused report-generation/filename tests; t20 full suite | PASS |
| `OUT-02` | Write one `{agent-name}-report.md` per agent for a single-pass run | **D** — t14 §2 plus focused `GenerateReportUseCaseTest`; t20 full suite | PASS |
| `OUT-03` | Write `{agent-name}-pass-{n}-report.md` for multi-pass runs | **D+** — t14.1 `ReportFilenameBehaviorContractTest`; focused rerun; t20 full suite | PASS |
| `OUT-04` | Create `executive_summary_{timestamp}.md` unless summary is disabled | **D** — t14 §2; t20 full suite | PASS |
| `OUT-05` | Use the fallback template when AI summary generation fails | **C** — t14 §2; focused `FallbackSummaryBuilderTest` / `SummaryGeneratorTest`; t20 full suite | PASS |
| `OUT-06` | Create reports under a timestamped `yyyy-MM-dd-HH-mm-ss/` directory | **D** — t14 §2; t20 full suite | PASS |
| `OUT-07` | Always print progress banners and completion summary to stdout | **D** — t14 §2; isolated JAR/native output probes in t20 | PASS |
| `OUT-08` | Route errors to stderr through the CLI output boundary | **D** — t14 §2; t20 full suite | PASS |
| `OUT-09` | Keep progress stdout and report-file output active together | **D+** — t14.1 `ReviewDualOutputBehaviorTest`; t20 full suite | PASS |

## Mandatory PM Carry-Forward Dispositions

### C2 — instruction scalar frontmatter does not narrow legacy behavior

**PASS.** `InstructionFrontmatterTest` proves scalar metadata extraction and raw-body fallback;
`FrontmatterParserTest` proves the top-level scalar parser behavior independently. The PM baseline
for `INS-05` requires metadata separation and no-frontmatter fallback; it does not require arbitrary
nested instruction metadata. The focused Java 28 rerun included both classes and passed.

### OUT-02 / OUT-03 — single-pass and multi-pass filenames

**PASS.** `GenerateReportUseCaseTest` proves the per-agent `security-report.md` path. In the same
focused run, `ReportFilenameBehaviorContractTest` proves the single-pass filename together with
`security-pass-1-report.md` and `security-pass-2-report.md`. This directly prevents either naming
mode from being inferred from the other.

## Ancillary Inventory Reconciliation

- `t3-pm.md` has an aggregate prose count of **74**, but its eight enumerated behavior groups total
  **69** and yield exactly 69 unique IDs. The enumerated matrix is the acceptance denominator.
- `t3-pm.md` says **30** templates, while its listed inventory contains **28** actual template files.
  t20 independently finds **28 embedded templates** in both final artifacts. This is documentation
  count drift, not a missing PM behavior or runtime template.
- The four commands and four documented exit codes remain represented in the final packaged CLI
  contract; t20 directly launches help, version, list, doctor help, and skill list flows in both JAR
  and native forms.

## Test Results

### Focused PM evidence rerun

- Command:
  `JAVA_HOME="$HOME/.sdkman/candidates/java/28.ea.9-open" PATH="$HOME/.sdkman/candidates/java/28.ea.9-open/bin:$PATH" ./mvnw -B -Dtest=InstructionFrontmatterTest,FrontmatterParserTest,GenerateReportUseCaseTest,ReportFilenameBehaviorContractTest,AgentPathConfigTest,LoadAgentPortWiringTest,AgentConfigValidatorTest,AgentLoadRejectionReportingTest,AgentDefinitionPolicyTest,AgentTrustContractBoundaryTest,AgentSchemaCoverageTest,SkillMarkdownParserTest,LocalFileProviderTest,ReviewOptionsParserTest,AgentReviewExecutorTest,RubberDuckDialogueRunnerTest,RubberDuckDialogueExecutorTest,ResolveTokenUseCaseTest,RetryExecutorTest,ReviewRetryExecutorTest,FallbackSummaryBuilderTest,SummaryGeneratorTest test`
- Return code: **0 — BUILD SUCCESS**
- Passed: **176**
- Failed: **0**
- Errors: **0**
- Skipped: **0**

### Authoritative final-tree runtime evidence

- Java 28: **1,106 Surefire + 4 Failsafe passed; 0 failed / 0 errors / 0 skipped**
- GraalVM 25: **1,106 JVM + 1,106 native-image + 4 Failsafe passed; 0 failed / 0 errors /
  0 skipped**
- Direct CLI smoke: **5 packaged-JAR + 5 native flows passed**
- Architecture analyzer: **365/365 production classes parsed**

## Findings and Residual Risk

- **CRITICAL:** 0
- **HIGH:** 0
- **Accepted LOW:** t18's bounded Unicode name-heuristic residual. The external Unicode 16 oracle
  found 0 of 4,174 default-ignorable code points admitted; a future JDK Unicode change is the
  documented re-review trigger.
- **INFO — evidence depth:** 19 IDs retain t14's PARTIAL assertion grade. They are not uncovered:
  each has passing behavioral-area coverage and therefore satisfies the explicit t5 gate, and the
  final Java/native suite is green. Future test-hardening may isolate their full observable
  guarantees, but this is not evidence of a parity defect.
- **INFO — AUTH-01:** interactive OAuth depends on the external `gh auth login` device flow and
  remains deliberately manual-tier. No automated result is being claimed for it.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/clarification.md` — fixed Java 28, CLI-only scope, regression
  posture, and native-image preservation contract.
- `.github/modernize/rearchitecture/team/pm/inbox.md` — mandatory C2 and OUT-02/OUT-03 PM checks.
- `.github/modernize/rearchitecture/artifacts/t3-pm.md` — authoritative behavior definitions,
  acceptance criteria, and 69-ID denominator.
- `.github/modernize/rearchitecture/artifacts/t5-teamlead-teststrategy.md` — binding behavior
  coverage threshold and severity rule.
- `.github/modernize/rearchitecture/artifacts/t14-tester.md` — original full regression and
  traceability context.
- `.github/modernize/rearchitecture/artifacts/t14-tester-traceability.md` — original per-ID
  DIRECT/PARTIAL/NONE grades.
- `.github/modernize/rearchitecture/artifacts/t14.1-tester.md` — executable closure run and accepted
  manual-tier handoff.
- `.github/modernize/rearchitecture/artifacts/t14.1-tester-traceability.md` — direct mapping for the
  ten former NONE IDs and two partial upgrades.
- `.github/modernize/rearchitecture/artifacts/t17-architect.md` — final layered-architecture
  certification.
- `.github/modernize/rearchitecture/artifacts/t18-security-gate.md` — final passed security ruling,
  dependency scan, and accepted LOW residual.
- `.github/modernize/rearchitecture/artifacts/t20-tester.md` — authoritative final-tree Java, JAR,
  native, architecture, and smoke evidence.
- `.github/modernize/rearchitecture/artifacts/t34-tester.md` — direct model-prefix boundary evidence
  for `AGT-03`.

## Evidence Mapping

| Upstream artifact / contract | This sign-off evidence |
|---|---|
| `t3-pm.md` §5.1–5.8 and §7 | 69 unique rows in `## 69-Behavior Acceptance Matrix`; category totals reconcile to 69 |
| `t5-teamlead-teststrategy.md` §Tier 2 behavior traceability | 68 automatable IDs have passing coverage; missing-coverage count is zero |
| `t14-tester-traceability.md` §§1–4 | D/C grades preserve the original 37 DIRECT / 21 PARTIAL / 11 NONE audit rather than rewriting history |
| `t14.1-tester-traceability.md` §§Former NONE / Partial Watchlist Upgrades | Ten D+ closures, `AUTH-01` manual-tier, and direct `INS-01/02` upgrades yield 49 D/D+ + 19 C + 1 M |
| `team/pm/inbox.md` C2 mandate | `INS-05` disposition and focused scalar-frontmatter/raw-fallback rerun |
| `team/pm/inbox.md` OUT-02/OUT-03 mandate | Single-pass and numbered multi-pass filenames proven together in the focused rerun |
| `t17-architect.md` §Certification Contract | PM accepts the responsibility split only after all eight architecture items pass with zero HIGH/CRITICAL |
| `t18-security-gate.md` §§The ruling / Finding reconciliation | No blocking security finding; named LOW Unicode residual carried explicitly |
| `t20-tester.md` §§Test Results / Final Runtime Verdict | Final coherent tree passes Java 28, packaged JAR, GraalVM native tests, architecture, and ten direct CLI flows |
| `t34-tester.md` §Behavioral Contract | `AGT-03` five-family allowlist is pinned across exact, suffix, case, shortened, embedded, and whitespace boundaries |

## PM Sign-off

The final layered architecture preserves the 69-behavior PM contract at the approved evidence
threshold. Architecture and security gates are clear, the exact final runtime passes JVM/JAR/native
validation, the two mandatory PM carry-forwards pass, and no HIGH or CRITICAL issue remains.
**Feature parity is signed off for release-gate completion.**

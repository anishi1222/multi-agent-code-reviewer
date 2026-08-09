# t20 — Final Three-Tier Runtime Validation

## Verdict

**PASS — 0 CRITICAL / 0 HIGH.**

The final shared source tree passes all three required runtime tiers from isolated clean copies:

1. OpenJDK 28 full Maven verification;
2. shaded packaged-JAR execution from fresh working directories; and
3. the exact unskipped GraalVM 25 native build, native test image, and native executable flows.

The source/build-input digest was identical in the shared tree and both isolated copies:
`67e0f81ae4f2def1328e305177fb4fc08902d3c28d4af57aee476720107e789f`.
No production source was changed by t20.

## Validation Boundary

- Java 28 copy: `/tmp/t20-java28-final.xL3TNM`
- GraalVM 25 copy: `/tmp/t20-final-tree.JYDXLy`
- `.git/` and pre-existing `target/` were excluded from both copies.
- OpenJDK: `28-ea+9-483`; `javac 28-ea`
- GraalVM/native-image: Oracle GraalVM `25.0.4`
- Maven wrapper: `3.9.14`
- Browser/API tier: **not applicable** — this is a CLI with no HTTP/RPC backend.
- Infrastructure-service tier: **not applicable** — no database, broker, or containerized
  dependency participates in these flows.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/clarification.md` — fixed Java 28, CLI-only scope,
  must-pass regression posture, and native-image preservation requirement.
- `.github/modernize/rearchitecture/team/tester/inbox.md` — exact Java/native activation,
  architecture non-vacuity requirements, and final t17/t32.1 handoffs.
- `.github/modernize/rearchitecture/artifacts/t17-architect.md` — layered responsibility
  certification, bytecode coverage, port direction, and CLI startup baseline.
- `.github/modernize/rearchitecture/artifacts/t18-security-gate.md` — passed security gate,
  closed HIGH findings, and accepted bounded LOW residual.
- `.github/modernize/rearchitecture/artifacts/t19-devops.md` — authoritative release/native
  commands, isolated five-flow CLI protocol, packaging contract, and earlier native baseline.
- `.github/modernize/rearchitecture/artifacts/t33-devops.md` — layered native metadata repair,
  exact-member reflection scope, and no-skip native handoff.
- `.github/modernize/rearchitecture/artifacts/t32.1-architect.md` — Rule 5c, bidirectional
  ADR/rule traceability, and exactly-once architecture controls.
- `.github/modernize/rearchitecture/artifacts/t34-tester.md` — model-prefix behavior closure
  and pre-final full-regression baseline.
- `.github/modernize/rearchitecture/artifacts/t14.1-tester.md` — authoritative 1,106
  Surefire plus 4 packaged-JAR Failsafe baseline.
- `.github/modernize/rearchitecture/artifacts/t14.2-backend.md` — final skill-resilience and
  deprecated-token-warning remediation covered by the 1,106-test suite.

## Evidence Mapping

| Upstream contract / section | Final t20 evidence |
|---|---|
| `clarification.md#Generic` | Exact Java 28 and GraalVM 25 clean commands pass; HTTP verification is explicitly N/A for the CLI |
| `team/tester/inbox.md#MANDATORY BUILD PRECONDITION` | Both commands use explicit `JAVA_HOME` and JDK-first `PATH`; neither native tests nor integration tests are skipped |
| `t17-architect.md#Certification Contract` | Final Java 28 run parses 365/365 production classes; current boundary rules, port controls, and cycle checks pass |
| `t18-security-gate.md#The ruling` | The already-passed security gate remains covered by the full 1,106-test regression; t20 introduces no production change |
| `t19-devops.md#Packaged JVM Artifact Evidence` | Final Java 28 JAR has the required main class, Java 28 manifest, 28 templates, and five isolated flows at exit 0 |
| `t19-devops.md#Native Verification Status` | The same exact native command passes 1,106 JVM tests, 1,106 native tests, 4 Failsafe tests, and five direct native flows |
| `t33-devops.md#Verification Details` | Both metadata copies parse, are byte-identical, contain 285 reflection entries and 28 resources, and have zero stale `dev.logicojp.reviewer.cli` references |
| `t32.1-architect.md#Rule 5c / ADR / D4` | Final architecture suite reports Rule 5c at 72/31/0/0, all negative-control fixtures load, and the full regression remains green |
| `t34-tester.md#Behavioral Contract` | The model-prefix suite remains part of the final 1,106 passing Surefire cases |
| `t14.1-tester.md` and `t14.2-backend.md#Test Results` | The 1,106 + 4 baseline is reproduced exactly on both final clean Maven paths |

## Test-Harness Portability Repair

The first exact GraalVM run correctly exposed four native-test-only failures:

- three architecture negative-control fixtures were addressed as classpath resources and were
  pruned from the native test image; and
- runtime reflection over `ReviewApp` returned no declared methods because `main` was not registered
  as reflective metadata for the test image.

Result: **1,102 passed / 4 failed**, native command return code 1.

These were test-environment defects, not production failures: the same production tree passed the
Java/JAR path, and the failed assertions inspected test artifacts through capabilities that native
image does not preserve unless separately registered. The repair changed only
`src/test/java/dev/logicojp/reviewer/architecture/LayerDependencyRulesTest.java`:

- Rule 0c now inspects `target/classes` with `java.lang.classfile`, matching the artifact under test.
- Permanent negative controls now read their bytecode from `target/test-classes`.
- Exact owner/target checks, non-vacuity checks, and all production boundary predicates remain intact.

The focused architecture suite passed **22/22**, then the unchanged exact native command passed all
tiers. `git diff --check` passes globally and for the changed test file.

## Test Results

### Tier 1 — Final OpenJDK 28 Clean Verification

- Command:
  `JAVA_HOME="$HOME/.sdkman/candidates/java/28.ea.9-open" PATH="$HOME/.sdkman/candidates/java/28.ea.9-open/bin:$PATH" ./mvnw -B clean verify`
- Return code: **0 — BUILD SUCCESS**
- Surefire XML: **1,106 passed / 0 failed / 0 errors / 0 skipped**
- Failsafe XML: **4 passed / 0 failed / 0 errors / 0 skipped**
- Maven time: **49.670 s**
- Architecture: **365/365** classes parsed; Rule 3a **352/0/0**; Rule 4a **35/0/0**;
  Rule 5c **72/0/0**; five layers and all sibling groups have zero cycles.

### Tier 2 — Final Packaged JAR

- Artifact: `target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar`
- SHA-256: `cb0217b72bbeab54d176ea34cbfab3b026d9c64fa5c46f8715f7d5a5cdf529c3`
- Manifest: `Main-Class: dev.logicojp.reviewer.ReviewApp`,
  `Java-Version: 28`, `Build-Jdk-Spec: 28`
- Embedded templates: **28**
- Isolated direct flows: **5 passed / 0 failed**

| Flow | Exit | Observable result |
|---|---:|---|
| `--help` | 0 | General usage and four commands |
| `--version` | 0 | `Multi-Agent Reviewer dev` |
| `list` | 0 | `No agents found.` |
| `doctor --help` | 0 | Doctor usage |
| `skill --list` | 0 | `Available Skills:` and empty inventory |

Every flow used a new temporary CWD, isolated `PATH`, no external CLI override, and no repository
fallback. No bootstrap, template, reflection-registration, or uncaught-exception marker appeared.

### Tier 3 — Fresh Exact GraalVM 25 Verification

- Command:
  `JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.4-graal" PATH="$HOME/.sdkman/candidates/java/25.0.4-graal/bin:$PATH" ./mvnw -B clean verify -Pnative -f pom-native.xml`
- Final return code: **0 — BUILD SUCCESS**
- JVM/Surefire XML: **1,106 passed / 0 failed / 0 errors / 0 skipped**
- Native test image XML: **1,106 passed / 0 failed / 0 errors / 0 skipped**
- Packaged-JAR/Failsafe XML: **4 passed / 0 failed / 0 errors / 0 skipped**
- Maven time: **2:32**
- `target/native-tests`: **54,914,432 bytes**,
  SHA-256 `f0089bede04538aaef29463d6b465d445119e0575972864a07a06ddf1d8dd90c`
- `target/review`: **42,550,936 bytes**,
  SHA-256 `1a0ae806181010be3360b8865804147771d79a8a56d63fb994ecbe1c8294fd01`
- Isolated direct native flows: **5 passed / 0 failed**, using the same flow table and forbidden
  markers as the packaged JAR.

## Smoke Test Verdict

- install_command: `n/a — Maven wrapper project`
- install_returncode: n/a
- build_command: `JAVA_HOME="$HOME/.sdkman/candidates/java/28.ea.9-open" PATH="$HOME/.sdkman/candidates/java/28.ea.9-open/bin:$PATH" ./mvnw -B clean verify`
- returncode: 0
- covers_all_modules: yes
- startup_http_status: n/a
- test_script_present: n/a
- test_returncode: 0

## Native Smoke Test Verdict

- build_command: `JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.4-graal" PATH="$HOME/.sdkman/candidates/java/25.0.4-graal/bin:$PATH" ./mvnw -B clean verify -Pnative -f pom-native.xml`
- returncode: 0
- covers_all_modules: yes
- native_test_image: 1,106 passed / 0 failed / 0 errors / 0 skipped
- packaged_jar_failsafe: 4 passed / 0 failed / 0 errors / 0 skipped
- native_cli_probes: 5 passed / 0 failed

## Findings

- CRITICAL: **0**
- HIGH: **0**
- The accepted LOW Unicode residual from t18 is unchanged and is not a runtime blocker.
- INFO: reachability metadata still contains older non-`cli` generated-definition names that are
  absent from the layered class tree. This is outside t33's stipulated stale-`cli` closure and has
  no observed runtime effect: both metadata copies parse, the exact native build passes, and all
  native tests and direct flows are green.

## Final Runtime Verdict

environment: PASS — exact OpenJDK 28, Maven 3.9.14, and GraalVM/native-image 25.0.4 activation verified
integration: PASS — Java 28 1,106 + 4 and native-path 1,106 + 1,106 + 4 all pass with zero skips
e2e: PASS — five isolated packaged-JAR flows and five isolated native-executable flows exit 0
overall: PASS — final-tree layered CLI is release-gate green with 0 HIGH / 0 CRITICAL

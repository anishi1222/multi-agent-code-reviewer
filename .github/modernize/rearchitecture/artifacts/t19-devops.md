# t19 — Independent Build and Packaged CLI Startup Verification

## Status

**PASS — clean independent re-pass after t33.** The Java 28 release build, shaded-JAR smoke,
exact unskipped GraalVM 25 native gate, native test image, packaged-JAR Failsafe suite, and
isolated native CLI startup probes all return 0. Final result: **0 HIGH / 0 CRITICAL**.

## Result Summary

- `pom.xml` now packages the root `templates/` tree and runs a four-case Failsafe smoke test
  against the shaded JAR after `package`.
- The smoke test launches from an isolated temporary directory and removes external CLI paths,
  preventing repository files or a locally installed Copilot CLI from hiding packaging defects.
- Real packaged startup exposed and fixed a Logback/Joran option-parsing failure. The two masking
  passes and their order remain intact; delimiter characters are represented with regex hex escapes.
- `pom.xml` and `pom-native.xml` now share `micronaut-parent:5.1.0` and the same BOM-managed
  Micronaut, Copilot SDK, Logback, Byte Buddy, Micronaut Test, and SnakeYAML versions.
- `.sdkmanrc` selects Java 28 for the default POM. GraalVM 25.0.4 activation is explicit in the
  runbook and Copilot instructions.
- CI now runs full `clean verify`, uploads Surefire and Failsafe reports, and no longer treats the
  native build as `continue-on-error`. Native startup smoke runs only after that required gate.
- The shipped command is `list`, not the stale `list-agents` name in the dispatch brief.
- After t33 replaced stale layered bean registrations and added exact-member reflection metadata,
  an independent clean re-pass confirmed **1,058 JVM + 1,058 native + 4 packaged-JAR tests**.

## Packaged JVM Artifact Evidence

| Evidence | Result |
|---|---|
| Artifact | `target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar` |
| SHA-256 | `bd6ff62f20b35f50833bcf9c1333fe049d7365eebb170008b8796a3431c3de5c` |
| Manifest entry point | `Main-Class: dev.logicojp.reviewer.ReviewApp` |
| Manifest toolchain | `Java-Version: 28`, `Build-Jdk-Spec: 28` |
| Embedded template entries | 28 files under `templates/` (29 archive entries including the directory marker) |
| Launch location | New temporary directory outside the repository |
| Fallback used | None — every probe used `java --enable-preview -jar <artifact>` |

Manual probes against the final artifact:

| Entry point | Exit | Observed contract |
|---|---:|---|
| `--help` | 0 | General usage and all four commands printed |
| `--version` | 0 | `Multi-Agent Reviewer dev` |
| `list` | 0 | `No agents found.` from isolated CWD |
| `doctor --help` | 0 | Doctor usage printed without performing diagnostics |
| `skill --list` with `COPILOT_SDK_LOG_LEVEL=warning` | 0 | Empty skill list printed; allowlisted production level accepted |

No probe emitted the prior `PatternSyntaxException`, LoggerContext bootstrap failure, or
`Template not found: summary-prompt.md` failure.

## Mechanical Ownership

`src/test/java/dev/logicojp/reviewer/PackagedCliSmokeIT.java` is selected by Failsafe during
`integration-test` and checks:

1. `--help`
2. `--version`
3. `list`
4. `doctor --help`

Each process uses the shaded JAR, a 30-second timeout, a temporary CWD, and an isolated `PATH`.
The test asserts exit 0, expected CLI output, and the absence of bootstrap/template exceptions.
The Micronaut parent's inherited `default` Failsafe execution is explicitly overridden so the
test runs once, not through a duplicate execution.

## Dependency and Toolchain Reconciliation

Both POMs resolve the same application/test dependency baseline:

| Component | Main | Native manifest |
|---|---:|---:|
| Micronaut parent | 5.1.0 | 5.1.0 |
| Micronaut core/inject | 5.1.10 | 5.1.10 |
| Micronaut Test | 5.1.0 | 5.1.0 |
| Copilot SDK | 1.0.8 | 1.0.8 |
| Logback classic/core | 1.5.37 | 1.5.37 |
| Byte Buddy | 1.18.10 | 1.18.10 |
| SnakeYAML | 2.4 | 2.4 |
| Java target | 28 | 25 |

Dead `micronaut.version` and native plugin-version overrides were removed. The native-only
annotation-processor override was also removed so the shared Micronaut parent owns a coherent
processor classpath. SnakeYAML remains a parent-managed configuration parser dependency; source
search confirmed no direct SnakeYAML deserialization API use, matching the upstream SEC-L6 ruling.

## Target Environment Evidence

- Default JVM: `sdk env` resolved
  `~/.sdkman/candidates/java/28.ea.9-open`; `java --version` reported OpenJDK `28-ea+9`.
- Native JVM: explicit `JAVA_HOME`/`PATH` resolved
  `~/.sdkman/candidates/java/25.0.4-graal`; both `java` and `native-image` reported `25.0.4`.
- Main build status: **READY**.
- Native toolchain status: **READY**.
- Native full-build status: **PASS** — exact unskipped gate returned 0 after t33.

## Native Verification Status

Required command:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.4-graal" \
PATH="$HOME/.sdkman/candidates/java/25.0.4-graal/bin:$PATH" \
./mvnw -B clean verify -Pnative -f pom-native.xml
```

Result: **exit 0 (`BUILD SUCCESS`)**. The build generated and executed the 54.15 MB
`target/native-tests` arm64 image, where all 1,058 tests passed, then generated the 42.52 MB
`target/review` arm64 executable. The packaged-JAR Failsafe suite also passed 4/4.

Fresh native artifact evidence:

| Evidence | Result |
|---|---|
| Artifact | `target/review` |
| Format | Mach-O 64-bit executable arm64 |
| SHA-256 | `b8068317858e60df887870d40ca9357a48ccc9efddbf8a1f65cd3fff0e340351` |
| Launch location | New temporary directory outside the repository |
| Fallback used | None — every probe directly executed `target/review` |
| Safe probes | `--help`, `--version`, `list`, `doctor --help`, `skill --list` |
| Probe result | 5 passed, 0 failed; every process exited 0 |

The two metadata copies are byte-identical, contain zero
`dev.logicojp.reviewer.cli` references, and retain only the exact members required by t33:
9 `ReviewSettings` accessors, 14 `AgentConfig` accessors, 8 `PromptBudgetConfig` accessors plus
its canonical constructor, and 3 `InstructionFrontmatter.Parsed` accessors.

## Test Results

### Java 28 release gate

- Command:
  `JAVA_HOME="$HOME/.sdkman/candidates/java/28.ea.9-open" PATH="$HOME/.sdkman/candidates/java/28.ea.9-open/bin:$PATH" ./mvnw -B clean verify`
- Return code: 0 (`BUILD SUCCESS`)
- Surefire: 1,058 passed, 0 failed, 0 errors, 0 skipped
- Failsafe packaged JAR: 4 passed, 0 failed, 0 errors, 0 skipped
- Manual final-JAR startup: 5 passed, 0 failed
- Total Maven time: 50.555 s
- Finished: `2026-08-07T11:58:47+09:00`

### GraalVM 25 required gate

- Command:
  `JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.4-graal" PATH="$HOME/.sdkman/candidates/java/25.0.4-graal/bin:$PATH" ./mvnw -B clean verify -Pnative -f pom-native.xml`
- Return code: 0 (`BUILD SUCCESS`)
- JVM phase: 1,058 passed, 0 failed, 0 errors, 0 skipped
- Native test image: 1,058 passed, 0 failed, 0 errors, 0 skipped
- Failsafe packaged JAR: 4 passed, 0 failed, 0 errors, 0 skipped
- Manual native startup: 5 passed, 0 failed
- Total Maven time: 2:30
- Finished: `2026-08-07T12:02:19+09:00`

### Configuration validation

- Both reachability metadata files parse and are byte-identical: passed
- Stale `dev.logicojp.reviewer.cli` metadata entries: 0
- Surefire XML `<testcase>` reconciliation: 1,058; failures/errors/skips: 0/0/0
- Failsafe XML `<testcase>` reconciliation: 4; failures/errors/skips: 0/0/0
- `git diff --check`: passed

## Smoke Test Verdict
- install_command: `n/a`
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
- native_test_image: 1,058 passed / 0 failed / 0 errors / 0 skipped
- packaged_jar_failsafe: 4 passed / 0 failed / 0 errors / 0 skipped
- native_cli_probes: 5 passed / 0 failed

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/team/devops/inbox.md` — authoritative Java 28 dispatch,
  artifact-entry-point requirements, native boundary, and SEC-L6 routing.
- `.github/modernize/rearchitecture/clarification.md` — immutable Java 28 / Micronaut 5 target.
- `.github/modernize/rearchitecture/artifacts/t14-tester.md` — packaged-JAR defect and unowned
  Tier-3 smoke finding.
- `.github/modernize/rearchitecture/artifacts/t14-tester-traceability.md` — runtime/auth behavior
  boundaries and external-interaction classification.
- `.github/modernize/rearchitecture/artifacts/t14-tester-retry-widening.md` — retry boundedness
  evidence retained by the full regression.
- `.github/modernize/rearchitecture/artifacts/t15-backend.md` — native annotation-processor failure,
  version drift, and dual-JDK evidence.
- `.github/modernize/rearchitecture/artifacts/t19-devops.md` (pre-re-pass revision) — prior
  Java 28 PASS, native 1,053/2/3 blocked baseline, artifact contracts, and exact rerun command.
- `.github/modernize/rearchitecture/artifacts/t33-devops.md` — repaired layered native metadata,
  clean upstream gate, exact member inventory, and explicit handoff requiring this independent re-pass.

## Evidence Mapping

- `team/devops/inbox.md#t19 dispatch brief` → final JAR manifest/hash, five packaged probes,
  Failsafe ownership, exact GraalVM attempt, and warning-level deployment guidance above.
- `clarification.md#Backend` → Java 28 default POM, `.sdkmanrc`, CI, and runbook alignment.
- `t14-tester.md#MEDIUM — mvn clean verify produces a non-executable jar; CLI smoke unowned`
  → shaded-JAR Failsafe gate plus manual `java -jar` evidence.
- `t14-tester-traceability.md#3.4 Not a defect` → deterministic startup smoke excludes
  interactive authentication while `doctor --help` verifies the packaged command surface safely.
- `t14-tester-retry-widening.md#Mandate` → Java 28 full regression retained and passed all
  retry/cancellation tests.
- `t15-backend.md#Issues found` → annotation-processor override removed, POM versions converged,
  GraalVM 25 compile/native packaging reached, and the new metadata blocker is isolated exactly.
- `t19-devops.md#Native Verification Status` (pre-re-pass revision) → replaced the recorded
  exit-1 native baseline with independently captured exit-0 build, test-image, artifact, and CLI evidence.
- `t33-devops.md#Downstream Handoff` → independently reran the exact unskipped command rather than
  inheriting t33's result; reproduced 1,058 JVM + 1,058 native + 4 packaged-JAR passes and directly
  launched all five safe native CLI entry points.

## Final Gate Verdict

**PASS — 0 HIGH, 0 CRITICAL.** The former t33 metadata blocker is closed and independently
reproduced without skip flags or fallback launch modes. t20 may consume this artifact as the
packaged JVM and native CLI release-gate evidence.

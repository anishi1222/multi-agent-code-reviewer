# t35 — Final User-Facing Documentation Synchronization

## Verdict

**PASS.** The five user-facing documents now describe the final Unreleased tree, its layered
architecture, supported runtime/toolchains, security boundaries, packaging paths, and verified
release state. English and Japanese current-state content is semantically paired, while every
historical release-note body remains byte-for-byte unchanged.

## Files Updated

- `README.md` — concise default guide and current release/runtime/architecture/security summary.
- `README_en.md` — detailed English guide.
- `README_ja.md` — semantically paired Japanese guide.
- `RELEASE_NOTES_en.md` — final English `Unreleased` entry.
- `RELEASE_NOTES_ja.md` — final Japanese `Unreleased` entry.

No dated release-note section was edited. The latest repository tag remains
`v2026.07.21-review-contract`; no release version was invented for this Unreleased work.

## Final State Documented

- Five named layers (`presentation`, `application`, `domain`, `infrastructure`, `shared`) plus the
  three-file layer-zero composition root.
- 201 production Java sources, 8 inbound port interfaces, and 15 outbound port interfaces.
- JDK 28 (`28.ea.9-open`) for the primary JVM build and Oracle GraalVM 25.0.4 for the separate
  Java 25 Native Image build.
- Micronaut parent 5.1.0, `copilot-sdk-java` 1.0.8, Maven wrapper 3.9.14, Jackson 3.1.5, and
  Jackson 2 BOM 2.22.1.
- Live standard-review pass key
  `reviewer.execution.concurrency.review-passes`, distinct per-pass reports, and no merge/checkpoint
  restoration.
- The current per-call session behavior: `--no-shared-session` is documented as a compatibility
  switch, not as a working session-reuse toggle.
- Typed agent-definition provenance and differential trust policy, Unicode/default-ignorable
  rejection, pinned provider-family validation, and redaction at both Logback sinks.
- Supported distributables are the shaded JAR and `target/review`; the repository has no
  `Dockerfile`.
- Both reachability-metadata copies must remain byte-identical and use exact-member registration.

## Documentation Integrity

- `README_en.md` and `README_ja.md` have positionally identical current heading depth and paired
  runtime, model, architecture, security, configuration, and build markers.
- The paired `Unreleased` sections have the same category structure and evidence markers.
- Historical release bodies beginning at `2026-07-21 (v2026.07.21-review-contract)` compare
  byte-identically with `HEAD`.
- Historical heading order remains unchanged independently in each file: 39 dated English headings
  and 38 dated Japanese headings. The pre-existing legacy count difference was preserved rather
  than rewriting history.
- All local Markdown links resolve; Markdown fences are balanced; both detailed READMEs contain two
  structurally balanced Mermaid diagrams.
- Current-state sections contain none of the retired runtime versions, configuration paths,
  architecture classes, or container claims covered by the stale-state scan.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/clarification.md` — scenario scope, constraints, and downstream
  usage baseline.
- `.github/modernize/rearchitecture/team/architect/inbox.md` — binding architecture corrections,
  final runtime notices, and shared-session caveat.
- `.github/modernize/rearchitecture/artifacts/t16-architect.md` — ADR-0006 layering decisions and
  documentation baseline.
- `.github/modernize/rearchitecture/artifacts/t17-architect.md` — current-tree architecture
  re-certification and responsibility split.
- `.github/modernize/rearchitecture/artifacts/t18-security-gate.md` — Unicode/model/security and
  dependency-CVE evidence.
- `.github/modernize/rearchitecture/artifacts/t22-teamlead.md` — final completeness/consistency gate.
- `.github/modernize/rearchitecture/artifacts/t22.2-tester.md` — corrected JVM, packaged-JAR, and
  native runtime verification.
- `.github/modernize/rearchitecture/artifacts/t22.3-pm.md` — final feature-parity evidence.
- `.github/modernize/rearchitecture/artifacts/t33-devops.md` — exact-member Native Image metadata
  repair and immutable v0.03 historical correction.

## Evidence Mapping

- `clarification.md#Backend` and `#Gaps & Defaults Applied` →
  runtime prerequisites, toolchain split, configuration, and CLI/package guidance in all three
  READMEs.
- `t16-architect.md#Decisions recorded in ADR 0006` →
  Ports & Adapters overview, ADR links, diagrams, and project-tree descriptions.
- `t17-architect.md#Certification Contract` and `#Current Responsibility Split` →
  five-layer direction, thin composition root, bytecode enforcement, source/port counts, and
  framework/SDK confinement claims.
- `t18-security-gate.md#2.2 Result` and `#Test Results` →
  Unicode/default-ignorable policy and positive-control wording.
- `t18-security-gate.md#Finding reconciliation` →
  31-runtime-coordinate / zero-known-CVE release statement.
- `t22-teamlead.md#Final Verdict` and `#Test Results` →
  final Unreleased readiness and zero-open-gate framing.
- `t22.2-tester.md#Test Results` and `#Native Smoke Test Verdict` →
  exact Java 28, GraalVM 25.0.4, shaded-JAR, template, native-image, and probe results.
- `t22.3-pm.md#69-Behavior Acceptance Matrix` and `#Test Results` →
  retained user-visible CLI, review, report, authentication, agent, and skill behavior.
- `t33-devops.md#Summary` and `#Verification Details` →
  mirrored exact-member reachability metadata and preservation of the v0.03 live-key correction.
- `team/architect/inbox.md` binding t31/t33/t35 notices →
  Logback sink boundary, current multi-pass wording, and explicit shared-session compatibility
  caveat.

## Test Results

### Full JVM Gate

- Command:
  `JAVA_HOME="$HOME/.sdkman/candidates/java/28.ea.9-open" PATH="$JAVA_HOME/bin:$PATH" ./mvnw -B clean verify`
- Return code: **0 — BUILD SUCCESS**
- Surefire: **1,107 passed / 0 failed / 0 errors / 0 skipped**
- Failsafe: **5 passed / 0 failed / 0 errors / 0 skipped**
- Shaded JAR: `--help` and `--version` both returned **0**.
- Packaged Markdown templates: **28**.

### Full Native Gate

- Command:
  `JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.4-graal" PATH="$JAVA_HOME/bin:$PATH" ./mvnw -B clean verify -Pnative -f pom-native.xml`
- Return code: **0 — BUILD SUCCESS**
- JVM/Surefire: **1,107 passed / 0 failed / 0 errors / 0 skipped**
- Native test image: **1,107 passed / 0 failed / 0 errors / 0 skipped**
- Failsafe: **5 passed / 0 failed / 0 errors / 0 skipped**
- `target/review`: `--help` and `--version` both returned **0**.
- Reachability metadata byte comparison: **identical**.

### Documentation and Fact Gates

- `git diff --check`: **PASS**.
- Local links: **0 broken** across 5 documents.
- Fence/Mermaid structure: **PASS** across 5 documents; 2 paired diagrams per detailed README.
- Paired README structure/semantic markers: **PASS**.
- Paired `Unreleased` structure/semantic markers: **PASS**.
- Historical release-note body and chronology comparison: **PASS**.
- Current-state stale-claim scan: **0 hits**.
- Direct source fact audit: **201** production Java files, **8** inbound interfaces,
  **15** outbound interfaces, **3** root Java files, latest tag
  `v2026.07.21-review-contract`, and **28** packaged Markdown templates.

This repository is a CLI application, so an HTTP endpoint verification gate is not applicable.

## Findings

- CRITICAL: **0**
- HIGH: **0**
- Documented caveat: the shared-session setting/CLI value is carried through configuration objects
  but the current adapter creates one SDK session per pass call. The docs deliberately make no
  session-reuse claim.


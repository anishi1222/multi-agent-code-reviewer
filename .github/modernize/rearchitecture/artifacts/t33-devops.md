# t33 — Layered Native-Image Metadata and Review-Passes Release-Note Repair

## Status

**PASS.** The exact unskipped GraalVM 25 native gate now completes with `BUILD SUCCESS`:
1,058 JVM tests, 1,058 native-image tests, and 4 packaged-JAR integration tests all pass.

## Summary

- Replaced the 18 stale `dev.logicojp.reviewer.cli.$…$Definition` registrations in each
  reachability metadata file with the generated definitions that exist in the layered
  `presentation`, `presentation.command`, `presentation.formatter`, and `presentation.parser`
  packages.
- Added explicit reflection metadata for only the members exercised dynamically:
  - `ResolveReviewSettingsPort$ReviewSettings`: 9 record accessors.
  - `AgentConfig`: 14 record accessors.
  - `PromptBudgetConfig`: 8 record accessors plus its canonical constructor.
  - `InstructionFrontmatter$Parsed`: `metadata()`, `body()`, and `hasFrontmatter()`.
- Kept both reachability metadata files byte-for-byte identical. No blanket
  `allDeclaredMethods`, `allDeclaredFields`, or all-class registration was introduced.
- Corrected the English and Japanese v0.03 release notes with an explicit historical
  correction: execution uses `reviewer.execution.concurrency.review-passes`; the formerly
  documented `reviewer.execution.review-passes` key changed only the banner and never
  controlled execution.

## Files Changed

- `src/main/resources/META-INF/native-image/reachability-metadata.json`
- `src/main/resources/META-INF/native-image/dev.logicojp/multi-agent-reviewer/reachability-metadata.json`
- `RELEASE_NOTES_en.md`
- `RELEASE_NOTES_ja.md`

## Scope Boundary

t33 changed only reachability metadata and release notes. It did **not** modify
`pom-native.xml` or repair its prior annotation-processor/configuration problem. The t19
changes already present in the shared worktree had made compilation and native-image
generation reachable; t33 closed the remaining reflection-metadata test gate on top of that
state.

## Verification Details

The first exact rerun after registering the five upstream-reported reflection areas reduced
the native failures from five to one: 1,057 passed and only reflective invocation of
`InstructionFrontmatter$Parsed.body()` failed. Adding that single accessor and rerunning the
same full command produced the final clean result below.

Static validation also confirmed:

- both JSON files parse and are structurally equal;
- zero `dev.logicojp.reviewer.cli` registrations remain;
- all 18 replacement bean-definition classes exist in generated `target/classes`;
- the release notes contain matching EN/JA correction language and both the retired and live
  keys for historical clarity;
- `git diff --check` passes for all four changed product files.

## Test Results

- Command:
  `JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.4-graal" PATH="$HOME/.sdkman/candidates/java/25.0.4-graal/bin:$PATH" ./mvnw -B clean verify -Pnative -f pom-native.xml`
- Return code: **0** (`BUILD SUCCESS`)
- JVM/Surefire: **1,058 passed, 0 failed, 0 errors, 0 skipped**
- Native test image: **1,058 passed, 0 failed, 0 errors, 0 skipped**
- Packaged-JAR/Failsafe: **4 passed, 0 failed, 0 errors, 0 skipped**
- Total Maven time: **2:24**
- Finished: `2026-08-07T11:51:14+09:00`

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/team/devops/inbox.md` — authoritative t33 remediation
  scope, five initial reflection failures, exact GraalVM command, and no-skip evidence rule.
- `.github/modernize/rearchitecture/clarification.md` — native-image preservation is a
  completion condition and the project exposes a CLI rather than an HTTP API.
- `.github/modernize/rearchitecture/artifacts/t19-devops.md` — failing native baseline,
  toolchain activation, failure totals, and the boundary between t19 build repairs and t33
  metadata ownership.
- `.github/modernize/rearchitecture/artifacts/t28-backend.md` — live review-pass key,
  retired banner-only key, and the release-note drift handoff.

## Evidence Mapping

- `team/devops/inbox.md#t33 remediation brief` → both metadata copies now contain exact
  layered bean definitions and member-level reflection entries; the prescribed unskipped
  native command returns 0.
- `clarification.md#Generic success definition` → GraalVM native-image behavior is preserved
  by a complete native build and native execution of all 1,058 tests.
- `t19-devops.md#Native Verification Status` → the 1,053/2/3 failing baseline becomes
  1,058/0/0 after the member registrations above; t19's POM changes remain untouched.
- `t28-backend.md#Config contract` and `#Handoff / flagged, not fixed` → EN/JA notes identify
  `reviewer.execution.concurrency.review-passes` as the sole execution key and explicitly
  explain why the former key was incorrect.

## Downstream Handoff

t19 can now perform its independent final clean re-pass against the same exact native command.
There are no remaining t33 blockers.

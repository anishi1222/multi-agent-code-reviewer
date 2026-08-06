# t7 — Target Environment Preparation

## Target Environment Preparation

**Status: READY** (with scope clarification — see §1)

---

## §1 Task Title vs Actual Build Reality

The task was titled "Prepare target environment and verify GraalVM 26 EA toolchain."
`project-profile.yaml` recorded `Java 26 (GraalVM 26 EA)` as the target version — this was accurate
at plan-generation time. During the 519 commits of prior execution phases, the build files evolved:

| File | Property | Value |
|---|---|---|
| `pom.xml` | `java.version` | **27** |
| `pom-native.xml` | `release.version` | **25** |
| `toolchains-template.xml` | toolchain versions | 27 (vendor: graalvm), 25 (vendor: graalvm) |

**No GraalVM 26 EA toolchain is needed or referenced by any current build file.**  
This section documents the _actual_ toolchain state. GraalVM 27 EA would be ideal for `pom.xml`,
but is not yet available via SDKMAN; OpenJDK 27-ea+32 is used for the main build (verified working).

---

## §2 JDK Matrix

| JDK | SDKMAN Identifier | Installed | `native-image` | Used by |
|---|---|---|---|---|
| Oracle GraalVM 25.0.4+7.1 | `25.0.4-graal` | ✅ (active/current) | ✅ `25.0.4` | `pom-native.xml` (-Pnative) |
| Oracle GraalVM 25.2.4+7.1 | `25.2.4-graal` | ✅ | ✅ | alternative for pom-native |
| OpenJDK 27-ea+32 | `27.ea.32-open` | ✅ | ❌ (not GraalVM) | `pom.xml` (main build + shade) |
| Oracle JDK 26.0.2 | `26.0.2-oracle` | ✅ | ❌ | unused |
| GraalVM 27 EA | n/a | ❌ not in SDKMAN | n/a | preferred for pom.xml — not available |

### Verified JDK Outputs

```
# Java 27 EA (OpenJDK)
openjdk version "27-ea" 2026-09-15
OpenJDK Runtime Environment (build 27-ea+32-2315)

# GraalVM 25 (active)
java version "25.0.4" 2026-07-21 LTS
Java(TM) SE Runtime Environment Oracle GraalVM 25.0.4+7.1 (build 25.0.4+7-LTS-jvmci-b01)

# native-image (GraalVM 25)
native-image 25.0.4 2026-07-21
GraalVM Runtime Environment Oracle GraalVM 25.0.4+7.1 (build 25.0.4+7-LTS-jvmci-b01)
Substrate VM Oracle GraalVM 25.0.4+7.1 (build 25.0.4+7-LTS, serial gc, compressed references)
```

---

## §3 Toolchains Configuration

Created `~/.m2/toolchains.xml` with both JDKs:

```xml
<!-- version=27 / vendor=openjdk  → OpenJDK 27-ea+32 → for pom.xml -->
<jdkHome>/Users/logico_jp/.sdkman/candidates/java/27.ea.32-open</jdkHome>

<!-- version=25 / vendor=graalvm  → Oracle GraalVM 25.0.4 → for pom-native.xml -->
<jdkHome>/Users/logico_jp/.sdkman/candidates/java/25.0.4-graal</jdkHome>

<!-- version=25 / vendor=graalvm-25.2 → Oracle GraalVM 25.2.4 → alternate -->
<jdkHome>/Users/logico_jp/.sdkman/candidates/java/25.2.4-graal</jdkHome>
```

> Note: `pom.xml` and `pom-native.xml` do **not** invoke the `maven-toolchains-plugin` (confirmed: zero references
> to `maven-toolchains-plugin` or `toolchain:toolchain` in both POMs). The toolchains.xml is provided for
> manual use and future CI configuration; builds currently select the JDK via `JAVA_HOME` env var.

---

## §4 pom-native.xml Fix Applied

**Problem**: `pom-native.xml` (inherits `micronaut-parent:5.0.2`) was missing the
`<logback.version>1.5.37</logback.version>` BOM override property. `pom.xml`
(inherits `micronaut-parent:5.1.2`) already had it. Without the property, the Micronaut 5.0.2
BOM resolved `logback-classic:1.5.37` with its transitive dep on `logback-core:1.5.32`,
creating a convergence conflict with the explicitly declared `logback-core:1.5.37`.

**Fix**: Added to `pom-native.xml` `<properties>`:
```xml
<logback.version>1.5.37</logback.version>
```

**Commit**: `f63a79c` — "fix(build): add logback.version=1.5.37 to pom-native.xml to resolve dependency convergence"

---

## §5 Build Verification Results

### Main Build (`pom.xml`, Java 27)

```
JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open ./mvnw -B clean compile test-compile -f pom.xml
```

| Check | Result |
|---|---|
| `RequireJavaVersion` enforcer | PASSED |
| `DependencyConvergence` enforcer | PASSED |
| Compile (157 source files, `--release 27 --enable-preview`) | ✅ BUILD SUCCESS |
| test-compile | ✅ BUILD SUCCESS |

**Deprecation warning observed** (non-blocking):
```
SkillExecutionCoordinator.java:[41,12] [removal] initializeOrThrow(String) in CopilotService 
has been deprecated and marked for removal
```
→ Notify backend: `CopilotService.initializeOrThrow` deprecation should be addressed in T009/infra-copilot.

### Native Build (`pom-native.xml`, GraalVM 25)

```
JAVA_HOME=~/.sdkman/candidates/java/25.0.4-graal ./mvnw -B compile -f pom-native.xml
```

| Check | Result |
|---|---|
| All enforcer rules | PASSED (after logback.version fix) |
| Compile (157 source files, `--release 25`) | ✅ BUILD SUCCESS |
| `native-image` binary accessible | ✅ `25.0.4` |

Full native-image packaging (`-Pnative`) not executed — this task verifies toolchain readiness only.
Native image packaging should be run as part of T014 (build-verify) after source migration completes.

---

## §6 Activation Commands for Downstream Tasks

```bash
# For pom.xml (main build, shade JAR, unit tests):
export JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open
export PATH="$JAVA_HOME/bin:$PATH"
# or: sdk use java 27.ea.32-open

# Command:
./mvnw -B clean verify -f pom.xml

# For pom-native.xml (GraalVM native-image):
export JAVA_HOME=~/.sdkman/candidates/java/25.0.4-graal
export PATH="$JAVA_HOME/bin:$PATH"
# or: sdk use java 25.0.4-graal

# Command:
./mvnw -B clean verify -Pnative -f pom-native.xml

# Verify active toolchain resolves correctly:
java -version           # should show expected version
which native-image      # should resolve for GraalVM 25 only
native-image --version  # GraalVM 25 only
```

---

## §7 Operational Prerequisites — Status

| Prerequisite | Status | Notes |
|---|---|---|
| JDK 27 for main build | ✅ OpenJDK 27-ea+32 | GraalVM 27 EA not in SDKMAN — will need manual install when available |
| JDK 25 GraalVM for native build | ✅ Oracle GraalVM 25.0.4 | native-image binary present |
| `native-image` registered | ✅ `25.0.4` | |
| `~/.m2/toolchains.xml` | ✅ created | both JDKs registered |
| Maven wrapper | ✅ `./mvnw` present | |
| `pom.xml` convergence | ✅ | already correct |
| `pom-native.xml` convergence | ✅ fixed | commit f63a79c |
| GitHub Copilot CLI | ⚠️ required at runtime | not verified here; needed for T016 (smoke-test) |
| `GITHUB_TOKEN` / Copilot auth | ⚠️ required at runtime | not verified here; needed for T016 |

---

## §8 Downstream Implications

- **T013/T014 (build-verify, test-migration)**: Use activation commands from §6. Main build requires
  `JAVA_HOME` pointed at `27.ea.32-open`. Do **not** run `./mvnw` with the current default (GraalVM 25)
  for `pom.xml` — `--release 27` compilation requires Java 27+ JDK.
- **T014 native verification**: Run with `JAVA_HOME=25.0.4-graal` and `-f pom-native.xml -Pnative`.
  Expect long compile time (~5 min) for native-image.
- **T016 (smoke-test)**: Requires GitHub Copilot CLI (`gh extension install github/gh-copilot` or
  equivalent) and active authentication. Document if blocked.
- **CI/CD**: If a CI pipeline is added in a future phase, pin exact SDKMAN identifiers or
  Docker image versions (`ghcr.io/graalvm/graalvm-community:25` for native, `openjdk:27-ea` for main).
  Never use floating tags.

---

## Upstream Artifacts Consumed

- `clarification.md` — confirmed Java 26 EA was original target; pom.xml has evolved to 27
- `artifacts/project-profile.yaml` — Java 26 EA baseline; confirmed build targets via pom.xml audit
- `t1-teamlead.md` — constitution §7.2 (build preservation: shade + native-image + AOT must continue)
- `t5-teamlead-plan.md` — T014 (build-verify) and T016 (smoke-test) task definitions

## Evidence Mapping

- `project-profile.yaml#assessment.transformations[1].toStackVersion` ("26 (GraalVM 26 EA)") →
  Superseded: pom.xml java.version=27 + pom-native.xml release.version=25 (§1 discrepancy documentation)
- `t1-teamlead.md#§7.2` (build preservation: shade + native-image) →
  §5 build verification (compile SUCCESS for both pom.xml/pom-native.xml)
- `t5-teamlead-plan.md#T014` (build-verify) →
  §6 activation commands (exact env vars for downstream T014 execution)
- `t1-teamlead.md#§10 devops directive` (verify build pipeline: shade, native-image, AOT) →
  §5 (main compile ✅, native compile ✅, AOT via Micronaut annotation processor verified via compile step)

## Test Results

- No unit tests executed in this task (environment prep task, not implementation)
- Build commands verified: `mvnw compile` (both pom files) — exit code 0
- Tests scheduled for T015 (regression)

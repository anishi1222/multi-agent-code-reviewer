# Runtime Validation Report — t14 (tester)

Produced under the `runtime-validation` skill for the Hardening phase of the Ports & Adapters
rewrite. Scope: Tier 2 regression + runtime startup verification of the CLI application.

## 1. Environment capability matrix

Probed 2026-08-05T08:11:41Z.

| Capability | Status | Detail |
|---|---|---|
| Java | **AVAILABLE** | `27.ea.32-open` via SDKMAN (required — see §2) |
| Node.js | **AVAILABLE** | v26.5.1 |
| Docker | **UNAVAILABLE** | CLI present, daemon not responding |
| Playwright / browser | **N/A by design** | CLI application, no HTTP server and no HTML surface |
| Infrastructure tier (DB/broker) | **N/A by design** | No database or message broker in the profile; `t5` specifies no Testcontainers |

Docker's absence is **not a capability downgrade** for this project. The application is a CLI tool
with no datastore or broker dependency, and the binding test strategy explicitly does not use
Testcontainers. Recording it as a gap would misrepresent the risk. The same reasoning applies to
the browser tier: there is no web surface to drive.

## 2. Build precondition (mandatory)

The default active JDK on this machine is GraalVM 25.0.4, which **cannot** compile `pom.xml` — it
requires `--release 27`. Neither `pom.xml` nor `pom-native.xml` configures
`maven-toolchains-plugin`, so `JAVA_HOME` must be set explicitly per invocation:

```bash
JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open ./mvnw -B clean verify
```

Omitting this produces a compilation failure that reads like a source defect but is purely an
environment-selection problem. (Consistent with `devops/dual-jdk-build-activation`.)

The GraalVM native build (`-Pnative -f pom-native.xml`, JDK 25.0.4-graal) is **t19's scope**, not
validated here.

## 3. Regression tier

```
command:   JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open ./mvnw -B clean verify
exit_code: 0
result:    BUILD SUCCESS
tests:     937 run / 0 failures / 0 errors / 0 skipped
classes:   325
log:       /tmp/t14-full-regression.log
```

Baseline reconciliation: 892 (t13.1) + 45 (added by t14) = 937 exactly. No pre-existing test
regressed; none were silently dropped or skipped.

## 4. Architecture-rule tier (executed as part of `verify`)

The hand-rolled `LayerDependencyRulesTest` (JDK-native `java.lang.classfile`, JEP 484) reported:

```
[arch] Rule 0: parsed 333/333 classes
[arch] Rule 1 (domain purity)                    67 classes,   0 violator(s),  0 exempt
[arch] Rule 2 (shared purity)                    31 classes,   0 violator(s),  0 exempt
[arch] Rule 3 (presentation is a leaf)          264 classes,   2 violator(s),  2 exempt
[arch] Rule 4 (infrastructure -> application.port only) 113 classes, 3 violator(s), 3 exempt
[arch] Rule 5b (presentation ⊥ infrastructure)   69 classes,   0 violator(s),  0 exempt
[arch] Rule 6a: 5 layers inspected, 0 cycle(s)
[arch] Rule 6b: 0 cycle(s) across all sibling sub-packages
[arch] Rule 6 scope: 5 layer(s) cover every package under dev.logicojp.reviewer
```

Identical to the `t13-backend.md` baseline → **no architecture regression**.

`Rule 0: parsed 333/333 classes` is the line that matters most here. It is the explicit
counter-measure to the ArchUnit false-green problem recorded in
`backend/archunit-java27-bytecode-ceiling`: ArchUnit's shaded ASM tops out at class-file major 69
(Java 25) while this project emits major 71 (Java 27), so it silently imported a partial class set
and reported green. Any future bytecode-inspecting tool added to this build must print its parsed
count and be checked against the total, or it cannot be trusted.

## 5. Startup verification

Tier 3 CLI smoke was assigned to "architect (T016)" by `t5-teamlead-teststrategy.md`, but `t16`
became an ADR/documentation task that states "Docs-only task. No source or configuration files were
touched." Startup was therefore **unowned**. I verified it directly rather than leave the phase
with no evidence that the application runs.

```
command:   java -cp target/classes:<runtime-cp> dev.logicojp.reviewer.ReviewApp --version
exit_code: 0
stdout:    Multi-Agent Reviewer dev
```

Observed during startup:
- Micronaut `DefaultEnvironment` established active environments `[cli]`
- `SECURITY_AUDIT [authentication:copilot.start] - Copilot client started`
- `CopilotService - Copilot client initialized`
- clean shutdown: client stopped, matching `copilot.stop` audit line
- correlation-ID logging format `[exec:] [:]` present on every line
  (confirms `backend/correlation-logging-port` is live at runtime, not just in tests)

**Startup: PASS.**

### 5.1 MEDIUM — the packaged jar is not executable

```
java -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar --version
→ no main manifest attribute
```

Root cause: the `maven-shade-plugin` execution `default-shade` (`pom.xml` L242–265) declares
**configuration only — no `<phase>`, no `<goals>`**, so it never binds to the lifecycle and never
runs. The build log contains zero shade output, confirming this. The `<mainClass>` at L320 belongs
to the **native-image profile**, not to shade.

The application is healthy; only its packaging is. If the intended distribution channel is the
GraalVM native image (t19), this is arguably by design and should simply be documented. If a
runnable fat-jar is expected — the `exec.mainClass` property at L25 and the shade block's presence
both suggest someone intended one — it is broken today.

Routed to coordinator: needs an owner, and needs Tier 3 smoke to have an owner going forward.

## 6. Tier verdicts

```
environment: docker: UNAVAILABLE — daemon not responding; node: AVAILABLE v26.5.1; java: 27.ea.32-open; playwright: N/A (CLI app, no HTML surface); infra-tier: N/A by design; browser-tier: N/A by design
startup:     PASS — exit_code: 0, "Multi-Agent Reviewer dev"; clean Micronaut + Copilot lifecycle; NOTE: via classpath, packaged jar lacks Main-Class (MEDIUM)
integration: PASS — exit_code: 0, 937 passed / 0 failed / 0 errors / 0 skipped, 325 classes
architecture: PASS — exit_code: 0, 333/333 classes parsed, all rules at baseline
e2e:         N/A by design — CLI application, no browser surface
overall:     PASS (1 HIGH finding: 11 uncovered behavior IDs; 1 MEDIUM: non-executable jar + unowned CLI smoke; 1 LOW: provenance comment drift)
```

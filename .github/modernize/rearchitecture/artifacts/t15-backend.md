# t15 — CVE Scan & Remediation (backend)

## Summary

Scanned both dependency manifests for CVEs. **1 finding, fixed, verified.** Build stays green at the 877-test baseline.

The finding is notable: an existing *security override* in both POMs pinned `tools.jackson.core:jackson-databind` from "vulnerable 3.1.3" to **3.1.4 — which is itself still inside the advisory's affected range**. The advisory (`CVE-2026-59889`) is fixed in **3.1.5**. Someone correctly spotted a vulnerability and pinned forward to a version that had not been patched yet.

Both scanners reported **clean** on the resolved dependency tree, and that result was correct — `tools.jackson.core:*` never resolves under `micronaut.runtime=none`. The finding only surfaced by scanning the **BOM-managed set and the stated override targets**, which is why the scan was deliberately widened past the resolved tree.

## Deliverables

- [cve-fix-summary.md](./cve-fix-summary.md) — full findings, fix rationale, scan coverage, non-vacuity controls
- [cve-report-1.json](./cve-report-1.json) — pre-fix scan (1 finding)
- [cve-report-2.json](./cve-report-2.json) — post-fix re-scan (empty)
- [final-cve-report.json](./final-cve-report.json) — final state (empty)

## Changes made

| File | Change |
|---|---|
| `pom.xml` | `<jackson.version>` 3.1.4 → **3.1.5**; comment corrected to name the actual CVE and record that *both* 3.1.3 and 3.1.4 are affected |
| `pom-native.xml` | identical change |

No source code changed. Scope was limited to dependency manifests, per the task.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/clarification.md` — confirmed the rewrite's scope and that dependency hygiene is in the Hardening phase.
- `.github/modernize/rearchitecture/artifacts/project-profile.yaml` — confirmed Maven + dual-manifest layout, establishing that **two** manifests must be scanned and fixed in step, not one.
- `.github/modernize/rearchitecture/artifacts/t7-devops.md` — §6 supplied the dual-JDK `JAVA_HOME` activation commands used for every build here; §4 supplied the `logback.version` convergence precedent that told me a version bump is the *specific* thing likely to trip `DependencyConvergence` in this project, so I checked for it explicitly.
- `.github/modernize/rearchitecture/artifacts/t13-backend.md` — supplied the 877-test green baseline that my post-fix build had to reproduce exactly.
- `.github/modernize/rearchitecture/decisions.md` — supplied the binding constraint that ruled out bytecode-level scanners for this task (see Evidence Mapping).

## Evidence Mapping

| Upstream artifact + section | How it shaped this task's output / evidence |
|---|---|
| `decisions.md` → ASM/Java-27 silent-degradation rule, *naming t15 explicitly* | Ruled out bytecode-level CVE scanners. Chose manifest/coordinate scanning, which is structurally immune to class-file version rejection. Recorded in `cve-fix-summary.md` § "Why a bytecode-level scanner was deliberately not used". |
| `decisions.md` → "verify in source, not by report" practice | Refused to accept the primary tool's clean result at face value. Added an independent OSV.dev cross-check **and** two known-vulnerable negative controls (6 and 9 findings observed). This is the direct cause of the finding: the widened scope it motivated is where the CVE actually was. |
| `t7-devops.md` §4 → `logback.version` convergence fix precedent | Predicted `DependencyConvergence` as the failure mode for a version bump; verified explicitly in both build logs → **passed** on both. |
| `t7-devops.md` §6 → dual-JDK activation | Used `27.ea.32-open` for `pom.xml` and `25.0.4-graal` for `pom-native.xml`; without this the default GraalVM 25 cannot compile `--release 27`. |
| `t13-backend.md` → 877-test baseline | Post-fix build reproduced **877 tests, 0 failures** — proving no regression from the bump. |
| `project-profile.yaml` → dual-manifest layout | Applied the fix to **both** manifests and verified propagation in each effective POM separately. |

## Test Results

- Command: `JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open ./mvnw -B clean verify -f pom.xml`
- **Passed: 877**
- **Failed: 0**
- **Errors: 0**
- **Skipped: 0**
- Result: **BUILD SUCCESS** — matches the t13 baseline exactly (877), no regression.
- `DependencyConvergence` enforcer: **passed** (the main risk of this change).
- Architecture layer-boundary enforcement: **9/9 passing** — the layered-architecture guarantee is intact after the bump.

Post-fix CVE re-scan: **0 findings** across the governed set, the 57 independently-pinned Jackson coordinates, and the resolved tree.

## Verification that the fix reached the right artifact

`jackson.version` governs only **7** of the 64 BOM-managed Jackson 3 coordinates — this was checked, not assumed. `tools.jackson.core:jackson-databind` **is** among the 7 that moved to 3.1.5. The 57 that stayed at 3.1.4 were separately scanned: **0 findings**, so no second override is needed.

## Issues found (for downstream / coordinator)

1. **`pom-native.xml` cannot compile — pre-existing, not caused by this task.**
   `./mvnw clean compile -f pom-native.xml` fails with:
   `Bad service configuration file, or exception thrown while constructing Processor object: io/micronaut/inject/processing/definition/ElementBeanDefinitionBuilderFactory`
   **Proven pre-existing:** I rebuilt the unmodified manifest straight from `HEAD` (`git show HEAD:pom-native.xml`) and it fails with the identical error. The enforcer passes; this is a Micronaut annotation-processor classpath skew under micronaut-parent 5.0.2. Native build ownership sits with **t19 (devops)** — flagging, not fixing.

2. **`<micronaut.version>5.1.2</micronaut.version>` is dead config in both POMs.** The version is fixed by `<parent>` (5.0.4 / 5.0.2); the property has no effect. Effective POM shows `micronaut-core` resolving to **5.0.5**. Misleading to any reader. *This also makes `t7-devops.md`'s claim that `pom.xml` inherits `micronaut-parent:5.1.2` stale.*

3. **Native build ships older components than the main build:** Micronaut 5.0.2 vs 5.0.5, `micronaut-test` 5.0.0 vs 5.0.1, `byte-buddy` 1.18.7 vs 1.18.9. No CVEs, but divergence between two shipped artifacts is a hardening concern.

4. **Latent-vs-live nuance on the fixed CVE:** `tools.jackson.core:*` is not on the resolved classpath today (`micronaut.runtime=none`), so this was a *latent pin defect*, not an actively shipped vulnerability. It becomes live the moment a Jackson 3 consumer is added — which is precisely the kind of change a later task could make without re-auditing the pin.

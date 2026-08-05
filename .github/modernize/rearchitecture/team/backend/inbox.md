## 2026-08-05T02:09:50Z — from teamlead (t1) [broadcast]

CONSTITUTION PUBLISHED — All roles must follow `artifacts/t1-teamlead.md`. Key rules:
1. 5+1 layer model: presentation / application / application.port / domain / infrastructure / shared.
2. Dependencies point inward only — domain imports ONLY java.* and shared.
3. Copilot SDK confined to infrastructure.
4. Micronaut / Jakarta confined to infrastructure + presentation.
5. ArchUnit enforces all boundaries.
6. Port naming: VerbNounPort. Adapter naming: TechNounAdapter.
7. Zero package cycles. Violations are CRITICAL.

## 2026-08-05T02:15:20Z — from architect (t2) [broadcast]

Architecture analysis complete: 10 dependency cycles (4 more than recon), 20 files with
Copilot SDK leakage, `TemplateService` is the hub of 5 cycles — defining `LoadTemplatePort`
will be the first port in t4 design. Full 120-file class-to-layer mapping ready for t4.

Artifacts: `t2-architect.md` (index), `t2-architect-cycles.md`, `t2-architect-leakage.md`,
`t2-architect-class-map.md`.

## 2026-08-05T02:22:30Z — from architect (t4) [broadcast]

Architecture design complete: 6 layers, 24 target packages, 12 port interfaces
(5 inbound + 7 outbound), 120 files mapped.

Key decisions:
- `LoadTemplatePort` breaks 5 of 10 cycles.
- Domain type moves (`AgentConfig`, `ReviewResult`, `SharedCircuitBreaker`,
  `SkillDefinition`) break the other 5.
- Domain purity enforced — zero SDK/Micronaut/Jakarta/SLF4J in the domain layer.
- All 69 PM behavior IDs traced to ports.

Artifacts: `t4-architect.md` (index), `t4-architect-packages.md`,
`t4-architect-ports.md` (port catalog + cycle resolution), `t4-architect-classmap.md`.


---
## 2026-08-05T02:49Z — from devops (t7) — INFO

`CopilotService.initializeOrThrow(String)` is **deprecated-for-removal** (warning emitted during `pom.xml` compile).

**Action**: address this when `CopilotService` moves to `infrastructure.copilot` in T009 (coordinator task **t11**). Migrate to the non-deprecated replacement rather than carrying the deprecation forward into the new layer.

---
## 2026-08-05T02:49Z — from devops (t7) — MANDATORY BUILD PRECONDITION

The repo uses **two POMs with different Java releases**. The default active JDK is GraalVM 25, which
**cannot** compile `pom.xml` (it requires `--release 27`). You MUST set `JAVA_HOME` explicitly.

```bash
# Main build (pom.xml — shade JAR, unit tests, ArchUnit):
export JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open
./mvnw -B clean verify -f pom.xml

# Native build (pom-native.xml — GraalVM native-image):
export JAVA_HOME=~/.sdkman/candidates/java/25.0.4-graal
./mvnw -B clean verify -Pnative -f pom-native.xml
```

**Corrected stack facts** — the profile's "Java 26 EA" was stale recon data. Actual:
`pom.xml java.version=27` (OpenJDK 27-ea+32, with `--enable-preview`) and
`pom-native.xml release.version=25` (Oracle GraalVM 25.0.4).
Do NOT "fix" these back to 26. Both POMs currently compile clean (157 source files).

**Any layer/package change must be applied to BOTH build paths** — constitution §7.2 requires shade,
native-image, and Micronaut AOT to keep working. `pom-native.xml` inherits a different
micronaut-parent (5.0.2 vs 5.1.2), so build config fixes are not automatically shared.

Evidence: `.github/modernize/rearchitecture/artifacts/t7-devops.md` §5–§6.

# t1 — Migration Constitution and Layer Dependency Rules

## Summary

This constitution governs the in-place rewrite of `src/main/java/dev/logicojp/reviewer` from its current flat-package structure to a Ports & Adapters layered architecture. All roles MUST follow these rules. Violations are CRITICAL.

## Upstream Artifacts Consumed

- `clarification.md` — target architecture definition, scope, backward-compatibility posture
- `artifacts/project-profile.yaml` — current structure, dependency cycles, SDK leakage inventory

## Evidence Mapping

- `clarification.md#Generic.success_definition` → Layer model (§1), Dependency direction (§2)
- `project-profile.yaml#assessment.transformations` → Layer model (§1)
- `project-profile.yaml#structure` → Current-to-target mapping guidance (§7)
- `clarification.md#user_decisions.boundary_enforcement` → Enforcement mechanism (§6)

---

## §1 Layer Model

All production code under `dev.logicojp.reviewer` MUST reside in exactly one of these layers:

| Layer | Package prefix | Purpose |
|---|---|---|
| **presentation** | `dev.logicojp.reviewer.presentation` | CLI argument parsing, command dispatch, output formatting. Entry point to the application. |
| **application** | `dev.logicojp.reviewer.application` | Use-case orchestration. Coordinates domain objects and ports. Contains NO business rules. |
| **application.port** | `dev.logicojp.reviewer.application.port` | Port interfaces (inbound + outbound). Contracts between application/domain and infrastructure. |
| **domain** | `dev.logicojp.reviewer.domain` | Pure business logic, domain models, value objects. Framework-free. |
| **infrastructure** | `dev.logicojp.reviewer.infrastructure` | Adapter implementations: Copilot SDK clients, file I/O, template engines, external process calls. |
| **shared** | `dev.logicojp.reviewer.shared` | Cross-cutting utilities with ZERO framework dependencies (text parsing, token counting). |

Sub-packages within each layer are permitted and encouraged for cohesion (e.g., `infrastructure.copilot`, `infrastructure.template`, `presentation.command`).

## §2 Dependency Direction — The Cardinal Rule

Dependencies MUST point inward only:

```
presentation → application → domain
                    ↓
infrastructure → application.port ← domain
                    ↑
               shared (leaf — no outward deps)
```

**Allowed imports per layer:**

| Layer | May import from |
|---|---|
| presentation | application, application.port, domain, shared |
| application | application.port, domain, shared |
| domain | shared, `java.*`, `java.util.*` — **NOTHING ELSE** |
| infrastructure | application.port, domain, shared |
| shared | `java.*` only |
| application.port | domain, shared |

**Forbidden (CRITICAL violation):**

- Domain importing any external library (`io.micronaut.*`, `jakarta.*`, `com.github.copilot.*`, `org.slf4j.*`, `org.yaml.*`)
- Any layer importing from presentation
- Infrastructure importing from application (only application.port)
- Any cyclic dependency between packages

## §3 Domain Purity Rules

The `domain` layer:

1. MUST NOT import Micronaut, Jakarta, SLF4J, SnakeYAML, Copilot SDK, or any non-`java.*` type
2. MUST NOT use DI annotations (`@Inject`, `@Singleton`, `@Named`, etc.)
3. MUST NOT perform I/O (file, network, process) — delegate via outbound ports
4. MAY define domain events and exceptions
5. MAY use `java.util.logging` if logging is required (prefer returning results over logging)

## §4 Port Convention

- Port interfaces live in `application.port`
- **Inbound ports**: define use-case entry points; implemented by `application` classes; called by `presentation`
- **Outbound ports**: define infrastructure contracts; implemented by `infrastructure` adapters; called by `application` / `domain`
- Port interface naming: `<Verb><Noun>Port` (e.g., `RunReviewPort`, `LoadAgentDefinitionPort`, `FormatReportPort`)
- One port per cohesive capability — do not create god-ports

## §5 Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Packages | lowercase, singular noun | `presentation.command`, `infrastructure.copilot` |
| Port interfaces | `<Verb><Noun>Port` | `LoadTemplatePort` |
| Adapter classes | `<Tech><Noun>Adapter` | `CopilotSdkReviewAdapter` |
| Use-case services | `<Verb><Noun>UseCase` or `<Noun>Service` | `RunReviewUseCase` |
| Domain models | Noun, no prefix/suffix | `AgentDefinition`, `ReviewResult` |
| Value objects | Noun or compound noun | `ReviewTarget`, `SkillReference` |
| Domain exceptions | `<Noun>Exception` | `AgentValidationException` |

## §6 Boundary Enforcement

- **Single Maven module** is preserved (no multi-module, no JPMS `module-info.java`)
- **ArchUnit tests** MUST verify all rules in §2 and §3. These tests are mandatory deliverables — the architecture is not considered implemented until they pass
- Package cycle detection MUST be included in ArchUnit tests
- ArchUnit test class: `src/test/java/dev/logicojp/reviewer/architecture/LayerDependencyRulesTest.java`

## §7 Migration Invariants

1. **In-place rewrite**: All changes happen within `src/main/java/dev/logicojp/reviewer`. No new Maven modules
2. **Build preservation**: Maven shade, GraalVM native image (`-Pnative`), Micronaut AOT MUST continue to work
3. **Test preservation**: Existing test suite MUST pass after migration (tests may be moved/refactored to match new packages)
4. **CLI contract**: CLI option names and `application.yml` keys MAY change only with ADR justification and migration notes
5. **Zero cycles**: After migration, zero package-level dependency cycles. ArchUnit enforces this
6. **SDK isolation**: `com.github.copilot.*` types appear ONLY in `infrastructure` layer
7. **Framework isolation**: `io.micronaut.*` and `jakarta.inject.*` appear ONLY in `infrastructure` and `presentation` (for DI wiring); NEVER in domain or shared
8. **Existing behavior preserved**: No functional regression. If behavior changes, it requires an ADR

## §8 File Placement Rules

- `ReviewApp.java` (entry point) → `presentation` (or top-level if Micronaut framework requires it)
- Agent definition parsing/validation → `domain` (pure logic) + `infrastructure` (file I/O adapters)
- Copilot SDK client management → `infrastructure.copilot`
- Template rendering → `infrastructure.template`
- CLI commands → `presentation.command`
- Output formatting → `presentation.formatter`
- Orchestration of parallel agent execution → `application`
- Report generation logic → `domain` (rules) + `infrastructure` (formatting/output)
- Configuration binding (`@ConfigurationProperties`) → `infrastructure.config`

## §9 ADR Requirements

Any decision that deviates from this constitution or changes existing CLI behavior MUST be recorded in `docs/adr/` following the existing ADR template. Required fields: status, context, decision, consequences.

## §10 Role-Specific Directives

| Role | Directive |
|---|---|
| **architect** | Design target package mapping and port catalog respecting §1–§4. Produce class-to-layer mapping for every current file |
| **pm** | Inventory CLI commands and behaviors as-is. Feature parity is the acceptance criterion |
| **backend** | Implement layer structure per architect's mapping. ArchUnit tests are part of implementation, not a separate task |
| **tester** | Verify ArchUnit rules pass, existing tests pass, and no functional regression |
| **security** | Audit that SDK/framework isolation (§3, §7.6, §7.7) holds in final code |
| **devops** | Verify build pipeline (shade, native-image, AOT) works with new package structure |

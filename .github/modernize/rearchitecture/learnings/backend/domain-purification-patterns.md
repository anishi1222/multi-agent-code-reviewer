# Domain Purification Patterns

**slug**: domain-purification-patterns

## Context

Brownfield rewrite of a Java CLI app (Micronaut + SDK dependencies) into Hexagonal/Layered architecture. Phase 1 covers shared → domain → ports migration.

## Patterns

### 1. CircuitBreaker interface in `shared` breaks import cycles

Place the `CircuitBreaker` interface in `shared`, NOT in `domain.resilience`. Both `shared.RetryExecutor` and `domain.resilience.SharedCircuitBreaker` depend on `shared.*`, not on each other. Without this, `shared.RetryExecutor` importing the domain class creates a downward dependency violation.

### 2. Constants belong to the class that uses them, not to config

When domain types previously sourced constants from infrastructure config classes (e.g. `CircuitBreakerConfig.DEFAULT_FAILURE_THRESHOLD`), inline them in the domain class. Example:
```java
// BAD (imports infrastructure config class)
import dev.logicojp.reviewer.config.CircuitBreakerConfig;
int threshold = CircuitBreakerConfig.DEFAULT_FAILURE_THRESHOLD;

// GOOD (inline in domain class)
static final int DEFAULT_FAILURE_THRESHOLD = 8;
```

### 3. `@Nullable` removal: just omit it

In the domain layer, simply remove `io.micronaut.core.annotation.@Nullable` — no replacement needed. Java supports null without annotation. Replacing with `Optional` fields breaks record compact constructors and changes API contracts.

### 4. SLF4J removal: silent fallback pattern

When removing SLF4J logger from domain classes that logged resource-loading failures:
```java
// BAD
if (stream == null) { logger.debug("resource not found: {}", path); return defaults; }

// GOOD: silently return defaults
if (stream == null) { return DEFAULT_VALUES; }
```
The log can be re-added in the infrastructure wrapper if needed.

### 5. YAML-free frontmatter parsing for domain layer

Use a simple `---` delimiter + line-by-line `key: value` parser in the domain layer. This is sufficient for all scalar frontmatter fields used by the domain. Complex YAML types stay in infrastructure's `FrontmatterParser`.

### 6. Outbound port DTOs — no SDK types allowed

When creating DTOs for outbound ports (called by application, implemented by infrastructure):
- Replace SDK types (`McpServerConfig`) with new domain DTOs (`McpServerSpec`)
- Infrastructure adapters map domain DTOs → SDK types at the boundary
- The domain DTO should contain only `java.*`, `domain.*`, `shared.*` types

### 7. Brownfield coexistence: old files stay untouched

During Phase 1, create new files at new package paths. Do NOT delete old files. Old tests continue to compile against old packages. New tests test new packages. This ensures `mvn test-compile` stays green at every step.

### N. Multi-field config: split into a pure `shared` record + an infrastructure binder

Pattern 2 above ("inline the constant") is the right move for a *single* constant. It does **not** scale when domain
needs a **multi-field, stateful** config object — inlining 8 fields duplicates them and they drift.

Instead **split the type in two**:

```java
// shared/PromptBudget.java — pure record, java.* only, normalises via ConfigDefaults
public record PromptBudget(int maxChars, int maxFiles, /* … */) {
    public PromptBudget { /* normalisation */ }
    public PromptBudget() { this(ConfigDefaults.…); }          // defaults
    public PromptBudget withCompactPrompts(boolean b) { … }
}

// infrastructure/config/PromptBudgetConfig.java — thin Micronaut binder, ONE exit point
@ConfigurationProperties("prompt-budget")
public class PromptBudgetConfig {
    public PromptBudget toPromptBudget() { return new PromptBudget(…); }
}
```

Rules that make this work:

- **Give the two types deliberately different names** (`PromptBudget` vs `PromptBudgetConfig`). Identical names invite
  the `duplicate-utility-consolidation-semantic-drift` trap — a later agent "consolidates" them and re-breaks layering.
- **Exactly one crossing point** (`toPromptBudget()`), so the boundary is greppable.
- **Route it through a type `application` may already see.** `application` cannot import `infrastructure`, so hand the
  pure record to an existing config record/builder that already flows infrastructure→application
  (here `OrchestratorConfig`, `SummaryGenerationConfig`). Because both used builders/records with defaults, adding a
  component broke only 5 test call-sites.
- **Prefer hoisting to `shared` over inlining when >1 layer needs the constant** — `AgentPromptBuilder` (domain) and
  `SkillConfig` (infrastructure) both needed `MAX_PARAMETER_VALUE_LENGTH`, so it went to
  `shared/ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH` with `SkillConfig` delegating. Inlining would have forked it.

**Merge-specific trigger (t23):** when upstream adds a framework-bound config type that domain consumes, `git merge`
will auto-insert the illegal import. It **compiles fine** and only the architecture test catches it. After any merge,
grep `domain/` and `shared/` for framework/infrastructure imports rather than trusting a green compile.

## History
- 2026-08-05 (multi-agent-code-reviewer/t10): added functional interface strategy pattern + visibility rule
- 2026-08-06 (multi-agent-code-reviewer/t23): added pattern N — multi-field config split (pure `shared` record +
  infrastructure binder), naming discipline to avoid consolidation drift, and the auto-merge layer-violation trigger

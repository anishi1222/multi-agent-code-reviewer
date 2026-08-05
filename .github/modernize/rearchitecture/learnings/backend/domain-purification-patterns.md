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

## History
- 2026-08-05 (multi-agent-code-reviewer/t10): added functional interface strategy pattern + visibility rule

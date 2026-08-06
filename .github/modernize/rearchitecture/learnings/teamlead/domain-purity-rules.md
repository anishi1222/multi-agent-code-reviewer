# Domain Purity Rules

Domain layer must import only java.* and shared — no Micronaut, Jakarta, SLF4J, SnakeYAML, or Copilot SDK.

## What Happened
Constitution §3 codifies that domain is framework-free. This is the most likely rule to be accidentally violated during implementation since the current codebase has framework annotations scattered across all packages.

## Takeaway
When moving classes to domain, strip all `@Inject`, `@Singleton`, `@Named`, `@ConfigurationProperties` annotations. Extract I/O into outbound ports. Use constructor parameters instead of DI annotations — the infrastructure/presentation layer wires them via Micronaut.

## History
- 2026-08-05 (rearchitecture/t1): initial

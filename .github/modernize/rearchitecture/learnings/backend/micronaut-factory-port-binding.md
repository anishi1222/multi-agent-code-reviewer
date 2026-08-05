# Micronaut @Factory Pattern for Port-to-Implementation Binding

Use Micronaut `@Factory` to bind port interfaces to infrastructure implementations without adding `@Singleton` to implementation classes that shouldn't be directly injectable.

## What Happened
Presentation layer's `ReviewRunExecutor` needs `GenerateReportPort` (an inbound port interface). The implementation `GenerateReportUseCase` requires constructor params from other beans. Created `ApplicationPortFactory` with `@Factory` + `@Singleton` methods, one per port.

## Takeaway
- `@Factory` class: `@Factory public class ApplicationPortFactory {}`
- Each method: `@Singleton PortInterface methodName(Dep1 d1, Dep2 d2) { return new UseCase(d1, d2); }`
- Micronaut injects the factory's dependencies automatically
- The method return type (the port interface) is what gets registered as a bean

## Example
```java
@Factory
public class ApplicationPortFactory {
    @Singleton
    GenerateReportPort generateReportPort(WriteReportPort writer, LoadTemplatePort templates, ...) {
        return new GenerateReportUseCase(writer, templates, ...);
    }
}
```

## History
- 2026-08-05 (multi-agent-code-reviewer/t12): initial

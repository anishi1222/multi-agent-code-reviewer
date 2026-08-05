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

## Method order is load-bearing (added t16.1)
Micronaut names each generated bean definition after the factory method's **declaration index** —
`$ApplicationPortFactory$ExecuteSkillPort5$Definition` is the 6th method. Architecture rules and
any tooling that references those names break when a method is inserted above an existing one.

**Append new factory methods at the end**, and say so in the method's Javadoc. Changing a method
*body* (e.g. rebinding a port to a different implementation) is safe — indices are unaffected.

## Bind the port, then prove the binding (added t16.1)
`@Factory` makes the *return type* the bean. That means a port can be silently bound to the wrong
implementation while every reference in the codebase stays legal — no static/bytecode rule can see
it. In t16.1 `ExecuteSkillPort` was bound to an infrastructure adapter while the correct
`ExecuteSkillUseCase` sat unreachable.

Pair each factory method with a `@MicronautTest` asserting what the container actually returns:

```java
@MicronautTest(environments = Environment.CLI)
class PortDirectionWiringTest {
    @Inject ExecuteSkillPort port;
    @Test void boundToUseCase() { assertThat(port).isInstanceOf(ExecuteSkillUseCase.class); }
}
```

## History
- 2026-08-05 (multi-agent-code-reviewer/t12): initial
- 2026-08-05 (multi-agent-code-reviewer/t16.1): added method-index brittleness + wiring-test rule

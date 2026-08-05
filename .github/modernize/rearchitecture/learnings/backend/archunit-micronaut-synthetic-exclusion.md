# ArchUnit — Excluding Micronaut-Generated Synthetic Classes

When Micronaut annotation processing generates `$ClassName$Definition` classes (e.g., `$ReviewApp$Definition`), ArchUnit's `resideOutsideOfPackage(presentation)` rule will flag them for referencing presentation-layer types.

## What Happened
Rule "nothing may import presentation" failed because Micronaut's generated `$ReviewApp$Definition` (in the root package) references `CliCommand` and `CliOutput` from the presentation layer. `haveSimpleNameNotContaining("$")` did NOT work — ArchUnit's `getSimpleName()` for top-level synthetic classes behaves differently than expected.

## Takeaway
Use `haveNameNotMatching(".*\\$.*")` (full class name regex) to exclude all Micronaut-generated synthetic classes from ArchUnit rules. Also set `archRule.failOnEmptyShould=false` in `archunit.properties` to prevent false failures when packages are transitionally empty.

## Example
```java
noClasses()
    .that().resideOutsideOfPackage(BASE + ".presentation..")
    .and().haveNameNotMatching(".*\\$.*") // exclude Micronaut synthetics
    .should().dependOnClassesThat().resideInAPackage(BASE + ".presentation..")
```

## History
- 2026-08-05 (multi-agent-code-reviewer/t12): initial

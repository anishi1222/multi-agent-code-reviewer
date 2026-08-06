# Logback Version BOM Override in pom-native.xml

pom-native.xml (micronaut-parent:5.0.2) needs explicit logback.version property to prevent convergence failure.

## What Happened

`pom-native.xml` inherits from `micronaut-parent:5.0.2`. When logback-classic:1.5.37 is declared
as a dependency, the BOM also brings in a transitive dependency on logback-core:1.5.32, creating
a convergence conflict with the explicitly declared logback-core:1.5.37. The `DependencyConvergence`
enforcer rule fails. `pom.xml` (micronaut-parent:5.1.2) already had `<logback.version>1.5.37</logback.version>`
as a property, which tells the Micronaut BOM to use that version throughout. pom-native.xml was missing it.

Project: anishi1222/multi-agent-code-reviewer / t7 — commit f63a79c

## Takeaway

When adding or updating logback to pom-native.xml, always ensure `<logback.version>X.Y.Z</logback.version>`
is in the `<properties>` block — not just version tags on the dependency declarations.

## Example

```xml
<properties>
    <!-- Without this, BOM resolves logback-classic with transitive logback-core at a mismatched version -->
    <logback.version>1.5.37</logback.version>
</properties>
```

## History

- 2026-08-05 (anishi1222/t7): initial

# Logback Version BOM Alignment

Keep both POMs on one Micronaut parent and prefer its coherent Logback BOM over legacy overrides.

## What Happened

In t7, `pom-native.xml` inherited from `micronaut-parent:5.0.2`. When logback-classic:1.5.37 was declared
as a dependency, the BOM also brings in a transitive dependency on logback-core:1.5.32, creating
a convergence conflict with the explicitly declared logback-core:1.5.37. The `DependencyConvergence`
enforcer rule fails. `pom.xml` (micronaut-parent:5.1.2) already had `<logback.version>1.5.37</logback.version>`
as a property, which tells the Micronaut BOM to use that version throughout. pom-native.xml was missing it.

In t19, both POMs moved to `micronaut-parent:5.1.0`. Its effective dependency graph resolves both
Logback artifacts to 1.5.37, so the old property and explicit dependency versions became redundant
and were removed.

Project: anishi1222/multi-agent-code-reviewer / t7, t19

## Takeaway

Do not copy an old BOM workaround forward automatically. First align the two parent versions, then
run `dependency:tree` and the Enforcer `DependencyConvergence` rule for both POMs. Add an explicit
override only when the effective graph still proves one is required.

## History

- 2026-08-05 (anishi1222/t7): initial
- 2026-08-07 (anishi1222/t19): retired the override after parent/BOM convergence

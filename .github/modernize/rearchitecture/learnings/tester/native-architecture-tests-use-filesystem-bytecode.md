# Native Architecture Tests Should Inspect Filesystem Bytecode

Architecture tests that verify compiled artifacts should parse class files from build output rather than rely on classloader resources or runtime reflection.

## What Happened

In `multi-agent-code-reviewer` task t20, the JVM architecture suite passed while its native test
image failed four controls. Three named test fixtures were pruned because they were reached only as
string resource paths, and reflection returned an empty method inventory for `ReviewApp` because
`main` had no native reflection registration.

The test was repaired without changing production behavior: `java.lang.classfile` reads the
production entry point from `target/classes`, and negative controls read fixture bytecode from
`target/test-classes`. The focused suite passed 22/22; the exact native gate then passed all 1,106
native tests.

## Takeaway

When the contract is about the built class, make the class file the source of truth. Read production
subjects from `target/classes` and deliberately retained test fixtures from `target/test-classes`;
keep exact owner/target and non-vacuity assertions. Do not add broad native reflection/resource
metadata solely to make a test harness observe artifacts already present on disk.

## History

- 2026-08-09 (multi-agent-code-reviewer/t20): initial

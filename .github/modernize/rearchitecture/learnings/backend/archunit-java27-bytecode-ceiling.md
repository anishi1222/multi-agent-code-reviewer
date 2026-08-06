# ArchUnit Cannot Parse Java 27 Bytecode — Use `java.lang.classfile` Instead

**ArchUnit silently inspects a partial class set on Java 27 and reports green.** Its shaded ASM rejects class-file major version 71, catches the error, logs it, and continues. Rules then pass because they inspect almost nothing.

## What Happened

Six layer-boundary rules were all green while `ReviewApp` openly imported 5 `presentation.*` types. A class-file major-version histogram over `target/classes` explained it:

| major | count | what |
|---|---|---|
| 61 (Java 17) | 107 | Micronaut-generated DI glue only |
| 71 (Java 27) | 580 | **every hand-written class** |

ArchUnit imported **107 of 687**. `ReviewApp` was never in the set — it did not pass, it was invisible. All six rules inspected zero application classes.

Two settings made this undetectable, and each hid the other:
- `archRule.failOnEmptyShould=false` suppressed the "rule matched no classes" error — the one signal that would have exposed the empty import set.
- A `haveNameNotMatching(".*\\$.*")` synthetics filter discarded virtually the only classes ArchUnit could still read.

## Takeaway

**Do not use ArchUnit on Java 24+ toolchains without first proving it parses your bytecode.** Upgrading does not help: the newest release (1.4.1) shades ASM with a ceiling of `V25 = 69`. Verify before planning an upgrade:

```bash
unzip -p ~/.m2/.../archunit-1.4.1.jar \
  com/tngtech/archunit/thirdparty/org/objectweb/asm/Opcodes.class > /tmp/Opcodes.class
javap -constants /tmp/Opcodes.class | grep -E 'V[0-9]+ ='   # highest = supported ceiling
```

ASM is **shaded**, so a newer ASM in your POM has no effect.

**Replace it with `java.lang.classfile`** (JEP 484, final since JDK 24). It parses any version the JDK compiles because it *is* the JDK's parser — permanently immune to third-party ceilings — and it removes a dependency instead of adding one. ~25KB of test code replaced all six rules plus Tarjan cycle detection.

Two non-obvious requirements:

1. **Union `ClassEntry` with a regex sweep of `Utf8Entry`.** Annotation types (`@Inject`, `@Singleton`, `@ConfigurationProperties`) and generic signatures exist *only* as Utf8 descriptors, never as `ClassEntry`. A domain-purity rule that skips the Utf8 sweep cannot see the framework annotations it exists to detect. Over-approximation errs toward false-RED and can never hide a violation.
2. **Add a completeness gate.** Assert parsed count == on-disk `.class` count, plus a few named anchor classes. This is the only test that can catch a silently-degraded analyzer, and its absence is what let the original failure ship.

## Example

```java
Set<String> deps(ClassModel cm) {
    Set<String> out = new HashSet<>();
    for (PoolEntry e : cm.constantPool()) {
        if (e instanceof ClassEntry ce) out.add(ce.asInternalName().replace('/', '.'));
        // MANDATORY: annotations & generic signatures live only here
        if (e instanceof Utf8Entry u) {
            Matcher m = Pattern.compile("L([\\w/$]+);").matcher(u.stringValue());
            while (m.find()) out.add(m.group(1).replace('/', '.'));
        }
    }
    return out;
}

@Test
void analyzerSeesEveryCompiledClass() throws IOException {
    try (var walk = Files.walk(Path.of("target/classes"))) {
        long onDisk = walk.filter(p -> p.toString().endsWith(".class")).count();
        assertThat(parsed).hasSize((int) onDisk);   // no silent partial import
    }
    assertThat(parsedNames).contains("dev.example.ReviewApp");  // named anchor
}
```

## History
- 2026-08-05 (multi-agent-code-reviewer/t12.1): initial — supersedes the ArchUnit guidance in `archunit-micronaut-synthetic-exclusion.md`

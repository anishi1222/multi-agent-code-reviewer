# A Record Field Name Can Break A Constant-Pool Regex Scan

`javac` emits a `;`-joined list of record component names as a string constant; a descriptor regex with an optional package separator matches `L…;` inside it and invents a dependency on a class that does not exist.

## What Happened

`multi-agent-code-reviewer` / t18.2. The full suite failed with:

```
Rule 1 (domain purity)  Violations found:
  dev.logicojp.reviewer.domain.agent.AgentTrustProfile -> ines
```

`ines` is not a class. `LayerDependencyRulesTest` scans `Utf8Entry` constant-pool entries — which
it must, because annotations and generic signatures appear *only* as UTF-8 descriptors — using

```java
Pattern.compile("L([a-zA-Z_$][a-zA-Z0-9_$]*(?:/[a-zA-Z_$][a-zA-Z0-9_$]*)*);")
```

The separator group is `*`, so the pattern also matches unqualified `L…;` runs in ordinary string
constants. Every record carries one: `equals`/`hashCode`/`toString` are bootstrapped through
`ObjectMethods` with a name list, here

```
maxFileChars;maxInstructionChars;maxInstructionLines;enforcesCharset;rejectsUnknownFrontmatterKeys
```

where `Lines;` matches and captures `ines`.

The trigger is **positional**: another record in the same codebase had `fence;openingLineEnd`, the
identical hazard, but its `L`-bearing component was last, so no `;` followed and the regex missed
it. Reordering fields would have broken the build spontaneously.

## What To Do

- In a descriptor scan, **require the package separator** (`(?:/…)+`). Framework annotations and
  signatures are always package-qualified, so nothing real is lost.
- Add a **compensating guard** for the assumption you just introduced — here, "no class lives in
  the default package" — so it is asserted rather than assumed.
- **Verify detection power after narrowing, do not assert it.** Exemption lists that assert *exact*
  equality do this for you: if the narrowing hid a genuine violation, an exemption goes stale and
  the test fails. I confirmed Rule 4 still found `10 violator(s), 10 exempt`, including the 7
  Micronaut-generated `$Definition` beans — precisely the annotation-driven dependencies the UTF-8
  sweep exists to catch.
- When a structural test fails on a name that is not a class, **read the constant pool**
  (`javap -v -p`) before believing the message. The fix belongs at the detector.

## Why It Matters

Renaming the field would also have gone green, while leaving the trap armed for the next record and
distorting domain vocabulary to satisfy a regex. "Make the failing check pass" and "fix the defect
the check found" diverge whenever the check itself is what is wrong — and a structural test that
names a nonexistent class is announcing that it is the one at fault.

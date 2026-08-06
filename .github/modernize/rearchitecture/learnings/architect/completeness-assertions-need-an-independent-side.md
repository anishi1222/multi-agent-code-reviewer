# A Completeness Assertion Whose Two Sides Share One Source Proves Nothing About Coverage

`assertEquals(filesOnDisk, parsed.size())` proves the parser saw everything compiled — never that everything in source was compiled.

## What Happened

`multi-agent-code-reviewer` rearchitecture, t24 (post-merge conformance check after an 82-conflict
merge touching 28 main source files).

The architecture test suite opens with Rule 0, its anti-vacuity guard — the rule that exists
because t12 discovered ArchUnit had silently parsed 107 of 687 classes and rendered all six layer
rules vacuous. Rule 0 reports `parsed 331/331` and is meant to guarantee every class is in scope.

But `analyseBytecode()` computes `classFilesOnDisk` by walking `target/classes`, then parses
*those same files* into `dependencies`, and Rule 0 asserts the two are equal. **Both sides derive
from a single directory walk.** If a source file never reaches `target/classes` — excluded by build
config, wrong source root, a merge that dropped it from the module — both numbers shrink together,
Rule 0 stays green, and that file escapes every layer rule.

Rule 0 answers "did the parser see everything that was compiled?" The question after a large merge
is "did everything in source get compiled *and* inspected?" Those are different questions, and the
green number does not distinguish them.

I closed it with an independent assertion: map every `src/main/java/**/*.java` to its expected
`.class` and check existence. 175/175 present, and all 28 merge-touched files among them. Only then
did `331/331` mean what it appears to mean.

## Takeaway

When a completeness assertion compares two quantities, ask **where each side comes from**. If both
derive from the same enumeration, the assertion is a self-consistency check, not a coverage check —
it can only catch a parser that drops items *after* enumeration.

To make it a real coverage check, one side must come from an **independent source of truth**:
source tree vs. build output, manifest vs. filesystem, spec vs. implementation. Anchor assertions
on a handful of named items help but do not close the gap unless the anchors include the items most
likely to be missing (after a merge, that means merge-added files — which named anchors never are).

Run the source→artifact correspondence check explicitly after any large merge, module move, or
build-config change. It is one shell loop and it is the only thing standing between a green
architecture gate and a file that quietly answers to no rule.

## Example

```bash
# Rule 0 says 331/331 — but assert the scope independently:
for f in $(find src/main/java -name '*.java'); do
  rel=${f#src/main/java/}
  [ -f "target/classes/${rel%.java}.class" ] || echo "MISSING: $rel"
done
# zero MISSING lines => Rule 0's denominator genuinely covers the source tree
```

## History

- 2026-08-06 (multi-agent-code-reviewer/t24): initial, from decision item 3-C — the ninth instance
  of this run's standing pattern, *a control's scope of application is invisible at the call site*.

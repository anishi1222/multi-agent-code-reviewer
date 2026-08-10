# Derive and Sweep Finite Security Domains

When a security control's input domain is finite, derive the rule from a property and prove it
by exhaustive enumeration — never curate a list of the bad values.

## What Happened

multi-agent-code-reviewer / t18.3 (SEC-H3). The agent-definition charset allowlist was 15
curated Unicode **block ranges**. A block admits *everything* in it, not just what its author
wanted: `\uFF00-\uFFEF` was added for fullwidth ASCII and carried `U+FFA0 HALFWIDTH HANGUL
FILLER`, which renders blank but is category `Lo`. It therefore defeated the charset allowlist
(in-range) *and* the prompt-injection denylist (`ig<U+FFA0>nore all previous instructions`
matches nothing, and the normalisation strip was `[\p{Cf}\p{Cc}]` — U+FFA0 is a letter).

The prior task, t18.2, hand-narrowed one range and got it exactly right. The identical defect
survived untouched in three other ranges of the same constant. Hand-curation does not generalise
even when the curator is correct.

Three things made the eventual fix hold:

1. **Subtract a property, not values.** Nine Unicode general categories (`Cf Cc Cn Co Cs Zl Zp
   Mn Me`) subtracted from whatever the ranges admit, as an AND-narrowing after the existing
   regex. It can only reject more, never admit more, so widening a range later cannot silently
   reintroduce an invisible character.
2. **Derive the residue from machine data.** Six blank-rendering codepoints are categorised
   `Lo`/`So`/`Po`, so no category mask sees them. Sweeping the BMP against `Character.getName()`
   found six; the hand-written list of "obvious" ones — including the security reviewer's own
   recommendation — had five. `U+A8F9 DEVANAGARI GAP FILLER` was on nobody's list.
3. **Enumerate the whole domain.** 1,114,112 codepoints is a `for` loop. The sweep printed 37
   offenders before the fix and 0 after, per range. No sampling, no judgement about which blocks
   "look risky" — that judgement is what produced the bug twice.

## Takeaway

- Before curating a denylist, ask whether the domain is finite. Unicode codepoints, enum values,
  HTTP methods, file extensions in a repo, MIME types — all enumerable. Sweep them.
- Pin derived data with **exact-equality re-derivation in the test**, not a subset check. Then a
  JDK/library upgrade that changes the underlying data fails the build *naming what changed*
  instead of silently widening the control. Subset checks let the set rot both ways.
- Keep the test's oracle **independent of the implementation**. Production pins an explicit set;
  the test re-derives from Unicode name tables. If both used the same mechanism the assertion
  would restate the code instead of checking it.
- Mutation-verify with an **over-block mutant**, not only a removal mutant. Removing the control
  proves the rejection tests work; adding `Zs` to the blocked set proved the *acceptance* tests
  work. Without the second, the rule is free to drift too strict and break legitimate content.
- Measure the blast radius on real content before accepting a broad category ban. Blocking `Mn`
  sounded expensive (it rejects NFD kana); scanning 1,332 repo files found **0** regressions,
  which turned a debate into a number.
- Restore mutants with `cp` from a `/tmp` snapshot. `git checkout <file>` reverts to HEAD and
  destroys in-progress work.

## Example

```java
// AND-narrowing: the range check is unchanged, the property subtraction is added after it.
if (!ALLOWED_CHAR_RANGE.matcher(content).matches()) return false;
return content.codePoints().noneMatch(cp ->
       !PERMITTED_CONTROL_CHARACTERS.contains(cp)              // \t \n \r are Cc on purpose
    && (INVISIBLE_CODE_POINTS.contains(cp)
        || BLOCKED_CATEGORIES.contains(Character.getType(cp))));
```

```java
// The test re-derives and demands exact equality — production has no name-based logic.
for (int cp = 0; cp <= 0xFFFF; cp++)
    if (isNamedInvisible(cp) && !inBlockedCategory(cp)) derived.add(cp);
assertThat(INVISIBLE_CODE_POINTS).containsExactlyInAnyOrderElementsOf(derived);
```

## History

- 2026-08-06 (multi-agent-code-reviewer/t18.3): initial, from the SEC-H3 charset fix

# Charset Allowlists Need Category Masks, Not Curated Block Ranges

A Unicode block range admits everything in the block, including invisible `Lo` fillers and unassigned codepoints, so hand-curated ranges keep reintroducing the hole they were narrowed to close.

## What Happened

`multi-agent-code-reviewer`, t18/t18.2/t18-re-run. `ALLOWED_CHAR_RANGE` guards
repository-supplied agent definitions (untrusted markdown → LLM instructions). It is a
whitelist of 15 Unicode block ranges.

t18.2 fixed defect F1: the range `\u2000-\u206F` had been whitelisted wholesale and was
admitting U+202A–202E (bidi override) and U+200B–200F (zero-width) — the exact
Trojan-Source characters the control existed to reject. Backend hand-narrowed it into three
sub-ranges and got it **exactly right**: 0 of 21 target codepoints leak.

The re-run swept all 65,536 BMP codepoints against the shipped regex and classified them by
Unicode category. The three F1-narrowed ranges were the only clean ones. Three *other*
ranges admitted 31 problem codepoints, including **U+FFA0 HALFWIDTH HANGUL FILLER** inside
`\uFF00-\uFFEF` (included for fullwidth ASCII and halfwidth katakana).

U+FFA0 defeated **both** layers of defence at once:

- charset allowlist → **ADMIT** (it is in the block)
- prompt-injection denylist → **silent**, because `ig<U+FFA0>nore all previous instructions`
  does not match `ignore\s+(all\s+)?previous\s+instructions?`

Normalisation did not help and was not at fault. `NFKC(U+FFA0) = U+1160` — one invisible
filler maps to another — and the strip is `[\p{Cf}\p{Cc}]` while both are category `Lo`
(OTHER_LETTER). Java's `\s` is ASCII-only without `UNICODE_CHARACTER_CLASS`, so the
whitespace collapse missed them too. Every stage worked as designed; the allowlist simply
admitted something no later stage claimed to handle.

Rated HIGH — same class and same boundary as F1. Two consecutive reviews had passed with a
green suite because the constant's only behavioural pin asserts **one** codepoint
(`U+202E`) against a 33,478-codepoint allowlist.

## Takeaway

**Never express a character allowlist as curated block ranges alone.** A block is named for
what its author wanted from it, and admits everything else in it. Fixing one block by hand
leaves the identical defect in every other block, and the fix *looks* thorough.

Subtract a category mask from whatever the ranges admit, so hygiene stops depending on
anyone's memory of block contents:

- reject `Cf`, `Cc`, `Cn` (unassigned — future Unicode decides what they become),
  `Co`, `Zl`, `Zp`
- **plus** the blank-rendering codepoints that are category `Lo` and therefore invisible to
  a `Cf`/`Cc` filter: `U+115F`, `U+1160`, `U+3164`, `U+FFA0`, `U+2800`

Also:

- **`\p{Cf}\p{Cc}` is not "the invisible characters".** The most useful ones for evasion are
  letters. Any control built on `Cf`/`Cc` misses them by construction.
- **Enumerate finite input domains.** The BMP is 65,536 codepoints — one probe. Reasoning
  about which blocks "look risky" is slower and worse.
- **Copy the constant verbatim into the probe.** Retyping it from intent silently repairs
  the defect you are hunting.
- **A one-codepoint test on an allowlist is not coverage.** Ask what a passing test actually
  ranges over, not whether it is green.
- Dead end, do not re-derive: `^[...]*$` with `DOTALL` but not `MULTILINE` looks bypassable
  in trailing position, because Java's `$` matches before a final line terminator and
  U+2028/U+2029/U+0085 are Java line terminators. It is **not** — `Matcher.matches()`
  requires full-region consumption.

## Example

```java
// Curated ranges alone: \uFF00-\uFFEF was added for fullwidth ASCII,
// and silently admits U+FFA0 HALFWIDTH HANGUL FILLER (invisible, category Lo).
if (!ALLOWED_CHAR_RANGE.matcher(s).matches()) reject();

// Add the subtraction, so no one has to remember block contents:
s.codePoints().forEach(cp -> {
    int t = Character.getType(cp);
    if (t == Character.FORMAT || t == Character.CONTROL || t == Character.UNASSIGNED
        || t == Character.PRIVATE_USE || t == Character.LINE_SEPARATOR
        || t == Character.PARAGRAPH_SEPARATOR || INVISIBLE_LETTERS.contains(cp)) reject();
});
```

And pin it with a negative control that is *not* a bidi character — assert a
blank-rendering `Lo` filler is rejected, or the next narrowing passes for the same
wrong reason.

## Verifying Such A Fix: Use A Definition The Code Does Not Own

When production pins a set of "invisible" codepoints and its test re-derives that set from
`Character.getName()`, those are two mechanisms sharing one *definition*. A wrong definition is
invisible to both, and the equality assertion between them still passes. Do not audit that by
re-deriving a third time from names.

Use the Unicode Consortium's own derived property instead:

    curl -sS https://www.unicode.org/Public/16.0.0/ucd/DerivedCoreProperties.txt

`Default_Ignorable_Code_Point` is the standard's canonical "should render as nothing" set (4,174
codepoints in Unicode 16.0.0). Parsing it is ~20 lines — split on `;`, expand `A..B` ranges. Java
regex does **not** expose it (`\p{IsDefault_Ignorable_Code_Point}` throws
`PatternSyntaxException`); it only offers `IsWhite_Space`, `IsNoncharacter_Code_Point`,
`IsAssigned`, `IsJoin_Control`. Parsing the UCD gives you any property, not just those four.

Then assert the population, not a sample: *zero* of the 4,174 may be admitted. Also measure how
many reach the pinned set at all — for this codebase only **1 of 6** pinned fillers (U+FFA0) was
reachable through the block ranges; the other five sat outside them and were pure defence-in-depth.
Checking reachability first costs one loop and cuts the surface you have to argue about by 5/6.

Build the probe in the same package as the control, from a `cmp`-verified byte-identical copy of
the source, with `src/main/resources` on the classpath. Same package means package-private
constants are readable without reflection and, critically, **without retyping them** — retyping a
constant silently repairs the defect you are auditing. The resources matter too: without them a
validator that loads a denylist from a resource falls back to its in-code defaults and you audit
the wrong denylist.

## Whitespace Splits A Denylist, And That Is Not A Finding

A classifier for "renders blank" built as `NFKC(cp)` → all-whitespace will flag every `Zs`
character as defeating a keyword denylist, because `ig<HAIR SPACE>nore` does not match `ignore`.
**Sanity-check it by looking for U+0020 SPACE in your own output.** If plain ASCII space is in the
list, the classifier is measuring "breaks a keyword", not "invisible" — and breaking a keyword with
a visible gap is something an attacker can do by pressing the spacebar. Not a charset defect, not
fixable by an allowlist, and filing it as HIGH burns reviewer trust.

The inverse is worth testing and is easy to miss: NFKC folding is *protective*, not merely neutral.
Put the exotic space where a space legitimately belongs — `ignore<Zs>all previous instructions` —
and the denylist still fires, because NFKC folds every `Zs` to U+0020 before matching.

Related blind spot: a category deliberately left **out** of the blocked set (here `Zs`) can never
appear in a sweep that only reports what was *removed*. Removal-only sweeps are structurally unable
to see it. Check deliberately-unblocked categories separately.

## History

- 2026-08-06 (multi-agent-code-reviewer/t18 re-run): initial. Found U+FFA0 two-layer bypass
  after t18.2's F1 fix; recorded the `$`/line-terminator dead end and the `Cf`/`Cc` gap.
- 2026-08-06 (multi-agent-code-reviewer/t18 gate re-run 2): SEC-H3 confirmed closed. Added the
  external-oracle verification method (Unicode `Default_Ignorable_Code_Point`), the 1-of-6
  reachability result, and the U+0020-in-your-own-output check that stopped a false HIGH.

# Carry Trust As A Type, And Refuse To Provide A Defaulting Overload

When two inputs deserve different trust, the distinction must survive as a type all the way to the validator — and the convenience overload that would spare you editing call sites is the exact thing that reintroduces the bug.

## What Happened

`multi-agent-code-reviewer` / t18.2. `ApplicationPortFactory` concatenated operator-supplied
directories and repository-supplied directories into one `List<Path>`. After that line the two
were indistinguishable, which caused two separately-filed findings that were really one defect:

- untrusted content was validated at operator leniency (SEC-H2);
- the strict limits had no condition to attach to, so they were never wired and read as
  intentional dead code (SEC-H1).

Introducing `AgentSource` (enum) and `AgentSourceDirectory` (path + source) broke ~10 test files.
The tempting fix was a `List<Path>` overload defaulting to the lenient value. That would have
restored exactly the property being removed: the compiler would stop asking "whose content is
this?", and the next caller would silently get the wrong answer.

## What To Do

- Model provenance as an **enum, not a boolean** — the value gets read three layers from where it
  was set, and `true` means nothing there.
- **Do not add a defaulting overload.** Take the mechanical call-site edits. Every caller then
  states its trust level, which is the guarantee you are buying.
- **Fail closed**: an unknown or null provenance resolves to the *stricter* profile. A forgotten
  tag then causes a possible false rejection, never a silent widening.
- **Assign trust in exactly one place** — the composition root, where both sources are visible
  together. Layers that can only ever produce one kind (CLI args are always operator-supplied) may
  tag their own, but the security-critical direction stays at the root.
- Write the differential test **first** and watch it go red: same bytes, two provenances, opposite
  verdicts. Mine failed with *"identical content from the reviewed repository must be refused"*
  before the fix — that captured red is the only proof the limits are actually provenance-aware.

## Why It Matters

A uniform limit is not a weaker version of a trust model; it is the absence of one. Any amount of
work on the limits themselves is wasted while provenance is missing, which is why the ordering
(provenance → policy owner → per-element contract) is a correctness constraint rather than a
preference.

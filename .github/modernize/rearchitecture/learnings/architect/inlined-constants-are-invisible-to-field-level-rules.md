# Inlined Constants Are Invisible To Field-Level Rules

A `public static final` primitive/String read compiles to a literal with no `Fieldref`, so a bytecode rule can only enforce it at *type* granularity — narrow the ADR's prose to match, never the reverse.

## What Happened

`rearchitecture/t30`. t24 specified Rule 8 as "no `domain` class may reference a **limit constant**".
That granularity turned out to be unenforceable, not merely awkward.

`SKILL_MAX_PARAMETER_VALUE_LENGTH` is a `public static final int` — a JLS §4.12.4 *constant
variable*. JLS §13.1 requires it to be resolved at compile time, so the read compiles to
`sipush 10000` and **no `Fieldref` is ever emitted**. Nothing in the reading class's bytecode names
the field. No amount of rule cleverness recovers it.

What makes the rule possible at all: javac still writes an **unreferenced `CONSTANT_Class` entry**
recording the compile-time dependency on the declaring type. Verified with `javap -v` (`#7 = Class
#8 // probe/Limits`, referenced by no instruction). That is *javac behaviour, not a JVMS guarantee.*

Evasion shapes probed on JDK 28 — re-export, static import, and arithmetic all stayed **visible**;
a read in a **`case` label** (`case Limits.MAX ->`) left **zero** trace in the pool.

## Takeaway

- Before writing a rule over `static final` values, check the constant pool with `javap -v`. Do not
  infer it from a `grep` of disassembly text — that gave a confident false positive here.
- Constant variables force **type-level** enforcement. That is *wider* than the intent: it also
  forbids legitimate members of the same type (here, pure helper *methods*).
- When enforcement is wider than the stated rule, **narrow the prose to what the mechanism can
  enforce** and state the over-reach in the rule body. Never leave an ADR claiming a precision the
  test does not have — a rule quietly wider than its row is more dangerous than a missing rule.
- Record measured blind spots (`case` labels) in both the rule and the ADR. An undocumented gap is
  indistinguishable from a bug later.
- Design remedy that keeps the rule honest: move the value into a **value object** carried inward
  (`PromptBudget`, `SkillBudget`). The domain then receives a resolved quantity instead of reading a
  default, and the rule's target type never appears.

## Example

```
$ javap -v probe/Reader.class | grep -E "sipush|Fieldref|= Class"
   #7 = Class    #8    // probe/Limits     <-- ghost entry, referenced by nothing
        3: sipush  10000                   <-- the value; no Fieldref anywhere
```

## History
- 2026-08-06 (rearchitecture/t30): initial — discovered while implementing Rule 8; caused the rule's
  declared scope to be narrowed from "limit constants" to "the `ConfigDefaults` type".

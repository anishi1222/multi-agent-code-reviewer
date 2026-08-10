# Constant Variables Are Invisible To Bytecode Liveness Checks

`static final int X = 64 * 1024;` is inlined by `javac` and emits no `Fieldref`, so any "is this constant still used?" check built on classfile analysis reports zero references for a constant used everywhere.

## What Happened

`multi-agent-code-reviewer` / t18.2, closing SEC-H1 (five limit constants declared but never
consulted). To prove the fix, I needed a test that fails if a constant goes dead again.

The obvious implementation — scan the constant pool for references — is structurally incapable of
this. Under JLS §4.12.4, a `static final` field of primitive or `String` type with a constant
initialiser is a *constant variable*: the compiler substitutes the literal value at every use
site. No `Fieldref` is emitted, so a classfile scan of a codebase that uses the constant in twenty
places finds nothing. A bytecode-based liveness check would have passed on the original SEC-H1
code and passed again after I re-broke it.

The same property has a second, useful consequence: `AgentDefinitionPolicy` calls
`AgentTrustProfile.forSource(...)` while `AgentTrustProfile` reads `AgentDefinitionPolicy.MAX_*`,
and there is **no static-initialisation cycle**, because those reads compile to literals and
trigger no class initialisation. That only holds while the referenced members are constant
variables — `Set.of(...)` is not one, and referencing such a field would create a real cycle.

## What To Do

- Test constant liveness by scanning **source text**, not bytecode.
- **Exclude the declaration line**, matched via `static final` plus `NAME\s*=`, and exclude comment
  lines. Counting the declaration is exactly how a dead constant looks alive — it was the original
  SEC-H1 camouflage.
- Add a self-check so the scanner cannot pass vacuously: assert it returns 0 for a fabricated name
  and > 0 for a name you know is used.
- When two classes reference each other's constants, check whether the members are constant
  variables before concluding there is an initialisation cycle — and leave a comment, because the
  next reader will assume there is one.

## Why It Matters

The liveness test caught me recreating SEC-H1 *inside the task fixing it*: I moved the constants to
a new owner, then wrote the profiles with literals (`16 * 1024`). Numerically right, and the
constants still had zero references. It failed 6 of 8 and named each one. Had it been built on
bytecode, it would have passed and the task would have reported the finding closed.

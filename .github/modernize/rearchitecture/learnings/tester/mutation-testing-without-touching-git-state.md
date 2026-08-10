# Mutation Testing Without Touching Git State

How to prove a test is non-vacuous by mutating production source when your charter forbids retaining production changes — and when another agent may hold uncommitted work in the same worktree.

## What Happened

Project: `anishi1222/multi-agent-code-reviewer` — Ports & Adapters rewrite, task `t25` (tester).

I needed to prove that two restored assertions actually kill regressions, not merely pass. That
requires a real mutant. But the tester charter forbids *retaining* production changes, and midway
through the task I discovered a **concurrent agent was writing production source in the same
worktree**. The reflex cleanup — `git checkout -- <file>` — would have silently destroyed their
uncommitted work.

The protocol that is safe under both constraints:

```bash
cp -p Foo.java /tmp/Foo.java.bak          # 1. byte-exact backup, preserves mtime
python3 - <<'PY'                          # 2. mutate; ASSERT it landed
s = open(p).read(); s2 = s.replace(OLD, NEW)
assert s2 != s, "mutation did not apply"
open(p,'w').write(s2)
PY
./mvnw -B test -Dtest=FooTest             # 3. expect exit 1 (killed)
cp -p /tmp/Foo.java.bak Foo.java          # 4. restore from BACKUP, not git
shasum -c /tmp/Foo.sha && git diff --quiet -- Foo.java   # 5. double verification
```

Two traps this protocol closes:

1. **`git checkout --` is not safe in a shared worktree.** Backup-copy restore is correct
   regardless of what else is uncommitted, and it restores to the *pre-mutation* state rather than
   to HEAD — which are different files when someone else is mid-edit.
2. **A silently failed mutation reads as a surviving mutant.** My first attempt used
   `perl -0pi -e 's/…/…/'` against a Java regex literal full of `\\d` escapes. It reported success,
   changed nothing, and the tests passed — which I would have recorded as "mutant survived, test is
   weak." The empty `git diff` I printed immediately after applying is the only thing that caught
   it.

## Takeaway

- Restore mutants from a **`cp -p` backup**, verified by `shasum -c` **and** `git diff --quiet`.
  Never `git checkout --` when the worktree may hold another agent's uncommitted work.
- **Assert the mutation applied** before running the suite (`assert s2 != s`). Prefer `python3`
  literal `str.replace()` over `sed`/`perl` for anything containing regex or escape sequences.
- Design mutants so their **kill sets differ**: one that removes the behaviour (kills only the
  positive arms) and one that inverts the guard (additionally kills the control arm). Differing
  kill sets are what proves the control arm is load-bearing rather than passing for free.
- State plainly in the artifact that **no production change was retained**, with the verification
  command as evidence — a reviewer cannot otherwise distinguish "mutated and restored" from
  "mutated and forgot".

## History
- 2026-08-06 (multi-agent-code-reviewer/t25): initial

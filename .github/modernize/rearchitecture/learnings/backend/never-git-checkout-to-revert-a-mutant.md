# Never Use `git checkout <file>` to Revert a Mutant

In a worktree with uncommitted work, `git checkout <file>` reverts to HEAD — silently destroying your in-progress edits, not the mutant you applied.

## What Happened

`multi-agent-code-reviewer` / t27. I scripted a mutation-testing loop as
`sed -i '' <mutate>` → run tests → `git checkout <file>`. The mutants were applied on top of my
*uncommitted* implementation, so `git checkout` restored HEAD and wiped the implementation and
the `application.yml` edit both.

The failure was not loud. The next mutant in the sequence then ran against the original code and
reported a plausible-looking failure in an unrelated test, which I nearly recorded as a genuine
kill. The tell was that the failing test had nothing to do with the mutated line.

## Takeaway

Copy the files to a scratch directory first and restore from the copy:

```bash
mkdir -p /tmp/bak && cp $FILE /tmp/bak/
mutate() { sed -i '' '...' $FILE; }
restore() { cp /tmp/bak/$(basename $FILE) $FILE; }
```

Two supporting habits:

- **Run a baseline through the same harness before the first mutant.** A green baseline proves
  the restore path works; without it, a broken restore looks like a mutant kill.
- **Sanity-check that the mutant's kill is topically related to the mutated line.** An unrelated
  failing test usually means the harness, not the code, changed.

This matters more in a co-tenant worktree where other agents hold uncommitted edits: a careless
`git checkout` can destroy *their* work too, and `git status` will not tell you it happened.

## History

- 2026-08-06 (multi-agent-code-reviewer/t27): initial

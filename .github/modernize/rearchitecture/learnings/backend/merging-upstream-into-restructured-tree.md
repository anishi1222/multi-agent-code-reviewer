# Merging Upstream into a Restructured (Flat→Layered) Tree

When a branch has restructured the package tree, the dangerous merge conflicts are the ones git resolves *silently* — `DU` conflicts and clean auto-merges — not the ones it marks.

## What Happened

Project: multi-agent-code-reviewer, task t23. Merged `origin/main` (+36 commits) into a branch that had deleted the
flat technical-concern tree (`agent/ cli/ config/ orchestrator/ report/ service/ util/`) and rewritten it as Ports &
Adapters layers. `main` had meanwhile kept the flat tree and added features to it. 82 conflicts.

The stated rule that made it tractable: **structure from ours, behaviour from theirs.** Declaring it up front
prevented inconsistent per-file judgement calls.

Three classes of silent loss appeared, none of which produce a conflict marker:

1. **`DU` (deleted by us, modified by them)** — 45 files. Resolves "cleanly" by keeping the deletion, silently
   discarding whatever upstream changed. A per-file diffstat audit found **3 features not mentioned in any commit
   message**. Trusting the commit log would have lost all three.
2. **Clean auto-merges** — git auto-merged a test file (no conflict at all) and dropped a test covering a capability
   we deliberately retained. Found only by diffing per-file `@Test` counts against `HEAD`.
3. **Auto-merged imports crossing layer boundaries** — twice, git inserted an `infrastructure` import into `domain`.
   Both compiled fine; only the architecture test would have caught them.

Runtime assets were a fourth blind spot: templates load *by path*, so a dropped template edit yields no compile error
and no test failure.

## Takeaway

When merging upstream into a restructured tree:

1. **State the resolution rule before touching anything** ("structure from X, behaviour from Y") and apply it uniformly.
2. **Audit every `DU` file with a real diff** against merge-base before accepting the deletion. Never rely on commit
   messages to enumerate what upstream changed.
3. **Audit clean auto-merges too.** Reconcile per-file test-annotation counts (`@Test`/`@ParameterizedTest`) between
   `HEAD` and the working tree. If the annotation delta matches the executed-test delta, the arithmetic is trustworthy
   *and* it pinpoints any file that silently lost a test.
4. **Grep for layer violations after auto-merge**, don't just trust compilation:
   `grep -rnE "^import (…infrastructure|io\.micronaut|…)" src/main/java/**/domain src/main/java/**/shared`
5. **Prove runtime assets landed** — `git diff MERGE_HEAD -- templates/ agents/ src/main/resources/` must be empty.
   Compilation says nothing about path-loaded resources.
6. **For whole-file "take ours", use `git show :2:<path> > <path>`.** Line-based resolvers (drop everything between
   `=======` and `>>>>>>>`) mis-interleave code when one side hoisted a block out of a nested class — the braces stop
   balancing and you get a confusing `';' expected` cascade. Line-based is only safe when both sides share block structure.
7. **Verify merge state with `git rev-parse -q --verify MERGE_HEAD`**, not `cat .git/MERGE_HEAD` — in a *worktree*
   `.git` is a file and the `cat` silently returns nothing, which looks exactly like a lost merge.
8. **Don't split a merge across agents.** The index is shared mutable state; partitioning destroys rename detection.

## Example

```bash
# Audit every DU file before accepting its deletion
git diff --diff-filter=DU --name-only MERGE_BASE MERGE_HEAD | while read f; do
  git diff --stat "$MERGE_BASE" "$MERGE_HEAD" -- "$f"   # what did upstream actually change?
done

# Catch tests silently lost to a CLEAN auto-merge
# (compare per-file @Test counts HEAD vs working tree; investigate every negative delta)

# Prove path-loaded runtime assets survived
git diff MERGE_HEAD -- templates/ agents/ src/main/resources/   # must be empty
```

## History
- 2026-08-06 (multi-agent-code-reviewer/t23): initial — 82 conflicts, 6 features ported, 2 silent regressions caught

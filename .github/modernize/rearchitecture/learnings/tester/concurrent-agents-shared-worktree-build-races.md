# Concurrent Agents in One Worktree Produce Fake Test Failures

Mass `NoClassDefFoundError` and a test count that drops *below* baseline are the fingerprint of a shared-`target/` build race, not a regression.

## What Happened

Project: `anishi1222/multi-agent-code-reviewer` — Ports & Adapters rewrite, task `t25` (tester).

I ran a verification `mvn -B clean verify` and got a catastrophic-looking result:

```
Tests run: 924, Failures: 23, Errors: 184, Skipped: 0
```

Against a **942** baseline. Every one of the 184 errors was `NoClassDefFoundError` — including for
`ReviewOverallSummaryAppender`, a class untouched by me whose `.java` **and** `.class` both existed
on disk, and including tests that predate my change entirely.

The cause was not my code. A different agent was running its own Maven build in the same worktree.
`clean` deleted `target/classes` out from under the other JVM (and vice versa), so classloading
failed at random. Separately, that agent's in-flight source edit broke `testCompile` mid-run
(`cannot find symbol: variable ConfigDefaults`), aborting a run that had nothing to do with me.

Re-running once `ps` showed no competing Maven process: **962 / 0 / 0 / 0, exit 0.**

## Takeaway

Before believing a red suite in a multi-agent worktree, check three tells:

1. **Did the total count drop below baseline?** 924 < 942 means classes *aborted*, not that tests
   started failing. A genuine regression keeps the count and moves passes into failures.
2. **Are the errors `NoClassDefFoundError` for classes that exist on disk?** That is a classloading
   race, not a compile or logic error.
3. **Are pre-existing, untouched tests failing?** Your change cannot explain those.

Then: `ps` for a competing build, wait, and re-run on a quiet tree. Also re-check `git status`
immediately before any destructive step — a worktree clean at preflight may not be clean 10 minutes
later, and `git checkout`/`git stash` will eat another agent's uncommitted work.

Finally, when reporting: if the tree contained another agent's in-flight changes, the absolute test
total is **not** a clean single-task number. Report your own delta separately and reconcile it
exactly (declared `@Test` added/removed per file), then state the caveat.

## History
- 2026-08-06 (multi-agent-code-reviewer/t25): initial

# Bulk Test Migration With Parallel Sub-Agents

Pattern for mechanically relocating a large test suite onto rewritten packages and fixing the resulting compile-error avalanche in parallel.

## What Happened

`multi-agent-code-reviewer` / t13. 148 test files had to move onto new layer packages, then be
adapted to substantial API drift. Two things dominated the effort.

**javac's 100-error cap hid most of the work.** `mvn test-compile` stops reporting at 100 errors, so
each round of fixes revealed an entirely new set of broken files. It took **five waves** to converge.
Early estimates based on the first error list were off by ~60%.

**Sequential fixing was untenable**, so I dispatched 15 sub-agents across the waves. The dispatch
recipe that worked:

- `mode: "background"` on **every** `task()` call — the default is sync, which serialises everything.
- **Disjoint file ownership**, grouped by package, stated explicitly.
- A pre-exported classpath at `/tmp/cp.txt` (`mvn dependency:build-classpath` once, up front).
- An explicit **"do NOT run maven"** instruction — parallel Maven runs fight over `target/`.
- A standalone verify command each agent could run in isolation:
  `javac --release 27 -Xmaxerrs 500 -d <own scratch dir> -cp "target/classes:$(cat /tmp/cp.txt)" -sourcepath src/test/java <its files>`
- **"Ignore errors in files that are not yours"** — otherwise agents chase each other's breakage.
- A requirement to **justify every deleted test method**, so deletions can't be used to force green.

## Takeaway

- Build the legacy→new FQN rename map first and drive the move with a script; resolve ambiguous
  duplicate simple names to the public/canonical class explicitly.
- **Always re-run `test-compile` to convergence.** Never size the work from the first error list.
  Use `-Xmaxerrs 500` for standalone `javac`.
- Give each parallel agent its own `-d` scratch directory (prefer `target/<name>out` over `/tmp`) so
  they don't corrupt each other's output.
- Tests that no longer have a SUT ("orphans") should be stashed aside during the bulk move and
  triaged individually afterwards — several turn out to cover classes that have **no other tests**
  and are worth porting rather than dropping.
- Count results from `<testcase>` elements in the surefire XML; both the `.txt` files and the XML
  `tests` attribute under-report (the attribute misses `@Nested`/parameterized containers).

## History
- 2026-08-05 (multi-agent-code-reviewer/t13): initial

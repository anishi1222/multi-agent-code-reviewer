# Controls Need A Negative Control Before You Trust Them

A rule or guard with zero observed failures is indistinguishable from one whose predicate is broken — applies to ArchUnit rules and to runtime control branches alike.

## What Happened

`multi-agent-code-reviewer` / t12.1 → t13.1. Two separate incidents on the same enforcement suite:

- **t12.1:** ArchUnit's shaded ASM tops out at class-file major 69 (`V25`); this project compiles
  `--release 27` → major 71. ArchUnit swallowed the parse error and silently imported 107 of 687
  classes, all Micronaut synthetics. Every rule passed **vacuously** for the whole project.
- **t13:** the `presentation ⊥ infrastructure` rule from the target architecture was never written
  at all. Nothing failed, because there was nothing to fail.

Both failure modes look identical from the build log: green. The suite was rebuilt on JDK-native
`java.lang.classfile` (JEP 484), and t13.1 added the missing rule *plus* a reproducible negative
control proving it fires.

## Takeaway

1. **Every new architecture rule ships with a recorded negative control.** Inject a real
   violation, capture the failure output naming the offending class, revert, re-confirm green.
   Paste all three into the task artifact.
2. **Inject a bytecode reference, not just an import.** Unused imports may be erased; a
   `static final Class<?> PROBE = Offender.class;` field guarantees a constant-pool entry.
3. **Back up the mutated file to `/tmp` and verify `git diff` is empty after restoring.** Never
   leave the probe behind.
4. **Assert violators *equal* the exemption set, not that violators are a subset of it.**
   Subset assertions pass when the subject set is empty — which is exactly the vacuous-pass bug.
5. **Assert the subject set is non-empty and that the parsed class count equals the on-disk
   `.class` count**, plus a few named anchor classes. This is what catches a silently-truncated
   import.
6. **Check any bytecode-inspecting library's shaded-ASM ceiling before adopting it**, especially
   on early-access//preview JDKs.
7. **The same discipline applies to runtime controls, not just ArchUnit rules** (t26). Disable the
   production branch (`if (false && …)`), re-run, confirm the test goes red, revert, confirm green.
8. **When several tests cover several controls, prove the mutants are killed by *disjoint* tests.**
   A suite that turns red tells you *something* caught the mutation, not that each test is doing
   work. Build a kill matrix (mutant × test); a test that kills no mutant is vacuous, and two tests
   that only ever die together are one test.
9. **Reaching the branch may require a specific shape, not just a big input.** t26's cumulative
   budget was mathematically unreachable with a single item, because an earlier per-item gate
   already bounded it. Prove reachability algebraically before assuming a test exercises the branch.
10. **A mutant that kills *more* tests than its blast radius should reach signals a fixture defect,
    not strong coverage** (t29). A mutant confined to one branch killed all three tests of a suite —
    because all three fixtures happened to take the *same* path, leaving the other path untested.
    Over-killing means the fixtures lack diversity. Split the fixture so the paths are parameterised,
    then confirm the mutant and its inverse are killed by *disjoint* sets.
11. **Pair each mutant with its inverse when guarding a two-path attach point** (t29). "Attach only
    on path A" and "attach only on path B" must be killed by complementary test sets. Either mutant
    alone can be killed by a suite that never exercises the other path.
12. **Predict each mutant's kill set before running it, then compare.** t29 predicted algebraically
    that the mutant re-introducing the original defect would *survive* the obvious regression test
    (both the fixed and mutated code drop the same oversized input). Confirmed empirically. Where
    prediction and result agree you have understood the control; where they disagree you have found
    either a fixture defect or a misunderstanding — both worth more than a green suite.

## Example

```
# mutation
[arch] Rule 5b (presentation ⊥ infrastructure)  69 classes inspected, 1 violator(s), 0 exempt
 ==> expected: <[]> but was: <[dev.logicojp.reviewer.presentation.CliOutput]>
[INFO] BUILD FAILURE

# after revert
[arch] Rule 5b (presentation ⊥ infrastructure)  69 classes inspected, 0 violator(s), 0 exempt
[INFO] BUILD SUCCESS
```

## History
- 2026-08-05 (multi-agent-code-reviewer/t13.1): initial — consolidates the t12.1 vacuous-pass finding with the t13 missing-rule finding.
- 2026-08-06 (multi-agent-code-reviewer/t26): generalised from architecture rules to any control; added the disjoint kill-matrix requirement and the branch-reachability check (takeaways 7–9).
- 2026-08-06 (multi-agent-code-reviewer/t29): added takeaways 10–12 — over-killing mutants as a fixture-defect signal, inverse-mutant pairing for two-path attach points, and predicting kill sets before running.

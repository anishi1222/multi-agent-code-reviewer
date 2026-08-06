# Shared Worktree Invalidates Build Results

When more than one agent writes to the same worktree, a concurrent `mvn clean` produces test failures and compile errors that look real and are not — verify in an isolated copy before reporting any build result.

## What Happened

`rearchitecture/t30`. A full `clean verify` had already passed at 965 tests / 0 failures. A later
re-run of the *same* tree reported `Tests run: 969, Failures: 1, BUILD FAILURE`, and a follow-up run
failed even earlier with a cascade of:

```
cannot access StructuredConcurrencyUtils
  bad class file: target/classes/.../StructuredConcurrencyUtils.class
    unable to access file: java.nio.file.NoSuchFileException
deconstruction patterns can only be applied to records, GitHubTarget is not a record
```

Those classes had been compiled successfully seconds earlier in the same build (`Compiling 176
source files … to target/classes`, no errors). `git status` explained it: a second agent was editing
the same worktree (`PromptBudgetConfig.java`, `application.yml`, a new binding test — which is also
where the +4 tests came from). Their `mvn clean` deleted `target/classes` between my build's
compile and test-compile phases.

Both symptoms were pure collision artifacts. Re-run in a pristine copy: **969 tests, 0 failures,
BUILD SUCCESS.**

## Takeaway

- A build failure that contradicts a green run minutes earlier, on unchanged code, is a **process
  problem until proven otherwise**. Check `git status` and `ps` for other writers *before* debugging
  the code.
- `NoSuchFileException` / `bad class file` on classes the same build just wrote, or nonsense
  diagnostics like "X is not a record" for a type that plainly is, are signatures of a `target/`
  deleted mid-build — not of a real defect.
- Never report test counts in an artifact or `[DONE]` from a shared worktree without isolating:
  `rsync -a --exclude target/ --exclude .git/ ./ /tmp/<task>iso/` and build there. It costs one build
  and makes the number trustworthy.
- Do not "fix" failures in files you did not touch — they may be another agent's work in flight.
- Escalate the collision itself; the real fix is one worktree per agent, or a serialised build.

## History
- 2026-08-06 (rearchitecture/t30): initial — cost three builds and a phantom regression before
  `git status` revealed the second writer.

# Verify clarification.md Against the Repo Before Trusting It

The canonical scenario record can go stale mid-run — reconcile its factual claims against the actual build files, and never "correct" the repo to match it without checking provenance first.

## What Happened

In multi-agent-code-reviewer/t26 the coordinator added `clarification.md` to the task
dependencies after delivery. Reconciling it line-by-line against the repo, one claim was
false: it stated the target framework was **Java 26 (GraalVM 26 EA)**, but `pom.xml`
declared `<java.version>28</java.version>` with `<release>${java.version}</release>`.

The tempting reactions were both wrong:
- "The canonical record wins → downgrade the pom to 26" — would have caused a real
  runtime regression *and* violated the record's own "no dependency/runtime version
  upgrades" out-of-scope rule.
- "The record is just wrong → ignore it" — would have hidden a genuine process defect.

Checking git provenance settled it in three commands: merge-base was **27**, `origin/main`
is **28**, and the 27→28 bump was authored by the *repository owner* before the run and is
an ancestor of `origin/main`. So the drift arrived legitimately via the Upstream Merge
phase — **no worker misbehaved** — and the record (generated mid-run) simply never saw it.

Separately, the same file *strengthened* an unrelated ruling: its "`application.yml` keys
may break only when justified by architecture quality, with ADR and migration notes" clause
independently mandated the ADR escalation that had been chosen on charter grounds alone.

## Takeaway

1. **Reconcile, don't just read.** Treat every factual claim in `clarification.md` (framework
   version, module layout, test posture) as a claim to check against the build files. It is
   generated at a point in time; Upstream Merge phases can invalidate it afterwards.
2. **On a conflict, check provenance before assigning blame or "fixing" anything.**
   `git show <merge-base>:file`, `git show origin/main:file`, and
   `git merge-base --is-ancestor <sha> origin/main` distinguish "a worker broke the rule"
   from "the record is stale" in seconds. The remedies are opposite.
3. **Never hand-edit the record** — it says so itself. Report staleness to the coordinator
   and let the Clarification Gate regenerate it.
4. **Mine it for corroboration too.** It often independently justifies a decision already
   made for other reasons, which converts "my judgement" into "the run's stated policy."

## Example

```bash
git show "$(git merge-base HEAD origin/main)":pom.xml | grep '<java.version>'
git show origin/main:pom.xml                          | grep '<java.version>'
git merge-base --is-ancestor <sha> origin/main && echo "upstream, not a worker"
```

## History
- 2026-08-06 (multi-agent-code-reviewer/t26): initial — from the stale "Java 26" vs actual Java 28 discrepancy.

# Duplicate Utilities Are Rarely Identical — Diff Semantics Before Deleting

Same-named classes in two packages usually diverged; deleting one silently changes behaviour unless you merge the union of their guards.

## What Happened

`multi-agent-code-reviewer` / t13.1. Task said "consolidate the duplicate `ConfigDefaults` and
`RetryPolicyUtils`". Both pairs *looked* like copy-paste leftovers from the layered rewrite.

`ConfigDefaults` genuinely was identical (one copy only added SLF4J debug logging).

`RetryPolicyUtils` was **not**:

| | `infrastructure` copy | `shared` copy |
|---|---|---|
| transient markers | `connection reset`, `timeout`, `unavailable`, `stream closed`, `broken pipe` | `timeout`, `temporarily`, `rate limit`, `too many requests`, `429`, `503`, `connection reset`, `network` |
| `InterruptedException` | explicit `→ false` | **absent** |
| null root cause | null-safe | **NPEs** |

Deleting either copy would have silently changed retry behaviour for its consumers — no
compile error, no test failure, just different production retry semantics under load.
Additionally, `shared/ConfigDefaults` had **zero importers**: it was dead code shadowed by the
infrastructure copy, so "keep the one in the right layer" and "keep the one that's actually
used" pointed at different files.

## Takeaway

1. **Diff the bodies before deleting.** `git diff --no-index <a> <b>` on the two files is the
   first action, not the last. File name equality proves nothing.
2. **Merge the union of guards, not the survivor's subset.** Keep every marker/branch/null-check
   present in *either* copy. Widening behaviour is safe and reviewable; narrowing it silently is
   the failure mode.
3. **Annotate provenance in the merged file** (`// from the infrastructure copy` /
   `// from the shared copy`) so a later reader can tell the merge was deliberate.
4. **Record the widening explicitly** in the task artifact — consumers of the narrower copy now
   retry on more conditions, which is an accepted behaviour change, not a no-op.
5. **Check importer counts before picking the survivor.** `grep -rl 'import …ClassName'` — the
   copy in the architecturally-correct package may be the dead one.

## History
- 2026-08-05 (multi-agent-code-reviewer/t13.1): initial — from the `RetryPolicyUtils` near-miss.

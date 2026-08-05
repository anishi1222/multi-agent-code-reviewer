# Re-Verify Docs Immediately Before Publishing

On a long documentation task, concurrent implementation tasks can invalidate what you already wrote — verify once to write, once to publish.

## What Happened

In `rearchitecture/t16` I authored ADR-0006 from a source sweep taken at the start of the task.
While the ADR was being written, `t13.1` landed and changed three of its claims:

- the displaced-capability port was named **`PropagateCorrelationPort`**, not the `LogExecutionPort`
  the ADR proposed;
- the new architecture rule was numbered **`5b`** (inserted to keep the dependency-direction rules
  contiguous and Rule 6a/6b stable), not the `Rule 7` the ADR proposed;
- two of the seven "Known deviations" were closed (duplicate class names consolidated into
  `shared`; `presentation ⊥ infrastructure` enforced with 0 exemptions).

A final verification sweep run immediately before writing `[DONE]` caught all three. Publishing an
ADR that names a port which does not exist would have been worse than publishing nothing, because
downstream agents treat the ADR as the authority.

## Takeaway

- **Verify twice on documentation tasks:** once to gather material, once immediately before
  publishing. The second sweep is cheap — re-run the same greps.
- Watch `git status` for untracked artifacts from sibling tasks (`t*-<role>.md`, new source files)
  as the signal that a concurrent task landed.
- When a concurrent task chose a different name for something you specified, **adopt the
  implemented name.** The ADR must describe reality; the implementation is the fact.
- Give "Known deviations" tables an explicit **Status** column so later tasks can close rows in
  place instead of the table going stale.
- Do not restate a sibling task's report as fact — re-derive its claims from source. Here the
  report was accurate, but the check cost one command per row.

## History
- 2026-08-05 (rearchitecture/t16): initial — t13.1 landed mid-task and changed 3 ADR-0006 claims

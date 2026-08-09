# Historical Links Require Identity, Not Just a Matching Number

A same-number PR in a surviving repository is not a valid replacement unless its identity matches.

## What Happened

In `multi-agent-code-reviewer` task t36.1, 16 dead PR links pointed at a retired repository.
The current repository had PRs with the same numbers, but their titles and merge dates contradicted
the dated release-note descriptions. Retargeting only the repository segment would have produced
working links to unrelated changes.

## Takeaway

Before a URL-only historical correction, verify repository lineage plus PR number, title, date, and
described change. If identity cannot be established, preserve the historical identifier as explicit
non-link text and state that no replacement was inferred. Never use HTTP 200 alone as proof.

## History

- 2026-08-10 (`multi-agent-code-reviewer`/t36.1): initial

# Unreleased State Does Not Rewrite Release History

Document the live tree in `Unreleased`, and validate each language's dated history against its own immutable baseline.

## What Happened

In `multi-agent-code-reviewer` task t35, the final tree differed substantially from the latest tag.
The English and Japanese release-note histories also had a pre-existing one-heading count
difference, so forcing their complete chronology to match would have rewritten historical records.

## Takeaway

Put source-verified current behavior only in the paired `Unreleased` sections. Locate the first
dated heading and compare every byte from that marker to `HEAD` before publishing. Validate the
dated heading sequence independently per language; require cross-language structure and semantic
parity only for the newly edited current-state content.

## History

- 2026-08-10 (`multi-agent-code-reviewer`/t35): initial

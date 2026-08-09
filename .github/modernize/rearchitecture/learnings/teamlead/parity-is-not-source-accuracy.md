# Parity Is Not Source Accuracy

Perfectly paired documentation can repeat the same functional error, so parity must be followed by
independent source and reference triangulation.

## What Happened

In `multi-agent-code-reviewer` task t36, the English and Japanese READMEs had identical structural
metrics and semantically paired text, but both documented `{{placeholder}}` while production code and
all templates implement `${key}`. A Markdown-link checker also passed every local link while missing
a non-existent repository path formatted as inline code.

The fresh re-gate also showed the opposite failure mode: exact whole-document code-token equality is
too strict when localized history intentionally differs, and a diff opcode count can undercount
adjacent historical substitutions. Exact parity became reliable after limiting it to matched
current-state sections; chronology became reliable after comparing headings, changed lines, and
preserved identifiers rather than opcode totals.

## Takeaway

After EN/JA parity checks, validate operational examples against their parser/helper and live
resource tokens. Extract repository-looking paths from inline code as well as Markdown links, and
probe external historical targets separately. Treat parity as a translation check, never as proof
that the shared claim is true. Use global structural parity plus section-scoped exact tokens and
curated semantic markers. For immutable history, validate identity and line-level substitutions
rather than assuming one diff opcode represents one change.

## History

- 2026-08-09 (`multi-agent-code-reviewer`/t36): initial
- 2026-08-09 (`multi-agent-code-reviewer`/t36 re-gate): added section-aware parity and line-level
  chronology oracles

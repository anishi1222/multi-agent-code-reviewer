# Trust Boundary Severity Calibration

Rate findings by which trust boundary the input crosses, not by pattern name — a user's own CLI flag is not path traversal.

## What Happened

multi-agent-code-reviewer / t18. Sub-agent scans flagged `--output`, `--local`, `--parallelism`
and `--dialogue-rounds` as HIGH path-traversal / resource-exhaustion findings: all are unvalidated
and `--output` reaches a file write with `REPLACE_EXISTING`.

Every one is a **false positive**. This is a single-user local CLI; those values come from the
person running the binary under their own privileges. `--output ../../x` grants them nothing they
could not already do with a text editor. Filing them as HIGH would have buried the two findings
that actually matter.

Mapping the boundaries relocated the real risk. Default agent directories are `./agents` and
`./.github/agents` — **relative to the CWD**, i.e. *inside the repository under review*. Untrusted
markdown from an arbitrary repo becomes system prompts for the LLM. That is where both genuine
HIGH findings were, and no pattern-based scan surfaced it, because nothing about the code *looks*
dangerous.

## Takeaway

Before assigning severity, write down the trust boundaries and label each input source. Then:

- Input that never crosses a boundary → **robustness/UX**, not a vulnerability.
- Input crossing an untrusted boundary → rate on real impact, even if the code looks benign.

For a local CLI, the untrusted inputs are almost never the flags — they are the **data the tool
ingests** (repo contents, config discovered by convention, model output, network responses).

Pay attention to **convention-based discovery relative to CWD** (`./.github/...`, `./config`). It
silently converts "data the user pointed at" into "code the tool trusts", and it is invisible in
diffs.

A review that flags a user's own flag as HIGH trains readers to ignore the report. Precision buys
the credibility that makes the genuine HIGHs land.

## Example

| Input | Boundary | Verdict |
|---|---|---|
| `--output <path>` | user → own CLI | robustness only |
| `./.github/agents/*.md` from reviewed repo | untrusted → LLM instructions | **HIGH** |
| LLM output → report file | untrusted → disk | rate on real impact |

## History

- 2026-08-05 (multi-agent-code-reviewer/t18): initial — reclassified 5 sub-agent "HIGH" findings to LOW and promoted the agent-file path to HIGH.

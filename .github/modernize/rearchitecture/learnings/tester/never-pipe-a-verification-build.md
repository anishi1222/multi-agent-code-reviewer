# Never Pipe a Verification Build Through tail

Piping Maven to `tail`/`head` reports the pipe's exit code, not the build's — a failed build can read as green.

## What Happened

Project: `anishi1222/multi-agent-code-reviewer`, task `t14` (tester), full regression run.

My first regression invocation was `./mvnw -B clean verify | tail -250`. Two problems, both silent:

1. **The exit code was the exit code of `tail`, not of Maven.** `tail` essentially always succeeds,
   so `$?` was `0` regardless of the build outcome. A verdict line reporting `exit_code: 0` from
   that command would have been unfounded — it happened to be a passing build, but I couldn't have
   known.
2. **Diagnostic output was truncated away.** The custom `[arch] Rule …` lines from the hand-rolled
   layer-dependency test were emitted mid-build and scrolled past the 250-line window, so the
   architecture-rule status simply wasn't in my captured output.

Rerun as redirect-to-file with an explicit exit-code capture, both problems disappeared.

A second, unrelated shell trap in the same task: `grep -E` uses `|` for alternation, **not** `\|`.
My first behavior-ID gap sweep used `\|` and returned zero hits for every pattern — which reads
exactly like "no coverage found anywhere" instead of "your regex is wrong". Every ID would have
been reported as a gap.

## Takeaway

For any build whose exit code you intend to report:

```bash
./mvnw -B clean verify > /tmp/build.log 2>&1; echo "MAVEN_EXIT_CODE=$?" | tee /tmp/rc.txt
```

Then `grep` the file for what you need. Rules:

- Never pipe a command whose exit code is part of your evidence. Capture `$?` immediately, on the
  same line, before anything else can overwrite it.
- Prefer a full log file over a truncated stream — custom instrumentation (`[arch]`, coverage
  summaries, enforcer output) rarely lands in the last N lines.
- When a broad `grep -E` returns zero hits across *every* alternative, suspect the regex before
  believing the result. `\|` is the BRE syntax; `-E` wants bare `|`.
- On macOS there is no `timeout` binary — don't build verification harnesses around it.

## History
- 2026-08-05 (multi-agent-code-reviewer/t14): initial

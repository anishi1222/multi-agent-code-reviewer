# Offline CLI Smoke and Live Doctor Need Different PATH Contracts

Offline entry-point smoke tests should isolate `PATH`, while live `doctor` validation must retain the external Copilot CLI prerequisite.

## What Happened

In `multi-agent-code-reviewer` task t22.2, help, version, list, and skill-list correctly passed
with `PATH` restricted to a fresh working directory. Reusing that environment for `doctor`
returned code 4 and reported an unhealthy Copilot client because the probe intentionally checks
the external CLI.

With the real `PATH`, both the shaded JAR and native executable returned 0, reported a healthy
client, and printed `All checks passed.`.

## Takeaway

Treat `doctor --help` as an offline packaging surface and `doctor` as an environment integration
probe. For live doctor evidence, verify the prerequisite first, preserve its command-resolution
path, and assert both return code 0 and the healthy-client summary.

## History

- 2026-08-09 (`multi-agent-code-reviewer`/t22.2): initial

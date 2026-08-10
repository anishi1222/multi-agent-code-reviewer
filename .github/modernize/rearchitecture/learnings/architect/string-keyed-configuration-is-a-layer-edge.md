# String-Keyed Configuration Is a Layer Edge

A configuration key named by a leaf layer is an architectural dependency even when no owner type is imported.

## What Happened

In multi-agent-code-reviewer t32.1, Rule 5b prohibited presentation-to-infrastructure type edges
but missed an incorrect `@Value` key because the coupling existed only as a string. Three similar
presentation bindings remained, including keys with the wrong prefix, path, or default.

## Takeaway

Do not repair this class of defect by correcting the string. Route effective, already-normalized
values from the configuration owner through an application boundary, then let presentation apply
only explicit user overrides. Enforce the rule against the framework's complete
configuration-binding annotation packages, with a source-backed subject and a permanent detection
fixture.

## History

- 2026-08-08 (multi-agent-code-reviewer/t32.1): initial


# Prefer Exact-Member Native Reflection Metadata

Native-image fixes should register only the constructors and accessors actually used, and must be verified by the full native test image.

## What Happened

In `multi-agent-code-reviewer` t33, five native-test failures followed a layered package
rewrite. Two metadata copies still named 18 deleted `cli` bean definitions, while reflective
tests queried record components and package-private accessors without metadata.

Replacing the bean-definition names and registering exact record accessors fixed the reported
failures. The first full rerun then exposed one more dynamically-invoked accessor,
`InstructionFrontmatter$Parsed.body()`, hidden behind the earlier failures. Adding only that
method made all 1,058 native tests pass.

## Takeaway

- Resolve Micronaut definition names against generated `target/classes`, not package guesses.
- Keep duplicate reachability files structurally identical and assert that before building.
- Prefer explicit `methods` and constructor descriptors over `allDeclaredMethods` or blanket
  class registration.
- Run the exact unskipped native gate after every metadata change; an earlier reflection error
  can hide the next missing member.

## History

- 2026-08-07 (multi-agent-code-reviewer/t33): initial

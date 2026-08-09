---
title: Source-backed architecture-rule subjects
role: backend
tags: [java, architecture-tests, bytecode, mutation-testing]
created: 2026-08-07
last_updated: 2026-08-07
---

# Source-backed Architecture-rule Subjects

## Context

A bytecode rule can pass vacuously when its package filter stops selecting
classes, even if a simple `subjects.isEmpty()` guard remains green because
generated or synthetic classes still match.

## Decision

- Enumerate production `.java` files independently of compiled bytecode.
- Derive each source file's expected primary FQN from its path.
- Assert every expected primary FQN appears in the rule's compiled subject set.
- Keep an exact forbidden-edge fixture under `src/test/java` and prove that the
  same analyzer reports its owner and target.
- Analyze real production rules from `target/classes`; load controls only from
  `target/test-classes`.

## Why

This combines breadth proof (every intended source type is analyzed) with
detector proof (the forbidden edge is actually rejected) without introducing
production exemptions or polluting the production graph.

## Reuse

Apply this pattern to every zero-violation architecture rule whose subjects are
selected by package or class-file location.

## History

- 2026-08-07: established for t17.1 Rules 3a and 4a.

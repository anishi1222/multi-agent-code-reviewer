## [t18] Security review — 2 HIGH, 6 MEDIUM, 9 LOW; both HIGHs in the untrusted-agent-file path

**Learnings consumed:** backend/defensive-copy-strips-security-wrapper, backend/redacted-literals-compare-by-hash, backend/cve-pin-can-itself-be-vulnerable, backend/correlation-logging-port

### Wrong assumption I had to correct mid-task
I initially accepted the sub-agent framing that `--output` path traversal was HIGH. It is not —
this is a single-user local CLI, so CLI flags are *trusted* input. Mapping trust boundaries
demoted 5 "HIGH" findings to LOW and simultaneously promoted the real risk: default agent dirs are
`./agents` / `./.github/agents`, **relative to CWD**, so agent markdown is loaded from inside the
reviewed repo and becomes LLM instructions. Both genuine HIGHs live there. Pattern-matching scans
missed it entirely because nothing about that code *looks* dangerous.

### What actually worked
- **Runtime probe over source reading.** Source review rated `SensitiveHeaderMasking` as solid.
  A canary probe showed it masks 3 of 10 access paths — `forEach`/`getOrDefault` inherit `Map`
  defaults that `AbstractMap` never overrides, and any copy strips the wrapper entirely. The t13
  learning said "verify at runtime"; it was right, and reading alone would have under-rated this.
- **Grep-count-equals-one** to detect dead security controls. Five constants in
  `CustomInstructionSafetyValidator` each appear exactly once — at their own declaration. Third
  instance of this failure shape on this project (after vacuous ArchUnit and deleted MDC tests).
- **Negative controls on the CVE scan.** 0 findings is uninterpretable alone; two known-bad
  coordinates returning 6 and 9 proved the query path was live.
- **Git history to distinguish gap from regression.** `git log -S "REDACTED"` returned nothing,
  so the missing report redaction is pre-existing, not something the rewrite dropped. That
  materially changes its priority and kept me from mis-filing it as a t13.1-G2-style regression.

### Gotchas for future tasks
- Compiled classes are major version 71 (Java 27); `javac`/`java` from `~/.sdkman/candidates/java/27.ea.32-open`
  are required for any probe against `target/classes`.
- The t12.1 tooling constraint binds security work: no bytecode SAST. Coordinate-based OSV queries
  sidestep it because they never parse a class file.
- `project-profile.yaml:19` claims Mustache-style `{{placeholder}}` templates. Stale — the real
  syntax is `${key}` and no Mustache engine is on the classpath.
- Large sub-agent output spills to `/var/folders/.../T/*.txt`; read it with `view` + `view_range`.
  `cat` re-spills it into a *new* temp file and wastes a round-trip.

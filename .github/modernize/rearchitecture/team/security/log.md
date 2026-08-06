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

## [t18 re-run] Verified F1 closed, found the same defect class alive in a different range of the same constant

- **The re-run's value was not in re-reading F1's fix — it was in asking F1's *question*
  of the other 14 ranges.** F1 narrowed `\u2000-\u206F` by hand and got it exactly right
  (0 leaks of 21 target codepoints). But `\uFF00-\uFFEF` admits U+FFA0 HALFWIDTH HANGUL
  FILLER, which is blank-rendering *and* invisible to the denylist. A hand-fix to one range
  left the identical hole one block over.
- **Copy the constant verbatim into the probe, never re-describe it.** I pasted the regex
  byte-for-byte from source. Had I retyped it from the intent, I would have "fixed" the
  range boundaries while transcribing and proved nothing about what ships.
- **Exhaustive beats clever.** I first tried to reason about which blocks were risky and
  had a nice hypothesis about Java's `$` and line terminators (see below). Sweeping all
  65,536 BMP codepoints and classifying by Unicode category took one probe and found the
  real thing immediately. For a finite input domain, enumerate it.
- **Dead end worth recording: the `$`/line-terminator hypothesis was wrong.** `^[...]*$`
  with `DOTALL` but not `MULTILINE` — Java's `$` matches before a *final* line terminator,
  and U+2028/U+2029/U+0085 are Java line terminators excluded from the allowlist. Looked
  like a clean trailing-position bypass. It is not: `Matcher.matches()` requires full-region
  consumption, so `$` never gets to be lenient. Plausible mechanism, false conclusion —
  settled in one probe. Do not re-derive it.
- **`\p{Cf}\p{Cc}` is not "the invisible characters".** The nastiest blank-rendering
  codepoints (U+115F, U+1160, U+3164, U+FFA0, U+2800) are category `Lo` — *letters*. Any
  strip built on `Cf`/`Cc` misses them by construction. Worse, NFKC maps U+FFA0 → U+1160,
  i.e. one invisible `Lo` filler to another, so normalisation does not help either.
- **The green suite was load-bearing in the wrong direction.** `ALLOWED_CHAR_RANGE`'s only
  pin asserts a single codepoint (`U+202E`). A one-codepoint test on a 33,478-codepoint
  allowlist reads as coverage and is not. Check what a passing test actually ranges over.
- **Sub-agent severities were inverted and unusable.** Its CRITICAL was a non-issue
  (model-name prefixes) and its one real item was rated equal to three false ones. Verified
  every candidate myself and downgraded 4 of 5. Delegate the *search*, never the *rating*.
- **Do not overwrite an artifact that upstream cites by name.** `t18-security.md` is
  referenced by `t18.1-architect.md` and ADR-0007; the re-run went to a new file.
- Learnings consumed: [security/trust-boundary-severity-calibration,
  security/dead-security-controls, security/cve-scan-non-vacuity-controls,
  security/masked-map-accessor-matrix, architect/shared-worktree-invalidates-build-results,
  architect/constant-variables-defeat-bytecode-liveness, backend/never-pipe-a-verification-build,
  tester/concurrent-agents-shared-worktree-build-races]

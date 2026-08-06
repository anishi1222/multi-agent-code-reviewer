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

## [t18] Security gate re-run 2 — PASSED; SEC-H3 closed via external Unicode oracle
- The useful move was refusing to re-derive what production and its test already agreed on.
  Both pin/derive invisibility from `Character.getName()`. Two mechanisms, one definition — a
  definition error is invisible to both. So I tested the definition from outside it: fetched
  `DerivedCoreProperties.txt` from unicode.org and used `Default_Ignorable_Code_Point`, the
  standard's own canonical "renders as nothing" property. 0 of 4,174 admitted. That is a
  population result, not a sample, and it is what let me *rule* on the residual instead of
  deferring on it.
- Java regex does NOT support `\p{IsDefault_Ignorable_Code_Point}` (PatternSyntaxException).
  It does support `IsWhite_Space`, `IsNoncharacter_Code_Point`, `IsAssigned`, `IsJoin_Control`.
  Parsing the UCD file directly is ~20 lines and gives any derived property, not just those four.
- Biggest calibration save: my first sweep's "renders blank" classifier (NFKC → all-whitespace)
  flagged 15 codepoints as denylist-defeating. **The first was U+0020 SPACE.** That is the tell
  the classifier measured "breaks a keyword", not "invisible". Had I sorted output by codepoint
  and not looked at the head of the list, I would have filed a HIGH on plain ASCII space. Always
  put the most boring possible input through your own classifier and check it lands where you
  expect.
- Corollary worth keeping: NFKC folding is *protective*, not merely neutral. `ignore<Zs>all
  previous instructions` still fires 15/15 because every admitted `Zs` folds to U+0020 before
  matching. I nearly recorded the fold as a neutral fact; testing the word-boundary position
  turned it into evidence for the defence.
- Only 1 of 6 pinned fillers (U+FFA0) is reachable through `ALLOWED_CHAR_RANGE`; the other 5 are
  already outside the ranges. Checking reachability *before* arguing about set correctness cut
  the residual surface by 5/6 and cost one loop.
- Wrong assumption I corrected mid-task: I expected `target/classes` to exist and planned to probe
  against it. It did not. Compiling a byte-identical copy of the validator in the same package in
  /tmp was better anyway — package-private constants readable without reflection, and `cmp` proves
  the copy is the shipped artifact.
- JDK trap: the project targets `release 28`. JDK 25 gives `error: release version 28 not
  supported` from maven-compiler-plugin. Use `28.ea.9-open` for anything running the project
  suite. The standalone probe still runs fine on 25 since the validator imports only `java.*`.
- Learnings consumed: [security/charset-allowlist-block-ranges, security/dead-security-controls,
  backend/derive-and-sweep-finite-security-domains]

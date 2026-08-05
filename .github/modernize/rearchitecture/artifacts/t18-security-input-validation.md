# t18 — Input Validation, Prompt Injection & Dependencies

Detail file for [t18-security.md](./t18-security.md). Contains **both HIGH findings**.

## Why B3 is the dangerous boundary

`infrastructure/config/AgentPathConfig.java:11`:

```java
public static final List<String> DEFAULT_DIRECTORIES = List.of("./agents", "./.github/agents");
```

These are **relative to the working directory**. The tool's primary use case is running it against
a checked-out repository — so by default it loads agent-definition markdown **from inside the
repository under review**. Those files define system prompts, instructions and output formats that
are sent to the LLM.

So untrusted markdown from the reviewed repo becomes **instructions to the model**. That is the
sharpest edge in this codebase, and it is where both HIGH findings sit.

## SEC-H1 (HIGH) — the validator's allowlist half is dead code

`domain/instruction/CustomInstructionSafetyValidator.java`. The class reads as a serious control:
size caps, a line cap, a charset allowlist, a structured result type. Verified by grep — **each of
these symbols appears exactly once in the entire repository, at its own declaration**:

| Symbol | Line | References outside its declaration |
|---|---|---|
| `MAX_INSTRUCTION_SIZE` | 24 | **0** |
| `MAX_UNTRUSTED_INSTRUCTION_SIZE` | 25 | **0** |
| `MAX_INSTRUCTION_LINES` | 26 | **0** |
| `ALLOWED_CHAR_RANGE` | 58 | **0** |
| `ValidationResult` | 108 | **0** |

The only method actually invoked anywhere is `containsSuspiciousPattern` — the **denylist**
(3 call sites: `AgentConfigLoader.java:256`, `SkillDefinition.java:58`, and one internal).

**Consequences:** no size limit, no line limit, and no charset allowlist is enforced on untrusted
instruction content. An arbitrarily large instruction can be loaded, and any character range is
accepted.

**This is the project's recurring failure mode.** It is the same shape as the vacuous ArchUnit
rules (t12.1: 107 of 687 classes silently imported) and the deleted MDC tests (t13.1 G2) — a
control that *looks* enforced, reads as enforced in review, and enforces nothing. The class name
actively works against the reader here.

**Fix (backend):** wire the caps and the allowlist into the actual validation path and return
`ValidationResult`. **Then add a negative-control test** that feeds an oversized / out-of-charset
instruction and asserts rejection — otherwise the fix is unfalsifiable in exactly the way the
current code is.

The **normalisation** logic at `:122-143` (NFKC + homoglyph folding) is genuinely good work and
should be preserved — it defends against Unicode-confusable bypasses that most implementations miss.

## SEC-H2 (HIGH) — injection defence is denylist-only, and covers 7 of 12 fields

`infrastructure/parsing/AgentConfigLoader.java:234-241` defines `FIELD_EXTRACTORS`, the set of
fields scanned by `containsSuspiciousPattern`:

**Scanned (6 scalars + `focusAreas`):** `systemPrompt` (role), `instruction`, `outputFormat`,
`displayName`, `model`, `name`, `focusAreas`

**`AgentConfig` actually declares 12 components** (`domain/agent/AgentConfig.java:12-25`).
Reconciling them:

| Unscanned component | Reaches LLM? | Verdict |
|---|---|---|
| `peerModel` | yes | **Covered elsewhere** — `AgentDefinitionPolicy.java:83-84` validates it via `validateModel` |
| `skills` | yes | **Covered** — `SkillDefinition.java:58` runs its own check |
| `language` | indirectly | **Gap** → SEC-L2 below |
| `rubberDuckEnabled`, `dialogueRounds` | n/a | Non-String, not injectable |

So the field-coverage gap is narrower than a raw 7-of-12 count implies, and I record that rather
than inflate it. **The structural problem is the denylist itself.**

A denylist of suspicious patterns cannot enumerate the space of prompt injections — paraphrase,
encoding, translation, or novel phrasing all bypass it. The allowlist and size caps that would
constrain the input space are precisely what SEC-H1 shows is dead code. The two findings compound:
**the denylist is the only live defence, and denylists do not hold.**

**Fix (architect):** treat this as a design decision, not a pattern-list update. Options worth
weighing — constrain untrusted agent files to a strict schema with bounded field lengths and a
charset allowlist (i.e. revive SEC-H1's caps); require explicit opt-in (`--allow-repo-agents`)
before loading agent definitions from the reviewed repository; or load repo-supplied agents in a
reduced-privilege mode. The current default — silently trusting `./.github/agents` from an
arbitrary repo — deserves an explicit, recorded decision.

## Denial-of-service surface

### SEC-M5 (MED) — ReDoS on untrusted LLM output

`domain/report/ContentSanitizer.java`:

- `:69` — `(?:(?!</a>).)*` is **greedy, not possessive**. Line 27 in the same file correctly uses
  `*+`, so the codebase knows the technique and applies it inconsistently.
- `:33`, `:45` — lazy `.*?` under `DOTALL`, with **no length cap** before the regexes run
  (`:96-105`).

Input is LLM output (B4), which is influenced by repository content (B2). An attacker who can
shape review output can drive quadratic backtracking.

**Fix:** make the quantifier possessive and impose a length cap before sanitisation.

### SEC-L1 (LOW) — no file-count or depth cap in the local walk

`LocalFileCandidateCollector.java:34-56` walks without a file-count or depth limit; the byte budget
is applied later in the processor. A pathological repository can inflate the candidate list before
any budget applies. Rated LOW because the user selects the directory (B1), but the *contents* are
B2 — a legitimately-chosen repo can still be hostile.

### SEC-L2 (LOW) — `language` flows unvalidated into a template key

`RubberDuckDialogueRunner.java:100-105`:
```java
String lang = language != null ? language : TEMPLATE_FALLBACK_LANG;
String key = TEMPLATE_INITIAL_PREFIX + lang;
return loadTemplate.loadRaw(key);
```
`language` is a recognised frontmatter key (`AgentDefinitionPolicy.java:42`) but its **value is
never validated**, and it is concatenated into a resource key. Impact is bounded by the
exception fallback at `:105` and by classpath resource resolution, so this is LOW — but it should
be constrained to a known-language allowlist, which is a one-line fix.

## SEC-L9 (LOW) — CLI bounds: robustness, not vulnerability

`ReviewOptionsParser.java` — `--output` (`:157`) is `Path.of(v)` with no normalisation or
containment; `--local` (`:131`) checks existence only; `--parallelism` (`:161-162`) is
lower-bounded only; `--dialogue-rounds` (`:202-203`) is unbounded on the CLI even though agent-file
values are capped at `0..10` (`AgentDefinitionPolicy.java:92-94`).

**I am deliberately not filing these as path traversal / resource exhaustion vulnerabilities.**
All four are B1 — supplied by the user running the tool under their own privileges. A user can
already write to any path they own; `--output ../../x` grants them nothing they lacked. Reporting
these as HIGH would be a false positive of exactly the kind that erodes trust in a security review.

They remain worth fixing as **robustness/UX**: an unbounded `--parallelism` amplifies LLM cost, and
the CLI/agent-file inconsistency on `--dialogue-rounds` is a genuine correctness smell (two paths,
two different limits, one of them absent).

## Dependencies & CVE

**Method.** Per the t12.1 tooling constraint, bytecode-inspecting scanners shading pre-Java-27 ASM
fail *silently and partially* on this codebase, so no bytecode SAST was used. OSV.dev
`querybatch` queries by Maven **coordinate**, which is structurally immune to that failure mode.

Per `cve-pin-can-itself-be-vulnerable`, I scanned the coordinate named in each
`<!-- Security: -->` override comment — not merely the resolved tree, which can report clean
*correctly and uselessly* when an override does not resolve under the active profile.

**Non-vacuity controls.** A clean result from a scanner that scanned nothing looks identical to a
genuinely clean result, so two known-vulnerable coordinates were included:

| Coordinate | Findings | Purpose |
|---|---|---|
| 5 pinned override targets (`pom.xml:37-42`, `pom-native.xml:38-43`) | **0** | Subject |
| `ch.qos.logback:logback-core:1.5.12` | **6** | Negative control |
| `com.fasterxml.jackson.core:jackson-databind:2.13.0` | **9** | Negative control |

The controls fired, so the clean result is meaningful. This independently confirms t15's
`3.1.4 → 3.1.5` fix holds.

**Suppressions.** `osv-scanner.toml` contains no active ignores — nothing is being hidden.

### SEC-L6 (LOW) — SnakeYAML is unused attack surface

`pom.xml:86-91` declares SnakeYAML at `compile` scope. Verified: it is **never instantiated**
anywhere in `src/main` — frontmatter is parsed by a hand-written regex parser
(`FrontmatterParser.java:12-79`).

This is a genuine positive for safety (no gadget chains, no billion-laughs) but it means the
dependency contributes **only** CVE exposure and native-image bloat for zero functional benefit.
Removing it is a strict improvement; it also removes a future footgun where someone "helpfully"
replaces the regex parser with `new Yaml(...)`.

## Upstream Artifacts Consumed

- `project-profile.yaml` — stack inventory; its "Mustache 風 `{{placeholder}}`" claim (line 19) is **stale** and corrected in the index.
- `t15-backend.md` — CVE baseline, independently re-verified here.
- `learnings/backend/cve-pin-can-itself-be-vulnerable.md` — drove scanning override targets rather than only the resolved tree.
- `team/security/inbox.md` (t12.1) — tooling constraint that ruled out bytecode SAST.
- `t4-architect.md` — layer ownership for each finding.

## Evidence Mapping

| Upstream | → | Evidence here |
|---|---|---|
| `cve-pin-can-itself-be-vulnerable` | → | Scanned all 5 `<!-- Security: -->` override coordinates; 0 findings; t15's fix confirmed |
| `t12.1` tooling constraint (ASM < 27 fails silently) | → | Rejected bytecode SAST; used coordinate-based OSV + negative controls |
| `t12.1` vacuous-rule failure mode | → | Applied the same suspicion to security controls → found **SEC-H1** (dead validator) by grep-counting symbol references |
| `t15-backend.md` CVE baseline | → | Independently re-verified rather than inherited |
| `AgentPathConfig.java:11` | → | Established B3 as untrusted; the basis for rating **SEC-H1/H2** HIGH |
| `AgentDefinitionPolicy.java:83-84`, `SkillDefinition.java:58` | → | Narrowed SEC-H2's field-coverage claim honestly (`peerModel`/`skills` **are** covered elsewhere) |

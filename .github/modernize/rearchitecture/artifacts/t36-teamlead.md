# t36 — Independent Documentation Quality Gate (Fresh Re-gate)

## Verdict

**PASS.**

The gate was rerun independently after t36.1 remediated F-01 through F-06. All five required
dimensions now pass on the current files: source accuracy, cross-file consistency, working
references, English/Japanese parity, and release chronology.

| Gate | Result | Fresh evidence |
|---|---|---|
| Source-of-truth accuracy | **PASS** | 25/25 source and F-01–F-06 assertions passed |
| Cross-file consistency | **PASS** | Runtime, architecture, packaging, template, tag, and workflow claims agree |
| Working references | **PASS** | 23/23 local destinations and 39/39 factual external targets are reachable |
| English/Japanese parity | **PASS** | 6/6 structural and semantic parity assertions passed |
| Release chronology | **PASS** | 4/4 heading/history assertions passed |

No source or user-facing documentation was modified by this gate.

## Gate Lineage

| Stage | Verdict | Meaning |
|---|---|---|
| Original t36 gate | **FAIL** | F-01 through F-06 identified; F-01 was HIGH |
| t36.1 remediation | **PASS** | Producer reported all six findings closed |
| This fresh t36 re-gate | **PASS** | Closure independently reproduced from current source and documents |

The original finding IDs remain the closure contract; this report supersedes the historical FAIL
verdict without erasing it.

## Finding Closure

### F-01 — CLOSED — Template placeholder syntax

- `PlaceholderUtils` recognizes `${key}` with `\$\{(\w+)}`.
- `README_en.md` and `README_ja.md` both prescribe `${placeholder}`.
- The 28 shipped templates contain 37 `${...}` occurrences across 17 files and zero
  `{{...}}` occurrences.
- The focused implementation test passed 2/2.

### F-02 — CLOSED — Release procedure reference

- All four former missing-checklist references now target
  `docs/runbook.md#release-procedure`.
- The file and `## Release Procedure` anchor exist.
- The retired `documentation_sync_checklist_2026-02-17.md` reference occurs zero times.

### F-03 — CLOSED — Retired historical targets

- `multi-agent-code-reviewer-java` occurs zero times in the five documents.
- Historical PR identifiers remain explicit non-links; no same-number replacement was inferred.
- The unavailable identifiers `v2026.02.17-notes`,
  `v2026.06.24-refactor-seams-tests`, and `v2026.07.21-sdk-upgrade` remain visible as
  explicit unavailable targets rather than guessed links.
- The complete external sweep found zero dead factual targets.

### F-04 — CLOSED — Release and native workflow wording

- `.github/workflows/release.yml` actively builds/releases the Java 28 JVM artifact.
- Its native release job remains commented out.
- `.github/workflows/ci.yml` actively runs the GraalVM 25.0.4 Native Image gate.
- `README.md` and both `Unreleased` sections now distinguish these paths consistently.

### F-05 — CLOSED — Release-tag namespace

- The latest `v*` tag is `v2026.07.21-review-contract`.
- `gh release view` confirms that tag is a published, non-draft, non-prerelease release.
- Both languages now say “release tag,” so newer backup tags no longer contradict the claim.

### F-06 — CLOSED — Template inventories

- `README_en.md` and `README_ja.md` each enumerate exactly the same 28 filenames present under
  `templates/`.
- `review-quality-constraints.md` is present in both inventories.
- Only the final entry uses `└──`; the preceding 27 entries use `├──`.

## Independently Reproduced Source Facts

- Production Java sources: **201**.
- Port interfaces: **8 inbound / 15 outbound**.
- Layer-zero files: `ReviewApp.java`, `ApplicationPortFactory.java`, and
  `ReviewPortFactory.java`.
- Stack: Java **28**, Micronaut parent **5.1.0**, `copilot-sdk-java` **1.0.8**,
  Jackson **3.1.5**, Jackson 2 BOM **2.22.1**, Maven wrapper **3.9.14**.
- `.sdkmanrc`: `28.ea.9-open`.
- Templates: **28**.
- Both reachability-metadata files are byte-identical and contain no broad `allDeclared*`
  registration.
- No `Dockerfile` exists.

## Cross-file Consistency

- All three READMEs and both `Unreleased` sections use the same Java/Micronaut/SDK versions,
  201-source count, 8/15 port counts, 28-template count, release state, and packaging split.
- The five documents consistently identify
  `reviewer.execution.concurrency.review-passes` as the live key and retain
  `reviewer.execution.review-passes` only as historical context.
- Release documentation consistently describes Java 28 release packaging, GraalVM 25.0.4 native
  CI, and the disabled native release job.
- Template documentation agrees with both the parser contract and the on-disk inventory.

## Working References

- Local Markdown references: **23 passed / 0 failed / 0 skipped**, including all file anchors.
- External URL tokens: **40 total**.
  - **37** factual public targets returned HTTP 200.
  - The GitHub Copilot MCP endpoint returned the expected authenticated HTTP 401.
  - The Microsoft Learn MCP endpoint returned the expected method-gated HTTP 405.
  - One illustrative `your-org` clone URL was explicitly excluded from factual-link validity.
- Latest release identity was also confirmed through `gh release view`.

## English/Japanese Parity

- Detailed READMEs: **75 headings each**, with identical depth sequences.
- Detailed READMEs: **56 fences**, **2 Mermaid blocks**, and **87 table lines** each.
- Current operational/architecture evidence markers: **26/26 paired**.
- Paired pre-history prefixes (update rules plus `Unreleased`): identical 8-heading depth sequence
  and exact 33-token inline-code multiset. The `Unreleased` subtrees account for 6 headings and
  30 inline-code tokens in each language.
- Paired `Unreleased` source evidence markers: **13/13 paired**.

## Release Chronology

- Dated-heading sequences exactly match `HEAD`: **39 English / 38 Japanese**.
- The pre-existing EN/JA heading-count asymmetry remains unchanged.
- Against each language's `HEAD` history, exactly **14 old lines / 14 new lines** differ.
  Every difference is an approved URL-to-explicit-non-link replacement; PR numbers and release
  identifiers are preserved exactly.
- No dated heading was added, removed, renamed, or reordered.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/clarification.md` — Java 28 CLI scope and must-pass baseline.
- `.github/modernize/rearchitecture/team/teamlead/inbox.md` — binding architecture, runtime, and
  source-verification directives.
- `.github/modernize/rearchitecture/artifacts/t35-architect.md` — producer baseline for final
  documentation state and chronology.
- `.github/modernize/rearchitecture/artifacts/t36-teamlead.md` (prior revision) — F-01 through F-06
  and the re-gate exit criteria.
- `.github/modernize/rearchitecture/artifacts/t36.1-architect.md` — remediation claims subjected to
  this independent re-gate.
- `README.md` — concise current-state, packaging, architecture, and release claims.
- `README_en.md` — detailed English current-state and operational claims.
- `README_ja.md` — detailed Japanese counterpart.
- `RELEASE_NOTES_en.md` — English `Unreleased` state and dated chronology.
- `RELEASE_NOTES_ja.md` — Japanese `Unreleased` state and dated chronology.

`.github/modernize/rearchitecture/context.md` remains unavailable. No gate claim depends on it.

## Evidence Mapping

- `t36-teamlead.md (prior)#F-01` → `Finding Closure / F-01` and focused 2/2 parser tests.
- `t36-teamlead.md (prior)#F-02` → `Finding Closure / F-02` and 23/23 local-link checks.
- `t36-teamlead.md (prior)#F-03` → `Finding Closure / F-03`, 39/39 factual external probes, and
  identity-preserving historical deltas.
- `t36-teamlead.md (prior)#F-04` → `Finding Closure / F-04` and direct release/CI workflow checks.
- `t36-teamlead.md (prior)#F-05` → `Finding Closure / F-05` and release-tag/release-identity probes.
- `t36-teamlead.md (prior)#F-06` → `Finding Closure / F-06` and exact 28-file set equality.
- `t35-architect.md#Final State Documented` → `Independently Reproduced Source Facts`.
- `t35-architect.md#Documentation Integrity` → `English/Japanese Parity` and
  `Release Chronology`.
- `t36.1-architect.md#Remediation Summary` → all six independently reproduced closure rows.
- `clarification.md#Backend` → Java 28 runtime/source checks and focused test execution.
- The five user-facing documents → all five gate sections and the test evidence below.

## Test Results

| Check / command | Passed | Failed | Skipped | Result |
|---|---:|---:|---:|---|
| Inline Python source/F-01–F-06 validator | 25 | 0 | 0 | PASS |
| Inline Python local destination + anchor validator | 23 | 0 | 0 | PASS |
| Concurrent `curl -L` external-reference sweep | 39 | 0 | 1 | PASS; one illustrative URL excluded |
| Inline Python EN/JA parity validator | 6 | 0 | 0 | PASS |
| Inline Python release chronology/history-delta validator | 4 | 0 | 0 | PASS |
| Inline Python Markdown fence validator | 5 | 0 | 0 | PASS |
| `JAVA_HOME="$HOME/.sdkman/candidates/java/28.ea.9-open" PATH="$HOME/.sdkman/candidates/java/28.ea.9-open/bin:$PATH" ./mvnw -B -ntp -Dtest=PlaceholderUtilsTest test` | 2 | 0 | 0 | BUILD SUCCESS |
| `git diff --check -- README.md README_en.md README_ja.md RELEASE_NOTES_en.md RELEASE_NOTES_ja.md` | 1 | 0 | 0 | PASS |

This documentation-only re-gate did not rerun the full JVM or Native Image build. The source tree
was not changed; t35's full JVM/native results remain upstream build evidence.

## Findings

- CRITICAL: **0**
- HIGH: **0**
- MEDIUM: **0**
- LOW: **0**

## [t3] CLI behavior inventory — 69 behaviors catalogued

- 4 commands (run, list, skill, doctor), 4 exit codes, 50+ config keys, 30 templates
- Rubber-duck mode is a significant sub-feature with its own language-specific templates (EN/JA)
- Prompt-injection safety is multi-layered: regex + homoglyph normalization + delimiter detection + NFKC
- Circuit breaker has 3 domains (review, skill, summary) — each independent
- Token resolution is a 4-step fallback chain: CLI arg → env vars → `gh auth token` → error
- Learnings consumed: (none)

## [t21] Signed off 69-behavior parity on the final layered runtime

- The enumerated t3 matrix is the authoritative denominator: 69 unique IDs; the prose total of 74
  is count drift, as is the 30-vs-28 template total.
- Final evidence resolves to 49 DIRECT, 19 COVERED-PARTIAL, and 1 coordinator-approved MANUAL-TIER;
  all 69 pass the approved t5 threshold without disguising evidence strength.
- C2 scalar instruction frontmatter/raw fallback and OUT-02/OUT-03 single-/multi-pass filenames
  were rerun together on Java 28: 176 passed, 0 failed/errors/skipped.
- Architecture t17, security t18, and final runtime t20 all pass with zero HIGH/CRITICAL findings.
- Learnings consumed: [pm/behavior-id-scheme]

## [t22.3] Re-signed 69 behaviors after runtime contradiction remediation

- t22 correctly invalidated the earlier t21 sign-off for AGT-01 and SKL-01; the historical state was
  67/69, not a waivable evidence gap.
- t22.2 proves both corrected behaviors with populated fixtures on the packaged JAR and native
  executable, including negative assertions against the former empty-inventory outputs.
- AGT-01 and SKL-01 move from COVERED-PARTIAL to DIRECT-CLOSURE; the corrected evidence distribution
  is 51 DIRECT/DIRECT-CLOSURE, 17 COVERED-PARTIAL, and 1 MANUAL-TIER.
- An independent current-tree focused PM run passed 207/207; the single-file artifact contains
  exactly 69 unique behavior rows and all category/grade totals reconcile.
- Global checkpoint findings C-001–C-003 remain outside PM ownership and were explicitly not waived.
- Learnings consumed: [pm/behavior-id-scheme, pm/parity-signoff-evidence-grades]

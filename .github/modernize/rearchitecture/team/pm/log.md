## [t3] CLI behavior inventory — 69 behaviors catalogued

- 4 commands (run, list, skill, doctor), 4 exit codes, 50+ config keys, 30 templates
- Rubber-duck mode is a significant sub-feature with its own language-specific templates (EN/JA)
- Prompt-injection safety is multi-layered: regex + homoglyph normalization + delimiter detection + NFKC
- Circuit breaker has 3 domains (review, skill, summary) — each independent
- Token resolution is a 4-step fallback chain: CLI arg → env vars → `gh auth token` → error
- Learnings consumed: (none)

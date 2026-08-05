# Domain Sub-Package Organization

Domain layer organized into 6 sub-packages by business concept, not by technical role.

## What Happened
During t4, mapped ~40 files to `domain` layer. Grouped by business concept: agent, report, skill, instruction, review, resilience — not by technical pattern (models, validators, formatters). This keeps related domain logic cohesive.

## Takeaway
- `domain.report` is the largest (~18 files) because report logic is mostly pure (findings extraction, formatting, sanitization, merging). If it becomes unwieldy during implementation, split into `domain.report.finding`, `domain.report.formatting`, `domain.report.sanitization`.
- `domain.resilience` groups SharedCircuitBreaker, SynthesisStrategy, CopilotCliException, CopilotStartupErrorFormatter, CopilotTimeoutResolver — these are cross-cutting resilience concerns not specific to one business domain.
- `domain.review` holds cross-cutting review models (ReviewContext, ReviewTarget, LocalFileCandidate, PromptTexts) used by multiple use-cases.

## History
- 2026-08-05 (rearchitecture/t4): initial — 6 domain sub-packages designed

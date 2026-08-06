# Vertical Module Task Split

Tasks must split by vertical business module (agent, report, skill), not by technical layer (entity, service, controller), to keep each task independently executable.

## What Happened
During t5 planning, the 120-file rewrite was organized by business domain (agent system, report system, skill system, CLI presentation) rather than by architectural layer. This keeps each task's files cohesive — a backend agent working on "agent domain" touches only agent-related models, validators, and prompt builders.

## Takeaway
For layered architecture rewrites, order phases by dependency direction (innermost layer first) but split tasks within each phase by domain module. This avoids cross-task merge conflicts and makes each task self-contained.

## History
- 2026-08-05 (rearchitecture/t5): initial

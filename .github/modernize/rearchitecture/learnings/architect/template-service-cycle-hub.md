# Template Service Cycle Hub

TemplateService is the single biggest dependency cycle hub in the codebase — involved in 5 of 10 cycles.

## What Happened
During t2 analysis, TemplateService was found to be imported by 8+ classes across 4 different packages (agent, orchestrator, report.core, report.factory, report.summary), all of which are also consumed by `service` package — creating mutual dependencies.

## Takeaway
When designing the port catalog (t4), define a `LoadTemplatePort` in `application.port` as the **first** port. This single port interface will break 5 cycles simultaneously. The `TemplateRepository` (file I/O) becomes the infrastructure adapter; `TemplateService` becomes an application-layer coordinator or is eliminated entirely.

## History
- 2026-08-05 (rearchitecture/t2): initial discovery — 5 of 10 cycles traced to TemplateService

## 2026-08-05T02:09:50Z — from teamlead (t1) [broadcast]

CONSTITUTION PUBLISHED — All roles must follow `artifacts/t1-teamlead.md`. Key rules:
1. 5+1 layer model: presentation / application / application.port / domain / infrastructure / shared.
2. Dependencies point inward only — domain imports ONLY java.* and shared.
3. Copilot SDK confined to infrastructure.
4. Micronaut / Jakarta confined to infrastructure + presentation.
5. ArchUnit enforces all boundaries.
6. Port naming: VerbNounPort. Adapter naming: TechNounAdapter.
7. Zero package cycles. Violations are CRITICAL.


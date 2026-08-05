# Behavior ID Scheme

Consistent behavior ID prefixes enable traceability from PM inventory through architecture mapping to test cases.

## What Happened
During t3 (CLI behavior inventory), established a prefix-based ID scheme for 69 behaviors across 8 categories: AGT (agent), SKL (skill), INS (instruction safety), TGT (target), ORC (orchestration), AUTH (authentication), RTY (retry/circuit-breaker), OUT (output). Project: multi-agent-code-reviewer / t3.

## Takeaway
All downstream roles (architect, tester, backend) should reference behaviors by these IDs when mapping classes to layers, writing tests, or tracking regressions. The IDs are stable — new behaviors get the next number in their prefix.

## History
- 2026-08-05 (multi-agent-code-reviewer/t3): initial

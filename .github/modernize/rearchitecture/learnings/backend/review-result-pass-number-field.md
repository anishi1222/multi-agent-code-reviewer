# ReviewResult passNumber Field for Multi-Pass Filename Routing

Architecture decision: `ReviewResult` carries a `passNumber` field (0=single-pass, ≥1=multi-pass index) so that `GenerateReportUseCase` can route to OUT-02 or OUT-03 filename patterns without external parameters.

## What Happened

OUT-02 (`{agent}-report.md`) and OUT-03 (`{agent}-pass-{n}-report.md`) have different filename
patterns but the same code path in `writePerAgentReports()`. The distinguishing information —
which pass number produced the result — was available inside `ReviewPassRunner`'s pass loop but
was discarded before the result was stored.

In t9.1, a `passNumber` field was added to `ReviewResult` (default `0` via builder). In
`ReviewPassRunner`, single-pass results keep `passNumber=0`; multi-pass results are tagged with
`withPassNumber(currentPass)`. `writePerAgentReports()` branches on `passNumber > 0`.

Source project: anishi1222/multi-agent-code-reviewer / t9.1

## Takeaway

When a domain result needs to carry contextual metadata that affects downstream formatting but is
only known at production time, embed it as a field on the record rather than threading it as a
parameter through every downstream call. Use a wither (`withPassNumber(int)`) for lightweight
immutable copy rather than rebuilding through the full builder.

Builder must declare `private int passNumber = 0` as a default so existing call sites compile
without change. `build()` must pass it as the Nth positional arg to the record canonical constructor.

## Example

```java
// In ReviewPassRunner pass loop:
ReviewResult tagged = passes > 1 ? raw.withPassNumber(currentPass) : raw;

// In GenerateReportUseCase.writePerAgentReports():
String filename = result.passNumber() > 0
    ? "%s-pass-%d-report.md".formatted(safeName, result.passNumber())  // OUT-03
    : "%s-report.md".formatted(safeName);                               // OUT-02
```

## History
- 2026-08-05 (anishi1222/t9.1): initial

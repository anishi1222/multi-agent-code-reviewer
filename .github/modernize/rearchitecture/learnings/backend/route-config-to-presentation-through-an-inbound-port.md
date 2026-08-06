# Route Config To Presentation Through An Inbound Port

Presentation must never name an infrastructure configuration key by string — route the value through an inbound port so drift becomes a compile/test failure instead of a silent one.

## What Happened

`multi-agent-code-reviewer` / t28 (finding F3). `presentation.formatter.ReviewOutputFormatter` bound
`@Value("${reviewer.execution.review-passes:1}")` to print "Review passes: N". The executor read
`reviewer.execution.concurrency.review-passes`. **Two different keys for one setting** — the banner
could claim 1 while 3 passes ran, or claim 7 while 1 ran.

ADR-0006 D1 forbids `presentation → infrastructure` *imports*, and `LayerDependencyRulesTest` Rule 5b
enforces it — but a `@Value` key is the same coupling expressed as a **string**, so no static rule
sees it. Nothing could observe the two keys drifting apart.

The temptation is to fix the string. That passes every test and leaves the gap wide open for the next
drift. The fix has to remove the *string-naming*, not correct it.

## Takeaway

1. **A `@Value`/`@ConfigurationProperty` key inside `presentation` (or any layer that may not import
   `infrastructure`) is a layering violation the linter cannot see.** Treat it as one.
2. **Add an inbound port** — `application.port.inbound.XxxPort` + a small DTO — implemented by a use
   case in `application`, bound once in the composition root. Presentation asks *what will happen*;
   the application answers.
3. **Bind the method reference, not a snapshotted value.** `new UseCase(executionConfig::reviewPasses)`
   makes the wiring read as "the same accessor the real consumer uses". Passing an `int` is
   runtime-identical but puts a *second independent read* in the factory — the exact shape of the bug.
4. **Do not re-normalise in the DTO.** ADR-0006 D6: defaults have one owner. Make the DTO **throw** on
   an impossible value so a broken owner surfaces instead of being masked.
5. **The test must cross-compare two derivations.** "set key to 3, assert banner says 3" passes against
   the broken code too. Set the *two* keys to contradictory values and assert the port's answer equals
   the real consumer's derivation. Then **mutation-verify**: reintroduce the defect and confirm red.

## Example

```java
// ✗ presentation names an infrastructure key — invisible drift
ReviewOutputFormatter(CliOutput out,
    @Value("${reviewer.execution.review-passes:1}") int reviewPasses) { … }

// ✓ presentation receives a plan; the key stays in infrastructure.config
ReviewPreparationService(ReviewOutputFormatter banner, DescribeReviewPlanPort planSource) { … }

// composition root (infrastructure) — binds the accessor the executor itself resolves
@Singleton
DescribeReviewPlanPort describeReviewPlanPort(ExecutionConfig cfg) {
    return new DescribeReviewPlanUseCase(cfg::reviewPasses);
}
```

## History
- 2026-08-06 (multi-agent-code-reviewer/t28): initial — from F3, `reviewPasses` banner/executor key split.

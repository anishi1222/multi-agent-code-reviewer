# A Logging Canary Must Load the Shipped Configuration Through Joran

Manually reconstructing a Logback layout can pass while the production XML parser rejects the same configuration.

## What Happened

During `multi-agent-code-reviewer/t16.2`, the full Java 28 build emitted
`PatternSyntaxException: Unclosed character class` while Logback's
`DefaultJoranConfigurator` loaded `HEADER_MASK_PATTERN`.

The existing sink canary was green because it extracted XML properties itself, substituted them
into the console pattern, and initialized `PatternLayout` directly. That bypassed Joran's property
substitution/option-parsing path—the exact path failing in production.

## Takeaway

- A canary for shipped logging configuration must load the exact XML through Joran and assert the
  `LoggerContext` has no error statuses.
- Keep direct `PatternLayout` rendering tests for masking semantics, but do not treat them as parser
  or startup validation.
- A green test suite is insufficient when framework initialization prints a caught exception;
  search clean-build output for configuration bootstrap failures.

## History

- 2026-08-07 (multi-agent-code-reviewer/t16.2): initial

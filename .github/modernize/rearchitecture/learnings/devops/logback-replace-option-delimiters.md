# Logback Replace Option Delimiters

Regex text substituted into Logback `%replace` options must encode converter delimiters.

## What Happened

In t19, the masking canary passed because it built a layout programmatically, but packaged
startup failed Joran initialization with `PatternSyntaxException: Unclosed character class`.
After `${HEADER_MASK_PATTERN}` substitution, raw comma, `}`, and escaped `]` characters inside
the regex character class were interpreted by Logback's converter option grammar.

## Takeaway

Encode those excluded characters as regex hex escapes (`\x2C`, `\x7D`, `\x5D`) in both
`logback.xml` and `logback-json.xml`. Preserve the value-shape pass before the header-name pass.
Always verify the shipped XML through real application startup; a standalone `PatternLayout`
canary does not cover Joran's option parser.

## History

- 2026-08-07 (anishi1222/t19): initial

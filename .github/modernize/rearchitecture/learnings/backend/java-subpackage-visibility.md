# Java Visibility Across Sub-Packages

Java's package-private access does NOT extend to sub-packages. `presentation.command` and `presentation.parser` are DIFFERENT packages from `presentation` — any type in `presentation/` used by sub-package code must be declared `public`.

## What Happened
34 compilation errors when `presentation.command.*` classes tried to access `CliValidationException`, `ReviewOptions`, `CliParsing` etc. from the parent `presentation` package. All were package-private. Fix: add `public` to every type in `presentation/` root that is referenced from any sub-package.

## Takeaway
When organizing a package with sub-packages, make ALL shared types `public`. Only truly internal types (used only within the EXACT same package file set) can be package-private.

For `public record` types: the compact canonical constructor MUST also be declared `public`, otherwise you get `invalid canonical constructor in record`.

## History
- 2026-08-05 (multi-agent-code-reviewer/t12): initial

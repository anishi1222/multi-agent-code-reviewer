# Micronaut Record Binding: An Absent Key Throws, It Does Not Default to Zero

Deleting `@Bindable(defaultValue=…)` from a `@ConfigurationProperties` record does not make unset keys fall back to the Java primitive default — Micronaut refuses to construct the bean.

## What Happened

`multi-agent-code-reviewer` / t27 (F2). The architecture review prescribed removing eight
`@Bindable(defaultValue = "…")` literals from a config record, reasoning that unbound `int`s
would arrive as `0` and the record's compact constructor would normalise them back to the
canonical default. The brief flagged this as unverified.

It is false on Micronaut 5.1.2 / Java 28. With the key absent:

- `int x` with no `@Bindable` → `DependencyInjectionException: Error resolving property
  value [prefix.x]. Property doesn't exist`.
- Adding compact-constructor normalisation does **not** help. The exception is thrown during
  parameter resolution, so the constructor body never runs.

Applying the prescribed fix would have turned a graceful default into a startup crash for any
deployment missing a key.

## Takeaway

To give a config record's defaults a single owner, **box the components and mark them
`@Nullable`**, then normalise `null` in the compact constructor. Reference types bind to
`null` when absent, so the constructor actually runs. This is also the shape already used by
`LocalFileConfig` (`@Nullable List`) and `ExecutionConfig.sharedSessionEnabled`.

Verify absent-key behaviour with a throwaway probe under a prefix that is guaranteed not to
exist in any config file, *before* editing production code. Values that exist in
`application.yml` cannot be made to look absent — no override (`""`, `" "`, whitespace)
simulates absence, so the fall-through path is untestable until the key is genuinely gone.

## Example

```java
@ConfigurationProperties("reviewer.prompt-budget")
public record PromptBudgetConfig(@Nullable Integer peerContentMaxChars /* … */) {
    public PromptBudgetConfig {          // runs; null means "key absent"
        peerContentMaxChars = peerContentMaxChars != null
            ? peerContentMaxChars : PromptBudget.DEFAULT_PEER_CONTENT_MAX_CHARS;
    }
}
```

## History

- 2026-08-06 (multi-agent-code-reviewer/t27): initial

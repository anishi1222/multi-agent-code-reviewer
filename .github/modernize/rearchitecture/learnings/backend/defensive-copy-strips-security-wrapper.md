# Defensive Copy Strips Security Wrappers — Encode It As A Type Invariant

`Map.copyOf()` in a record's compact constructor silently discards a caller-supplied masking wrapper; make the security property an invariant of the DTO instead.

## What Happened

`multi-agent-code-reviewer` / t13. A test asserted that `McpServerSpec.headers().toString()` masks
the Authorization value so the token cannot leak through SDK debug logging, while
`headers().get("Authorization")` still returns the raw value for actual requests.

It failed: `toString()` emitted the **raw token**. Neither of the two existing
`SensitiveHeaderMasking` classes was referenced anywhere in `src/main` — masking had been dropped
entirely during the rewrite.

The first fix wired `SensitiveHeaderMasking.wrapHeaders(...)` in at the **call site**
(`GithubMcpConfig.toMcpServerSpec`). It compiled, it looked right, and it **still leaked**.

Root cause: the record's own compact constructor did

```java
headers = headers != null ? Map.copyOf(headers) : Map.of();
```

`Map.copyOf` copies the *entries* into a plain `ImmutableCollections$MapN`, discarding the wrapper
type and therefore the overridden `toString()`. The DTO's javadoc even said "use masked map
externally" — a contract the class actively destroyed.

Fix: wrap inside the compact constructor, so masking holds on **every** construction path:

```java
headers = headers != null ? SensitiveHeaderMasking.wrapHeaders(headers) : Map.of();
```

The wrapper already did `Map.copyOf(delegate)` internally, so immutability was preserved. The call
site was then simplified back to passing a plain map, and the weak duplicate masking class deleted.

## Takeaway

- A security property that depends on **every caller remembering** to wrap is not a security
  property. Encode it as an invariant of the type that owns the data.
- Watch for `Map.copyOf` / `List.copyOf` / `Set.copyOf` in record compact constructors and setters:
  they preserve *contents* but destroy *behaviour* (overridden `toString`, `equals`, custom views).
  Anywhere a wrapper carries behaviour, a defensive copy is a silent bug.
- If a javadoc tells callers to supply a specially-behaved value, verify the constructor does not
  normalise it away.
- **Verify the fix at runtime, not by reading the code.** A `jshell` probe asserting
  `contains("Bearer ***")` and `!contains(rawToken)` is what proved the first fix insufficient.

## History
- 2026-08-05 (multi-agent-code-reviewer/t13): initial

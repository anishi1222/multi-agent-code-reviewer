# Masked Map Accessor Matrix

A `Map` wrapper that masks `toString()` still leaks through `forEach`, `getOrDefault`, `entrySet().getValue()` and any defensive copy — prove it at runtime.

## What Happened

multi-agent-code-reviewer / t18. `SensitiveHeaderMasking.MaskedHeadersMap extends AbstractMap` and
overrides nine methods including `toString`, `values` and `entrySet`. It reads as thorough.

A runtime probe with a canary token showed it is masked against **3 of 10** access paths:

```
toString() / values() / entrySet().toString()   -> masked
forEach(...)                     [INHERITED]    -> RAW LEAK
getOrDefault(...)                [INHERITED]    -> RAW LEAK
entrySet() iter -> getValue()                   -> RAW LEAK
entrySet().stream().map(getValue)               -> RAW LEAK
new HashMap<>(m) / Map.copyOf(m)   [SDK copy]   -> RAW LEAK
```

Two root causes. **(1)** `AbstractMap` does *not* override `Map.forEach` or `Map.getOrDefault`, so
the interface defaults run — and they iterate `entrySet()` and call `Entry.getValue()`, which was
raw. **(2)** The wrapper protects the *object*, not the *data*, so protection ends at the first
copy — and copying is exactly what an SDK does with a config map you hand it.

Confusingly, `MaskedHeaderEntry.toString()` *was* masked, so printing an entry looked safe while
extracting its value was not.

## Takeaway

When reviewing or writing a masking wrapper, enumerate the **full accessor matrix** — not just
`toString()`:

`toString`, `values`, `keySet`, `entrySet` (both its `toString` *and* `Entry.getValue()`),
`get`, `getOrDefault`, `forEach`, `containsValue`, plus `stream()` over any of them.

Remember `AbstractMap` leaves `forEach`/`getOrDefault` to the `Map` defaults.

**Prefer masking at the sink over masking at the object.** A `toString()` wrapper cannot survive a
boundary it does not control. Redact in the logging adapter, or carry a `SecretString` type whose
`toString()` is safe and whose value needs an explicit `reveal()`.

Always verify with a runtime probe. Build the canary at runtime (`"ghp_" + "X".repeat(36)`) so no
secret-shaped literal enters the repo. Source reading rated this control far higher than it deserved.

## History

- 2026-08-05 (multi-agent-code-reviewer/t18): initial — SEC-M2/M3/M4; extends `backend/defensive-copy-strips-security-wrapper` from copies to the whole accessor surface, with runtime proof.

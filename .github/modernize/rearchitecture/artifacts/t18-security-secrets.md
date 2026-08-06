# t18 — Secrets Handling

Detail file for [t18-security.md](./t18-security.md). Covers SEC-M1, M2, M3, M4, M6 and SEC-L3, L4, L5, L7.

## Runtime proof

The `defensive-copy-strips-security-wrapper` learning (t13) states that masking must be verified
**at runtime, not by reading the code**. I did that. The canary is constructed at runtime
(`"ghp_" + "X".repeat(36)`) so no secret-shaped literal is introduced into the repository — per the
`redacted-literals-compare-by-hash` learning.

```java
static final String CANARY = "ghp_" + "X".repeat(36);   // built at runtime, never a literal

Map<String,String> raw = new LinkedHashMap<>();
raw.put("Authorization", "Bearer " + CANARY);
Map<String,String> m = SensitiveHeaderMasking.wrapHeaders(raw);
// then assert whether CANARY appears in each accessor's output
```

Run against `target/classes` on the project JDK (Java 27 / class-file major 71):

```
toString()                                  -> masked/ok
values().toString()                         -> masked/ok
entrySet().toString()                       -> masked/ok
forEach((k,v) -> ...)         [INHERITED]   -> *** RAW TOKEN LEAKED ***
getOrDefault("Authorization","")[INHERITED] -> *** RAW TOKEN LEAKED ***
entrySet() iter -> getValue()               -> *** RAW TOKEN LEAKED ***
entrySet().stream().map(getValue)           -> *** RAW TOKEN LEAKED ***
get("Authorization")        [raw by design] -> *** RAW TOKEN LEAKED ***
new HashMap<>(m).toString()      [SDK copy] -> *** RAW TOKEN LEAKED ***
Map.copyOf(m).toString()         [SDK copy] -> *** RAW TOKEN LEAKED ***
```

Reading the source suggests the map is masked. It is masked against exactly three of ten access
paths. **This is why the runtime check was worth doing.**

### SEC-M2 (MED) — inherited `Map` defaults bypass masking

`shared/SensitiveHeaderMasking.java:101-188`. `MaskedHeadersMap extends AbstractMap` and overrides
`entrySet`, `get`, `keySet`, `size`, `isEmpty`, `containsKey`, `containsValue`, `values`,
`toString`. It does **not** override `forEach` or `getOrDefault`.

`AbstractMap` does not override those either, so `Map`'s default implementations run — and they
iterate `entrySet()` and call `Entry.getValue()`, which at `:200-201` returns the **raw** value.
`MaskedHeaderEntry.toString()` (`:209`) *is* masked, which is precisely why this is easy to miss:
printing an entry looks safe, extracting its value is not.

There is also an internal contradiction: `values()` (`:152`) yields masked values while
`entrySet().getValue()` yields raw ones. A consumer reading the map through two different accessors
gets two different answers — that can leak *and* can break functionality.

**Fix:** override `forEach` and `getOrDefault`, and make `MaskedHeaderEntry.getValue()` consistent
with the chosen contract. Then add tests — **no test currently covers `forEach` or `getOrDefault`**
on the masked map (verified against `SensitiveHeaderMaskingTest.java`).

### SEC-M3 (MED) — any defensive copy strips masking

`ReviewSessionConfigFactory.java:56` and `SdkRubberDuckSessionFactory.java:80` hand the masked map
to the Copilot SDK via `setHeaders(spec.headers())`. As proven above, both `Map.copyOf` and
`new HashMap<>` produce a plain map with raw values.

This is the **same defect class the t13 learning was written about**, resurfacing at a different
call site. The wrapper defends the object, not the data — so the protection ends at the first copy.

**Bounding the claim honestly:** `javap` on `com.github.copilot.rpc.McpHttpServerConfig` shows
`headers` stored as a plain field with **no `toString()` override** on the class, so there is no
confirmed live logging sink. I did not decompile `setHeaders`. The finding is that the control is
**unreliable by construction**, not that a leak is occurring today.

**Fix direction (architect):** a `toString()`-based wrapper cannot survive a boundary it does not
control. Either mask at the *sink* (a logging port that redacts on write — consistent with
ADR-0006 **D4**), or pass a dedicated `SecretString` type whose `toString()` is safe and whose
value requires an explicit `reveal()` call.

### SEC-M4 (MED) — `MaskedToStringMap` is a latent trap

`:81-99`. Masks `toString()` and nothing else — `entrySet`, `get`, `values`, `containsValue` all
delegate raw. Verified by grep: **no caller in `src/main`**, so nothing leaks today.

The risk is prospective. It is `public` API in `shared`, and its name implies safety. The next
caller who reaches for it will get far less protection than the name suggests.
**Recommend deletion** rather than documentation — an unused security wrapper that under-delivers
is worse than no wrapper.

## SEC-M1 (MED) — report output has no secret redaction

`domain/report/ContentSanitizer.java:73-90`. The pipeline is five rules: chain-of-thought
stripping, three XSS rules, blank-line collapsing. There is **no secret redaction of any kind**.

Verified repo-wide: a grep for `ghp_|gho_|ghs_|ghu_|ghr_|github_pat|AKIA|BEGIN [A-Z ]*PRIVATE KEY|REDACTED`
across `src/main/java` returns **zero** hits — there is no redaction logic anywhere in the codebase.

This matters because of the tool's data flow: it reads a repository (which may contain `.env`
files, embedded keys, or config) and writes AI review output — which quotes that source — to a
file on disk.

**Two things bound the impact, and I record them rather than overstate the finding:**
- report files are written `0600` via atomic move,
- `/reports/`, `*.log` and `logs/` are gitignored.

**Not a regression.** I checked git history for a lost capability (the `t13.1` G2 failure mode):
`git log -S "REDACTED"` and `-S "redactSecrets"` across all branches return **nothing**. Redaction
never existed. This is a pre-existing gap the rewrite carried forward — which lowers its urgency
but makes it a legitimate ADR-0006 **D4** question: redaction is a cross-cutting capability that
currently belongs to no layer and no port.

Combined with SEC-L9, a user redirecting `--output` into a tracked directory could commit an
unredacted secret.

## SEC-M6 (MED) — token lifetime

`TokenInputReader.java:45` and `GhAuthTokenProvider.java:107` convert the token to a `String`.
Java strings are immutable and cannot be wiped; the value persists until GC and may survive in a
heap dump or core file. It is then copied into at least five further `String` fields.

`TokenReadUtils` correctly wipes the intermediate `char[]`/buffers — the buffer discipline is good.
The gap is that the `String` conversion discards that benefit.

**Realistic fix:** a `SecretString`/`SecretChars` holder carrying `char[]` with explicit `close()`,
which also resolves SEC-L3 (`toString()` safety) in the same change.

## Lower-severity items

| ID | Location | Detail |
|---|---|---|
| SEC-L3 | `ReviewRequest.java:38`, `ReviewOptions.java:13`, `SkillOptions.java:15` | Records with a `githubToken` component and no `toString()` override. `OrchestratorConfig.java:88-91` **has** one — so the codebase knows the pattern and applies it inconsistently. Any record `toString()` (logging, exception message, debugger) prints the token |
| SEC-L4 | `CopilotService.java:174-179` | `COPILOT_SDK_LOG_LEVEL` is validated against a supported-values allowlist (good), but can still raise SDK verbosity, which is what would turn SEC-M2/M3 into a live leak. Treat as an operational control |
| SEC-L5 | `SensitiveHeaderMasking.java:63-67` | Masking preserves everything before the first space. `Bearer ghp_x` masks correctly; a token with **no** space is preserved in cleartext |
| SEC-L7 | `ContentSanitizer.java:104` | Single-pass HTML entity decode → `&amp;lt;script&amp;gt;` survives one pass and can be re-decoded downstream |

## Upstream Artifacts Consumed

- `t13-backend.md` / `t13.1-backend.md` — masking-wrapper incident and the MDC-deletion failure mode.
- `learnings/backend/defensive-copy-strips-security-wrapper.md` — motivated the runtime probe.
- `learnings/backend/redacted-literals-compare-by-hash.md` — canary built at runtime, not as a literal.
- `t4-architect.md` — placed the fix for SEC-M3 at a port boundary rather than in `shared`.
- `team/security/inbox.md` (t16 ADR-0006 **D4**) — framed SEC-M1 as a displaced cross-cutting capability.

## Evidence Mapping

| Upstream | → | Evidence here |
|---|---|---|
| `defensive-copy-strips-security-wrapper` learning | → | Runtime probe; `Map.copyOf` and `new HashMap<>` both proven to strip masking (**SEC-M3**) |
| Same learning, generalised to all accessors | → | `forEach`/`getOrDefault`/`entrySet().getValue()` proven raw (**SEC-M2**) |
| `redacted-literals-compare-by-hash` | → | Canary constructed at runtime; probe adds no secret-shaped literal |
| `t13.1` G2 (capability deleted, not migrated) | → | Git-history check proved **SEC-M1 is not** a regression — redaction never existed |
| ADR-0006 **D4** | → | SEC-M1 and SEC-M3 both framed as "which port owns this cross-cutting capability" |
| `t4-architect.md` | → | Owner assignment: `shared` contract changes → architect; call-site fixes → backend |

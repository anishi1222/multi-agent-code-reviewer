# t18 — Auth Flows & Process Execution

Detail file for [t18-security.md](./t18-security.md). **No findings above LOW in this dimension** —
this area of the rewrite is genuinely solid. Recorded in full so it counts as *audited*, not skipped.

## Default-deny — proven, not assumed

The charter requires default-deny be *demonstrated*. There are exactly two call sites that require
a repository token, and both fail closed:

**1. `presentation/ReviewTargetResolver.java:43-53`**
```java
private String requireRepositoryToken(String token) {
    if (token == null || token.isBlank()) {
        throw new CliValidationException(...);   // fail CLOSED
    }
    return token;
}
```

**2. `presentation/SkillExecutionPreparation.java:70-79`** — same shape, independent path.

**The unauthenticated path is an explicit whitelist, not a fallthrough.** This is the part that
matters: `resolveLocalTarget` (`:55-59`) is reached through a *distinct* `switch` branch on
`ReviewTargetSelection.LocalDirectory`. Local review is deliberately token-free because it never
contacts the GitHub API. A new selection variant added later would not silently inherit the
token-free path — it would have to add its own branch. That is a correct default-deny shape.

**Assessment: PASS.** No finding.

## Token acquisition

Three sources, in precedence order, all constrained:

| Source | Location | Control |
|---|---|---|
| `--token -` (stdin) | `CliParsing.java:130-142` | **Only** `-` accepted. `--token <value>` is *rejected*, so the token cannot land in shell history or `ps` output. 256-byte cap |
| Environment | `GhAuthTokenProvider.java` | Read directly, never echoed |
| `gh auth token` | `GhAuthTokenProvider.java:83` | See below |

Rejecting an inline `--token <value>` is a deliberate, and correct, hardening choice.

## Process execution — the only `ProcessBuilder` in the codebase

`GhAuthTokenProvider.java:83` is the **sole** `ProcessBuilder` in all of `src/main` (verified by
repo-wide grep). Its hardening is layered:

- **Discrete literal args** — no shell, no string concatenation, so no injection surface.
- **Path re-validation** — `CliPathResolver.revalidateExecutionPath` (`:87-99`) re-resolves the
  binary and asserts `realPath.equals(normalized)`. This is an explicit **TOCTOU** check: the
  binary cannot be swapped for a symlink between resolution and execution.
- **Trusted-directory allowlist** — execution is constrained to `TRUSTED_DIRECTORIES`.
- **Child env scrubbed** (`:159-161`) — `GITHUB_TOKEN`, `GH_TOKEN`, `GH_ENTERPRISE_TOKEN` are
  removed before spawn, so the child cannot inherit and re-emit a token.
- **stdout/stderr separated** (`:104` vs `:107`) — stderr is not parsed as token material.

**Assessment: PASS.** This is well above the norm for CLI tooling.

## Session & permission model

Not applicable in the conventional sense — this is a single-user local CLI with no session
lifecycle, no cookies, no CSRF surface, and no multi-user permission model. The `auth-review`
skill's session-management, CSRF and password-policy dimensions are **N/A by architecture**, not
overlooked. Authorization is delegated entirely to GitHub via the bearer token.

## MCP transport

`infrastructure/config/GithubMcpConfig.java:76-126`:

- HTTPS enforced (plaintext rejected)
- Host allowlist
- **CRLF injection guards on both header name and value** — this is the check most
  implementations omit, and it is present here

**Assessment: PASS.**

## Findings originating in this dimension

| ID | Sev | Note |
|---|---|---|
| SEC-M6 | MED | Token lifetime — detailed in [t18-security-secrets.md](./t18-security-secrets.md) |
| SEC-L8 | LOW | `TokenHashUtils.java:33-35` unsalted SHA-256, no production caller. Low value as an attack target, but `MessageDigest.isEqual` appears **nowhere** in the repo — if a token comparison is ever added, it must be constant-time |

## Upstream Artifacts Consumed

- `clarification.md` — confirmed no auth behaviour change was in scope, making any auth regression out-of-contract.
- `t3-pm.md` — behaviour IDs checked for silently-dropped auth behaviour; none found.
- `t4-architect.md` — confirmed token acquisition correctly sits in `infrastructure`, with `presentation` holding only the fail-closed gates.

## Evidence Mapping

| Upstream | → | Evidence here |
|---|---|---|
| Charter: "verify default-deny, do not assume" | → | Both gates traced to source and quoted; whitelist branch identified at `ReviewTargetResolver.java:55-59` |
| `t4-architect.md` layer model | → | Confirmed no auth logic leaked into `domain` |
| `t3-pm.md` behaviour IDs | → | No auth behaviour dropped in the rewrite |

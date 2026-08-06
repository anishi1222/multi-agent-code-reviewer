# Fixing an Inbound Port Implemented in Infrastructure

When infrastructure implements an inbound port, move the *policy* into a use case and leave a new outbound port for the *mechanism* — do not simply relabel the port as outbound.

## What Happened
multi-agent-code-reviewer/t16.1. `infrastructure.auth.GitHubTokenResolver` implemented the inbound
`ResolveTokenPort` directly (ADR-0006 deviation #1). The one-line fix — move `ResolveTokenPort`
into `application.port.outbound` — is wrong: the callers were `presentation` classes, and the
dependency matrix lets presentation reach `application.port.inbound` but **not** `.outbound`. It
would have traded a detected violation for an undetected one, because no rule enforced
presentation's allowlist.

The same shape appeared twice more in one codebase (`ExecuteSkillPort`, `RunReviewPort`), so treat
it as a recurring pattern, not a one-off.

## Takeaway
A port-direction rule has **two** halves. Before "fixing" direction by moving a port, check:
1. **Implementer** — must live in `application` for inbound.
2. **Callers** — moving the port may break *their* allowed-imports row.

If the callers pin the port to inbound, split instead:
- keep the inbound port where it is;
- add a use case in `application` that implements it and holds the **policy** (precedence,
  feature switches, ordering);
- add a **new outbound port** exposing only the **mechanisms**;
- the infrastructure class implements the outbound port and keeps its helpers.

Bonus that justifies the extra type: mechanisms behind a port become **stubbable**. Here the
`gh` CLI branch had never been asserted positively because the old tests shelled out to the real
binary; after the split the use-case test drives it deterministically and can assert
short-circuiting (that the expensive mechanism is *not* called).

## Example
```java
// application/port/outbound — mechanisms only, no policy
public interface AcquireGitHubTokenPort {
    Optional<String> fromProvidedValue(String raw);
    Optional<String> fromGhCli();
}

// application/auth — policy lives here, implements the INBOUND port
public final class ResolveTokenUseCase implements ResolveTokenPort {
    public Optional<String> resolve(String provided) {
        Optional<String> supplied = tokenSource.fromProvidedValue(provided);
        if (supplied.isPresent()) return supplied;
        if (!ghAuthFallbackEnabled) return Optional.empty();  // never consults the CLI
        return tokenSource.fromGhCli();
    }
}
```

## History
- 2026-08-05 (multi-agent-code-reviewer/t16.1): initial

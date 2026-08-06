# Purity-Displaced Capabilities Must Come Back As Ports

When a layer-purity rule evicts a cross-cutting capability, reintroduce it as an outbound port — never let it be silently dropped.

## What Happened

In `rearchitecture/t16` the domain-purity rule ("`domain` and `application` may import `java.*` and
`shared` only") evicted SLF4J from those layers. Nothing replaced it, so the code silently
downgraded to `java.util.logging`: 4 files in `domain` and 10 in `application`, while
`presentation` (10) and `infrastructure` (29) kept SLF4J.

The result was worse than either extreme: correlation IDs and MDC scoping were lost precisely in
the layers that orchestrate parallel work, and the split was invisible because it produced no rule
violation. Purity was satisfied; observability was not.

## Takeaway

- Adopt this as a **standing rule**, not a one-off fix: any cross-cutting technical capability
  displaced by a purity rule MUST be reintroduced as an `application.port.outbound` port with an
  `infrastructure` adapter. It must never be silently dropped or downgraded to a weaker JDK API.
- The rule pre-binds future capabilities too — metrics, tracing, feature flags, clock — so the same
  argument doesn't have to be re-fought each time.
- Specify the *capabilities* the port must preserve, not just the method names. For logging that
  meant: (1) correlation-scope begin/end mapped to MDC inside the adapter, (2) propagation into
  virtual threads, (3) leveled diagnostic output.
- A silent capability downgrade produces **zero** rule violations. Detect it by diffing which
  framework each layer actually imports, not by re-running the layer tests.
- Not every split is a failure: a deliberate split (pure sanitizer in `shared`, side-effecting
  audit in `presentation`) should be documented as intentional so it isn't "fixed" later.

## History
- 2026-08-05 (rearchitecture/t16): initial — recorded as ADR-0006 D4. First application landed in
  t13.1 as `PropagateCorrelationPort` + `MdcCorrelationAdapter`, scoped to correlation propagation;
  log *emission* in `domain`/`application` is still on `java.util.logging`, so the displaced
  capability was only partly restored.

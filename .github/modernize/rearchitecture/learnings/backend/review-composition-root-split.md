# Split Review Wiring From Settings And SDK Construction

Bind inbound review behavior at layer zero, keep external settings and SDK creation behind outbound ports, and never echo request credentials through a settings adapter.

## What Happened

In `multi-agent-code-reviewer` t16.3, infrastructure `ReviewOrchestratorFactory` implemented inbound
`RunReviewPort`, mapped Micronaut configuration, constructed SDK adapters, and instantiated the
application orchestrator. Moving that class to the root would only hide the inversion.

The fix added a wiring-only root `@Factory`, made the application orchestrator the resolved inbound
implementation, and split infrastructure into settings-resolution and session-adapter outbound
ports. An initial broad configuration DTO was narrowed after review because it unnecessarily sent
the GitHub token and invocation correlation values out to infrastructure and back.

In t17.2 the same pattern closed the remaining application-wide factory deviation. The stable root
entry point kept only process startup, CLI behavior moved to presentation, and provenance, config,
filesystem, SDK, and logging operations became focused infrastructure adapters. With the source
exception gone, all generated Rule 4 exceptions and their derivation helper disappeared too.

## Takeaway

- A layer-zero factory may name application implementations and outbound ports, but must only wire.
- Keep a singleton application orchestrator invocation-safe by resolving settings and creating
  stateful SDK adapters inside each call.
- A settings port should carry externally owned settings only. Credentials, timestamps, and other
  request-owned values stay in the application and are combined there.
- Prove both halves: a static rule rejects infrastructure inbound implementations, while a
  container test asserts the concrete bean Micronaut actually resolves.
- Prefer eliminating a factory exception over making generated exceptions durable; the desired
  end-state is zero source and zero generated exemptions.

## History

- 2026-08-07 (multi-agent-code-reviewer/t16.3): initial.
- 2026-08-07 (multi-agent-code-reviewer/t17.2): generalized to application startup and removed the
  final factory/generated-definition exemptions.

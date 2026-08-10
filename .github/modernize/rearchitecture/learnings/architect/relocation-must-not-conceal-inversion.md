# Relocation Must Not Conceal a Dependency Inversion

A file move is not remediation when it only places the same wrong-direction implementation inside a broader rule exemption.

## What Happened

In `multi-agent-code-reviewer/t16.2`, ADR-0006 D3 said three `*Factory` classes should move into
the composition root because they were Micronaut factories. Source inspection disproved that:
only `ApplicationPortFactory` has `@Factory`; `ReviewContextFactory` maps configuration; and
`ReviewOrchestratorFactory` is a `@Singleton` implementing inbound `RunReviewPort`.

Moving the last class to the root would make Rule 4 green while leaving infrastructure as the
inbound-port implementation. The defect would become harder to see, not fixed. D3 was corrected,
and the inversion was recorded as deviation #8 pending a dedicated review-path refactor.

## Takeaway

- Classify a relocation candidate by responsibility, annotations, and implemented ports—not its name.
- The composition root may wire an inbound implementation but must not itself become that implementation.
- Close a direction defect only when the DI-visible inbound implementation belongs to `application`
  and the corresponding exemption is removed.
- A rule that fails on file location but passes on the same inversion after a move guards the wrong property.

## History

- 2026-08-07 (multi-agent-code-reviewer/t16.2): initial

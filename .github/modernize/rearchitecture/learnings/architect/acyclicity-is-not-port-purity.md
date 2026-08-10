# Acyclicity Is Not Port Purity

A cycle rule can incidentally reject a wrong-way port dependency without enforcing the port's allowed-import row.

## What Happened

ADR-0006 says `application.port` may depend on domain/shared but must not depend on application
implementations. The current bytecode suite had Rule 5 over all application classes and Rule 6b over
application subpackages, so the port row appeared indirectly covered:

- Rule 5 rejects adapter dependencies, but permits application-to-application dependencies.
- A port importing an existing use-case package often creates a cycle because that use case already
  imports the port; Rule 6b then rejects it.

That second effect is accidental. In t17 an isolated mutant added a new
`application.policy.T17ApplicationPolicy` with no back-edge and made an inbound port class reference
it. The graph stayed acyclic and all 15 architecture tests passed. The explicit matrix row had no
rule even though the ADR's correspondence table claimed one-to-one coverage.

## Takeaway

- For every allowed-import row, encode its forbidden set directly; never rely on another invariant
  such as acyclicity to reject common examples.
- Mutation-test the **one-way** form of a forbidden edge. A bidirectional mutant proves the cycle
  detector, not the import boundary.
- For `application.port`, select port classes directly and reject any `application.*` target outside
  `application.port`, with zero exemptions and a non-empty subject assertion.
- A green zero-cycle graph says nothing about whether every individual edge is legal.

## History

- 2026-08-07 (`multi-agent-code-reviewer`/t17): initial; a new one-way
  `application.port -> application.policy` edge passed all existing architecture rules.

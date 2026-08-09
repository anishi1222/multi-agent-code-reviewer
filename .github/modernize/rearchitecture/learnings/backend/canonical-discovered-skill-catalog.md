# Canonical Discovered-Skill Catalog

Publish the exact skill-discovery result through one outbound catalog port instead of rescanning or wiring registry methods into use cases.

## What Happened

In `multi-agent-code-reviewer` task t22.1, `AgentConfigLoader` parsed global skills for agent
enrichment, but `ExecuteSkillUseCase` queried a separate empty registry. Re-running discovery in
the execution path would have introduced ordering, validation, and failure-policy drift.

The loader report was extended with the valid skills from its existing pass. The filesystem
adapter publishes that complete result through `ManageSkillCatalogPort`, and skill listing and
execution query the same port.

## Takeaway

Keep discovery in infrastructure and expose its result as one immutable, ordered snapshot.
Replace the catalog atomically on each completed scan rather than appending: this removes stale
entries, avoids partially refreshed reads, and preserves one validation policy. Application
factories should depend on the port, never concrete registry method references.

## History

- 2026-08-09 (`multi-agent-code-reviewer`/t22.1): initial

# ADR-0005: Official Azure Skills and Microsoft Learn MCP for WAF Reviews

| Field     | Value                |
|-----------|----------------------|
| Status    | Accepted             |
| Date      | 2026-05-28           |
| Deciders  | Project maintainers  |

## Context

The project previously carried project-local Azure/WAF `SKILL.md` files under `.github/skills/`. Those skills captured useful review viewpoints, but they had two limitations:

- Azure operational guidance could drift from Microsoft's official Azure Skills Plugin.
- WAF (Azure Well-Architected Framework) review findings could be based on static prompt text rather than current Microsoft Learn documentation.

The project needs Azure skill behavior that is:

1. Aligned with the official Microsoft-maintained `microsoft/azure-skills` repository.
2. Usable by Copilot CLI users who may or may not have the Azure Skills Plugin installed globally.
3. Grounded in official Microsoft Learn WAF documentation for Security, Reliability, Cost Optimization, Operational Excellence, and Performance Efficiency reviews.
4. Reproducible for contributors through tracked project configuration.

## Decision

We adopt a hybrid Azure Skills strategy:

1. **Prefer the official Azure Skills Plugin for Copilot CLI users.**
   - Users should install it with:
     - `/plugin marketplace add microsoft/azure-skills`
     - `/plugin install azure@azure-skills`
   - Existing installs should be refreshed with `/plugin update azure@azure-skills`.

2. **Keep a project-level fallback copy of official Azure skills.**
   - The official Azure skills are copied into `.agents/skills/`.
   - `skills-lock.json` records the upstream source, ref, and computed hashes.
   - This lets the repository work in environments where the plugin is unavailable or not yet installed.

3. **Configure MCP servers in the workspace.**
   - `.vscode/mcp.json` defines:
     - `azure`: `npx -y @azure/mcp@latest server start`
     - `microsoft-learn`: `https://learn.microsoft.com/api/mcp`
   - `.gitignore` explicitly allows tracking `.vscode/mcp.json` and `.vscode/settings.json`.

4. **Keep project-specific WAF skills as Microsoft Learn MCP-grounded wrappers.**
   - `.github/skills/waf-*` remains project-specific because it maps to this repository's WAF review agents and output format.
   - Each WAF skill requires Microsoft Learn MCP searches/fetches before recommendations are made.
   - Findings must cite the Microsoft Learn URL used as the source of WAF guidance.

5. **Document setup behavior for unconfigured users.**
   - `.github/instructions/azure-skills.instructions.md` tells agents how to guide users who have not installed Azure Skills or Microsoft Learn MCP.

## Alternatives Considered

1. **Only use the Azure Skills Plugin, with no repository copy**
   - Pros: smallest repository footprint and always managed by the plugin.
   - Cons: contributors without the plugin would not have reproducible skill behavior from the repository alone.

2. **Only keep project-local custom Azure/WAF skills**
   - Pros: simple and fully controlled by the repository.
   - Cons: higher drift risk from official Azure guidance and no alignment with Microsoft-maintained Azure MCP workflows.

3. **Replace WAF skills entirely with generic official Azure skills**
   - Pros: minimal custom skill maintenance.
   - Cons: loses the repository's WAF-specific agent routing and output format, and does not guarantee Microsoft Learn MCP grounding per WAF finding.

4. **Use generic web search for WAF references**
   - Pros: no MCP setup required.
   - Cons: weaker provenance and greater hallucination/supply-chain risk than using the first-party Microsoft Learn MCP endpoint.

## Consequences

### Positive

- Azure implementation, deployment, diagnostics, cost, identity, and platform work can use official Microsoft-maintained skills.
- WAF findings are grounded in current Microsoft Learn documentation rather than static prompts.
- Copilot CLI users get a clear plugin install path, while project contributors retain a fallback skill copy.
- MCP server configuration is visible and reviewable in source control.
- `skills-lock.json` improves traceability of imported skill content.

### Negative / Trade-offs

- The repository carries a larger `.agents/skills/` payload.
- Official skill updates require an explicit refresh and review of `skills-lock.json` changes.
- WAF review quality depends on MCP availability; fallback CLI searches are acceptable only when MCP is unavailable.
- Contributors may need Node.js 18+, `npx`, Azure CLI, and Azure authentication for some Azure MCP workflows.

### Risks

- Project-local fallback skills can become stale if not periodically updated from `microsoft/azure-skills`.
- Some Azure MCP tools perform live Azure operations; destructive changes still require explicit user approval.
- The Microsoft Learn MCP endpoint is external infrastructure; outage handling must use documented fallback commands.

## Operational Notes

- Install or refresh Azure Skills in Copilot CLI:
  - `/plugin marketplace add microsoft/azure-skills`
  - `/plugin install azure@azure-skills`
  - `/plugin update azure@azure-skills`
- Install Microsoft Learn MCP for WAF grounding if missing:
  - `/plugin install microsoftdocs/mcp`
- Verify runtime environment:
  - `/skills`
  - `/mcp show`
- Refresh the project-level fallback copy:

```bash
npx skills add https://github.com/microsoft/azure-skills/tree/main/.github/plugins/azure-skills/skills -a github-copilot --skill '*' --copy -y
```

- Validate customization files after refresh:
  - skill / instruction frontmatter YAML validation
  - `skills-lock.json` and `.vscode/mcp.json` JSON validation
  - skill folder/name consistency check

## References

- [Azure Skills Plugin](https://github.com/microsoft/azure-skills)
- [Microsoft Learn MCP Server](https://github.com/MicrosoftDocs/mcp)
- [Microsoft Learn MCP endpoint](https://learn.microsoft.com/api/mcp)
- [Release v2026.05.28-azure-skills-mcp](https://github.com/anishi1222/multi-agent-code-reviewer/releases/tag/v2026.05.28-azure-skills-mcp)

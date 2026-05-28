---
description: "Use when: Azure Skills Plugin, azure-skills, Azure official skills, Microsoft Learn MCP, WAF skills, or missing Azure skill installation is discussed."
---

# Azure Skills Plugin setup guidance

When a user needs the official Azure skills but their Copilot CLI environment has not installed them yet, do not assume they are available. Tell the user to run these Copilot CLI slash commands:

```text
/plugin marketplace add microsoft/azure-skills
/plugin install azure@azure-skills
```

If the marketplace was already added, the first command can be skipped. To refresh an existing install, use:

```text
/plugin update azure@azure-skills
```

After installation, ask the user to verify the environment with:

```text
/skills
/mcp show
```

The Azure Skills Plugin should provide the Azure skills layer and configure the Azure MCP server. Azure MCP operations require local prerequisites such as Node.js 18+ with `npx`, Azure CLI, and `az login`.

## WAF skill requirement

The WAF skills in this repository must be grounded in official Microsoft Learn documentation through the Microsoft Learn MCP Server. If `microsoft-learn` is not shown by `/mcp show`, tell Copilot CLI users to install the Microsoft Docs MCP plugin:

```text
/plugin install microsoftdocs/mcp
```

The Microsoft Learn MCP endpoint is:

```text
https://learn.microsoft.com/api/mcp
```

For WAF reviews, the agent must use Microsoft Learn MCP tools such as `microsoft_docs_search` and `microsoft_docs_fetch` before making Well-Architected Framework recommendations, and include the referenced Microsoft Learn URL in findings.

## Project fallback

If plugin installation is not possible, this repository also tracks project-level Azure skills under `.agents/skills/` and locks their source in `skills-lock.json`. To recreate that project-level copy, use:

```bash
npx skills add https://github.com/microsoft/azure-skills/tree/main/.github/plugins/azure-skills/skills -a github-copilot --skill '*' --copy -y
```

Prefer the plugin install path for normal Copilot CLI users because it keeps the official Azure skills and MCP wiring managed by the plugin.

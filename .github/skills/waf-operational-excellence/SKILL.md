---
name: waf-operational-excellence
description: "Azure Well-Architected Framework の Operational Excellence pillar を Microsoft Learn MCP Server の公式ドキュメントで根拠付けてレビューします。Use when: WAF operational excellence, IaC, CI/CD, observability, App Insights, runbook, deployment."
metadata:
  agent: waf-operational-excellence
  mcpServers:
    - microsoft-learn
---

# Operational Excellence pillar review with Microsoft Learn MCP

以下のリポジトリを Azure Well-Architected Framework の **Operational Excellence pillar** に基づいてレビューしてください。

**対象リポジトリ**: ${repository}

## MCP grounding requirement

この skill は `.vscode/mcp.json` の `microsoft-learn` server（`https://learn.microsoft.com/api/mcp`）を前提にします。レビュー前に必ず Microsoft Learn MCP Server で公式ドキュメントを検索・取得し、指摘には参照 URL を含めてください。

優先ツール:

1. `microsoft_docs_search` — WAF Operational Excellence pillar、design principles、checklists、Azure サービス別ベストプラクティスを検索する。
2. `microsoft_docs_fetch` — 採用する根拠ページを取得する。
3. MCP が利用できない場合のみ、`npx @microsoft/learn-cli search "Azure Well-Architected Framework operational excellence pillar"` を代替として使う。

## Required searches

- `Azure Well-Architected Framework operational excellence pillar design principles`
- `Azure Well-Architected Framework operational excellence checklist`
- `Azure IaC CI/CD observability Application Insights runbook best practices`

## Review focus

- Infrastructure as Code、環境差分、再現性
- CI/CD、段階的リリース、rollback、feature flags
- Azure Monitor / Application Insights / OpenTelemetry
- 構造化ログ、分散トレーシング、アラート
- Runbook、運用手順、ADR、構成外部化
- 運用自動化と変更管理

## Output

指摘ごとに以下を出力してください。

| 項目 | 内容 |
|------|------|
| **WAF pillar** | Operational Excellence |
| **Priority** | Critical / High / Medium / Low |
| **指摘の概要** | 問題の要約 |
| **該当箇所** | ファイルパスと行番号 |
| **推奨対応** | 具体的な修正案 |
| **Microsoft Learn MCP 参照** | `microsoft_docs_fetch` で確認した URL と根拠 |

指摘がない場合は「指摘事項なし」と記載してください。

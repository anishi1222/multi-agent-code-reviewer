---
name: waf-security
description: "Azure Well-Architected Framework の Security pillar を Microsoft Learn MCP Server の公式ドキュメントで根拠付けてレビューします。Use when: WAF security, Well-Architected security, managed identity, Key Vault, Zero Trust, RBAC least privilege, private endpoint."
metadata:
  agent: waf-security
  mcpServers:
    - microsoft-learn
---

# Security pillar review with Microsoft Learn MCP

以下のリポジトリを Azure Well-Architected Framework の **Security pillar** に基づいてレビューしてください。

**対象リポジトリ**: ${repository}

## MCP grounding requirement

この skill は `.vscode/mcp.json` の `microsoft-learn` server（`https://learn.microsoft.com/api/mcp`）を前提にします。レビュー前に必ず Microsoft Learn MCP Server で公式ドキュメントを検索・取得し、指摘には参照 URL を含めてください。

優先ツール:

1. `microsoft_docs_search` — WAF Security pillar、design principles、checklists、Azure サービス別ベストプラクティスを検索する。
2. `microsoft_docs_fetch` — 採用する根拠ページを取得する。
3. MCP が利用できない場合のみ、`npx @microsoft/learn-cli search "Azure Well-Architected Framework security pillar"` を代替として使う。

## Required searches

- `Azure Well-Architected Framework security pillar design principles`
- `Azure Well-Architected Framework security checklist`
- `Azure managed identity Key Vault RBAC least privilege private endpoint best practices`

## Review focus

- Managed ID と Key Vault によるシークレット管理
- Zero Trust 原則、境界防御、最小権限 RBAC
- Private Endpoint / ネットワーク分離
- 保存時・転送時暗号化
- 監査ログ、脅威検出、Microsoft Defender for Cloud
- 入力検証、依存関係、サプライチェーン保護

## Output

指摘ごとに以下を出力してください。

| 項目 | 内容 |
|------|------|
| **WAF pillar** | Security |
| **Priority** | Critical / High / Medium / Low |
| **指摘の概要** | 問題の要約 |
| **該当箇所** | ファイルパスと行番号 |
| **推奨対応** | 具体的な修正案 |
| **Microsoft Learn MCP 参照** | `microsoft_docs_fetch` で確認した URL と根拠 |

指摘がない場合は「指摘事項なし」と記載してください。

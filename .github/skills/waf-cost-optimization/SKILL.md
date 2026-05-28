---
name: waf-cost-optimization
description: "Azure Well-Architected Framework の Cost Optimization pillar を Microsoft Learn MCP Server の公式ドキュメントで根拠付けてレビューします。Use when: WAF cost optimization, Azure cost, SKU, autoscale, reserved capacity, idle resources, FinOps."
metadata:
  agent: waf-cost-optimization
  mcpServers:
    - microsoft-learn
---

# Cost Optimization pillar review with Microsoft Learn MCP

以下のリポジトリを Azure Well-Architected Framework の **Cost Optimization pillar** に基づいてレビューしてください。

**対象リポジトリ**: ${repository}

## MCP grounding requirement

この skill は `.vscode/mcp.json` の `microsoft-learn` server（`https://learn.microsoft.com/api/mcp`）を前提にします。レビュー前に必ず Microsoft Learn MCP Server で公式ドキュメントを検索・取得し、指摘には参照 URL を含めてください。

優先ツール:

1. `microsoft_docs_search` — WAF Cost Optimization pillar、design principles、checklists、Azure サービス別ベストプラクティスを検索する。
2. `microsoft_docs_fetch` — 採用する根拠ページを取得する。
3. MCP が利用できない場合のみ、`npx @microsoft/learn-cli search "Azure Well-Architected Framework cost optimization pillar"` を代替として使う。

## Required searches

- `Azure Well-Architected Framework cost optimization pillar design principles`
- `Azure Well-Architected Framework cost optimization checklist`
- `Azure cost optimization autoscale SKU reserved capacity tagging best practices`

## Review focus

- SKU / plan / tier の過剰または過小プロビジョニング
- Autoscale、serverless、consumption model の活用
- Reserved capacity / Savings Plans / Spot の適用余地
- リソースタグ、予算、コスト可視化
- ログ、メトリクス、保持期間のコスト効率
- ストレージ階層、データ転送、アイドルリソース

## Output

指摘ごとに以下を出力してください。

| 項目 | 内容 |
|------|------|
| **WAF pillar** | Cost Optimization |
| **Priority** | Critical / High / Medium / Low |
| **指摘の概要** | 問題の要約 |
| **該当箇所** | ファイルパスと行番号 |
| **推奨対応** | 具体的な修正案 |
| **Microsoft Learn MCP 参照** | `microsoft_docs_fetch` で確認した URL と根拠 |

指摘がない場合は「指摘事項なし」と記載してください。

---
name: waf-performance-efficiency
description: "Azure Well-Architected Framework の Performance Efficiency pillar を Microsoft Learn MCP Server の公式ドキュメントで根拠付けてレビューします。Use when: WAF performance efficiency, cache, CDN, async messaging, scale, query optimization, latency."
metadata:
  agent: waf-performance-efficiency
  mcpServers:
    - microsoft-learn
---

# Performance Efficiency pillar review with Microsoft Learn MCP

以下のリポジトリを Azure Well-Architected Framework の **Performance Efficiency pillar** に基づいてレビューしてください。

**対象リポジトリ**: ${repository}

## MCP grounding requirement

この skill は `.vscode/mcp.json` の `microsoft-learn` server（`https://learn.microsoft.com/api/mcp`）を前提にします。レビュー前に必ず Microsoft Learn MCP Server で公式ドキュメントを検索・取得し、指摘には参照 URL を含めてください。

優先ツール:

1. `microsoft_docs_search` — WAF Performance Efficiency pillar、design principles、checklists、Azure サービス別ベストプラクティスを検索する。
2. `microsoft_docs_fetch` — 採用する根拠ページを取得する。
3. MCP が利用できない場合のみ、`npx @microsoft/learn-cli search "Azure Well-Architected Framework performance efficiency pillar"` を代替として使う。

## Required searches

- `Azure Well-Architected Framework performance efficiency pillar design principles`
- `Azure Well-Architected Framework performance efficiency checklist`
- `Azure caching CDN autoscale async messaging query optimization best practices`

## Review focus

- Cache / CDN / compression / connection pooling
- Autoscale、load testing、capacity planning
- 非同期メッセージング、backpressure、queue-based load leveling
- データベース partitioning、index、query optimization
- レイテンシ、throughput、resource limits
- パフォーマンス計測と継続的チューニング

## Output

指摘ごとに以下を出力してください。

| 項目 | 内容 |
|------|------|
| **WAF pillar** | Performance Efficiency |
| **Priority** | Critical / High / Medium / Low |
| **指摘の概要** | 問題の要約 |
| **該当箇所** | ファイルパスと行番号 |
| **推奨対応** | 具体的な修正案 |
| **Microsoft Learn MCP 参照** | `microsoft_docs_fetch` で確認した URL と根拠 |

指摘がない場合は「指摘事項なし」と記載してください。

---
name: waf-reliability
description: "Azure Well-Architected Framework の Reliability pillar を Microsoft Learn MCP Server の公式ドキュメントで根拠付けてレビューします。Use when: WAF reliability, resilience, retry, timeout, health probe, disaster recovery, zone redundancy, failover."
metadata:
  agent: waf-reliability
  mcpServers:
    - microsoft-learn
---

# Reliability pillar review with Microsoft Learn MCP

以下のリポジトリを Azure Well-Architected Framework の **Reliability pillar** に基づいてレビューしてください。

**対象リポジトリ**: ${repository}

## MCP grounding requirement

この skill は `.vscode/mcp.json` の `microsoft-learn` server（`https://learn.microsoft.com/api/mcp`）を前提にします。レビュー前に必ず Microsoft Learn MCP Server で公式ドキュメントを検索・取得し、指摘には参照 URL を含めてください。

優先ツール:

1. `microsoft_docs_search` — WAF Reliability pillar、design principles、checklists、Azure サービス別ベストプラクティスを検索する。
2. `microsoft_docs_fetch` — 採用する根拠ページを取得する。
3. MCP が利用できない場合のみ、`npx @microsoft/learn-cli search "Azure Well-Architected Framework reliability pillar"` を代替として使う。

## Required searches

- `Azure Well-Architected Framework reliability pillar design principles`
- `Azure Well-Architected Framework reliability checklist`
- `Azure retry timeout health probes disaster recovery zone redundancy best practices`

## Review focus

- リトライ、指数バックオフ、サーキットブレーカー
- タイムアウト、キャンセル、冪等性
- Health check / readiness / liveness
- Zone redundancy、multi-region failover、DR
- 障害分離、graceful degradation
- バックアップ、復旧手順、データ整合性

## Output

指摘ごとに以下を出力してください。

| 項目 | 内容 |
|------|------|
| **WAF pillar** | Reliability |
| **Priority** | Critical / High / Medium / Low |
| **指摘の概要** | 問題の要約 |
| **該当箇所** | ファイルパスと行番号 |
| **推奨対応** | 具体的な修正案 |
| **Microsoft Learn MCP 参照** | `microsoft_docs_fetch` で確認した URL と根拠 |

指摘がない場合は「指摘事項なし」と記載してください。

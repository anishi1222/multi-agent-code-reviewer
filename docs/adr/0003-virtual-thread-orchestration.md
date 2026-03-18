# 0003: Orchestrate agent execution with virtual threads and structured concurrency

- Status: Accepted
- Date: 2026-03-18
- Deciders: Multi-Agent Code Reviewer maintainers
- Tags: concurrency, orchestration, operations

## Context

複数エージェントを並列実行するため、スループットと可読性のバランスが必要です。  
将来的なパス並列化やタイムアウト制御も見据え、実行制御の一貫性を保つ必要があります。

## Decision

オーケストレーション実装は、仮想スレッド + 構造化並行（structured concurrency）ベースを維持します。  
関連ヘルパー (`StructuredConcurrencyUtils` など) を共通化し、タイムアウト/キャンセル制御を集中管理します。

## Alternatives considered

1. 固定スレッドプール + CompletableFuture 中心  
2. 完全同期実行（シリアル）

## Consequences

### Positive

- エージェント数増加時のスケール特性が良い
- タイムアウト・キャンセルを一元的に扱いやすい
- コードフローが「開始→待機→集約」で追跡しやすい

### Negative / Trade-offs

- JDK 26 preview 依存のため、実行環境要件を満たす必要がある
- ログ/トレース整備が不足すると並列時の原因追跡が難しい

## Operational notes

- 並列度は `application.yml` の `reviewer.execution.concurrency.parallelism` で制御
- 大規模レビュー時はタイムアウト設定を併せて見直す
- 障害解析しやすいよう、MDC のイベントカテゴリを維持する

## References

- `src/main/java/dev/logicojp/reviewer/orchestrator/ReviewOrchestrator.java`
- `src/main/java/dev/logicojp/reviewer/util/StructuredConcurrencyUtils.java`

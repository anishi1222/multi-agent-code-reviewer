# 0001: Keep custom CLI parser and command dispatch

- Status: Accepted
- Date: 2026-03-18
- Deciders: Multi-Agent Code Reviewer maintainers
- Tags: cli, architecture

## Context

本プロジェクトは `run` / `list` / `skill` を持つ軽量 CLI です。  
標準ライブラリ + Micronaut DI を中心に依存を抑え、起動を軽く保つ方針があります。

## Decision

`ReviewApp` と `dev.logicojp.reviewer.cli` パッケージによる独自 CLI 解析・ディスパッチを継続します。  
外部 CLI フレームワーク（Picocli 等）は採用しません。

## Alternatives considered

1. Picocli などのフレームワーク導入  
2. Spring Shell などの対話型 CLI 基盤への移行

## Consequences

### Positive

- 依存追加を抑制できる
- 実行経路が単純でデバッグしやすい
- 現在のヘルプ表示/終了コード制御を維持しやすい

### Negative / Trade-offs

- 引数解析ロジックを自前保守する必要がある
- サブコマンド拡張時にテスト/ドキュメント更新が必須

## Operational notes

- CLI オプション追加時は `CliUsage` と README の同時更新を必須運用とする
- 破壊的変更時は ADR を更新し、運用手順に影響を明記する

## References

- `src/main/java/dev/logicojp/reviewer/ReviewApp.java`
- `src/main/java/dev/logicojp/reviewer/cli/`

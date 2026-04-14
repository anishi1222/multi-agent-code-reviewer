# 0002: Use Micronaut dependency injection as composition backbone

- Status: Accepted
- Date: 2026-03-18
- Deciders: Multi-Agent Code Reviewer maintainers
- Tags: micronaut, di, architecture

## Context

アプリケーションは多数のサービス（オーケストレーター、テンプレート、レポート生成、Copilot クライアント補助）で構成されます。  
テスト容易性と低オーバーヘッドを両立する DI が必要です。

## Decision

`io.micronaut` を DI コンテナとして継続採用し、`@Singleton` とコンストラクタ注入を標準とします。

## Alternatives considered

1. 手動 DI（new 連鎖）へ回帰  
2. 別コンテナ（Spring 等）への移行

## Consequences

### Positive

- 依存関係の構成が明示され、テスト差し替えが容易
- ネイティブイメージ適性と相性が良い
- モジュール間責務の境界を保ちやすい

### Negative / Trade-offs

- Micronaut アノテーション・ライフサイクルへの理解が必要
- DI 起因の構成不整合は起動時に検知されるため、CI での起動検証が必要

## Operational notes

- 新規コンポーネントは原則コンストラクタ注入を使用する
- 依存追加時は `./mvnw -B -ntp test` を CI で必須化する

## References

- `src/main/java/dev/logicojp/reviewer/ReviewApp.java`
- `pom.xml` (`micronaut-inject`, `micronaut-parent`)

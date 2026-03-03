# リリースノート

## 更新ルール（テンプレート）

参照チェックリスト: `reports/anishi1222/multi-agent-code-reviewer/documentation_sync_checklist_2026-02-17.md`

1. EN/JA 両方に同一構成で新しい日付セクションを追加する。
2. 注釈付きタグ（例: `vYYYY.MM.DD-notes`）を作成して push する。
3. タグから GitHub Release を作成し、EN/JA 要約を本文に含める。
4. `README_en.md` と `README_ja.md` にリリース参照とURLを追記する。

## 2026-03-03 (v2026.03.03-notes)

### 概要
- 総評のレベルごと指摘件数が実際のマージ済みindingsと一致するよう、レポート生成パイプラインを再構成しました。
- コード品質レポート推奨対応として DRY・責務分離・Optional化・型安全化等を実装しました。

### 主な変更

#### レポート生成フロー改善
- パスごとにレビューレポートを生成（総評なし）。
- 全パス終了後に `ReviewResultMerger` でエージェント単位にマージ。
- マージ済みレポート本文から指摘件数を再集計し、新規 `ReviewOverallSummaryAppender` で正確な総評を追記。
- エグゼクティブサマリーで複数エージェント間の同一指摘を統合し、レビューカテゴリを列挙。
- `ReviewResultPipeline` からマージ責務を分離し、`ReviewRunExecutor` でマージ・総評追加を実施。

#### コード品質リメディエーション
- `ReviewAgent` のレビューパスパラメータ解決の重複を削減（`ResolvedReviewParams`）。
- `LocalSourcePrecomputer` の nullable 返却を `Optional` に変更し、オーケストレータに伝播。
- `FindingPriority` enum と `EnumMap` ベースの優先度カウントを導入。
- `AgentConfigLoader.loadAgentsInternal` をフェーズ分割。
- `CopilotClientStarter` のリトライ/バックオフ重複を削減。
- `ExecutorResources.shutdownGracefully()` によるクリーンアップ集約。
- 新規テスト: `RetryExecutorTest`, `CircuitBreakerFactoryTest`, `ExecutorResourcesTest`, `OrchestratorConfigTest`, `ReviewOverallSummaryAppenderTest`。

#### PRチェーン
- [#72](https://github.com/anishi1222/multi-agent-code-reviewer/pull/72): コード品質レポート推奨対応
- [#73](https://github.com/anishi1222/multi-agent-code-reviewer/pull/73): 総評の件数一致のためのレポート生成フロー修正

### 検証
- `mvn test` — 838テスト合格、0失敗
- `mvn clean package` — ビルド成功

---

## 2026-03-02 (v2026.03.02-notes)

### 概要
- マージ後findingsを基準に総評を生成するためのレビュー統合リメディエーションを完了しました。
- 重複指摘の統合と総評生成の挙動を、全レポート経路で一貫する形に調整しました。
- `pom.xml` の Micronaut を 4.10.9 に更新し、ビルドと実行検証を実施しました。

### 主な変更

#### レポート統合と総評の整合
- レポートパイプラインにおける重複指摘の統合挙動を統一。
- マージ後findingsを基準とした単一の `総評` セクションを追記する方式に統一。
- パス単位の総評統合に依存せず、マージ後レポート単位で総評を生成する仕様へ整理。

#### PRチェーン
- [#57](https://github.com/anishi1222/multi-agent-code-reviewer/pull/57): 重複指摘とマージ後総評生成を修正
- [#58](https://github.com/anishi1222/multi-agent-code-reviewer/pull/58): 全レポートにマージ後総評を反映
- [#59](https://github.com/anishi1222/multi-agent-code-reviewer/pull/59): マージ済みfindings基準で総評を生成

#### 依存関係ベースライン更新
- `pom.xml` の Micronaut バージョンを `4.10.9` へ更新。

### 検証
- ローカルビルド成功: `mvn clean package`。
- Native Image tracing agent 有効での end-to-end レビュー実行成功。

---

## 2026-02-19 Daily Rollup

### エグゼクティブサマリー
- 同日内で v1 から v5 まで段階的にリメディエーションと文書同期を完了しました。
- 最終状態として、コード・CI・RELEASE_NOTES・README EN/JA 参照がすべて整合しています。

### 進行概要（v1 → v5）
- v1: マルチパスレビュー性能改善（`CopilotSession` 共有再利用）。
- v2: CodeQL ワークフローの Java 25 方針整合（JDK 26 → 25）。
- v3: idle-timeout scheduler の停止/拒否経路に対する信頼性強化。
- v4: 運用完了チェックの同期（2026-02-19 基準化、PR #76 の追跡性明示）。
- v5: v2-v4 の運用サマリーを README EN/JA に簡潔追記。

### 主要PRチェーン
- 信頼性・コード経路: [#74](https://github.com/anishi1222/multi-agent-code-reviewer/pull/74), [#76](https://github.com/anishi1222/multi-agent-code-reviewer/pull/76)
- ドキュメント・リリース同期: [#78](https://github.com/anishi1222/multi-agent-code-reviewer/pull/78), [#79](https://github.com/anishi1222/multi-agent-code-reviewer/pull/79), [#80](https://github.com/anishi1222/multi-agent-code-reviewer/pull/80), [#81](https://github.com/anishi1222/multi-agent-code-reviewer/pull/81)

---

## 2026-02-19 (v12)

### 概要
- v9〜v12 のベストプラクティスリメディエーションサイクルを完了しました。
- キャッシュ管理の簡素化、CLI I/O の抽象化、ロギング精度の改善、並行設計ドキュメントの強化を実施しました。

### 主な変更

#### PR #95: ベストプラクティスリメディエーション v12
- `TemplateService`: 決定的LRU挙動を維持しつつキャッシュ同期を簡素化。
- `SkillService`: 手動実行器キャッシュ管理を Caffeine ライブラリ（エビクション時クローズ）に置換。
- `CliParsing`: `TokenInput` インターフェースでCLIトークン読込をシステムI/Oから抽象化。
- `ContentCollector`: 連結キャッシュロックを簡素化。
- `AgentMarkdownParser`: セクション解析の可読性を改善（Iterableキャストトリック除去）。
- `ReviewExecutionModeRunner`: マルチパス開始ログを実行実態に一致。
- `GithubMcpConfig`: Mapラッパー委譲メソッドを補完（`isEmpty`/`containsValue`/`keySet`/`values`）。
- `ReviewResult`: デフォルトtimestamp処理を簡素化。
- `SkillExecutor`: FQCNユーティリティ呼出しを除去（`ExecutorUtils` import）。
- `CopilotService` / `ReviewOrchestrator`: 並行/スレッド設計意図ドキュメントを強化。
- `pom.xml` に Caffeine 依存を追加。
- `CliParsingTest` に `TokenInput` 抽象化テストを追加。

#### PR #96: ドキュメント同期
- README EN/JA のプロジェクト構造ツリーとアーキテクチャMermaid図を v7 → v12 へ更新。
- `.github/workflows/` セクション、オーケストレータ新規8ファイル、`SecurityAuditLogger`、`TokenHashUtils` を追加。

### 検証
- 756テスト、0失敗。
- CI 5/5 合格（両PR）。

### マージ済み PR
- [#95](https://github.com/anishi1222/multi-agent-code-reviewer/pull/95): fix: remediate best-practices findings v12
- [#96](https://github.com/anishi1222/multi-agent-code-reviewer/pull/96): docs: sync project structure and architecture diagram to v12

---

## 2026-02-19 (v11)

### 概要
- コード品質リメディエーション — ネスト型の抽出、共通ユーティリティの統一、デッドコード削除、コマンドユニットテストの追加。

### 主な変更

#### PR #94: コード品質リメディエーション v11
- SHA-256トークンハッシュを共通 `TokenHashUtils` に統一。
- オーケストレータの失敗結果生成を `ReviewResult.failedResults(...)` に統一。
- `ReviewOrchestrator` のネスト型（`OrchestratorConfig`、`PromptTexts`、協調インターフェース/レコード群）をトップレベル型へ分離。
- `ScopedInstructionLoader` を明示ループ + 分離IO例外処理へリファクタ。
- `ExecutionConfig` にグルーピング設定（`ConcurrencySettings`、`TimeoutSettings`、`RetrySettings`、`BufferSettings`）とファクトリを追加。
- デッドコード削除: `ReviewResultPipeline.collectFromFutures`、未使用 `ReviewFindingSimilarity.WHITESPACE`。
- `ReviewCommand` / `SkillCommand` 専用ユニットテストを追加。

### 検証
- 756テスト、0失敗。
- CI 5/5 合格。

### マージ済み PR
- [#94](https://github.com/anishi1222/multi-agent-code-reviewer/pull/94): fix: resolve code-quality remediation findings v11

---

## 2026-02-19 (v10)

### 概要
- マージフロー、ファイルI/O、正規表現処理、ロギング、ビルドガバナンスに渡るパフォーマンス最適化とWAFセキュリティ強化。

### 主な変更

#### PR #93: パフォーマンス + WAFセキュリティ強化 v10
- マージフローの重複キー抽出を排除（`findingKeyFromNormalized` 再利用）。
- 近似重複検出で priority+タイトルprefix インデックスを追加。
- ローカルファイル読込の `ByteArrayOutputStream` 初期容量を最適化。
- フォールバック要約の空白正規化正規表現を事前コンパイル化。
- 認証/信頼/命令検証向けに構造化 `SECURITY_AUDIT` ログを導入。
- `--verbose` 時も Copilot SDK ロガーを `WARN` 固定。
- POSIX環境でレポート出力ディレクトリ/ファイルを owner-only 権限化。
- Maven `dependencyConvergence` エンフォーサールールを追加。
- 週次 OWASP 依存関係監査ワークフロー（`dependency-audit.yml`）を追加。

### 検証
- 756テスト、0失敗。
- CI 5/5 合格。

### マージ済み PR
- [#93](https://github.com/anishi1222/multi-agent-code-reviewer/pull/93): fix: resolve performance and WAF security findings

---

## 2026-02-19 (v9)

### 概要
- セキュリティ追従対応完了 — プロンプトインジェクション検証拡張、MCPヘッダーマスキング強化、トークン露出時間短縮。

### 主な変更

#### PR #92: セキュリティ追従対応 v9
- エージェント定義の疑わしいパターン検証を全プロンプト注入フィールドへ拡張。
- MCP認証ヘッダーのマスキングを `entrySet`/`values` 文字列化経路まで強化。
- `--token -` の標準入力読込をトークン解決境界まで遅延し、メモリ内トークン露出時間を最小化。

### 検証
- 全テストスイート合格。
- CI 5/5 合格。

### マージ済み PR
- [#92](https://github.com/anishi1222/multi-agent-code-reviewer/pull/92): fix: security follow-up closure

---

## 2026-02-19 (v8)

### 概要
- エグゼクティブサマリー出力の命名を、レポートファイル命名ポリシーに整合させました。
- タイムスタンプ付きのサマリーファイル名を廃止し、固定名 `executive-summary.md` に統一しました。
- 命名整合のため、関連テストスタブと README EN/JA の出力例を更新しました。

### 主な変更

#### PR #90: エグゼクティブサマリーファイル名の整合
- `SummaryGenerator` のエグゼクティブサマリー出力を `executive-summary.md` に変更。
- `ReviewRunExecutorTest` のサマリー出力スタブパスを `executive-summary.md` に更新。
- `README_en.md` と `README_ja.md` の出力例を新命名規則に同期。

### 検証
- PR #90 必須チェック合格: `CI/Build Native Image`, `CI/Build and Test`, `CI/Supply Chain Guard`, `Dependency Review`, `Automatic Dependency Submission`

### マージ済み PR
- [#90](https://github.com/anishi1222/multi-agent-code-reviewer/pull/90): エグゼクティブサマリーのファイル名をレポート命名規則に整合

---

## 2026-02-19 (v7)

### 概要
- 最新のセキュリティレポートに対する追従修正と、関連ドキュメント同期を実施しました。
- `LocalFileConfig` の機密ファイルパターン・フォールバック定義をリソース既定値と同期し、フォールバック経路でも機密ファイル除外の安全性を維持しました。
- OWASP 依存関係監査を任意で実行できるプロファイルを追加し、README EN/JA（アーキテクチャ図・プロジェクト構造注記含む）を更新しました。

### 主な変更

#### 機密パターン・フォールバック整合
- `LocalFileConfig` の `FALLBACK_SENSITIVE_FILE_PATTERNS` に、`defaults/sensitive-file-patterns.txt` で既に管理している汎用設定ファイル名を追加:
  - `application.yml`, `application.yaml`
  - `config.json`, `settings.json`, `settings.yaml`
- リソース読み込み失敗時でも、フォールバック経路で同等の機密ファイル除外を維持できるようにしました。

#### 回帰防止
- `LocalFileConfigTest#fallbackSensitivePatternsContainResourcePatterns` を追加。
- リソース定義の機密パターンがフォールバック定義に必ず包含されることを検証し、将来の乖離を防止します。

#### サプライチェーン監査運用
- `pom.xml` に `dependency.check.version` と任意プロファイル `security-audit` を追加。
- `mvn -Psecurity-audit verify` で OWASP `dependency-check-maven` を実行可能にし、通常ビルドの負荷増加は回避します。

#### ドキュメント同期
- `README_en.md` / `README_ja.md` に v7 対応内容を反映。
- アーキテクチャ図に `CopilotClientStarter` / `CopilotCliHealthChecker` の関係を追加。
- プロジェクト構造セクションに、現行ソース構成との同期注記を追加。

### 検証
- ローカル全体テスト: 971 tests, 0 failures。

---

## 2026-02-19 (v5)

### 概要
- README EN/JA に 2026-02-19 v2-v4 の運用サマリーを1行で追記しました。
- リメディエーション進行を簡潔に可視化し、運用引き継ぎ時の把握性を向上しました。
- ドキュメント更新を PR #80 で `main` に反映しました。

### 主な変更

#### PR #80: README への v2-v4 運用サマリー追記
- `README_en.md` と `README_ja.md` に運用サマリーを1行追加。
- サマリーで PR #74（Java 25 CI整合）→ PR #76（idle-timeout scheduler 耐障害性）→ PR #78（運用完了チェック同期）の流れを明示。

### 検証
- PR #80 の必須チェック合格: `Supply Chain Guard`, `dependency-review`, `submit-maven`, `Build and Test`, `Build Native Image`

### マージ済み PR
- [#80](https://github.com/anishi1222/multi-agent-code-reviewer/pull/80): README へ v2-v4 運用サマリーを追加

---

## 2026-02-19 (v4)

### 概要
- README EN/JA の運用完了チェックを 2026-02-19 時点に更新しました。
- チェック項目に PR #76 の信頼性対応を明示し、追跡性を改善しました。
- ドキュメント同期変更を PR #78 で `main` に反映しました。

### 主な変更

#### PR #78: 運用完了チェックの更新
- `README_en.md` / `README_ja.md` の該当セクション日付を 2026-02-18 から 2026-02-19 に更新。
- 固定テスト件数表現を全テストスイート合格の表現へ変更。
- PR #76（idle-timeout scheduler 停止時フォールバック）完了項目を追加。

### 検証
- PR #78 の必須チェック合格: `Supply Chain Guard`, `dependency-review`, `submit-maven`, `Build and Test`, `Build Native Image`

### マージ済み PR
- [#78](https://github.com/anishi1222/multi-agent-code-reviewer/pull/78): 2026-02-19 運用完了チェック更新

---

## 2026-02-19 (v3)

### 概要
- 停止済み共有 scheduler へのアイドルタイムアウト登録で発生していたレビュー失敗を修正しました。
- スケジューリング不可時は idle watchdog なしで処理継続するフォールバックを実装し、実行の安定性を向上しました。
- shutdown 済み scheduler 経路の回帰テストを追加し、修正を PR #76 で `main` に反映しました。

### 主な変更

#### PR #76: Idle Timeout Scheduler の耐障害性向上
- `IdleTimeoutScheduler.schedule(...)` で scheduler が停止済み、または登録拒否された場合に no-op `ScheduledFuture` を返すよう変更。
- フォールバック時に警告ログを出力し、失敗させず可観測性を維持。
- `IdleTimeoutSchedulerTest` に shutdown scheduler ケースを追加。

### 検証
- フォーカステスト: `mvn -Dtest=IdleTimeoutSchedulerTest test` 成功
- 全体ビルド: `mvn clean package` 成功
- PR #76 の必須チェック合格: `Supply Chain Guard`, `dependency-review`, `submit-maven`, `Build and Test`, `Build Native Image`

### マージ済み PR
- [#76](https://github.com/anishi1222/multi-agent-code-reviewer/pull/76): レビュー時の idle-timeout scheduler 停止を許容

---

## 2026-02-19 (v2)

### 概要
- CodeQL ワークフローの Java セットアップを 26 から 25 へ変更し、プロジェクト方針（Java 25.0.2）に統一しました。
- `pom.xml` と main CI ワークフローで先行して揃えていた Java 25 設定に、CodeQL 側も整合させて不一致を解消しました。
- 変更を PR #74 で `main` にマージしました。

### 主な変更

#### PR #74: CodeQL ワークフローの JDK 統一
- `.github/workflows/codeql.yml` の JDK 設定を `26` から `25` に更新。
- ビルド設定と GitHub Actions ワークフローの Java バージョン方針を全体で整合。

### 検証
- ローカル検証: `mvn clean package` 成功
- PR #74 の必須チェック合格: `Supply Chain Guard`, `dependency-review`, `submit-maven`, `Build and Test`, `Build Native Image`

### マージ済み PR
- [#74](https://github.com/anishi1222/multi-agent-code-reviewer/pull/74): CodeQL ワークフローの JDK を Java 25 に統一

---

## 2026-02-19

### 概要
- マルチパスレビュー時の性能指摘に対応し、同一エージェント内の複数パスで単一の `CopilotSession` を再利用するよう改善しました。
- 実行モデルを「パス単位」から「エージェント単位（内部で複数パス実行）」へリファクタし、マルチパスマージの挙動を維持しました。
- 単一 reviewer インスタンス再利用を検証する回帰テストを追加し、新しいマルチパス実行契約に合わせてオーケストレータテストを更新しました。
- 変更を PR #67 で `main` にマージしました。

### 主な変更

#### PR #67: マルチパスのセッション再利用とオーケストレータ改善
- `ReviewAgent.reviewPasses(...)` を追加し、同一エージェントの複数パスを1セッションで実行。
- `ReviewExecutionModeRunner` を1エージェント1タスク実行に変更し、タスク内でパス結果を収集。
- `AgentReviewExecutor` を `reviewPasses(...)` ベースに更新し、タイムアウト・失敗時のパス結果整形を実装。
- `ReviewOrchestrator` の実行経路を新しいパス対応フローへ更新。
- テスト追加・更新:
  - `AgentReviewExecutorTest`: マルチパスで reviewer 生成1回・`reviewPasses` 呼び出し1回を検証
  - `ReviewExecutionModeRunnerTest`: async/structured 実行の新しい list ベース契約を検証

### 検証
- PR #67 のCI必須チェック合格: `Supply Chain Guard`, `dependency-review`, `submit-maven`, `Build and Test`, `Build Native Image`
- ローカルコンパイル検証: `mvn -q -DskipTests compile` 成功

### マージ済み PR
- [#67](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/67): `CopilotSession` のマルチパス再利用と関連更新

---

## 2026-02-18

### 概要
- 3回目のフルレビューサイクル（best-practices、複数ラウンド）を実施し、PR #41〜#65 で全指摘事項に対応しました。
- Azure WAF エージェント（`azure-waf.agent.md`）を Well-Architected Framework の5つのピラー別エージェントに分割しました。
- StepSecurity による CI セキュリティ強化と GitHub Actions 用 Dependabot 設定を追加しました。
- `SkillService` のスレッドセーフティ問題を `Collections.synchronizedMap` で修正しました。
- テスト数が 722 から 730 以上に増加（nullable・防御的コピーのテスト追加）。

- 単一の `azure-waf.agent.md` を5つのピラー別エージェントに分割:
  - `waf-reliability.agent.md`、`waf-security.agent.md`、`waf-cost-optimization.agent.md`、`waf-operational-excellence.agent.md`、`waf-performance-efficiency.agent.md`
- 全 SKILL.md のエージェント参照を対応するピラー別エージェントに更新

- `report/` を `report/finding/`、`report/summary/`、`report/util/` サブパッケージに分割

- `@Factory` + `@Named` PrintStream ビーンによる CLI 出力の統一
- `AgentConfigLoader` に `LinkedHashMap` を使用し安定したエージェント順序を確保
- `ReviewContext` のフィールドを `TimeoutConfig` と `CachedResources` レコードにグループ化
- 仮想スレッドに名前プレフィックスを追加（可観測性向上）
- `FrontmatterParser` の catch 句を縮小、`loadAs()` で型安全性向上
- `ReviewResult` のタイムスタンプを `LocalDateTime` から `Instant` に変更、テスト容易性のため `Clock` を注入
- 全 `toLowerCase` 呼び出しに `Locale.ROOT` を追加
- `AgentPromptBuilder` と `CustomInstruction` の文字列連結をテキストブロックに変換
- 不要コード削除: 未使用の `CommandExecutor` オーバーロード、`CopilotService` の initialized フラグ、空の `FeatureFlags` コンストラクタ
- `SkillService` の LRU を `LinkedHashMap.removeEldestEntry` でイディオマティックに変更
- `ContentCollector` の汎用 `RuntimeException` を `SessionEventException` に変更
- `SkillConfig.defaults()` ファクトリメソッドを追加

#### PR #56: SkillService スレッドセーフティ修正
- `SkillService.executorCache` を `ConcurrentHashMap` から `Collections.synchronizedMap` に変更し `computeIfAbsent` デッドロックリスクを回避

#### PR #49: StepSecurity セキュリティ強化
- CI ワークフローの GitHub Actions を SHA ベース参照にピン留め
- GitHub Actions 自動バージョン更新用 `dependabot.yml` を追加

#### PR #60: CODEOWNERS と CI 強化
- CodeQL ワークフローの `oracle-actions/setup-java` をピン留め

#### PR #63: 不足ユニットテスト追加
- nullable・防御的コピー変更に対するユニットテストを12ファイルで追加（+203行）

#### PR #64: README 修正
- README ファイルの重複画像参照を削除

### 検証
- テスト数: 730 以上（116テストクラス、0失敗、0エラー）
- CI: 全PR で全必須チェック合格

### マージ済み PR
- [#41](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/41)〜[#49](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/49)、[#56](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/56)、[#60](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/60)〜[#65](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/65)

---

## 2026-02-17 (v2)

### 概要
- マルチエージェントコードレビュアー自体に対して2回目のフルレビューサイクル（best-practices / code-quality / performance / security）を実施しました。
- 全指摘事項を7つのPR（#34〜#40）で対応。セキュリティ強化、パフォーマンス最適化、コード品質、ベストプラクティス、テストカバレッジを改善しました。
- テスト数が614から722に増加（+108テスト）。

### 主な変更

#### PR #34: 初期レビュー指摘対応（セキュリティ・パフォーマンス・ベストプラクティス・テスト追加）
- `ContentSanitizer` に XSS 防止用 `DANGEROUS_HTML_PATTERN` を追加
- 安全性バリデータにホモグリフ正規化・デリミタインジェクション検出を追加
- `.agent.md` ファイルのプロンプトインジェクション検証を追加
- `ReviewResultMerger` の O(N²) 近似重複検出を改善
- `ContentCollector` のロック競合を軽減
- `CopilotCliException` に `@Serial serialVersionUID` を追加
- `CopilotCliHealthChecker` に `CopilotTimeoutResolver` を DI 注入（DRY）
- 17テストファイルに91件の新規テストを追加

#### PR #35: ベストプラクティスレビュー対応
- `LocalFileConfig` の volatile 遅延初期化を Holder イディオムに変更
- `SkillExecutor` に `AutoCloseable` を実装
- `CustomInstructionLoader` を Micronaut DI 対応
- `ReviewTarget.isLocal()` を exhaustive `switch` 式に変更
- `GithubMcpConfig` に名前付き `MaskedToStringMap` クラスを導入
- `SkillService` を `LinkedHashMap(accessOrder=true)` による LRU キャッシュに変更
- `CliValidationException` に cause-chain コンストラクタを追加
- `ReviewResult.Builder` に `final` を追加

#### PR #36: コード品質レビュー対応
- `AgentMarkdownParser.extractFocusAreas()` のイミュータブルリストバグを修正（High）
- `AggregatedFinding` の record 不変性を復元（`addPass()` → `withPass()`）
- `AgentConfigValidator` のデッドコード `focusAreas() == null` チェックを削除
- `DEFAULT_LOCAL_REVIEW_RESULT_PROMPT` を `AgentPromptBuilder` に集約
- `SummaryCollaborators.withDefaults()` メソッドを追加
- `AgentReviewExecutor` のネスト try-catch を平坦化

#### PR #37: パフォーマンスレビュー対応
- **Critical**: `SkillService` の `computeIfAbsent` デッドロックリスクを修正
- `normalizeText()` の7段 `String.replace()` を単一パス `char[]` 処理に変更
- `ContentSanitizer` の CoT+HTML パターンを統合（3パス→2パス）
- `ContentSanitizationRule` に `find()` 事前チェックを追加
- `ContentCollector` の StringBuilder を 64KB→4KB に削減

#### PR #38: セキュリティレビュー対応
- ソースコードを `<source_code trust_level="untrusted">` デリミタで囲み保護
- `--trust` 使用時の監査ログを追加
- `sensitive-file-patterns.txt` に汎用設定ファイルパターンを追加
- `javascript:` および `data:base64` URI 検出を追加
- `CliPathResolver` に Windows `.exe`/`.cmd` 対応を追加

#### PR #40: テスト整備
- `SkillExecutorTest` を deprecated `shutdown()` → `close()` に更新
- `ContentSanitizerTest` に HTML/XSS サニタイズ7テストを追加
- `LogbackLevelSwitcherTest` を新規作成
- cause-chain コンストラクタ、`generateReports()`、ソースコードデリミタのテストを追加

### 検証
- 全体テスト: 722テスト合格、0失敗、0エラー
- CI: 全PR で全5チェック合格（Build and Test, Native Image, Supply Chain Guard, Dependency Review, Dependency Submission）
- 実行確認: `mvn clean package` + `run --repo ... --all` 終了コード 0

### マージ済み PR
- [#34](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/34)〜[#40](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/40)

---

## 2026-02-17

### 概要
- 最終リメディエーションの分割対応（PR-1〜PR-5）を完了しました。
- セキュリティとDI一貫性の未完了項目を解消し、最終チェックリストを完了状態に更新しました。
- 回帰防止のため、影響範囲テスト・全体テスト・実行確認まで実施しました。

### 主な変更
- PR-1: `GithubMcpConfig.buildMcpServers()` を `Optional<Map<String,Object>>` 化（null契約排除）。
- PR-2: `ReviewRunRequest` から `resolvedToken` を分離し、実行境界で短寿命受け渡しへ変更。
- PR-3: `CustomInstruction` を構造化サンドボックス化し、システム命令優先を明示。
- PR-4: `ContentCollector` に `LongSupplier` 時計注入を追加し、連結キャッシュ判定をバージョン依存へ変更。
- PR-5: `CopilotService` no-arg コンストラクタを削除し、DIコンストラクタへ統一。

### 検証
- 影響テスト: `CopilotServiceTest` / `ReportServiceTest` / `SkillServiceTest` 成功。
- 全体テスト: `mvn -q -DskipITs test` 実行後、Surefire レポートで failures/errors 0 を確認。
- 実行確認: `mvn clean package` と `run --repo ... --all` の終了コード 0 を確認。

### 参照
- 最終チェックリスト: `reports/anishi1222/multi-agent-code-reviewer/final_remediation_checklist_2026-02-16.md`
- 最終サマリー: `reports/anishi1222/multi-agent-code-reviewer/final_remediation_summary_2026-02-17.md`
- RELEASE_NOTES 日英対応ガイド: `reports/anishi1222/multi-agent-code-reviewer/release_notes_bilingual_alignment_2026-02-17.md`
- リリースタグ: `v2026.02.17-notes`
- GitHub Release: https://github.com/anishi1222/multi-agent-code-reviewer-java/releases/tag/v2026.02.17-notes

## 2026-02-16

### 概要
- 大規模クラス分割の継続対応として、CLI、ローカルファイル収集、orchestrator、report パイプライン、Copilot 起動処理の責務分離を完了しました。
- 既存挙動を維持したまま、コマンド/サービスの責務密度を下げ、テスト容易性を向上しました。
- 直近の `CopilotClientStarter` 抽出後も回帰がないことを確認しました。

### 主な変更
- CLI リファクタ:
  - `ReviewCommand` を解析/解決/準備/リクエスト生成/実行調停に分離。
  - `SkillCommand` を解析/準備/実行/出力整形に分離。
  - `--token` の直接値指定を拒否し、stdin/env ベース運用に統一。
- ローカルソース収集リファクタ:
  - `LocalFileProvider` を候補収集・候補処理・内容整形・設定正規化へ分離。
- Orchestrator/Report リファクタ:
  - orchestrator 側に実行モード・結果パイプライン・コンテキスト生成・事前収集の専用コンポーネントを追加。
  - report/summary 内部を parser/formatter/builder 系ヘルパーへ分割。
- Copilot 起動リファクタ:
  - `CopilotService` から CLI パス解決、ヘルスチェック、タイムアウト解決、起動エラーフォーマット、起動実行を分離。
  - `CopilotClientStarter` を追加し、起動時の timeout/cause 変換と安全な close を独立化。

### 検証
- フォーカステスト: 101 run, 0 failures, 0 errors
- 全体テスト: 760 run, 0 failures, 0 errors

## 2026-02-14

### 概要
- Maven と GitHub Actions にサプライチェーンガードレールを導入しました。
- CI の必須チェックを追加し、実PRでブランチ保護の動作を検証しました。
- 依存関係ポリシーと CI ポリシーを英語/日本語 README に明記しました。

### 主な変更
- Maven ポリシー強化:
  - Maven Central 成果物のチェックサム検証を強制。
  - Maven Enforcer により SNAPSHOT 依存関係/プラグインを禁止。
- Dependency Review ポリシー強化:
  - 脆弱性 `moderate` 以上で PR を失敗。
  - 拒否ライセンス: `GPL-2.0`, `GPL-3.0`, `AGPL-3.0`, `LGPL-2.1`, `LGPL-3.0`。
- CI 強制:
  - `Supply Chain Guard` と `Build and Test` ジョブを追加。
  - ブランチ保護で required checks が正しく認識されることを確認。

### ブランチ保護（main）
- Required checks:
  - `Supply Chain Guard`
  - `Build and Test`
  - `dependency-review`
- 追加保護:
  - `strict` status checks 有効
  - `enforce_admins` 有効
  - `required_conversation_resolution` 有効

### マージ済み PR
- [#10](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/10): enforce dependency policy and required checks

### 変更ファイル（PR #10）
- `.github/workflows/dependency-review.yml`
- `README_en.md`
- `README_ja.md`

## 2026-02-15

### 概要
- 4レビュー（best-practices / code-quality / performance / security）の指摘 26 件（High 4 / Medium 10 / Low 12）に対応しました。
- セキュリティ、保守性、性能、設定/DI 一貫性に関する改善を実施しました。
- PR #13 を squash merge し、`main` へ反映しました。

### 主な変更
- セキュリティ強化:
  - `CustomInstructionSafetyValidator` を regex + Unicode 正規化（NFKC）ベースに強化。
  - 自動ロードされたカスタムインストラクションにも安全性検証を適用。
  - `TemplateService` のテンプレート名検証を allowlist + 正規化パス検証に変更。
  - `GithubMcpConfig` のヘッダ文字列表現で認証値をマスク化。
- コード品質/設計改善:
  - 旧重複コマンド 3 クラスを削除（デッドコード解消）。
  - `FeatureFlags` / `SkillRegistry` を Micronaut DI 管理へ統一。
  - `ReviewCommand.applyOption` を分割し複雑度を低減。
  - wildcard import を明示 import に統一。
- パフォーマンス改善:
  - `LocalFileProvider` に one-pass 収集 API（`collectAndGenerate`）を追加。
  - `ReviewContext` の共有再利用、`FindingsExtractor` の single-pass 化、`SummaryGenerator` 置換処理の軽量化。

### 検証
- `mvn -B -ntp clean package`: BUILD SUCCESS
- テスト結果: 431 run, 0 failures, 0 errors

### マージ済み PR
- [#13](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/13): fix: resolve all findings from 2026-02-15 reports

### 参照
- 詳細対応表: `reports/anishi1222/multi-agent-code-reviewer/remediation_checklist_2026-02-15.md`

## 2026-02-15 (v0.03)

### 概要
- マルチパスレビュー機能を追加し、各エージェントが複数回レビューを実施して結果をマージする仕組みを導入しました。
- 全レビュー指摘（High / Medium / Low）に対する追加修正とテストカバレッジ強化を実施しました。
- CI ワークフローの依存関係修正、設定のデフォルト値追加など安定性を向上しました。

### 主な変更

#### 新機能: マルチパスレビュー
- 各エージェントが `review-passes` 回（デフォルト: 1）レビューを実施し、結果を `ReviewResultMerger` で統合。
- 全パスを Virtual Thread プールに同時投入し、`Semaphore(parallelism)` で同時実行数を制御。
- マージは文字列連結のみ（追加 AI 呼び出しなし）。
- `application.yml` の `reviewer.execution.review-passes` で設定可能。

#### セキュリティ強化
- `ReviewTarget` のパスバリデーション強化。
- `CliPathResolver` に CLI パスの安全性チェックを追加。
- `CustomInstructionSafetyValidator` のプロンプトインジェクション検出を拡充・正規化。
- `GithubMcpConfig` の MCP ヘッダマスキング動作改善。

#### パフォーマンス・安定性
- `ReviewAgent` のリトライ動作とタイムアウトハンドリング改善。
- `ReviewOrchestrator` の起動時クリーンアップとサマリーフォールバック品質向上。
- `ReviewResultMerger` にエージェント単位の重複排除機能を追加。
- `LocalFileProvider` のファイル収集ロジックをリファクタリングし効率化。
- `CopilotService` のリトライとエラーハンドリング改善。

#### 設定・構成
- `AgentPathConfig` を新設し、エージェントパス設定を外部化。
- `LocalFileConfig` にデフォルト値と `@Nullable` アノテーション追加。
- `application.yml` に `reviewer.local-files` のデフォルト設定追加。

#### テスト
- `ReviewResultMergerTest`（9 ケース）を新規追加。
- `AgentPathConfigTest`、`LocalFileConfigTest`、`ReviewTargetTest`、`LocalFileProviderTest`、`CommandExecutorTest`、`CliPathResolverTest` を追加・拡充。
- テスト総数: 453（0 failures, 0 errors）。

#### ドキュメント
- README（日英）にマルチパスレビュー機能の説明、設定例、アーキテクチャ図を追加。

### マージ済み PR
- [#16](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/16): fix: add default reviewer.local-files settings
- [#17](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/17): fix: update build-native-image job dependencies
- [#18](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/18): fix: remediate 2026-02-15 report findings (all severities)
- [#19](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/19): feat: マルチパスレビュー機能の追加
- [#20](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/20): docs: README にマルチパスレビュー機能の説明を追加
- [#21](https://github.com/anishi1222/multi-agent-code-reviewer-java/pull/21): fix: all review findings and strengthen coverage

### 検証
- `mvn test`: 453 run, 0 failures, 0 errors
- CI: Build and Test / Native Image Build / Supply Chain Guard / Dependency Review すべて成功

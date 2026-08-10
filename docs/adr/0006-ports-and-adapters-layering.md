# 0006: Adopt Ports & Adapters layering with an explicit composition root

- Status: Accepted
- Date: 2026-08-05
- Last amended: 2026-08-08 (t32.2: Rule 5c and executable ADR-rule traceability verified)
- Deciders: Multi-Agent Code Reviewer maintainers
- Tags: architecture, ports-and-adapters, layering, observability

## Context

本プロジェクトは当初、技術的関心事ごとのフラットなパッケージ構成（`agent/` `cli/` `config/` `orchestrator/`
`report/` `service/` `skill/` `target/` `util/`）を採っていました。機能追加を重ねた結果、以下が観測されました。

- **循環依存 6 組**: `service⇄orchestrator` `service⇄agent` `service⇄report` `agent⇄report` `agent⇄skill` `util→agent`
- **外部 SDK 型の漏出**: `com.github.copilot.*` が 8 パッケージ・53 箇所に出現
- **DI 束縛の拡散**: `io.micronaut.*` が `config` 以外の 6 パッケージに、`jakarta.inject.*` が 7 パッケージに拡散
- **責務の同居**: `service` が SDK クライアント管理／テンプレート／レポート／エージェント取得を、
  `cli` が引数解析／コマンド実行／ユースケース調整／出力整形を、`util` が並行処理／テキスト解析／
  トークン処理／可観測性をそれぞれ抱えていた

これは「層構造として成立していない」ことの証拠であり、部分的な是正では解けないと判断しました。
そこで Ports & Adapters（ヘキサゴナル）に基づく 5 層への全面再構成を実施しました。

再構成の設計（層定義・許可インポート表・ポートカタログ）は完了し、実装も完了していますが、
設計時点では解決しきれなかった構造上の緊張が残りました。初版ではそのうち 6 点を
`LayerDependencyRulesTest` の**名前指定の例外**として整理しました。その後の追随実装（t16.1）で
D3 の前提が誤っていたことと、レビュー経路に未記録の依存方向反転が 1 件あることが判明したため、
t16.2 で決定と逸脱表を実装事実に合わせて訂正しました。その後 t16.3 でレビュー経路を責務ごとに
分離し、t16.2 の独立再検証で DI 束縛と Rule 4 の両方を確認して deviation #8 を解消済みとしました。
t17.1 では layer→root と port→application 実装の欠落していた強制規則を追加し、t17.2 では
`ReviewApp` と `ApplicationPortFactory` の責務を分割して deviation #4 を解消しました。

本 ADR は、これらの緊張を隠さず、以後の判断基準と追随先を確定させるものです。

## Decision

### D1. 5 層 + コンポジションルート（第 0 層）を正式な構造とする

| 層 | 責務 | インポートしてよい先 | インポートしてはならない先 |
|---|---|---|---|
| `dev.logicojp.reviewer`（**コンポジションルート**） | オブジェクトグラフの組み立てとプロセス起動のみ | すべての層 | — |
| `presentation` | CLI 引数解析・コマンド・出力整形 | `application`, `application.port.inbound`, `domain`, `shared` | `infrastructure` |
| `application` | ユースケース調整 | `application.port.*`, `domain`, `shared` | `presentation`, `infrastructure` |
| `application.port` | 境界の契約（インタフェース + DTO） | `domain`, `shared` | `presentation`, `application` 実装, `infrastructure` |
| `domain` | 業務ルールとモデル | `shared`, `java.*` | それ以外すべて（Micronaut / Jakarta / SLF4J / Copilot SDK / SnakeYAML 不可） |
| `infrastructure` | 外部世界とのアダプタ | `application.port.outbound`, `domain`, `shared` | `presentation`, `application` 実装, `application.port.inbound` |
| `shared` | 層をまたぐ純粋ユーティリティ・定数 | `java.*` のみ | それ以外すべて |

この表の依存は Java の `import` だけを意味しません。**設定キーを文字列で名指しすることも、
そのキーを所有する層への依存**です。したがって `presentation` は `@Value` / `@Property` /
`@ConfigurationProperties` 等で外部設定を直接束縛してはなりません。設定の所有者は
`infrastructure.config`、既定値を含む実効値を presentation に公開する境界は
`application.port.inbound` とします。Micronaut の型を import していないことだけでは
`presentation ⊥ infrastructure` を満たしたことになりません。

**コンポジションルートを「第 0 層」として明文化する**ことが本 ADR の中心的な決定です。
Ports & Adapters において、オブジェクトグラフを組み立てる場所はすべての実装型を名指しできなければなりません。
これは層違反ではなく、**層構造が成立するための前提**です。設計時にこの層を持たなかったことが、
D3 で述べる例外の累積を招きました。

コンポジションルートには次の制約を課します。

1. 配線（DI 定義・エントリポイント）以外のコードを置かない
2. 業務判断・入出力整形・外部 I/O を行わない
3. 他のどの層からも参照されない（ルートは常にグラフの入口であって部品ではない）

### D2. ポートの向きは「実装者がどの層か」で決まる

| 種別 | パッケージ | 実装する層 | 呼び出す層 |
|---|---|---|---|
| **inbound**（駆動される側） | `application.port.inbound` | `application` | `presentation`（＋コンポジションルート） |
| **outbound**（駆動する側） | `application.port.outbound` | `infrastructure` | `application` |

この定義は運用可能（decidable）です。**inbound ポートの唯一の実装が `infrastructure` のクラスであるものは、
命名の揺れではなく層の欠陥**として扱います。逆も同様です。

したがって `infrastructure` に許すのは `application.port.outbound` **のみ**であり、
`application.port` 全体ではありません（実装ルールの是正は D5 参照）。

### D3. `ReviewApp` は現在地に据え置き、ルートへ移せるのは配線だけとする

エントリポイント `dev.logicojp.reviewer.ReviewApp` を `presentation` へ移す案は**採用しません**。

- t16 の判断時点では `ReviewApp` が `infrastructure.logging.LogbackLevelSwitcher` を直接参照しており、
  移設すると `presentation → infrastructure` 違反（より厳しいルール）に置き換わる状態だった。
  t17.2 でこの具体操作は outbound port のアダプタへ分離したが、安定 FQN を維持する判断は変わらない。
- 移設コストは構造的利得に見合わない。`pom.xml`／`pom-native.xml` の `mainClass` 計 4 箇所、
  GraalVM `reachability-metadata.json` 2 ファイル、さらにログ名 `d.l.reviewer.ReviewApp` に依存する
  利用者側のログ解析設定まで壊れる。
- ルートパッケージはすでに事実上のコンポジションルートである。**名前を与えるほうが正しい。**

#### t16.2 訂正 — 初版の前提は誤り

初版は、Rule 4 の例外になっている次の 3 クラスをすべて「Micronaut の DI ファクトリ」とみなし、
まとめてコンポジションルートへ移すよう指示していました。ソースを再確認した結果、その前提は
3 件中 2 件で成立しません。

| クラス | t16.2 で確認した形 | 裁定 | 最終追随後 |
|---|---|---|---|
| `ApplicationPortFactory` | `@Factory`。配線に加えて provenance 判定、ファイル I/O、設定写像、SDK 構築を所有 | 純粋配線だけをルートへ移し、他責務は outbound adapter へ分割する | **t17.2:** ルートには inbound use case の constructor wiring だけを残し、4 種の責務へ分割。Rule 4 例外を削除 |
| `ReviewContextFactory` | DI 注釈を持たない通常クラス。Micronaut 設定を `OrchestratorConfig` へ写像し、既定値を選ぶ | 配線ではない設定写像を含む形のままルートへ移してはならない | `@Singleton implements ResolveReviewSettingsPort` の outbound 設定アダプタへ分離し、Rule 4 例外を削除 |
| `ReviewOrchestratorFactory` | `@Singleton` かつ inbound `RunReviewPort` の実装 | ルートへ移すと D2 違反を Rule 4 のルート例外で隠すため、移設してはならない（deviation #8） | `@Singleton implements CreateReviewSessionPortsPort` の outbound SDK セッション生成アダプタへ縮小し、inbound 実装と Rule 4 例外を削除 |

したがって、D3 の追随は一括移設ではなく、責務ごとに分けます。

1. `ApplicationPortFactory` の純粋な配線はコンポジションルートへ移せる。
2. `ReviewContextFactory` は、設定写像を純粋値の組み立てまたは outbound port のアダプタとして
   分離してから Rule 4 の例外を外す。現在のロジックをルートへ移すだけの変更は不採用とする。
3. DI から解決される `RunReviewPort` の実装は `application` に置く。
   `ReviewOrchestratorFactory` が担う SDK アダプタ組み立てと設定写像は、コンポジションルートの配線と
   outbound adapter に分割する。`infrastructure` が inbound port を実装する状態をファイル移動だけで
   解消したことにしてはならない。

**強制手段は既存の Rule 4**（`infrastructure → application.port.outbound` のみ）です。
t16.3 追随後、`ReviewContextFactory` と `ReviewOrchestratorFactory` は通常の outbound adapter として
Rule 4 の例外から削除されました。ルートの `ReviewPortFactory` は配線だけを行い、Micronaut が解決する
`RunReviewPort` は `application.review.ReviewOrchestrator` であることを `PortDirectionWiringTest` が
実コンテナで固定します。これにより #8 は解消済みです。

t17.2 では旧 `infrastructure.copilot.ApplicationPortFactory` を削除し、provenance-aware loading、
Micronaut 設定写像、SDK adapter 構築、filesystem adapter 構築をそれぞれ focused infrastructure
adapter/factory へ分割しました。ルートの新 `ApplicationPortFactory` は inbound use case の
constructor wiring だけを所有します。`ApplicationPortSplitWiringTest` が実コンテナ束縛を固定し、
Rule 4 は **0 violators / 0 exemptions** となったため deviation #4 は解消済みです。

同時に `ReviewApp` は `main` だけを持つ安定エントリポイントへ縮小しました。global CLI
parse/help/version/error/dispatch/output は `presentation.CliApplication`、起動前のログディレクトリ
保護と JVM flag 診断は `infrastructure.startup.StartupEnvironment`、具体的なログレベル変更は
`ConfigureLoggingPort → ConfigureLoggingUseCase → SetLogLevelPort` へ移しました。Rule 0c が
`ReviewApp` の許可依存・メソッド・フィールドを固定し、再肥大化を build-time に検出します。

### D4. 純粋性が押し出した横断的関心事は、必ずポートとして復元する

`domain` / `application` から SLF4J を排除した結果、`application` のログが `java.util.logging` に退避し、
**MDC による実行相関 ID の伝播が失われました**（仮想スレッド越しの相関が切れ、それを守っていたテストも削除された）。
これは純粋性のための正当な代償ではなく、**能力の消失**です。

本 ADR は次を恒久ルールとします。

> **純粋性ルールによって層から追い出された横断的技術能力は、
> `application.port.outbound` のポートとして再導入し、`infrastructure` に実装を置く。
> 能力を落としたり、機能の劣る代替（MDC を持たない `java.util.logging` 等）に静かに置き換えたりしてはならない。**

適用第一号は、仮想スレッド越しの相関コンテキスト伝播を担う `PropagateCorrelationPort` です
（`application.port.outbound`、実装は `infrastructure.logging.MdcCorrelationAdapter`。t13.1 で実装済み）。
このポートは最低限、次の能力を提供しなければなりません。

1. 実行相関スコープの束縛と解除（アダプタ側で MDC にマップする）
2. 仮想スレッド／`StructuredTaskScope` へ相関スコープを引き継ぐ手段
3. 借用したプールスレッドを汚染しないよう、呼び出し元のコンテキストを正常・異常いずれの復帰時にも復元すること

**残課題:** 本ポートが復元したのは相関の伝播であって、ログ出力そのものではありません。
`domain`（4 ファイル）と `application`（10 ファイル）は依然として `java.util.logging` を使用しています。
レベル付き診断出力を同じ規律で扱うかどうかは別途判断が必要です（Known deviations #5）。

同じ規律はメトリクスとトレーシングにも適用されます（将来の追加時に本 ADR を根拠とすること）。

関連して、`shared` は `java.*` のみを参照できるため、SLF4J を必要とするセキュリティ監査ログは
`presentation.CliSecurityAudit` に、フレームワーク非依存の値サニタイズは `shared.LogValueSanitizer` に
分離されています。**これは分割の失敗ではなく、上記ルールに沿った意図的な配置**です。

### D5. 許可インポート表の全行に、対応する強制ルールを 1 つずつ持つ

設計上 `presentation ⊥ infrastructure` を定めていたにもかかわらず、**それを検査するルールが存在しませんでした**。
結果として違反 2 件が本番コードに存在したまま検出されませんでした。

> **許可インポート表の行に対応する強制ルールが存在しないことは、それ自体が欠陥である。**
> 行を追加したら、同じコミットで強制ルールを追加する。

現行の対応表（D-item と実行可能なルールの双方向照合に使う正準カタログ）:

| 表の行 | 強制ルール |
|---|---|
| 解析器が全コンパイル済みクラスを見る | Rule 0 |
| descriptor 解析が前提とする「default package 不在」 | Rule 0b |
| `ReviewApp` は薄い process entry point | Rule 0c |
| `domain` の純粋性 | Rule 1 |
| `shared` の純粋性 | Rule 2 |
| `presentation` は葉である | Rule 3 |
| 5 層から layer zero を参照しない | **Rule 3a（t17.1 で追加済み・例外 0 件）** |
| `infrastructure → application.port.outbound` のみ | Rule 4 |
| `application.port` が application 実装を参照しない | **Rule 4a（t17.1 で追加済み・例外 0 件）** |
| `application.port` は値の表示用セキュリティ helper に依存しない | **Rule 4b（ADR-0007 D5、t31 で追加済み・例外 0 件）** |
| `application` はアダプタ非依存 | Rule 5 |
| `presentation ⊥ infrastructure` | **Rule 5b（t13.1 で追加済み・例外 0 件）** |
| `presentation` は外部設定を直接束縛しない | **Rule 5c（t32.2 で追加済み・例外 0 件）** |
| `domain` が設定既定値を直接読まない | **Rule 8（t30 で追加済み・例外 0 件。ADR-0008）** |
| 層・サブパッケージの非循環 | Rule 6a / Rule 6b |
| 全パッケージがいずれかの層に属する | Rule 6 scope |

Rule 4 の対象は `application.port` 全体ではなく `application.port.outbound` に狭めます（D2 の帰結）。

ルール番号は既存の学習記録・レビュー履歴から参照されるため、**追加ルールは既存番号を繰り上げず、
`5b` のように連番の位置に接尾辞付きで挿入します**（Rule 6a / 6b の番号を保つため）。

#### t32.1 設計・t32.2 実装 — Rule 5c: string-keyed dependency も層の辺である

Rule 5b が観測するのは Java 型の依存だけです。t28 で
`ReviewOutputFormatter` が `reviewer.execution.review-passes` を文字列で束縛し、実行側の
`reviewer.execution.concurrency.review-passes` と乖離していたことが判明しました。
型の辺が無いため Rule 5b は green のままでした。

同じ形は t32.1 時点で 3 クラスに残っていました。t32.2 で全 3 箇所を port 経由へ
移行または削除し、presentation の直接設定束縛は 0 件になりました。

| presentation の束縛 | 実際の所有者／実効経路 | 裁定 |
|---|---|---|
| `ReviewModelConfigResolver`: `reviewer.model.*` 4 キー | `ModelConfig` は `reviewer.models.*`（複数形）を所有 | **port 経由へ移行**。文字列を直すだけの是正は不採用 |
| `ReviewOptionsParser`: `reviewer.execution.parallelism:1` | `ExecutionConfig` は `reviewer.execution.concurrency.parallelism`、既定 4 を所有 | **port 経由へ移行**。設定所有者の正規化済み値を使う |
| `SkillCommand`: `reviewer.execution.skill-timeout-minutes:10` | `ExecutionConfig` の正準キーは `reviewer.execution.timeouts.skill-timeout-minutes`。渡した値は `SkillExecutionCoordinator` で未使用 | **死んだ束縛を削除**。将来 timeout を実装する場合は application → outbound session 境界で扱い、presentation に戻さない |

実装の正準形は、t28 が導入した `DescribeReviewPlanPort` / `ReviewPlan` を拡張し、
`reviewPasses` に加えて設定所有者が正規化した `defaultParallelism` と
review / report / summary / reasoning の model defaults を運ぶことです。
`DescribeReviewPlanUseCase` は `ResolveApplicationSettingsPort` から得た純粋値を写像し、
ルートの `ApplicationPortFactory` はその accessor を配線します。
`ReviewOptionsParser` と `ReviewModelConfigResolver` はこの inbound port の値に CLI override を
重ねます。presentation 側で既定値を再適用してはなりません。

Rule 5c は presentation の全 source-backed primary type を対象に、
`io.micronaut.context.annotation` および `io.micronaut.core.bind.annotation` への依存を
禁止します（`@Value` / `@Property` / `@ConfigurationProperties` / `@EachProperty` /
`@Bindable` を含む）。**例外は 0 件**です。0 違反・0 例外で blind にならないよう、
(a) 対象 source type が 1 件以上、(b) test-tree の `@Value` fixture を検出できることを
恒久的に主張します。t32.2 では実 production presentation class に `@Value` を植える変異で
Rule 5c がクラス名と禁止 annotation を報告して RED になることを確認済みです。

#### t32.1 設計・t32.2 実装 — ADR ↔ Rule 参照を実行可能にする

`AdrRuleReferenceGuardTest` を t32.2 で追加し、次の契約を機械化しています。

1. `docs/adr/*.md` のうち `Status: Accepted` の文書だけを読み、`### Dn.` 節を次の
   同レベル以上の見出しまで切り出す。
2. D-item 本文から `Rule N` / `Rule Nx`（`x` は小文字 1 文字）を抽出し、
   `LayerDependencyRulesTest` の **`@Test` が付いたメソッド**の
   `@DisplayName` が `^Rule N[x](?: scope)?:` に一致する**主テスト**から得る実行可能
   ルール在庫と照合する。`Rule Nx control:` は在庫から除外し、対照テストだけが残って
   主ルールの欠落を隠すことを防ぐ。`scope` は同じ canonical ID に畳み込む。
3. **順方向**: ADR の各参照に実行可能ルールが必要。失敗メッセージは
   ADR の相対パス、D-item、欠落した Rule を含める。
4. **逆方向**: 実行可能ルール在庫の各 ID は、少なくとも 1 つの Accepted ADR の
   D-item から参照されなければならない。失敗メッセージは Rule と test method を含める。
5. 非空の主張だけで済ませず、実在する anchor
   `0006/D5 → Rule 5b`、`0007/D5 → Rule 4b`、`0008/D2 → Rule 8` を固定する。
   test 側だけの改名と ADR 側だけの改名を別々に行い、双方が RED になることを記録する。
6. `7 — RESERVED, not implemented` の番号予約マーカーはコメントであり `@Test` ではないため
   在庫に入らない。
   特例で黙らせるのではなく、**実行可能性を在庫条件にすることで**番号を予約したまま
   false positive を避ける。

これにより「ADR が存在しない Rule を約束する」と「Rule の根拠 ADR が消える」の両方を
同じ build で止めます。regex が 0 件を返して永久 green になることも、3 anchor と
双方向変異によって止めます。

### D6. 層をまたぐ既定値は `shared` が単独で所有する

`ConfigDefaults` と `RetryPolicyUtils` が `shared` と `infrastructure.*` の両方に存在していました。
既定値が二重化すると、片方だけが更新されて挙動が分岐します。

- **`shared` が既定値・再試行方針の唯一の所有者**とする
- `infrastructure.config` は Micronaut 束縛の設定レコードのみを持ち、既定値を再定義せず `shared` を参照する
- 再発防止として、**`dev.logicojp.reviewer` 配下で単純クラス名は一意**であることを規約とする
  （現在の重複は上記 2 件のみであり、除去後は機械的に検査可能）

### D7. `RunReviewPort` は複数結果を返す

```java
public interface RunReviewPort {
    List<ReviewResult> execute(ReviewRequest request);
}
```

設計初版は単一の `ReviewResult` を返す形でしたが、これでは**エージェント別レポート
（`{agent-name}-report.md`）とマルチパスレポート（`{agent-name}-pass-{n}-report.md`）が生成不能**でした。
戻り値はエージェント×パスごとに 1 要素とし、単一パス時は `passNumber == 0`、
マルチパス時は `passNumber >= 1` を用います。以後、ポート契約は
**「既存の出力仕様を満たせること」を受け入れ条件として検証**します。

## Alternatives considered

1. **`ReviewApp` を `presentation` へ移設する** — 例外が消えず、より厳しいルールへの違反に置き換わるだけで、
   ビルド定義 4 箇所・ネイティブメタデータ 2 ファイル・利用者のログ名を壊す。棄却。
2. **専用の `bootstrap` サブパッケージを新設する** — 構造的には最も整うが、`ReviewApp` の移設コスト（案 1 と同じ）が発生する。
   ルートパッケージが実質同じ役割を果たしているため、費用対効果で棄却。
3. **名前が `Factory` の 3 クラスを一括してルートへ移す** — t16.2 で棄却。
   実際に `@Factory` なのは 1 件だけであり、残る 2 件を移すと設定写像ロジックと inbound port 実装を
   ルート例外の内側へ隠す。例外の恒久維持も不採用であり、#8 は t16.3、#4 は t17.2 の
   責務分離でそれぞれ解消した。
4. **`application` でも SLF4J を許可する** — `domain` 純粋性との一貫性が崩れ、
   メトリクス・トレーシング追加時に同じ議論を繰り返すことになる。棄却（D4 のポート化を採用）。
5. **層の強制をレビュー運用（人手）に委ねる** — `presentation ⊥ infrastructure` の違反が
   実際に見逃された事実が、この案が機能しないことを示している。棄却。

## Consequences

### Positive

- 循環依存 6 組が解消され、依存は内向き一方向になった
- `domain` は JDK と `shared` のみに依存し、Copilot SDK / Micronaut / Jakarta / SLF4J から完全に独立した
- ポート境界により、SDK 差し替え・テストダブル注入が層をまたがずに可能になった
- 「なぜ例外があるのか」が名前の列挙ではなく**層の定義**として説明できるようになった
- 例外の総数が減少し、以後は増加そのものを異常として検知できる
- 表の行と強制ルールが 1 対 1 になり、設計と検査の乖離が構造的に検出される

### Negative / Trade-offs

- コンポジションルートは全層を参照できるため、**規律が緩めば「何でも置ける場所」になり得る**。
  配線以外を置かない制約は、レビューで継続的に守る必要がある。
- Rule 4 の例外は 0 件になった。#4 と #8 はいずれも責務分割、例外削除、実コンテナ束縛テストの
  3 点を満たして解消しており、今後も例外を追加せず同じ完了条件を適用する。
- 横断的関心事のポート化により、単純なログ 1 行のために契約が 1 つ増える。ボイラープレートは増える。
- 単純クラス名の一意性規約は、意図的な同名 DTO を層ごとに置く自由を制限する。
- 層の再編に伴い、既存のパッケージ名を参照する外部ドキュメント・ログ解析設定は追随が必要。

## Known deviations (2026-08-07 更新)

本 ADR の決定に対するコード側の追随状況です。**Status** 列は最終確認時点の事実であり、
`Open` の項目は後続タスクで解消します。ファイル移動によってルールの例外側へ入っただけでは
`Resolved` としません。

| # | 逸脱 | 該当 | Status | 決定との差 |
|---|---|---|---|---|
| 1 | `ResolveTokenPort` が `application.port.inbound` にあるが、実装は `infrastructure.auth.GitHubTokenResolver` のみ | D2 | **Resolved (t16.1)** | `application.auth.ResolveTokenUseCase` を inbound 実装とし、GitHub CLI 機構を outbound `AcquireGitHubTokenPort` に分離済み |
| 2 | `ExecuteSkillPort`（inbound）を `application.skill.ExecuteSkillUseCase` と `infrastructure.copilot.SkillExecutor` が二重実装し、DI 束縛先は後者 | D2 | **Resolved (t16.1)** | `ExecuteSkillUseCase` を DI 束縛先へ戻し、`SkillExecutor` を削除済み |
| 3 | Rule 4 の対象が `application.port` 全体 | D2 / D5 | **Resolved (t16.1)** | `application.port.outbound` のみに狭め、#1 / #2 を実違反として RED にした後に是正済み |
| 4 | `ApplicationPortFactory` が `infrastructure.copilot` にあり、Rule 4 の名前指定例外になっている | D3 | **Resolved (t17.2)** | provenance-aware loading、設定写像、SDK/filesystem 構築を focused outbound adapters/factories へ分割。ルート factory は constructor wiring のみ。Rule 4 は例外 0 件、実コンテナ束縛も固定済み |
| 5 | `domain`（4 ファイル）・`application`（10 ファイル）のログ出力が `java.util.logging` のまま | D4 | **Partial** | 相関伝播は t13.1 の `PropagateCorrelationPort` で復元済み。レベル付き診断出力を同じ規律に載せるかは未決 |
| 6 | `ConfigDefaults` / `RetryPolicyUtils` が `shared` と `infrastructure` に重複 | D6 | **Resolved (t13.1)** | `shared` に統合済み。単純クラス名の重複は現在 0 件 |
| 7 | `presentation ⊥ infrastructure` の強制ルールが不在 | D5 | **Resolved (t13.1)** | Rule 5b として追加済み（例外 0 件、違反 0 件） |
| 8 | `infrastructure.copilot.ReviewOrchestratorFactory` が `@Singleton` として inbound `RunReviewPort` を実装し、presentation への DI 束縛先になっていた | D2 / D3 | **Resolved (t16.3; independently verified by t16.2)** | ルート `ReviewPortFactory` が application の `ReviewOrchestrator` を束縛。設定解決と SDK セッション生成は 2 outbound port へ分離し、旧 infrastructure 実装と Rule 4 例外を削除。実コンテナ束縛も固定済み |
| 9 | presentation の 3 クラスが Micronaut 設定キーを文字列で直接束縛し、Rule 5b から不可視 | D1 / D5 | **Resolved (t32.2; independently verified by t32.1 re-pass)** | `DescribeReviewPlanPort` に実効値を集約し、死んだ timeout 束縛を削除。Rule 5c は 72 compiled / 31 source-backed primary types を検査し、違反 0・例外 0。恒久 fixture と production 変異で検出性を確認済み |

## Operational notes

- **エントリポイントは変わりません。** メインクラスは `dev.logicojp.reviewer.ReviewApp` のままであり、
  `pom.xml` / `pom-native.xml` の `mainClass`、GraalVM の `reachability-metadata.json`、
  および `docs/runbook.md` に記載のロガー名 `d.l.reviewer.ReviewApp` は**いずれも変更されません**。
- **CLI の外部仕様は変わりません。** オプション名・設定キー・終了コード・レポート出力先はいずれも再構成の対象外です。
- **層の逸脱はビルドで落ちます。** 層間依存は `LayerDependencyRulesTest` が JDK 標準の
  `java.lang.classfile` API でバイトコードを直接検査します。`mvn verify` の一部として実行されるため、
  違反はレビュー前に検出されます。Rule 4 は例外 0 件です。
- **ArchUnit は使用しません。** 同ライブラリが同梱する ASM は Java 28 のクラスファイル
  （major version 72）を解釈できず、**エラーを出さずに大半のクラスを読み飛ばす**ため、
  層検査が空振りします。層検査の依存追加時は、対象 JDK のクラスファイルバージョンへの
  対応を必ず確認してください。
- **ビルドは 2 つの JDK を使い分けます。** `pom.xml` は Java 28、`pom-native.xml` は release 25 を
  対象とし、`.sdkmanrc` は native build 用に GraalVM 25.0.3 を選択します。
  `JAVA_HOME` を明示せずに実行すると、意図しない toolchain でコンパイルされます。

## References

- [0001: Keep custom CLI parser and command dispatch](0001-custom-cli-parser.md)
- [0002: Use Micronaut dependency injection as composition backbone](0002-micronaut-di.md)
- [0003: Orchestrate agent execution with virtual threads and structured concurrency](0003-virtual-thread-orchestration.md)
- `src/test/java/dev/logicojp/reviewer/architecture/LayerDependencyRulesTest.java` — 層依存の強制実装
- `.github/modernize/rearchitecture/artifacts/t16.1-backend.md` — D3 の前提反証と deviation #8 の発見証拠
- `.github/modernize/rearchitecture/artifacts/t16.3-backend.md` — deviation #8 の実装追随と RED/GREEN 証拠
- `.github/modernize/rearchitecture/artifacts/t16.2-architect.md` — t16.3 後の独立再検証と最終裁定
- `.github/modernize/rearchitecture/artifacts/t17.1-backend.md` — layer zero / port purity の強制規則
- `.github/modernize/rearchitecture/artifacts/t17.2-backend.md` — deviation #4 の責務分割と RED/GREEN 証拠
- `README_ja.md` / `README_en.md` の「プロジェクト構造」「アーキテクチャ」節 — 実装後の層構成

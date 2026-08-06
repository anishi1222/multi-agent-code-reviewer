# 0006: Adopt Ports & Adapters layering with an explicit composition root

- Status: Accepted
- Date: 2026-08-05
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
設計時点では解決しきれなかった**構造上の緊張が 6 点**残りました。これらは実装中に
`LayerDependencyRulesTest` の**名前指定の例外**として暫定的に吸収されており、
テストコード中にも `// Note for ADR-0006` として明示的に残されています。

本 ADR は、その 6 点すべてに決着をつけ、以後の判断基準を確定させるものです。

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

### D3. `ReviewApp` は現在地に据え置き、DI ファクトリをルートへ移す

エントリポイント `dev.logicojp.reviewer.ReviewApp` を `presentation` へ移す案は**採用しません**。

- 移設しても例外は消えない。`ReviewApp` は `infrastructure.logging.LogbackLevelSwitcher` を参照するため、
  `presentation` に置けば `presentation → infrastructure` 違反（より厳しいルール）に置き換わるだけである。
- 移設コストは構造的利得に見合わない。`pom.xml`／`pom-native.xml` の `mainClass` 計 4 箇所、
  GraalVM `reachability-metadata.json` 2 ファイル、さらにログ名 `d.l.reviewer.ReviewApp` に依存する
  利用者側のログ解析設定まで壊れる。
- ルートパッケージはすでに事実上のコンポジションルートである。**名前を与えるほうが正しい。**

代わりに、Micronaut の DI ファクトリ（`ApplicationPortFactory` ほか）を `infrastructure.copilot` から
**コンポジションルートへ移設**します。これにより:

- `infrastructure → application.port.inbound` を許すための**クラス名指定の例外 3 件が消滅**する
- `presentation` 参照の例外は「クラス名の列挙」から「コンポジションルートというパッケージ」へと、
  **境界が増えない形**に変わる
- ビルド定義・ネイティブメタデータ・ログ名の変更が一切発生しない

**正味で例外は減ります。**「例外は減ることはあっても増えてはならない」という本再構成の受け入れ基準を満たします。

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

現行の対応表:

| 表の行 | 強制ルール |
|---|---|
| `domain` の純粋性 | Rule 1 |
| `shared` の純粋性 | Rule 2 |
| `presentation` は葉である | Rule 3 |
| `infrastructure → application.port.outbound` のみ | Rule 4 |
| `application` はアダプタ非依存 | Rule 5 |
| `presentation ⊥ infrastructure` | **Rule 5b（t13.1 で追加済み・例外 0 件）** |
| 層・サブパッケージの非循環 | Rule 6a / 6b |
| 全パッケージがいずれかの層に属する | Rule 6 scope |

Rule 4 の対象は `application.port` 全体ではなく `application.port.outbound` に狭めます（D2 の帰結）。

ルール番号は既存の学習記録・レビュー履歴から参照されるため、**追加ルールは既存番号を繰り上げず、
`5b` のように連番の位置に接尾辞付きで挿入します**（Rule 6a / 6b の番号を保つため）。

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
3. **DI ファクトリを `infrastructure` に残し、例外を維持する** — 例外リストが恒久化し、
   「例外は増えない」という受け入れ基準の実効性を失う。棄却。
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
- 横断的関心事のポート化により、単純なログ 1 行のために契約が 1 つ増える。ボイラープレートは増える。
- 単純クラス名の一意性規約は、意図的な同名 DTO を層ごとに置く自由を制限する。
- 層の再編に伴い、既存のパッケージ名を参照する外部ドキュメント・ログ解析設定は追随が必要。

## Known deviations (2026-08-05 時点)

本 ADR の決定に対するコード側の追随状況です。**Status** 列が本 ADR 発行時点の事実であり、
`Open` の項目は後続タスクで解消されます。

| # | 逸脱 | 該当 | Status | 決定との差 |
|---|---|---|---|---|
| 1 | `ResolveTokenPort` が `application.port.inbound` にあるが、実装は `infrastructure.auth.GitHubTokenResolver` のみ | D2 | **Open** | 向きの誤分類。outbound 化するか、`application` に実装を置く |
| 2 | `ExecuteSkillPort`（inbound）を `application.skill.ExecuteSkillUseCase` と `infrastructure.copilot.SkillExecutor` が二重実装し、DI 束縛先は後者。ユースケースは Javadoc 以外から未参照 | D2 | **Open** | 同上。ユースケースを束縛先に戻し、SDK 呼び出しは outbound ポートへ委譲する |
| 3 | Rule 4 の対象が `application.port` 全体 | D2 / D5 | **Open** | `application.port.outbound` へ狭める（狭めることで #1 #2 が機械的に検出される） |
| 4 | DI ファクトリ 3 件（`ApplicationPortFactory` / `ReviewContextFactory` / `ReviewOrchestratorFactory`）が `infrastructure.copilot` に所在し、Rule 4 のクラス名例外になっている | D3 | **Open** | コンポジションルートへ移設する |
| 5 | `domain`（4 ファイル）・`application`（10 ファイル）のログ出力が `java.util.logging` のまま | D4 | **Partial** | 相関伝播は t13.1 の `PropagateCorrelationPort` で復元済み。レベル付き診断出力を同じ規律に載せるかは未決 |
| 6 | `ConfigDefaults` / `RetryPolicyUtils` が `shared` と `infrastructure` に重複 | D6 | **Resolved (t13.1)** | `shared` に統合済み。単純クラス名の重複は現在 0 件 |
| 7 | `presentation ⊥ infrastructure` の強制ルールが不在 | D5 | **Resolved (t13.1)** | Rule 5b として追加済み（例外 0 件、違反 0 件） |

## Operational notes

- **エントリポイントは変わりません。** メインクラスは `dev.logicojp.reviewer.ReviewApp` のままであり、
  `pom.xml` / `pom-native.xml` の `mainClass`、GraalVM の `reachability-metadata.json`、
  および `docs/runbook.md` に記載のロガー名 `d.l.reviewer.ReviewApp` は**いずれも変更されません**。
- **CLI の外部仕様は変わりません。** オプション名・設定キー・終了コード・レポート出力先はいずれも再構成の対象外です。
- **層の逸脱はビルドで落ちます。** 層間依存は `LayerDependencyRulesTest` が JDK 標準の
  `java.lang.classfile` API でバイトコードを直接検査します。`mvn verify` の一部として実行されるため、
  違反はレビュー前に検出されます。
- **ArchUnit は使用しません。** 同ライブラリが同梱する ASM は Java 27 のクラスファイル
  （major version 71）を解釈できず、**エラーを出さずに大半のクラスを読み飛ばす**ため、
  層検査が空振りします。層検査の依存追加時は、対象 JDK のクラスファイルバージョンへの
  対応を必ず確認してください。
- **ビルドは 2 つの JDK を使い分けます。** `pom.xml` は OpenJDK 27-ea、`pom-native.xml` は
  Oracle GraalVM 25.0.4 を必要とします。`JAVA_HOME` を明示せずに実行するとコンパイルに失敗します。

## References

- [0001: Keep custom CLI parser and command dispatch](0001-custom-cli-parser.md)
- [0002: Use Micronaut dependency injection as composition backbone](0002-micronaut-di.md)
- [0003: Orchestrate agent execution with virtual threads and structured concurrency](0003-virtual-thread-orchestration.md)
- `src/test/java/dev/logicojp/reviewer/architecture/LayerDependencyRulesTest.java` — 層依存の強制実装
- `README_ja.md` / `README_en.md` の「プロジェクト構造」「アーキテクチャ」節 — 実装後の層構成

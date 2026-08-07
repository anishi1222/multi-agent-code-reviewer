# 0006: Adopt Ports & Adapters layering with an explicit composition root

- Status: Accepted
- Date: 2026-08-05
- Last amended: 2026-08-07 (t16.3 remediation; t16.2 independent re-verification of deviation #8)
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

- 移設しても例外は消えない。`ReviewApp` は `infrastructure.logging.LogbackLevelSwitcher` を参照するため、
  `presentation` に置けば `presentation → infrastructure` 違反（より厳しいルール）に置き換わるだけである。
- 移設コストは構造的利得に見合わない。`pom.xml`／`pom-native.xml` の `mainClass` 計 4 箇所、
  GraalVM `reachability-metadata.json` 2 ファイル、さらにログ名 `d.l.reviewer.ReviewApp` に依存する
  利用者側のログ解析設定まで壊れる。
- ルートパッケージはすでに事実上のコンポジションルートである。**名前を与えるほうが正しい。**

#### t16.2 訂正 — 初版の前提は誤り

初版は、Rule 4 の例外になっている次の 3 クラスをすべて「Micronaut の DI ファクトリ」とみなし、
まとめてコンポジションルートへ移すよう指示していました。ソースを再確認した結果、その前提は
3 件中 2 件で成立しません。

| クラス | t16.2 で確認した形 | 裁定 | t16.3 追随後 |
|---|---|---|---|
| `ApplicationPortFactory` | `@Factory`。ポートと実装の配線を定義する | **唯一、ルートへの移設対象になり得る** | 変更なし。deviation #4 として Rule 4 の例外に残る |
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
実コンテナで固定します。これにより #8 は解消済みです。Rule 4 の例外に残るのは
`ApplicationPortFactory` とその生成定義だけであり、deviation #4 は引き続き Open です。

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
| `domain` が設定既定値を直接読まない | **Rule 8（t30 で追加済み・例外 0 件。ADR-0008）** |
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
3. **名前が `Factory` の 3 クラスを一括してルートへ移す** — t16.2 で棄却。
   実際に `@Factory` なのは 1 件だけであり、残る 2 件を移すと設定写像ロジックと inbound port 実装を
   ルート例外の内側へ隠す。例外の恒久維持も不採用であり、#8 は t16.3 の責務分離で解消、
   #4 は `ApplicationPortFactory` のみを残課題とする。
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
- Rule 4 には #4 の `ApplicationPortFactory` とその生成定義に対応する例外が残る。
  レビュー経路の #8 は例外削除と実コンテナ束縛テストの両方で解消済みだが、
  残る例外は build green だけで解消扱いにしてはならない。
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
| 4 | `ApplicationPortFactory` が `infrastructure.copilot` にあり、Rule 4 の名前指定例外になっている | D3 | **Open — scope reduced (t16.3)** | `ReviewContextFactory` は outbound `ResolveReviewSettingsPort` 実装へ分離して例外を削除済み。残る実 `@Factory` のルート移設は未実施 |
| 5 | `domain`（4 ファイル）・`application`（10 ファイル）のログ出力が `java.util.logging` のまま | D4 | **Partial** | 相関伝播は t13.1 の `PropagateCorrelationPort` で復元済み。レベル付き診断出力を同じ規律に載せるかは未決 |
| 6 | `ConfigDefaults` / `RetryPolicyUtils` が `shared` と `infrastructure` に重複 | D6 | **Resolved (t13.1)** | `shared` に統合済み。単純クラス名の重複は現在 0 件 |
| 7 | `presentation ⊥ infrastructure` の強制ルールが不在 | D5 | **Resolved (t13.1)** | Rule 5b として追加済み（例外 0 件、違反 0 件） |
| 8 | `infrastructure.copilot.ReviewOrchestratorFactory` が `@Singleton` として inbound `RunReviewPort` を実装し、presentation への DI 束縛先になっていた | D2 / D3 | **Resolved (t16.3; independently verified by t16.2)** | ルート `ReviewPortFactory` が application の `ReviewOrchestrator` を束縛。設定解決と SDK セッション生成は 2 outbound port へ分離し、旧 infrastructure 実装と Rule 4 例外を削除。実コンテナ束縛も固定済み |

## Operational notes

- **エントリポイントは変わりません。** メインクラスは `dev.logicojp.reviewer.ReviewApp` のままであり、
  `pom.xml` / `pom-native.xml` の `mainClass`、GraalVM の `reachability-metadata.json`、
  および `docs/runbook.md` に記載のロガー名 `d.l.reviewer.ReviewApp` は**いずれも変更されません**。
- **CLI の外部仕様は変わりません。** オプション名・設定キー・終了コード・レポート出力先はいずれも再構成の対象外です。
- **層の逸脱は、明示済みの #4 を除いてビルドで落ちます。** 層間依存は `LayerDependencyRulesTest` が JDK 標準の
  `java.lang.classfile` API でバイトコードを直接検査します。`mvn verify` の一部として実行されるため、
  例外に入っていない違反はレビュー前に検出されます。名前指定例外の存在は「解消済み」を意味しません。
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
- `README_ja.md` / `README_en.md` の「プロジェクト構造」「アーキテクチャ」節 — 実装後の層構成

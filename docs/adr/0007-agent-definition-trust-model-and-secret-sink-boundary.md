# 0007: Agent 定義の信頼モデルと、秘匿値の遮蔽境界

- Status: Accepted
- Date: 2026-08-05
- Last amended: 2026-08-08 (t32.1: D3 / D4 / D7 and executable rule traceability)
- Deciders: Multi-Agent Code Reviewer maintainers
- Tags: security, trust-boundary, architecture, ports-and-adapters, observability
- 実装状況: **D5 / D6 は t31（2026-08-06）で実装済み**。Enforcement の Rule 4b を
  `LayerDependencyRulesTest` に追加し、D6 のカナリアテストを
  `SensitiveHeaderMaskingSinkCanaryTest` として新設。移行順序（D6 → D5）は遵守した。

## Context

t18 のセキュリティレビューで HIGH 2 件・MEDIUM 6 件が報告されました。本 ADR はそのうち
**構造に起因する 3 件**（SEC-H2 / SEC-M3 / SEC-M4）に決着をつけます。個別のコード修正
（SEC-H1 の死んだ定数、SEC-M6 のトークン保持など）は t18.2 の担当であり、本 ADR はその
判断基準のみを与えます。

### 問題 1: リポジトリ由来の Agent 定義に信頼モデルがない（SEC-H2）

`AgentPathConfig.DEFAULT_DIRECTORIES` は `./agents` と `./.github/agents` です
（`infrastructure/config/AgentPathConfig.java:11`）。いずれも **CWD 相対** — つまり
**レビュー対象リポジトリの中**を指します。本ツールの用途はリポジトリのレビューですから、
既定動作は「第三者が書いた Markdown を読み込み、LLM のシステムプロンプトとして使う」に
なります。これは t18 の信頼境界表で **B3（未信頼）** に分類された経路です。

現状の防御を実測した結果、t18 の「デニーリストのみ」という要約は**不正確**でした。
`domain/agent/AgentDefinitionPolicy` は既に実効性のある制約を課しています。

| 制約 | 値 | 状態 |
|---|---|---|
| ファイルサイズ上限 | 64 KiB | **稼働**（`validateRawContent`） |
| frontmatter で始まること | `---` | **稼働** |
| `name` 文字種・長さ | `^[a-z0-9][a-z0-9-]{0,63}$` | **稼働** |
| `model` / `peer-model` 接頭辞 | `claude-` `gpt-` `o3` `o4-mini` `gemini-` | **稼働** |
| `focusAreas` | 50 個 × 200 文字 | **稼働** |
| `dialogue-rounds` | 0–10 | **稼働** |
| `enabled: false` による無効化 | — | **稼働** |
| 未知の frontmatter キー | 9 キーを既知として定義 | **警告のみ**（`auditFrontmatterKeys`） |

したがって真の欠落はより狭く、より正確に述べられます。

1. **LLM に到達する自由記述 3 フィールド（`systemPrompt` / `instruction` / `outputFormat`）に
   フィールド単位の上限がない。** 効くのはファイル全体の 64 KiB のみ。
2. **文字種のアローリストがどこにも適用されていない。** それを与えるはずの
   `ALLOWED_CHAR_RANGE` は `CustomInstructionSafetyValidator` の中で**死んでいる**（SEC-H1）。
3. **行数上限がない。**
4. **frontmatter スキーマが開いている。** 未知キーは警告されるだけで拒否されない。
5. **`language` が無検証**（SEC-L2）でテンプレートキーに流れる。
6. **信頼レベルの区別が存在しない。** `--agents-dir` で利用者が明示した B1 のディレクトリと、
   レビュー対象リポジトリの `./.github/agents`（B3）が、まったく同一に扱われます。
7. **同じ職責を持つ検証クラスが 2 つあり、所有者が決まっていない。**
   `domain.agent.AgentDefinitionPolicy`（稼働）と
   `domain.instruction.CustomInstructionSafetyValidator`（半死）。

このうち **6 と 7 が構造的な根因**であり、検証ロジックの追加では解けません。

- **6 の根因**: `LoadAgentPort.loadAll(List<Path>)` は素の `List<Path>` を受け取ります。
  2 系統の出自は `ApplicationPortFactory:53-58` で 1 本のリストに**併合**され、以後
  下流のどのコードも両者を区別**できません**。信頼境界が型に現れていないため、
  どれほど良い検証器を書いても信頼レベル別の扱いは実装不可能です。
- さらに当時の `AgentConfig`（12 要素）は出自を保持しませんでした。D1 の `source` と
  ADR-0008 の是正である `skillBudget` が追加された現在は **14 要素**です。`source` が
  無ければ `AgentConfig` になった時点で
  「どこから来たか」は失われており、事後の権限縮小もできません。
- **7 の根因**: 2 クラスが同じ職責を主張し、どちらも完全ではありません。読者がどちらか一方を
  読むと「検証されている」と結論できてしまう。SEC-H1 の死んだ半分が長く見過ごされたのは
  これが理由です。

### 問題 2: 秘匿値のラッパーが越えられない境界を越えようとしている（SEC-M3 / SEC-M4）

`McpServerSpec` は **`application.port.outbound` のポート DTO** でありながら、正準
コンストラクタで `SensitiveHeaderMasking.wrapHeaders(...)` を適用しています
（`McpServerSpec.java:34`）。しかしその `MaskedHeadersMap` は、1 つの型の中に
**両立しない 2 つの契約**を抱えています。

- `get()` は**生値**を返す — Javadoc に「downstream SDK calls can use real credentials」と明記
- `values()` は**マスク値**を返す

SDK が実際の資格情報を何らかのアクセサ経由で受け取らねばならない以上、
**この `Map` が完全な遮蔽制御になることは原理的にありえません**。上書きし忘れた
アクセサはすべて漏洩経路であり、かつ上書きし尽くすことはできません。
`forEach` と `getOrDefault` を塞ぐ修正は 2 つの実例を潰すだけで、**欠陥の種類は開いたまま**
残ります。これは同じ形の欠陥の **3 度目の再発**です。

加えて `wrapWithMaskedToString` / `MaskedToStringMap`（SEC-M4）は
`src/main` に**呼び出し元が 1 つもありません**（宣言のみ）。名前が実態より強い約束をしている
死んだ公開 API です。

**誠実な限界の明示**: `McpHttpServerConfig` に `toString()` の上書きは確認されておらず、
**稼働中の漏洩シンクは実証されていません**。本 ADR は「実証済みの漏洩」ではなく
**「機構が判明している潜在欠陥」**として扱います。SEC-L4 が指摘する
`COPILOT_SDK_LOG_LEVEL` の引き上げが、これを顕在化させる条件です。

> **t31 追記（計測結果）**: 上記の「未確認」を実測で確定させた。`copilot-sdk-java:1.0.8`
> のバイトコードを検査したところ、
> (a) `McpHttpServerConfig.setHeaders` は防御的コピーのない単純な `putfield` であり、
> (b) `McpHttpServerConfig` / `McpServerConfig` の**いずれも `toString()` を上書きしていない**。
> さらに `src/main` には `McpServerSpec`・ヘッダ・リクエストをログに渡す呼び出しが**存在しない**
> （`ReviewOrchestratorFactory:107` が記録するのは `ExecutionConfig` で、これに `McpServerSpec`
> は含まれない）。
> **結論**: ラッパーが唯一守っていた `toString()` という面は、境界の先では到達不能であり、
> かつ今日そこを通る経路も無い。ラッパーは「弱い防御」ですらなく、**何も守っていなかった**。
> これは D5 の撤去判断を弱めるのではなく、逆に**根拠を強める**。ただし D6 を先に入れる
> 移行順序の要求は（後述の実測した唯一の実効カバレッジ差により）依然として正しかった。

### 問題 3: 「効いているように読めるが何も効いていない」制御の反復

SEC-H1 は同じ失敗の **4 例目**です。

| # | 事象 | タスク |
|---|---|---|
| 1 | ArchUnit が 687 クラス中 107 しか読まず全ルールが空振り | t12 |
| 2 | 許可インポート表の 1 行に対応する強制ルールが不在 | t13.1 / G1 |
| 3 | Rule 4 の対象が `application.port` に限定され残りが無検査 | t16 |
| 4 | 上限・文字種の定数が宣言のみで参照 0 | SEC-H1 |

4 回起きた事象は偶然ではなく、記録されていない規約の欠落です。

## Decision

### D1. 信頼レベルは型で運ぶ

Agent 定義の出自を表す列挙を導入し、**ディレクトリの解決時点から `AgentConfig` まで
一貫して運びます**。

```
domain.agent.AgentSource
  ├─ USER_SUPPLIED        … --agents-dir で利用者が明示したパス（境界 B1）
  └─ REPOSITORY_SUPPLIED  … AgentPathConfig 由来の CWD 相対パス（境界 B3）
```

- `LoadAgentPort.loadAll` の引数は素の `List<Path>` をやめ、出自を伴う型を受け取る。
- `AgentConfig` に出自を表す要素を 1 つ追加する（D1 実装時は 12 → 13 要素）。
  その後 ADR-0008 の `skillBudget` が追加され、現在のレコードは **14 要素**である。
- 出自の判定は**合成のルート側**（`ApplicationPortFactory` で 2 系統を併合している箇所）で
  行い、以後は変更しない。

**信頼レベルは切り替え可能なフラグではなく、出自から決まる恒久的な属性とします。**
`REPOSITORY_SUPPLIED` を `USER_SUPPLIED` に格上げする CLI オプションは設けません。

### D2. Agent 定義ポリシーの単独所有者を定める

- **`domain.agent.AgentDefinitionPolicy` を信頼境界ポリシーの唯一の所有者**とする。
  Agent 定義に課される制約は、例外なくこのクラスから発火しなければならない。
- **`domain.instruction.CustomInstructionSafetyValidator` は「パターン照合の部品」に降格**する。
  上位の制御ではなく、`AgentDefinitionPolicy` から呼ばれる下請けとして位置づける。
  正規化（NFKC・制御文字除去・39 種の同形異字畳み込み）と suspicious パターン照合という
  実装価値のある部分は保持する。
- **死んでいた上限定数は、宣言のあった意図どおり `AgentDefinitionPolicy` 側で稼働させる。**
  定数を消すのではなく、効かせる。

### D3. 信頼レベル別スキーマ契約（現在の全 14 要素に行を与える）

`AgentConfig` の**全要素に行を与えます**。「未規定の要素」を残さないことが本表の要件です。
要素数は文書中の固定値をテストのオラクルにせず、
`AgentConfig.class.getRecordComponents()` から導出し、宣言済みの被覆集合との**完全一致**を
要求します。これにより 15 番目の要素が追加された場合、表とテストを同時に更新しない限り
ビルドが落ちます。

| # | 要素 | 到達先 | USER_SUPPLIED | REPOSITORY_SUPPLIED |
|---|---|---|---|---|
| 1 | `name` | 内部キー | `^[a-z0-9][a-z0-9-]{0,63}$`（現行維持） | 同左 |
| 2 | `displayName` | 出力 | ≤ 200 文字 | ≤ 200 文字 + 文字種 |
| 3 | `model` | SDK | 接頭辞リスト（現行維持） | 同左 |
| 4 | `systemPrompt` | **LLM** | ≤ 32 KiB / ≤ 300 行 | **≤ 8 KiB / ≤ 300 行 + 文字種** |
| 5 | `instruction` | **LLM** | ≤ 32 KiB / ≤ 300 行 | **≤ 8 KiB / ≤ 300 行 + 文字種** |
| 6 | `outputFormat` | **LLM** | ≤ 32 KiB / ≤ 300 行 | **≤ 8 KiB / ≤ 300 行 + 文字種** |
| 7 | `focusAreas` | **LLM** | 50 × 200（現行維持） | 同左 + 要素ごとに文字種 |
| 8 | `skills` | **LLM** | `SkillDefinition` 側で既定（現行維持） | 同左 |
| 9 | `peerModel` | SDK | 接頭辞リスト（現行維持） | 同左 |
| 10 | `rubberDuckEnabled` | 制御 | `boolean`（型で充足） | 同左 |
| 11 | `dialogueRounds` | 制御 | 0–10（現行維持） | 同左 |
| 12 | `language` | テンプレートキー | **アローリスト**（SEC-L2） | 同左 |
| 13 | `skillBudget`（ADR-0008 で追加） | LLM プロンプト予算 | `SkillBudget` の純粋値。Agent 定義からは設定不能 | 同左 |
| 14 | `source`（D1 で追加） | 内部 | 列挙（型で充足） | 同左 |

加えて、ファイル単位・frontmatter 単位で:

| 対象 | USER_SUPPLIED | REPOSITORY_SUPPLIED |
|---|---|---|
| ファイルサイズ | 64 KiB（現行維持） | **16 KiB** |
| 未知の frontmatter キー | 警告（現行維持） | **拒否**（閉じたスキーマ） |

**数値の根拠**（発明ではなく既存の宣言と実測から導出）:

- 32 KiB / 8 KiB / 300 行 は `CustomInstructionSafetyValidator` に
  `MAX_INSTRUCTION_SIZE` / `MAX_UNTRUSTED_INSTRUCTION_SIZE` / `MAX_INSTRUCTION_LINES` として
  **既に宣言されている値**です。設計意図は最初から「未信頼側は 8 KiB」でした。本 ADR は
  新しい値を決めるのではなく、**宣言済みの意図を稼働させます**。
- 文字種は同クラスの `ALLOWED_CHAR_RANGE`（ASCII 印字可能 + 日本語 + CJK 統合漢字 + ハングル +
  約物・矢印・罫線・記号ブロック + `\t\n\r`）をそのまま用います。
- **実測による安全確認**: 本リポジトリ自身の Agent 定義 18 ファイルは
  最大 **4,291 バイト / 97 行**、`ALLOWED_CHAR_RANGE` 逸脱文字は **0 件**。
  16 KiB・8 KiB・300 行・文字種のいずれに対しても 1.8 倍以上の余裕があり、
  本決定による既存定義の破壊はありません。

### D4. 違反時の挙動 — 拒否し、続行し、必ず可視化する

- 違反したファイルは **拒否**（読み込まない）。当該ファイルのみを落とし、実行は続行する。
- 拒否理由には **違反した規則の識別子と出自**を含める。
- 1 回の Agent 読み込みにつき、受理件数と拒否件数を含む
  **`Agent load summary:` の要約イベントをちょうど 1 件出力**する。拒否 0 件でも
  `INFO`、1 件以上なら `WARN` で出力し、「拒否が無かった」と「集計処理が走らなかった」を
  区別できるようにする。
- 可視性の基準は、存在しない `--quiet` オプションではなく、**出荷する既定の
  `logback.xml` で `AgentConfigLoader` の `INFO` / `WARN` が有効であること**とする。
  将来、出力抑制機能を導入する場合は、要約イベントが通常実行で引き続き観測可能であることを
  先にテストで証明しなければならない。
- **0 件になった場合の挙動は現行どおり**（警告して継続）とし、変更しない。
  未信頼リポジトリが自分のレビュアを黙らせることは、Agent を置かない場合と等価であり、
  新たな権限を与えないため。

### D5. ポート DTO はセキュリティ制御を担わない

**ポート DTO の契約は「境界を越えて値を運ぶこと」であり、「値の表示のされ方を取り締まること」
ではありません。**

- `McpServerSpec.headers` は素の不変マップ（`Map.copyOf`）に戻す。
- `application.port.outbound` から `shared.SensitiveHeaderMasking` への依存を除去する。
- `SensitiveHeaderMasking` から `Map` ラッパー生成 API を撤去する。
  残すのは判定関数と整形関数（`isSensitiveHeaderName` / `maskHeaderValue` /
  `maskSensitiveValue` / `buildMaskedMapString`）のみ。
- `wrapWithMaskedToString` と `MaskedToStringMap` は**削除**する（SEC-M4、呼び出し元 0）。

**一般規則**: `toString()` の上書きによるオブジェクト単位の遮蔽を、セキュリティ制御として
採用してはならない。防御的コピーを 1 回受けた時点で失われるため。

**実装（t31）**: 上記をすべて適用済み。`McpServerSpec` は `Map.copyOf` に戻り、
`SensitiveHeaderMasking` は 226 行 → 79 行（判定関数と整形関数のみ）。
撤去後も `src/main` はコンパイルが通り、**本番側の呼び出し元が 1 つも無かったこと**が
コンパイラによって裏付けられた。Rule 4b は追加時点で `McpServerSpec` ただ 1 件を
違反として検出（RED）し、撤去後に GREEN へ遷移した（22 クラス検査・違反 0・例外 0）。

### D6. 秘匿値の遮蔽はシンクで行う

制御を**オブジェクト側からシンク側へ移します**。

- 遮蔽の責務は **ログ／診断出力のシンク**（`infrastructure.logging`）が負う。
  ここは本プロジェクトが所有する境界であり、SDK が何を渡そうと必ず通過する。
- これは ADR-0006 **D4**（純粋性が押し出した横断的関心事はポートとして復元する）と
  同じ形であり、既存の `PropagateCorrelationPort` / `MdcCorrelationAdapter` と
  `shared.LogValueSanitizer`（CRLF 無害化）に前例がある。
- シンク側遮蔽は**どのオブジェクトが保持していたかに依存しない**ため、
  防御的コピー・SDK 内部の再構築・文字列連結のいずれも通り抜けられない。
  これが「越えられない境界を越えようとしない」ということの意味である。

**移行順序（重要）**: D6 のシンク側制御を**先に**入れ、その稼働を確認してから
D5 のラッパー撤去を行う。順序を逆にすると、弱いながら機能していた `toString()` 遮蔽を
代替なしに失う**退行**になる。

**実装（t31）**: `logback.xml` / `logback-json.xml` の全アペンダに適用済み。

- 本 ADR 以前から、両プロファイルには**値の形**に基づく `MASK_PATTERN`
  （`Bearer …` / `ghp_…` / `sk-…` 等）が入っていた（commit `8d8fec1`、本 ADR より前）。
  これは D6 の一部を偶然先取りしていたが、**ADR にも学習記録にも記載が無かった**。
- 撤去するラッパーは**ヘッダ名**に基づいており、既存シンクとカバー範囲が異なる。
  差分を実測した結果、ラッパーだけが持っていた実効カバレッジは
  **「接頭辞に特徴の無い不透明な独自ヘッダ値」**（例: `reviewer.mcp.github.headers` 経由の
  `X-API-Key: <任意文字列>`）**ただ 1 種**だった。
- そこで**ヘッダ名**に基づく 2 本目 `HEADER_MASK_PATTERN` を追加し、この差分を閉じた。
- **入れ子の順序が本質**: `%replace(%replace(%msg){MASK_PATTERN}){HEADER_MASK_PATTERN}` と
  し、値の形に基づく方を**内側**に置く。逆順にすると `HEADER_MASK_PATTERN` の値クラスが
  空白で終端するため `Bearer` の語だけを消費し、**トークン本体が露出したまま残る**。
  `logback-json.xml` では 2 本のマスクを JSON エスケープの**内側**に置く。

カナリアテスト `SensitiveHeaderMaskingSinkCanaryTest` は、**出荷される XML 自体**を読み、
実物の `PatternLayout` で描画して検証する（テスト内でパターンを再宣言しない）。
`HEADER_MASK_PATTERN` を意図的に壊す変異試験では、
**不透明な独自ヘッダのケースだけが RED になり、`Bearer`／生トークンのケースは GREEN のまま**
だった。これは上記のカバレッジ差分の分析を、独立に裏付けている。

### D7. 否定的対照のない制御は、制御ではない

**恒久規則として記録する。**

> セキュリティ上・構造上の制約を新設・改修する際は、**その制約に違反する入力を与えて
> 失敗することを確認するテスト（否定的対照）を同時に用意しなければならない。**
> 否定的対照を伴わない制御は、実装されたとみなさない。

「制御を削除すると落ちる」だけでは片方向です。入力を受理／拒否に分ける制御には、
次の**双方向**の証拠を要求します。

1. **過少拒否（under-block）変異** — 制約を弱め、受理集合を広げる変異をテストが kill する。
2. **過剰拒否（over-block）変異** — 制約を強め、受理集合を狭める変異をテストが kill する。
3. 入力領域が有限か現実的に全列挙できる場合（Unicode code point、enum、レコード要素、
   ADR / Rule 参照など）は、実装とは独立したオラクルから境界を導出して**全件走査**し、
   受理集合／被覆集合の完全一致を主張する。手書きの代表例だけで済ませない。
4. 現在の違反が 0 件の構造ルールは、検査対象が非空であることと、禁止辺を検出できることを
   恒久フィクスチャで主張する。`[] == []` は証拠に数えない。

`LayerDependencyRulesTest` が採る「例外リストと実測違反集合の完全一致を主張する」手法は、
この規則の既存の実装例です（同テスト Javadoc §3 が「これが規則が実際に発火することの
否定的対照を兼ねる」と明記）。本規則はそれを個別テストの工夫から**プロジェクトの規約へ
昇格**させるものです。

## Enforcement

**各決定は、対応する失敗するテストを 1 つずつ持ちます**（ADR-0006 D5 の要件を本 ADR にも適用）。
テストが書けない決定は、決まっていないものとして扱います。

| 決定 | 強制手段 | 種別 | 否定的対照の形 |
|---|---|---|---|
| D1 | **差分テスト**: 同一の Agent 定義ファイルを `USER_SUPPLIED` として読むと**受理**、`REPOSITORY_SUPPLIED` として読むと**拒否**されること（例: 9 KiB の `instruction`） | 差分 | 出自が末端まで運ばれていなければ両者が同一結果になり、必ず落ちる |
| D2 | `AgentDefinitionPolicy` の各定数・各パターンについて、`src/main` 参照数 ≥ 1 かつ `src/test` 参照数 ≥ 1 を主張するテスト | メタ | SEC-H1 と同型の「宣言のみ」を再発時に検出 |
| D3 | `AgentSchemaCoverageTest`: `AgentConfig.class.getRecordComponents()` から導出した全要素と被覆表を完全一致させる。`POLICY_RULE` の各スカラー契約には実際の拒否入力を与える | メタ + 否定的対照 | 要素数を固定値にしないため、15 番目の要素を被覆判断なしで追加すると落ちる。被覆表をすべて `TYPE_SATISFIED` にして空虚化することも拒否する |
| D4 | `AgentLoadRejectionReportingTest`: 違反ファイル 1 件を含むディレクトリを読み、(a) 他の Agent は読み込まれ、(b) 拒否理由に規則識別子と出自が含まれ、(c) `Agent load summary:` がちょうど 1 件出ること、(d) 拒否 0 件でも `INFO` で要約が出ることを主張 | 振る舞い | 要約呼び出しを削除すると拒否あり／なしの双方が落ちる。既定ログ設定で `INFO` が有効であることも固定する |
| D5 | `application.port` 配下のクラスが `shared.SensitiveHeaderMasking` を参照しないことを **`LayerDependencyRulesTest` の Rule 4b** として追加 ✅ **実装済み（t31）** | 構造 | 例外リストとの完全一致方式により、不要になった例外も落ちる。加えて Rule 4b は**空集合になり得る**ため、(a) 検査対象が非空であること (b) 参照が実際に検出可能であること を主張する対照 `rule4bCanSeeAMaskingReference` を常設した |
| D6 | **カナリアテスト**: 既知のトークン値を含む生マップをロガーに渡し、アペンダ出力にカナリア文字列が現れないことを主張 ✅ **実装済み（t31）**: `SensitiveHeaderMaskingSinkCanaryTest` | 否定的対照 | 遮蔽が外れた瞬間に落ちる。オブジェクトの型に依存しない。変異試験で RED になることを確認済み |
| D7 | `CharsetAllowlistSweepTest` の全 Unicode 走査 + under-block / over-block 変異、および t32.2 の `AdrRuleReferenceGuardTest`（Accepted ADR の D-item ↔ 実行可能な `Rule N[x]` を双方向照合） | メタ + 双方向否定的対照 | ルール名をテスト側だけ／ADR 側だけで改名する 2 変異がそれぞれ RED になり、ADR・D-item・Rule を失敗メッセージに出す。Rule 7 の RESERVED コメントは実行可能テストではないため在庫に含めない |

**ルール番号について**: ADR-0006 **D5** に従い、新ルールは論理的な位置に**英字接尾辞**で
追加し、既存番号を振り直しません（学習記録と ADR が番号を引用しているため）。
D5 の構造ルールは Rule 4（`infrastructure` はポート経由でのみ `application` に到達する）の
直後に **Rule 4b** として置きます。

## Alternatives considered

1. **`--allow-repo-agents` によるオプトイン（t18 の提案 2）を単独で採用する。**
   却下。(a) レビュー対象リポジトリが自身のレビュア定義を持つという主要ユースケースを
   既定で壊す。(b) より重大な点として、利用者はフラグを反射的に付けるようになり、
   その後の防御はゼロに戻る。これは境界ではなく UX ゲートである。
   ただし「未信頼由来には常に厳格プロファイルを適用する」という形（D1）で、
   提案の趣旨は恒久的な属性として取り込んだ。

2. **`MaskedHeadersMap` の未上書きアクセサ（`forEach` / `getOrDefault` 等）を塞ぐ。**
   却下。SDK が生値を何らかの経路で受け取る必要がある以上、遮蔽は原理的に不完全であり、
   個別の穴を塞いでも欠陥の種類は開いたまま残る。同型の欠陥が 3 度再発した事実が、
   個別対処では収束しないことの証拠である。

3. **全ヘッダを `SecretValue` 型で持つ。**
   却下。`Content-Type` のような非機密ヘッダにまで `reveal()` を強制し、
   呼び出し側の可読性を損なう。秘匿値の型付け自体は有効なので、
   **トークンそのもの**（SEC-M6、t18.2 の担当）に限定して適用する。

4. **`AgentPathConfig.DEFAULT_DIRECTORIES` から CWD 相対パスを削除する。**
   却下。本ツールの主要な価値をなくす。問題はリポジトリ由来の定義を読むこと自体ではなく、
   それを**利用者自身の定義と区別せずに**読むことである。

5. **D7 を ADR-0006 の修正として記録する。**
   却下。ADR-0006 は Accepted 済みであり、決定番号の追記は追跡性を損なう。
   本 ADR の D7 として記録し、ADR-0006 からは参照のみとする。

## Consequences

### Positive

- 信頼境界が**型に現れる**ため、以後の変更で区別を失うと**コンパイルまたはテストが落ちる**。
  レビュー時の注意力に依存しない。
- Agent 定義に対する制約が**1 箇所（`AgentDefinitionPolicy`）に集約**され、
  「どちらかを読んで安心する」失敗が起きなくなる。
- 死んでいた定数（SEC-H1）が**削除ではなく稼働**によって解消される。
  設計意図（未信頼側 8 KiB）が失われない。
- 秘匿値の遮蔽が**オブジェクトの形に依存しなくなる**ため、SDK の内部実装変更・
  防御的コピー・ログレベル変更のいずれにも影響されない。
- `application.port` パッケージから `shared` のセキュリティユーティリティ依存が消え、
  ポート層が薄くなる。

### Negative / Trade-offs

- `AgentConfig` は D1 で 12 → 13 要素になり、その後 ADR-0008 の `skillBudget` 追加で
  現在は 14 要素である。今後の追加はリフレクション由来の被覆検査により、判断なしでは通らない。
- `LoadAgentPort` の署名が変わる。インバウンドポートの契約変更であり、
  `presentation` 側の呼び出し 2 箇所（`ReviewCommand` / `ListAgentsCommand` 経由）に波及する。
- 未信頼側の制約により、**8 KiB を超える正当な `systemPrompt` を持つリポジトリ定義は
  読み込まれなくなる**。回避策は `--agents-dir` による明示指定（B1 への格上げ）であり、
  これは利用者の意思表示として妥当。
- D6 のシンク側遮蔽は、ログ出力経路に処理を 1 段追加する。

### 移行リスク（HIGH）

**D5 を D6 より先に実施してはならない。** 現行の `toString()` 遮蔽は不完全ながら機能して
おり、代替なしに撤去すると防御が純減する。t18.2 は D6 → 稼働確認 → D5 の順で進めること。

> **t31 追記: このリスクは解消済み（順序を遵守した）。** 実施順は
> ① Rule 4b を追加し RED を目視 → ② D6 を適用しカナリアで稼働確認 → ③ D5 を撤去 →
> ④ Rule 4b が GREEN になることを確認、である。
> なお t31 の課題文はこの順序に言及していなかったが、**ADR 自身が課す順序制約が
> タスクの記述に優先する**と判断した。実測の結果、ラッパーの実効カバレッジは
> 「不透明な独自ヘッダ値」1 種のみで、D6 の `HEADER_MASK_PATTERN` がこれを閉じている。
> したがって本移行による防御の純減は**無い**。

## Known limits（誠実な限界）

- **SEC-M3 は実証された漏洩ではない**（t31 で実測により確定）。`copilot-sdk-java:1.0.8` は
  `McpHttpServerConfig` / `McpServerConfig` の**どちらでも `toString()` を上書きしておらず**、
  ヘッダマップを防御的コピーせずそのまま保持する。稼働中の漏洩シンクは依然として未特定であり、
  機構が判明している潜在欠陥として扱う。`COPILOT_SDK_LOG_LEVEL` の引き上げ（SEC-L4）が
  顕在化の条件。**SDK が将来 `toString()` を追加した場合、D6 のシンク側遮蔽はそれを
  自動的にカバーする**（オブジェクトの型に依存しないため）。これが D5/D6 を選んだ理由である。
- **シンク側遮蔽は「テキストの形」に対する制御である。** ログ／診断出力の経路は覆うが、
  シリアライズされた JSON ボディ、ヒープダンプ、コアダンプ、デバッガ、
  および SDK が独自に持つ出力経路（本プロジェクトの logback を通らないもの）は覆わない。
  これはオブジェクト単位の遮蔽でも同様に覆えなかった範囲であり、**退行ではない**が、
  「秘匿値がプロセスメモリ上に平文で存在する」という事実は変わらない。
  トークンの寿命短縮・最小権限化は別の層の課題として残る。
- **デニーリストは廃止しない。** D3 のアローリストは入力空間を有界にするが、
  有界な空間内の悪意ある指示は依然として通る。両者は代替ではなく重層である。
- **`validateRawContent` はバイト数ではなく UTF-16 文字数で判定している**
  （メッセージは "bytes" と表示）。D3 の上限もこの単位に揃えるか、
  バイト数に統一するかは t18.2 の実装判断とし、**選んだ側をテストで固定すること**。
- 本 ADR は SEC-M1（`ContentSanitizer` に秘匿値の伏字がない）を扱わない。
  これは再構成による退行ではなく既存の欠落であり、t18.2 の担当。

## Operational notes

- 拒否要約行は出荷する既定の `logback.xml` の `INFO` / `WARN` で見えること。
  現在は `--quiet` オプションが存在しないため、それを対象にした空虚な保証は置かない。
- リポジトリ由来 Agent が 1 件も読み込まれなかった場合、既存の
  `No agents found in any configured directory` 警告に加えて、
  **拒否によるものか不在によるものかを区別**できる文言とすること。
- 本 ADR の決定は `.github/copilot-instructions.md` の記述と整合させること
  （同ファイルは t13 で削除された旧パッケージ構成を記述したまま残存していた）。

## References

- ADR-0006: Adopt Ports & Adapters layering with an explicit composition root
  （D4 = 押し出された横断的関心事のポート化、D5 = 表の各行に強制ルールを 1 つ、
  D6 = `shared` の単独所有）
- t18 セキュリティレビュー: `artifacts/t18-security.md`,
  `t18-security-input-validation.md`, `t18-security-secrets.md`
- `LayerDependencyRulesTest` Javadoc §3（否定的対照を兼ねる例外リスト方式）
- 実装対象タスク: t18.2

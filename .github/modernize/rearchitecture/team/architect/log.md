## [t2] Analyze current architecture — dependency cycles and framework leakage
- Recon reported 6 cycles; actual count is 10 (4 additional intra-report cycles and the util→agent was actually bidirectional)
- `TemplateService` is the single biggest cycle hub — involved in 5 of 10 cycles
- `AgentConfig` + `ReviewResult` are shared domain models trapped in wrong packages — create 5 cycles
- SLF4J is in 50/120 files — domain purity requires either dropping logging or switching to j.u.l.
- Micronaut `@Nullable` is used as a simple nullability annotation in many domain-like classes — can be replaced with `java.util.Optional` or removed
- `agent` package (30 files) is the hardest decomposition target: mixes 5 different responsibilities across all target layers
- Learnings consumed: (none)

## [t4] Design target layered architecture, full package mapping, and port catalog
- Produced 3 detail files: packages (24 target packages), ports (12 interfaces), classmap (120→138 files)
- Key design: 5 inbound + 7 outbound ports; LoadTemplatePort breaks 5 cycles; domain type moves break 5 more
- Learnings consumed: [architect/shared-domain-types-cycle-roots, architect/template-service-cycle-hub]
- RetryExecutor→SharedCircuitBreaker cycle resolved by parameterizing circuit breaker (pass via method arg, not import)
- Cycle 9 (finding⇄formatter) resolved by making FindingsExtractor produce data only, Formatter consumes — no mutual ref
- domain.report is largest sub-package (~18 files) — may benefit from further sub-splitting during implementation
- DoctorCommand needs new RunDiagnosticsPort to avoid SDK types in presentation

## [t16] Authored ADR 0006 and re-synced all user-facing docs to the implemented layering

- **Docs must be verified against source, not against upstream artifacts.** t4's port catalog
  (§2.1) still showed a superseded single-result `RunReviewPort`, and the README mermaid named
  8 classes that no longer exist. Had I documented from the artifacts alone, ADR-0006 would have
  shipped a wrong contract. Every structural claim in this task was re-derived from HEAD.
- **The port-direction defect was found while writing docs, not while reviewing code.** Trying to
  draw the layer diagram forced the question "who implements this port?" — which immediately
  exposed that `ResolveTokenPort` and `ExecuteSkillPort` are inbound-by-package but
  infrastructure-by-implementer. Diagramming is a cheap defect detector; the arrow has to point
  somewhere and a misclassified port makes it point the wrong way.
- **`ExecuteSkillUseCase` is dead code**: `ApplicationPortFactory` binds the inbound port straight
  to `infrastructure.copilot.SkillExecutor`, so the application-layer use case has zero references
  outside its own Javadoc. A grep for "who calls this" is worth running on every use-case class
  before certifying a layer.
- **Wrong initial assumption: "move `ReviewApp` down into `presentation`."** It looked like the
  obvious fix for the Rule 3 exemption. It is not — it trades a documented exemption for a
  *stricter* `presentation → infrastructure` violation, and breaks `mainClass` in 4 places, 2
  GraalVM `reachability-metadata.json` files, and the `d.l.reviewer.ReviewApp` logger name that
  `docs/runbook.md` asserts verbatim. Moving the 3 `@Factory` classes *up* into the root instead
  reduces net exemptions and left runbook.md needing no edit at all. Check what asserts a class's
  FQN before proposing to move it.
- **Technique worth reusing:** for line-for-line parallel EN/JA docs, splice with a throwaway
  script that (a) asserts its anchor lines before touching anything and (b) replaces regions
  bottom-up so earlier offsets stay valid. First run failed its own assertion because
  `'│   └── util/'.strip()` does not start with `└──` — box-drawing glyphs are not whitespace.
  Assert on `in`, not `startswith`, when tree glyphs are involved.
- **Historical ADRs: cross-link, don't rewrite.** 0001/0002/0003 are still Accepted; only their
  file paths moved. Adding one "still valid, location moved" line to each References section
  preserves the decision history while removing dead paths.
- Learnings consumed: [architect/port-catalog-design, architect/domain-subpackage-organization,
  architect/shared-domain-types-cycle-roots, architect/template-service-cycle-hub,
  teamlead/layer-naming-conventions, teamlead/domain-purity-rules]
- **A concurrent task landed mid-write and invalidated part of the ADR.** t13.1 shipped while t16
  was drafting: it named the logging port `PropagateCorrelationPort` (I had written
  `LogExecutionPort`), numbered the new rule `5b` (I had written `Rule 7`), and closed two of the
  seven Known deviations. Re-running the verification sweep *immediately before* writing `[DONE]`
  caught all of it. **Verify twice on a documentation task: once to write, once to publish.** An
  ADR that names a port which does not exist is worse than no ADR.
- Backend's rule-numbering choice (`5b` inserted in position, not `7` appended) is better than
  mine and is now the recorded convention — prior learnings cite Rules 6a/6b by number, and
  appending would have been fine but inserting keeps the dependency-direction rules contiguous.
- Add a **Status** column to any "Known deviations" table in an ADR. Without it the table reads as
  a permanent indictment; with it, later tasks can close rows in place and the ADR stays live.

## [t18.1] ADR-0007 — 信頼モデルと秘匿値の遮蔽境界

- **セキュリティ報告の前提を鵜呑みにしなかったのが分岐点だった。** 報告は SEC-H2 を
  「防御はデニーリストのみ」と要約していたが、`AgentDefinitionPolicy` を実読すると
  64 KiB 上限・名前の文字種・model 接頭辞・要素数上限は**稼働中**だった。報告の要約に
  沿ってアローリストを足す ADR を書いていたら、既にある制御を再発明しただけで真因は
  残っていた。**所見表の 1 行は、必ず該当ソースまで降りて確認する。**
- **真因は検証ロジックではなく型だった。** `ApplicationPortFactory:48-58` で利用者指定
  ディレクトリと CWD 相対の既定ディレクトリが素の `List<Path>` に併合されており、
  出自の情報がそこで消えていた。`AgentConfig` にも出自要素がない。つまり
  「未信頼側だけ厳しく」は下流でどう書いても実装不可能な状態だった。
  → 詳細は `learnings/architect/trust-level-must-be-carried-by-a-type.md`
- **`MaskedHeadersMap` は「直せるバグ」ではなかった。** `get()` は生値・`values()` は
  マスク値という両立しない 2 契約が 1 型に同居しており、構造的に完全な遮蔽になり得ない。
  パッチではなく境界の移動（シンク側）が必要と判断。
  → `learnings/architect/secret-redaction-belongs-at-the-sink.md`
- **数値は発明せず発掘した。** 8 KiB / 32 KiB / 300 行は
  `CustomInstructionSafetyValidator` に**既に宣言されていた**死んだ定数の値。
  「未信頼は 8 KiB」は元々の設計意図であり、ADR はそれを復活させただけ。
  新しい数字を提案するより、既存の意図を掘り起こす方が反論されにくい。
- **上限を書く前に実測した。** 自リポジトリの `.agent.md` 18 ファイルを計測（最大
  4,291 B / 97 行）し、`ALLOWED_CHAR_RANGE` を Python で再実装して全ファイルに当てて
  逸脱 0 文字を確認。**自分のリポジトリを壊す規則を提案しかけていないかは、
  提案する前に測れる。** 1.8 倍以上の余裕があると分かって初めて数値を確定した。
- **「差分テスト」という強制の型を得た。** 単一経路のテストは出自をハードコードした
  実装でも通ってしまう。同一ファイルを 2 つの出自で読ませ受理／拒否に分かれることを
  主張すれば、出自が末端まで運ばれていない実装は必ず落ちる。以後、信頼レベルや
  権限を扱う決定にはこの形を使う。
- **ドキュメント修正の指摘は氷山の一角だった。** コーディネータの指摘は
  `{{placeholder}}` 1 点だったが、`.github/copilot-instructions.md` の Architecture 節は
  t13 で削除済みの旧 9 パッケージ構成をそのまま記述していた。同ファイル内で
  `{{placeholder}}` と `${repository}` が矛盾していたのが最初の手がかり。
  **文書が自分自身と矛盾していたら、報告された箇所以外も腐っていると疑う。**
- 未着手として申し送り: `.sdkmanrc`(Java 26) と `pom.xml`(Java 27) の不整合は devops 案件。
  SEC-L6 / SEC-L8 は architect 所有だがタスク範囲外のため未裁定。
- Learnings consumed: [architect/matrix-row-requires-enforcement-rule,
  architect/purity-displaced-capabilities-become-ports, architect/reverify-docs-before-publishing,
  architect/port-direction-by-implementer, security/trust-boundary-severity-calibration,
  security/masked-map-accessor-matrix, security/dead-security-controls]

## [t24] マージ後アーキテクチャ適合再検査 — PASS（0 CRITICAL / 1 HIGH / 2 MEDIUM）

- **判定項目は「前提」から検証する。** 3-A は「YAML 面もテストもない多重パス機能」という前提付きで
  提示されたが、両方とも誤り。`sharedSessionEnabled` は CLI フラグ `--no-shared-session`
  （`ReviewOptionsParser:211` / `CliUsage:48`）と **inbound port DTO のフィールド**を持ち、
  `reviewPasses` は `@Bindable` キー `reviewer.execution.concurrency.review-passes` と
  `> 1` を実行するテスト 3 件を持つ。設問どおり答えていれば公開 CLI フラグと port フィールドを
  削除していた。ADR-0006 D3 に続く 2 例目。→ learnings 化。
- **「出荷 YAML にない」≠「設定面がない」。** Micronaut は `@Bindable` キーを利用者 YAML・環境変数・
  システムプロパティから束縛する。設定面の有無は注釈側で確認する。
- **Rule 0 の `331/331` は「網羅」を証明しない。** `analyseBytecode()` は `target/classes` を 1 回
  walk して `classFilesOnDisk` と `dependencies` の**両辺**を作る。自己整合チェックであって、
  ソースがコンパイルされたかは言えない。ソース→クラスの対応を独立に検査して初めて意味を持つ
  （175/175、マージ由来 28/28）。層別 328 + ルート 3（`ReviewApp`, `$ReviewApp$Definition`,
  `ReviewApp$GlobalOptions`）= 331 で完全に一致。
- **同一 ADR 条項でも遵守の質に差が出る。** D6 に対し `SkillConfig:22` はコンパイル時定数参照で
  ドリフト不能（模範）、`PromptBudgetConfig` は `@Bindable(defaultValue="12000")` で既定値を
  文字列リテラル再宣言（F2）。値は現在一致しているが、`PromptBudgetConfigTest:37` は
  Java コンストラクタ経路しか固定しておらず Micronaut 束縛経路は素通り。
- **似た名前の制御を同一視しない。** `AgentConfigLoader` の per-file 上限（227 行）と累積予算
  （189 行）は別物。`rejectsOversizedSkillFile` は前者しか踏まず、しかも `metadata.agent` を
  持たないため後者の分岐自体に到達しない。「予算のテストはあるか」を grep で見ると緑に見える（F1）。
- **純粋性ルールは所属ルールではない。** Rule 2 は `shared` の import だけを縛る。`java.*` しか
  使わなければ何でも置ける。#4 の裁定で無自覚に使っていた基準（2 層以上から参照 ∧ 業務語彙なし）を
  明文化すべき。なお「2 層から使われる」だけでは不十分 — `application → domain` が合法なら
  `domain` が正しい置き場になる。
- 手法: 判定の根拠はすべて upstream 成果物ではなく**コードに対する実行**で取り直した
  （import 全列挙・consumer 逆引き・`@Bindable` リテラルと定数の突き合わせ・単純名重複 0 件確認）。
- ソース変更は 0 件。F2/F3 は小さいが設計選択を含むため憲章どおり escalate に留めた。
- Learnings consumed: [architect/matrix-row-requires-enforcement-rule,
  architect/composition-root-as-layer-zero, architect/port-direction-by-implementer,
  architect/purity-displaced-capabilities-become-ports, architect/reverify-docs-before-publishing,
  architect/trust-level-must-be-carried-by-a-type, architect/secret-redaction-belongs-at-the-sink,
  backend/merging-upstream-into-restructured-tree, backend/self-cleaning-architecture-exclusions,
  backend/architecture-rule-negative-control, backend/derived-exemptions-for-generated-beans,
  backend/duplicate-utility-consolidation-semantic-drift, tester/never-pipe-a-verification-build]

## [t24 round 1] Post-merge conformance re-check — CLEAN PASS; F1 closed, F4 downgraded to MEDIUM and excluded

- **The merge state changed under me between rounds.** Round 0 recorded `MERGE_HEAD=5844456`,
  staged-not-committed. Round 1: no `MERGE_HEAD`, `HEAD=3ed3eda`, both commits ancestors. My
  round-0 wording ("the staged merge may be committed as-is") had become a factual error in my
  own artifact. **Re-establish git state at the top of every re-check round** — a verdict phrased
  against a state that no longer exists reads as a stale rubber stamp.
- **I falsified a premise of my own making.** I first grepped shipped skills for a top-level
  `agent:` key, found none, and concluded site 5 was unreachable — a conclusion that would have
  made F4 trivially dismissible. Wrong: `SkillMarkdownParser` reads `metadata` via
  `FrontmatterParser.parseNestedBlock(raw, "metadata")`, so the key is **nested** under
  `metadata:`. 25 of 34 skills are agent-assigned. The grep matched the wrong shape. Lesson:
  when a negative result would conveniently settle a hard question, that is exactly when to
  re-derive it from the parser's actual code path rather than from a guessed file format.
- **Backend's premise was also false, and in the same area.** t26 §C cited a 12,908-byte skill
  dropped every run as F4's live incentive. It is dropped at the *file gate* (site 2, byte-
  denominated, different warning text), and it carries no `metadata.agent`, so raising the knob
  would admit it only for `AgentPromptBuilder:127` to filter it straight out. The cited scenario
  cannot chain to the crash. Two false premises in one round; `rule-the-premise-before-the-question`
  has now paid off four times in this run.
- **Simulating the gate chain beat reading it.** Rather than argue about reachability, I
  reimplemented the exact chain (file gate → content gate → cumulative gate → render, including
  the header text and per-skill markup) over all 9 agents × 25 assigned skills. Result: worst
  agent at 3,858/10,000 rendered — 61 % headroom, zero warnings, zero throws. That single table
  did more to settle F4's severity than any amount of code reading, and it also produced the
  `72 + ~10n` markup constant that corroborated backend's `71 + 10n` estimate.
- **Three escalated "decisions" were one defect.** They arrived as separate questions (split the
  knob / bytes-vs-chars / make site 1 a pre-check) and each has a tempting local answer. Answering
  them together revealed that the pre-check framing is actively harmful — it would make
  infrastructure track domain's rendering format forever — and that one change (inject the budget
  as a value, degrade gracefully) resolves F4, decision A's motivation, and decision C at once.
  **When several escalations touch the same mechanism, rule them as a set.**
- **Re-framing beat adjudicating.** The knob split was escalated as a breaking config-contract
  change needing ADR + migration notes. Making the new keys *additive with fallback to the
  existing knob* removes the breakage entirely and drops the cost to a single ADR. The most
  valuable architect output this round was rejecting the question's shape, not answering it.
- **Guarding against outcome-driven severity.** F4 was proposed HIGH and the gate could not clean-
  PASS with a HIGH open, so downgrading it was suspiciously convenient. I forced the test "would
  I rule MEDIUM if the verdict did not depend on it?" — yes, on three grounds that stand alone
  (bit-identical to `origin/main`; cited trigger provably doesn't chain; 61 % headroom). I also
  recorded the one thing the restructure made *worse* (Rule 1 removes the one-line fix, so F4 is
  now a design task) rather than letting the downgrade read as a whitewash.
- Learnings consumed: [architect/rule-the-premise-before-the-question,
  architect/completeness-assertions-need-an-independent-side,
  architect/matrix-row-requires-enforcement-rule, architect/purity-rule-is-not-a-membership-rule,
  architect/trust-level-must-be-carried-by-a-type,
  architect/purity-displaced-capabilities-become-ports]

## [t30] ADR-0008 + Rule 8 — the rule that could not prove itself
- **The premise was nearly false.** Rule 8 targets a `public static final int`. That is a JLS §4.12.4
  *constant variable*, resolved at compile time (§13.1), so the read compiles to `sipush 10000` with
  **no `Fieldref`**. The field-level rule t24 specified was not hard — it was impossible. What saves
  it is that javac still emits an *unreferenced* `CONSTANT_Class` ghost entry. That is compiler
  behaviour, not a JVMS guarantee.
- **A grep-shaped probe told me the opposite, confidently.** My first check "confirmed" a `Fieldref`
  existed; it was matching javap's disassembly text, not the pool. Re-running with `javap -v` and
  reading the pool directly reversed the answer. Cheap probes lie in the direction you want.
- **Biggest catch: an empty violator set means the rule observes nothing.** This file proves a rule
  fires by asserting violators == exemptions. With 0 violators *and* 0 exemptions that check passes
  for a broken predicate, a misspelled constant, or an absent pool entry. Rule 8 would have shipped
  green and blind — the exact defect ADR-0008 exists to name, occurring inside the mechanism meant to
  prevent it. Fixed with a permanent fixture-based control, then *proved* it by reintroducing F4's
  original shape and watching Rule 8 go red.
- **Measured blind spot:** a read in a `case` label leaves zero trace in the pool. Documented rather
  than hidden; budget gates use `>`, not `switch`.
- **Tenth instance found in the ADR series itself.** ADR-0007 D5 declares "Rule 4b"; `grep "Rule 4b"
  src/test` → 0 matches, and `McpServerSpec:34` genuinely calls `SensitiveHeaderMasking.wrapHeaders`.
  An Accepted ADR declared an enforcement nobody built. Left unfixed on purpose — it goes red
  immediately and needs a real design decision, and bundling it would make Rule 8's green
  unattributable.
- **Trap that cost three builds: another agent is writing to this same worktree.** Their `mvn clean`
  deleted `target/classes` mid-build, giving me one phantom test failure and then a cascade of
  `bad class file … NoSuchFileException` on classes that had just compiled successfully. Both looked
  like real regressions. Verified in an `rsync` copy at `/tmp/t30iso` instead: 969 tests, 0 failures.
  Never trust a shared-worktree build result you did not isolate.
- Learnings consumed: [architect/rule-the-premise-before-the-question,
  architect/completeness-assertions-need-an-independent-side,
  architect/matrix-row-requires-enforcement-rule, architect/reverify-docs-before-publishing,
  architect/purity-rule-is-not-a-membership-rule]

## [t31] Implemented ADR-0007 Rule 4b + D5/D6; resolved the McpServerSpec violation
- **The task framing omitted a constraint the ADR itself imposed.** ADR-0007 carries a HIGH
  migration risk: "D5 must not precede D6." t31 asked only for Rule 4b + violation resolution.
  Doing the obvious thing (delete the wrapper → rule goes green) is precisely the regression the
  ADR forbids. Rule: **read the ADR's own risk/ordering sections before executing a task that
  cites one of its D-items.** The task description is not the whole specification.
- **The wrapper protected nothing — measured, not assumed.** ADR-0007 recorded the SDK's
  `toString()` behaviour as unverified. Bytecode inspection of `copilot-sdk-java:1.0.8`:
  `setHeaders` is a plain `putfield` (no defensive copy), and *neither* `McpHttpServerConfig` nor
  `McpServerConfig` overrides `toString()`. Plus zero call sites in `src/main` log an
  `McpServerSpec`. So the one guarded surface was unreachable and unvisited. Worth the 20 minutes:
  it converted a "weak but working defence" narrative into a measured "no defence" — which changed
  how I wrote the ADR, though *not* the execution order (see next point).
- **"It protected nothing" still didn't license reordering.** The wrapper had exactly one genuine
  coverage edge over the pre-existing sink: opaque custom header values (`X-API-Key: <no prefix>`).
  Name-based vs value-shape-based masking are different sets. That single delta is what D6's second
  pattern exists to close. Nearly talked myself into "D6 is unnecessary" from the null result.
- **Found undocumented prior art.** logback already had `%replace` masking from commit `8d8fec1`,
  predating ADR-0007, mentioned in neither the ADR nor any learning. The ADR was written as if the
  sink were bare. Always diff the ADR's assumed starting state against the actual tree.
- **Regex nesting order was a real trap.** `HEADER_MASK_PATTERN`'s value class stops at whitespace,
  so if it runs *outside* `MASK_PATTERN` it eats only the word `Bearer` and leaves the token
  exposed. Caught it by testing the composed pattern in a throwaway Java program before touching
  logback — cheaper than a Maven cycle, and the failure would have been silent.
- **Logback gotchas when testing the shipped XML**: `${...}` substitution is Joran's job, not
  `PatternLayout`'s — resolve the properties yourself. A bare `new LoggerContext()` has no
  MDCAdapter (`setMDCAdapter(new LogbackMDCAdapter())`), and `new LoggingEvent()` has no logger
  context — build events via `context.getLogger(...)`. Three consecutive errors, each ~1 min.
  Reading the actual stack trace beat guessing every time.
- **Mutation-testing the canary paid for itself twice**: it proved the canary can fail, *and*
  the per-case red/green split independently confirmed my coverage analysis (only the opaque-header
  case went red). Evidence I did not have to argue for.
- Learnings consumed: [architect/empty-violator-set-needs-a-permanent-control,
  architect/matrix-row-requires-enforcement-rule, architect/verify-the-premise-before-ruling,
  architect/disclose-scope-exceeded-instead-of-narrowing]

## [t16.2] Corrected ADR-0006 D3 and recorded review-path inversion #8

- D3's three-class premise was false: only `ApplicationPortFactory` is `@Factory`;
  `ReviewContextFactory` maps config, and `ReviewOrchestratorFactory` is a `@Singleton` implementing
  inbound `RunReviewPort`.
- Ruled that moving the latter two into the root would conceal, not fix, the boundary defect.
  Deviation #8 remains HIGH and needs a dedicated backend refactor that binds an application
  implementation and removes the Rule 4 exemption.
- Chose the real-refactor branch: architecture records only, no partial production-code change.
- The final whole-ADR sweep also caught its operational note still naming Java 27 /
  GraalVM 25.0.4; reconciled it to `pom.xml` Java 28 and `.sdkmanrc` GraalVM 25.0.3.
- A first verification assertion searched raw `@Singleton` text and falsely matched
  `ReviewOrchestrator` Javadoc saying the annotation had been removed. Anchoring checks to complete
  annotation lines corrected the verifier; the final source/ADR suite passed 12/12.
- Learnings consumed: [architect/composition-root-as-layer-zero,
  architect/port-direction-by-implementer, architect/rule-the-premise-before-the-question,
  architect/reverify-docs-before-publishing, architect/matrix-row-requires-enforcement-rule,
  architect/inherited-defect-is-not-a-merge-finding]

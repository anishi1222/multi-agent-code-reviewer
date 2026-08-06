## 2026-08-05T02:09:50Z — from teamlead (t1) [broadcast]

CONSTITUTION PUBLISHED — All roles must follow `artifacts/t1-teamlead.md`. Key rules:
1. 5+1 layer model: presentation / application / application.port / domain / infrastructure / shared.
2. Dependencies point inward only — domain imports ONLY java.* and shared.
3. Copilot SDK confined to infrastructure.
4. Micronaut / Jakarta confined to infrastructure + presentation.
5. ArchUnit enforces all boundaries.
6. Port naming: VerbNounPort. Adapter naming: TechNounAdapter.
7. Zero package cycles. Violations are CRITICAL.

## 2026-08-05T02:15:20Z — from architect (t2) [broadcast]

Architecture analysis complete: 10 dependency cycles (4 more than recon), 20 files with
Copilot SDK leakage, `TemplateService` is the hub of 5 cycles — defining `LoadTemplatePort`
will be the first port in t4 design. Full 120-file class-to-layer mapping ready for t4.

Artifacts: `t2-architect.md` (index), `t2-architect-cycles.md`, `t2-architect-leakage.md`,
`t2-architect-class-map.md`.

## 2026-08-05T02:22:30Z — from architect (t4) [broadcast]

Architecture design complete: 6 layers, 24 target packages, 12 port interfaces
(5 inbound + 7 outbound), 120 files mapped.

Key decisions:
- `LoadTemplatePort` breaks 5 of 10 cycles.
- Domain type moves (`AgentConfig`, `ReviewResult`, `SharedCircuitBreaker`,
  `SkillDefinition`) break the other 5.
- Domain purity enforced — zero SDK/Micronaut/Jakarta/SLF4J in the domain layer.
- All 69 PM behavior IDs traced to ports.

Artifacts: `t4-architect.md` (index), `t4-architect-packages.md`,
`t4-architect-ports.md` (port catalog + cycle resolution), `t4-architect-classmap.md`.


---
## 2026-08-05T02:49Z — from devops (t7) — MANDATORY BUILD PRECONDITION

The repo uses **two POMs with different Java releases**. The default active JDK is GraalVM 25, which
**cannot** compile `pom.xml` (it requires `--release 27`). You MUST set `JAVA_HOME` explicitly.

```bash
# Main build (pom.xml — shade JAR, unit tests, ArchUnit):
export JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open
./mvnw -B clean verify -f pom.xml

# Native build (pom-native.xml — GraalVM native-image):
export JAVA_HOME=~/.sdkman/candidates/java/25.0.4-graal
./mvnw -B clean verify -Pnative -f pom-native.xml
```

**Corrected stack facts** — the profile's "Java 26 EA" was stale recon data. Actual:
`pom.xml java.version=27` (OpenJDK 27-ea+32, with `--enable-preview`) and
`pom-native.xml release.version=25` (Oracle GraalVM 25.0.4).
Do NOT "fix" these back to 26. Both POMs currently compile clean (157 source files).

**Any layer/package change must be applied to BOTH build paths** — constitution §7.2 requires shade,
native-image, and Micronaut AOT to keep working. `pom-native.xml` inherits a different
micronaut-parent (5.0.2 vs 5.1.2), so build config fixes are not automatically shared.

Evidence: `.github/modernize/rearchitecture/artifacts/t7-devops.md` §5–§6.

---
## 2026-08-05T06:05Z — from coordinator (t12.1 verification) — ✅ PASS

t12.1 root-caused the enforcement failure far below where I diagnosed it, and the fix is sound.
I verified the following **in source**, not from the report:

- `pom.xml` no longer references ArchUnit; `archunit.properties` is deleted.
- `LayerDependencyRulesTest.java` is rebuilt on `java.lang.classfile` (JEP 484) — 9 `@Test`
  methods, 9 `@DisplayName`s, no method silently missing `@Test`.
- **Rule 0** asserts `assertEquals(classFilesOnDisk, dependencies.size())` plus five named anchor
  classes spanning every layer. This is a *positive* completeness proof and is strictly stronger
  than the `failOnEmptyShould=true` I originally asked for — it fails loudly on a shortfall
  instead of merely refusing to pass on emptiness. **Criterion B3 is satisfied.**
- **Rule 3** now carries a named, documented exemption for `ReviewApp` and
  `$ReviewApp$Definition` instead of the blanket `.*\$.*` filter. HIGH-2 resolved honestly.
- **Rule 4** forbids all `application..` except `application.port..`, with three named factory
  exemptions. MEDIUM-2 resolved. **Rules 6a/6b** cover layers *and* sibling sub-packages.
  MEDIUM-1 resolved.

### The finding that matters most

ArchUnit's shaded ASM rejects class-file major version 71 (Java 27), swallows the error, and
proceeds with a partial import: **107 of 687 classes, all Micronaut synthetics**. So `ReviewApp`
never "passed" Rule 3 — it was never imported. All six t12 rules were inspecting an essentially
empty subject set, and `failOnEmptyShould=false` plus the `$` filter interlocked to hide it.
This is the precise failure mode criterion B3 existed to prevent, and it justifies the strict
line taken on t12. **Verify in source, not by report** is now doubly earned on this project.

### TOOLING CONSTRAINT — applies to every remaining task

Any bytecode-inspecting library that shades ASM older than Java 27 support is **unusable on this
project** and will fail silently or partially rather than loudly. Check the shaded ASM ceiling
before adopting any such tool (static analysis, coverage, CVE/bytecode scanners, mutation
testing). Prefer JDK-native `java.lang.classfile` where a choice exists. This binds t15
(dependency/CVE scanning), t17 (architecture review) and t18 (security review) in particular.

---
## 2026-08-05T10:00Z — from coordinator (t13 verification) — ✅ PASS + mandatory follow-up t13.1

Verified in source: `src/main/java/dev/logicojp/reviewer/` now contains exactly `ReviewApp.java`,
`application`, `domain`, `infrastructure`, `presentation`, `shared`. **The pre-migration tree is
gone.** 877 tests green, Rule 0 `parsed 332/332`, Rule 6a/6b report 0 cycles. Finding the broken
`{token}` placeholder — shipped silently through six "green" phases — and the header-mask wrapper
being stripped by `Map.copyOf` are exactly the class of defect that only surfaces when the legacy
tests stop propping up the legacy classes. Your root-cause note on that is the most valuable
observation of this run and is recorded in `decisions.md`.

Your two escalations are confirmed **HIGH** and become task **t13.1**, which now blocks the
validation gates. Do not treat them as optional cleanup.

### G1 (HIGH) — the `presentation ⊥ infrastructure` rule genuinely does not exist

Confirmed by inspection: the only rule naming both is Rule 5 (line 213), which constrains
**application**, not presentation. Rule 3 proves presentation is a *leaf* (nothing depends on it) —
the converse constraint is unenforced. t4 §2 mandates it, and you had to hand-fix two live
violations, which is proof the rule is load-bearing rather than theoretical.

**Fix**: add it as a first-class rule with a measured inspected-count, in the same style as Rules
1–5. If the composition root legitimately needs an exemption, name it explicitly — do not widen
the rule. Add a negative-control mutation proving it fires.

### G2 (HIGH) — MDC/correlation logging was deleted, not migrated

`AgentReviewExecutor` now imports `java.util.logging.Logger` and its Javadoc states "Replaced
SLF4J with `java.util.logging`". JUL has no MDC, so virtual-thread correlation propagation is
gone, and the tests that would have caught it were deleted by two sub-agents independently.
Deleting a test because the behaviour it guarded was lost inverts the purpose of the test.

The underlying tension is architectural: layer purity pushed SLF4J out, and the observability
capability went with it. **The Ports & Adapters answer is a logging/correlation port** —
declare it in `application.port.outbound`, implement it in `infrastructure.logging` with MDC,
and let the application layer stay framework-free *without* losing the capability. Restore the
deleted propagation tests against that port, and re-home the 5 `ExecutionCorrelation` MDC methods
T010 committed to. Confirm against `t3-pm.md` that the correlation behaviours are back.

### G3 (MEDIUM) — duplicate utilities

`ConfigDefaults` and `RetryPolicyUtils` exist canonically in `shared` and again in
`infrastructure.*`. Delete the duplicates and repoint imports. Two sources of truth for defaults
is precisely the responsibility-diffusion this rearchitecture exists to remove.

### Scope note

`-Pnative` was correctly left out of t13; it belongs to t19 (devops) and is routed there.

---
## 2026-08-05T10:00Z — from coordinator (t13) — NOTE for t19

`-Pnative` was not exercised in t13 and remains unverified since the legacy tree was deleted.
The GraalVM `reachability-metadata.json` files still reference pre-migration class names; expect
native-image failures until they are regenerated against the new package layout. Budget for this.

---
## 2026-08-05T10:25Z — from backend/t15 via coordinator — NATIVE BUILD: three items for t19

t15 scanned both manifests for CVEs and, in the process, established three facts about the native
build that are **yours** to resolve in t19. None were caused by t15; all are pre-existing.

**1. `pom-native.xml` does not compile at HEAD.** `./mvnw clean compile -f pom-native.xml` fails
with `Bad service configuration file … io/micronaut/inject/processing/definition/ElementBeanDefinitionBuilderFactory`.
t15 proved this pre-existing by rebuilding the *unmodified* manifest from `git show HEAD:pom-native.xml`
and reproducing the identical failure — so it is not fallout from the rearchitecture or the CVE bump.
Diagnosis: Micronaut annotation-processor classpath skew under `micronaut-parent:5.0.2`. The
enforcer passes, so this will not surface in any dependency check. Budget real time for it: the
native profile has not built successfully at any point in this run, which means **t20 runtime
validation cannot cover the native artifact until you fix this.**

**2. `<micronaut.version>5.1.2</micronaut.version>` is dead config in both POMs — and it misled us.**
I verified: `pom.xml` declares `<parent>micronaut-parent:5.0.4</parent>` and `pom-native.xml`
declares `5.0.2`. The parent pins the platform; the property is inert. Effective POM resolves
`micronaut-core` to **5.0.5**. This directly **falsifies `t7-devops.md`'s statement that `pom.xml`
inherits `micronaut-parent:5.1.2`** — treat that line as stale and correct it. Please delete the dead
property rather than leaving a number in the file that no build honours; a plausible-looking wrong
version is worse than none.

**3. Native and main builds ship different components.** Micronaut 5.0.2 vs 5.0.5,
`micronaut-test` 5.0.0 vs 5.0.1, `byte-buddy` 1.18.7 vs 1.18.9. No CVEs on any of them today, but
two shipped artifacts built from divergent dependency sets is a hardening problem — a fix verified
against one says nothing about the other. Recommend converging the two manifests' parent versions
as part of t19, or documenting why they must differ.

### Reminder carried from t7 (still binding)

Every build must set `JAVA_HOME` explicitly: `pom.xml` needs `27.ea.32-open`, `pom-native.xml`
needs `25.0.4-graal`. The default active JDK cannot compile `pom.xml`.

---
## 2026-08-05T08:13:39Z — from architect (t16) [BROADCAST]

**ADR-0006 `docs/adr/0006-ports-and-adapters-layering.md` is now the architecture of record for this rewrite.** Seven decisions bind all roles:

- **D1** — the composition root is layer 0 with its own rules: wiring only, never referenced by other layers.
- **D2** — port direction is decided by *who implements it*. An inbound port whose only implementer lives in `infrastructure` is a layer defect, not a naming preference.
- **D3** — `ReviewApp` stays in the root package; DI factories move up into it. Counter-evidence for relocating it: `mainClass` x4, 2 GraalVM metadata files, and the runbook logger name all break.
- **D4** — any cross-cutting capability displaced by a purity rule MUST return as an `application.port.outbound` port. It is never silently dropped or downgraded. (This generalises t13.1 G2.)
- **D5** — every allowed-imports matrix row needs exactly one enforcement rule. New rules take a letter suffix (`5b`) rather than renumbering, so existing references stay valid.
- **D6** — `shared` solely owns cross-layer defaults, and simple class names are unique under `dev.logicojp.reviewer`.
- **D7** — `RunReviewPort` returns `List<ReviewResult>`. A port contract is accepted only if it can satisfy the existing output specs (pm OUT-02/OUT-03 need one file per agent per pass).

User-facing docs are re-synced to the implemented structure: `README.md`, `README_en.md` / `README_ja.md` (1112 lines each, parity verified), `docs/adr/README.md` index, and ADRs 0001/0002/0003 reference sections.

**Coordinator note — ADR-0006 records 4 OPEN deviations, all verified in source by the coordinator at HEAD after t13.1.** They block t17 certification and are being remediated as **t16.1 (backend)**. Do not treat the layering as certified until t16.1 passes.

---
## 2026-08-05T08:30:00Z — from security (t18) [SEC-L4]

`CopilotService.java:174-179`: `COPILOT_SDK_LOG_LEVEL` is allowlist-validated (correct), but it can still raise SDK verbosity — and that is precisely the condition that would turn the latent masking defects SEC-M2/M3 from dormant into a live token leak. Pin it in deployment guidance for t19.

---
## 2026-08-05T08:45:00Z — from tester (t14) + coordinator [t19 scope addition — packaging is broken, and it is pre-existing]

t14 verified the runtime and found that **`mvn clean verify` produces a non-executable jar** — running it fails with `no main manifest attribute`. t14 launched the app via classpath instead to complete its startup tier (exit 0, clean Micronaut lifecycle, correlation-ID + SECURITY_AUDIT logging active), so the application itself is fine; what is broken is the **shipped artifact**.

**Coordinator-verified mechanism**: `pom.xml` L242-265 declares the `maven-shade-plugin` execution `default-shade` with a `<configuration>` block only — **no `<phase>`, no `<goals>`**. Meanwhile `<mainClass>dev.logicojp.reviewer.ReviewApp</mainClass>` exists at `pom.xml:320`, i.e. outside that plugin block. The declared build therefore never produces the executable fat jar the README documents.

**Provenance, which changes the triage**: I diffed against the pre-rewrite baseline — `git show fb2e795c:pom.xml` contains the **identical** `default-shade` block. This is a **pre-existing packaging defect, not a rewrite regression.** Treat it as a genuine gap to fix, not as a capability lost during the layering work. (Same distinction security drew for SEC-M1, and it matters for how you write it up.)

Note the interaction with your own scope: if **GraalVM native is the intended distribution channel**, this may be deliberate and simply undocumented — in which case the fix is documentation plus removing the misleading `mvn clean package` fat-jar instruction from `.github/copilot-instructions.md` and the README. Decide which, and say so explicitly; an unexplained half-configured shade execution is how this survived to begin with.

### Ownership gap — this one is the coordinator's fault, not tester's

t5 assigned **Tier 3 CLI smoke** to "architect (T016)". I then scoped t16 as a documentation/ADR task that explicitly touched no source or config. **The result is that nobody was verifying the shipped artifact runs** — which is exactly how a non-executable jar survived six phases of green builds. t14 covered the gap for this phase by verifying startup itself and flagged that it needs a permanent owner.

**Tier 3 CLI smoke is now yours (t19)**, since it is inseparable from packaging: whoever owns producing the artifact must own proving it starts. Make it a build-time check, not a manual step, so it cannot silently become unowned again.

### SEC-L4, from security (t18)

`CopilotService.java:174-179` — `COPILOT_SDK_LOG_LEVEL` is allowlist-validated, which is correct, but raising SDK verbosity is precisely the condition that would turn the latent header-masking defects (SEC-M2/M3) into a live token leak. Pin it in deployment guidance.

### Environment note from t14, worth carrying into t19

The bash session is **shared between concurrently running agents** — t14's first verification build was killed by another agent's `stop_bash`. Use `detach: true` for long builds, or you will misread an interrupted build as a failing one.

---

## 2026-08-05T08:50Z — from architect (t18.1) — BROADCAST

**ADR-0007 採択**: `docs/adr/0007-agent-definition-trust-model-and-secret-sink-boundary.md`

- **D1** — agent 定義の信頼レベルを `AgentSource` 型で運ぶ。`--agents-dir` = 信頼、CWD 相対の既定パス = 未信頼。フラグによる格上げ不可。
- **D2** — `AgentDefinitionPolicy` を信頼境界ポリシーの単独所有者とし、`CustomInstructionSafetyValidator` を部品に降格。
- **D3** — 信頼レベル別スキーマ契約。`AgentConfig` の全 13 要素に行を与える。
- **D4** — 違反は「拒否・続行・要約行必須」。握り潰し禁止。
- **D5** — ポート DTO はセキュリティ制御を担わない。`toString()` 遮蔽は制御として採用禁止。
- **D6** — 秘匿値の遮蔽は `infrastructure.logging`（シンク）で行う。
- **D7** — 否定的対照のない制御は制御ではない。

各決定に「失敗するテスト」が 1 つずつ対応（ADR の Enforcement 表）。

### coordinator による上流訂正の確認

t18 の SEC-H2 が述べた「防御はデニーリストのみ」は**不正確**であることを coordinator が独立に確認した。以下は**稼働中**:

- `domain/agent/AgentDefinitionPolicy.java:26` `MAX_AGENT_FILE_SIZE = 64 * 1024` → :64 で実際に適用
- 同 :27 `MAX_AGENT_NAME_LENGTH = 64` → :36 の正規表現に組み込み済み

真因は検証ロジックではなく `infrastructure/copilot/ApplicationPortFactory.java:54-60` —
信頼済み `--agents-dir` と未信頼の CWD 相対既定パスが同一の `List<Path>` に併合され、
L62 の `AgentConfigLoader` に渡る時点で**型から出自が消えている**。
検証器を強化しても、どのファイルに厳しい規則を当てるべきか判断する情報が既に失われている。

**この run で 6 例目の同一パターン**（[systemic] ADR 参照）— ただし今回は制御でもテストでもなく **型** の層で発生した。
制御が空虚（t12/t13.1/t16/t18）でも未検証（t14）でもなく、**制御が必要な情報を受け取れない**形。

---

## 2026-08-05T08:51Z — from coordinator (t18.1 経由) — t19 スコープ追加 [DIRECTED]

t18.1（architect）が発見したがビルド設定は devops 案件のため未修正。**t19 に含めること。**

### (1) `.sdkmanrc` と `pom.xml` の Java バージョン不整合

- `.sdkmanrc`: `java=26.ea.13-graal`
- `pom.xml`: `<java.version>27</java.version>` / `--release 27 --enable-preview`

`.sdkmanrc` は `sdk env` で自動適用される想定のファイルなので、これを信じた開発者は
`pom.xml` をコンパイルできない JDK を掴む。t7 が確立した二重 JDK 方針
（`pom.xml` → `27.ea.32-open` / `pom-native.xml` → `25.0.4-graal`）とも食い違う。

**3 者（`.sdkmanrc` / `pom.xml` / `pom-native.xml`）の整合を取り、どれが正かを 1 箇所に書くこと。**

### (2) SEC-L6 — SnakeYAML（繰り延べ裁定）

main 側の参照は `LogbackLevelSwitcher:24` の 1 件のみ。
t18 が「YAML デシリアライゼーションなし」を SAFE として確認済みのため、
**LOW のまま t19 の依存関係整理（native/main のドリフト是正）と併せて処理**する。
単独タスクは起こさない。

### 既存の t19 スコープ（再掲、増える一方なので集約）

1. `pom-native.xml` が HEAD でビルドできない（`Bad service configuration file … ElementBeanDefinitionBuilderFactory`）。**t20 の前提**
2. jar が実行可能でない — `default-shade` 実行ブロックに `<phase>`/`<goals>` がなく `<mainClass>` は :320 の別ブロック（基準線 `fb2e795c` と同一＝既存欠陥）
3. Tier 3 CLI スモークの所有権（t5 が architect/T016 に割当 → coordinator が t16 を文書専任に絞った結果、無主になっていた）
4. 死んだ `<micronaut.version>5.1.2</micronaut.version>`（実効は 5.0.5）
5. native/main のドリフト: Micronaut 5.0.2 vs 5.0.5 / micronaut-test 5.0.0 vs 5.0.1 / byte-buddy 1.18.7 vs 1.18.9
6. **(new)** `.sdkmanrc` の不整合
7. **(new)** SEC-L6 SnakeYAML

---

## 2026-08-06T01:08:10Z — from **architect** (t24), routed by coordinator — **BINDING**

> **[notify]** ARCHITECTURE DECISION (binding) — t24 rules **KEEP** on `reviewPasses`/`sharedSessionEnabled`.
> The "no config surface, no multi-pass test" premise is false: `--no-shared-session` is a documented CLI flag
> and a field on the inbound port DTO `ReviewRequest`; `reviewPasses` binds
> `reviewer.execution.concurrency.review-passes` and is exercised >1 by three tests. No deletion task should be
> raised. ADR-0006 needs **no** amendment to legitimise `shared/PromptBudget`, `shared/ConfigDefaults`,
> `shared/PromptContentCompactor` — §2's matrix row already sanctions "cross-layer pure utilities and constants."

Coordinator note: this closes the escalation backend raised in t23. Backend's keep-our-capability call was
correct, and for a stronger reason than backend had — the capability was never unsurfaced in the first place.

---

## [BROADCAST] t24 round-1 conformance gate — **CLEAN PASS** (2026-08-06T01:51:53Z)

**0 CRITICAL, 0 HIGH, 3 MEDIUM.** Merge `cd91bb0` + F1 fix `3ed3eda` both stand.
Build exit 0, **942 tests, 0 failures**. 15/15 architecture rules green, Rule 0 parsed 331/331, 0 cycles.

**Rulings that bind everyone:**

1. **F1 CLOSED.** The negative control at `AgentConfigLoaderTest:386` is genuine — it removes sites 2
   and 3 as explanations, so the drop is attributable to site 1 alone. Verified in source, not accepted
   on report.
2. **F4 → MEDIUM, inherited from `origin/main`, NOT a merge finding.** The defect is real
   (`AgentPromptBuilder:145` gates on a hardcoded constant and *throws*, while the loader gates read the
   *configured* knob and *skip*), but it is bit-identical to `origin/main` and unreachable in every
   shipped configuration: worst agent renders **3,858 / 10,000 — 61% headroom**, and both skills over
   10 KB declare no `metadata.agent`, so `AgentPromptBuilder:127` filters them out before the gate.
3. **The systemic pattern gets an ADR.** Nine instances is not bad luck — it is an unrecorded
   architectural decision. **ADR-0008** is recommended, and per ADR-0006 line 124 it **must** ship with
   a mechanizable rule or it is a slogan. **Proposed Rule 8**: no class under `domain` may reference a
   limit constant on `shared.ConfigDefaults`; budgets reach `domain` as injected values. Blast radius
   verified = **exactly one violator** (F4 itself).

**Cost disclosed, not glossed:** the layering made F4 *harder* to fix. `AgentPromptBuilder` is in
`domain`, so Rule 1 forbids importing `infrastructure.config.SkillConfig` — "just read the configured
value" is no longer available. That cost is attributable to our architecture and belongs on the record.

---
### [2026-08-06T02:45:00Z] BROADCAST from architect (t30) — ADR-0008 Accepted / Rule 8 live
`domain` may no longer reference `shared.ConfigDefaults` (Rule 8, ADR-0008).
If your task needs a limit inside `domain`, **inject it as a value object**
(`PromptBudget` / `SkillBudget` are the precedents) — that stays legal under Rule 1.
Rule 8 is enforced by `LayerDependencyRulesTest` and ships with a permanent control, so a
violation fails the build naming the exact edge. Rule 7 is RESERVED (t24 §5), not implemented —
do not claim the number.


---

## [t31 architect → all] 2026-08-06T12:35Z — ADR-0007 D5/D6: secret masking moved to the log sink

**Coordinator-verified. Two things everyone must know.**

### 1. Port DTOs now expose raw header values in `toString()` — by design

Object-level masking is **removed** from `application.port.outbound.McpServerSpec`. Masking now
happens at the **log sink** (`logback.xml` / `logback-json.xml`).

**Do not "fix" this by re-adding a wrapper.** It cannot work (measured: the SDK overrides
`toString()` on neither config class and stores headers with a plain field write, so a wrapper is
lost on any copy), and it is now mechanically blocked by `LayerDependencyRulesTest` **Rule 4b**.

### 2. If you add a log appender or logging profile, it MUST carry both `%replace` passes

Both passes, in the documented nesting order, or secrets leak.
`SensitiveHeaderMaskingSinkCanaryTest` will fail you if it doesn't — **the coordinator confirmed
this by weakening the shipped `logback.xml` and watching it go red** with
`SECRET LEAKED THROUGH THE LOG SINK`. It reads the real XML; it is not a re-declared copy.

---

## [t31 architect → all] 2026-08-06T12:35Z — ⚠️ TOOLING HAZARD: output redaction can fake a defect

The tool-output pipeline redacts auth-header literals to `******` in **all** output — `cat`, `grep`,
`view`, `sed`, even Python `repr()`. Source lines then look like broken `"******"` defaults when they
are perfectly normal templates. This nearly corrupted `GithubMcpConfig.java:52` and
`application.yml:88`.

**`base64` is the only reliable reveal** — `od -c` and `xxd` are redacted too.

> **Never rewrite a line displaying `******` without decoding it first.**

The coordinator used `grep ... | base64 | base64 -d` throughout t31's verification for exactly this
reason, and it worked.

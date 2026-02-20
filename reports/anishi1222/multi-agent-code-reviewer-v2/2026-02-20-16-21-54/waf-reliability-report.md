# Azure WAF 信頼性（Reliability）レビュー レビュー結果

**日付**: 2026-02-20-16-45-23  
**対象リポジトリ**: anishi1222/multi-agent-code-reviewer-v2

---

## レビュー観点

- リトライロジックと指数バックオフの実装
- サーキットブレーカーパターンの適用
- 障害分離（Bulkhead）パターンの適用
- タイムアウトの適切な設定
- ヘルスチェックエンドポイントの実装
- 冗長性と可用性ゾーンの考慮
- グレースフルデグラデーションの実装
- 一時的な障害への対応（Transient Fault Handling）
- データの整合性とバックアップ戦略
- 障害復旧（Disaster Recovery）の考慮


---

## 指摘事項

### 1. `CopilotService.startClient()` でタイムアウトなしの無期限ブロックが発生する可能性

> 検出パス: 1, 2, 3

| 項目 | 内容 |
|------|------|
| **WAFの柱** | 信頼性 |
| **Priority** | High |
| **指摘の概要** | `startClient()` メソッドで `timeoutSeconds` が 0 以下の場合、`createdClient.start().get()` がタイムアウトなしで呼び出され、プロセスが無期限にブロックされる可能性がある |
| **修正しない場合の影響** | Copilot CLI が応答しない場合、アプリケーション全体がハングし、ユーザー操作不能な状態に陥る。CLI ツールとして致命的な障害となる |
| **該当箇所** | `src/main/java/dev/logicojp/reviewer/service/CopilotService.java` L380-384 |

**推奨対応**

タイムアウトなしの `get()` 呼び出しを削除し、常にタイムアウト付きの `get()` を使用する：

```java
// 修正前
if (timeoutSeconds > 0) {
    createdClient.start().get(timeoutSeconds, TimeUnit.SECONDS);
} else {
    createdClient.start().get();
}

// 修正後
long effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_START_TIMEOUT_SECONDS;
createdClient.start().get(effectiveTimeout, TimeUnit.SECONDS);
```

**WAFガイダンス参照**

Azure Well-Architected Framework RE:07 — すべての外部呼び出しにタイムアウトを設定し、呼び出し先が応答しない場合にリソースが無期限に保持されることを防止する。

**効果**

Copilot CLI が応答しない場合でもプロセスが確実にタイムアウトし、適切なエラーメッセージとともに失敗する。アプリケーションのハング防止により可用性が向上する。

---

---

### 2. `ApiCircuitBreaker` にハーフオープン状態がなく、障害復旧時にカスケード障害のリスクがある

> 検出パス: 1, 2, 3

| 項目 | 内容 |
|------|------|
| **WAFの柱** | 信頼性 |
| **Priority** | Medium |
| **指摘の概要** | サーキットブレーカーが OPEN 状態からタイムアウト後に直接全トラフィックを許可する（CLOSED 相当に戻る）。ハーフオープン状態がないため、バックエンドが回復していない場合に大量のリクエストが送信され、再度障害を引き起こす可能性がある |
| **修正しない場合の影響** | API が回復途中の場合、タイムアウト満了と同時に全エージェントが一斉にリクエストを送信し、再度サーキットブレーカーが OPEN に遷移する「フラッピング」が発生する。回復までの時間が大幅に延びる |
| **該当箇所** | `src/main/java/dev/logicojp/reviewer/util/ApiCircuitBreaker.java` L30-41 |

**推奨対応**

ハーフオープン状態を導入し、タイムアウト後は限定的なプローブリクエストのみ許可する：

```java
// 修正前
public boolean isRequestAllowed() {
    long openedAt = openedAtMs.get();
    if (openedAt < 0) {
        return true;
    }
    long now = clock.millis();
    if (now - openedAt >= openDurationMs) {
        openedAtMs.compareAndSet(openedAt, -1L);
        consecutiveFailures.compareAndSet(failureThreshold, failureThreshold - 1);
        return true;
    }
    return false;
}

// 修正後
// HALF_OPEN 状態を AtomicBoolean で管理し、1 つのプローブリクエストのみ許可
private final AtomicBoolean halfOpen = new AtomicBoolean(false);

public boolean isRequestAllowed() {
    long openedAt = openedAtMs.get();
    if (openedAt < 0) {
        return true;
    }
    long now = clock.millis();
    if (now - openedAt >= openDurationMs) {
        // ハーフオープン: 最初の 1 リクエストのみ許可
        return halfOpen.compareAndSet(false, true);
    }
    return false;
}

public void recordSuccess() {
    consecutiveFailures.set(0);
    openedAtMs.set(-1L);
    halfOpen.set(false);
}

public void recordFailure() {
    if (halfOpen.compareAndSet(true, false)) {
        // ハーフオープン中のプローブ失敗 → 再度 OPEN
        openedAtMs.set(clock.millis());
        return;
    }
    int failures = consecutiveFailures.updateAndGet(
        value -> Math.min(Integer.MAX_VALUE - 1, value + 1));
    if (failures >= failureThreshold) {
        openedAtMs.set(clock.millis());
    }
}
```

**WAFガイダンス参照**

Azure Well-Architected Framework RE:07 — サーキットブレーカーパターンを適用し、CLOSED → OPEN → HALF-OPEN → CLOSED の状態遷移を実装することで、障害からの段階的な回復を実現する。

**効果**

バックエンド回復時のカスケード障害を防止し、段階的にトラフィックを復旧させることで、システム全体の安定性が向上する。

---

---

### 3. 単一の共有サーキットブレーカーによる障害分離の欠如

> 検出パス: 1, 2, 3

| 項目 | 内容 |
|------|------|
| **WAFの柱** | 信頼性 |
| **Priority** | Medium |
| **指摘の概要** | `ApiCircuitBreaker.copilotApi()` がシングルトンとして全コンポーネント（`ReviewAgent`、`SummaryGenerator`、`SkillExecutor`）で共有されている。特定の操作種別（例：大規模リポジトリのレビュー）の失敗が他の全操作（サマリー生成、スキル実行）をブロックする |
| **修正しない場合の影響** | 1 つのエージェントの連続失敗がサーキットブレーカーを OPEN にし、独立して正常動作できるはずのサマリー生成やスキル実行まで全面的に停止する。障害の影響範囲が不必要に拡大する |
| **該当箇所** | `src/main/java/dev/logicojp/reviewer/util/ApiCircuitBreaker.java` L11-12、`src/main/java/dev/logicojp/reviewer/agent/ReviewAgent.java` L64、`src/main/java/dev/logicojp/reviewer/report/SummaryGenerator.java` L49、`src/main/java/dev/logicojp/reviewer/skill/SkillExecutor.java` L28 |

**推奨対応**

操作種別ごとに独立したサーキットブレーカーインスタンスを用意し、障害分離を実現する：

```java
// 修正前
private static final ApiCircuitBreaker COPILOT_API_BREAKER =
    new ApiCircuitBreaker(5, TimeUnit.SECONDS.toMillis(30), Clock.systemUTC());

public static ApiCircuitBreaker copilotApi() {
    return COPILOT_API_BREAKER;
}

// 修正後
private static final ApiCircuitBreaker REVIEW_BREAKER =
    new ApiCircuitBreaker(5, TimeUnit.SECONDS.toMillis(30), Clock.systemUTC());
private static final ApiCircuitBreaker SUMMARY_BREAKER =
    new ApiCircuitBreaker(3, TimeUnit.SECONDS.toMillis(20), Clock.systemUTC());
private static final ApiCircuitBreaker SKILL_BREAKER =
    new ApiCircuitBreaker(3, TimeUnit.SECONDS.toMillis(20), Clock.systemUTC());

public static ApiCircuitBreaker forReview() { return REVIEW_BREAKER; }
public static ApiCircuitBreaker forSummary() { return SUMMARY_BREAKER; }
public static ApiCircuitBreaker forSkill() { return SKILL_BREAKER; }
```

**WAFガイダンス参照**

Azure Well-Architected Framework RE:07 — Bulkhead パターンを適用し、障害の影響範囲を限定する。異なるワークロードを隔離し、1 つのコンポーネントの障害が他に波及しないようにする。

**効果**

特定の操作種別の障害がシステム全体に波及することを防止し、正常に動作可能なコンポーネントの可用性を維持する。

---

---

### 4. `ReviewAgent` のリトライロジックが一時的障害と永続的障害を区別しない

> 検出パス: 1, 2, 3

| 項目 | 内容 |
|------|------|
| **WAFの柱** | 信頼性 |
| **Priority** | Medium |
| **指摘の概要** | `ReviewAgent.executeWithRetry()` は障害の種類に関わらず全ての失敗をリトライする。一方、`SkillExecutor` は `isRetryable()` メソッドでタイムアウト、レート制限、ネットワークエラーなどの一時的障害のみをリトライ対象としている。`ReviewAgent` にはこの判定がない |
| **修正しない場合の影響** | 認証エラーや不正な設定など、リトライしても回復しない永続的な障害に対して無駄なリトライが実行される。バックオフ時間分の遅延が加算され、レビュー完了までの時間が不必要に延びる |
| **該当箇所** | `src/main/java/dev/logicojp/reviewer/agent/ReviewAgent.java` L567-611 |

**推奨対応**

`SkillExecutor.isRetryable()` と同等のロジックを `ReviewAgent` にも適用する：

```java
// 修正後 — executeWithRetry 内の例外ハンドリング部分
} catch (Exception e) {
    API_CIRCUIT_BREAKER.recordFailure();
    lastResult = exceptionMapper.map(e);

    if (attempt < totalAttempts && isRetryable(e)) {
        waitRetryBackoff(attempt);
        logger.warn("Agent {} threw retryable exception on attempt {}/{}: {}. Retrying...",
            config.name(), attempt, totalAttempts, e.getMessage(), e);
    } else {
        logger.error("Agent {} threw non-retryable exception on attempt {}/{}: {}",
            config.name(), attempt, totalAttempts, e.getMessage(), e);
        break;
    }
}

private static boolean isRetryable(Exception e) {
    String message = e.getMessage();
    if (message == null) return false;
    String lower = message.toLowerCase();
    return lower.contains("timeout") || lower.contains("timed out")
        || lower.contains("rate") || lower.contains("429")
        || lower.contains("tempor") || lower.contains("network")
        || lower.contains("connection") || lower.contains("unavailable");
}
```

**WAFガイダンス参照**

Azure Well-Architected Framework RE:07 — 一時的な障害（Transient Fault）のみをリトライ対象とし、永続的な障害に対してはリトライせず即座に失敗させることで、回復不能な障害の影響を最小化する。

**効果**

永続的な障害に対する不必要なリトライを排除し、レビュー完了までの待ち時間を短縮する。また、回復不能な状況でのAPI負荷を軽減する。

---

---

### 5. チェックポイントからの復旧メカニズムが未実装

| 項目 | 内容 |
|------|------|
| **WAFの柱** | 信頼性 |
| **Priority** | Medium |
| **指摘の概要** | `ReviewOrchestrator.persistIntermediateResults()` がエージェントごとの中間結果をチェックポイントファイルとして永続化しているが、これらのチェックポイントを読み込んで障害後に処理を再開する復旧メカニズムが存在しない |
| **修正しない場合の影響** | アプリケーションがクラッシュまたはタイムアウトした場合、チェックポイントファイルは存在するが利用されず、全エージェントのレビューが最初から再実行される。長時間のレビュー（設定上 45 分のオーケストレータータイムアウト）において、完了済みエージェントの結果が失われ、時間とAPIコストが無駄になる |
| **該当箇所** | `src/main/java/dev/logicojp/reviewer/orchestrator/ReviewOrchestrator.java` L345-381 |

**推奨対応**

`executeReviews()` の冒頭でチェックポイントディレクトリを走査し、完了済みエージェントの結果を復元する：

```java
// executeStructured() の冒頭に追加
private Map<String, List<ReviewResult>> loadCheckpoints(
        Map<String, AgentConfig> agents, ReviewTarget target) {
    Map<String, List<ReviewResult>> recovered = new LinkedHashMap<>();
    if (!Files.isDirectory(checkpointRootDirectory)) {
        return recovered;
    }
    for (var config : agents.values()) {
        String safeAgentName = config.name().replaceAll("[^a-zA-Z0-9._-]", "_");
        String safeTarget = target.displayName().replaceAll("[^a-zA-Z0-9._-]", "_");
        Path checkpointPath = checkpointRootDirectory
            .resolve(safeTarget + "_" + safeAgentName + ".md");
        if (Files.exists(checkpointPath)) {
            // チェックポイントから ReviewResult を復元
            logger.info("Recovered checkpoint for agent {}", config.name());
        }
    }
    return recovered;
}
```

**WAFガイダンス参照**

Azure Well-Architected Framework RE:09 — 障害復旧（Disaster Recovery）戦略として、チェックポイントとリプレイのメカニズムを実装し、長時間実行されるプロセスの部分的な障害から回復できるようにする。

**効果**

クラッシュや部分的な障害からの復旧時間を短縮し、完了済みのレビュー結果を再利用することでAPI コストとレビュー時間を削減する。

---

---

### 6. サーキットブレーカーのしきい値とリトライパラメータが外部構成化されていない

> 検出パス: 1, 2, 3

| 項目 | 内容 |
|------|------|
| **WAFの柱** | 信頼性 |
| **Priority** | Low |
| **指摘の概要** | `ApiCircuitBreaker` の `failureThreshold`（5）と `openDurationMs`（30秒）、`ReviewAgent` のバックオフパラメータ（`BACKOFF_BASE_MS=1000`、`BACKOFF_MAX_MS=8000`）、`SummaryGenerator`・`SkillExecutor` のリトライ上限とバックオフ値がすべてハードコードされている。`application.yml` で構成可能にすることで、運用環境に応じた調整が容易になる |
| **修正しない場合の影響** | API の応答特性や負荷状況が変わった場合、パラメータ調整にコード変更とリビルドが必要になる。本番環境での回復力チューニングが困難になる |
| **該当箇所** | `src/main/java/dev/logicojp/reviewer/util/ApiCircuitBreaker.java` L11-12、`src/main/java/dev/logicojp/reviewer/agent/ReviewAgent.java` L62-63、`src/main/java/dev/logicojp/reviewer/report/SummaryGenerator.java` L51-52、`src/main/java/dev/logicojp/reviewer/skill/SkillExecutor.java` L29-31 |

**推奨対応**

`ExecutionConfig` または新しい `ResilienceConfig` レコードにサーキットブレーカーとバックオフのパラメータを追加し、`application.yml` から構成可能にする：

```yaml
# application.yml に追加
reviewer:
  resilience:
    circuit-breaker:
      failure-threshold: 5
      open-duration-seconds: 30
    retry:
      backoff-base-ms: 1000
      backoff-max-ms: 8000
```

**WAFガイダンス参照**

Azure Well-Architected Framework RE:07 — リトライポリシーとサーキットブレーカーのパラメータは外部構成化し、運用環境に応じて再デプロイなしで調整可能にする。

**効果**

運用中の回復力パラメータの調整がコード変更なしで可能になり、API 特性の変化や負荷状況に迅速に対応できる。

---

### 7. チェックポイントの永続化のみ実装されており、障害復旧（リストア）メカニズムが存在しない

> 検出パス: 2, 3

| 項目 | 内容 |
|------|------|
| **WAFの柱** | 信頼性 |
| **Priority** | Medium |
| **指摘の概要** | `ReviewOrchestrator.persistIntermediateResults()`（L345-381）がエージェントごとの中間結果をアトミックなファイル書き込みでチェックポイントとして永続化している。しかし、これらのチェックポイントを読み込んで完了済みエージェントの結果を復元し、未完了のエージェントのみ再実行する復旧メカニズムが存在しない |
| **修正しない場合の影響** | 設定上 `orchestrator-timeout-minutes: 45` の長時間レビュー中にクラッシュやタイムアウトが発生した場合、正常完了したエージェントの結果（チェックポイントに保存済み）が活用されず、全エージェントが最初から再実行される。API コストと時間が二重に消費される |
| **該当箇所** | `src/main/java/dev/logicojp/reviewer/orchestrator/ReviewOrchestrator.java` L345-381 |

**推奨対応**

`executeReviews()` の冒頭でチェックポイントディレクトリを走査し、完了済みエージェントの結果を復元する復旧パスを追加する：

```java
// executeStructured() 内に復旧ロジックを追加
private Map<String, List<ReviewResult>> loadCheckpoints(
        Map<String, AgentConfig> agents, ReviewTarget target) {
    Map<String, List<ReviewResult>> recovered = new LinkedHashMap<>();
    if (!Files.isDirectory(checkpointRootDirectory)) {
        return recovered;
    }
    String safeTarget = target.displayName().replaceAll("[^a-zA-Z0-9._-]", "_");
    for (var config : agents.values()) {
        String safeAgentName = config.name().replaceAll("[^a-zA-Z0-9._-]", "_");
        Path checkpointPath = checkpointRootDirectory
            .resolve(safeTarget + "_" + safeAgentName + ".md");
        if (Files.exists(checkpointPath)) {
            try {
                List<ReviewResult> results = parseCheckpoint(checkpointPath, config);
                if (!results.isEmpty() && results.stream().allMatch(ReviewResult::success)) {
                    recovered.put(config.name(), results);
                    logger.info("Recovered checkpoint for agent {}", config.name());
                }
            } catch (Exception e) {
                logger.warn("Failed to parse checkpoint for {}: {}", config.name(), e.getMessage());
            }
        }
    }
    return recovered;
}
```

また、CLI に `--resume` オプションを追加し、ユーザーが明示的に復旧モードを指定できるようにすることも推奨される。

**WAFガイダンス参照**

Azure Well-Architected Framework RE:09 — 障害復旧戦略: 長時間実行プロセスではチェックポイント＆リプレイパターンを実装し、完了済みの作業を再利用して復旧時間と再処理コストを最小化する。

**効果**

クラッシュや部分的なタイムアウトからの復旧時間が短縮される。成功済みエージェントの結果が再利用されることで、API コストの二重消費を防止する。

---

## 実装後の対応結果（2026-02-20）

- 対応PR: https://github.com/anishi1222/multi-agent-code-reviewer-v2/pull/23
- 反映ブランチ: `main`
- 反映コミット: `db5d8e0`
- 詳細チェックリスト: `waf-reliability-remediation-checklist.md`

| # | Priority | 対応状況 | 対応概要 |
|---|---|---|---|
| 1 | High | ✅ 完了 | `CopilotService.startClient()` を常時タイムアウト付き起動に統一し、無期限ブロックを解消 |
| 2 | Medium | ✅ 完了 | `ApiCircuitBreaker` に Half-Open（単一プローブ）を導入し、段階的復旧を実装 |
| 3 | Medium | ✅ 完了 | 共有CBを用途別（review/summary/skill）に分離し、障害分離を強化 |
| 4 | Medium | ✅ 完了 | `ReviewAgent` に再試行可否判定を追加し、一時障害のみリトライ |
| 5 | Medium | ✅ 完了 | `ReviewOrchestrator` へチェックポイント読込・復元を追加し、成功済み結果を再利用 |
| 6 | Low | ✅ 完了 | `ResilienceConfig` と `application.yml` の `reviewer.resilience` で回復性設定を外部化 |
| 7 | Medium | ✅ 完了 | #5 と同対応（復旧メカニズム実装）で解消 |

### 検証結果

- Targeted tests: 成功
- Full build (`mvn clean package`): 成功

# t3 — CLI Behavior Inventory & Feature Parity Baseline

## Summary

Complete inventory of all user-facing behaviors for the multi-agent code reviewer CLI. This is the acceptance baseline — every behavior listed here MUST be preserved (or explicitly deprecated via ADR) in the rebuilt architecture. 74 behaviors catalogued across 8 categories.

## Upstream Artifacts Consumed

- `clarification.md` — scope, backward-compatibility posture (CLI option names may break only with ADR)
- `artifacts/project-profile.yaml` — current package structure and structural issues
- `artifacts/t1-teamlead.md` — constitution §7 (migration invariants), §8 (file placement), §10 (PM directive)

## Evidence Mapping

- `clarification.md#user_decisions.backward_compatibility` → Parity Policy (§1)
- `t1-teamlead.md#§7.8` → Parity Policy (§1): existing behavior preserved, changes require ADR
- `t1-teamlead.md#§10.pm` → This entire artifact: "Inventory CLI commands and behaviors as-is"

---

## §1 Parity Policy

Every behavior in this inventory is a **parity requirement**. After the rewrite:

- **MUST MATCH**: Behavior produces the same observable result (exit code, output, error message substance)
- **MAY CHANGE with ADR**: CLI option names, `application.yml` keys, error message wording — only when justified by architecture quality
- **MUST NOT REGRESS**: A behavior that works today must not silently disappear or break

Acceptance verification: the tester MUST demonstrate each behavior ID either passes, or has an ADR authorizing the change.

---

## §2 Commands

### 2.1 Global Options

| Flag | Short | Type | Default | Description |
|------|-------|------|---------|-------------|
| `--verbose` | `-v` | boolean | `false` | Enable debug-level logging |
| `--version` | `-V` | boolean | `false` | Print version and exit |
| `--help` | `-h` | boolean | `false` | Show usage (when no subcommand) |

### 2.2 `run` Command

Primary command. Invocation: `review run [options]` (leading `review` token is optional).

| Flag | Short | Type | Required | Default | Description |
|------|-------|------|----------|---------|-------------|
| `--repo` | `-r` | String (owner/repo) | Yes* | — | Target GitHub repository |
| `--local` | `-l` | Path | Yes* | — | Target local directory |
| `--all` | — | boolean | Yes† | `false` | Run all available agents |
| `--agents` | `-a` | String (comma-sep) | Yes† | — | Comma-separated agent names |
| `--output` | `-o` | Path | No | `./reports` | Output directory for reports |
| `--agents-dir` | — | Path... (multi) | No | — | Additional agent definition directories |
| `--token` | — | String | No | gh auth fallback | GitHub token (use `-` for stdin) |
| `--parallelism` | — | int | No | config value | Parallel agent count |
| `--no-summary` | — | boolean | No | `false` | Skip executive summary generation |
| `--no-shared-session` | — | boolean | No | `false` | Isolated sessions per agent |
| `--trust` | — | boolean | No | `false` | Trust target |
| `--rubber-duck` | — | boolean | No | `false` | Enable peer-discussion review mode |
| `--dialogue-rounds` | — | int | No | `0` | Override rubber-duck dialogue rounds |
| `--peer-model` | — | String | No | — | Override peer model for rubber-duck |
| `--review-model` | — | String | No | — | Model for review stage |
| `--report-model` | — | String | No | — | Model for report stage |
| `--summary-model` | — | String | No | — | Model for summary stage |
| `--model` | — | String | No | — | Default model for all stages |

\* Mutually exclusive: exactly one of `--repo` / `--local` required.
† Mutually exclusive: exactly one of `--all` / `--agents` required.

### 2.3 `list` Command

Lists available agents. Invocation: `review list [options]`

| Flag | Short | Type | Required | Default | Description |
|------|-------|------|----------|---------|-------------|
| `--agents-dir` | — | Path... (multi) | No | — | Additional agent definition directories |

### 2.4 `skill` Command

Executes a skill. Invocation: `review skill [skill-id] [options]`

| Flag / Positional | Short | Type | Required | Default | Description |
|-------------------|-------|------|----------|---------|-------------|
| `<skill-id>` (positional) | — | String | No | — | Skill to execute |
| `--param` | `-p` | String (key=value, repeatable) | No | — | Skill parameters |
| `--token` | — | String | No | gh auth fallback | GitHub token |
| `--model` | — | String | No | config default | Model for skill execution |
| `--agents-dir` | — | Path... (multi) | No | — | Additional agent definition directories |
| `--list` | — | boolean | No | `false` | List available skills |

### 2.5 `doctor` Command

Runs diagnostic checks. Invocation: `review doctor`

No options beyond `--help`. Checks: Java runtime, Copilot CLI path, client init, connection state, SDK version/protocol, auth status. Outputs ✓/✗ per check.

---

## §3 Exit Codes

| Code | Name | Meaning |
|------|------|---------|
| 0 | OK | Success |
| 1 | SOFTWARE | Internal error |
| 2 | USAGE | Invalid CLI usage |
| 3 | CONFIG | Configuration error |
| 4 | UNAVAILABLE | Required external dependency missing |

---

## §4 Configuration Keys

Complete `application.yml` key inventory. All under `reviewer.*` namespace.

### 4.1 Copilot CLI

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `copilot.cli-path` | String | env `COPILOT_CLI_PATH` | Copilot CLI executable path |
| `copilot.gh-cli-path` | String | env `GH_CLI_PATH` | GitHub CLI executable path |
| `copilot.start-timeout-seconds` | long | 60 | CLI startup timeout |
| `copilot.cli-healthcheck-seconds` | long | 10 | Healthcheck interval |
| `copilot.cli-authcheck-seconds` | long | 15 | Auth-check interval |

### 4.2 Agents

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `agents.directories` | List\<String\> | `[./agents, ./.github/agents]` | Agent definition directories |

### 4.3 Execution

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `execution.shared-session-enabled` | boolean | true | Share Copilot session across agents |
| `execution.gh-auth-fallback-enabled` | boolean | false | Allow `gh` CLI auth fallback |
| `execution.concurrency.parallelism` | int | 4 | Max parallel agents |
| `execution.concurrency.review-passes` | int | 1 | Review passes per agent |
| `execution.timeouts.orchestrator-timeout-minutes` | long | 45 | Overall orchestrator timeout |
| `execution.timeouts.agent-timeout-minutes` | long | 20 | Per-agent timeout |
| `execution.timeouts.idle-timeout-minutes` | long | 5 | Idle session timeout |
| `execution.timeouts.skill-timeout-minutes` | long | 20 | Skill execution timeout |
| `execution.timeouts.summary-timeout-minutes` | long | 20 | Summary generation timeout |
| `execution.timeouts.gh-auth-timeout-seconds` | long | 30 | GitHub auth timeout |
| `execution.retry.max-retries` | int | 2 | Max retry attempts per agent |
| `execution.buffers.max-accumulated-size` | int | 4194304 | Max accumulated buffer (4 MB) |
| `execution.buffers.initial-accumulated-capacity` | int | 4096 | Initial buffer capacity |
| `execution.buffers.instruction-buffer-extra-capacity` | int | 32 | Extra instruction buffer capacity |

### 4.4 Circuit Breaker

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `circuit-breaker.failure-threshold` | int | 8 | Failures before circuit opens |
| `circuit-breaker.reset-timeout-ms` | long | 30000 | Reset timeout (ms) |

### 4.5 Local Files

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `local-files.max-file-size` | long | 262144 | Max single file size (256 KB) |
| `local-files.max-total-size` | long | 2097152 | Max total content (2 MB) |

### 4.6 Templates

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `templates.directory` | String | `templates` | Template directory |
| `templates.default-output-format` | String | `default-output-format.md` | Default output format |
| `templates.report` | String | `report.md` | Report template |
| `templates.local-review-content` | String | `local-review-content.md` | Local review content |
| `templates.output-constraints` | String | `output-constraints.md` | Output constraints |
| `templates.review-quality-constraints` | String | `review-quality-constraints.md` | Quality constraints |
| `templates.summary.system-prompt` | String | `summary-system.md` | Summary system prompt |
| `templates.summary.user-prompt` | String | `summary-prompt.md` | Summary user prompt |
| `templates.summary.executive-summary` | String | `executive-summary.md` | Executive summary |
| `templates.fallback.summary` | String | `fallback-summary.md` | Fallback summary |

### 4.7 Skills

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `skills.filename` | String | `SKILL.md` | Skill definition filename |
| `skills.directory` | String | `.github/skills` | Skill files directory |
| `skills.max-parameter-value-length` | int | 10000 | Max param value length |
| `skills.max-executor-cache-size` | int | 16 | Executor cache max size |
| `skills.service-shutdown-timeout-seconds` | int | 60 | Service shutdown timeout |
| `skills.executor-shutdown-timeout-seconds` | int | 30 | Executor shutdown timeout |

### 4.8 MCP

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `mcp.github.type` | String | `http` | MCP server type |
| `mcp.github.url` | String | `https://api.githubcopilot.com/mcp/` | MCP server URL |
| `mcp.github.tools` | List\<String\> | `[get_file_contents, search_code, list_commits, get_commit]` | Allowed MCP tools |
| `mcp.github.allowed-hosts` | List\<String\> | `[api.githubcopilot.com]` | Allowed hosts |

### 4.9 Rubber Duck

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `rubber-duck.enabled` | boolean | true | Enable rubber-duck mode |
| `rubber-duck.dialogue-rounds` | int | 3 | Dialogue rounds (1–10) |
| `rubber-duck.peer-model` | String | `gpt-5.5` | Peer review model |
| `rubber-duck.synthesis-strategy` | String | `last-responder` | Synthesis strategy |

### 4.10 Models

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `models.default-model` | String | `claude-opus-4.8-xhigh` | Fallback model |
| `models.review-model` | String | `gpt-5.3-codex` | Review model |
| `models.report-model` | String | `claude-sonnet-4.6` | Report model |
| `models.summary-model` | String | `claude-sonnet-4.6` | Summary model |
| `models.reasoning-effort` | String | `high` | Reasoning effort (low/medium/high) |

### 4.11 Summary

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `summary.max-content-per-agent` | int | 50000 | Max chars per agent in summary |
| `summary.max-total-prompt-content` | int | 200000 | Max total summary content |
| `summary.fallback-excerpt-length` | int | 180 | Fallback excerpt length |

---

## §5 Observable Behaviors

### 5.1 Agent Loading & Validation (AGT-01 – AGT-13)

| ID | Behavior | Acceptance Criterion |
|----|----------|---------------------|
| AGT-01 | Agents loaded from configured directories (`agents/`, `.github/agents/`) | Agents from all configured dirs are discovered |
| AGT-02 | Agent name validation: lowercase alphanumeric + hyphens, max 64 chars | Invalid names rejected with error message |
| AGT-03 | Model allowlist enforced (prefixes: `claude-`, `gpt-`, `o3`, `o4-mini`, `gemini-`) | Disallowed model → agent rejected |
| AGT-04 | Agent file size limit: 64 KiB | Oversized → rejected with byte count |
| AGT-05 | YAML frontmatter required | Missing `---` → rejected |
| AGT-06 | Required fields: `name`, `systemPrompt`, `instruction` | Missing → `IllegalArgumentException` |
| AGT-07 | Output format recommended sections validated | Missing sections → warning (agent still loads) |
| AGT-08 | Prompt-injection safety scan on all agent fields | Suspicious patterns → agent skipped |
| AGT-09 | `enabled: false` flag disables agent | Agent silently excluded |
| AGT-10 | Focus area limits: max 50 areas, max 200 chars each | Exceeds → rejected |
| AGT-11 | Dialogue rounds range: 0–10 | Out of range → rejected |
| AGT-12 | Unknown frontmatter keys audited | Warning logged (agent still loads) |
| AGT-13 | Agent not found by name | Warning logged |

### 5.2 Skill System (SKL-01 – SKL-08)

| ID | Behavior | Acceptance Criterion |
|----|----------|---------------------|
| SKL-01 | Skills discovered from `.github/skills/<name>/SKILL.md` | All valid skills loaded; parse failure → logged, skipped |
| SKL-02 | Frontmatter parsed; body is prompt | No frontmatter → warn; empty body → exception |
| SKL-03 | Skill ID and prompt required | Null/empty → validation error |
| SKL-04 | Required parameter validation | Missing required param → rejected |
| SKL-05 | Parameter value length limit | Over-long → rejected |
| SKL-06 | Parameter prompt-injection scan | Suspicious → rejected |
| SKL-07 | Execution with retry, circuit breaker, timeout | Timeout/failure/empty → specific messages |
| SKL-08 | Symlink traversal prevention | Skills outside root excluded |

### 5.3 Custom Instructions & Safety (INS-01 – INS-05)

| ID | Behavior | Acceptance Criterion |
|----|----------|---------------------|
| INS-01 | Multi-language prompt-injection detection (EN, JA, KO, ZH) | Suspicious content flagged and rejected |
| INS-02 | Homoglyph normalization (Cyrillic/Greek → Latin) | Bypass via lookalike chars detected |
| INS-03 | Control character stripping, NFKC normalization | Invisible chars cannot bypass safety |
| INS-04 | Delimiter injection detection | `---BEGIN SYSTEM---` etc. patterns rejected |
| INS-05 | Frontmatter extraction from instructions | Metadata separated; no frontmatter → raw content used |

### 5.4 Review Target Collection (TGT-01 – TGT-09)

| ID | Behavior | Acceptance Criterion |
|----|----------|---------------------|
| TGT-01 | Local directory walked for source files | Files collected; missing dir → error |
| TGT-02 | Ignored directories (`.git`, `node_modules`, `target`, etc.) | Skipped via SKIP_SUBTREE |
| TGT-03 | Source file extension filter + special filenames | Only recognized files collected |
| TGT-04 | Sensitive file exclusion (`.env`, `.key`, `.pem`, etc.) | Patterns excluded silently |
| TGT-05 | Per-file size limit (256 KB default) | Large files skipped with debug log |
| TGT-06 | Total content size limit (2 MB default) | Collection stops; warning logged |
| TGT-07 | Symlink traversal prevention | Symlinks outside base rejected |
| TGT-08 | No matching files → placeholder `"(no source files found)"` | Review proceeds with placeholder |
| TGT-09 | Race condition detection during file read | Changed file skipped |

### 5.5 Orchestration (ORC-01 – ORC-10)

| ID | Behavior | Acceptance Criterion |
|----|----------|---------------------|
| ORC-01 | Configurable parallelism | `--parallelism` overrides config |
| ORC-02 | Virtual threads for agent execution | Named threads used |
| ORC-03 | Per-agent timeout | Timed-out agent → failed result |
| ORC-04 | Orchestrator-level timeout | All unfinished tasks cancelled |
| ORC-05 | Concurrency permit semaphore | Agents queue when limit reached |
| ORC-06 | Multi-pass review | Each agent runs N times, results merged |
| ORC-07 | Metrics collection (duration, wait, outcome) | Summary logged |
| ORC-08 | Rubber-duck mode | Peer-discussion with 2 models, dynamic timeout |
| ORC-09 | Agent execution failure handling | Failed result returned (not crash) |
| ORC-10 | Interrupt handling | Graceful cancellation |

### 5.6 Authentication & SDK (AUTH-01 – AUTH-11)

| ID | Behavior | Acceptance Criterion |
|----|----------|---------------------|
| AUTH-01 | OAuth device-flow via `gh auth login` | Pre-authentication works |
| AUTH-02 | CLI path resolution from env/PATH | Not found → guidance message |
| AUTH-03 | Client startup retry (3 attempts, exponential backoff) | Transient failures retried |
| AUTH-04 | Startup timeout (configurable) | Timeout → guidance message with `review doctor` suggestion |
| AUTH-05 | Protocol timeout (ping failure) | Timeout → guidance message |
| AUTH-06 | Health probe & auto-reinitialize | Unhealthy → re-init attempted |
| AUTH-07 | Token resolution: CLI arg → env vars → `gh auth token` | Fallback chain works |
| AUTH-08 | `--token -` reads from stdin | Stdin token works, blank falls through |
| AUTH-09 | `review doctor` diagnostics | All checks report ✓/✗ |
| AUTH-10 | Deprecated token API warning | Warning logged |
| AUTH-11 | Security audit logging | Auth events logged |

### 5.7 Retry & Circuit Breaker (RTY-01 – RTY-04)

| ID | Behavior | Acceptance Criterion |
|----|----------|---------------------|
| RTY-01 | Review retry with exponential backoff | Transient failures retried with logging |
| RTY-02 | Circuit breaker (3 domains: review, skill, summary) | Opens after threshold; resets after timeout |
| RTY-03 | Non-retryable vs transient classification | Only transient errors retried |
| RTY-04 | Skill retry (max 1 retry) | Skill failures retried once |

### 5.8 Output & Reporting

| ID | Behavior | Acceptance Criterion |
|----|----------|---------------------|
| OUT-01 | Markdown-only output format | All reports are `.md` files |
| OUT-02 | Per-agent reports: `{agent-name}-report.md` | One report per agent per run |
| OUT-03 | Multi-pass reports: `{agent-name}-pass-{n}-report.md` | Numbered per pass |
| OUT-04 | Executive summary: `executive_summary_{timestamp}.md` | Generated unless `--no-summary` |
| OUT-05 | Fallback summary when AI summary fails | Fallback template used |
| OUT-06 | Reports in timestamped subdirectory (`yyyy-MM-dd-HH-mm-ss/`) | Directory auto-created |
| OUT-07 | Progress to stdout: banners, completion summary | Always printed |
| OUT-08 | Errors to stderr | Via `CliOutput.errorln()` |
| OUT-09 | Dual output: stdout (progress) + files (reports) | Both always active; no suppress option |

---

## §6 Template Inventory

30 Mustache-style Markdown templates in `templates/`:

| Template | Purpose |
|----------|---------|
| `report.md` | Per-agent report structure |
| `default-output-format.md` | Default output format instructions |
| `local-review-content.md` | Local file review content |
| `output-constraints.md` | Agent output constraints |
| `review-quality-constraints.md` | Review quality rules |
| `summary-system.md` | AI summary system prompt |
| `summary-prompt.md` | AI summary user prompt |
| `executive-summary.md` | Executive summary format |
| `summary-result-entry.md` | Success entry in summary |
| `summary-result-error-entry.md` | Failure entry in summary |
| `fallback-summary.md` | Fallback when AI fails |
| `fallback-agent-row.md` | Agent row in fallback table |
| `fallback-agent-success.md` | Success agent in fallback |
| `fallback-agent-failure.md` | Failed agent in fallback |
| `report-link-entry.md` | Report cross-reference link |
| `local-review-result-request.md` | Local review result request |
| `local-source-header.md` | Local source file header |
| `review-custom-instruction.md` | Custom instruction injection |
| `custom-instruction-section.md` | Custom instruction section |
| `agent-focus-areas-guidance.md` | Focus areas guidance |
| `rubber-duck-initial-{en,ja}.md` | Rubber-duck initial prompt (2 langs) |
| `rubber-duck-peer-review-{en,ja}.md` | Peer review prompt (2 langs) |
| `rubber-duck-counter-{en,ja}.md` | Counter-argument prompt (2 langs) |
| `rubber-duck-synthesis-{en,ja}.md` | Synthesis prompt (2 langs) |

---

## §7 Parity Checklist Summary

| Category | Count | IDs |
|----------|-------|-----|
| Agent loading & validation | 13 | AGT-01 – AGT-13 |
| Skill system | 8 | SKL-01 – SKL-08 |
| Custom instructions & safety | 5 | INS-01 – INS-05 |
| Review target collection | 9 | TGT-01 – TGT-09 |
| Orchestration | 10 | ORC-01 – ORC-10 |
| Authentication & SDK | 11 | AUTH-01 – AUTH-11 |
| Retry & circuit breaker | 4 | RTY-01 – RTY-04 |
| Output & reporting | 9 | OUT-01 – OUT-09 |
| **Total** | **69** | |

Plus 4 commands, 4 exit codes, 30 templates, 50+ configuration keys.

Tester: use these IDs as the traceability index for regression testing.

# Deleting The Legacy Tree Is What Verifies A Rewrite

A brownfield rewrite reports false green while both trees coexist; migrating the tests and deleting the old tree is the first real verification.

## What Happened

`multi-agent-code-reviewer` / t13. Phases T007–T012 each rewrote a slice of the codebase into a new
layered structure and each reported a green `mvn clean verify` — 912 tests, 0 failures.

That green was meaningless. The legacy tests were still importing and exercising the **legacy**
classes, which still existed alongside the new ones. Nothing was asserting that the new production
code actually worked.

t13 migrated all 148 test files onto the new packages and deleted the pre-migration tree. The first
run against the new code produced **14 failures**, which root-caused to **7 genuine production
regressions** introduced by the earlier "green" phases:

- `GithubMcpConfig.authHeaderTemplate` lost its `{token}` placeholder → **GitHub MCP auth completely
  broken by default** (CRITICAL).
- `McpServerSpec`'s `Map.copyOf(headers)` stripped the header-masking wrapper → **raw auth token
  leaked into logs via `toString()`** (HIGH, security).
- `LocalFileConfig` dropped **22 of 36** sensitive-file patterns incl. `aws-credentials`,
  `kubeconfig`, `.netrc`, `terraform.tfvars` (HIGH, security).
- `CopilotCliException` was duplicated into another package, so all 5 catch sites caught the wrong
  type and path-rejection errors escaped every handler.
- `AgentConfig.validateRequired()` was inlined, silently dropping two required-field checks.
- `CopilotClientStarter` let a close failure replace the real startup error.
- `ExecutionCorrelation` dropped 5 MDC methods a prior task had promised to re-home.

A useful secondary signal: **three of these left dead code behind** (`AgentConfigValidator` and both
`SensitiveHeaderMasking` classes were unreferenced). Dead code after a rewrite is usually the
fingerprint of a dropped behaviour, not harmless cruft.

## Takeaway

- Treat "tests pass" during a phased rewrite as **unverified** until the legacy tree is deleted.
  Schedule the migrate-tests-and-delete task as early as the dependency graph allows — every phase
  before it is running blind.
- Budget the deletion task for regression *repair*, not just mechanical moves. Roughly half of t13's
  effort was diagnosing production defects the migration surfaced.
- After a rewrite, grep `src/main` for classes with **zero inbound references**. Each one is a
  candidate dropped behaviour.
- Rule for triage: where the new behaviour is a **defect**, fix production; where it is
  **intentional and better**, update the test and document why in-code. Never silently rewrite a
  test to make the build green. Require sub-agents to justify every deleted test method for the
  same reason.

## History
- 2026-08-05 (multi-agent-code-reviewer/t13): initial

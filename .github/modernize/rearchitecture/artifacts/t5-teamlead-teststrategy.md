# t5 — Test Strategy

## Overview

Three-tier validation strategy for the Ports & Adapters rewrite:

1. **ArchUnit boundary tests** — structural correctness (compile-time)
2. **Regression test suite** — behavioral correctness (existing 148 tests)
3. **Smoke tests** — runtime correctness (CLI commands work end-to-end)

---

## Tier 1: ArchUnit Boundary Tests

**Owner**: backend (T012)
**When**: Created after all layers are wired (Phase 5). Run on every subsequent build.

### Rules

| Rule | What it verifies | Constitution ref |
|------|------------------|------------------|
| Domain purity | `domain..` imports nothing from Micronaut, Jakarta, Copilot SDK, SLF4J, SnakeYAML | §3 |
| Shared purity | `shared..` imports only `java..` | §2 |
| Inward dependency | No package imports `presentation..` (except presentation itself) | §2 |
| Infrastructure isolation | `infrastructure..` imports only `application.port..`, `domain..`, `shared..` | §2 |
| Application isolation | `application..` (excl. port) does not import `infrastructure..`, `presentation..` | §2 |
| Cycle freedom | Package-level slice analysis: zero cycles | §7.5 |

### Implementation

```java
@AnalyzeClasses(packages = "dev.logicojp.reviewer")
class LayerDependencyRulesTest {
    // 6 rules as @ArchTest fields
    // Uses ArchUnit 1.3+ with JUnit 5 integration
}
```

**Dependency**: Add `com.tngtech.archunit:archunit-junit5:1.3.0` to `pom.xml` `<scope>test</scope>`.

**Pass criteria**: All 6 rules pass. Any violation = CRITICAL.

---

## Tier 2: Regression Test Suite

**Owner**: tester (T015)
**When**: After test migration (T013) and build verification (T014).

### Scope

- All 148 existing test files (~17017 LOC)
- Tests are refactored for package moves only — no test logic changes
- Test framework: JUnit 5 + AssertJ + Micronaut Test (unchanged)

### Execution

```bash
mvn -B test
```

### Pass criteria

- **Zero test failures** — any failure means regression
- **Zero test errors** — compilation/runtime errors from bad imports
- **Skipped tests**: acceptable only if already skipped before migration (track count)

### Behavior traceability

Tests are cross-referenced to PM behavior IDs (t3-pm.md §5–§7). The tester must verify:

| Category | Behavior IDs | Approximate test count |
|----------|-------------|----------------------|
| Agent loading & validation | AGT-01–13 | ~20 tests |
| Skill system | SKL-01–08 | ~10 tests |
| Custom instructions & safety | INS-01–05 | ~5 tests |
| Review target collection | TGT-01–09 | ~10 tests |
| Orchestration | ORC-01–10 | ~15 tests |
| Authentication & SDK | AUTH-01–11 | ~10 tests |
| Retry & circuit breaker | RTY-01–04 | ~5 tests |
| Output & reporting | OUT-01–09 | ~15 tests |

Each behavior ID must be covered by at least one passing test. Missing coverage → report as HIGH finding.

---

## Tier 3: Smoke Tests

**Owner**: architect (T016, per charter: smoke test is architect's responsibility)
**When**: After full regression passes.

### Tests

| Test | Command | Expected |
|------|---------|----------|
| Doctor diagnostics | `java -jar target/*.jar doctor` | Exit 0, diagnostic output |
| Agent listing | `java -jar target/*.jar list` | Exit 0, agents listed |
| Help output | `java -jar target/*.jar --help` | Exit 0, usage text |
| Version output | `java -jar target/*.jar --version` | Exit 0, version string |

### Verdict format

```
## Smoke Test Verdict
- build_command: `mvn -B clean verify`
- returncode: <integer>
- covers_all_modules: yes
- test_returncode: <integer>
```

---

## Build Verification Matrix

| Build mode | Command | Constitution ref | Mandatory |
|------------|---------|------------------|-----------|
| Standard | `mvn -B clean verify` | §7.2 | Yes |
| Shade JAR | Check `target/*.jar` exists | §7.2 | Yes |
| Native image | `mvn -B clean verify -Pnative` | §7.2 | If GraalVM 26 EA available |

---

## Environment Requirements

| Requirement | Value | Source |
|-------------|-------|--------|
| Java | 26 (GraalVM 26 EA) | clarification.md |
| Maven | 3.9+ | pom.xml |
| ArchUnit | 1.3.0+ | New dependency (T012) |
| Test framework | JUnit 5 + AssertJ + Micronaut Test | Existing |

---

## Quality Gates

| Gate | Owner | Trigger | Criteria |
|------|-------|---------|----------|
| Per-task test | backend | After each task | Module tests pass, no new failures |
| ArchUnit | backend | After T012 | All 6 rules pass |
| Full regression | tester | After T014 | 148 tests pass, zero failures |
| Smoke test | architect | After T015 | All CLI commands return expected output |
| Build preservation | backend | T014 | Shade + native-image both succeed |

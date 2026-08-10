# t34 — SEC-L10 model-prefix behavioral closure

## Summary

`ALLOWED_MODEL_PREFIXES` is now pinned by a dedicated behavioral suite rather than by a
name-only liveness reference. The suite fixes the admitted family set at `claude-`, `gpt-`,
`o3`, `o4-mini`, and `gemini-`, then exercises the public policy with matched acceptance and
rejection boundaries for every family.

No production source was changed.

## Upstream Artifacts Consumed

- `.github/modernize/rearchitecture/clarification.md` — Java 28 CLI target and the requirement
  that the existing Maven suite must continue to pass.
- `.github/modernize/rearchitecture/team/tester/inbox.md` — SEC-L10 origin, ADR-0007 D7
  non-vacuity requirement, and the instruction to pin behavior rather than one sample.
- `.github/modernize/rearchitecture/artifacts/t18.3-backend.md` — authoritative half-closed
  status: `ALLOWED_CHAR_RANGE` closed, `ALLOWED_MODEL_PREFIXES` remaining.
- `.github/modernize/rearchitecture/artifacts/t18-security-rerun.md` — original SEC-L10 finding
  and the explicit ruling that prefix matching itself is not a security finding.
- `.github/modernize/rearchitecture/artifacts/t18-security-rerun-charset-audit.md` — source/test
  reference counts and the requirement that the test name the constant.

## Evidence Mapping

- `clarification.md#Generic / Existing test posture` → Java 28 full `clean verify`, 1,090 total
  tests, zero failures/errors/skips.
- `team/tester/inbox.md#SEC-L10` → `AgentModelPrefixPolicyTest` directly identifies the private
  constant and tests 15 admitted plus 15 rejected boundary inputs.
- `t18.3-backend.md#SEC-L10 is half-closed` → `ALLOWED_MODEL_PREFIXES` added to
  `AgentPolicyConstantsAreLiveTest` only after the behavioral suite existed.
- `t18-security-rerun-charset-audit.md#SEC-L10 detail` →
  `configuredFamiliesArePinnedExactly` fixes the complete five-family set, while matched pairs
  prove start anchoring, prefix length, suffix acceptance, and case folding.
- `t18-security-rerun.md#Deliberately not raised` → tests preserve the shipped prefix semantics;
  they do not invent a narrower model-name contract.

## Behavioral Contract

For each of the five configured prefixes:

| Boundary | Expected |
|---|---|
| Exact prefix | admitted |
| Prefix plus an arbitrary suffix | admitted |
| Upper-cased prefix plus suffix | admitted |
| Prefix shortened by one character | rejected with `AGENT-MODEL` |
| Prefix occurring after `vendor-` | rejected with `AGENT-MODEL` |
| Leading whitespace before the prefix | rejected with `AGENT-MODEL` |

The rejection checks also pin the observable rule ID and diagnostic text.

## Files Changed

- `src/test/java/dev/logicojp/reviewer/domain/agent/AgentModelPrefixPolicyTest.java` — new,
  three-test behavioral contract with 30 boundary inputs.
- `src/test/java/dev/logicojp/reviewer/domain/agent/AgentPolicyConstantsAreLiveTest.java` —
  includes `ALLOWED_MODEL_PREFIXES` in the policy-constant inventory.

## Test Results

### Focused

- Command:
  `JAVA_HOME="$HOME/.sdkman/candidates/java/28.ea.9-open" ./mvnw -B -Dtest=AgentModelPrefixPolicyTest,AgentDefinitionPolicyTest,AgentPolicyConstantsAreLiveTest test`
- Exit code: **0**
- Passed: **42**
- Failed: **0**
- Errors: **0**
- Skipped: **0**
- Reconciliation: prior focused baseline **38** + 3 behavioral tests + 1 liveness parameter
  = **42**.

### Full regression

- Command:
  `JAVA_HOME="$HOME/.sdkman/candidates/java/28.ea.9-open" ./mvnw -B clean verify`
- Exit code: **0**
- Surefire passed: **1,086**
- Failsafe packaged-CLI passed: **4**
- Total passed: **1,090**
- Failed: **0**
- Errors: **0**
- Skipped: **0**
- Reconciliation: t32.3 baseline **1,082 + 4 = 1,086**; t34 adds exactly **4**, producing
  **1,086 + 4 = 1,090**.
- Packaged CLI flows: general help, version, agent listing, and doctor help all started from
  the shaded JAR in an isolated working directory.
- API endpoint gate: not applicable; `clarification.md` identifies a CLI-only application with
  no HTTP/RPC server.

## Residual Observation

`AgentPolicyConstantsAreLiveTest` counts its own `@ValueSource` entry as a test-side reference,
so adding a name there alone is self-satisfying. This task does not rely on that assertion:
the separate behavioral suite names the constant and exercises both sides of every prefix
boundary. The general meta-test limitation was reported to the coordinator and backend.

## Verdict

integration: PASS — exit_code: 0, passed: 1086, failed: 0, skipped: 0; all model-prefix families and the full JVM regression suite verified, no in-scope gaps
e2e: PASS — exit_code: 0, passed: 4, failed: 0; packaged JAR help/version/list/doctor flows verified in an isolated environment
overall: PASS — SEC-L10's remaining constant is behaviorally pinned and all regression gates are green

# Every Matrix Row Needs Exactly One Enforcement Rule

An allowed-imports row with no automated rule behind it is a defect in the architecture, not just a gap in the tests.

## What Happened

In `rearchitecture/t16` the ADR-0006 review mapped every row of the t4 allowed-imports matrix to
its enforcing rule in `LayerDependencyRulesTest` and found two mismatches:

- **No rule at all** for `presentation ⊥ infrastructure`. Current violations were 0, so the gap was
  invisible — the constraint was being satisfied by luck, with nothing preventing regression.
- **A rule wider than the matrix**: Rule 4 allowed `infrastructure → application.port` where the
  matrix says `application.port.outbound`. That extra width is exactly what let two ports be
  misclassified as inbound while being implemented in `infrastructure`.

Both were only found by doing the row→rule mapping explicitly as a table. Reading the rules or
reading the matrix alone did not surface either one.

## Takeaway

- Maintain an explicit **row → rule** mapping table alongside the matrix and treat any row with
  zero rules, or any rule broader than its row, as a defect to be filed.
- "0 current violations" is not evidence a rule is unnecessary — it is the cheapest possible moment
  to add it, because adding it costs no remediation.
- Rules that are *wider* than the matrix are more dangerous than missing rules: they give false
  assurance and actively permit a defect class.
- Do this mapping before certifying a layered structure. It is a table-construction exercise, not
  a code review, and it takes minutes.

## History
- 2026-08-05 (rearchitecture/t16): initial — recorded as ADR-0006 D5; produced Rule 7 and the Rule 4 narrowing

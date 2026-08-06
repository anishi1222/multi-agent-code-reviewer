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

- Name new rules with a **letter suffix at their logical position** (`5b`) rather than appending a
  next number or renumbering. Rule numbers get cited by learnings, review notes, and ADRs;
  renumbering silently invalidates those citations.
  - **Clarified in t30:** the governing constraint is *never renumber an existing rule*; the `5b`
    suffix is the mechanism for **inserting between** existing rules. Appending the next free number
    is fine when it renumbers nothing — and is the *better* choice when the new number is already
    cited downstream, since inventing a suffix would then invalidate the very citations this rule
    protects. Check what is already published before choosing.

## History
- 2026-08-05 (rearchitecture/t16): initial — recorded as ADR-0006 D5; produced the Rule 4 narrowing
  and the `presentation ⊥ infrastructure` rule, which backend landed as **Rule 5b** (0 exempt) in t13.1
- 2026-08-06 (rearchitecture/t30): **first observed consequence.** ADR-0007 D5 (Accepted) declares a
  "Rule 4b" forbidding `application.port → shared.SensitiveHeaderMasking`. `grep "Rule 4b" src/test`
  → **0 matches**: the rule was never written, and `McpServerSpec:34` has been calling
  `SensitiveHeaderMasking.wrapHeaders(...)` undetected ever since. Declaring an enforcement inside an
  ADR does not create it. **Add the guard: an ADR must not be marked Accepted while any of its
  D-items names a rule that does not yet exist in the test tree** — either ship the rule with the
  ADR, or write the item as a proposal and leave the ADR Proposed. Also added the numbering
  clarification above after Rule 8 exposed the ambiguity.

- 2026-08-06 (t31): the ADR-0007 Rule 4b gap above was **closed** — rule written, observed RED
  against the real violator (`McpServerSpec`), then GREEN after the D5 removal, with a permanent
  subject-count control because it lands at 0 violators / 0 exemptions. The proposed guard
  ("an ADR must not be Accepted while a D-item names a rule absent from the test tree") is
  **still not adopted** and is re-raised in `artifacts/t31-architect.md` §7 for ratification.
  This was the third instance of the pattern; the row is not the control.

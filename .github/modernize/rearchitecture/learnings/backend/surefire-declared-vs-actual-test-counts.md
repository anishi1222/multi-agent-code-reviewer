# Surefire Reports Two Different Test Counts

Maven's console total counts actual `<testcase>` elements; summing the `tests` attribute of surefire XML roots under-counts parameterized classes — reconcile with the console figure.

## What Happened
multi-agent-code-reviewer/t16.1. A test-count reconciliation disagreed with the baseline by exactly
9 (928 vs 937), and the discrepancy had been carried forward across tasks as "a different measure".

It is not a measurement philosophy difference — it is a defect in the XML root attribute. Three
classes (`ConfigDefaultsTest`, `AgentPromptBuilderTest`, `TokenHashUtilsTest`) declare fewer tests
in `<testsuite tests="…">` than they emit as `<testcase>` children (6 + 1 + 2 = 9), because of
`@ParameterizedTest` / dynamic test expansion.

Also worth knowing: `-Dtest=SomeTest` does **not** clear `target/surefire-reports/`, so scripts that
glob that directory after a targeted run will happily sum stale XML from the previous full run —
including reports for test classes that have since been deleted.

## Takeaway
- Reconcile against Maven's `Tests run:` console summary, not a script over XML root attributes.
- If you must script it, count `root.findall('.//testcase')`, not `root.get('tests')`.
- Always `clean` before a run whose counts you intend to quote.
- A constant offset between two counts across several tasks is a smell — find the cause once
  instead of re-explaining it every task.

## Example
```python
# wrong: under-counts parameterized classes
total = sum(int(ET.parse(p).getroot().get('tests', 0)) for p in xmls)
# right
total = sum(len(ET.parse(p).getroot().findall('.//testcase')) for p in xmls)
```

## History
- 2026-08-05 (multi-agent-code-reviewer/t16.1): initial

# Package Cycle Detection Needs Sibling Granularity, Not Top-Level Slices

**Slicing cycle detection at the top-level layer boundary misses the cycles that actually occur.** Real cycles form between *sibling sub-packages* (`report.core ⇄ report.formatter`), which collapse into a single slice and become invisible.

## What Happened

A layer-level cycle rule (slices = the 5 top-level layers) reported 0 cycles and was believed to mean "no package cycles." Re-running with slices = each layer's immediate sub-packages immediately surfaced a real one:

```
presentation ⇄ presentation.command ⇄ presentation.parser ⇄ presentation.formatter
```

The load-bearing edge was `presentation.parser.SkillOptionsParser` → `presentation.command.SkillCommand$ParsedOptions` — a parser depending on a command, purely because a shared DTO had been nested inside the command class. Every one of those packages lives inside `presentation`, so top-level slicing folded the whole cycle into one node and saw nothing.

The naive fix — slice at sub-package granularity *including* the layer root — produces noise instead. Parent↔child package edges (`presentation` ↔ `presentation.command`) are benign Java cohesion: packages have no nesting semantics, so a parent depending on its child is not a design smell. Reporting those buries the sibling cycles you care about.

## Takeaway

**Run two rules at two granularities:**

- **6a — layer level.** Slices are the top-level layers. Catches genuine architectural inversions (`domain → infrastructure`).
- **6b — sibling level.** Slices are each layer's immediate sub-packages, **excluding the layer root**. Catches the cycles that really happen, without flagging benign parent↔child cohesion.

When 6b reports a cycle, resolve it by finding the shared type that was nested in the wrong place. A cycle between a parser and a command almost always means a DTO belongs in the parent package that both already depend on — not that either needs an interface or an inversion. Check whether the codebase already has a sibling DTO establishing that convention (here, `ReviewOptions` was already in `presentation`, and the nested `SkillCommand.ParsedOptions` was simply inconsistent with it).

**Scope transitional exclusions so they self-destruct** — assert the legacy-package set is *non-empty*, so the test fails once the legacy tree is deleted and forces its own cleanup rather than rotting.

## Example

```java
// 6b: slices are the layer's immediate sub-packages, root EXCLUDED
private Set<String> siblingSubPackages(String layer) {
    return parsedClassNames.stream()
        .filter(n -> n.startsWith(layer + "."))
        .map(n -> n.substring(0, n.indexOf('.', layer.length() + 1) < 0
                ? n.lastIndexOf('.') : n.indexOf('.', layer.length() + 1)))
        .filter(p -> !p.equals(layer))   // <-- drops benign parent<->child edges
        .collect(toSet());
}

for (String layer : NEW_LAYERS) {
    var cycles = stronglyConnectedComponents(packageGraph(siblingSubPackages(layer)));
    assertThat(cycles).as("sibling cycles in %s", layer).isEmpty();
}
```

Verified by mutation: adding a known-cyclic legacy package to 6b's scope makes it fail and report `report.finding → report.formatter → report.core → report.finding`.

## History
- 2026-08-05 (multi-agent-code-reviewer/t12.1): initial

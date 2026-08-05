package dev.logicojp.reviewer.architecture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Enforces the layered / ports-and-adapters boundaries defined in
/// `t4-architect-packages.md` §2 (the allowed-imports-per-layer matrix).
///
/// ## Why this test does not use ArchUnit
///
/// This project compiles with `--release 27`, emitting class files at major version **71**.
/// ArchUnit shades ASM internally, and its newest release (1.4.1) tops out at `V25 = 69`.
/// ArchUnit therefore throws `IllegalArgumentException: Unsupported class file major version 71`
/// for every application class, then **catches it, logs it, and silently continues with a partial
/// class set**. Measured on this repository: ArchUnit imported 107 of 687 classes, and those 107
/// were exclusively Micronaut-generated glue compiled at major 61. Every boundary rule was
/// consequently vacuous — it inspected zero hand-written classes and passed unconditionally.
/// Because ASM is shaded it cannot be overridden from the POM, and the Java 27 target is fixed.
///
/// This test instead uses the JDK's own `java.lang.classfile` API (JEP 484, final since JDK 24),
/// which parses the current release by construction and cannot fall behind it.
///
/// ## How this test stays honest
///
/// 1. **Rule 0** asserts the analyzer parsed *every* class file on disk, plus named anchor
///    classes. Silent analyzer blindness — the exact failure described above — cannot recur
///    undetected.
/// 2. Every rule asserts its subject set is **non-empty** before checking violations, replacing
///    ArchUnit's `failOnEmptyShould=false`, which permitted vacuous passes.
/// 3. Exclusions are **exact FQNs and self-cleaning**: each rule asserts that the violations found
///    *while ignoring exclusions* equal the exclusion list **exactly**. A new violator fails the
///    build, and so does an exemption that is no longer needed. This doubles as the negative
///    control proving the rule actually fires.
/// 4. Every rule prints how many classes it inspected, so a reader can see it is doing work.
@DisplayName("Architecture: layer boundary enforcement")
class LayerDependencyRulesTest {

    private static final String BASE = "dev.logicojp.reviewer";
    private static final Path CLASSES = Path.of("target", "classes");

    private static final String DOMAIN = BASE + ".domain";
    private static final String APPLICATION = BASE + ".application";
    private static final String APPLICATION_PORT = BASE + ".application.port";
    private static final String INFRASTRUCTURE = BASE + ".infrastructure";
    private static final String PRESENTATION = BASE + ".presentation";
    private static final String SHARED = BASE + ".shared";

    /// The five layers introduced by the rearchitecture. Everything else under [#BASE] is
    /// pre-migration code scheduled for deletion in t13.
    private static final List<String> NEW_LAYERS =
        List.of(DOMAIN, APPLICATION, INFRASTRUCTURE, PRESENTATION, SHARED);

    /// Matches a JVM field descriptor for a reference type, e.g. `Lio/micronaut/context/Foo;`.
    ///
    /// Scanning `Utf8Entry` values in addition to `ClassEntry` is required, not optional:
    /// annotation types (`@Singleton`, `@Inject`) and generic signatures appear *only* as UTF-8
    /// descriptors, never as `ClassEntry`. Detecting exactly those annotations is the whole point
    /// of Rule 1. The sweep slightly over-approximates — a string constant shaped like a
    /// descriptor would be counted — which can only produce a false failure, never a false pass.
    private static final Pattern TYPE_DESCRIPTOR =
        Pattern.compile("L([a-zA-Z_$][a-zA-Z0-9_$]*(?:/[a-zA-Z_$][a-zA-Z0-9_$]*)*);");

    /// Fully-qualified class name -> every type it references.
    private static Map<String, Set<String>> dependencies;
    private static int classFilesOnDisk;

    @BeforeAll
    static void analyseBytecode() throws IOException {
        assertTrue(Files.isDirectory(CLASSES),
            "target/classes is missing — compile the project before running architecture tests");

        Map<String, Set<String>> graph = new TreeMap<>();

        try (Stream<Path> files = Files.walk(CLASSES)) {
            List<Path> classFiles = files
                .filter(path -> path.toString().endsWith(".class"))
                .filter(path -> !path.getFileName().toString().equals("module-info.class"))
                .toList();
            classFilesOnDisk = classFiles.size();

            for (Path file : classFiles) {
                ClassModel model = ClassFile.of().parse(Files.readAllBytes(file));
                String owner = toFqn(model.thisClass().asInternalName());
                graph.computeIfAbsent(owner, ignored -> new TreeSet<>()).addAll(referencedTypes(model));
            }
        }

        dependencies = Map.copyOf(graph);
    }

    // ------------------------------------------------------------------------------------------
    // Rule 0 — the analyzer must actually see the code
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 0: the analyzer parses every compiled class (guards against vacuous rules)")
    void analyzerSeesEveryCompiledClass() {
        assertAll(
            () -> assertTrue(classFilesOnDisk > 0, "No class files found under " + CLASSES),
            () -> assertEquals(classFilesOnDisk, dependencies.size(),
                "Parsed class count must equal the number of .class files on disk. A shortfall "
                    + "means the analyzer is silently skipping classes, so every rule below is "
                    + "unreliable — this is exactly the ArchUnit failure this test replaced."),
            // Named anchors spanning every layer. If a package is ever renamed, these fail first
            // and say so, instead of a rule quietly narrowing to an empty subject set.
            () -> assertAnchorPresent(BASE + ".ReviewApp"),
            () -> assertAnchorPresent(PRESENTATION + ".CliOutput"),
            () -> assertAnchorPresent(PRESENTATION + ".SkillOptions"),
            () -> assertAnchorPresent(APPLICATION + ".port.inbound.LoadAgentPort"),
            () -> assertAnchorPresent(INFRASTRUCTURE + ".copilot.ApplicationPortFactory")
        );

        System.out.printf("[arch] Rule 0: parsed %d/%d classes%n", dependencies.size(), classFilesOnDisk);
        NEW_LAYERS.forEach(layer ->
            System.out.printf("[arch]   %-44s %4d classes%n", layer, classesIn(layer).size()));
    }

    // ------------------------------------------------------------------------------------------
    // Rules 1-5 — the allowed-imports matrix (t4-architect-packages.md §2)
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 1: domain depends only on the JDK, domain and shared (no frameworks)")
    void domainIsFrameworkFree() {
        // Deliberately an allowlist rather than a denylist of known frameworks: a denylist rots
        // silently the moment an unfamiliar dependency is introduced.
        assertNoViolations("Rule 1 (domain purity)", classesIn(DOMAIN),
            dep -> !(isJdk(dep) || dep.startsWith(DOMAIN) || dep.startsWith(SHARED)),
            Set.of());
    }

    @Test
    @DisplayName("Rule 2: shared depends only on the JDK and shared")
    void sharedIsSelfContained() {
        assertNoViolations("Rule 2 (shared purity)", classesIn(SHARED),
            dep -> !(isJdk(dep) || dep.startsWith(SHARED)),
            Set.of());
    }

    @Test
    @DisplayName("Rule 3: nothing outside the presentation layer depends on presentation")
    void presentationIsNotReachedFromOtherLayers() {
        Set<String> subjects = new TreeSet<>(dependencies.keySet());
        subjects.removeIf(owner -> !owner.startsWith(BASE + ".") || owner.startsWith(PRESENTATION));

        assertNoViolations("Rule 3 (presentation is a leaf)", subjects,
            dep -> dep.startsWith(PRESENTATION),
            // Composition root: the Micronaut entry point must name the commands it wires up.
            // `$ReviewApp$Definition` is the DI metadata Micronaut generates for ReviewApp; it
            // mirrors ReviewApp's injection points, so it reproduces the same dependency and is
            // exempted for the same reason. Generated classes are otherwise fully in scope — the
            // previous revision excluded every class whose name contained `$`, which silently
            // removed most of the class set from this rule.
            //
            // Note for ADR-0006 — t4 §1 places ReviewApp *inside* presentation/, which would
            // remove the need for both exemptions entirely. Relocating it is outside the scope of
            // t12.1 (scoped to the enforcement layer), so it is exempted here, explicitly.
            Set.of(
                BASE + ".ReviewApp",
                BASE + ".$ReviewApp$Definition"));
    }

    @Test
    @DisplayName("Rule 4: infrastructure reaches application only through its ports")
    void infrastructureUsesApplicationPortsOnly() {
        assertNoViolations("Rule 4 (infrastructure -> application.port only)", classesIn(INFRASTRUCTURE),
            dep -> dep.startsWith(APPLICATION) && !dep.startsWith(APPLICATION_PORT),
            // Micronaut @Factory classes form the composition root: binding a port to its
            // implementation necessarily names that implementation, and this is the one place in
            // the system where that is legitimate. Note for ADR-0006 — t4 §3 places these
            // factories in infrastructure.copilot while §2 forbids infrastructure -> application
            // internals; that tension in the blueprint is still unresolved.
            Set.of(
                INFRASTRUCTURE + ".copilot.ApplicationPortFactory",
                INFRASTRUCTURE + ".copilot.ReviewContextFactory",
                INFRASTRUCTURE + ".copilot.ReviewOrchestratorFactory"));
    }

    @Test
    @DisplayName("Rule 5: application depends on neither infrastructure nor presentation")
    void applicationDependsOnNeitherAdapterLayer() {
        assertNoViolations("Rule 5 (application is adapter-agnostic)", classesIn(APPLICATION),
            dep -> dep.startsWith(INFRASTRUCTURE) || dep.startsWith(PRESENTATION),
            Set.of());
    }

    // ------------------------------------------------------------------------------------------
    // Rule 6 — acyclicity, at two granularities
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 6a: the five layers form no dependency cycle")
    void layersAreAcyclic() {
        Map<String, Set<String>> graph = packageGraph(NEW_LAYERS::contains);

        assertEquals(NEW_LAYERS.size(), graph.size(),
            "Expected every layer to appear in the graph, found " + graph.keySet());

        List<List<String>> cycles = stronglyConnectedComponents(graph);
        System.out.printf("[arch] Rule 6a: %d layers inspected, %d cycle(s)%n", graph.size(), cycles.size());
        assertTrue(cycles.isEmpty(), () -> "Dependency cycle between layers:\n" + renderCycles(cycles));
    }

    @Test
    @DisplayName("Rule 6b: sibling sub-packages within a layer form no dependency cycle")
    void siblingSubPackagesAreAcyclic() {
        // Granularity matters. Slicing only at layer level — what the previous revision did —
        // hides sibling cycles such as `report.core <-> report.formatter`. This rule slices at
        // `<layer>.<sub>` and compares siblings.
        //
        // A layer's own root package is deliberately not a slice here: in Java a parent package
        // and its children are one cohesive unit with no nesting semantics, so parent<->child
        // edges are ordinary cohesion rather than an architectural defect. Cycles *between
        // siblings* are the real defect, and are what this rule targets.
        List<String> offenders = new ArrayList<>();
        int inspected = 0;

        for (String layer : NEW_LAYERS) {
            Map<String, Set<String>> graph = packageGraph(pkg -> pkg.startsWith(layer + "."));
            List<List<String>> cycles = stronglyConnectedComponents(graph);
            inspected += graph.size();

            System.out.printf("[arch] Rule 6b: %-44s %2d sibling sub-packages, %d cycle(s)%n",
                layer, graph.size(), cycles.size());
            if (!cycles.isEmpty()) {
                offenders.add(layer + ":\n" + renderCycles(cycles));
            }
        }

        assertTrue(inspected > 0, "Rule 6b inspected 0 sub-packages — it would pass unconditionally");
        assertTrue(offenders.isEmpty(),
            () -> "Dependency cycle between sibling sub-packages:\n" + String.join("\n", offenders));
    }

    @Test
    @DisplayName("Rule 6 scope: pre-migration packages are excluded, but named and counted")
    void legacyPackagesAreExplicitlyOutOfCycleScope() {
        Map<String, Integer> legacy = new TreeMap<>();
        for (String owner : dependencies.keySet()) {
            if (!owner.startsWith(BASE + ".")) {
                continue;
            }
            String topLevel = topLevelPackageOf(owner);
            if (!NEW_LAYERS.contains(topLevel)) {
                legacy.merge(topLevel, 1, Integer::sum);
            }
        }

        // Documented, not silent. These packages carry the ten cycles catalogued in t2 and are
        // deleted by t13. Rules 6a/6b are scoped to the new layers so that known, scheduled-for-
        // removal code cannot mask a regression in the new architecture.
        System.out.printf("[arch] Rule 6 scope: %d pre-migration package(s) excluded, removed by t13%n",
            legacy.size());
        legacy.forEach((pkg, count) -> System.out.printf("[arch]   %-44s %4d classes%n", pkg, count));

        // Trigger for t13: once the legacy tree is gone this assertion fails, forcing the
        // exclusion — and this test — to be removed rather than left behind as dead scaffolding.
        assertFalse(legacy.isEmpty(),
            "No pre-migration packages remain, so the t13 cleanup is complete. Delete this test "
                + "and widen Rules 6a/6b to every package under " + BASE + ".");
    }

    // ------------------------------------------------------------------------------------------
    // Rule assertion helper
    // ------------------------------------------------------------------------------------------

    /// Asserts that no class in `subjects` references a type matching `forbidden`, other than the
    /// classes named in `expectedViolators`.
    ///
    /// Three properties are checked together, and it is the combination that makes an exemption
    /// honest rather than a loophole:
    ///
    /// - the subject set is **non-empty**, so the rule cannot pass vacuously;
    /// - the violations found *while ignoring exemptions* equal `expectedViolators` **exactly**,
    ///   so a new violator fails the build and so does a stale exemption;
    /// - therefore the rule is proven to fire, which is the negative control.
    private static void assertNoViolations(String ruleName,
                                           Set<String> subjects,
                                           Predicate<String> forbidden,
                                           Set<String> expectedViolators) {
        assertFalse(subjects.isEmpty(),
            ruleName + " inspected 0 classes. A rule with no subjects passes unconditionally and "
                + "proves nothing — check this test's package names against the source tree.");

        Map<String, Set<String>> found = new TreeMap<>();
        for (String subject : subjects) {
            Set<String> forbiddenDeps = new TreeSet<>();
            for (String dep : dependencies.getOrDefault(subject, Set.of())) {
                if (!dep.equals(subject) && forbidden.test(dep)) {
                    forbiddenDeps.add(dep);
                }
            }
            if (!forbiddenDeps.isEmpty()) {
                found.put(subject, forbiddenDeps);
            }
        }

        System.out.printf("[arch] %-48s %4d classes inspected, %d violator(s), %d exempt%n",
            ruleName, subjects.size(), found.size(), expectedViolators.size());

        assertEquals(new TreeSet<>(expectedViolators), new TreeSet<>(found.keySet()),
            () -> """
                %s

                Expected exactly the exempted classes to violate this rule.

                Violations found:
                %s
                A class listed here that is not exempt breaks the layering — fix the dependency.
                An exempt class missing from here means the exemption is stale — delete it.
                """.formatted(ruleName, renderViolations(found)));
    }

    // ------------------------------------------------------------------------------------------
    // Bytecode analysis
    // ------------------------------------------------------------------------------------------

    private static Set<String> referencedTypes(ClassModel model) {
        Set<String> references = new TreeSet<>();
        for (PoolEntry entry : model.constantPool()) {
            switch (entry) {
                // Superclass, interfaces, field/method owners, `new`, `checkcast`, catch types.
                case ClassEntry classEntry -> {
                    String internalName = classEntry.asInternalName();
                    if (!internalName.startsWith("[")) {
                        references.add(toFqn(internalName));
                    }
                }
                // Annotations and generic signatures live here and nowhere else.
                case Utf8Entry utf8Entry -> {
                    Matcher matcher = TYPE_DESCRIPTOR.matcher(utf8Entry.stringValue());
                    while (matcher.find()) {
                        references.add(toFqn(matcher.group(1)));
                    }
                }
                default -> {
                    // Numeric, string and method-handle entries carry no type reference.
                }
            }
        }
        return references;
    }

    private static String toFqn(String internalName) {
        return internalName.replace('/', '.');
    }

    private static Set<String> classesIn(String layer) {
        Set<String> members = new TreeSet<>();
        for (String owner : dependencies.keySet()) {
            if (owner.startsWith(layer + ".")) {
                members.add(owner);
            }
        }
        return members;
    }

    private static boolean isJdk(String type) {
        return type.startsWith("java.");
    }

    private static String packageOf(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot < 0 ? "" : fqn.substring(0, lastDot);
    }

    private static String topLevelPackageOf(String fqn) {
        String pkg = packageOf(fqn);
        int separator = pkg.indexOf('.', BASE.length() + 1);
        return separator < 0 ? pkg : pkg.substring(0, separator);
    }

    // ------------------------------------------------------------------------------------------
    // Package graph and cycle detection
    // ------------------------------------------------------------------------------------------

    /// Collapses the class graph into a package graph containing only nodes accepted by
    /// `include`. Self-edges, and edges leaving the selected scope, are dropped.
    private static Map<String, Set<String>> packageGraph(Predicate<String> include) {
        Map<String, Set<String>> graph = new TreeMap<>();

        for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
            String from = nodeFor(entry.getKey(), include);
            if (from == null) {
                continue;
            }
            Set<String> targets = graph.computeIfAbsent(from, ignored -> new TreeSet<>());
            for (String dependency : entry.getValue()) {
                String to = nodeFor(dependency, include);
                if (to != null && !to.equals(from)) {
                    targets.add(to);
                }
            }
        }

        graph.values().forEach(targets -> targets.retainAll(graph.keySet()));
        return graph;
    }

    /// Maps a class to the graph node owning it: the longest accepted prefix of its package.
    private static String nodeFor(String fqn, Predicate<String> include) {
        if (!fqn.startsWith(BASE + ".")) {
            return null;
        }
        String pkg = packageOf(fqn);
        while (pkg.startsWith(BASE)) {
            if (include.test(pkg)) {
                return pkg;
            }
            pkg = packageOf(pkg);
        }
        return null;
    }

    /// Tarjan's algorithm. Returns only components representing a real cycle: components with
    /// more than one node, since self-edges are dropped when the graph is built.
    private static List<List<String>> stronglyConnectedComponents(Map<String, Set<String>> graph) {
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> lowLink = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new LinkedHashSet<>();
        List<List<String>> components = new ArrayList<>();
        int[] counter = {0};

        for (String node : graph.keySet()) {
            if (!index.containsKey(node)) {
                strongConnect(node, graph, index, lowLink, stack, onStack, components, counter);
            }
        }
        return components;
    }

    private static void strongConnect(String node,
                                      Map<String, Set<String>> graph,
                                      Map<String, Integer> index,
                                      Map<String, Integer> lowLink,
                                      Deque<String> stack,
                                      Set<String> onStack,
                                      List<List<String>> components,
                                      int[] counter) {
        index.put(node, counter[0]);
        lowLink.put(node, counter[0]);
        counter[0]++;
        stack.push(node);
        onStack.add(node);

        for (String next : graph.getOrDefault(node, Set.of())) {
            if (!index.containsKey(next)) {
                strongConnect(next, graph, index, lowLink, stack, onStack, components, counter);
                lowLink.put(node, Math.min(lowLink.get(node), lowLink.get(next)));
            } else if (onStack.contains(next)) {
                lowLink.put(node, Math.min(lowLink.get(node), index.get(next)));
            }
        }

        if (lowLink.get(node).equals(index.get(node))) {
            List<String> component = new ArrayList<>();
            String member;
            do {
                member = stack.pop();
                onStack.remove(member);
                component.add(member);
            } while (!member.equals(node));

            if (component.size() > 1) {
                components.add(component);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Reporting
    // ------------------------------------------------------------------------------------------

    private static void assertAnchorPresent(String fqn) {
        assertTrue(dependencies.containsKey(fqn),
            "Anchor class " + fqn + " was not parsed. Either it moved or was renamed (update this "
                + "test), or the analyzer is no longer seeing the whole class tree.");
    }

    private static String renderViolations(Map<String, Set<String>> violations) {
        StringBuilder rendered = new StringBuilder();
        violations.forEach((owner, targets) -> {
            rendered.append("  ").append(owner).append('\n');
            targets.forEach(target -> rendered.append("      -> ").append(target).append('\n'));
        });
        return rendered.toString();
    }

    private static String renderCycles(List<List<String>> cycles) {
        StringBuilder rendered = new StringBuilder();
        for (List<String> cycle : cycles) {
            rendered.append("  ").append(String.join(" -> ", cycle))
                    .append(" -> ").append(cycle.getFirst()).append('\n');
        }
        return rendered.toString();
    }
}

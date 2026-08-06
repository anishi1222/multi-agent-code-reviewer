package dev.logicojp.reviewer.architecture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    /// The *driven* side of the port set — the only part of `application` that infrastructure is
    /// allowed to name (ADR-0006 D2). `application.port.inbound` is deliberately **not** included:
    /// an inbound port is implemented by `application` and called by `presentation`, so an
    /// infrastructure class that names one is either implementing a port it has no business
    /// implementing, or calling into the application from the wrong side.
    private static final String APPLICATION_PORT_OUTBOUND = BASE + ".application.port.outbound";
    private static final String INFRASTRUCTURE = BASE + ".infrastructure";
    private static final String PRESENTATION = BASE + ".presentation";
    private static final String SHARED = BASE + ".shared";

    /// The whole port set — both directions. Rule 4b constrains this, not just the outbound half:
    /// ADR-0007 D5 is about what a *port declaration* may depend on, and an inbound port carrying a
    /// masking dependency would be the same defect.
    private static final String APPLICATION_PORT = BASE + ".application.port";

    /// The configuration-defaults holder guarded by Rule 8 (ADR-0008).
    private static final String CONFIG_DEFAULTS = SHARED + ".ConfigDefaults";

    /// The masking helper guarded by Rule 4b (ADR-0007 D5).
    private static final String SENSITIVE_HEADER_MASKING = SHARED + ".SensitiveHeaderMasking";

    /// The five layers introduced by the rearchitecture. Everything else under [#BASE] is
    /// pre-migration code scheduled for deletion in t13.
    private static final List<String> NEW_LAYERS =
        List.of(DOMAIN, APPLICATION, INFRASTRUCTURE, PRESENTATION, SHARED);

    /// Matches a JVM field descriptor for a reference type, e.g. `Lio/micronaut/context/Foo;`.
    ///
    /// Scanning `Utf8Entry` values in addition to `ClassEntry` is required, not optional:
    /// annotation types (`@Singleton`, `@Inject`) and generic signatures appear *only* as UTF-8
    /// descriptors, never as `ClassEntry`. Detecting exactly those annotations is the whole point
    /// of Rule 1.
    ///
    /// The package separator is **required** (`(?:/…)+`, not `*`). Without it the sweep matched
    /// unqualified `L…;` runs inside ordinary string constants, and one such constant is emitted
    /// for every record: `javac` bootstraps `equals`/`hashCode`/`toString` via `ObjectMethods`
    /// with a `;`-joined list of component names. A record component containing an uppercase `L`
    /// that is not the final component therefore produced a phantom dependency — the name list
    /// `…;maxInstructionLines;enforcesCharset` yielded a "class" called `ines`. The trigger was
    /// positional (the last component has no trailing `;`), so merely reordering fields could
    /// break the build. Requiring a package separator removes the whole class of false positives
    /// and costs nothing: every framework annotation and signature is package-qualified, and
    /// `noProjectClassLivesInTheDefaultPackage` pins the only assumption this relies on.
    private static final Pattern TYPE_DESCRIPTOR =
        Pattern.compile("L([a-zA-Z_$][a-zA-Z0-9_$]*(?:/[a-zA-Z_$][a-zA-Z0-9_$]*)+);");

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

    @Test
    @DisplayName("Rule 0b: no class lives in the default package (pins the descriptor-scan assumption)")
    void noProjectClassLivesInTheDefaultPackage() {
        // TYPE_DESCRIPTOR requires a package separator, so an unqualified descriptor such as
        // `LFoo;` is not recognised as a type reference. That narrowing is only safe while every
        // compiled class is package-qualified. If a default-package class ever appears, this fails
        // and says so, rather than letting Rules 1-5 quietly stop seeing a dependency.
        Set<String> defaultPackage = new TreeSet<>();
        for (String owner : dependencies.keySet()) {
            if (owner.indexOf('.') < 0) {
                defaultPackage.add(owner);
            }
        }

        assertEquals(Set.of(), defaultPackage,
            "Classes in the default package are invisible to the UTF-8 descriptor scan. Either "
                + "move them into a package, or relax TYPE_DESCRIPTOR and re-verify that no record "
                + "component name reintroduces phantom dependencies.");
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
    @DisplayName("Rule 4: infrastructure reaches application only through its outbound ports")
    void infrastructureUsesApplicationPortsOnly() {
        // Micronaut @Factory / @Singleton classes form the composition root: binding a port to its
        // implementation necessarily names that implementation, and this is the one place in the
        // system where that is legitimate.
        //
        // Note for ADR-0006 D3 — relocating these to the composition root package would retire the
        // exemptions, but only `ApplicationPortFactory` is actually a Micronaut `@Factory`; the
        // other two carry config-mapping logic and an inbound-port implementation, which D1 forbids
        // the root from holding. See t16.1's artifact before acting on D3.
        Set<String> compositionRoot = Set.of(
            INFRASTRUCTURE + ".copilot.ApplicationPortFactory",
            INFRASTRUCTURE + ".copilot.ReviewContextFactory",
            INFRASTRUCTURE + ".copilot.ReviewOrchestratorFactory");

        Predicate<String> reachesApplicationOffPort =
            dep -> dep.startsWith(APPLICATION) && !dep.startsWith(APPLICATION_PORT_OUTBOUND);

        // Narrowed from `application.port` to `application.port.outbound` in t16.1.
        //
        // The wider form passed vacuously for the two defects ADR-0006 recorded as deviations #1
        // and #2: `infrastructure.auth.GitHubTokenResolver` implemented the *inbound*
        // `ResolveTokenPort`, and `infrastructure.copilot.SkillExecutor` implemented the *inbound*
        // `ExecuteSkillPort` — shadowing `application.skill.ExecuteSkillUseCase`, which the DI
        // container therefore never instantiated. Both were direction inversions that this rule was
        // supposed to catch and could not, because `application.port.inbound` sits underneath
        // `application.port`. Narrowing the prefix made both fail mechanically; they were then
        // fixed, not exempted.
        assertNoViolations("Rule 4 (infrastructure -> application.port.outbound only)",
            classesIn(INFRASTRUCTURE),
            reachesApplicationOffPort,
            withGeneratedBeanDefinitions(compositionRoot, reachesApplicationOffPort));
    }

    @Test
    @DisplayName("Rule 4b: no port declaration depends on shared.SensitiveHeaderMasking")
    void portsDoNotDependOnSensitiveHeaderMasking() {
        // ADR-0007 D5. A port is a *declaration of intent* — it says what the application needs from
        // the outside world. Masking is a property of a **sink** (a log line, a diagnostic dump), not
        // a property of the data. When a port type masks its own fields it makes three promises it
        // cannot keep:
        //
        //   1. It only guards `toString()`. Any consumer that reads the map — `get()`, `entrySet()`,
        //      a JSON serializer, a debugger — sees the raw value. Measured on this tree: the SDK's
        //      `McpHttpServerConfig.setHeaders` stores the map by `putfield` with no defensive copy,
        //      and neither `McpHttpServerConfig` nor `McpServerConfig` overrides `toString()`. The
        //      wrapper therefore protected *nothing* once the value crossed into the SDK.
        //   2. It is object-identity bound. Copy the map, stream it, rebuild it, and the guard is
        //      gone silently — no compiler or test notices.
        //   3. It makes the port's compile-time surface depend on a security helper, so the port can
        //      no longer be read as a pure contract.
        //
        // The sink owns masking instead: `logback.xml` / `logback-json.xml` mask on every appender,
        // by value shape (`MASK_PATTERN`) and by header name (`HEADER_MASK_PATTERN`), regardless of
        // which object produced the text. `SensitiveHeaderMaskingSinkCanaryTest` is the control.
        assertNoViolations("Rule 4b (application.port ⊥ shared.SensitiveHeaderMasking)",
            classesIn(APPLICATION_PORT),
            dep -> dep.equals(SENSITIVE_HEADER_MASKING)
                || dep.startsWith(SENSITIVE_HEADER_MASKING + "$"),
            Set.of());
    }

    @Test
    @DisplayName("Rule 4b control: the rule has subjects and can see a masking reference")
    void rule4bCanSeeAMaskingReference() throws IOException {
        // Once `McpServerSpec` was fixed, Rule 4b sits at 0 violators *and* 0 exemptions — the same
        // vacuity trap Rule 8 fell into. At that point its exact-match assertion observes nothing:
        // it would pass identically if `SENSITIVE_HEADER_MASKING` were misspelled, if
        // `APPLICATION_PORT` pointed at a package that does not exist, or if `referencedTypes` never
        // reported the dependency at all. This control pins both halves.
        //
        // Half 1 — the rule has subjects. `classesIn` returns the empty set for a prefix that
        // matches nothing, and `assertNoViolations` is perfectly happy inspecting zero classes.
        // Rule 0 guards *parsing*, not this prefix.
        Set<String> subjects = classesIn(APPLICATION_PORT);
        assertFalse(subjects.isEmpty(), () -> """
            Rule 4b inspected zero classes: nothing under `%s` was found in %s.

            The rule is vacuous — it cannot fail. Either the package was renamed and the constant \
            was not updated, or the ports moved. Fix the prefix; do not delete this control.
            """.formatted(APPLICATION_PORT, CLASSES));

        // Half 2 — a masking reference is actually detectable. The fixture calls a surviving
        // `SensitiveHeaderMasking` method and nothing else from that class, reproducing the exact
        // shape of the violation ADR-0007 D5 removed (`McpServerSpec` called `wrapHeaders`).
        String fixture = "LayerDependencyRulesTest$MaskingReferenceProbe.class";
        byte[] bytes;
        try (InputStream in = LayerDependencyRulesTest.class.getResourceAsStream(fixture)) {
            assertNotNull(in, "Rule 4b control fixture not found on the test classpath: " + fixture);
            bytes = in.readAllBytes();
        }

        Set<String> references = referencedTypes(ClassFile.of().parse(bytes));

        System.out.printf("[arch] %-48s %d subject(s), fixture references %s%n",
            "Rule 4b control (masking reference is detectable)", subjects.size(),
            references.contains(SENSITIVE_HEADER_MASKING)
                ? SENSITIVE_HEADER_MASKING : "NOTHING — Rule 4b IS BLIND");

        assertTrue(MaskingReferenceProbe.probe("Authorization"),
            "Fixture must genuinely call the helper, not merely mention it.");
        assertTrue(references.contains(SENSITIVE_HEADER_MASKING), () -> """
            Rule 4b cannot detect a call to `%s`.

            The fixture calls it and nothing else from that class, yet the type does not appear in \
            its constant pool. Rule 4b is therefore vacuous: it would pass even with a live \
            violator under `application.port`.

            Fixture references: %s
            """.formatted(SENSITIVE_HEADER_MASKING, references));
    }

    /// Fixture for [#rule4bCanSeeAMaskingReference]. Reproduces the shape of the ADR-0007 D5
    /// violation: a class calling [dev.logicojp.reviewer.shared.SensitiveHeaderMasking] with no
    /// other reference to it, so the only thing that can put `shared.SensitiveHeaderMasking` in
    /// this class's constant pool is that call.
    ///
    /// It lives in the test tree, so it is never a Rule 4b subject: the analyzer walks
    /// `target/classes` only.
    static final class MaskingReferenceProbe {

        private MaskingReferenceProbe() {
        }

        static boolean probe(String headerName) {
            return dev.logicojp.reviewer.shared.SensitiveHeaderMasking.isSensitiveHeaderName(headerName);
        }
    }

    @Test
    @DisplayName("Rule 5: application depends on neither infrastructure nor presentation")
    void applicationDependsOnNeitherAdapterLayer() {
        assertNoViolations("Rule 5 (application is adapter-agnostic)", classesIn(APPLICATION),
            dep -> dep.startsWith(INFRASTRUCTURE) || dep.startsWith(PRESENTATION),
            Set.of());
    }

    @Test
    @DisplayName("Rule 5b: presentation depends on no infrastructure")
    void presentationDependsOnNoInfrastructure() {
        // The `presentation ⊥ infrastructure` edge of t4 §2 had no rule at all until t13.1.
        //
        // It is genuinely independent of the rules around it, and the gap was easy to miss
        // precisely because those rules *mention* both layers:
        //   - Rule 3 proves presentation is a leaf — that nothing depends *on* presentation.
        //     It says nothing about what presentation depends on.
        //   - Rule 5 names both adapter layers, but constrains `application`, not presentation.
        // So the two directions that the driving adapter must not take — into the driven
        // adapter — were unguarded, and t13 found two live violations by hand rather than by
        // build failure (they were fixed by extracting `shared.LogValueSanitizer` and
        // `presentation.CliSecurityAudit`).
        //
        // Rules 4 + 5 + 5b together now close the adapter matrix: infrastructure may only
        // reach application through ports, application may reach neither adapter, and
        // presentation may not reach infrastructure. The CLI talks to the outside world only
        // through inbound ports, so the DI container — not the driving adapter — chooses the
        // implementations.
        //
        // The exemption set is empty and must stay that way. A presentation class that needs
        // something from infrastructure needs a port instead.
        assertNoViolations("Rule 5b (presentation ⊥ infrastructure)", classesIn(PRESENTATION),
            dep -> dep.startsWith(INFRASTRUCTURE),
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
    @DisplayName("Rule 6 scope: every package under the base package is one of the five layers")
    void everyPackageBelongsToALayer() {
        // The pre-migration tree is gone (t13), so Rules 6a/6b — which slice by layer — now cover
        // the whole codebase. That coverage is only trustworthy if it is *proved* rather than
        // assumed: this guard fails the moment a package appears outside the five layers, which
        // would otherwise silently escape both cycle rules.
        //
        // This replaces the former `legacyPackagesAreExplicitlyOutOfCycleScope` self-destruct,
        // whose job was to fail once the legacy tree was deleted. It has now fired and been
        // removed, as its own failure message instructed.
        Set<String> allowedAtRoot = Set.of(BASE + ".ReviewApp", BASE + ".$ReviewApp$Definition");

        Map<String, Integer> strays = new TreeMap<>();
        List<String> rootDwellers = new ArrayList<>();

        for (String owner : dependencies.keySet()) {
            if (!owner.startsWith(BASE + ".")) {
                continue;
            }
            String topLevel = topLevelPackageOf(owner);
            if (NEW_LAYERS.contains(topLevel)) {
                continue;
            }
            if (topLevel.equals(BASE)) {
                // A class sitting directly in the base package belongs to no layer, so only the
                // two entries exempted by Rule 3 may live there. Nested types (ReviewApp$Xxx)
                // are part of their enclosing class and inherit its exemption.
                String enclosing = owner.contains("$") ? owner.substring(0, owner.indexOf('$')) : owner;
                if (!allowedAtRoot.contains(owner) && !allowedAtRoot.contains(enclosing)) {
                    rootDwellers.add(owner);
                }
                continue;
            }
            strays.merge(topLevel, 1, Integer::sum);
        }

        System.out.printf("[arch] Rule 6 scope: %d layer(s) cover every package under %s%n",
            NEW_LAYERS.size(), BASE);

        assertTrue(strays.isEmpty(),
            () -> "Package(s) outside the five layers escape Rules 6a/6b — add them to a layer:\n"
                + strays.entrySet().stream()
                    .map(e -> "  " + e.getKey() + " (" + e.getValue() + " classes)")
                    .collect(Collectors.joining("\n")));

        assertTrue(rootDwellers.isEmpty(),
            () -> "Class(es) in the base package " + BASE + " belong to no layer: " + rootDwellers
                + ". Only " + allowedAtRoot + " may live there.");
    }

    // ------------------------------------------------------------------------------------------
    // Rule 7 — RESERVED, not implemented.
    //
    // t24-architect.md §5 proposes a "one simple name per type" rule (group `dependencies.keySet()`
    // by simple name, assert every group has size 1). The number is held here rather than reused so
    // that "Rule 8" keeps the identity it already has in t24-architect.md §5A.4, decisions.md and
    // three role inboxes. ADR-0006 L143's `5b` suffix convention governs *insertions* between
    // existing rules — it exists to protect the 6a/6b pair — so appending 8 is compliant.
    // ------------------------------------------------------------------------------------------

    // ------------------------------------------------------------------------------------------
    // Rule 8 — domain reads no configuration default directly (ADR-0008)
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 8: domain reads no configuration default from shared.ConfigDefaults")
    void domainReadsNoConfigurationDefault() {
        // ADR-0008: a control's scope of application must be visible at its call site.
        //
        // F4 was the ninth instance of that pattern. Two gates guarded one resource -- skill
        // parameter length -- with different quantities from different sources: one read the
        // *configured* value and skipped with a warning, the other read the *hardcoded*
        // `ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH` and threw. Nothing at either call site
        // revealed that the other gate existed, or that they could disagree. The remedy was to
        // inject the resolved budget inward as a pure value (`shared.SkillBudget`), so the number
        // in force arrives through the seam instead of being reached for.
        //
        // ## Why this rule names a type, not a field
        //
        // `SKILL_MAX_PARAMETER_VALUE_LENGTH` is a `public static final int` -- a JLS §4.12.4
        // *constant variable*. JLS §13.1 requires the compiler to resolve it at compile time, so
        // the read compiles to `sipush 10000` and **no `Fieldref` is ever emitted**. Field-level
        // precision is therefore not merely awkward here, it is unavailable: there is nothing in
        // the bytecode naming the field. The enforceable unit is the type.
        //
        // That makes the rule *wider* than the sentence it enforces -- it also forbids `domain`
        // from calling the pure helpers (`defaultIfBlank`, `defaultIfNonPositive`). That widening
        // is deliberate and currently free (0 violators): a default belongs at the configuration
        // seam, and `infrastructure.config` + `shared` -- the only two layers that legitimately
        // materialise defaults -- retain full access. Stated plainly so the rule is not read as
        // narrower than it is.
        //
        // ## What this rule does not touch
        //
        // `shared.PromptBudget` and `shared.SkillBudget` are pure value objects injected *inward*.
        // `domain` depending on them is the remedy, not the defect, and Rule 1 already permits it.
        // Only `ConfigDefaults` itself is forbidden. If this rule ever flags a budget value object,
        // the rule is wrong -- not the value object.
        assertNoViolations("Rule 8 (domain ⊥ shared.ConfigDefaults)", classesIn(DOMAIN),
            dep -> dep.equals(CONFIG_DEFAULTS),
            Set.of());
    }

    @Test
    @DisplayName("Rule 8 control: an inlined constant read is still visible to the analyzer")
    void rule8DetectsAnInlinedConstantRead() throws IOException {
        // Rule 8 is the one rule in this file that its own exemption mechanism cannot prove.
        //
        // Rules 3 and 4 carry non-empty exemption sets, so `assertNoViolations`' exact-equality
        // check observes them firing on every run. Rule 8 has 0 violators *and* 0 exemptions, so
        // that check observes nothing: it would pass identically if the predicate were broken, if
        // `CONFIG_DEFAULTS` were misspelled, or if the constant-pool reference did not exist at all.
        // Shipping it without this control would reproduce, inside the enforcement mechanism, the
        // very defect ADR-0008 exists to prevent -- a control whose scope of application is
        // invisible at its call site.
        //
        // The detectability Rule 8 depends on is real but *not* guaranteed by the JVM spec: javac
        // records the compile-time dependency as an unreferenced `CONSTANT_Class` entry even though
        // it inlined the value and emitted no instruction that uses it. That is compiler behaviour,
        // not language semantics. This control pins it: if a toolchain change ever elides the ghost
        // entry, this test goes red and says Rule 8 has gone blind -- instead of Rule 8 passing
        // green forever while enforcing nothing.
        //
        // Known blind spot, measured on JDK 28 and stated rather than papered over: a constant read
        // in a `case` label (`case ConfigDefaults.SOME_MAX ->`) leaves *no* trace in the reading
        // class's constant pool. Rule 8 cannot see that shape. It is not a budget-gate idiom
        // (budgets are compared, not switched on), so the gap is accepted and recorded here.
        String fixture = "LayerDependencyRulesTest$InlinedConstantReadProbe.class";
        byte[] bytes;
        try (InputStream in = LayerDependencyRulesTest.class.getResourceAsStream(fixture)) {
            assertNotNull(in, "Rule 8 control fixture not found on the test classpath: " + fixture);
            bytes = in.readAllBytes();
        }

        Set<String> references = referencedTypes(ClassFile.of().parse(bytes));

        System.out.printf("[arch] %-48s fixture references %s%n",
            "Rule 8 control (inlined constant is detectable)",
            references.contains(CONFIG_DEFAULTS) ? CONFIG_DEFAULTS : "NOTHING — Rule 8 IS BLIND");

        assertTrue(InlinedConstantReadProbe.overBudget(Integer.MAX_VALUE),
            "Fixture must genuinely read the constant, not merely mention it.");
        assertTrue(references.contains(CONFIG_DEFAULTS), () -> """
            Rule 8 cannot detect a direct read of an inlined `static final` constant.

            The fixture reads `ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH` and nothing else \
            from that class, yet %s does not appear in its constant pool. Rule 8 is therefore \
            vacuous: it would pass even with a live violator in `domain`.

            Fix Rule 8's detection strategy (a source-text scan of `src/main/java/**/domain/**` is \
            the fallback) -- do not delete this control.

            Fixture references: %s
            """.formatted(CONFIG_DEFAULTS, references));
    }

    /// Fixture for [#rule8DetectsAnInlinedConstantRead]. Reproduces F4's original shape exactly: a
    /// class reading the hardcoded limit straight off [ConfigDefaults], with no other reference to
    /// it — no method call, no field of that type — so the *only* thing that can put
    /// `shared.ConfigDefaults` in this class's constant pool is the inlined constant read.
    ///
    /// It lives in the test tree, so it is never a Rule 8 subject: the analyzer walks
    /// `target/classes` only.
    static final class InlinedConstantReadProbe {

        private InlinedConstantReadProbe() {
        }

        static boolean overBudget(int length) {
            return length > dev.logicojp.reviewer.shared.ConfigDefaults.SKILL_MAX_PARAMETER_VALUE_LENGTH;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Rule assertion helper
    // ------------------------------------------------------------------------------------------

    /// Expands a set of exempt, hand-written classes to include the Micronaut bean-definition
    /// classes generated *from* them — `$ApplicationPortFactory$ExecuteSkillPort5$Definition` and
    /// friends.
    ///
    /// Narrowing Rule 4 to `application.port.outbound` brought these into scope for the first time:
    /// a factory method returning an inbound port produces a `$…$Definition` naming that port, so
    /// the generated mirror violates the rule for exactly the reason its source class is exempt.
    /// Listing them by hand is possible but hostile — the numeric infix is the factory method's
    /// declaration index, so inserting a method silently renames several of them.
    ///
    /// This is **not** the blanket "skip anything containing `$`" exclusion that an earlier revision
    /// used and that Rule 3's comment warns about. Two conditions must both hold, and together they
    /// make the expansion provably non-loosening:
    ///
    /// 1. the generated class's declaring source class is itself already exempt, and
    /// 2. its forbidden dependencies are a **subset** of the source's forbidden dependencies.
    ///
    /// So a generated class can only ever inherit an exemption that a human already justified for
    /// the code it was generated from. A generated class whose source is *not* exempt still fails
    /// the rule — which is what caught `$GitHubTokenResolver$Definition` alongside
    /// `GitHubTokenResolver` in t16.1 — and a generated class that somehow acquired a dependency its
    /// source does not have also still fails.
    private static Set<String> withGeneratedBeanDefinitions(Set<String> exemptSources,
                                                             Predicate<String> forbidden) {
        Set<String> expanded = new TreeSet<>(exemptSources);
        Set<String> derived = new TreeSet<>();

        for (String candidate : dependencies.keySet()) {
            String declaring = declaringClassOfGenerated(candidate);
            if (declaring == null || !exemptSources.contains(declaring)) {
                continue;
            }
            Set<String> candidateDeps = forbiddenDepsOf(candidate, forbidden);
            // Only classes that actually violate may be exempted: `assertNoViolations` requires the
            // exemption set to equal the violator set exactly, so listing a clean class would
            // register as a stale exemption and fail the build.
            if (candidateDeps.isEmpty()) {
                continue;
            }
            if (forbiddenDepsOf(declaring, forbidden).containsAll(candidateDeps)) {
                expanded.add(candidate);
                derived.add(candidate.substring(candidate.lastIndexOf('.') + 1));
            }
        }

        if (!derived.isEmpty()) {
            System.out.printf("[arch] Rule 4: %d generated bean definition(s) inherit a "
                + "composition-root exemption: %s%n", derived.size(), String.join(", ", derived));
        }
        return Set.copyOf(expanded);
    }

    /// Maps a Micronaut-generated class to the class it was generated from, or `null` when the name
    /// is not of that shape. `a.b.$Foo$Definition` and `a.b.$Foo$Bar5$Definition` both map to
    /// `a.b.Foo`.
    private static String declaringClassOfGenerated(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        String simpleName = lastDot < 0 ? fqn : fqn.substring(lastDot + 1);
        if (!simpleName.startsWith("$")) {
            return null;
        }
        int end = simpleName.indexOf('$', 1);
        if (end < 0) {
            return null;
        }
        String declaringSimpleName = simpleName.substring(1, end);
        return lastDot < 0 ? declaringSimpleName : fqn.substring(0, lastDot + 1) + declaringSimpleName;
    }

    private static Set<String> forbiddenDepsOf(String owner, Predicate<String> forbidden) {
        Set<String> forbiddenDeps = new TreeSet<>();
        for (String dep : dependencies.getOrDefault(owner, Set.of())) {
            if (!dep.equals(owner) && forbidden.test(dep)) {
                forbiddenDeps.add(dep);
            }
        }
        return forbiddenDeps;
    }

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

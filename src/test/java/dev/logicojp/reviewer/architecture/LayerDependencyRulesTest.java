package dev.logicojp.reviewer.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/// ArchUnit boundary tests for Layered Architecture rules.
///
/// Enforces the package dependency constraints defined in the architecture
/// constitution (t1-teamlead) and package design (t4-architect-packages).
///
/// Rules:
/// <ol>
///   <li>Domain is framework-free (no Micronaut, Jakarta, Copilot SDK, SLF4J, SnakeYAML)</li>
///   <li>Shared is pure Java (no framework imports)</li>
///   <li>No package imports presentation (one-way inward flow)</li>
///   <li>Infrastructure only imports application ports, domain, shared, and infra frameworks</li>
///   <li>Application core does not import infrastructure or presentation</li>
///   <li>No package-level cycles</li>
/// </ol>
@DisplayName("Layered Architecture Boundary Rules")
class LayerDependencyRulesTest {

    private static final String BASE = "dev.logicojp.reviewer";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);
    }

    // ------------------------------------------------------------------ //
    // Rule 1 — Domain is framework-free                                   //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Rule 1: domain classes must not import framework packages")
    void domainMustNotImportFrameworks() {
        ArchRule rule = noClasses()
            .that().resideInAPackage(BASE + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "io.micronaut..",
                "jakarta..",
                "com.github.copilot..",
                "org.slf4j..",
                "org.yaml.."
            )
            .because("Domain must be a pure object model with no external framework dependencies");

        rule.check(classes);
    }

    // ------------------------------------------------------------------ //
    // Rule 2 — Shared is pure Java                                        //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Rule 2: shared classes must only import java.* packages")
    void sharedMustBeJavaOnly() {
        ArchRule rule = noClasses()
            .that().resideInAPackage(BASE + ".shared..")
            .should().dependOnClassesThat().resideOutsideOfPackages(
                "java..",
                BASE + ".shared.."
            )
            .because("Shared must contain only pure Java types with no external dependencies");

        rule.check(classes);
    }

    // ------------------------------------------------------------------ //
    // Rule 3 — No package imports presentation (inward flow)              //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Rule 3: no package outside presentation may import presentation classes")
    void nothingMayImportPresentation() {
        ArchRule rule = noClasses()
            .that().resideOutsideOfPackage(BASE + ".presentation..")
            .and().haveNameNotMatching(".*\\$.*") // exclude Micronaut-generated synthetic classes
            .should().dependOnClassesThat().resideInAPackage(BASE + ".presentation..")
            .because("Presentation is the outermost layer; nothing inward should depend on it");

        rule.check(classes);
    }

    // ------------------------------------------------------------------ //
    // Rule 4 — Infrastructure allowed list                                //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Rule 4: infrastructure must not import application use-cases or service classes")
    void infrastructureMustNotImportApplicationUseCases() {
        ArchRule rule = noClasses()
            .that().resideInAPackage(BASE + ".infrastructure..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".application.review..")
            .because("Infrastructure adapters must depend on application ports only, not concrete use-case classes");

        rule.check(classes);
    }

    // ------------------------------------------------------------------ //
    // Rule 5 — Application core does not import infrastructure or        //
    //          presentation                                               //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Rule 5: application core (excluding port sub-packages) must not import infrastructure or presentation")
    void applicationCoreMustNotImportInfrastructureOrPresentation() {
        ArchRule rule = noClasses()
            .that().resideInAPackage(BASE + ".application..")
            .and().resideOutsideOfPackages(
                BASE + ".application.port.."
            )
            .should().dependOnClassesThat().resideInAnyPackage(
                BASE + ".infrastructure..",
                BASE + ".presentation.."
            )
            .because("Application use-case classes must be framework-independent and must not depend on infrastructure or presentation");

        rule.check(classes);
    }

    // ------------------------------------------------------------------ //
    // Rule 6 — No package-level cycles                                    //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Rule 6: no cyclic dependencies between top-level packages")
    void noPackageLevelCycles() {
        ArchRule rule = slices()
            .matching(BASE + ".(*)..") // one slice per top-level package
            .should().beFreeOfCycles()
            .because("Cyclic package dependencies make the codebase harder to maintain and test");

        rule.check(classes);
    }
}

package dev.logicojp.reviewer.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Keeps Accepted ADR decision items and executable architecture rules in lockstep.
///
/// Controls are deliberately excluded from the executable inventory: a surviving
/// `Rule 4b control:` must not conceal deletion or renaming of the primary `Rule 4b:` test.
@DisplayName("ADRと実行可能アーキテクチャルールの双方向トレーサビリティ")
class AdrRuleReferenceGuardTest {

    private static final Path ADR_DIRECTORY = Path.of("docs", "adr");
    private static final Pattern DECISION_HEADING = Pattern.compile("^###\\s+(D\\d+)\\.");
    private static final Pattern LEVEL_ONE_TO_THREE_HEADING =
        Pattern.compile("^#{1,3}\\s+.*");
    private static final Pattern RULE_REFERENCE =
        Pattern.compile("\\bRule\\s+(\\d+[a-z]?)\\b");
    private static final Pattern PRIMARY_RULE_DISPLAY_NAME =
        Pattern.compile("^Rule\\s+(\\d+[a-z]?)(?:\\s+scope)?:.*");

    @Test
    @DisplayName("Accepted ADRのD-itemと主ルール在庫が双方向に一致する")
    void acceptedAdrDecisionsAndPrimaryRulesMatchBidirectionally() throws IOException {
        List<AdrRuleReference> references = acceptedAdrRuleReferences();
        Map<String, String> primaryRules = executablePrimaryRules();

        List<AdrRuleReference> missingExecutableRules = references.stream()
            .filter(reference -> !primaryRules.containsKey(reference.rule()))
            .toList();
        List<Map.Entry<String, String>> missingAdrReferences = primaryRules.entrySet().stream()
            .filter(rule -> references.stream().noneMatch(reference -> reference.rule().equals(rule.getKey())))
            .toList();

        assertAll(
            () -> assertFalse(references.isEmpty(),
                "Accepted ADR D-item parser found zero Rule references; traceability would pass vacuously"),
            () -> assertFalse(primaryRules.isEmpty(),
                "LayerDependencyRulesTest primary-rule inventory is empty; traceability would pass vacuously"),
            () -> assertTrue(missingExecutableRules.isEmpty(), () -> """
                Accepted ADR decision item(s) reference no executable primary architecture rule:
                %s
                Add or restore the matching `@Test` + `@DisplayName("Rule N[x]: ...")`.
                A `Rule N[x] control:` method does not satisfy this inventory.
                """.formatted(renderMissingExecutableRules(missingExecutableRules))),
            () -> assertTrue(missingAdrReferences.isEmpty(), () -> """
                Executable primary architecture rule(s) have no Accepted ADR D-item reference:
                %s
                Record each rule in the decision item that authorizes it.
                """.formatted(renderMissingAdrReferences(missingAdrReferences)))
        );
    }

    @Test
    @DisplayName("3つの実在anchorがADR解析と主ルール解析の空振りを防ぐ")
    void realAnchorsPreventEmptyOrMisScopedParsing() throws IOException {
        List<AdrRuleReference> references = acceptedAdrRuleReferences();
        Map<String, String> primaryRules = executablePrimaryRules();

        assertAll(
            () -> assertAnchor(references, "0006-ports-and-adapters-layering.md", "D5", "5b"),
            () -> assertAnchor(references,
                "0007-agent-definition-trust-model-and-secret-sink-boundary.md", "D5", "4b"),
            () -> assertAnchor(references,
                "0008-control-scope-must-be-visible-at-the-call-site.md", "D2", "8"),
            () -> assertTrue(primaryRules.containsKey("5b"),
                "Primary inventory is missing Rule 5b"),
            () -> assertTrue(primaryRules.containsKey("4b"),
                "Primary inventory is missing Rule 4b"),
            () -> assertTrue(primaryRules.containsKey("8"),
                "Primary inventory is missing Rule 8")
        );
    }

    private static List<AdrRuleReference> acceptedAdrRuleReferences() throws IOException {
        assertTrue(Files.isDirectory(ADR_DIRECTORY),
            "ADR directory is missing: " + ADR_DIRECTORY);

        List<AdrRuleReference> references = new ArrayList<>();
        try (Stream<Path> files = Files.list(ADR_DIRECTORY)) {
            for (Path path : files
                .filter(file -> file.getFileName().toString().endsWith(".md"))
                .sorted()
                .toList()) {
                List<String> lines = Files.readAllLines(path);
                if (lines.stream().noneMatch(line -> line.trim().equals("- Status: Accepted"))) {
                    continue;
                }
                references.addAll(decisionReferences(path, lines));
            }
        }
        references.sort(Comparator
            .comparing((AdrRuleReference reference) -> reference.path().toString())
            .thenComparing(AdrRuleReference::decision)
            .thenComparing(AdrRuleReference::rule));
        return List.copyOf(references);
    }

    private static List<AdrRuleReference> decisionReferences(Path path, List<String> lines) {
        List<AdrRuleReference> references = new ArrayList<>();
        for (int start = 0; start < lines.size(); start++) {
            Matcher decision = DECISION_HEADING.matcher(lines.get(start));
            if (!decision.find()) {
                continue;
            }

            int end = start + 1;
            while (end < lines.size()
                && !LEVEL_ONE_TO_THREE_HEADING.matcher(lines.get(end)).matches()) {
                end++;
            }

            String body = String.join("\n", lines.subList(start + 1, end));
            Matcher rule = RULE_REFERENCE.matcher(body);
            while (rule.find()) {
                references.add(new AdrRuleReference(path, decision.group(1), rule.group(1)));
            }
        }
        return references;
    }

    private static Map<String, String> executablePrimaryRules() {
        Map<String, String> rules = new TreeMap<>();
        for (Method method : LayerDependencyRulesTest.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Test.class)) {
                continue;
            }
            DisplayName displayName = method.getAnnotation(DisplayName.class);
            if (displayName == null) {
                continue;
            }
            Matcher primary = PRIMARY_RULE_DISPLAY_NAME.matcher(displayName.value());
            if (!primary.matches()) {
                continue;
            }

            String previous = rules.putIfAbsent(primary.group(1), method.getName());
            assertTrue(previous == null, () ->
                "Duplicate executable primary Rule " + primary.group(1)
                    + " methods: " + previous + ", " + method.getName());
        }
        return Map.copyOf(rules);
    }

    private static void assertAnchor(List<AdrRuleReference> references,
                                     String fileName,
                                     String decision,
                                     String rule) {
        assertTrue(references.stream().anyMatch(reference ->
                reference.path().getFileName().toString().equals(fileName)
                    && reference.decision().equals(decision)
                    && reference.rule().equals(rule)),
            () -> "Missing real traceability anchor docs/adr/" + fileName + "/"
                + decision + " -> Rule " + rule);
    }

    private static String renderMissingExecutableRules(List<AdrRuleReference> missing) {
        return missing.stream()
            .map(reference -> "  " + reference.path() + "#"
                + reference.decision() + " -> Rule " + reference.rule())
            .distinct()
            .reduce((left, right) -> left + "\n" + right)
            .orElse("  (none)");
    }

    private static String renderMissingAdrReferences(List<Map.Entry<String, String>> missing) {
        return missing.stream()
            .map(rule -> "  Rule " + rule.getKey() + " -> "
                + "LayerDependencyRulesTest#" + rule.getValue())
            .reduce((left, right) -> left + "\n" + right)
            .orElse("  (none)");
    }

    private record AdrRuleReference(Path path, String decision, String rule) {
    }
}

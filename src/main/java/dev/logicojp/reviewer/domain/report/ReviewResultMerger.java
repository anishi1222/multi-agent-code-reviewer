package dev.logicojp.reviewer.domain.report;

import dev.logicojp.reviewer.domain.agent.AgentConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Merges multiple review results from the same agent (multi-pass reviews)
/// into a single consolidated {@link ReviewResult}.
///
/// SLF4J removed — multi-pass merge warnings are surfaced in the returned
/// {@link ReviewResult}'s {@code errorMessage} when all passes fail, rather than logged.
///
/// Pure {@code java.*} — no framework dependencies.
public final class ReviewResultMerger {

    @FunctionalInterface
    public interface FindingBlockExtractor {
        List<ReviewFindingParser.FindingBlock> extract(String content);
    }

    @FunctionalInterface
    public interface FindingKeyResolver {
        String resolve(ReviewFindingParser.FindingBlock block,
                       AggregatedFinding.NormalizedFinding normalized);
    }

    @FunctionalInterface
    public interface MergedContentFormatter {
        String format(Map<String, AggregatedFinding> aggregatedFindings, int totalPasses, int failedPasses);
    }

    private ReviewResultMerger() {
    }

    /// Merges a flat list of review results (potentially multiple per agent)
    /// into a list with exactly one result per agent.
    public static List<ReviewResult> mergeByAgent(List<ReviewResult> results) {
        return mergeByAgent(
            results,
            ReviewFindingParser::extractFindingBlocks,
            (block, normalized) -> ReviewFindingParser.findingKeyFromNormalized(normalized, block.body()),
            ReviewMergedContentFormatter::format
        );
    }

    static List<ReviewResult> mergeByAgent(List<ReviewResult> results,
                                           FindingBlockExtractor findingBlockExtractor,
                                           FindingKeyResolver findingKeyResolver,
                                           MergedContentFormatter mergedContentFormatter) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        Map<String, List<ReviewResult>> byAgent = new LinkedHashMap<>();
        for (ReviewResult result : results) {
            String agentName = result.agentConfig() != null
                ? result.agentConfig().name()
                : "__unknown__";
            byAgent.computeIfAbsent(agentName, _ -> new ArrayList<>()).add(result);
        }

        List<ReviewResult> merged = new ArrayList<>(byAgent.size());
        for (var entry : byAgent.entrySet()) {
            List<ReviewResult> agentResults = entry.getValue();
            if (agentResults.size() == 1) {
                merged.add(normalizeSingleResult(
                    agentResults.getFirst(),
                    findingBlockExtractor,
                    findingKeyResolver,
                    mergedContentFormatter
                ));
            } else {
                merged.add(mergeAgentResults(
                    agentResults,
                    findingBlockExtractor,
                    findingKeyResolver,
                    mergedContentFormatter
                ));
            }
        }

        return merged;
    }

    private static ReviewResult normalizeSingleResult(ReviewResult result,
                                                      FindingBlockExtractor findingBlockExtractor,
                                                      FindingKeyResolver findingKeyResolver,
                                                      MergedContentFormatter mergedContentFormatter) {
        if (result == null || !result.success()) {
            return result;
        }
        String content = result.content();
        if (content == null || content.isBlank()) {
            return result;
        }

        Map<String, AggregatedFinding> lightweightFindings = collectSinglePassFindings(content, findingBlockExtractor);
        String normalizedContent = mergedContentFormatter.format(lightweightFindings, 1, 0);
        return ReviewResult.builder()
            .agentConfig(result.agentConfig())
            .repository(result.repository())
            .content(normalizedContent)
            .success(true)
            .errorMessage(result.errorMessage())
            .timestamp(result.timestamp())
            .build();
    }

    private static Map<String, AggregatedFinding> collectSinglePassFindings(
        String content,
        FindingBlockExtractor findingBlockExtractor
    ) {
        List<ReviewFindingParser.FindingBlock> blocks = findingBlockExtractor.extract(content);
        if (blocks.isEmpty()) {
            String normalized = ReviewFindingSimilarity.normalizeText(content);
            if (normalized.isEmpty()) {
                return Map.of();
            }
            return Map.of("fallback|" + normalized,
                AggregatedFinding.fallbackWithNormalized(content, normalized, 1));
        }

        Map<String, AggregatedFinding> findings = new LinkedHashMap<>(blocks.size());
        int order = 0;
        for (ReviewFindingParser.FindingBlock block : blocks) {
            findings.put("single|" + order++, AggregatedFinding.lightweight(block, 1));
        }
        return findings;
    }

    private static ReviewResult mergeAgentResults(List<ReviewResult> agentResults,
                                                  FindingBlockExtractor findingBlockExtractor,
                                                  FindingKeyResolver findingKeyResolver,
                                                  MergedContentFormatter mergedContentFormatter) {
        AgentConfig config = agentResults.getFirst().agentConfig();
        String repository = agentResults.getFirst().repository();

        List<ReviewResult> successful = agentResults.stream()
            .filter(ReviewResult::success)
            .toList();

        if (successful.isEmpty()) {
            // All passes failed — return last result as-is (error propagation)
            return agentResults.getLast();
        }

        FindingIndex findingIndex = collectFindings(successful, findingBlockExtractor, findingKeyResolver);

        int failedCount = agentResults.size() - successful.size();
        String content = mergedContentFormatter.format(findingIndex.findings(), agentResults.size(), failedCount);

        return ReviewResult.builder()
            .agentConfig(config)
            .repository(repository)
            .content(content)
            .success(true)
            .build();
    }

    private static FindingIndex collectFindings(List<ReviewResult> successful,
                                                FindingBlockExtractor findingBlockExtractor,
                                                FindingKeyResolver findingKeyResolver) {
        FindingIndex findingIndex = new FindingIndex(findingKeyResolver);
        Set<String> fallbackPassContents = new LinkedHashSet<>();

        for (int i = 0; i < successful.size(); i++) {
            String content = successful.get(i).content();
            if (content == null || content.isBlank()) {
                continue;
            }
            collectPassFindings(content, i + 1, findingBlockExtractor, findingIndex, fallbackPassContents);
        }
        return findingIndex;
    }

    private static void collectPassFindings(String content,
                                            int passNumber,
                                            FindingBlockExtractor findingBlockExtractor,
                                            FindingIndex findingIndex,
                                            Set<String> fallbackPassContents) {
        List<ReviewFindingParser.FindingBlock> blocks = findingBlockExtractor.extract(content);
        if (blocks.isEmpty()) {
            String normalized = ReviewFindingSimilarity.normalizeText(content);
            if (!normalized.isEmpty() && fallbackPassContents.add(normalized)) {
                findingIndex.putIfAbsent(
                    "fallback|" + normalized,
                    AggregatedFinding.fallbackWithNormalized(content, normalized, passNumber)
                );
            }
            return;
        }

        for (ReviewFindingParser.FindingBlock block : blocks) {
            findingIndex.addOrMerge(block, passNumber);
        }
    }

    private static final class FindingIndex {
        private final Map<String, AggregatedFinding> findings = new LinkedHashMap<>();
        private final Map<String, LinkedHashSet<Integer>> passNumbersByKey = new LinkedHashMap<>();
        private final FindingKeyResolver findingKeyResolver;

        private FindingIndex(FindingKeyResolver findingKeyResolver) {
            this.findingKeyResolver = findingKeyResolver;
        }

        Map<String, AggregatedFinding> findings() {
            Map<String, AggregatedFinding> materialized = new LinkedHashMap<>(findings.size());
            for (var entry : findings.entrySet()) {
                String key = entry.getKey();
                AggregatedFinding finding = entry.getValue();
                LinkedHashSet<Integer> passNumbers = passNumbersByKey.get(key);
                if (passNumbers == null || passNumbers.equals(finding.passNumbers())) {
                    materialized.put(key, finding);
                    continue;
                }
                materialized.put(key, new AggregatedFinding(
                    finding.title(),
                    finding.body(),
                    passNumbers,
                    finding.normalized()
                ));
            }
            return materialized;
        }

        void putIfAbsent(String key, AggregatedFinding finding) {
            if (findings.putIfAbsent(key, finding) == null) {
                passNumbersByKey.put(key, new LinkedHashSet<>(finding.passNumbers()));
            }
        }

        void addOrMerge(ReviewFindingParser.FindingBlock block, int passNumber) {
            AggregatedFinding.NormalizedFinding normalized = AggregatedFinding.normalize(block);
            String key = findingKeyResolver.resolve(block, normalized);

            if (mergePassIfExactMatch(key, passNumber)) {
                return;
            }
            AggregatedFinding finding = AggregatedFinding.fromNormalized(block, normalized, passNumber);
            findings.put(key, finding);
            passNumbersByKey.put(key, new LinkedHashSet<>(finding.passNumbers()));
        }

        private boolean mergePassIfExactMatch(String key, int passNumber) {
            AggregatedFinding existingExact = findings.get(key);
            if (existingExact == null) {
                return false;
            }
            passNumbersByKey.computeIfAbsent(key, _ -> new LinkedHashSet<>(existingExact.passNumbers()))
                .add(passNumber);
            return true;
        }
    }
}

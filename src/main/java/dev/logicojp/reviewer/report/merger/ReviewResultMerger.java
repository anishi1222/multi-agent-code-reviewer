package dev.logicojp.reviewer.report.merger;

import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.report.finding.AggregatedFinding;
import dev.logicojp.reviewer.report.finding.ReviewFindingParser;
import dev.logicojp.reviewer.report.finding.ReviewFindingSimilarity;
import dev.logicojp.reviewer.report.formatter.ReviewMergedContentFormatter;

import dev.logicojp.reviewer.agent.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Merges multiple review results from the same agent (multi-pass reviews)
/// into a single consolidated `ReviewResult`.
///
/// When an agent performs multiple review passes, each pass may discover
/// overlapping findings. This merger consolidates identical findings and
/// emits a deduplicated per-agent report.
public final class ReviewResultMerger {

    @FunctionalInterface
    interface FindingBlockExtractor {
        List<ReviewFindingParser.FindingBlock> extract(String content);
    }

    @FunctionalInterface
    interface FindingKeyResolver {
        String resolve(ReviewFindingParser.FindingBlock block,
                       AggregatedFinding.NormalizedFinding normalized);
    }

    @FunctionalInterface
    interface MergedContentFormatter {
        String format(Map<String, AggregatedFinding> aggregatedFindings, int totalPasses, int failedPasses);
    }

    private static final Logger logger = LoggerFactory.getLogger(ReviewResultMerger.class);
    private ReviewResultMerger() {
        // utility class
    }

    /// Merges a flat list of review results (potentially multiple per agent)
    /// into a list with exactly one result per agent.
    ///
    /// Results are grouped by agent name. If an agent has only one result,
    /// it is returned as-is. If an agent has multiple results, the successful
    /// ones are aggregated into unique findings.
    ///
    /// @param results flat list of all review results (may contain duplicates per agent)
    /// @return merged list with one result per agent (order preserved)
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

        // Group results by agent name, preserving insertion order
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

        FindingIndex findingIndex = new FindingIndex(findingKeyResolver);
        collectPassFindings(content, 1, findingBlockExtractor, findingIndex, new LinkedHashSet<>());

        String normalizedContent = mergedContentFormatter.format(findingIndex.findings(), 1, 0);
        return ReviewResult.builder()
            .agentConfig(result.agentConfig())
            .repository(result.repository())
            .content(normalizedContent)
            .success(true)
            .errorMessage(result.errorMessage())
            .timestamp(result.timestamp())
            .build();
    }

    /// Merges multiple results from the same agent into a single result.
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
            logger.warn("Agent {}: all {} passes failed", config.name(), agentResults.size());
            return agentResults.getLast();
        }

        logger.info("Agent {}: merging {} successful pass(es) out of {} total",
            config.name(), successful.size(), agentResults.size());

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

    /// Collects and deduplicates findings from all successful pass results.
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

    /// Extracts findings from a single pass and adds them to the index.
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

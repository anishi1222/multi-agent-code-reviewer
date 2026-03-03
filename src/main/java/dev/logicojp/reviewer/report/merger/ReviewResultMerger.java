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
        List<ReviewFindingParser.FindingBlock> blocks = findingBlockExtractor.extract(content);
        if (blocks.isEmpty()) {
            String normalized = ReviewFindingSimilarity.normalizeText(content);
            if (!normalized.isEmpty()) {
                findingIndex.putIfAbsent(
                    "fallback|" + normalized,
                    AggregatedFinding.fallbackWithNormalized(content, normalized, 1)
                );
            }
        }
        for (ReviewFindingParser.FindingBlock block : blocks) {
            findingIndex.addOrMerge(block, 1);
        }

        String normalizedContent = mergedContentFormatter.format(findingIndex.findings(), 1, 0);
        String mergedOverallSummary = buildMergedOverallSummary(findingIndex.findings(), 1, 0);
        normalizedContent = appendOverallSummarySection(normalizedContent, mergedOverallSummary);
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
        // Use the config from the first result
        AgentConfig config = agentResults.getFirst().agentConfig();
        String repository = agentResults.getFirst().repository();

        List<ReviewResult> successful = agentResults.stream()
            .filter(ReviewResult::success)
            .toList();

        if (successful.isEmpty()) {
            // All passes failed — return the last failure
            logger.warn("Agent {}: all {} passes failed", config.name(), agentResults.size());
            return agentResults.getLast();
        }

        logger.info("Agent {}: merging {} successful pass(es) out of {} total",
            config.name(), successful.size(), agentResults.size());

        FindingIndex findingIndex = new FindingIndex(findingKeyResolver);
        Set<String> fallbackPassContents = new LinkedHashSet<>();

        for (int i = 0; i < successful.size(); i++) {
            ReviewResult result = successful.get(i);
            int passNumber = i + 1;
            String content = result.content();
            if (content == null || content.isBlank()) {
                continue;
            }

            List<ReviewFindingParser.FindingBlock> blocks = findingBlockExtractor.extract(content);
            if (blocks.isEmpty()) {
                String normalized = ReviewFindingSimilarity.normalizeText(content);
                if (!normalized.isEmpty() && fallbackPassContents.add(normalized)) {
                    findingIndex.putIfAbsent(
                        "fallback|" + normalized,
                        AggregatedFinding.fallbackWithNormalized(content, normalized, passNumber)
                    );
                }
                continue;
            }

            for (ReviewFindingParser.FindingBlock block : blocks) {
                findingIndex.addOrMerge(block, passNumber);
            }
        }

        int failedCount = agentResults.size() - successful.size();
        String content = mergedContentFormatter.format(findingIndex.findings(), agentResults.size(), failedCount);
    String mergedOverallSummary = buildMergedOverallSummary(findingIndex.findings(), agentResults.size(), failedCount);
        content = appendOverallSummarySection(content, mergedOverallSummary);

        return ReviewResult.builder()
            .agentConfig(config)
            .repository(repository)
            .content(content)
            .success(true)
            .build();
    }

    private static String buildMergedOverallSummary(Map<String, AggregatedFinding> aggregatedFindings,
                                                    int totalPasses,
                                                    int failedPasses) {
        int findingCount = aggregatedFindings.size();
        if (findingCount == 0) {
            return failedPasses > 0
                ? "成功したパスでは新たな指摘事項は確認されませんでした。"
                : "重大な指摘事項は確認されませんでした。";
        }

        int critical = 0;
        int high = 0;
        int medium = 0;
        int low = 0;
        int unspecified = 0;
        List<String> topTitles = new ArrayList<>();

        for (AggregatedFinding finding : aggregatedFindings.values()) {
            String priority = finding.normalized().priority();
            switch (priority) {
                case "critical" -> critical++;
                case "high" -> high++;
                case "medium" -> medium++;
                case "low" -> low++;
                default -> unspecified++;
            }
            if (topTitles.size() < 3) {
                topTitles.add(finding.title());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("マージ後のレビュー結果として、").append(findingCount).append("件の指摘事項を確認しました。");
        sb.append(" 優先度内訳: ");
        sb.append("Critical ").append(critical).append("件, ");
        sb.append("High ").append(high).append("件, ");
        sb.append("Medium ").append(medium).append("件, ");
        sb.append("Low ").append(low).append("件");
        if (unspecified > 0) {
            sb.append(", 未分類 ").append(unspecified).append("件");
        }
        sb.append("。");

        if (!topTitles.isEmpty()) {
            sb.append(" 主な指摘: ").append(String.join("、", topTitles)).append("。");
        }

        if (failedPasses > 0) {
            sb.append(" なお、").append(totalPasses).append("パス中 ").append(failedPasses)
                .append("パスは失敗しており、総評は成功パスの結果に基づきます。");
        }

        return sb.toString();
    }

    private static String appendOverallSummarySection(String mergedContent, String mergedOverallSummary) {
        if (mergedOverallSummary == null || mergedOverallSummary.isBlank()) {
            return mergedContent;
        }

        return mergedContent
            + "\n\n---\n\n"
            + "**総評**\n\n"
            + mergedOverallSummary.trim();
    }

    private static final class FindingIndex {
        private final Map<String, AggregatedFinding> findings = new LinkedHashMap<>();
        private final Map<String, Set<String>> findingKeysByPriority = new LinkedHashMap<>();
        private final Map<String, Set<String>> findingKeysByPriorityAndPrefix = new LinkedHashMap<>();
        private final Map<String, Set<String>> findingKeysByKeyword = new LinkedHashMap<>();
        private final FindingKeyResolver findingKeyResolver;

        private FindingIndex(FindingKeyResolver findingKeyResolver) {
            this.findingKeyResolver = findingKeyResolver;
        }

        Map<String, AggregatedFinding> findings() {
            return findings;
        }

        void putIfAbsent(String key, AggregatedFinding finding) {
            findings.putIfAbsent(key, finding);
        }

        void addOrMerge(ReviewFindingParser.FindingBlock block, int passNumber) {
            AggregatedFinding.NormalizedFinding normalized = AggregatedFinding.normalize(block);
            String key = findingKeyResolver.resolve(block, normalized);

            if (mergePassIfExactMatch(key, passNumber)) {
                return;
            }
            if (mergePassIfNearDuplicate(normalized, passNumber)) {
                return;
            }

            findings.put(key, AggregatedFinding.fromNormalized(block, normalized, passNumber));
            indexByPriority(normalized.priority(), key);
            indexByPriorityAndPrefix(normalized.priority(), buildPrefixKey(normalized.title()), key);
            indexByKeyword(firstKeyword(normalized.titleKeywords()), key);
        }

        private boolean mergePassIfExactMatch(String key, int passNumber) {
            AggregatedFinding existingExact = findings.get(key);
            if (existingExact == null) {
                return false;
            }
            findings.put(key, existingExact.withPass(passNumber));
            return true;
        }

        private boolean mergePassIfNearDuplicate(AggregatedFinding.NormalizedFinding incoming, int passNumber) {
            String nearDuplicateKey = findNearDuplicateKey(incoming);
            if (nearDuplicateKey == null) {
                return false;
            }
            AggregatedFinding nearExisting = findings.get(nearDuplicateKey);
            findings.put(nearDuplicateKey, nearExisting.withPass(passNumber));
            return true;
        }

        private String findNearDuplicateKey(AggregatedFinding.NormalizedFinding incoming) {
            // Use index for both priority-specified and priority-blank findings
            String priorityKey = incoming.priority().isBlank() ? "" : incoming.priority();
            String titlePrefix = buildPrefixKey(incoming.title());
            Set<String> keys = findingKeysByPriorityAndPrefix.get(priorityPrefixIndexKey(priorityKey, titlePrefix));
            if (keys == null || keys.isEmpty()) {
                keys = findingKeysByKeyword.getOrDefault(firstKeyword(incoming.titleKeywords()), Set.of());
            }
            if (keys.isEmpty()) {
                keys = findingKeysByPriority.getOrDefault(priorityKey, Set.of());
            }
            for (String key : keys) {
                AggregatedFinding candidate = findings.get(key);
                if (candidate != null && candidate.isNearDuplicateOf(incoming)) {
                    return key;
                }
            }
            return null;
        }

        private void indexByPriority(String priority, String key) {
            // Index all findings including those with blank priority (keyed as "")
            String indexKey = (priority == null || priority.isBlank()) ? "" : priority;
            findingKeysByPriority.computeIfAbsent(indexKey, _ -> new LinkedHashSet<>()).add(key);
        }

        private void indexByPriorityAndPrefix(String priority, String prefix, String key) {
            String priorityKey = (priority == null || priority.isBlank()) ? "" : priority;
            String indexKey = priorityPrefixIndexKey(priorityKey, prefix);
            findingKeysByPriorityAndPrefix.computeIfAbsent(indexKey, _ -> new LinkedHashSet<>()).add(key);
        }

        private void indexByKeyword(String keyword, String key) {
            if (keyword == null || keyword.isBlank()) {
                return;
            }
            findingKeysByKeyword.computeIfAbsent(keyword, _ -> new LinkedHashSet<>()).add(key);
        }

        private String firstKeyword(Set<String> keywords) {
            if (keywords == null || keywords.isEmpty()) {
                return "";
            }
            return keywords.iterator().next();
        }
    }

    private static String priorityPrefixIndexKey(String priority, String prefix) {
        return priority + "|" + prefix;
    }

    private static String buildPrefixKey(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        int length = Math.min(title.length(), 8);
        return title.substring(0, length);
    }

}

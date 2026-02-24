package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.report.ReviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

/// Merges multiple review results from the same agent (multi-pass reviews)
/// into a single consolidated {@link ReviewResult}.
///
/// When an agent performs multiple review passes, each pass may discover
/// overlapping findings. This merger consolidates identical findings and
/// emits a deduplicated per-agent report.
///
/// Integrates finding parsing, similarity checking (Dice coefficient ≥ 0.80),
/// aggregation, and merged content formatting — all previously split across
/// 5 v1 files (ReviewResultMerger, ReviewFindingParser, ReviewFindingSimilarity,
/// AggregatedFinding, ReviewMergedContentFormatter).
public final class ReviewResultMerger {

    private static final Logger logger = LoggerFactory.getLogger(ReviewResultMerger.class);

    private static final int FALLBACK_SIMILARITY_PREFIX_LENGTH = 500;
    private static final int FALLBACK_KEY_HASH_LENGTH_BYTES = 12;

    private ReviewResultMerger() {
    }

    // ========================================================================
    // Inner records
    // ========================================================================

    /// A parsed finding block from review output.
    record FindingBlock(String title, String body) {}

    /// Aggregated finding with deduplication tracking across passes.
    record AggregatedFinding(
        String title,
        String body,
        Set<Integer> passNumbers,
        String normalizedTitle,
        String normalizedPriority,
        String normalizedSummary,
        String normalizedLocation,
        Set<Integer> titleBigrams,
        Set<Integer> summaryBigrams,
        Set<Integer> locationBigrams
    ) {
        AggregatedFinding {
            Objects.requireNonNull(title);
            Objects.requireNonNull(body);
            passNumbers = Collections.unmodifiableSet(new LinkedHashSet<>(passNumbers));
        }

        AggregatedFinding withPass(int passNumber) {
            var newPasses = new LinkedHashSet<>(passNumbers);
            newPasses.add(passNumber);
            return new AggregatedFinding(title, body, newPasses,
                normalizedTitle, normalizedPriority, normalizedSummary,
                normalizedLocation, titleBigrams, summaryBigrams, locationBigrams);
        }

        boolean isNearDuplicateOf(NormalizedFinding incoming) {
            if (hasPriorityMismatch(incoming.priority())) return false;
            if (hasLocationContext(incoming.location())) return matchByLocationAndContent(incoming);
            return matchBySummaryAndTitle(incoming);
        }

        private boolean hasPriorityMismatch(String incomingPriority) {
            return !normalizedPriority.isEmpty()
                && !incomingPriority.isEmpty()
                && !normalizedPriority.equals(incomingPriority);
        }

        private boolean hasLocationContext(String incomingLocation) {
            return !normalizedLocation.isEmpty() && !incomingLocation.isEmpty();
        }

        private boolean matchByLocationAndContent(NormalizedFinding incoming) {
            if (!ReviewFindingSimilarity.isSimilarText(normalizedLocation, incoming.location(),
                locationBigrams, incoming.locationBigrams())) return false;
            return ReviewFindingSimilarity.isSimilarText(normalizedSummary, incoming.summary(),
                summaryBigrams, incoming.summaryBigrams())
                || ReviewFindingSimilarity.isSimilarText(normalizedTitle, incoming.title(),
                titleBigrams, incoming.titleBigrams())
                || ReviewFindingSimilarity.hasCommonKeyword(normalizedTitle, incoming.title());
        }

        private boolean matchBySummaryAndTitle(NormalizedFinding incoming) {
            return ReviewFindingSimilarity.isSimilarText(normalizedSummary, incoming.summary(),
                summaryBigrams, incoming.summaryBigrams())
                && ReviewFindingSimilarity.isSimilarText(normalizedTitle, incoming.title(),
                titleBigrams, incoming.titleBigrams());
        }
    }

    /// Normalized representation of a finding for comparison.
    record NormalizedFinding(
        String title,
        String priority,
        String summary,
        String location,
        Set<Integer> titleBigrams,
        Set<Integer> summaryBigrams,
        Set<Integer> locationBigrams
    ) {}

    // ========================================================================
    // Public API
    // ========================================================================

    /// Merges a flat list of review results (potentially multiple per agent)
    /// into a list with exactly one result per agent.
    public static List<ReviewResult> mergeByAgent(List<ReviewResult> results) {
        if (results == null || results.isEmpty()) return List.of();

        Map<String, List<ReviewResult>> byAgent = new LinkedHashMap<>();
        for (ReviewResult result : results) {
            String agentName = result.agentConfig() != null
                ? result.agentConfig().name() : "__unknown__";
            byAgent.computeIfAbsent(agentName, _ -> new ArrayList<>()).add(result);
        }

        List<ReviewResult> merged = new ArrayList<>(byAgent.size());
        for (var entry : byAgent.entrySet()) {
            List<ReviewResult> agentResults = entry.getValue();
            if (agentResults.size() == 1) {
                merged.add(agentResults.getFirst());
            } else {
                merged.add(mergeAgentResults(agentResults));
            }
        }
        return merged;
    }

    // ========================================================================
    // Merge logic
    // ========================================================================

    private static ReviewResult mergeAgentResults(List<ReviewResult> agentResults) {
        AgentConfig config = agentResults.getFirst().agentConfig();
        String repository = agentResults.getFirst().repository();

        List<ReviewResult> successful = agentResults.stream()
            .filter(ReviewResult::success).toList();

        if (successful.isEmpty()) {
            logger.warn("Agent {}: all {} passes failed", config.name(), agentResults.size());
            return agentResults.getLast();
        }

        logger.info("Agent {}: merging {} successful pass(es) out of {} total",
            config.name(), successful.size(), agentResults.size());

        Map<String, AggregatedFinding> aggregatedFindings = new LinkedHashMap<>();
        Map<String, Set<String>> findingKeysByPriority = new LinkedHashMap<>();
        Map<String, Set<String>> findingKeysByPriorityAndPrefix = new LinkedHashMap<>();
        Set<String> fallbackPassContents = new LinkedHashSet<>();

        for (int i = 0; i < successful.size(); i++) {
            processPassResult(successful.get(i), i + 1,
                aggregatedFindings, findingKeysByPriority, findingKeysByPriorityAndPrefix, fallbackPassContents);
        }

        int failedCount = agentResults.size() - successful.size();
        String content = formatMergedContent(aggregatedFindings, agentResults.size(), failedCount);

        return ReviewResult.builder()
            .agentConfig(config)
            .repository(repository)
            .content(content)
            .success(true)
            .build();
    }

    private static void processPassResult(ReviewResult result,
                                          int passNumber,
                                          Map<String, AggregatedFinding> aggregatedFindings,
                                          Map<String, Set<String>> findingKeysByPriority,
                                          Map<String, Set<String>> findingKeysByPriorityAndPrefix,
                                          Set<String> fallbackPassContents) {
        String content = result.content();
        if (content == null || content.isBlank()) {
            return;
        }

        List<FindingBlock> blocks = ReviewFindingBlockParser.extractFindingBlocks(content);
        if (blocks.isEmpty()) {
            addFallbackIfAbsent(content, passNumber, aggregatedFindings, fallbackPassContents);
            return;
        }

        for (FindingBlock block : blocks) {
            mergeFindingBlock(block, passNumber,
                aggregatedFindings, findingKeysByPriority, findingKeysByPriorityAndPrefix);
        }
    }

    private static void addFallbackIfAbsent(String content,
                                            int passNumber,
                                            Map<String, AggregatedFinding> aggregatedFindings,
                                            Set<String> fallbackPassContents) {
        String normalizedContent = normalizeText(content);
        if (!normalizedContent.isEmpty() && fallbackPassContents.add(normalizedContent)) {
            String contentHash = shortSha256(normalizedContent);
            aggregatedFindings.putIfAbsent(
                "fallback|" + contentHash,
                createFallback(content, passNumber));
        }
    }

    private static String shortSha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, FALLBACK_KEY_HASH_LENGTH_BYTES);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static void mergeFindingBlock(FindingBlock block,
                                          int passNumber,
                                          Map<String, AggregatedFinding> aggregatedFindings,
                                          Map<String, Set<String>> findingKeysByPriority,
                                          Map<String, Set<String>> findingKeysByPriorityAndPrefix) {
        NormalizedFinding normalized = normalizeFinding(block);
        String key = findingKeyFromNormalized(normalized, block.body());

        if (appendPassToExactMatch(key, passNumber, aggregatedFindings)) {
            return;
        }

        String nearDuplicateKey = findNearDuplicateKey(
            aggregatedFindings, findingKeysByPriority, findingKeysByPriorityAndPrefix, normalized);
        if (nearDuplicateKey != null) {
            appendPassToNearDuplicate(nearDuplicateKey, passNumber, aggregatedFindings);
            return;
        }

        addNewFinding(block, normalized, key, passNumber,
            aggregatedFindings, findingKeysByPriority, findingKeysByPriorityAndPrefix);
    }

    private static boolean appendPassToExactMatch(String key,
                                                  int passNumber,
                                                  Map<String, AggregatedFinding> aggregatedFindings) {
        AggregatedFinding existingExact = aggregatedFindings.get(key);
        if (existingExact == null) {
            return false;
        }
        aggregatedFindings.put(key, existingExact.withPass(passNumber));
        return true;
    }

    private static void appendPassToNearDuplicate(String nearDuplicateKey,
                                                  int passNumber,
                                                  Map<String, AggregatedFinding> aggregatedFindings) {
        AggregatedFinding nearExisting = aggregatedFindings.get(nearDuplicateKey);
        aggregatedFindings.put(nearDuplicateKey, nearExisting.withPass(passNumber));
    }

    private static void addNewFinding(FindingBlock block,
                                      NormalizedFinding normalized,
                                      String key,
                                      int passNumber,
                                      Map<String, AggregatedFinding> aggregatedFindings,
                                      Map<String, Set<String>> findingKeysByPriority,
                                      Map<String, Set<String>> findingKeysByPriorityAndPrefix) {
        aggregatedFindings.put(key, fromNormalized(block, normalized, passNumber));
        indexByPriority(findingKeysByPriority, normalized.priority(), key);
        indexByPriorityAndPrefix(findingKeysByPriorityAndPrefix,
            normalized.priority(), ReviewFindingSimilarity.buildPrefixKey(normalized.title()), key);
    }

    // ========================================================================
    // Finding normalization and key generation
    // ========================================================================

    private static NormalizedFinding normalizeFinding(FindingBlock block) {
        String normalizedTitle = normalizeText(block.title());
        String normalizedPriority = normalizeText(ReviewFindingBlockParser.extractTableValue(block.body(), "Priority"));
        String normalizedSummary = normalizeText(ReviewFindingBlockParser.extractTableValue(block.body(), "指摘の概要"));
        String normalizedLocation = normalizeText(ReviewFindingBlockParser.extractTableValue(block.body(), "該当箇所"));
        return new NormalizedFinding(
            normalizedTitle, normalizedPriority, normalizedSummary, normalizedLocation,
            ReviewFindingSimilarity.bigrams(normalizedTitle),
            ReviewFindingSimilarity.bigrams(normalizedSummary),
            ReviewFindingSimilarity.bigrams(normalizedLocation));
    }

    private static String findingKeyFromNormalized(NormalizedFinding normalized, String rawBody) {
        if (!normalized.title().isEmpty()
            && (!normalized.summary().isEmpty() || !normalized.location().isEmpty()
                || !normalized.priority().isEmpty())) {
            return String.join("|", normalized.title(), normalized.priority(),
                normalized.location(), normalized.summary());
        }
        return "raw|" + normalizeText(rawBody);
    }

    private static AggregatedFinding fromNormalized(FindingBlock block,
                                                    NormalizedFinding normalized,
                                                    int passNumber) {
        return new AggregatedFinding(
            block.title(), block.body(), new LinkedHashSet<>(Set.of(passNumber)),
            normalized.title(), normalized.priority(), normalized.summary(), normalized.location(),
            normalized.titleBigrams(), normalized.summaryBigrams(), normalized.locationBigrams());
    }

    private static AggregatedFinding createFallback(String rawContent, int passNumber) {
        String normalizedRaw = normalizeText(rawContent);
        String similarityTarget = normalizedRaw.length() > FALLBACK_SIMILARITY_PREFIX_LENGTH
            ? normalizedRaw.substring(0, FALLBACK_SIMILARITY_PREFIX_LENGTH)
            : normalizedRaw;
        return new AggregatedFinding(
            "レビュー結果", rawContent, new LinkedHashSet<>(Set.of(passNumber)),
            normalizeText("レビュー結果"), "", normalizedRaw, "",
            ReviewFindingSimilarity.bigrams(normalizeText("レビュー結果")),
            ReviewFindingSimilarity.bigrams(similarityTarget),
            Set.of());
    }

    // ========================================================================
    // Near-duplicate detection (Dice coefficient)
    // ========================================================================

    private static String findNearDuplicateKey(Map<String, AggregatedFinding> existing,
                                               Map<String, Set<String>> findingKeysByPriority,
                                               Map<String, Set<String>> findingKeysByPriorityAndPrefix,
                                               NormalizedFinding incoming) {
        String priorityKey = incoming.priority().isBlank() ? "" : incoming.priority();
        String titlePrefix = ReviewFindingSimilarity.buildPrefixKey(incoming.title());
        Set<String> keys = findingKeysByPriorityAndPrefix.get(priorityKey + "|" + titlePrefix);
        if (keys == null || keys.isEmpty()) {
            keys = findingKeysByPriority.getOrDefault(priorityKey, Set.of());
        }
        for (String key : keys) {
            AggregatedFinding candidate = existing.get(key);
            if (candidate != null && candidate.isNearDuplicateOf(incoming)) return key;
        }
        return null;
    }

    private static void indexByPriority(Map<String, Set<String>> index,
                                        String priority, String key) {
        String indexKey = (priority == null || priority.isBlank()) ? "" : priority;
        index.computeIfAbsent(indexKey, _ -> new LinkedHashSet<>()).add(key);
    }

    private static void indexByPriorityAndPrefix(Map<String, Set<String>> index,
                                                 String priority, String prefix, String key) {
        String priorityKey = (priority == null || priority.isBlank()) ? "" : priority;
        index.computeIfAbsent(priorityKey + "|" + prefix, _ -> new LinkedHashSet<>()).add(key);
    }

    static String normalizeText(String value) {
        return ReviewFindingSimilarity.normalizeText(value);
    }

    // ========================================================================
    // Merged content formatting
    // ========================================================================

    private static String formatMergedContent(Map<String, AggregatedFinding> aggregatedFindings,
                                              int totalPasses, int failedPasses) {
        return ReviewMergedContentFormatter.formatMergedContent(aggregatedFindings, totalPasses, failedPasses);
    }
}

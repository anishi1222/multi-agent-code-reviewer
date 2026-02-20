package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.report.ReviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // --- Finding parsing patterns ---
    private static final Pattern FINDING_HEADER = Pattern.compile("(?m)^###\\s+(\\d+)\\.\\s+(.+?)\\s*$");
    private static final Pattern TABLE_ROW_TEMPLATE = Pattern.compile(
        "(?m)^\\|\\s*\\*\\*%s\\*\\*\\s*\\|\\s*(.*?)\\s*\\|\\s*$");
    private static final Map<String, Pattern> TABLE_VALUE_PATTERNS = new ConcurrentHashMap<>();

    // --- Similarity constants ---
    private static final Pattern KEYWORD_PATTERN = Pattern.compile(
        "[a-z0-9_]+|[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]{2,}");
    private static final double NEAR_DUPLICATE_SIMILARITY = 0.80d;
    private static final int FALLBACK_SIMILARITY_PREFIX_LENGTH = 500;

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
        Set<String> titleBigrams,
        Set<String> summaryBigrams,
        Set<String> locationBigrams
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
            if (!isSimilarText(normalizedLocation, incoming.location(),
                locationBigrams, incoming.locationBigrams())) return false;
            return isSimilarText(normalizedSummary, incoming.summary(),
                summaryBigrams, incoming.summaryBigrams())
                || isSimilarText(normalizedTitle, incoming.title(),
                titleBigrams, incoming.titleBigrams())
                || hasCommonKeyword(normalizedTitle, incoming.title());
        }

        private boolean matchBySummaryAndTitle(NormalizedFinding incoming) {
            return isSimilarText(normalizedSummary, incoming.summary(),
                summaryBigrams, incoming.summaryBigrams())
                && isSimilarText(normalizedTitle, incoming.title(),
                titleBigrams, incoming.titleBigrams());
        }
    }

    /// Normalized representation of a finding for comparison.
    record NormalizedFinding(
        String title,
        String priority,
        String summary,
        String location,
        Set<String> titleBigrams,
        Set<String> summaryBigrams,
        Set<String> locationBigrams
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

        List<FindingBlock> blocks = extractFindingBlocks(content);
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
            aggregatedFindings.putIfAbsent(
                "fallback|" + normalizedContent,
                createFallback(content, passNumber));
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
            normalized.priority(), buildPrefixKey(normalized.title()), key);
    }

    // ========================================================================
    // Finding parsing
    // ========================================================================

    private static List<FindingBlock> extractFindingBlocks(String content) {
        Matcher matcher = FINDING_HEADER.matcher(content);
        List<int[]> headerPositions = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            headerPositions.add(new int[]{matcher.start(), matcher.end()});
            titles.add(matcher.group(2).trim());
        }
        if (headerPositions.isEmpty()) return List.of();

        List<FindingBlock> blocks = new ArrayList<>(headerPositions.size());
        for (int i = 0; i < headerPositions.size(); i++) {
            int bodyStart = headerPositions.get(i)[1];
            int bodyEnd = i + 1 < headerPositions.size()
                ? headerPositions.get(i + 1)[0] : content.length();
            String body = content.substring(bodyStart, bodyEnd).trim();
            if (!body.isEmpty()) {
                blocks.add(new FindingBlock(titles.get(i), body));
            }
        }
        return blocks;
    }

    private static String extractTableValue(String body, String key) {
        Pattern pattern = TABLE_VALUE_PATTERNS.computeIfAbsent(
            key,
            k -> Pattern.compile(TABLE_ROW_TEMPLATE.pattern().formatted(Pattern.quote(k))));
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    // ========================================================================
    // Finding normalization and key generation
    // ========================================================================

    private static NormalizedFinding normalizeFinding(FindingBlock block) {
        String normalizedTitle = normalizeText(block.title());
        String normalizedPriority = normalizeText(extractTableValue(block.body(), "Priority"));
        String normalizedSummary = normalizeText(extractTableValue(block.body(), "指摘の概要"));
        String normalizedLocation = normalizeText(extractTableValue(block.body(), "該当箇所"));
        return new NormalizedFinding(
            normalizedTitle, normalizedPriority, normalizedSummary, normalizedLocation,
            bigrams(normalizedTitle), bigrams(normalizedSummary), bigrams(normalizedLocation));
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
            bigrams(normalizeText("レビュー結果")), bigrams(similarityTarget), Set.of());
    }

    // ========================================================================
    // Near-duplicate detection (Dice coefficient)
    // ========================================================================

    private static String findNearDuplicateKey(Map<String, AggregatedFinding> existing,
                                               Map<String, Set<String>> findingKeysByPriority,
                                               Map<String, Set<String>> findingKeysByPriorityAndPrefix,
                                               NormalizedFinding incoming) {
        String priorityKey = incoming.priority().isBlank() ? "" : incoming.priority();
        String titlePrefix = buildPrefixKey(incoming.title());
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

    private static String buildPrefixKey(String title) {
        if (title == null || title.isBlank()) return "";
        int length = Math.min(title.length(), 8);
        return title.substring(0, length);
    }

    // ========================================================================
    // Text similarity
    // ========================================================================

    static String normalizeText(String value) {
        if (value == null || value.isBlank()) return "";
        char[] chars = value.toCharArray();
        StringBuilder sb = new StringBuilder(chars.length);
        boolean lastWasSpace = true;
        for (char c : chars) {
            char lower = Character.toLowerCase(c);
            switch (lower) {
                case '`', '*', '_' -> { /* skip markdown formatting */ }
                case '|', '/', '\t', '\n', '\r', ' ' -> {
                    if (!lastWasSpace) { sb.append(' '); lastWasSpace = true; }
                }
                default -> {
                    if (lower == '\u30FB') { // ・ (middle dot)
                        if (!lastWasSpace) { sb.append(' '); lastWasSpace = true; }
                    } else {
                        sb.append(lower); lastWasSpace = false;
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    private static Set<String> bigrams(String text) {
        String compact = text.replace(" ", "");
        if (compact.length() < 2) return compact.isEmpty() ? Set.of() : Set.of(compact);
        Set<String> grams = HashSet.newHashSet(compact.length() - 1);
        for (int i = 0; i < compact.length() - 1; i++) {
            grams.add(compact.substring(i, i + 2));
        }
        return grams;
    }

    private static boolean isSimilarText(String left, String right,
                                         Set<String> leftBigrams, Set<String> rightBigrams) {
        if (left.isEmpty() || right.isEmpty()) return false;
        if (left.equals(right)) return true;
        if (left.length() >= 8 && right.contains(left)) return true;
        if (right.length() >= 8 && left.contains(right)) return true;
        return diceCoefficient(leftBigrams, rightBigrams) >= NEAR_DUPLICATE_SIMILARITY;
    }

    private static boolean hasCommonKeyword(String left, String right) {
        Set<String> leftWords = extractKeywords(left);
        Set<String> rightWords = extractKeywords(right);
        if (leftWords.isEmpty() || rightWords.isEmpty()) return false;
        for (String word : leftWords) {
            if (rightWords.contains(word)) return true;
        }
        return false;
    }

    private static Set<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) return Set.of();
        Set<String> keywords = new LinkedHashSet<>();
        Matcher matcher = KEYWORD_PATTERN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 2) keywords.add(token);
        }
        return keywords;
    }

    private static double diceCoefficient(Set<String> leftBigrams, Set<String> rightBigrams) {
        if (leftBigrams.isEmpty() || rightBigrams.isEmpty()) return 0.0d;
        int overlap = 0;
        for (String gram : leftBigrams) {
            if (rightBigrams.contains(gram)) overlap++;
        }
        return (2.0d * overlap) / (leftBigrams.size() + rightBigrams.size());
    }

    // ========================================================================
    // Merged content formatting
    // ========================================================================

    private static String formatMergedContent(Map<String, AggregatedFinding> aggregatedFindings,
                                              int totalPasses, int failedPasses) {
        var sb = new StringBuilder();

        if (aggregatedFindings.isEmpty()) {
            sb.append("指摘事項なし");
        } else {
            int index = 1;
            int totalFindings = aggregatedFindings.size();
            for (AggregatedFinding finding : aggregatedFindings.values()) {
                sb.append("### ").append(index).append(". ").append(finding.title()).append("\n\n");
                if (finding.passNumbers().size() > 1) {
                    sb.append("> 検出パス: ").append(formatPassNumbers(finding.passNumbers())).append("\n\n");
                }
                sb.append(finding.body().trim());
                if (index < totalFindings) {
                    sb.append("\n\n---\n\n");
                }
                index++;
            }
        }

        if (failedPasses > 0) {
            sb.append("\n\n---\n\n> **注記**: %d パス中 %d パスが失敗しました。上記は成功したパスの結果のみです。\n"
                .formatted(totalPasses, failedPasses));
        }

        return sb.toString();
    }

    private static String formatPassNumbers(Set<Integer> passNumbers) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Integer passNumber : passNumbers) {
            if (i > 0) sb.append(", ");
            sb.append(passNumber);
            i++;
        }
        return sb.toString();
    }
}

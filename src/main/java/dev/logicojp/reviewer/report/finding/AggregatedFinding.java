package dev.logicojp.reviewer.report.finding;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record AggregatedFinding(String title,
                               String body,
                               Set<Integer> passNumbers,
                               NormalizedFinding normalized) {

    public record NormalizedFinding(String title,
                                    String priority,
                                    String summary,
                                    String location,
                                    Set<String> titleKeywords,
                                    Set<String> titleBigrams,
                                    Set<String> summaryBigrams,
                                    Set<String> locationBigrams) {
        public NormalizedFinding {
            title = title != null ? title : "";
            priority = priority != null ? priority : "";
            summary = summary != null ? summary : "";
            location = location != null ? location : "";
            titleKeywords = titleKeywords != null ? Set.copyOf(titleKeywords) : Set.of();
            titleBigrams = titleBigrams != null ? Set.copyOf(titleBigrams) : Set.of();
            summaryBigrams = summaryBigrams != null ? Set.copyOf(summaryBigrams) : Set.of();
            locationBigrams = locationBigrams != null ? Set.copyOf(locationBigrams) : Set.of();
        }
    }

    private static final int FALLBACK_SIMILARITY_PREFIX_LENGTH = 500;

    public AggregatedFinding {
        Objects.requireNonNull(title);
        Objects.requireNonNull(body);
        Objects.requireNonNull(normalized);
        passNumbers = Collections.unmodifiableSet(new LinkedHashSet<>(passNumbers));
    }

    public static AggregatedFinding from(ReviewFindingParser.FindingBlock block, int passNumber) {
        return fromNormalized(block, normalize(block), passNumber);
    }

    public static AggregatedFinding fromNormalized(ReviewFindingParser.FindingBlock block,
                                            NormalizedFinding normalized,
                                            int passNumber) {
        return new AggregatedFinding(
            block.title(),
            block.body(),
            new LinkedHashSet<>(Set.of(passNumber)),
            normalized
        );
    }

    public static NormalizedFinding normalize(ReviewFindingParser.FindingBlock block) {
        String normalizedTitle = ReviewFindingSimilarity.normalizeText(block.title());
        String normalizedPriority = ReviewFindingSimilarity.normalizeText(ReviewFindingParser.extractTableValue(block.body(), "Priority"));
        String normalizedSummary = ReviewFindingSimilarity.normalizeText(ReviewFindingParser.extractTableValue(block.body(), "指摘の概要"));
        String normalizedLocation = ReviewFindingSimilarity.normalizeText(ReviewFindingParser.extractTableValue(block.body(), "該当箇所"));
        return new NormalizedFinding(
            normalizedTitle,
            normalizedPriority,
            normalizedSummary,
            normalizedLocation,
            ReviewFindingSimilarity.extractKeywords(normalizedTitle),
            ReviewFindingSimilarity.bigrams(normalizedTitle),
            ReviewFindingSimilarity.bigrams(normalizedSummary),
            ReviewFindingSimilarity.bigrams(normalizedLocation)
        );
    }

    public static AggregatedFinding fallback(String rawContent, int passNumber) {
        String normalizedRaw = ReviewFindingSimilarity.normalizeText(rawContent);
        return fallbackWithNormalized(rawContent, normalizedRaw, passNumber);
    }

    public static AggregatedFinding fallbackWithNormalized(String rawContent,
                                                           String normalizedRaw,
                                                           int passNumber) {
        normalizedRaw = normalizedRaw != null ? normalizedRaw : "";
        String similarityTarget = normalizedRaw.length() > FALLBACK_SIMILARITY_PREFIX_LENGTH
            ? normalizedRaw.substring(0, FALLBACK_SIMILARITY_PREFIX_LENGTH)
            : normalizedRaw;

        String normalizedTitle = ReviewFindingSimilarity.normalizeText("レビュー結果");

        return new AggregatedFinding(
            "レビュー結果",
            rawContent,
            new LinkedHashSet<>(Set.of(passNumber)),
            new NormalizedFinding(
                normalizedTitle,
                "",
                normalizedRaw,
                "",
                ReviewFindingSimilarity.extractKeywords(normalizedTitle),
                ReviewFindingSimilarity.bigrams(normalizedTitle),
                ReviewFindingSimilarity.bigrams(similarityTarget),
                Set.of()
            )
        );
    }

    public boolean isNearDuplicateOf(NormalizedFinding incoming) {
        if (hasPriorityMismatch(incoming.priority())) {
            return false;
        }

        if (hasLocationContext(incoming.location())) {
            return matchByLocationAndContent(incoming);
        }

        return matchBySummaryAndTitle(incoming);
    }

    private boolean hasPriorityMismatch(String incomingPriority) {
        return !normalized.priority().isEmpty()
            && !incomingPriority.isEmpty()
            && !normalized.priority().equals(incomingPriority);
    }

    private boolean hasLocationContext(String incomingLocation) {
        return !normalized.location().isEmpty() && !incomingLocation.isEmpty();
    }

    private boolean matchByLocationAndContent(NormalizedFinding incoming) {
        if (!ReviewFindingSimilarity.isSimilarText(normalized.location(), incoming.location(),
            normalized.locationBigrams(), incoming.locationBigrams())) {
            return false;
        }

        return ReviewFindingSimilarity.isSimilarText(normalized.summary(), incoming.summary(),
            normalized.summaryBigrams(), incoming.summaryBigrams())
            || ReviewFindingSimilarity.isSimilarText(normalized.title(), incoming.title(),
            normalized.titleBigrams(), incoming.titleBigrams())
            || ReviewFindingSimilarity.hasCommonKeyword(normalized.titleKeywords(), incoming.titleKeywords());
    }

    private boolean matchBySummaryAndTitle(NormalizedFinding incoming) {
        return ReviewFindingSimilarity.isSimilarText(normalized.summary(), incoming.summary(),
            normalized.summaryBigrams(), incoming.summaryBigrams())
            && ReviewFindingSimilarity.isSimilarText(normalized.title(), incoming.title(),
            normalized.titleBigrams(), incoming.titleBigrams());
    }

    public AggregatedFinding withPass(int passNumber) {
        if (passNumbers.contains(passNumber)) {
            return this;
        }
        var newPasses = new LinkedHashSet<>(passNumbers);
        newPasses.add(passNumber);
        return new AggregatedFinding(title, body, newPasses, normalized);
    }
}
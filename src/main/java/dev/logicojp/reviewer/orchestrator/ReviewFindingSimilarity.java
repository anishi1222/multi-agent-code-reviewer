package dev.logicojp.reviewer.orchestrator;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Similarity and normalization helpers for finding deduplication.
final class ReviewFindingSimilarity {

    private static final Pattern KEYWORD_PATTERN = Pattern.compile(
        "[a-z0-9_]+|[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]{2,}");
    private static final double NEAR_DUPLICATE_SIMILARITY = 0.80d;

    private ReviewFindingSimilarity() {
    }

    static String normalizeText(String value) {
        if (value == null || value.isBlank()) return "";
        char[] chars = value.toCharArray();
        StringBuilder sb = new StringBuilder(chars.length);
        boolean lastWasSpace = true;
        for (char c : chars) {
            char lower = Character.toLowerCase(c);
            switch (lower) {
                case '`', '*', '_' -> { }
                case '|', '/', '\t', '\n', '\r', ' ' -> {
                    if (!lastWasSpace) {
                        sb.append(' ');
                        lastWasSpace = true;
                    }
                }
                default -> {
                    if (lower == '\u30FB') {
                        if (!lastWasSpace) {
                            sb.append(' ');
                            lastWasSpace = true;
                        }
                    } else {
                        sb.append(lower);
                        lastWasSpace = false;
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    static Set<Integer> bigrams(String text) {
        String compact = text.replace(" ", "");
        if (compact.length() < 2) return compact.isEmpty() ? Set.of() : Set.of((int) compact.charAt(0));
        Set<Integer> grams = HashSet.newHashSet(compact.length() - 1);
        for (int i = 0; i < compact.length() - 1; i++) {
            grams.add((compact.charAt(i) << 16) | compact.charAt(i + 1));
        }
        return grams;
    }

    static boolean isSimilarText(String left, String right,
                                 Set<Integer> leftBigrams, Set<Integer> rightBigrams) {
        if (left.isEmpty() || right.isEmpty()) return false;
        if (left.equals(right)) return true;
        if (left.length() >= 8 && right.contains(left)) return true;
        if (right.length() >= 8 && left.contains(right)) return true;
        return diceCoefficient(leftBigrams, rightBigrams) >= NEAR_DUPLICATE_SIMILARITY;
    }

    static boolean hasCommonKeyword(String left, String right) {
        Set<String> leftWords = extractKeywords(left);
        Set<String> rightWords = extractKeywords(right);
        if (leftWords.isEmpty() || rightWords.isEmpty()) return false;
        for (String word : leftWords) {
            if (rightWords.contains(word)) return true;
        }
        return false;
    }

    static String buildPrefixKey(String title) {
        if (title == null || title.isBlank()) return "";
        int length = Math.min(title.length(), 8);
        return title.substring(0, length);
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

    private static double diceCoefficient(Set<Integer> leftBigrams, Set<Integer> rightBigrams) {
        if (leftBigrams.isEmpty() || rightBigrams.isEmpty()) return 0.0d;
        int overlap = 0;
        for (int gram : leftBigrams) {
            if (rightBigrams.contains(gram)) overlap++;
        }
        return (2.0d * overlap) / (leftBigrams.size() + rightBigrams.size());
    }
}

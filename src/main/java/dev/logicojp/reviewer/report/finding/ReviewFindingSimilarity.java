package dev.logicojp.reviewer.report.finding;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReviewFindingSimilarity {

    private static final Pattern KEYWORD_PATTERN = Pattern.compile("[a-z0-9_]+|[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]{2,}");
    private static final double NEAR_DUPLICATE_SIMILARITY = 0.80d;

    private ReviewFindingSimilarity() {
    }

    public static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        char[] chars = value.toCharArray();
        StringBuilder sb = new StringBuilder(chars.length);
        boolean lastWasSpace = true;
        for (char c : chars) {
            char lower = Character.toLowerCase(c);
            switch (lower) {
                case '`', '*', '_' -> { /* skip */ }
                case '|', '/', '\t', '\n', '\r', ' ' -> {
                    if (!lastWasSpace) { sb.append(' '); lastWasSpace = true; }
                }
                default -> {
                    if (lower == '\u30FB') { // ・
                        if (!lastWasSpace) { sb.append(' '); lastWasSpace = true; }
                    } else {
                        sb.append(lower); lastWasSpace = false;
                    }
                }
            }
        }
        return sb.toString().trim();
    }

     static Set<String> bigrams(String text) {
        Set<String> grams = HashSet.newHashSet(text.length());
        char prev = 0;
        boolean hasPrev = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                // Skip spaces but keep hasPrev so bigrams cross word boundaries,
                // preserving the original compact-string behaviour of replace(" ", "").
                continue;
            }
            if (hasPrev) {
                grams.add(new String(new char[]{prev, c}));
            }
            prev = c;
            hasPrev = true;
        }
        // Equivalent to the original compact.length() < 2 early-return cases:
        // - no non-space chars  → return Set.of()
        // - exactly one non-space char → return Set.of(that char)
        if (grams.isEmpty()) {
            return hasPrev ? Set.of(String.valueOf(prev)) : Set.of();
        }
        return grams;
    }

     static boolean isSimilarText(String left, String right,
                                 Set<String> leftBigrams,
                                 Set<String> rightBigrams) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        if (left.length() >= 8 && right.contains(left)) {
            return true;
        }
        if (right.length() >= 8 && left.contains(right)) {
            return true;
        }
        return diceCoefficient(leftBigrams, rightBigrams) >= NEAR_DUPLICATE_SIMILARITY;
    }

     static boolean hasCommonKeyword(String left, String right) {
        Set<String> leftWords = extractKeywords(left);
        Set<String> rightWords = extractKeywords(right);
        return hasCommonKeyword(leftWords, rightWords);
    }

    static boolean hasCommonKeyword(Set<String> leftWords, Set<String> rightWords) {
        if (leftWords.isEmpty() || rightWords.isEmpty()) {
            return false;
        }
        for (String word : leftWords) {
            if (rightWords.contains(word)) {
                return true;
            }
        }
        return false;
    }

    static Set<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> keywords = new LinkedHashSet<>();
        Matcher matcher = KEYWORD_PATTERN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 2) {
                keywords.add(token);
            }
        }
        return keywords;
    }

    private static double diceCoefficient(Set<String> leftBigrams, Set<String> rightBigrams) {
        if (leftBigrams.isEmpty() || rightBigrams.isEmpty()) {
            return 0.0d;
        }

        int overlap = 0;
        for (String gram : leftBigrams) {
            if (rightBigrams.contains(gram)) {
                overlap++;
            }
        }
        return (2.0d * overlap) / (leftBigrams.size() + rightBigrams.size());
    }
}
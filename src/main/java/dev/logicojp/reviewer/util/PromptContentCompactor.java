package dev.logicojp.reviewer.util;

/// Utility for character-budget prompt compaction.
public final class PromptContentCompactor {

    private static final String SOURCE_OMISSION_MARKER =
        "\n\n... (remaining source omitted for token budget)\n";

    private PromptContentCompactor() {
    }

    public static String compact(String content, int maxChars) {
        String safeContent = content != null ? content : "";
        if (maxChars <= 0 || safeContent.length() <= maxChars) {
            return safeContent;
        }

        String marker = omittedMarker(safeContent.length() - maxChars);
        int prefixLength = maxChars - marker.length();
        if (prefixLength <= 0) {
            return safeContent.substring(0, maxChars);
        }
        return safeContent.substring(0, prefixLength) + marker;
    }

    public static String compactKeepingTail(String content, int maxChars) {
        String safeContent = content != null ? content : "";
        if (maxChars <= 0 || safeContent.length() <= maxChars) {
            return safeContent;
        }

        String marker = omittedMarker(safeContent.length() - maxChars);
        int available = maxChars - marker.length();
        if (available <= 0) {
            return safeContent.substring(safeContent.length() - maxChars);
        }

        int headLength = Math.max(0, available / 3);
        int tailLength = available - headLength;
        return safeContent.substring(0, headLength)
            + marker
            + safeContent.substring(safeContent.length() - tailLength);
    }

    public static String compactSourceBlocks(String content, int maxChars) {
        String safeContent = content != null ? content : "";
        if (maxChars <= 0 || safeContent.length() <= maxChars) {
            return safeContent;
        }

        int contentBudget = maxChars - SOURCE_OMISSION_MARKER.length();
        int completeBlockEnd = findLastCompleteBlockEnd(safeContent, contentBudget);
        if (completeBlockEnd >= 0) {
            return safeContent.substring(0, completeBlockEnd) + SOURCE_OMISSION_MARKER;
        }

        FenceInfo firstFence = findFirstFence(safeContent);
        if (firstFence == null) {
            return compact(safeContent, maxChars);
        }

        String closingFence = "\n" + firstFence.fence() + "\n";
        int prefixBudget = contentBudget - closingFence.length();
        if (prefixBudget <= 0) {
            return compact(safeContent, maxChars);
        }

        int cutIndex = safeContent.lastIndexOf('\n', prefixBudget);
        if (cutIndex <= 0) {
            cutIndex = prefixBudget;
        }
        String prefix = safeContent.substring(0, cutIndex);
        String close = cutIndex >= firstFence.openingLineEnd() ? closingFence : "";
        return prefix + close + SOURCE_OMISSION_MARKER;
    }

    private static int findLastCompleteBlockEnd(String content, int maxEnd) {
        int searchStart = 0;
        int lastCompleteEnd = -1;
        while (searchStart < content.length()) {
            FenceInfo fenceInfo = findFence(content, searchStart);
            if (fenceInfo == null) {
                break;
            }
            String closingSequence = "\n" + fenceInfo.fence() + "\n\n";
            int closingStart = content.indexOf(closingSequence, fenceInfo.openingLineEnd());
            if (closingStart < 0) {
                break;
            }
            int blockEnd = closingStart + closingSequence.length();
            if (blockEnd > maxEnd) {
                break;
            }
            lastCompleteEnd = blockEnd;
            searchStart = blockEnd;
        }
        return lastCompleteEnd;
    }

    private static FenceInfo findFirstFence(String content) {
        return findFence(content, 0);
    }

    private static FenceInfo findFence(String content, int searchStart) {
        int headerStart = content.indexOf("### ", searchStart);
        if (headerStart < 0) {
            return null;
        }
        int separatorStart = content.indexOf("\n\n", headerStart);
        if (separatorStart < 0) {
            return null;
        }
        int openingStart = separatorStart + 2;
        int openingLineEnd = content.indexOf('\n', openingStart);
        if (openingLineEnd < 0) {
            return null;
        }

        int fenceEnd = openingStart;
        while (fenceEnd < openingLineEnd && content.charAt(fenceEnd) == '`') {
            fenceEnd++;
        }
        if (fenceEnd - openingStart < 3) {
            return null;
        }
        return new FenceInfo(content.substring(openingStart, fenceEnd), openingLineEnd);
    }

    private static String omittedMarker(int omittedChars) {
        return "\n\n... (" + Math.max(1, omittedChars) + " chars omitted for token budget)\n\n";
    }

    private record FenceInfo(String fence, int openingLineEnd) {
    }
}

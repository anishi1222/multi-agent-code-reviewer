package dev.logicojp.reviewer.domain.review;

/// Prompt template strings used during agent review execution.
///
/// Moved to the domain layer so that application use-cases can reference
/// these without depending on orchestration infrastructure.
///
/// @param focusAreasGuidance        guidance paragraph appended when focus areas are set
/// @param localSourceHeader         header injected before local source content
/// @param localReviewResultRequest  prompt suffix used when reviewing local source files
public record PromptTexts(
    String focusAreasGuidance,
    String localSourceHeader,
    String localReviewResultRequest
) {

    public PromptTexts {
        focusAreasGuidance = focusAreasGuidance != null ? focusAreasGuidance : "";
        localSourceHeader = localSourceHeader != null ? localSourceHeader : "";
        localReviewResultRequest = localReviewResultRequest != null ? localReviewResultRequest : "";
    }
}

package com.preplit.model;

import java.util.List;

/**
 * Final feedback report for the candidate.
 */
public record FeedbackReport(
    String summary,
    List<String> strengths,
    List<String> weaknesses,
    Verdict verdict,
    List<String> nextSteps,
    EvaluationScores scores
) {
}

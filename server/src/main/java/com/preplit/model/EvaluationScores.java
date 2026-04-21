package com.preplit.model;

/**
 * Tracks evaluation scores across five dimensions.
 * All scores are integers in range [0, 100].
 */
public record EvaluationScores(
    int problemUnderstanding,
    int approach,
    int correctness,
    int communication,
    int optimization
) {
    /**
     * Creates initial scores with all values at 50.
     */
    public static EvaluationScores initial() {
        return new EvaluationScores(50, 50, 50, 50, 50);
    }
    
    /**
     * Validates that all scores are within bounds [0, 100].
     */
    public EvaluationScores {
        problemUnderstanding = clamp(problemUnderstanding);
        approach = clamp(approach);
        correctness = clamp(correctness);
        communication = clamp(communication);
        optimization = clamp(optimization);
    }
    
    /**
     * Calculates the total score as average of all five scores.
     */
    public int totalScore() {
        return (problemUnderstanding + approach + correctness + communication + optimization) / 5;
    }
    
    /**
     * Creates new scores with updated problemUnderstanding.
     */
    public EvaluationScores withProblemUnderstanding(int delta) {
        return new EvaluationScores(
            problemUnderstanding + delta,
            approach,
            correctness,
            communication,
            optimization
        );
    }
    
    /**
     * Creates new scores with updated approach.
     */
    public EvaluationScores withApproach(int delta) {
        return new EvaluationScores(
            problemUnderstanding,
            approach + delta,
            correctness,
            communication,
            optimization
        );
    }
    
    /**
     * Creates new scores with updated correctness.
     */
    public EvaluationScores withCorrectness(int delta) {
        return new EvaluationScores(
            problemUnderstanding,
            approach,
            correctness + delta,
            communication,
            optimization
        );
    }
    
    /**
     * Creates new scores with updated communication.
     */
    public EvaluationScores withCommunication(int delta) {
        return new EvaluationScores(
            problemUnderstanding,
            approach,
            correctness,
            communication + delta,
            optimization
        );
    }
    
    /**
     * Creates new scores with updated optimization.
     */
    public EvaluationScores withOptimization(int delta) {
        return new EvaluationScores(
            problemUnderstanding,
            approach,
            correctness,
            communication,
            optimization + delta
        );
    }
    
    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}

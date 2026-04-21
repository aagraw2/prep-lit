package com.preplit.model;

import java.time.LocalDateTime;

/**
 * Tracks the candidate's state during the interview.
 * Includes confidence, engagement, frustration levels and stuck turn counter.
 */
public record CandidateStateModel(
    ConfidenceLevel confidence,
    EngagementLevel engagement,
    FrustrationLevel frustration,
    int stuckTurns,
    LocalDateTime lastUpdated
) {
    /**
     * Creates initial candidate state with default values.
     */
    public static CandidateStateModel initial() {
        return new CandidateStateModel(
            ConfidenceLevel.MEDIUM,
            EngagementLevel.MEDIUM,
            FrustrationLevel.LOW,
            0,
            LocalDateTime.now()
        );
    }
    
    /**
     * Validates that stuckTurns is within bounds [0, 5].
     */
    public CandidateStateModel {
        if (stuckTurns < 0 || stuckTurns > 5) {
            throw new IllegalArgumentException("stuckTurns must be between 0 and 5, got: " + stuckTurns);
        }
    }
    
    /**
     * Creates a new state with updated confidence level.
     */
    public CandidateStateModel withConfidence(ConfidenceLevel newConfidence) {
        return new CandidateStateModel(newConfidence, engagement, frustration, stuckTurns, LocalDateTime.now());
    }
    
    /**
     * Creates a new state with updated engagement level.
     */
    public CandidateStateModel withEngagement(EngagementLevel newEngagement) {
        return new CandidateStateModel(confidence, newEngagement, frustration, stuckTurns, LocalDateTime.now());
    }
    
    /**
     * Creates a new state with updated frustration level.
     */
    public CandidateStateModel withFrustration(FrustrationLevel newFrustration) {
        return new CandidateStateModel(confidence, engagement, newFrustration, stuckTurns, LocalDateTime.now());
    }
    
    /**
     * Creates a new state with incremented stuck turns (capped at 5).
     */
    public CandidateStateModel incrementStuckTurns() {
        return new CandidateStateModel(confidence, engagement, frustration, Math.min(stuckTurns + 1, 5), LocalDateTime.now());
    }
    
    /**
     * Creates a new state with stuck turns reset to 0.
     */
    public CandidateStateModel resetStuckTurns() {
        return new CandidateStateModel(confidence, engagement, frustration, 0, LocalDateTime.now());
    }
}

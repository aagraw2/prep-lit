package com.preplit.model;

/**
 * Represents the current phase of the interview.
 * Phases are sequential and determine interviewer behavior.
 */
public enum InterviewPhase {
    /**
     * Introduction phase - problem statement and expectations.
     */
    INTRO,
    
    /**
     * Clarification phase - candidate asks questions about requirements.
     */
    CLARIFICATION,
    
    /**
     * Approach phase - candidate describes solution approach.
     */
    APPROACH,
    
    /**
     * Implementation phase - candidate writes code or describes implementation.
     */
    IMPLEMENTATION,
    
    /**
     * Deep dive phase - optimization, edge cases, tradeoffs.
     */
    DEEP_DIVE,
    
    /**
     * Wrap up phase - final questions and feedback.
     */
    WRAP_UP
}

package com.preplit.model;

/**
 * Represents the current state of the interviewer based on candidate behavior.
 * Used to determine appropriate interviewer actions and responses.
 */
public enum InterviewerState {
    /**
     * Candidate is exploring the problem, asking questions, or thinking through approach.
     * Interviewer provides open-ended guidance.
     */
    EXPLORING,
    
    /**
     * Candidate is stuck and making no progress.
     * Interviewer offers hints or suggests alternative approaches.
     */
    STUCK,
    
    /**
     * Candidate is making good progress toward solution.
     * Interviewer asks deeper follow-up questions.
     */
    PROGRESSING,
    
    /**
     * Candidate shows low engagement (short responses, minimal effort).
     * Interviewer simplifies questions or attempts re-engagement.
     */
    DISENGAGED,
    
    /**
     * Interview is concluding.
     * Interviewer wraps up and provides feedback.
     */
    WRAPPING_UP
}

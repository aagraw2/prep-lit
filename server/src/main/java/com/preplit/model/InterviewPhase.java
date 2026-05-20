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
    WRAP_UP,

    /**
     * Requirements phase - candidate clarifies functional and non-functional
     * requirements for the system. Used by API_AND_DATABASE_DESIGN interviews.
     */
    REQUIREMENTS,

    /**
     * API design phase - candidate designs the REST/GraphQL API including
     * endpoints, request/response shapes, versioning, and authentication.
     * Used by API_AND_DATABASE_DESIGN interviews.
     */
    API_DESIGN,

    /**
     * Schema design phase - candidate designs the database schema including
     * entities, relationships, indexes, and normalization decisions.
     * Used by API_AND_DATABASE_DESIGN interviews.
     */
    SCHEMA_DESIGN
}

package com.preplit.model;

/**
 * Represents the level of hint provided to the candidate.
 * Hints progress from vague to specific.
 */
public enum HintLevel {
    /**
     * Level 1: Vague direction without specific data structures.
     */
    L1,
    
    /**
     * Level 2: Structural hint with data structure suggestion.
     */
    L2,
    
    /**
     * Level 3: Strong hint with specific implementation details.
     */
    L3
}

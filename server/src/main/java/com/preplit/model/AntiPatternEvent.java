package com.preplit.model;

import java.time.LocalDateTime;

/**
 * Records an anti-pattern detected during the interview.
 */
public record AntiPatternEvent(
    AntiPattern pattern,
    LocalDateTime timestamp
) {
    public static AntiPatternEvent of(AntiPattern pattern) {
        return new AntiPatternEvent(pattern, LocalDateTime.now());
    }
}

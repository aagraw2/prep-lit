package com.preplit.model;

import java.time.LocalDateTime;

/**
 * Records a hint given to the candidate.
 */
public record HintRecord(
    HintLevel level,
    String text,
    LocalDateTime timestamp
) {
    public static HintRecord of(HintLevel level, String text) {
        return new HintRecord(level, text, LocalDateTime.now());
    }
}

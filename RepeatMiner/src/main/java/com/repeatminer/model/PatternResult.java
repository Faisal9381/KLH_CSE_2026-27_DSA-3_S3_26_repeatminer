package com.repeatminer.model;

/**
 * One detected repeated word sequence ("pattern").
 *
 * <p>Example: the document {@code "the cat sat. the cat sleeps."} contains the
 * pattern {@code "the cat"} with {@code wordCount = 2} and {@code frequency = 2}.
 * Patterns always consist of complete word tokens — never character fragments
 * (requirement 3), because the suffix structures are built over word tokens.
 *
 * <p>Immutable value type. Fields are finalised already in Phase 1 so that later
 * phases share one stable vocabulary; behaviour is filled in during Phase 8.
 */
public record PatternResult(String phrase, int wordCount, int frequency,
                            int firstPosition, int[] positions) {

    public PatternResult {
        if (phrase == null || phrase.isBlank()) {
            throw new IllegalArgumentException("phrase must not be blank");
        }
        if (wordCount <= 0) {
            throw new IllegalArgumentException("wordCount must be positive: " + wordCount);
        }
        if (frequency <= 0) {
            throw new IllegalArgumentException("frequency must be positive: " + frequency);
        }
        positions = positions == null ? new int[0] : positions.clone();
    }

    /** Convenience constructor when occurrence positions are not tracked. */
    public PatternResult(String phrase, int wordCount, int frequency) {
        this(phrase, wordCount, frequency, -1, new int[0]);
    }

    /** Token indices (into the document token array) where the pattern starts. */
    public int[] positions() {
        return positions.clone();
    }

    /** Human-readable first-occurrence position for reports. */
    public String firstPositionOrUnknown() {
        return firstPosition >= 0 ? Integer.toString(firstPosition) : "n/a";
    }
}

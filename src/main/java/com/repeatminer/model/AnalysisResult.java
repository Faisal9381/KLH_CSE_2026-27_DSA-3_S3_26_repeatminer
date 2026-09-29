package com.repeatminer.model;

import java.util.List;

/**
 * The complete result of one analysis run, handed from the algorithm core to the
 * UI and the report generator (requirement 25).
 *
 * <p>Populated by {@code detection.PatternDetector} in Phases 7&ndash;8; the record
 * shape is fixed now so UI and reporting can be built against it.
 *
 * @param documentName    display name of the analysed file (never a full path)
 * @param totalCharacters character count of the raw document
 * @param totalTokens     number of word tokens after preprocessing
 * @param vocabularySize  number of distinct tokens (integer IDs 0..vocabularySize-1)
 * @param patterns        detected repeated patterns, already sorted for display
 * @param longestPatterns all patterns sharing the maximum word count
 *                        (tie-break within them: higher frequency first — requirement 12)
 * @param minPhraseLength minimum phrase length requested by the user
 * @param minFrequency    minimum frequency requested by the user
 */
public record AnalysisResult(String documentName,
                             long totalCharacters,
                             int totalTokens,
                             int vocabularySize,
                             List<PatternResult> patterns,
                             List<PatternResult> longestPatterns,
                             int minPhraseLength,
                             int minFrequency) {

    public AnalysisResult {
        patterns = patterns == null ? List.of() : List.copyOf(patterns);
        longestPatterns = longestPatterns == null ? List.of() : List.copyOf(longestPatterns);
    }
}

package com.repeatminer.naive;

import com.repeatminer.model.PatternResult;
import com.repeatminer.preprocessing.Vocabulary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 9: the deliberately naive baseline detector (requirement 14).
 *
 * <p><b>The obvious strategy.</b> For every phrase length L from
 * {@code minPhraseLength} to {@code maxPhraseLength}, slide a window over the
 * token array, build the phrase STRING for each window, and count occurrences in
 * a hash map; keep phrases with count >= {@code minFrequency}. No suffix array,
 * no LCP array, no cleverness — this is exactly what one would write before
 * knowing anything about suffix structures, which is the point of the baseline.
 *
 * <p><b>Why the suffix approach wins (documented, measured in Phase 10).</b>
 * This method re-reads and re-builds overlapping regions of the text once per
 * phrase length and pays string hashing per window: expected work around
 * O(n · maxLen²) character operations for maxLen phrase lengths. The suffix
 * approach pays O(n log² n) ONCE to build its index and then O(n + output) per
 * query — and, crucially, it never enumerates substrings that occur only once.
 * Real measured numbers live in {@code PerformanceBenchmark}; no claims beyond
 * that (requirement 14).
 *
 * <p>Both detectors return the same {@code PatternDetector.DetectionResult}
 * shape and are sorted with the same display order, so the GUI can swap them
 * transparently and tests can assert equality of results.
 */
public final class NaivePatternDetector {

    public record Parameters(int minPhraseLength, int minFrequency, int maxPatterns,
                             int maxPhraseLength) {

        public Parameters {
            if (minPhraseLength < 1) {
                throw new IllegalArgumentException("minPhraseLength must be >= 1");
            }
            if (minFrequency < 2) {
                throw new IllegalArgumentException("minFrequency must be >= 2");
            }
            if (maxPatterns < 1) {
                throw new IllegalArgumentException("maxPatterns must be >= 1");
            }
            if (maxPhraseLength < minPhraseLength) {
                throw new IllegalArgumentException(
                        "maxPhraseLength must be >= minPhraseLength");
            }
        }

        public static Parameters defaults() {
            return new Parameters(2, 2, 50, 8);
        }
    }

    private static final Comparator<PatternResult> DISPLAY_ORDER =
            Comparator.comparingInt(PatternResult::frequency).reversed()
                    .thenComparing(Comparator.comparingInt(PatternResult::wordCount).reversed())
                    .thenComparing(PatternResult::phrase);

    private final int[] tokenIds;
    private final Vocabulary vocabulary;

    public NaivePatternDetector(int[] tokenIds, Vocabulary vocabulary) {
        if (tokenIds == null || tokenIds.length == 0) {
            throw new IllegalArgumentException("token IDs must contain at least one token");
        }
        if (vocabulary == null) {
            throw new IllegalArgumentException("vocabulary must not be null");
        }
        this.tokenIds = tokenIds.clone();
        this.vocabulary = vocabulary;
    }

    /** Runs the naive enumeration with the given parameters. */
    public com.repeatminer.detection.PatternDetector.DetectionResult detect(Parameters params) {
        int n = tokenIds.length;
        List<PatternResult> all = new ArrayList<>();
        int longestFound = 0;

        for (int length = params.minPhraseLength(); length <= params.maxPhraseLength(); length++) {
            if (length > n) {
                break;
            }
            Map<String, Integer> counts = new HashMap<>();
            Map<String, Integer> firstPos = new HashMap<>();

            for (int start = 0; start + length <= n; start++) {
                StringBuilder builder = new StringBuilder();
                for (int k = 0; k < length; k++) {
                    if (k > 0) {
                        builder.append(' ');
                    }
                    String word = vocabulary.wordFor(tokenIds[start + k]);
                    builder.append(word != null ? word : "#" + tokenIds[start + k]);
                }
                String phrase = builder.toString();
                counts.merge(phrase, 1, Integer::sum);
                firstPos.putIfAbsent(phrase, start);
            }

            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (entry.getValue() >= params.minFrequency()) {
                    all.add(new PatternResult(entry.getKey(), length, entry.getValue(),
                            firstPos.getOrDefault(entry.getKey(), -1),
                            new int[0]));
                    longestFound = Math.max(longestFound, length);
                }
            }
        }

        all.sort(DISPLAY_ORDER);
        List<PatternResult> capped =
                new ArrayList<>(all.subList(0, Math.min(params.maxPatterns(), all.size())));

        List<PatternResult> longest = new ArrayList<>();
        for (PatternResult result : all) {
            if (result.wordCount() == longestFound) {
                longest.add(result);
            }
        }

        return new com.repeatminer.detection.PatternDetector.DetectionResult(
                List.copyOf(capped), List.copyOf(longest));
    }
}

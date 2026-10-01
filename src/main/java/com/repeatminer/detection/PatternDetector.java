package com.repeatminer.detection;

import com.repeatminer.model.PatternResult;
import com.repeatminer.preprocessing.Vocabulary;
import com.repeatminer.suffix.LCPArray;
import com.repeatminer.suffix.SuffixArray;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Phase 7: detects repeated word sequences using the Suffix Array + LCP Array
 * (requirements 10-13). This is the heart of the application.
 *
 * <p><b>The key structural fact.</b> All occurrences of a given phrase are
 * CONTIGUOUS in the suffix array, because the SA is sorted: suffixes starting
 * with the same words are neighbours. So every distinct repeated phrase
 * corresponds to exactly one <em>LCP-interval</em> &mdash; a block
 * {@code SA[lb..rb]} in which every internal LCP value is at least the phrase
 * length &mdash; and the phrase's frequency is the block width {@code rb-lb+1}.
 *
 * <p><b>The stack sweep (one pass, no deduplication).</b> Walk SA indices
 * {@code i = 1..n} with {@code L = lcp[i]} (a 0 sentinel closes the walk). Keep a
 * stack of {@code (lcpValue, intervalStart)} pairs:
 *
 * <ul>
 *   <li>while the top's value exceeds {@code L}, that interval has ended: pop it
 *       and EMIT it with depth = phrase length and width = frequency;</li>
 *   <li>push a new candidate interval when the top's value is strictly smaller
 *       than {@code L}.</li>
 * </ul>
 *
 * Each suffix is pushed and popped at most once, and each distinct repeated
 * phrase is emitted exactly once (equal phrases share one interval, so no hash
 * deduplication is needed). Sub-phrases such as {@code "the"} inside
 * {@code "the cat"} are ancestor intervals and fall out of the nesting
 * automatically. Hand trace on {@code [the, cat, the, cat, sleeps]}: the sweep
 * emits {@code [the, cat] x2}, {@code [the] x2}, {@code [cat] x2}.
 *
 * <p><b>Filtering and sorting (requirements 10, 13).</b> Emitted intervals are
 * filtered by {@code minPhraseLength} and {@code minFrequency}, sorted by
 * frequency descending, then phrase length descending, then alphabetically
 * ascending, and capped at {@code maxPatterns}. The <em>longest</em> repeated
 * phrases are all intervals achieving the maximum length among intervals that
 * ALSO satisfy the frequency threshold (requirement 12: a longest pattern that
 * occurs too rarely is not reported); ties are ordered by frequency descending,
 * then alphabetically.
 *
 * <p><b>Complexity (requirement 22).</b> SA construction O(n log² n) and LCP
 * construction O(n) happen once in the constructor. {@link #detect} is an O(n)
 * sweep plus output-size work for phrase reconstruction &mdash; O(n + total
 * emitted phrase characters). Pathological inputs (e.g. one token repeated a
 * million times) genuinely have ~n²/2 distinct repeated phrases, so the output
 * itself &mdash; not the algorithm &mdash; is the bound; {@code maxPatterns}
 * caps what is returned, and construction structures are reused across calls,
 * so the GUI can re-run with different thresholds for free.
 */
public final class PatternDetector {

    /** Detection parameters (requirement 10 defaults; 50 max patterns). */
    public record Parameters(int minPhraseLength, int minFrequency, int maxPatterns) {

        public Parameters {
            if (minPhraseLength < 1) {
                throw new IllegalArgumentException(
                        "Minimum phrase length must be at least 1 word (got " + minPhraseLength + ")");
            }
            if (minFrequency < 2) {
                throw new IllegalArgumentException(
                        "Minimum frequency must be at least 2 occurrences (got " + minFrequency + ")");
            }
            if (maxPatterns < 1) {
                throw new IllegalArgumentException(
                        "Maximum patterns must be at least 1 (got " + maxPatterns + ")");
            }
        }

        public static Parameters defaults() {
            return new Parameters(2, 2, 50);
        }
    }

    /** Both result lists of one detection run; consumed by Phase 8's AnalysisResult. */
    public record DetectionResult(List<PatternResult> patterns, List<PatternResult> longestPatterns) {

        public DetectionResult {
            patterns = patterns == null ? List.of() : List.copyOf(patterns);
            longestPatterns = longestPatterns == null ? List.of() : List.copyOf(longestPatterns);
        }
    }

    private static final Comparator<PatternResult> DISPLAY_ORDER =
            Comparator.comparingInt(PatternResult::frequency).reversed()
                    .thenComparing(Comparator.comparingInt(PatternResult::wordCount).reversed())
                    .thenComparing(PatternResult::phrase);

    private final int[] text;
    private final Vocabulary vocabulary;
    private final int[] suffixArray;
    private final int[] lcp;

    /**
     * Builds the suffix structures once; {@link #detect} can then be called any
     * number of times with different parameters.
     *
     * @param tokenIds   the document as token IDs (from TextProcessor + Vocabulary)
     * @param vocabulary the token-to-word mapping, used to rebuild readable phrases
     */
    public PatternDetector(int[] tokenIds, Vocabulary vocabulary) {
        if (tokenIds == null || tokenIds.length == 0) {
            throw new IllegalArgumentException("token IDs must contain at least one token");
        }
        if (vocabulary == null) {
            throw new IllegalArgumentException("vocabulary must not be null");
        }
        this.text = tokenIds.clone();
        this.vocabulary = vocabulary;
        this.suffixArray = new SuffixArray(text).getSuffixArray();
        this.lcp = new LCPArray(text, suffixArray).getLcpArray();
    }

    /**
     * Runs the detection sweep with the given parameters.
     */
    public DetectionResult detect(Parameters params) {
        int n = text.length;

        // Explicit stacks (primitive arrays, no boxing) — requirement 22.
        int[] stackValue = new int[n + 1];
        int[] stackStart = new int[n + 1];
        int top = -1;

        List<PatternResult> all = new ArrayList<>();
        int maxLength = 0;

        for (int i = 1; i <= n; i++) {
            int l = (i == n) ? 0 : lcp[i];
            int currentStart = i - 1;

            while (top >= 0 && stackValue[top] > l) {
                int depth = stackValue[top];
                int start = stackStart[top];
                top--;
                int count = i - start; // interval [start, i-1] -> width i-start

                // Every depth between the entry below and this one shares the same
                // bounds [start, i-1]: lcp[start] equals the below entry's value
                // (no left extension) and lcp[i] = l (no right extension). Emitting
                // only 'depth' would LOSE those nested phrases - e.g. 'w0 w1 w1 w0'
                // under 'w0 w1 w1 w0 w1'. Each depth is emitted exactly once, from
                // the entry whose below-value sits just under it.
                int below = top >= 0 ? stackValue[top] : 0;
                int lowest = Math.max(l, below) + 1;

                for (int d = lowest; d <= depth; d++) {
                    if (d >= params.minPhraseLength() && count >= params.minFrequency()) {
                        PatternResult result = new PatternResult(
                                phraseFor(start, d), d, count);
                        all.add(result);
                        maxLength = Math.max(maxLength, d);
                    }
                }
                currentStart = start;
            }
            if (top < 0 || stackValue[top] < l) {
                top++;
                stackValue[top] = l;
                stackStart[top] = currentStart;
            }
        }

        all.sort(DISPLAY_ORDER);
        List<PatternResult> capped =
                all.subList(0, Math.min(params.maxPatterns(), all.size()));

        List<PatternResult> longest = new ArrayList<>();
        for (PatternResult result : all) { // 'all' already in display order
            if (result.wordCount() == maxLength) {
                longest.add(result);
            }
        }

        return new DetectionResult(List.copyOf(capped), List.copyOf(longest));
    }

    /** Rebuilds the readable phrase: tokens text[SA[start] .. SA[start]+depth-1]. */
    private String phraseFor(int saIndex, int depth) {
        StringBuilder builder = new StringBuilder();
        int textStart = suffixArray[saIndex];
        for (int k = 0; k < depth; k++) {
            if (k > 0) {
                builder.append(' ');
            }
            String word = vocabulary.wordFor(text[textStart + k]);
            builder.append(word != null ? word : ("#" + text[textStart + k]));
        }
        return builder.toString();
    }
}

package com.repeatminer.suffix;

/**
 * Phase 6: LCP (Longest Common Prefix) array over word tokens, built with
 * Kasai's algorithm (requirement 9).
 *
 * <p><b>What LCP means here.</b> {@code lcp[i]} is the number of consecutive
 * word tokens shared by the ADJACENT suffixes {@code SA[i-1]} and {@code SA[i]}
 * in sorted order; {@code lcp[0] = 0} because suffix {@code SA[0]} has no
 * predecessor. Example for tokens {@code [the, cat, the, cat, sleeps]}
 * (SA = [0, 2, 1, 3, 4]):
 *
 * <pre>
 *   SA index   suffix                    lcp   shared phrase
 *   0          the cat the cat sleeps     0    -
 *   1          the cat sleeps             2    "the cat"
 *   2          cat the cat sleeps         0    -
 *   3          cat sleeps                 1    "cat"
 *   4          sleeps                     0    -
 * </pre>
 *
 * {@code lcp[1] = 2} literally IS the repeated phrase "the cat" occurring twice —
 * {@code detection.PatternDetector} (Phase 7) scans these values to enumerate
 * repeated word sequences without ever materialising substrings.
 *
 * <p><b>How Kasai's algorithm works, and why it is O(n).</b> The naive way
 * compares each adjacent suffix pair from scratch: O(n) per pair, O(n²) overall.
 * Kasai instead processes suffixes in TEXT order (position 0, 1, 2, ...) while
 * carrying the value {@code h} = the LCP just measured. Removing the first token
 * from two suffixes that share {@code h} tokens leaves suffixes sharing
 * {@code h - 1} tokens, so the next suffix's LCP starts from at least
 * {@code h - 1} — comparisons resume where the previous round stopped instead of
 * from zero. Over the whole run, {@code h} drops at most once per position, so
 * the compare loop executes at most 2n token comparisons in TOTAL: O(n) time,
 * O(n) memory (the inverse-suffix permutation array).
 *
 * <p><b>Complexity (requirement 22):</b> O(n) time, O(n) auxiliary memory.
 * Requires a correctly built suffix array (Phase 4); the two structures are
 * always produced together by the pipeline.
 */
public final class LCPArray {

    private final int[] text;
    private final int[] suffixArray;
    private final int[] lcp;

    /**
     * Builds the LCP array for a token sequence and its suffix array using
     * Kasai's algorithm. Both inputs are copied; the caller keeps ownership.
     *
     * @param text       token IDs, non-empty, values >= 0
     * @param suffixArray the suffix array of {@code text} (a permutation of 0..n-1)
     */
    public LCPArray(int[] text, int[] suffixArray) {
        if (text == null || text.length == 0) {
            throw new IllegalArgumentException("text must contain at least one token");
        }
        if (suffixArray == null || suffixArray.length != text.length) {
            throw new IllegalArgumentException(
                    "suffix array must be non-null and match the text length");
        }
        this.text = text.clone();
        this.suffixArray = suffixArray.clone();
        this.lcp = new int[text.length];
        build();
    }

    private void build() {
        int n = text.length;
        if (n == 1) {
            lcp[0] = 0;
            return;
        }

        // inverse[i] = SA index at which the suffix starting at position i appears
        int[] inverse = new int[n];
        for (int i = 0; i < n; i++) {
            inverse[suffixArray[i]] = i;
        }

        // Walk suffixes in text order; h = LCP shared with the text-order
        // predecessor. h never increases and drops at most 1 per step, so the
        // total comparison work is bounded by 2n.
        int h = 0;
        for (int i = 0; i < n; i++) {
            int saIndex = inverse[i];
            if (saIndex > 0) {
                int predecessor = suffixArray[saIndex - 1];
                while (i + h < n && predecessor + h < n
                        && text[i + h] == text[predecessor + h]) {
                    h++;
                }
                lcp[saIndex] = h;
                if (h > 0) {
                    h--; // Kasai's step: chopping one token keeps h-1 matches
                }
            } else {
                h = 0; // first suffix in sorted order has no predecessor
            }
        }
    }

    /** The built LCP array — a copy is returned. */
    public int[] getLcpArray() {
        return lcp.clone();
    }

    /**
     * LCP value at SA index {@code i}: tokens shared by suffixes
     * {@code SA[i-1]} and {@code SA[i]}; 0 for {@code i = 0}.
     */
    public int lcpAt(int i) {
        if (i < 0 || i >= lcp.length) {
            throw new IndexOutOfBoundsException("SA index " + i + " out of range [0," + lcp.length + ")");
        }
        return lcp[i];
    }
}

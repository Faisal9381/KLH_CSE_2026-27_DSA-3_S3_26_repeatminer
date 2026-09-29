package com.repeatminer.suffix;

/**
 * Phase 4: suffix array over a sequence of word-token IDs (requirement 8).
 *
 * <p><b>What a suffix is here.</b> For a token sequence T of length n, suffix i is
 * the sub-sequence T[i..n-1]. Example: tokens {@code [the, cat, the, cat, sleeps]}
 * (IDs {@code [0,1,0,1,2]}) have five suffixes, and suffix 2 is
 * {@code [the, cat, sleeps]}. The suffix array is the list of suffix start
 * positions sorted by their token sequences:
 *
 * <pre>
 *   SA = [0, 2, 1, 3, 4]   for [0, 1, 0, 1, 2]
 * </pre>
 *
 * <p>The key property: suffixes that begin with the same words are ADJACENT in
 * sorted order. Suffixes 0 and 2 above both start with "the cat" and are
 * neighbours — repeated phrases appear as adjacency, which is what
 * {@code detection.PatternDetector} exploits in Phase 7.
 *
 * <p><b>How it is built: prefix doubling with rank pairs.</b> Comparing whole
 * suffixes directly would cost O(n) per comparison. Instead we sort in rounds
 * over integer ranks, never re-scanning words:
 *
 * <ol>
 *   <li>Round 1: rank every suffix by its first token ID (the token ID itself)
 *       and sort.</li>
 *   <li>Round k: the first 2^k tokens of a suffix are fully described by the
 *       PAIR (my rank for 2^(k-1) tokens, the rank of the suffix starting
 *       2^(k-1) positions later for 2^(k-1) tokens). A suffix that runs past the
 *       end gets the second key -1, which sorts before everything — exactly
 *       right, because a shorter suffix that matches a longer one is the smaller
 *       one. Sorting by the pair yields the rank order for 2^k tokens.</li>
 *   <li>Re-rank from the sorted order, double the prefix length, repeat until
 *       every rank is distinct.</li>
 * </ol>
 *
 * <p><b>A bug we hit while building this (worth remembering):</b> the first
 * version sorted suffixes only by their OLD rank and then decided ties by
 * comparing pairs — that is wrong, because ties must be ordered BY the pair, not
 * left in arbitrary position order. Example: for [0,0,0] the suffix [0] must
 * sort before [0,0], but both share the old rank 0. Sorting by the explicit pair
 * (old rank, neighbour rank) fixes it. The brute-force property test caught it.
 *
 * <p><b>Ordering note:</b> IDs reflect first-seen word order, not the alphabet,
 * so suffixes sort by ID order rather than A-to-Z. That is correct for a suffix
 * array — any fixed total order works, because repeat detection only needs equal
 * sequences to become adjacent. Human-readable output is re-sorted
 * alphabetically later (Phase 8) if requested.
 *
 * <p><b>Sorting implementation.</b> Merge sort over suffix start positions with
 * an explicit buffer: stable, two-int comparisons per step, O(n) auxiliary
 * memory, entirely our own code (requirement 24 — no library suffix
 * construction).
 *
 * <p><b>Complexity (honest version, requirement 22):</b> O(log n) rounds, each an
 * O(n log n) two-int merge sort plus O(n) re-ranking &rarr; <b>O(n log² n) time</b>,
 * <b>O(n) extra memory</b>. (A counting-sort variant would reach O(n log n);
 * listed as a future optimisation, not claimed.)
 */
public final class SuffixArray {

    private final int[] text;
    private final int[] suffixArray;
    private final int[] rank;
    private final int[] nextKey;

    /**
     * Builds the suffix array for the given token-ID sequence using prefix
     * doubling. The input array is copied; the caller keeps ownership.
     *
     * @param text token IDs, must be non-empty and contain only values >= 0
     */
    public SuffixArray(int[] text) {
        if (text == null || text.length == 0) {
            throw new IllegalArgumentException("text must contain at least one token");
        }
        for (int id : text) {
            if (id < 0) {
                throw new IllegalArgumentException("token IDs must be non-negative, saw " + id);
            }
        }
        this.text = text.clone();
        this.suffixArray = new int[text.length];
        this.rank = new int[text.length];
        this.nextKey = new int[text.length];
        build();
    }

    private void build() {
        int n = text.length;

        // Round 1: rank by first token = the token ID itself; no second key yet.
        for (int i = 0; i < n; i++) {
            rank[i] = text[i];
            nextKey[i] = 0;
        }
        sortSuffixArrayByPair(n);

        // Prefix doubling: on entry to each iteration, rank[] describes the
        // first p tokens of every suffix and SA is sorted accordingly.
        for (int p = 1; ; p <<= 1) {
            for (int i = 0; i < n; i++) {
                nextKey[i] = i + p < n ? rank[i + p] : -1;
            }
            sortSuffixArrayByPair(n);
            boolean allDistinct = reassignRanks(n);
            if (allDistinct || p >= n) {
                break;
            }
        }
    }

    /**
     * Sorts suffixArray (positions 0..n) by the pair (rank, nextKey) with our own
     * stable merge sort. Positions are never re-scanned against the text.
     */
    private void sortSuffixArrayByPair(int n) {
        for (int i = 0; i < n; i++) {
            suffixArray[i] = i;
        }
        mergeSort(suffixArray, 0, n, new int[n]);
    }

    private void mergeSort(int[] a, int from, int to, int[] buffer) {
        if (to - from < 2) {
            return;
        }
        int mid = (from + to) >>> 1;
        mergeSort(a, from, mid, buffer);
        mergeSort(a, mid, to, buffer);
        int left = from, right = mid, out = 0;
        while (left < mid && right < to) {
            if (pairLess(a[right], a[left])) {
                buffer[out++] = a[right++];
            } else {
                buffer[out++] = a[left++]; // ties keep left first: stable
            }
        }
        while (left < mid) {
            buffer[out++] = a[left++];
        }
        while (right < to) {
            buffer[out++] = a[right++];
        }
        System.arraycopy(buffer, 0, a, from, to - from);
    }

    /** True when the pair of suffix {@code a} sorts strictly before that of {@code b}. */
    private boolean pairLess(int a, int b) {
        if (rank[a] != rank[b]) {
            return rank[a] < rank[b];
        }
        return nextKey[a] < nextKey[b];
    }

    /**
     * Assigns new ranks from the current SA order (SA is sorted by pair, so equal
     * pairs are adjacent).
     *
     * @return true when every suffix received a distinct rank
     */
    private boolean reassignRanks(int n) {
        int[] newRank = new int[n];
        int current = 0;
        newRank[suffixArray[0]] = 0;
        for (int i = 1; i < n; i++) {
            int prev = suffixArray[i - 1];
            int curr = suffixArray[i];
            if (rank[prev] != rank[curr] || nextKey[prev] != nextKey[curr]) {
                current++;
            }
            newRank[curr] = current;
        }
        System.arraycopy(newRank, 0, rank, 0, n);
        return current == n - 1;
    }

    /** The built suffix array — a copy is returned. */
    public int[] getSuffixArray() {
        return suffixArray.clone();
    }

    /** Final rank of the suffix starting at {@code position} (smaller = lexicographically smaller suffix). */
    public int rankOf(int position) {
        checkPosition(position);
        return rank[position];
    }

    /**
     * Compares suffixes at two positions by their token sequences.
     *
     * @return negative if suffix(a) &lt; suffix(b), positive if greater, zero if identical
     */
    public int compareSuffixes(int a, int b) {
        checkPosition(a);
        checkPosition(b);
        while (a < text.length && b < text.length) {
            if (text[a] != text[b]) {
                return Integer.compare(text[a], text[b]);
            }
            a++;
            b++;
        }
        return Integer.compare(text.length - a, text.length - b);
    }

    /**
     * Binary search for the pattern in sorted-suffix order.
     *
     * @return {@code [first, last)} — the half-open range of suffix-array
     *         indices whose suffixes start with the pattern, or {@code [-1, -1]}
     *         when the pattern does not occur
     */
    public int[] search(int[] pattern) {
        if (pattern == null || pattern.length == 0) {
            return new int[] {-1, -1};
        }
        int lo = 0, hi = text.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (comparePrefixWithSuffix(pattern, suffixArray[mid]) <= 0) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        if (lo == text.length || comparePrefixWithSuffix(pattern, suffixArray[lo]) != 0) {
            return new int[] {-1, -1};
        }
        int first = lo;
        // Upper bound: first suffix strictly GREATER than the pattern (compare < 0
        // means pattern < suffix under our sign convention). Using <= 0 here would
        // just converge to the block start again and yield an empty range.
        lo = first;
        hi = text.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (comparePrefixWithSuffix(pattern, suffixArray[mid]) < 0) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return new int[] {first, lo};
    }

    /** Compares the pattern with the first pattern.length tokens of a suffix. */
    private int comparePrefixWithSuffix(int[] pattern, int suffixStart) {
        for (int i = 0; i < pattern.length; i++) {
            int pos = suffixStart + i;
            if (pos >= text.length) {
                return 1; // suffix ended early: pattern is greater
            }
            if (pattern[i] != text[pos]) {
                return Integer.compare(pattern[i], text[pos]);
            }
        }
        return 0;
    }

    /** Number of tokens whose suffixes were indexed. */
    public int length() {
        return text.length;
    }

    private void checkPosition(int position) {
        if (position < 0 || position >= text.length) {
            throw new IndexOutOfBoundsException("position " + position + " out of range [0," + text.length + ")");
        }
    }
}

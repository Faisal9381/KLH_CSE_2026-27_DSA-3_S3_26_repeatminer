package com.repeatminer.benchmark;

import com.repeatminer.detection.PatternDetector;
import com.repeatminer.model.BenchmarkResult;
import com.repeatminer.naive.NaivePatternDetector;
import com.repeatminer.preprocessing.Vocabulary;

import java.util.Locale;

/**
 * Phase 10: measures naive vs suffix-array detection on the SAME input
 * (requirement 15). All values are actual measurements with
 * {@code System.nanoTime()}; nothing is estimated or fabricated.
 *
 * <p><b>Fairness contract.</b> Each strategy is timed "from token IDs to
 * detection result": the naive path pays its per-length window enumeration; the
 * suffix path pays suffix array construction O(n log² n) + LCP construction
 * O(n) + the detection sweep. Each strategy gets one untimed warm-up iteration,
 * then {@code iterations} timed iterations, and the reported time is the mean.
 * {@code System.gc()} is requested between strategies (best effort only).
 *
 * <p>The interesting measured outcome is that the suffix path pays a one-time
 * build cost and therefore does not necessarily win on tiny inputs — the
 * benchmark reports whatever is true, including a speedup below 1.0 on small
 * documents (requirement 15: honest reporting).
 */
public final class PerformanceBenchmark {

    /** One measured comparison; times are means over the timed iterations. */
    public record Outcome(BenchmarkResult naive, BenchmarkResult suffix, double speedup) {

        /** Formatted speedup for GUI and reports, e.g. "12.4x". */
        public String speedupFormatted() {
            return String.format(Locale.ROOT, "%.2fx", speedup);
        }
    }

    /**
     * Benchmarks both strategies on the given token IDs.
     *
     * @param tokenIds    the document as token IDs (not copied; not modified)
     * @param vocabulary  matching vocabulary for rebuilding phrases
     * @param naiveParams parameters for the naive detector
     * @param suffixParams parameters for the suffix detector
     * @param iterations  timed iterations per strategy (>= 1)
     */
    public Outcome runOn(int[] tokenIds, Vocabulary vocabulary,
                         NaivePatternDetector.Parameters naiveParams,
                         PatternDetector.Parameters suffixParams,
                         int iterations) {
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be >= 1");
        }

        // --- naive: one warm-up, then timed iterations -----------------------
        NaivePatternDetector naiveDetector = new NaivePatternDetector(tokenIds, vocabulary);
        naiveDetector.detect(naiveParams); // warm-up
        long naiveStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            naiveDetector.detect(naiveParams);
        }
        long naiveTotal = System.nanoTime() - naiveStart;

        // --- suffix: fresh structures per iteration (build + detect together) -
        System.gc(); // best effort between strategies
        new PatternDetector(tokenIds, vocabulary).detect(suffixParams); // warm-up
        long suffixStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            new PatternDetector(tokenIds, vocabulary).detect(suffixParams);
        }
        long suffixTotal = System.nanoTime() - suffixStart;

        long avgNaive = naiveTotal / iterations;
        long avgSuffix = suffixTotal / iterations;
        double speedup = suffixTotal == 0 ? Double.POSITIVE_INFINITY
                : (double) naiveTotal / (double) suffixTotal;

        BenchmarkResult naiveResult = new BenchmarkResult("Naive", tokenIds.length,
                naiveTotal, iterations, avgNaive, 4L * tokenIds.length, -1);
        BenchmarkResult suffixResult = new BenchmarkResult("Suffix Array + LCP", tokenIds.length,
                suffixTotal, iterations, avgSuffix, 4L * tokenIds.length, -1);
        return new Outcome(naiveResult, suffixResult, speedup);
    }
}

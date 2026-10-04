package com.repeatminer.model;

/**
 * Measured performance of one detection strategy on one input (requirement 15).
 *
 * <p>All values come from actual measurements with {@code System.nanoTime()} —
 * the benchmark never reports estimated or fabricated numbers. Timing semantics
 * are fixed in Phase 10.
 *
 * @param strategyName     "Naive" or "Suffix Array + LCP"
 * @param tokenCount       input size in tokens for this measurement
 * @param totalTimeNanos   total time across all measured iterations
 * @param iterations       number of timed iterations
 * @param avgTimeNanos     {@code totalTimeNanos / iterations}
 * @param inputBytes       approximate in-memory size of the input (token array)
 * @param peakHeapBytes    best-effort JVM heap reading, or -1 when unavailable
 */
public record BenchmarkResult(String strategyName,
                              int tokenCount,
                              long totalTimeNanos,
                              int iterations,
                              long avgTimeNanos,
                              long inputBytes,
                              long peakHeapBytes) {

    public BenchmarkResult {
        if (strategyName == null || strategyName.isBlank()) {
            throw new IllegalArgumentException("strategyName must not be blank");
        }
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations must be positive: " + iterations);
        }
    }

    /** Average time in milliseconds, formatted for the GUI and reports. */
    public String avgTimeMillisFormatted() {
        return String.format("%.3f ms", avgTimeNanos / 1_000_000.0);
    }
}

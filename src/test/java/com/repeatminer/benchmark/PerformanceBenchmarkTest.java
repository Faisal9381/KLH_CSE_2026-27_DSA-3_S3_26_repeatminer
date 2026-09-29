package com.repeatminer.benchmark;

import com.repeatminer.detection.AnalysisEngine;
import com.repeatminer.detection.PatternDetector;
import com.repeatminer.model.AnalysisResult;
import com.repeatminer.naive.NaivePatternDetector;
import com.repeatminer.preprocessing.Vocabulary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceBenchmarkTest {

    @Test
    @DisplayName("Produces sane measured outcomes on a small real text")
    void producesSaneOutcome() {
        AnalysisResult result = new AnalysisEngine().analyzeText(
                "The cat sat on the mat.\nThe cat likes milk.\nThe cat sleeps.",
                "cats.txt", PatternDetector.Parameters.defaults());

        Vocabulary vocabulary = new Vocabulary();
        com.repeatminer.preprocessing.TextProcessor processor =
                new com.repeatminer.preprocessing.TextProcessor();
        int[] ids = vocabulary.internAll(processor.tokenize(
                "The cat sat on the mat.\nThe cat likes milk.\nThe cat sleeps."));

        PerformanceBenchmark.Outcome outcome = new PerformanceBenchmark().runOn(
                ids, vocabulary,
                new NaivePatternDetector.Parameters(2, 2, 50, 8),
                PatternDetector.Parameters.defaults(),
                2);

        assertEquals(ids.length, outcome.naive().tokenCount());
        assertEquals(ids.length, outcome.suffix().tokenCount());
        assertTrue(outcome.naive().totalTimeNanos() > 0, "naive must have a real timing");
        assertTrue(outcome.suffix().totalTimeNanos() > 0, "suffix must have a real timing");
        assertTrue(outcome.speedup() > 0, "speedup must be positive");
        assertEquals("Naive", outcome.naive().strategyName());
        assertEquals("Suffix Array + LCP", outcome.suffix().strategyName());
        assertTrue(outcome.speedupFormatted().matches("\\d+\\.\\d+x"));
    }

    @Test
    @DisplayName("Rejects invalid iteration counts")
    void validatesIterations() {
        int[] ids = {0, 1, 0};
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.internAll(java.util.List.of("a", "b", "a"));
        assertThrows(IllegalArgumentException.class,
                () -> new PerformanceBenchmark().runOn(ids, vocabulary,
                        NaivePatternDetector.Parameters.defaults(),
                        PatternDetector.Parameters.defaults(), 0));
    }
}

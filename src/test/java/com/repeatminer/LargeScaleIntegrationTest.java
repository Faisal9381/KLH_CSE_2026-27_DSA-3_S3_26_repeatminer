package com.repeatminer;

import com.repeatminer.benchmark.DatasetGenerator;
import com.repeatminer.benchmark.PerformanceBenchmark;
import com.repeatminer.detection.AnalysisEngine;
import com.repeatminer.detection.PatternDetector;
import com.repeatminer.model.AnalysisResult;
import com.repeatminer.naive.NaivePatternDetector;
import com.repeatminer.preprocessing.TextProcessor;
import com.repeatminer.preprocessing.Vocabulary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 15: large-scale integration. A generated 20k-token document (deterministic
 * seed) flows through the ENTIRE pipeline - file IO, tokenization, vocabulary,
 * suffix array, LCP, detection, benchmark - and the planted phrases must come out
 * with EXACTLY their planted counts. This is the correctness proof on realistic
 * input sizes (requirement 16).
 */
class LargeScaleIntegrationTest {

    private static final int TOKENS = 20_000;
    private static final int REPEATS = 30;
    private static final long SEED = 42;

    @Test
    @DisplayName("20k-token document: planted phrases detected with exact counts end-to-end")
    void plantedPhrasesSurviveTheWholePipeline(@TempDir Path tempDir) throws IOException {
        Path dataset = tempDir.resolve("integration.txt");
        DatasetGenerator.writeDataset(dataset, SEED, TOKENS,
                DatasetGenerator.DEFAULT_PHRASES, REPEATS);

        AnalysisResult result = new AnalysisEngine().analyze(dataset,
                new PatternDetector.Parameters(2, 2, 100_000));

        // The generator emits '.' sentence breaks, which the tokenizer correctly
        // drops; the token count must therefore equal the NON-dot token count.
        long expectedTokens = DatasetGenerator
                .generateTokens(SEED, TOKENS, DatasetGenerator.DEFAULT_PHRASES, REPEATS)
                .stream().filter(t -> !t.equals(".")).count();
        assertEquals(expectedTokens, result.totalTokens(),
                "pipeline must see exactly the generator's real words");

        Map<String, Integer> frequencies = new HashMap<>();
        result.patterns().forEach(p -> frequencies.put(p.phrase(), p.frequency()));

        // Phrases whose words never appear in the filler pool cannot occur by
        // accident, so their counts must be EXACTLY the planted number.
        assertEquals(REPEATS, frequencies.get("machine learning"));
        assertEquals(REPEATS, frequencies.get("artificial intelligence"));
        assertEquals(REPEATS, frequencies.get("neural network"));
        assertEquals(REPEATS, frequencies.get("suffix array construction"));
        assertEquals(REPEATS, frequencies.get("pattern detection"));
        assertEquals(REPEATS, frequencies.get("repeated word sequences"));

        // The planted 4-word phrase guarantees the longest repeat is >= 4 words.
        assertTrue(result.longestPatterns().get(0).wordCount() >= 4,
                "planted 'data structures and algorithms' implies a >=4-word repeat");
    }

    @Test
    @DisplayName("Both detectors agree on the 20k-token document")
    void detectorsAgreeAtScale() throws IOException {
        // Feed a REAL document: tokenize (drops '.' sentence breaks), like the app does.
        List<String> rawTokens = DatasetGenerator.generateTokens(SEED, TOKENS,
                DatasetGenerator.DEFAULT_PHRASES, REPEATS);
        List<String> tokens = new TextProcessor().tokenize(String.join(" ", rawTokens));
        Vocabulary vocabulary = new Vocabulary();
        int[] ids = vocabulary.internAll(tokens);

        Map<String, Integer> suffix = new HashMap<>();
        new PatternDetector(ids, vocabulary)
                .detect(new PatternDetector.Parameters(2, 2, 100_000))
                .patterns()
                .forEach(p -> suffix.put(p.phrase(), p.frequency()));

        // maxPhraseLength must be generous, but the interleaved generator keeps
        // repeats realistic (planted length + short filler coincidences), so 16
        // is far above anything the suffix detector can find in this dataset.
        Map<String, Integer> naive = new HashMap<>();
        new NaivePatternDetector(ids, vocabulary)
                .detect(new NaivePatternDetector.Parameters(2, 2, 100_000, 16))
                .patterns()
                .forEach(p -> naive.put(p.phrase(), p.frequency()));

        assertEquals(suffix, naive, "suffix-array and naive detectors must agree exactly at scale");
    }

    @Test
    @DisplayName("Benchmark completes at scale and produces real timings")
    void benchmarkAtScale() {
        List<String> tokens = DatasetGenerator.generateTokens(SEED, TOKENS,
                DatasetGenerator.DEFAULT_PHRASES, REPEATS);
        Vocabulary vocabulary = new Vocabulary();
        int[] ids = vocabulary.internAll(tokens);

        PerformanceBenchmark.Outcome outcome = new PerformanceBenchmark().runOn(
                ids, vocabulary,
                new NaivePatternDetector.Parameters(2, 2, 20, 8),
                new PatternDetector.Parameters(2, 2, 20),
                2);

        assertTrue(outcome.naive().totalTimeNanos() > 0);
        assertTrue(outcome.suffix().totalTimeNanos() > 0);
        System.out.printf("Measured at %,d tokens: naive %s, suffix %s, speedup %s%n",
                ids.length,
                outcome.naive().avgTimeMillisFormatted(),
                outcome.suffix().avgTimeMillisFormatted(),
                outcome.speedupFormatted());
    }

    @Test
    @DisplayName("Tokenizer handles the generated 50k-token dataset quickly")
    void tokenizerHandlesLargeFile(@TempDir Path tempDir) throws IOException {
        Path dataset = tempDir.resolve("large.txt");
        DatasetGenerator.writeDataset(dataset, SEED, 50_000,
                DatasetGenerator.DEFAULT_PHRASES, 50);
        assertTrue(Files.size(dataset) > 100_000, "50k tokens are well above 100 KB");

        TextProcessor.TokenizationResult result = new TextProcessor().tokenizeFile(dataset);
        // '.' breaks are dropped by the tokenizer, so compare against real words.
        // Same SEED constant as the file above (a stale literal here caused a
        // false failure that a standalone diff program tracked down).
        long expectedTokens = DatasetGenerator
                .generateTokens(SEED, 50_000, DatasetGenerator.DEFAULT_PHRASES, 50)
                .stream().filter(t -> !t.equals(".")).count();
        assertEquals(expectedTokens, result.tokenCount());
    }
}

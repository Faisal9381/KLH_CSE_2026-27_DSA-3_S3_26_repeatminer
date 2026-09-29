package com.repeatminer.detection;

import com.repeatminer.model.AnalysisResult;
import com.repeatminer.model.PatternResult;
import com.repeatminer.preprocessing.TextProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisEngineTest {

    private static final String CAT_TEXT =
            "The cat sat on the mat.\nThe cat likes milk.\nThe cat sleeps.";

    private final AnalysisEngine engine = new AnalysisEngine();

    @Test
    @DisplayName("End-to-end on the cat text: statistics, patterns and longest phrase")
    void analyzesCatTextEndToEnd() {
        AnalysisResult result = engine.analyzeText(
                CAT_TEXT, "cats.txt", PatternDetector.Parameters.defaults());

        assertEquals("cats.txt", result.documentName());
        assertEquals(13, result.totalTokens(), "6 + 4 + 3 tokens");
        assertEquals(8, result.vocabularySize(), "distinct words");
        assertEquals(59, result.totalCharacters(), "23+1 + 19+1 + 15 characters");
        assertEquals(2, result.minPhraseLength());
        assertEquals(2, result.minFrequency());

        assertTrue(result.patterns().stream().anyMatch(p ->
                        p.phrase().equals("the cat") && p.frequency() == 3),
                "'the cat' x3 must be among the detected patterns");

        assertEquals(1, result.longestPatterns().size());
        PatternResult longest = result.longestPatterns().get(0);
        assertEquals("the cat", longest.phrase());
        assertEquals(2, longest.wordCount());
        assertEquals(3, longest.frequency());
    }

    @Test
    @DisplayName("Analyzes a real file from disk")
    void analyzesFileFromDisk(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, CAT_TEXT);

        AnalysisResult result = engine.analyze(file, PatternDetector.Parameters.defaults());

        assertEquals("sample.txt", result.documentName());
        assertEquals(13, result.totalTokens());
        assertTrue(result.patterns().stream().anyMatch(p -> p.phrase().equals("the cat")));
    }

    @Test
    @DisplayName("Bundled datasets/small.txt works through the full pipeline")
    void analyzesBundledDataset() throws IOException {
        Path small = Path.of("datasets", "small.txt");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(small));

        AnalysisResult result = engine.analyze(small, PatternDetector.Parameters.defaults());

        assertEquals(13, result.totalTokens());
        assertTrue(result.longestPatterns().get(0).phrase().contains("cat"));
    }

    @Test
    @DisplayName("Missing and empty documents raise friendly errors")
    void friendlyErrors(@TempDir Path tempDir) throws IOException {
        assertThrows(TextProcessor.InvalidDocumentException.class,
                () -> engine.analyze(tempDir.resolve("missing.txt"),
                        PatternDetector.Parameters.defaults()));

        Path empty = tempDir.resolve("empty.txt");
        Files.writeString(empty, "   \n\t\n");
        assertThrows(TextProcessor.InvalidDocumentException.class,
                () -> engine.analyze(empty, PatternDetector.Parameters.defaults()));

        assertThrows(TextProcessor.InvalidDocumentException.class,
                () -> engine.analyzeText("!!! ...", "punct.txt",
                        PatternDetector.Parameters.defaults()));
    }

    @Test
    @DisplayName("Result lists are immutable snapshots")
    void resultsAreImmutable() {
        AnalysisResult result = engine.analyzeText(
                CAT_TEXT, "cats.txt", PatternDetector.Parameters.defaults());

        assertThrows(UnsupportedOperationException.class,
                () -> result.patterns().add(new PatternResult("fake", 1, 9)));
        assertThrows(UnsupportedOperationException.class,
                () -> result.longestPatterns().clear());
    }

    @Test
    @DisplayName("Different parameters re-run cheaply on the same engine")
    void parametersCanBeVaried() {
        AnalysisResult strict = engine.analyzeText(
                CAT_TEXT, "cats.txt", new PatternDetector.Parameters(2, 3, 50));
        AnalysisResult loose = engine.analyzeText(
                CAT_TEXT, "cats.txt", new PatternDetector.Parameters(1, 2, 50));

        assertTrue(strict.patterns().size() <= loose.patterns().size(),
                "looser thresholds cannot yield fewer patterns");
        assertEquals(1, strict.patterns().size(), "only 'the cat' x3 survives strict thresholds");
    }
}

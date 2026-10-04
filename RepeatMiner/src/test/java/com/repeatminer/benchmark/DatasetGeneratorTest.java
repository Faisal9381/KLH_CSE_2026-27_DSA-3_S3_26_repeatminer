package com.repeatminer.benchmark;

import com.repeatminer.preprocessing.TextProcessor;
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

class DatasetGeneratorTest {

    @Test
    @DisplayName("Same seed yields byte-identical token lists (reproducibility)")
    void deterministicForSameSeed() {
        List<String> first = DatasetGenerator.generateTokens(42, 500, 5);
        List<String> second = DatasetGenerator.generateTokens(42, 500, 5);
        assertEquals(first, second);
    }

    @Test
    @DisplayName("Planted phrases occur exactly the planted number of times")
    void plantedPhrasesAreCountable(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("gen.txt");
        int repeats = 7;
        DatasetGenerator.writeDataset(file, 42, 1000, DatasetGenerator.DEFAULT_PHRASES, repeats);

        List<String> tokens = new TextProcessor().tokenizeFile(file).tokens();

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < tokens.size() - 1; i++) {
            String bigram = tokens.get(i) + " " + tokens.get(i + 1);
            counts.merge(bigram, 1, Integer::sum);
        }
        assertEquals(repeats, counts.get("machine learning"),
                "planted phrase must appear exactly its planted count");
        assertEquals(repeats, counts.get("suffix array"),
                "first bigram of the planted trigram");
        assertEquals(repeats, counts.get("array construction"),
                "second bigram of the planted trigram");
        assertTrue(counts.getOrDefault("data structures", 0) >= repeats);
    }

    @Test
    @DisplayName("Generated file is UTF-8 text of plausible size")
    void fileIsPlausible(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("gen.txt");
        DatasetGenerator.writeDataset(file, 7, 2000, DatasetGenerator.DEFAULT_PHRASES, 10);
        assertTrue(Files.size(file) > 1000, "2000 tokens should exceed 1 KB");
        String content = Files.readString(file);
        assertTrue(content.contains("machine learning"));
    }

    @Test
    @DisplayName("Scale: 50k-token generation completes and size matches request")
    void generatesLargeScale() {
        int target = 50_000;
        List<String> tokens = DatasetGenerator.generateTokens(1, target, 40);
        assertTrue(tokens.size() >= target,
                "filler loop must fill up to the target (planted tokens may push past)");
    }
}

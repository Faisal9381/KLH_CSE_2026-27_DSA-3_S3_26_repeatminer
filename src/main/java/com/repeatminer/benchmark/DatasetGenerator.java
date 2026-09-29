package com.repeatminer.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Requirement 16: deterministic synthetic datasets with CONTROLLED repeated
 * phrases, so correctness can be verified (planted phrases must be found with
 * exactly their planted counts) and benchmarks can scale (medium ~5k tokens,
 * large ~50k tokens).
 *
 * <p>Structure: planted phrases (topic vocabulary) are interleaved with filler
 * sentences drawn from a small function/topic word pool. Because the generator
 * is seeded, the same seed always yields byte-identical files — reproducible
 * benchmarks and reproducible test expectations.
 */
public final class DatasetGenerator {

    /** Topic phrases planted {@code repeatsEach} times into the text. */
    public static final List<String> DEFAULT_PHRASES = List.of(
            "machine learning",
            "artificial intelligence",
            "neural network",
            "data structures and algorithms",
            "suffix array construction",
            "pattern detection",
            "large document analysis",
            "repeated word sequences");

    private static final String[] FILLER_WORDS = {
            "the", "of", "and", "a", "in", "model", "data", "system", "text",
            "result", "method", "study", "document", "process", "structure"};

    /**
     * Generates {@code targetTokens} tokens: each planted phrase repeated
     * {@code repeatsEach} times, padded with random filler sentences.
     *
     * @return the token list (lowercase words, no punctuation)
     */
    public static List<String> generateTokens(long seed, int targetTokens,
                                              List<String> phrases, int repeatsEach) {
        if (targetTokens < 1) {
            throw new IllegalArgumentException("targetTokens must be >= 1");
        }
        Random random = new Random(seed);
        List<String> tokens = new ArrayList<>(targetTokens);

        // Plant each phrase the requested number of times, INTERLEAVED with filler
        // sentences. Real documents do not concatenate topic phrases back-to-back;
        // separating them keeps the longest repeats realistic (bounded by phrase
        // length + short filler coincidences) instead of 100+-word run-on blocks.
        for (int r = 0; r < repeatsEach; r++) {
            for (String phrase : phrases) {
                for (String word : phrase.split(" ")) {
                    tokens.add(word);
                }
                if (random.nextBoolean()) {
                    tokens.add("."); // sentence break; tokenizer ignores it
                }
                if (random.nextInt(3) > 0) { // separator filler 2/3 of the time
                    int sentenceLength = 3 + random.nextInt(4);
                    for (int i = 0; i < sentenceLength; i++) {
                        tokens.add(FILLER_WORDS[random.nextInt(FILLER_WORDS.length)]);
                    }
                    if (random.nextBoolean()) {
                        tokens.add(".");
                    }
                }
            }
        }

        // Fill up with random 3-6 word filler sentences.
        while (tokens.size() < targetTokens) {
            int sentenceLength = 3 + random.nextInt(4);
            for (int i = 0; i < sentenceLength && tokens.size() < targetTokens; i++) {
                tokens.add(FILLER_WORDS[random.nextInt(FILLER_WORDS.length)]);
            }
            if (tokens.size() < targetTokens && random.nextBoolean()) {
                tokens.add(".");
            }
        }
        return tokens;
    }

    /** Convenience overload with the default phrase pool. */
    public static List<String> generateTokens(long seed, int targetTokens, int repeatsEach) {
        return generateTokens(seed, targetTokens, DEFAULT_PHRASES, repeatsEach);
    }

    /**
     * Writes the dataset as a text file (words separated by spaces, one
     * "sentence" per line for readability). Deterministic for a given seed.
     */
    public static void writeDataset(Path file, long seed, int targetTokens,
                                    List<String> phrases, int repeatsEach) throws IOException {
        List<String> tokens = generateTokens(seed, targetTokens, phrases, repeatsEach);
        StringBuilder builder = new StringBuilder();
        int wordsOnLine = 0;
        for (String token : tokens) {
            if (token.equals(".")) {
                builder.append('\n');
                wordsOnLine = 0;
            } else {
                if (wordsOnLine > 0) {
                    builder.append(' ');
                }
                builder.append(token);
                wordsOnLine++;
            }
        }
        Files.writeString(file, builder.toString(), StandardCharsets.UTF_8);
    }
}

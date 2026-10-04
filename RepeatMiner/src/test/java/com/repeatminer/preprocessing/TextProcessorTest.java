package com.repeatminer.preprocessing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TextProcessorTest {

    private final TextProcessor processor = new TextProcessor();

    @Test
    @DisplayName("Master-prompt example: punctuation and case are normalized")
    void tokenizesMachineLearningExample() {
        List<String> tokens = processor.tokenize("Machine learning, is changing the world!");
        assertIterableEquals(List.of("machine", "learning", "is", "changing", "the", "world"), tokens);
    }

    @Test
    @DisplayName("Cat example across multiple lines")
    void tokenizesCatExample() {
        List<String> tokens = processor.tokenize("""
                The cat sat on the mat.
                The cat likes milk.
                The cat sleeps.""");

        assertIterableEquals(List.of(
                "the", "cat", "sat", "on", "the", "mat",
                "the", "cat", "likes", "milk",
                "the", "cat", "sleeps"), tokens);
    }

    @Test
    @DisplayName("Empty input produces no tokens")
    void emptyInputYieldsNoTokens() {
        assertTrue(processor.tokenize("").isEmpty());
    }

    @Test
    @DisplayName("Whitespace-only input produces no tokens")
    void whitespaceOnlyYieldsNoTokens() {
        assertTrue(processor.tokenize("   \t  \n  ").isEmpty());
    }

    @Test
    @DisplayName("One-word input")
    void singleWord() {
        assertIterableEquals(List.of("hello"), processor.tokenize("hello"));
    }

    @Test
    @DisplayName("Punctuation is stripped, words preserved")
    void punctuationIsStripped() {
        assertIterableEquals(List.of("hello", "world", "goodbye"),
                processor.tokenize("hello, world!! ... (goodbye)"));
    }

    @Test
    @DisplayName("Upper and lower case are folded together")
    void caseIsNormalized() {
        // four separate words; casing must not change the token texts
        assertIterableEquals(List.of("the", "cat", "the", "cat"),
                processor.tokenize("The CAT tHe cAt"));
    }

    @Test
    @DisplayName("Apostrophe inside a word stays; leading/trailing apostrophes are dropped")
    void apostrophesFollowWordBoundaries() {
        assertIterableEquals(List.of("don't", "quoted", "cats"),
                processor.tokenize("'don't' ... 'quoted' cats'"));
    }

    @Test
    @DisplayName("Digits are kept inside words")
    void digitsAreKept() {
        assertIterableEquals(List.of("covid", "19", "90's"), processor.tokenize("Covid 19 (90's)!"));
    }

    @Test
    @DisplayName("Unicode letters are tokenized")
    void unicodeLettersAreTokenized() {
        assertIterableEquals(List.of("café", "naïve"), processor.tokenize("Café, naïve."));
    }

    @Test
    @DisplayName("Tabs and multiple spaces are treated as separators")
    void whitespaceVariantsAreSeparators() {
        assertIterableEquals(List.of("a", "b", "c"), processor.tokenize("a\t\tb    c"));
    }

    @Test
    @DisplayName("tokenizeFile reads a real file and counts characters")
    void tokenizesFileOnDisk(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("doc.txt");
        Files.writeString(file, "One two three.\nFour two.");

        TextProcessor.TokenizationResult result = processor.tokenizeFile(file);

        assertEquals(5, result.tokenCount(), "one two three four two");
        assertEquals("two", result.tokens().get(1));
        assertTrue(result.characterCount() >= 22);
    }

    @Test
    @DisplayName("Missing file raises a friendly InvalidDocumentException")
    void missingFileIsRejected(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("no-such-file.txt");
        assertThrows(TextProcessor.InvalidDocumentException.class, () -> processor.tokenizeFile(missing));
    }

    @Test
    @DisplayName("Empty file raises a friendly InvalidDocumentException")
    void emptyFileIsRejected(@TempDir Path tempDir) throws IOException {
        Path empty = tempDir.resolve("empty.txt");
        Files.writeString(empty, "   \n");
        assertThrows(TextProcessor.InvalidDocumentException.class, () -> processor.tokenizeFile(empty));
    }

    @Test
    @DisplayName("The bundled datasets/small.txt tokenizes as expected")
    void bundledSampleDatasetTokenizes() throws IOException {
        Path small = Path.of("datasets", "small.txt");
        assumeTrue(Files.exists(small), "datasets/small.txt not present in this checkout");

        List<String> tokens = processor.tokenizeFile(small).tokens();

        // "The cat sat on the mat. The cat likes milk. The cat sleeps." ->
        // 6 + 4 + 3 = 13 tokens
        assertEquals(13, tokens.size());
        assertEquals("the", tokens.get(0));
        assertEquals("cat", tokens.get(1));
        assertEquals("sleeps", tokens.get(12));
    }
}

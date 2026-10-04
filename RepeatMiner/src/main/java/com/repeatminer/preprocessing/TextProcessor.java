package com.repeatminer.preprocessing;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 2: turns raw TXT documents into word tokens (requirement 6).
 *
 * <p><b>How the tokenizer works (one pass, no regex):</b> scan the text character
 * by character. Letters and digits are appended to the current word; any other
 * character ends the word. One special rule: an apostrophe between two letters
 * stays inside the word ({@code don't} &rarr; {@code don't}), while a leading or
 * trailing apostrophe is dropped ({@code 'quoted'} &rarr; {@code quoted},
 * {@code cats'} &rarr; {@code cats}) so that quotations and possessives do not
 * create mismatched tokens that can never match a repeat.
 *
 * <p>Example: {@code "Machine learning, is changing the world!"} produces the
 * tokens {@code [machine, learning, is, changing, the, world]}.
 *
 * <p><b>Complexity:</b> O(n) over the number of characters — each character is
 * examined exactly once. The file is read line by line with a
 * {@link BufferedReader}, so the whole document is never duplicated in memory
 * (requirement 22). No automatic stop-word removal here; that may only appear
 * later as an explicitly optional setting (requirement 6).
 */
public final class TextProcessor {

    /** Statistics describing a tokenized document (requirement 17). */
    public record TokenizationResult(List<String> tokens, long characterCount) {

        public TokenizationResult {
            tokens = tokens == null ? List.of() : List.copyOf(tokens);
        }

        public int tokenCount() {
            return tokens.size();
        }
    }

    /**
     * Reads and tokenizes a TXT file.
     *
     * @param path file to read
     * @return tokens (lowercased) plus the raw character count
     * @throws IOException            if the file cannot be read
     * @throws InvalidDocumentException if the file is empty or contains no words
     */
    public TokenizationResult tokenizeFile(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new InvalidDocumentException("File not found: " + path.getFileName());
        }
        if (Files.isDirectory(path)) {
            throw new InvalidDocumentException("Not a file: " + path.getFileName());
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<String> tokens = new ArrayList<>();
            long characters = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                characters += line.length() + 1; // +1 approximates the line break
                tokenizeLine(line, tokens);
            }
            if (tokens.isEmpty()) {
                throw new InvalidDocumentException(
                        "The document contains no readable words (empty file?).");
            }
            return new TokenizationResult(tokens, characters);
        }
    }

    /** Tokenizes an in-memory string; same rules as {@link #tokenizeFile(Path)}. */
    public List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        tokenizeLine(text, tokens);
        return tokens;
    }

    private void tokenizeLine(String line, List<String> out) {
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = Character.toLowerCase(line.charAt(i));
            if (isWordCharacter(c, word, line, i)) {
                word.append(c);
            } else if (!word.isEmpty()) {
                out.add(word.toString());
                word.setLength(0);
            }
        }
        if (!word.isEmpty()) {
            out.add(word.toString());
        }
    }

    /**
     * A character belongs to the current word if it is a letter or digit, or if
     * it is an apostrophe strictly between two letters of the same word.
     */
    private boolean isWordCharacter(char c, StringBuilder word, String line, int index) {
        if (Character.isLetterOrDigit(c)) {
            return true;
        }
        if (c == '\'' && !word.isEmpty()) {
            int next = index + 1;
            return next < line.length() && Character.isLetter(line.charAt(next));
        }
        return false;
    }

    /** User-friendly document problems; never a raw stack trace for these (req. 20). */
    public static class InvalidDocumentException extends RuntimeException {
        public InvalidDocumentException(String message) {
            super(message);
        }
    }
}

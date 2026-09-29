package com.repeatminer.detection;

import com.repeatminer.model.AnalysisResult;
import com.repeatminer.model.PatternResult;
import com.repeatminer.preprocessing.TextProcessor;
import com.repeatminer.preprocessing.Vocabulary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Phase 8: the offline analysis pipeline as one facade (requirement 25).
 *
 * <pre>
 *   TXT file
 *     -> TextProcessor   (tokenize, O(n))
 *     -> Vocabulary      (token -> integer IDs, O(n))
 *     -> SuffixArray     (prefix doubling, O(n log^2 n))   [inside PatternDetector]
 *     -> LCPArray        (Kasai, O(n))                     [inside PatternDetector]
 *     -> PatternDetector (LCP-interval sweep, O(n + output))
 *     -> AnalysisResult
 * </pre>
 *
 * The GUI and the console mode both call exactly this class; neither knows
 * anything about suffix arrays. The whole pipeline runs without JavaFX, which is
 * what makes it unit-testable (requirement 25).
 */
public final class AnalysisEngine {

    private final TextProcessor textProcessor = new TextProcessor();

    /**
     * Analyses a TXT file with the given parameters.
     *
     * @throws IOException                              if the file cannot be read
     * @throws TextProcessor.InvalidDocumentException   if the file is missing or has no words
     */
    public AnalysisResult analyze(Path file, PatternDetector.Parameters params) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file path must not be null");
        }
        if (!Files.exists(file)) {
            throw new TextProcessor.InvalidDocumentException("File not found: " + file.getFileName());
        }
        TextProcessor.TokenizationResult tokenization = textProcessor.tokenizeFile(file);
        return assemble(file.getFileName().toString(), tokenization, params);
    }

    /** Analyses in-memory text; used by tests and demos without touching disk. */
    public AnalysisResult analyzeText(String rawText, String documentName,
                                      PatternDetector.Parameters params) {
        List<String> tokens = textProcessor.tokenize(rawText == null ? "" : rawText);
        if (tokens.isEmpty()) {
            throw new TextProcessor.InvalidDocumentException(
                    "The document contains no readable words.");
        }
        long characters = rawText == null ? 0 : rawText.length();
        return assemble(documentName, new TextProcessor.TokenizationResult(tokens, characters), params);
    }

    /**
     * Analyses a tokenization performed earlier (e.g. when the GUI showed file
     * statistics on selection and reuses it so the file is parsed only once).
     */
    public AnalysisResult analyzeTokens(TextProcessor.TokenizationResult tokenization,
                                        String documentName,
                                        PatternDetector.Parameters params) {
        if (tokenization == null || tokenization.tokens().isEmpty()) {
            throw new TextProcessor.InvalidDocumentException(
                    "The document contains no readable words.");
        }
        return assemble(documentName, tokenization, params);
    }

    private AnalysisResult assemble(String documentName,
                                    TextProcessor.TokenizationResult tokenization,
                                    PatternDetector.Parameters params) {
        Vocabulary vocabulary = new Vocabulary();
        int[] tokenIds = vocabulary.internAll(tokenization.tokens());

        PatternDetector detector = new PatternDetector(tokenIds, vocabulary);
        PatternDetector.DetectionResult detection = detector.detect(params);

        List<PatternResult> patterns = detection.patterns();
        List<PatternResult> longest = detection.longestPatterns();
        return new AnalysisResult(
                documentName,
                tokenization.characterCount(),
                tokenization.tokens().size(),
                vocabulary.size(),
                patterns,
                longest,
                params.minPhraseLength(),
                params.minFrequency());
    }
}

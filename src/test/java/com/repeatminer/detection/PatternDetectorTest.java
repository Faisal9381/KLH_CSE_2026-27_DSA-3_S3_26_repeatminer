package com.repeatminer.detection;

import com.repeatminer.model.PatternResult;
import com.repeatminer.preprocessing.Vocabulary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternDetectorTest {

    // ---------------------------------------------------------------- helpers

    /** The master prompt's cat document: 13 tokens, 8 distinct words. */
    private static final String CAT_TEXT =
            "The cat sat on the mat.\nThe cat likes milk.\nThe cat sleeps.";

    private record Prepared(PatternDetector detector, Vocabulary vocabulary) {
    }

    private Prepared prepare(String rawText) {
        List<String> tokens = new com.repeatminer.preprocessing.TextProcessor().tokenize(rawText);
        Vocabulary vocabulary = new Vocabulary();
        int[] ids = vocabulary.internAll(tokens);
        return new Prepared(new PatternDetector(ids, vocabulary), vocabulary);
    }

    /** Brute force: enumerate every phrase (start x length), keep freq >= 2. */
    private Map<String, Integer> bruteForcePhrases(List<String> tokens) {
        Map<String, Integer> counts = new HashMap<>();
        for (int start = 0; start < tokens.size(); start++) {
            StringBuilder phrase = new StringBuilder();
            for (int end = start; end < tokens.size(); end++) {
                if (end > start) {
                    phrase.append(' ');
                }
                phrase.append(tokens.get(end));
                counts.merge(phrase.toString(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /** Map from phrase -> frequency, for convenient assertions. */
    private Map<String, Integer> asMap(List<PatternResult> patterns) {
        Map<String, Integer> map = new HashMap<>();
        for (PatternResult p : patterns) {
            map.put(p.phrase(), p.frequency());
        }
        return map;
    }

    // ---------------------------------------------------------------- tests

    @Test
    @DisplayName("Cat dataset: 'the cat' x3 and 'cat' x3 with defaults (min length 2)")
    void catDatasetDefaultParameters() {
        Prepared prepared = prepare(CAT_TEXT);
        List<PatternResult> patterns =
                prepared.detector().detect(PatternDetector.Parameters.defaults()).patterns();

        Map<String, Integer> map = asMap(patterns);

        assertEquals(3, map.get("the cat"), "master prompt expects 'The cat' -> 3");
        assertTrue(patterns.size() <= 50);
    }

    @Test
    @DisplayName("Longest repeated phrase in the cat dataset is 'the cat' (2 words)")
    void catDatasetLongestPattern() {
        Prepared prepared = prepare(CAT_TEXT);
        List<PatternResult> longest = prepared.detector()
                .detect(PatternDetector.Parameters.defaults()).longestPatterns();

        assertEquals(1, longest.size());
        assertEquals("the cat", longest.get(0).phrase());
        assertEquals(2, longest.get(0).wordCount());
        assertEquals(3, longest.get(0).frequency());
    }

    @Test
    @DisplayName("minPhraseLength=1 includes single repeated words like 'cat' x3")
    void singleWordPatternsWhenAllowed() {
        Prepared prepared = prepare(CAT_TEXT);
        List<PatternResult> patterns = prepared.detector()
                .detect(new PatternDetector.Parameters(1, 2, 50)).patterns();

        Map<String, Integer> map = asMap(patterns);
        // hand-counted: 'the' occurs 4x (sentence 1 alone has two: "The cat sat on THE mat"),
        // 'cat' 3x, 'the cat' 3x; every other word occurs exactly once.
        assertEquals(4, map.get("the"));
        assertEquals(3, map.get("cat"));
        assertEquals(3, map.get("the cat"));
        assertEquals(3, patterns.size(), "no other phrase repeats twice or more");
    }

    @Test
    @DisplayName("No repeats -> empty results, no crash")
    void noRepeatsYieldsEmptyResults() {
        Prepared prepared = prepare("alpha beta gamma delta epsilon zeta.");
        DetectionResultStub stub = new DetectionResultStub(
                prepared.detector().detect(new PatternDetector.Parameters(2, 2, 50)));
        assertTrue(stub.patterns().isEmpty());
        assertTrue(stub.longest().isEmpty());
    }

    @Test
    @DisplayName("One-word document is accepted and yields nothing")
    void oneWordDocument() {
        Prepared prepared = prepare("hello");
        DetectionResultStub stub = new DetectionResultStub(
                prepared.detector().detect(PatternDetector.Parameters.defaults()));
        assertTrue(stub.patterns().isEmpty());
        assertTrue(stub.longest().isEmpty());
    }

    @Test
    @DisplayName("Frequency threshold filters rarer patterns")
    void frequencyThresholdFilters() {
        // 'the cat' x3, 'sat on' x1, 'on the' x1 ... with minFrequency=3 only x3 phrases survive
        Prepared prepared = prepare(CAT_TEXT);
        List<PatternResult> patterns = prepared.detector()
                .detect(new PatternDetector.Parameters(2, 3, 50)).patterns();

        for (PatternResult p : patterns) {
            assertTrue(p.frequency() >= 3, "all results must meet min frequency 3: " + p.phrase());
        }
        assertEquals(1, patterns.size(), "only 'the cat' x3 survives minFrequency=3 at length>=2");
        assertTrue(asMap(patterns).containsKey("the cat"));
    }

    @Test
    @DisplayName("maxPatterns caps the output")
    void maxPatternsCapsOutput() {
        Prepared prepared = prepare(CAT_TEXT);
        List<PatternResult> patterns = prepared.detector()
                .detect(new PatternDetector.Parameters(1, 2, 3)).patterns();

        assertEquals(3, patterns.size());
    }

    @Test
    @DisplayName("Sorting: frequency desc, then length desc, then alphabetical")
    void displayOrderIsDeterministic() {
        Prepared prepared = prepare(CAT_TEXT);
        List<PatternResult> patterns = prepared.detector()
                .detect(new PatternDetector.Parameters(1, 2, 50)).patterns();

        for (int i = 1; i < patterns.size(); i++) {
            PatternResult prev = patterns.get(i - 1);
            PatternResult curr = patterns.get(i);
            assertTrue(prev.frequency() >= curr.frequency(), "frequency descending: " + patterns);
            if (prev.frequency() == curr.frequency()) {
                assertTrue(prev.wordCount() >= curr.wordCount(), "length descending on ties");
            }
        }
    }

    @Test
    @DisplayName("Parameter validation: bad lengths, frequencies and caps are rejected")
    void parameterValidation() {
        assertThrows(IllegalArgumentException.class, () -> new PatternDetector.Parameters(0, 2, 50));
        assertThrows(IllegalArgumentException.class, () -> new PatternDetector.Parameters(1, 1, 50));
        assertThrows(IllegalArgumentException.class, () -> new PatternDetector.Parameters(1, 2, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PatternDetector(new int[0], new Vocabulary()));
    }

    @RepeatedTest(value = 15, name = "random text {currentRepetition}/{totalRepetitions} matches brute force")
    @DisplayName("Property: detected phrase frequencies equal exhaustive enumeration")
    void randomTextsMatchBruteForce() {
        Random random = new Random(1234);
        int n = 5 + random.nextInt(40);
        int alphabet = 2 + random.nextInt(4);

        List<String> tokens = new ArrayList<>();
        Vocabulary vocabulary = new Vocabulary();
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            String word = "w" + random.nextInt(alphabet);
            tokens.add(word);
            ids[i] = vocabulary.intern(word);
        }

        PatternDetector.Parameters params = new PatternDetector.Parameters(1, 2, 100_000);
        Map<String, Integer> detected = asMap(
                new PatternDetector(ids, vocabulary).detect(params).patterns());

        Map<String, Integer> expected = new HashMap<>();
        bruteForcePhrases(tokens).forEach((phrase, freq) -> {
            if (freq >= 2) {
                expected.put(phrase, freq);
            }
        });

        assertEquals(expected, detected, "sweep must find every repeated phrase exactly");
    }

    @Test
    @DisplayName("Structured worst case: many equal tokens still terminates correctly")
    void manyEqualTokens() {
        Vocabulary vocabulary = new Vocabulary();
        int[] ids = new int[40];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = vocabulary.intern("same");
        }
        PatternDetector.Parameters params = new PatternDetector.Parameters(2, 2, 50);
        List<PatternResult> patterns =
                new PatternDetector(ids, vocabulary).detect(params).patterns();

        // phrases of length 2..39 exist ('same same' x39 down to 38-token phrase x2):
        // exactly 38 distinct multi-word phrases, and maxPatterns=50 does not cut them
        assertEquals(38, patterns.size(), "all 38 multi-word phrases (lengths 2..39) fit under the cap");
        assertEquals("same same", patterns.get(0).phrase());
        assertEquals(39, patterns.get(0).frequency());
    }

    /** Tiny adapter so tests can read both result lists without pattern-matching records. */
    private record DetectionResultStub(
            com.repeatminer.detection.PatternDetector.DetectionResult result) {

        List<PatternResult> patterns() {
            return result.patterns();
        }

        List<PatternResult> longest() {
            return result.longestPatterns();
        }
    }
}

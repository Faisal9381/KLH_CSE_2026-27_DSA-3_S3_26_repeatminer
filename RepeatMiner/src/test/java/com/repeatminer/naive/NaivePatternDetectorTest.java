package com.repeatminer.naive;

import com.repeatminer.detection.PatternDetector;
import com.repeatminer.model.PatternResult;
import com.repeatminer.preprocessing.Vocabulary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NaivePatternDetectorTest {

    private static final String CAT_TEXT =
            "The cat sat on the mat.\nThe cat likes milk.\nThe cat sleeps.";

    private record Prepared(int[] ids, Vocabulary vocabulary) {
    }

    private Prepared prepare(String text) {
        List<String> tokens = new com.repeatminer.preprocessing.TextProcessor().tokenize(text);
        Vocabulary vocabulary = new Vocabulary();
        return new Prepared(vocabulary.internAll(tokens), vocabulary);
    }

    private Map<String, Integer> asMap(List<PatternResult> patterns) {
        Map<String, Integer> map = new HashMap<>();
        for (PatternResult p : patterns) {
            map.put(p.phrase(), p.frequency());
        }
        return map;
    }

    @Test
    @DisplayName("Naive result equals suffix-array result on the cat text")
    void matchesSuffixDetectorOnCatText() {
        Prepared prepared = prepare(CAT_TEXT);

        PatternDetector.DetectionResult naive =
                new NaivePatternDetector(prepared.ids(), prepared.vocabulary())
                        .detect(new NaivePatternDetector.Parameters(1, 2, 50, 8));
        PatternDetector.DetectionResult suffix =
                new PatternDetector(prepared.ids(), prepared.vocabulary())
                        .detect(new PatternDetector.Parameters(1, 2, 50));

        assertEquals(asMap(suffix.patterns()), asMap(naive.patterns()),
                "both detectors must agree on phrase frequencies");
        assertEquals(asMap(suffix.longestPatterns()), asMap(naive.longestPatterns()),
                "both detectors must agree on the longest phrase");
    }

    @Test
    @DisplayName("Caps and longest behave like the suffix detector")
    void longestAndCaps() {
        Prepared prepared = prepare(CAT_TEXT);
        NaivePatternDetector naive =
                new NaivePatternDetector(prepared.ids(), prepared.vocabulary());

        assertEquals(2, naive.detect(new NaivePatternDetector.Parameters(1, 2, 2, 8))
                .patterns().size());
        assertEquals("the cat", naive.detect(new NaivePatternDetector.Parameters(2, 2, 50, 8))
                .longestPatterns().get(0).phrase());
    }

    @Test
    @DisplayName("Validation rejects bad parameters and inputs")
    void validation() {
        Prepared prepared = prepare(CAT_TEXT);
        assertThrows(IllegalArgumentException.class,
                () -> new NaivePatternDetector.Parameters(0, 2, 50, 8));
        assertThrows(IllegalArgumentException.class,
                () -> new NaivePatternDetector.Parameters(1, 1, 50, 8));
        assertThrows(IllegalArgumentException.class,
                () -> new NaivePatternDetector.Parameters(3, 2, 50, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new NaivePatternDetector(new int[0], prepared.vocabulary()));
    }
}

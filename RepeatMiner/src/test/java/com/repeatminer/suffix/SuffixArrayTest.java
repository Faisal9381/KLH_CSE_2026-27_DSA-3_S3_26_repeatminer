package com.repeatminer.suffix;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuffixArrayTest {

    // ---------------------------------------------------------------- helpers

    /** Brute-force reference: order suffix positions by direct comparison. */
    private int[] bruteForceSuffixArray(int[] text) {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < text.length; i++) {
            positions.add(i);
        }
        positions.sort((a, b) -> {
            while (a < text.length && b < text.length) {
                if (text[a] != text[b]) {
                    return Integer.compare(text[a], text[b]);
                }
                a++;
                b++;
            }
            return Integer.compare(text.length - a, text.length - b);
        });
        return positions.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Property check: built SA must equal the brute-force order. */
    private void assertMatchesBruteForce(int[] text) {
        SuffixArray sa = new SuffixArray(text);
        assertArrayEquals(bruteForceSuffixArray(text), sa.getSuffixArray(),
                () -> "mismatch for text " + java.util.Arrays.toString(text));
    }

    // ---------------------------------------------------------------- tests

    @Test
    @DisplayName("Cat example: suffixes starting 'the cat' become adjacent")
    void catExampleBuildsExpectedArray() {
        // tokens: the=0 cat=1 sat=2 on=3 the=0 mat=4 the=0 cat=1 sleeps=5
        // sorted groups: {0,6,4} start with 'the', {1,7} with 'cat', then sat/on/mat/sleeps
        SuffixArray sa = new SuffixArray(new int[] {0, 1, 2, 3, 0, 4, 0, 1, 5});

        assertArrayEquals(new int[] {0, 6, 4, 1, 7, 2, 3, 5, 8}, sa.getSuffixArray());
    }

    @Test
    @DisplayName("Tiny five-token example from the documentation")
    void fiveTokenDocumentationExample() {
        SuffixArray sa = new SuffixArray(new int[] {0, 1, 0, 1, 2});
        assertArrayEquals(new int[] {0, 2, 1, 3, 4}, sa.getSuffixArray());
    }

    @Test
    @DisplayName("Single token input")
    void singleToken() {
        SuffixArray sa = new SuffixArray(new int[] {7});
        assertArrayEquals(new int[] {0}, sa.getSuffixArray());
        assertEquals(0, sa.rankOf(0));
    }

    @Test
    @DisplayName("All tokens equal: suffix order is by remaining length")
    void allEqualTokens() {
        SuffixArray sa = new SuffixArray(new int[] {5, 5, 5, 5});
        // shorter suffixes are lexicographically smaller: [3, 2, 1, 0]
        assertArrayEquals(new int[] {3, 2, 1, 0}, sa.getSuffixArray());
    }

    @Test
    @DisplayName("compareSuffixes distinguishes, orders and equates suffixes")
    void compareSuffixesBehaviour() {
        SuffixArray sa = new SuffixArray(new int[] {0, 1, 0, 1, 2});

        assertTrue(sa.compareSuffixes(0, 1) < 0, "'the cat...' < 'cat ...'");
        assertTrue(sa.compareSuffixes(1, 0) > 0, "comparison is antisymmetric");
        assertEquals(0, sa.compareSuffixes(0, 0), "a suffix equals itself");
        assertTrue(sa.compareSuffixes(0, 2) < 0,
                "shared 'the cat' prefix, then 'the'(0) < 'sleeps'(2) decides");
    }

    @Test
    @DisplayName("search finds both occurrences of 'the cat' pattern")
    void searchFindsRepeatedPhrase() {
        // the cat sat on the mat the cat sleeps  (the=0 cat=1 sat=2 on=3 mat=4 sleeps=5)
        SuffixArray sa = new SuffixArray(new int[] {0, 1, 2, 3, 0, 4, 0, 1, 5});

        int[] range = sa.search(new int[] {0, 1});
        assertEquals(2, range[1] - range[0], "pattern 'the cat' occurs twice");
    }

    @Test
    @DisplayName("search returns [-1, -1] for absent patterns")
    void searchAbsentPattern() {
        SuffixArray sa = new SuffixArray(new int[] {0, 1, 0, 1, 2});
        assertArrayEquals(new int[] {-1, -1}, sa.search(new int[] {2, 0}));
        assertArrayEquals(new int[] {-1, -1}, sa.search(new int[] {9}));
    }

    @Test
    @DisplayName("search handles boundary patterns: full text, longer than text, empty")
    void searchBoundaryPatterns() {
        SuffixArray sa = new SuffixArray(new int[] {0, 1, 0, 1, 2});

        assertEquals(1, sa.search(new int[] {0, 1, 0, 1, 2})[1] - sa.search(new int[] {0, 1, 0, 1, 2})[0],
                "the whole text occurs once");
        assertArrayEquals(new int[] {-1, -1}, sa.search(new int[] {0, 1, 0, 1, 2, 3}),
                "pattern longer than text cannot occur");
        assertArrayEquals(new int[] {-1, -1}, sa.search(new int[] {}));
    }

    @Test
    @DisplayName("Empty and invalid inputs are rejected with clear messages")
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> new SuffixArray(new int[0]));
        assertThrows(IllegalArgumentException.class, () -> new SuffixArray(null));
        assertThrows(IllegalArgumentException.class, () -> new SuffixArray(new int[] {3, -1, 2}));
    }

    @Test
    @DisplayName("Constructor copies the input array")
    void inputArrayIsCopied() {
        int[] text = {0, 1, 0};
        SuffixArray sa = new SuffixArray(text);
        text[0] = 99; // mutating the caller's array must not corrupt the built structure

        // brute force over the ORIGINAL content: suffixes [0,1,0],[1,0],[0] -> [2,0,1]
        assertArrayEquals(new int[] {2, 0, 1}, sa.getSuffixArray());
    }

    @RepeatedTest(value = 20, name = "random text {currentRepetition}/{totalRepetitions} matches brute force")
    @DisplayName("Property: built SA equals brute-force order on random texts")
    void randomTextsMatchBruteForce() {
        Random random = new Random(42);
        int n = 1 + random.nextInt(60);
        int alphabet = 1 + random.nextInt(6); // small alphabet => many ties

        int[] text = new int[n];
        for (int i = 0; i < n; i++) {
            text[i] = random.nextInt(alphabet);
        }
        assertMatchesBruteForce(text);
    }

    @Test
    @DisplayName("Structured worst case: block repetition stresses tie-breaking")
    void structuredRepetitionMatchesBruteForce() {
        // a b a b a b a b ... plus a tail: heavy equal-prefix ties
        int[] text = new int[64];
        for (int i = 0; i < 62; i++) {
            text[i] = i % 2;
        }
        text[62] = 5;
        text[63] = 6;
        assertMatchesBruteForce(text);
    }
}

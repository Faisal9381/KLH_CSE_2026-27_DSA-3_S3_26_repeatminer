package com.repeatminer.suffix;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LCPArrayTest {

    // ---------------------------------------------------------------- helpers

    /** Reference: directly compare each adjacent suffix pair in SA order. */
    private int[] bruteForceLcp(int[] text, int[] sa) {
        int[] lcp = new int[sa.length];
        for (int i = 1; i < sa.length; i++) {
            int a = sa[i - 1], b = sa[i], h = 0;
            while (a + h < text.length && b + h < text.length && text[a + h] == text[b + h]) {
                h++;
            }
            lcp[i] = h;
        }
        return lcp;
    }

    /** Builds SA+LCP with the production classes and cross-checks against brute force. */
    private void assertLcpMatchesBruteForce(int[] text) {
        SuffixArray sa = new SuffixArray(text);
        LCPArray lcp = new LCPArray(text, sa.getSuffixArray());
        assertArrayEquals(bruteForceLcp(text, sa.getSuffixArray()), lcp.getLcpArray(),
                () -> "LCP mismatch for text " + java.util.Arrays.toString(text));
    }

    // ---------------------------------------------------------------- tests

    @Test
    @DisplayName("Documentation example [0,1,0,1,2] -> LCP [0,2,0,1,0]")
    void documentationExample() {
        SuffixArray sa = new SuffixArray(new int[] {0, 1, 0, 1, 2});
        LCPArray lcp = new LCPArray(new int[] {0, 1, 0, 1, 2}, sa.getSuffixArray());
        assertArrayEquals(new int[] {0, 2, 0, 1, 0}, lcp.getLcpArray());
    }

    @Test
    @DisplayName("Cat sentence: 'the cat' shows up as lcp = 2, 'cat' as lcp = 1")
    void catSentence() {
        // the cat sat on the mat the cat sleeps (the=0 cat=1 sat=2 on=3 mat=4 sleeps=5)
        int[] text = {0, 1, 2, 3, 0, 4, 0, 1, 5};
        SuffixArray sa = new SuffixArray(text);
        LCPArray lcp = new LCPArray(text, sa.getSuffixArray());

        assertArrayEquals(new int[] {0, 2, 1, 0, 1, 0, 0, 0, 0}, lcp.getLcpArray());
    }

    @Test
    @DisplayName("No repeated tokens -> all LCP values are zero")
    void noRepeatsAllZero() {
        int[] text = {0, 1, 2, 3};
        SuffixArray sa = new SuffixArray(text);
        LCPArray lcp = new LCPArray(text, sa.getSuffixArray());
        assertArrayEquals(new int[] {0, 0, 0, 0}, lcp.getLcpArray());
    }

    @Test
    @DisplayName("Single token -> LCP [0]")
    void singleToken() {
        LCPArray lcp = new LCPArray(new int[] {9}, new int[] {0});
        assertArrayEquals(new int[] {0}, lcp.getLcpArray());
    }

    @Test
    @DisplayName("All tokens equal -> LCP grows by one along sorted suffixes")
    void allEqualTokens() {
        // text [5,5,5,5], SA [3,2,1,0]: shared prefixes are 1, 2, 3 tokens
        int[] text = {5, 5, 5, 5};
        SuffixArray sa = new SuffixArray(text);
        LCPArray lcp = new LCPArray(text, sa.getSuffixArray());
        assertArrayEquals(new int[] {0, 1, 2, 3}, lcp.getLcpArray());
    }

    @Test
    @DisplayName("lcpAt returns values and rejects out-of-range indexes")
    void lcpAtBounds() {
        LCPArray lcp = new LCPArray(new int[] {0, 1, 0, 1, 2}, new int[] {0, 2, 1, 3, 4});
        assertEquals(0, lcp.lcpAt(0));
        assertEquals(2, lcp.lcpAt(1));
        assertEquals(1, lcp.lcpAt(3));
        assertThrows(IndexOutOfBoundsException.class, () -> lcp.lcpAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> lcp.lcpAt(5));
    }

    @Test
    @DisplayName("Invalid inputs are rejected with clear messages")
    void rejectsInvalidInput() {
        int[] text = {0, 1, 0};
        int[] sa = {2, 0, 1};
        assertThrows(IllegalArgumentException.class, () -> new LCPArray(null, sa));
        assertThrows(IllegalArgumentException.class, () -> new LCPArray(new int[0], sa));
        assertThrows(IllegalArgumentException.class, () -> new LCPArray(text, null));
        assertThrows(IllegalArgumentException.class, () -> new LCPArray(text, new int[] {0, 1}));
        assertThrows(IllegalArgumentException.class, () -> new LCPArray(text, new int[] {0, 1, 2, 3}));
    }

    @RepeatedTest(value = 20, name = "random text {currentRepetition}/{totalRepetitions} LCP matches brute force")
    @DisplayName("Property: Kasai LCP equals direct adjacent-pair comparison")
    void randomTextsMatchBruteForce() {
        Random random = new Random(7);
        int n = 1 + random.nextInt(60);
        int alphabet = 1 + random.nextInt(6);

        int[] text = new int[n];
        for (int i = 0; i < n; i++) {
            text[i] = random.nextInt(alphabet);
        }
        assertLcpMatchesBruteForce(text);
    }

    @Test
    @DisplayName("Structured worst case: long equal runs stress the h-carry logic")
    void structuredRepetitionMatchesBruteForce() {
        int[] text = new int[64];
        for (int i = 0; i < 62; i++) {
            text[i] = i % 2;      // a b a b a b ...
        }
        text[62] = 5;
        text[63] = 6;
        assertLcpMatchesBruteForce(text);
    }

    @Test
    @DisplayName("Block repetition: 'x y x y x y' style repeats give strong LCP values")
    void blockRepetitionHasStrongLcpValues() {
        // xyxy... pattern: text [0,1,0,1,0,1] -> SA [4,2,0,5,3,1]
        int[] text = {0, 1, 0, 1, 0, 1};
        SuffixArray sa = new SuffixArray(text);
        LCPArray lcp = new LCPArray(text, sa.getSuffixArray());

        // Direct check against brute force rather than one hand-computed array:
        assertArrayEquals(bruteForceLcp(text, sa.getSuffixArray()), lcp.getLcpArray());
        // And the structural claim: some adjacency shares at least 4 tokens ("x y x y").
        int max = 0;
        for (int v : lcp.getLcpArray()) {
            max = Math.max(max, v);
        }
        assertEquals(4, max, "longest repeated phrase in xyxyxy is 'x y x y' = 4 tokens");
    }
}

package com.repeatminer.preprocessing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VocabularyTest {

    @Test
    @DisplayName("First-seen words get dense sequential IDs 0, 1, 2, ...")
    void assignsDenseSequentialIds() {
        Vocabulary vocabulary = new Vocabulary();
        int[] ids = vocabulary.internAll(List.of("machine", "learning", "is", "changing", "the", "world"));
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5}, ids);
        assertEquals(6, vocabulary.size());
    }

    @Test
    @DisplayName("A repeated word reuses its existing ID")
    void repeatedWordsReuseIds() {
        Vocabulary vocabulary = new Vocabulary();
        int first = vocabulary.intern("the");
        int second = vocabulary.intern("cat");
        int third = vocabulary.intern("the");

        assertEquals(0, first);
        assertEquals(1, second);
        assertEquals(0, third, "'the' must map to the same ID on every occurrence");
        assertEquals(2, vocabulary.size(), "two distinct words, three internings");
    }

    @Test
    @DisplayName("wordFor is the exact inverse of intern")
    void wordForRoundTrips() {
        Vocabulary vocabulary = new Vocabulary();
        int[] ids = vocabulary.internAll(List.of("the", "cat", "sat"));

        assertEquals("the", vocabulary.wordFor(ids[0]));
        assertEquals("cat", vocabulary.wordFor(ids[1]));
        assertEquals("sat", vocabulary.wordFor(ids[2]));
    }

    @Test
    @DisplayName("Out-of-range IDs return null instead of throwing")
    void outOfRangeIdsReturnNull() {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.intern("word");

        assertNull(vocabulary.wordFor(-1));
        assertNull(vocabulary.wordFor(1));
        assertNull(vocabulary.wordFor(999));
    }

    @Test
    @DisplayName("Empty token list maps to an empty array")
    void emptyListMapsToEmptyArray() {
        Vocabulary vocabulary = new Vocabulary();
        assertArrayEquals(new int[0], vocabulary.internAll(List.of()));
        assertEquals(0, vocabulary.size());
    }

    @Test
    @DisplayName("Pipeline: cat example produces 8 distinct IDs over 15 tokens")
    void pipelineOnCatExample() {
        TextProcessor processor = new TextProcessor();
        List<String> tokens = processor.tokenize("""
                The cat sat on the mat.
                The cat likes milk.
                The cat sleeps.""");

        Vocabulary vocabulary = new Vocabulary();
        int[] ids = vocabulary.internAll(tokens);

        assertEquals(13, ids.length, "the cat text has 6 + 4 + 3 = 13 tokens");
        assertEquals(8, vocabulary.size(), "distinct words: the, cat, sat, on, mat, likes, milk, sleeps");
        assertEquals(ids[0], ids[6], "'the' at positions 0 and 6 share an ID");
        assertEquals(ids[1], ids[7], "'cat' at positions 1 and 7 share an ID");
    }
}

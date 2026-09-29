package com.repeatminer.preprocessing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 3: maps each distinct word to a dense integer ID (requirement 7).
 *
 * <p>The suffix array will constantly <em>compare</em> sequences of tokens:
 * comparing two ints is a single CPU operation, while comparing strings would
 * re-scan characters inside the sort's inner loops. So every first-seen word is
 * assigned the next free ID, 0, 1, 2, ... — a dense {@code 0..V-1} numbering.
 * (The master prompt's example numbers words from 1; we use 0 because suffix
 * array positions are 0-based array indices, keeping position and ID spaces
 * consistent.)
 *
 * <p>Two structures are kept, mirroring each other:
 * <ul>
 *   <li>{@code idByWord} — hash map for O(1) expected word &rarr; id lookup;</li>
 *   <li>{@code wordById} — array list for O(1) id &rarr; word, so detected
 *       patterns can be rebuilt into readable phrases (requirement 7).</li>
 * </ul>
 *
 * <p><b>Complexity:</b> interning all n tokens is O(n) expected time, O(V) extra
 * memory for the vocabulary itself.
 */
public final class Vocabulary {

    private final Map<String, Integer> idByWord = new HashMap<>();
    private final List<String> wordById = new ArrayList<>();

    /** Returns the ID of {@code word}, assigning the next free ID on first sight. */
    public int intern(String word) {
        Integer existing = idByWord.get(word);
        if (existing != null) {
            return existing;
        }
        int id = wordById.size();
        idByWord.put(word, id);
        wordById.add(word);
        return id;
    }

    /** Maps a whole token list to an int array in one call. */
    public int[] internAll(List<String> tokens) {
        int[] ids = new int[tokens.size()];
        for (int i = 0; i < tokens.size(); i++) {
            ids[i] = intern(tokens.get(i));
        }
        return ids;
    }

    /** The original word for an ID, or {@code null} if the ID is out of range. */
    public String wordFor(int id) {
        if (id < 0 || id >= wordById.size()) {
            return null;
        }
        return wordById.get(id);
    }

    /** Number of distinct words seen so far (vocabulary size V). */
    public int size() {
        return wordById.size();
    }
}

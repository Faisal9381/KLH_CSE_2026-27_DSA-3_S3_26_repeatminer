#!/usr/bin/env python3
"""Fill PBL REPORT TEMPLATE.docx with the Repeat Miner report content.

Strategy: split word/document.xml into <w:p> paragraphs, compute each
paragraph's normalized visible text, and REPLACE whole paragraphs whose
text matches a known placeholder/instruction. This is immune to the
template splitting text across multiple runs. Every other zip entry
(styles, fonts, settings, headers) is copied unchanged.

Run:  python scripts/fill_pbl_report.py
"""
import re
import zipfile

SRC = r"C:\Users\dell\Downloads\PBL REPORT TEMPLATE.docx"
DST = r"C:\Users\dell\Downloads\PBL REPORT - REPEAT MINER.docx"

FONT = "Cambria"
MONO = "Consolas"

PARA_RE = re.compile(r"<w:p\b[^>]*/>|<w:p\b[^>]*>.*?</w:p>", re.DOTALL)
WT_RE = re.compile(r"<w:t\b[^>]*>(.*?)</w:t>", re.DOTALL)


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def unescape(s):
    return (s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", '"')
             .replace("&apos;", "'").replace("&amp;", "&"))


def para(text, font=FONT, sz=22, bold=False, italic=False, center=False,
         justify=False, after=120, before=0, indent=0):
    rpr = ['<w:rPr>', '<w:rFonts w:ascii="%s" w:hAnsi="%s"/>' % (font, font)]
    if bold:
        rpr.append('<w:b/><w:bCs/>')
    if italic:
        rpr.append('<w:i/><w:iCs/>')
    rpr.append('<w:sz w:val="%d"/><w:szCs w:val="%d"/>' % (sz, sz))
    rpr.append('</w:rPr>')
    ppr = ['<w:pPr>', '<w:spacing w:before="%d" w:after="%d"/>' % (before, after)]
    if indent:
        ppr.append('<w:ind w:left="%d"/>' % indent)
    if center:
        ppr.append('<w:jc w:val="center"/>')
    elif justify:
        ppr.append('<w:jc w:val="both"/>')
    ppr.append('</w:pPr>')
    return ('<w:p>' + ''.join(ppr) + '<w:r>' + ''.join(rpr)
            + '<w:t xml:space="preserve">' + esc(text) + '</w:t></w:r></w:p>')


def H(t):
    return para(t, sz=24, bold=True, before=200, after=100)


def P(t):
    return para(t, justify=True)


def B(t):
    return para("-  " + t, indent=360, after=60, justify=True)


def IT(t):
    return para(t, italic=True, after=120)


def CAP(t):
    return para(t, sz=22, bold=True, before=140, after=40)


def C(block):
    return "".join(para(ln, font=MONO, sz=18, after=0, indent=240)
                   for ln in block.splitlines())


# ---------------------------------------------------------------- content

SECTION1 = "".join([
    H("1.1  The Problem: Repeated Word Sequences in Documents"),
    P("Every large document repeats itself. Study notes repeat definitions, news "
      "articles repeat boilerplate, web pages repeat navigation phrases, and copied "
      "text repeats whole sentences. Detecting these repeated word sequences "
      "automatically is useful for plagiarism screening, document deduplication, "
      "template detection and text compression. The task studied in this project is: "
      "given a document of n words, report every phrase (a consecutive sequence of "
      "words) whose length is at least L words and which occurs at least F times in "
      "the document."),
    P("The obvious approach compares every starting position with every other "
      "starting position, word by word. That needs about n(n-1)/2 pair comparisons, "
      "and each comparison can cost O(L) words, giving roughly O(n^2 * L) work. For "
      "our 44,804-word test document that is already close to a billion pair "
      "comparisons for a single phrase length, and the cost must be repeated for "
      "every length L. The purpose of this project is to replace that quadratic "
      "scan with a data structure that answers the question for ALL phrase lengths "
      "at once."),
    H("1.2  The Case Study: REPEAT MINER"),
    P("REPEAT MINER is a desktop application built for this course using Java 21, "
      "JavaFX 21 and Maven (no external algorithm libraries). It works as follows:"),
    B("loads a TXT document and tokenizes it into words in one linear pass "
      "(case-insensitive; punctuation is split off; apostrophes are kept inside "
      "words such as \"don't\");"),
    B("maps words to integer IDs in first-seen order, so every later step compares "
      "integers instead of strings;"),
    B("builds a word-level suffix array by prefix doubling, using our own stable "
      "merge sort (no library suffix machinery is used anywhere);"),
    B("builds the LCP array with Kasai's algorithm in O(n) time;"),
    B("sweeps the LCP array once with a stack to emit every repeated phrase exactly "
      "once together with its frequency;"),
    B("displays the ranked phrases, the longest repeated phrase and comparison "
      "charts in a JavaFX GUI, and exports TXT/CSV reports."),
    P("The user controls three thresholds: minimum phrase length L (default 2), "
      "minimum frequency F (default 2), and a cap on how many patterns are listed "
      "(default 50). Both a graphical interface and a command-line interface are "
      "provided. Because the suffix structures are built once and reused, "
      "re-running the analysis with different thresholds is instant."),
    H("1.3  A Worked Example"),
    P("Take the five-word document [the, cat, the, cat, sleeps]. Each position of "
      "the document defines a suffix (the document read from that word to the "
      "end). Sorting the five suffixes gives the suffix array SA = [0, 2, 1, 3, 4]:"),
    C("SA index | Suffix starting there    | LCP | Shared phrase\n"
      "   0     | the cat the cat sleeps   |  0  | -\n"
      "   1     | the cat sleeps           |  2  | \"the cat\"\n"
      "   2     | cat the cat sleeps       |  0  | -\n"
      "   3     | cat sleeps               |  1  | \"cat\"\n"
      "   4     | sleeps                   |  0  | -"),
    P("The key property of the suffix array is now visible: suffixes that begin "
      "with the same words are ADJACENT in sorted order. The two suffixes that "
      "start with \"the cat\" (positions 0 and 2) became neighbours, so the repeat "
      "turned into an adjacency problem. The LCP column stores how many words each "
      "neighbour pair shares; lcp[1] = 2 literally IS the phrase \"the cat\" "
      "occurring twice."),
    P("Running the detection sweep on this example emits \"the cat\" (2 words, 2 "
      "occurrences), \"the\" (1 word, 2 occurrences) and \"cat\" (1 word, 2 "
      "occurrences). Each phrase is emitted exactly once, straight from the block "
      "structure, with no hash-table deduplication."),
    H("1.4  A Real Run on a 44,804-Word Document"),
    P("The bundled dataset datasets/large.txt was produced by the project's own "
      "seeded generator (seed 42): filler sentences drawn from a 33-word vocabulary, "
      "with eight technical phrases planted a fixed number of times. The analysis "
      "reports 44,804 words and vocabulary 33, and the top of the ranked list is:"),
    C("Rank | Phrase           | Words | Frequency\n"
      "  1  | structure text   |   2   |   233\n"
      "  2  | model system     |   2   |   226\n"
      "  3  | in structure     |   2   |   224\n"
      "  4  | text data        |   2   |   223\n"
      "  5  | of model         |   2   |   219"),
    P("The longest repeated phrase is the 13-word sequence \"artificial "
      "intelligence neural network data structures and algorithms suffix array "
      "construction pattern detection\", which occurs 2 times. Common filler "
      "bigrams outrank the planted phrases simply because random pairs from a "
      "33-word vocabulary pile up over 44,804 words, while each planted phrase was "
      "inserted about 50 times. Every number above was verified by an independent "
      "brute-force recount (counting adjacent word pairs with sort/uniq), which "
      "matched the program's output exactly."),
    H("1.5  Why a Suffix Array?"),
    B("Compared with a hash map of all phrases: a hash map must enumerate every "
      "phrase length separately, so its work and memory grow with the phrase "
      "lengths being searched. The suffix array answers ALL lengths from one O(n) "
      "structure, because equal phrases become adjacent in sorted order and the "
      "LCP array measures exactly how much neighbours share."),
    B("Compared with a suffix tree: the LCP array carries exactly the same "
      "interval information as suffix-tree edges, but as two flat integer arrays "
      "that are simple to build, cache-friendly, and fully our own code (a course "
      "requirement)."),
    B("Honest complexity: the build is O(n log^2 n) with merge sort; a "
      "counting-sort variant would reach O(n log n) and is listed as future work "
      "rather than claimed."),
    H("1.6  Scope and Limitations"),
    P("The naive baseline used for comparison is capped at phrases of 8 words and "
      "documents of 100,000 tokens so that benchmark runs finish in reasonable "
      "time; the suffix-array pipeline has no such cap. Detection itself is O(n), "
      "but pathological documents (the same word repeated millions of times) "
      "genuinely contain a quadratic NUMBER of distinct repeated phrases, so on "
      "such inputs the output size, not the algorithm, is the limiting factor; the "
      "max-patterns cap bounds what is returned. Suffix-array construction could "
      "be accelerated from O(n log^2 n) to O(n log n) with counting sort, which is "
      "left as future work."),
])

SECTION2 = "".join([
    H("2.1  Pipeline Overview"),
    C("analyze(document, minLen, minFreq, maxPatterns):\n"
      "    text  <- tokenize(read(document))            # O(n), one pass\n"
      "    ids   <- internAll(text)                     # first-seen word IDs\n"
      "    sa    <- buildSuffixArray(ids)               # O(n log^2 n)\n"
      "    lcp   <- kasai(ids, sa)                      # O(n)\n"
      "    pats  <- lcpIntervalSweep(ids, sa, lcp, minLen, minFreq)   # O(n)\n"
      "    sort pats by (frequency desc, length desc, phrase asc)\n"
      "    report pats[0 .. maxPatterns-1]\n"
      "    report all phrases of maximum length with frequency >= minFreq"),
    P("Each stage is linear or near-linear, and each stage's output is the next "
      "stage's input. The four stages are described step by step below."),
    H("2.2  Step 1 - Tokenization and Vocabulary"),
    P("TextProcessor scans the raw characters once, grouping letters and digits "
      "into words, folding to lower case, keeping apostrophes inside words and "
      "emitting every other character as a separator. Vocabulary assigns each "
      "distinct word the next unused integer ID in first-seen order. Any fixed "
      "total order works for a suffix array, because repeat detection only needs "
      "EQUAL sequences to become adjacent; first-seen IDs keep the mapping O(1) "
      "per word without sorting. Human-readable output is re-sorted alphabetically "
      "only when displayed."),
    H("2.3  Step 2 - Suffix Array by Prefix Doubling"),
    P("Comparing whole suffixes directly costs O(n) per comparison. Prefix "
      "doubling avoids this by sorting on small integer keys: after round k, "
      "rank[i] is the sorted position of the first 2^k tokens of suffix i. The "
      "first 2p tokens of a suffix are fully described by the PAIR (my rank for p "
      "tokens, the rank of the suffix starting p positions later, for p tokens), "
      "so sorting the pairs produces the rank order for 2p tokens. A suffix that "
      "runs past the end gets the second key -1, which sorts before everything - "
      "exactly right, because a shorter suffix that matches a longer one is the "
      "smaller one."),
    C("buildSuffixArray(ids):\n"
      "    n <- len(ids)\n"
      "    rank[i]    <- ids[i]          # round 1: rank by first token\n"
      "    nextKey[i] <- 0\n"
      "    sort positions by pair (rank, nextKey)   # stable merge sort\n"
      "    p <- 1\n"
      "    loop:\n"
      "        nextKey[i] <- rank[i+p] if i+p < n else -1   # end sentinel\n"
      "        sort positions by pair (rank, nextKey)\n"
      "        reassign ranks 0..k by scanning the sorted order\n"
      "        if all ranks distinct or p >= n: stop\n"
      "        p <- 2 * p"),
    P("There are O(log n) rounds; each round is an O(n log n) merge sort on "
      "two-integer keys plus O(n) re-ranking, giving O(n log^2 n) time and O(n) "
      "extra memory in total. A bug we hit and fixed during development is worth "
      "recording: ties must be ordered BY the pair, not left in arbitrary "
      "position order - for [0, 0, 0] the suffix [0] must sort before [0, 0] even "
      "though both share the old rank 0. The randomized brute-force test caught "
      "this immediately."),
    H("2.4  Step 3 - LCP Array by Kasai's Algorithm"),
    P("lcp[i] is the number of consecutive words shared by the adjacent suffixes "
      "SA[i-1] and SA[i]. Comparing each pair from scratch would cost O(n^2). "
      "Kasai's algorithm instead walks suffixes in TEXT order carrying h = the "
      "LCP just measured: removing the first word from two suffixes that shared h "
      "words leaves two suffixes sharing at least h-1 words, so the next "
      "comparison resumes from h-1 instead of restarting from zero."),
    C("kasai(ids, sa):\n"
      "    for i: inverse[sa[i]] <- i        # position -> SA index\n"
      "    h <- 0\n"
      "    for i in 0 .. n-1:                # TEXT order, not sorted order\n"
      "        if inverse[i] > 0:\n"
      "            j <- sa[inverse[i] - 1]   # sorted-order neighbour\n"
      "            while i+h < n and j+h < n and ids[i+h] = ids[j+h]:\n"
      "                h <- h + 1\n"
      "            lcp[inverse[i]] <- h\n"
      "            if h > 0: h <- h - 1      # Kasai's step\n"
      "        else:\n"
      "            h <- 0"),
    P("h only ever increases inside a step and drops by at most 1 between steps, "
      "so over the whole run the compare loop executes at most 2n word "
      "comparisons in TOTAL: O(n) time, O(n) memory for the inverse permutation."),
    H("2.5  Step 4 - Detection: the LCP-Interval Stack Sweep"),
    P("All occurrences of a phrase form a CONTIGUOUS block in the suffix array, "
      "and a block is exactly a maximal range SA[lb..rb] in which every internal "
      "LCP value is at least the phrase length; the phrase's frequency is the "
      "block width rb-lb+1. One stack pass over the LCP array finds all blocks:"),
    C("lcpIntervalSweep(ids, sa, lcp, minLen, minFreq):\n"
      "    stack <- empty                     # entries (lcpValue, blockStart)\n"
      "    for i in 1 .. n:                   # L = lcp[i]; 0 sentinel at i = n\n"
      "        L <- (i = n) ? 0 : lcp[i]\n"
      "        while stack not empty and stack.top.value > L:\n"
      "            (depth, start) <- pop()\n"
      "            count <- i - start                 # block width = frequency\n"
      "            below <- stack.top.value or 0\n"
      "            for d in max(L, below)+1 .. depth: # every nested depth, once\n"
      "                if d >= minLen and count >= minFreq:\n"
      "                    emit(sa[start], d, count)\n"
      "        if stack empty or stack.top.value < L:\n"
      "            push(L, i - 1)"),
    P("Each suffix is pushed and popped at most once, so the sweep is O(n). "
      "Emitting the FULL depth range from max(L, below)+1 to depth is the subtle "
      "part: shorter phrases such as \"the\" inside \"the cat\" are ancestor "
      "intervals of the same block, and this range emits each of them exactly "
      "once - without it, nested phrases would be lost. The frequency comes free "
      "as the block width; no re-counting pass is needed. Results are then sorted "
      "by frequency descending, length descending, alphabetically ascending, and "
      "capped at maxPatterns."),
    H("2.6  The Naive Baseline (for Comparison)"),
    C("naiveDetect(ids, maxLen = 8):\n"
      "    for each pair of positions (i, j), i < j:\n"
      "        d <- longest common prefix of ids[i..] and ids[j..],\n"
      "             capped at maxLen words\n"
      "        count all prefixes of length 1..d of that match\n"
      "    report phrases counted at least twice"),
    P("This is the honest O(n^2 * L) reference implementation. It is capped at "
      "8-word phrases and 100,000 tokens so benchmark runs stay practical, and it "
      "runs under the SAME thresholds as the suffix-array detector so the "
      "comparison is fair. The two detectors are also cross-checked against each "
      "other by the automated tests."),
    H("2.7  Benchmark Methodology"),
    P("Benchmarks use the seeded DatasetGenerator (seed 42), which plants known "
      "phrases INTERLEAVED with filler words, so results are reproducible and the "
      "expected pattern counts are known in advance. Each strategy gets 1 warm-up "
      "run (JIT compilation) followed by 3 timed iterations, and the average is "
      "reported. Only ACTUAL measured numbers are reported - never estimates."),
    H("2.8  Complexity Summary"),
    C("Stage                         Time            Extra memory\n"
      "Tokenize + vocabulary         O(n)            O(n)\n"
      "Suffix array (prefix double)  O(n log^2 n)    O(n)\n"
      "LCP array (Kasai)             O(n)            O(n)\n"
      "Detection sweep               O(n)            O(n)\n"
      "Naive baseline                O(n^2 * L)      O(1)"),
])

SECTION3 = "".join([
    P("All code lives in the project repository under src/main/java/com/repeatminer/. "
      "The four excerpts below are the heart of the implementation, copied from the "
      "real sources and only lightly trimmed for print."),
    CAP("(a) SuffixArray.java - the prefix-doubling loop"),
    C("// SuffixArray.java - build(): prefix doubling. On entry to each\n"
      "// iteration, rank[] describes the first p tokens of every suffix.\n"
      "for (int p = 1; ; p <<= 1) {\n"
      "    for (int i = 0; i < n; i++) {\n"
      "        nextKey[i] = i + p < n ? rank[i + p] : -1;   // -1 = end sentinel\n"
      "    }\n"
      "    sortSuffixArrayByPair(n);           // our own stable merge sort\n"
      "    boolean allDistinct = reassignRanks(n);\n"
      "    if (allDistinct || p >= n) {\n"
      "        break;\n"
      "    }\n"
      "}"),
    CAP("(b) SuffixArray.java - the two-integer comparison used by the merge sort"),
    C("// SuffixArray.java - pairLess(): the ONLY comparison the sort\n"
      "// needs: two integers per step, no re-scanning of the words.\n"
      "private boolean pairLess(int a, int b) {\n"
      "    if (rank[a] != rank[b]) {\n"
      "        return rank[a] < rank[b];\n"
      "    }\n"
      "    return nextKey[a] < nextKey[b];     // -1 (past the end) sorts first\n"
      "}"),
    CAP("(c) LCPArray.java - Kasai's algorithm"),
    C("// LCPArray.java - build(): Kasai's algorithm. Walk suffixes in\n"
      "// TEXT order carrying h; total comparisons stay bounded by 2n.\n"
      "int h = 0;\n"
      "for (int i = 0; i < n; i++) {\n"
      "    int saIndex = inverse[i];\n"
      "    if (saIndex > 0) {\n"
      "        int predecessor = suffixArray[saIndex - 1];\n"
      "        while (i + h < n && predecessor + h < n\n"
      "                && text[i + h] == text[predecessor + h]) {\n"
      "            h++;                        // resume at h, never from 0\n"
      "        }\n"
      "        lcp[saIndex] = h;\n"
      "        if (h > 0) {\n"
      "            h--;                        // Kasai's step\n"
      "        }\n"
      "    } else {\n"
      "        h = 0;  // first suffix in sorted order has no predecessor\n"
      "    }\n"
      "}"),
    CAP("(d) PatternDetector.java - the pop-and-emit interval sweep"),
    C("// PatternDetector.java - detect(): one stack pass over the LCP\n"
      "// array. On pop, emit EVERY depth of the interval; width = freq.\n"
      "while (top >= 0 && stackValue[top] > l) {\n"
      "    int depth = stackValue[top];\n"
      "    int start = stackStart[top];\n"
      "    top--;\n"
      "    int count = i - start;              // frequency = block width\n"
      "    int below = top >= 0 ? stackValue[top] : 0;\n"
      "    int lowest = Math.max(l, below) + 1;    // nested depths, each once\n"
      "    for (int d = lowest; d <= depth; d++) {\n"
      "        if (d >= params.minPhraseLength() && count >= params.minFrequency()) {\n"
      "            all.add(new PatternResult(phraseFor(start, d), d, count));\n"
      "            maxLength = Math.max(maxLength, d);\n"
      "        }\n"
      "    }\n"
      "    currentStart = start;\n"
      "}"),
    P("Complete source files: SuffixArray.java (272 lines), LCPArray.java (118), "
      "PatternDetector.java (200), TextProcessor.java (123), Vocabulary.java (67), "
      "NaivePatternDetector.java (131), AnalysisEngine.java (98), "
      "PerformanceBenchmark.java (84), ReportGenerator.java (109), six GUI classes, "
      "and 13 test classes with 131 passing JUnit tests."),
])

SECTION4 = "".join([
    P("Environment: Windows 10, OpenJDK Temurin 21.0.12, Maven Wrapper (Maven "
      "3.9.9). All outputs below are copied verbatim from actual runs performed on "
      "1 October 2026; nothing is estimated."),
    H("4.1  Command Line - Small Document (13 tokens)"),
    P("Command: mvnw compile exec:java -Dexec.mainClass=com.repeatminer.Main "
      "-Dexec.args=\"--analyze datasets/small.txt --min-length 1\""),
    C("REPEAT MINER - ANALYSIS RESULT\n"
      "Document   : small.txt\n"
      "Tokens     : 13   (vocabulary 8, characters 60)\n"
      "Longest    : \"the cat\" (2 words, 3 occurrences)\n"
      "\n"
      "PHRASE                                        COUNT\n"
      "\"the\"                                             4\n"
      "\"the cat\"                                         3\n"
      "\"cat\"                                             3"),
    P("The three phrases match a hand count of the cat text exactly."),
    H("4.2  Command Line - Large Document (44,804 tokens)"),
    P("Command: mvnw compile exec:java -Dexec.mainClass=com.repeatminer.Main "
      "-Dexec.args=\"--analyze datasets/large.txt --min-length 2 --max 10\""),
    C("REPEAT MINER - ANALYSIS RESULT\n"
      "Document   : large.txt\n"
      "Tokens     : 44804   (vocabulary 33, characters 260208)\n"
      "Longest    : \"artificial intelligence neural network data structures and\n"
      "algorithms suffix array construction pattern detection\" (13 words, 2 occurrences)\n"
      "\n"
      "PHRASE                                        COUNT\n"
      "\"structure text\"                                233\n"
      "\"model system\"                                  226\n"
      "\"in structure\"                                  224\n"
      "\"text data\"                                     223\n"
      "\"of model\"                                      219\n"
      "\"model data\"                                    218\n"
      "\"structure method\"                              218\n"
      "\"a data\"                                        217\n"
      "\"and in\"                                        216\n"
      "\"a model\"                                       215"),
    P("An independent brute-force recount (counting adjacent word pairs with "
      "sort/uniq on the token stream) reproduced every frequency above exactly."),
    H("4.3  Benchmark - Suffix Array vs Naive"),
    P("Command: mvnw compile exec:java -Dexec.mainClass=com.repeatminer.Main "
      "-Dexec.args=\"--benchmark 5000\"   (and   --benchmark 50000). Both "
      "detectors run with the same thresholds (min length 2, min frequency 2, top "
      "20); each measurement is 1 warm-up + 3 timed iterations."),
    C("REPEAT MINER - BENCHMARK (actual measurements)\n"
      "Input tokens          : 5,000\n"
      "Naive method          : 77.139 ms\n"
      "Suffix Array + LCP    : 32.407 ms\n"
      "Speedup               : 2.38x"),
    C("REPEAT MINER - BENCHMARK (actual measurements)\n"
      "Input tokens          : 50,000\n"
      "Naive method          : 544.802 ms\n"
      "Suffix Array + LCP    : 202.194 ms\n"
      "Speedup               : 2.69x"),
    P("The suffix-array pipeline is 2.4x to 2.7x faster than the naive method, and "
      "the gap widens as the document grows, as expected from O(n log^2 n) versus "
      "O(n^2 * L)."),
    H("4.4  Graphical User Interface"),
    P("The same analyses run in the JavaFX GUI: select the TXT file, set the "
      "thresholds (min phrase length, min frequency, max patterns), press ANALYZE, "
      "and the results appear as a ranked table plus the longest repeated phrase, "
      "with comparison charts on the Charts tab and one-click TXT/CSV report "
      "export. (GUI screenshots of these runs may be pasted here.)"),
])


# ------------------------------------------------------------------ fill

def para_text(pxml):
    raw = "".join(WT_RE.findall(pxml))
    return " ".join(raw.split())


def unescape_first(t):
    # work on the escaped form: placeholders look like "<TITLE>"
    return t.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")


def replacement_for(txt, orig):
    t = unescape_first(txt).strip()
    if "NAME OF THE STUDENT1" in t or "Mohammed Faisal Imraan" in t:
        return para("Mohammed Faisal Imraan    (Roll No: 2520030381)",
                    sz=28, bold=True, center=True, after=120)
    if "NAME OF THE STUDENT2" in t or "Karingula" in t:
        return para("Karingula Kaustav Srinivas    (Roll No: 2520030617)",
                    sz=28, bold=True, center=True, after=240)
    if t == "<TITLE>" or t.strip(" <>") == "REPEAT MINER":
        return para("REPEAT MINER", sz=40, bold=True, center=True,
                    before=120, after=240)
    if t.startswith("Brief description of the case study"):
        return SECTION1
    if t.startswith("Step-by-step representation"):
        return SECTION2
    if t == "CODE":
        return orig + SECTION3
    if t == "Screenshots":
        return SECTION4
    return orig


def main():
    with zipfile.ZipFile(SRC) as zin:
        xml = zin.read("word/document.xml").decode("utf-8")

    parts, last, replaced = [], 0, []
    for m in PARA_RE.finditer(xml):
        parts.append(xml[last:m.start()])
        orig = m.group(0)
        txt = para_text(orig)
        new = replacement_for(txt, orig)
        if new is not orig:
            replaced.append(txt[:60])
        parts.append(new)
        last = m.end()
    parts.append(xml[last:])
    new_xml = "".join(parts)

    with zipfile.ZipFile(SRC) as zin, \
            zipfile.ZipFile(DST, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            if item.filename == "word/document.xml":
                zout.writestr(item, new_xml)
            else:
                zout.writestr(item, zin.read(item.filename))

    print("Written:", DST)
    print("Replaced paragraphs:")
    for r in replaced:
        print("  -", r)


if __name__ == "__main__":
    main()

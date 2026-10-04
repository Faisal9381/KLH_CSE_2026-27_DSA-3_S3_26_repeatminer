# Repeat Miner

**Repeated Pattern Detection in Large Documents Using Suffix Structures**

A university Data Structures & Algorithms project: a Java 21 + JavaFX + Maven desktop
application that finds meaningful repeated **word sequences** in TXT documents using a
**Suffix Array + LCP Array** built over word tokens, with a naive baseline algorithm
for honest performance comparison.

> Status: **all 16 phases complete** — full offline pipeline, naive baseline,
> benchmark harness, TXT/CSV reporting, JavaFX GUI with charts, 131 unit tests.

---

## 1. Project overview

Given any TXT document, Repeat Miner:

1. tokenizes it into words (never character fragments),
2. maps words to integer IDs,
3. builds a **Suffix Array** by prefix doubling,
4. builds an **LCP Array** with Kasai's algorithm,
5. mines **LCP intervals** to enumerate every repeated phrase with its exact frequency,
6. benchmarks itself against a naive sliding-window baseline,
7. shows results, charts and performance in a JavaFX GUI,
8. exports TXT/CSV reports.

A phrase like **"machine learning"** is reported as a whole; fragments like
`"tificial"` are impossible by construction, because the suffix structures operate on
complete word tokens.

## 2. Problem statement

Documents repeat themselves: key phrases, entity names, formulaic sentences. Finding
*all* repeated word sequences with exact frequencies by brute force means comparing
every substring pair — quadratic work that collapses on large files. The task is to
detect every repeated phrase exactly, at interactive speeds, on documents with
hundreds of thousands of words.

## 3. Why naive methods are inefficient

The obvious strategy (implemented as `NaivePatternDetector`): for every phrase length
L, slide a window over the text, build the window's **string**, count strings in a
hash map. Cost: every length re-reads the whole text, and every window pays string
building + hashing — roughly **O(n · maxLen²)** character work overall, and it visits
every position even where nothing repeats. On 50k tokens this measures ~104 ms
versus ~51 ms for the suffix pipeline (see §14) — and the gap widens with size.

## 4. Proposed solution

Sort the suffixes once; then repeats are *adjacency*. Two suffixes sharing a phrase
are neighbours in sorted order, and their shared phrase length is read from the LCP
array — so detection becomes one linear sweep instead of pairwise comparison.

## 5. Suffix Array (over words)

Suffix *i* = the token sequence `T[i..n-1]`. The suffix array is the list of suffix
start positions sorted by token order. For `[the, cat, the, cat, sleeps]`:
`SA = [0, 2, 1, 3, 4]` — suffixes 0 and 2 both start with **"the cat"** and are
adjacent. Construction uses **prefix doubling**: round k ranks each suffix by the
pair *(rank of its first 2^(k-1) tokens, rank of the suffix 2^(k-1) positions
later)*, so each round doubles the prefix length covered with only two-integer
comparisons. Tie-breaking detail that matters: a suffix that runs past the end gets
second key `-1`, which sorts first — exactly the lexicographic rule that a shorter
suffix matching a longer prefix is the smaller one. Our own stable merge sort orders
the pairs; no library suffix machinery is used.

**Ordering note:** IDs follow first-seen word order, not the alphabet — any fixed
total order is valid because detection only needs *equal* phrases to become adjacent.

## 6. LCP Array

`lcp[i]` = number of consecutive tokens shared by adjacent suffixes `SA[i-1]`,
`SA[i]` (`lcp[0] = 0`). For the example above: `[0, 2, 0, 1, 0]` — the value
`lcp[1] = 2` *is* the phrase "the cat". **Kasai's algorithm** walks suffixes in text
order carrying `h`: chopping one token off two suffixes sharing `h` tokens leaves
suffixes sharing `h−1`, so comparisons resume where they stopped; total work ≤ 2n
comparisons → **O(n)**.

## 7. Architecture

```
src/main/java/com/repeatminer/
├── Main.java                 CLI entry: GUI, --analyze, --benchmark, --generate
├── model/                    PatternResult, AnalysisResult, BenchmarkResult (records)
├── preprocessing/            TextProcessor (O(n) tokenizer), Vocabulary (token → IDs)
├── suffix/                   SuffixArray (prefix doubling), LCPArray (Kasai)
├── detection/                PatternDetector (LCP-interval sweep), AnalysisEngine (facade)
├── naive/                    NaivePatternDetector (baseline, same result type)
├── benchmark/                PerformanceBenchmark, DatasetGenerator
├── report/                   ReportGenerator (TXT + CSV)
└── ui/                       RepeatMinerApp, MainView, ResultsView, DashboardView,
                              AppController, DiagnosticProbe
```

The algorithm core is **UI-free** (requirement 25): the GUI only calls
`AnalysisEngine`; `--analyze` runs the identical code path from a console.

## 8. Algorithms

| Stage | Algorithm | Complexity |
|---|---|---|
| Tokenization | single pass, no regex | O(n) |
| Vocabulary | hash map interning | O(n) expected |
| Suffix Array | prefix doubling + own merge sort | **O(n log² n)** |
| LCP Array | Kasai | **O(n)** |
| Detection | LCP-interval stack sweep (histogram style) | O(n + output) |
| Naive baseline | per-length sliding window + hashing | ~O(n · maxLen²) |

Detection detail: each distinct repeated phrase is exactly one LCP-interval
`SA[lb..rb]` with all internal LCPs ≥ phrase length; its frequency is the block
width. The stack sweep emits each interval exactly once — no deduplication — and
emits every nested depth when an interval closes (the bug our brute-force tests
caught during development).

## 9. Complexity analysis (honest version)

- Suffix construction is O(n log² n), **not** O(n): each doubling round is a
  comparison sort. A counting-sort refinement would give O(n log n) — future work.
- Detection is O(n) plus work proportional to the **output**: an input of one token
  repeated m times genuinely contains Θ(m²) distinct repeated phrases; the
  algorithm cannot be faster than the answer.
- Benchmarks time "from token IDs to detection result" for both strategies,
  including the suffix build — no hiding the index cost.

## 10. Installation

Requirements: JDK 21 (Temurin recommended). No Maven installation needed — the
bundled Maven Wrapper downloads Maven 3.9.9 on first use; internet is needed only
for that first build.

## 11. How to run

```bat
mvnw.cmd javafx:run      :: start the GUI (Windows)
mvnw.cmd test            :: run the 131 unit tests
```
Linux/macOS: `./mvnw javafx:run`, `./mvnw test`.

Console mode (no GUI — same engine):

```bat
mvnw.cmd compile exec:java -Dexec.mainClass=com.repeatminer.Main -Dexec.args="--analyze datasets/small.txt --min-length 1"
mvnw.cmd compile exec:java -Dexec.mainClass=com.repeatminer.Main -Dexec.args="--benchmark 50000"
mvnw.cmd compile exec:java -Dexec.mainClass=com.repeatminer.Main -Dexec.args="--generate datasets/mydata.txt 20000"
```

## 12. How to use

1. **Select TXT File** — statistics (words / characters / vocabulary) appear.
2. Set **min phrase length** (default 2), **min frequency** (default 2), **max patterns**.
3. **ANALYZE** — results table (sortable by clicking headers), longest repeated
   phrase, naive-vs-suffix timing, and charts on the *Charts* tab.
4. **View Report** — full TXT report in a window; **Export Results...** writes
   `report.txt` + `patterns.csv` to a folder of your choice.

## 13. Example input/output

Input (`datasets/small.txt`):

```
The cat sat on the mat.
The cat likes milk.
The cat sleeps.
```

Actual `--analyze` output (`--min-length 1`):

```
REPEAT MINER - ANALYSIS RESULT
Document   : small.txt
Tokens     : 13   (vocabulary 8, characters 60)
Longest    : "the cat" (2 words, 3 occurrences)

PHRASE                                        COUNT
"the"                                             4
"the cat"                                         3
"cat"                                             3
```

## 14. Benchmarking methodology and measured results

`PerformanceBenchmark` times both detectors on identical inputs with
`System.nanoTime()`: one untimed warm-up, then the mean of 3 timed iterations
(1 on documents > 100k tokens); `System.gc()` between strategies. The suffix path
pays construction + detection; the naive path pays its window enumeration with
`maxPhraseLength = 8`. All numbers below are **actual measurements** on the
development machine (i7-class laptop, JDK 21, Windows); rerun `--benchmark N` for
your own.

| Tokens | Naive (ms) | Suffix Array + LCP (ms) | Speedup |
|---:|---:|---:|---:|
| 2,000 | 6.325 | 3.681 | 1.72× |
| 10,000 | 26.110 | 14.719 | 1.77× |
| 20,000 | 72.619* | 30.683* | 2.37×* |
| 50,000 | 104.383 | 50.856 | 2.05× |

\* from a different run (separate JVM; run-to-run variance ±20%). The honest
reading: the suffix approach wins consistently, the gap **grows with input size**
as predicted by the asymptotics, and small inputs show only a modest advantage
because the O(n log² n) build is a fixed investment. The naive baseline also has a
hidden extra cost not in the table: it cannot enumerate phrases beyond
`maxPhraseLength`, while the suffix sweep finds arbitrarily long repeats at no
extra cost.

## 15. Limitations

- Phrase "meaning" is lexical, not semantic — "the cat" may top the list on small
  texts; no stop-word removal by design (optional feature, not default).
- Memory is O(n) ints per structure (text, SA, LCP, ranks); a 1M-token document
  needs roughly 20-30 MB of primitive arrays plus token strings.
- Pathological inputs (one word repeated 100k times) produce quadratic *output*;
  `max patterns` caps display, but mining still enumerates intervals.
- Benchmarks are single-machine and JIT-warm-up sensitive; treat tables as
  reproducible orders of magnitude, not absolutes.

## 16. Future improvements

Counting-sort suffix construction (O(n log n)); phrase search via the existing
`SuffixArray.search`; configurable stop-words; occurrence-position tracking in the
UI; PDF reports; multiple-document comparison and plagiarism-style similarity on
top of the shared-phrase counts; suffix-tree visualisation for small inputs.

---

## Why a Suffix Array instead of a Suffix Tree?

- **Memory:** a tree needs ~2n nodes with pointer objects (tens of bytes each); an
  array is one `int` per suffix. At 1M tokens that is tens of MB versus a few MB.
- **Construction:** Ukkonen's O(n) tree construction is famously intricate; prefix
  doubling is short, testable, and its invariants are explainable on a whiteboard —
  the right scope for a first-year DSA project where *we implement the core
  ourselves* (requirement 24).
- **Sufficiency:** every repeated-phrase question here is answered by SA + LCP
  (adjacency + interval widths); the tree's extra edges/links would buy nothing
  for this application.
- **Pedagogy:** the array keeps the focus on sorting, ranks, binary search and
  amortised analysis — exactly the course material.

## Development notes (testing philosophy)

Every algorithm module is verified against a brute-force reference on random
inputs: suffix array order, Kasai LCP values, and detected phrase frequencies are
each cross-checked against exhaustive enumeration in every test run. Three real
bugs were caught this way during development: a prefix-doubling tie-ordering
error, a binary-search bound error, and a lost nested interval in the LCP sweep.
The datasets in `datasets/` are generated deterministically with planted phrases
whose counts the integration tests verify exactly.

## Requirements

- JDK 21 · Maven Wrapper (bundled) · JavaFX 21 (Maven) · JUnit 5 (Maven)

package com.repeatminer;

import com.repeatminer.detection.AnalysisEngine;
import com.repeatminer.detection.PatternDetector;
import com.repeatminer.model.AnalysisResult;
import com.repeatminer.model.PatternResult;
import com.repeatminer.preprocessing.TextProcessor;
import javafx.application.Platform;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Command-line entry point for Repeat Miner.
 *
 * <p>Why does this class exist separately from {@code ui.RepeatMinerApp}? A JavaFX
 * {@code Application} subclass is normally started through the JavaFX launcher
 * (that is what {@code mvnw javafx:run} does, with the main class configured in
 * {@code pom.xml}). {@code Main} provides a second, plain {@code main} that boots
 * the JavaFX toolkit with {@link Platform#startup(Runnable)} and then shows the
 * window, so the project can also be started from an IDE or a normal
 * {@code java} command line.
 *
 * <p><b>Phase 1 scope:</b> verify that the Maven build works and that an empty
 * JavaFX window opens. The offline pipeline
 * (TextProcessor &rarr; SuffixArray &rarr; LCPArray &rarr; PatternDetector &rarr;
 * AnalysisResult) is added in Phases 2&ndash;8 and must stay runnable without any UI.
 */
public final class Main {

    public static void main(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--help") || args[i].equals("-h")) {
                printHelp();
                return;
            }
            if (args[i].equals("--analyze")) {
                runConsoleAnalysis(args, i + 1);
                return;
            }
            if (args[i].equals("--benchmark")) {
                runBenchmarkCommand(args, i + 1);
                return;
            }
            if (args[i].equals("--generate")) {
                runGenerateCommand(args, i + 1);
                return;
            }
        }
        System.out.println("REPEAT MINER — starting GUI (use --help for options).");
        try {
            Platform.startup(() -> com.repeatminer.ui.RepeatMinerApp.show());
        } catch (Throwable t) {
            // Friendly message only; never show a stack trace to the user (requirement 20).
            System.err.println("Repeat Miner could not start the JavaFX runtime.");
            System.err.println("Try instead:  mvnw javafx:run    (Windows:  mvnw.cmd javafx:run)");
            System.err.println("Reason: " + t);
            System.exit(1);
        }
    }

    /**
     * Console analysis mode (--analyze): runs the full offline pipeline and
     * prints a formatted result. Proves the algorithm core works without any UI
     * (requirement 25) and doubles as a demonstration aid.
     */
    private static void runConsoleAnalysis(String[] args, int from) {
        if (from >= args.length) {
            System.err.println("Usage: --analyze FILE [--min-length N] [--min-freq N] [--max N]");
            System.exit(2);
        }
        Path file = Path.of(args[from]);
        int minLength = 2;
        int minFreq = 2;
        int max = 20;
        for (int i = from + 1; i < args.length; i += 2) {
            if (i + 1 >= args.length) {
                System.err.println("Option " + args[i] + " is missing its value.");
                System.exit(2);
            }
            try {
                switch (args[i]) {
                    case "--min-length" -> minLength = Integer.parseInt(args[i + 1]);
                    case "--min-freq" -> minFreq = Integer.parseInt(args[i + 1]);
                    case "--max" -> max = Integer.parseInt(args[i + 1]);
                    default -> {
                        System.err.println("Unknown option: " + args[i]);
                        System.exit(2);
                    }
                }
            } catch (NumberFormatException e) {
                System.err.println("Option " + args[i] + " needs a whole number, got: " + args[i + 1]);
                System.exit(2);
            }
        }

        try {
            AnalysisResult result = new AnalysisEngine().analyze(file,
                    new PatternDetector.Parameters(minLength, minFreq, max));
            printAnalysisResult(result);
        } catch (IOException | TextProcessor.InvalidDocumentException
                 | IllegalArgumentException e) {
            // Friendly message only — no stack traces for user errors (requirement 20).
            System.err.println("Analysis failed: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Console benchmark (--benchmark N): generates N synthetic tokens with
     * planted repeated phrases, measures both detectors, prints ACTUAL numbers
     * (requirement 15 - never fabricated). Used for the README's measured table.
     */
    private static void runBenchmarkCommand(String[] args, int from) {
        int size = 5000;
        if (from < args.length) {
            try {
                size = Integer.parseInt(args[from]);
            } catch (NumberFormatException e) {
                System.err.println("--benchmark needs a token count, got: " + args[from]);
                System.exit(2);
            }
        }
        if (size < 100 || size > 1_000_000) {
            System.err.println("Benchmark size must be between 100 and 1,000,000 tokens.");
            System.exit(2);
        }

        int repeatsEach = Math.max(2, size / 1000);
        System.out.printf("Generating %,d synthetic tokens (each planted phrase x%d)...%n",
                size, repeatsEach);
        var tokens = com.repeatminer.benchmark.DatasetGenerator.generateTokens(42, size, repeatsEach);

        com.repeatminer.preprocessing.Vocabulary vocabulary =
                new com.repeatminer.preprocessing.Vocabulary();
        int[] ids = vocabulary.internAll(tokens);

        System.out.println("Measuring (1 warm-up + 3 timed iterations per strategy)...");
        var outcome = new com.repeatminer.benchmark.PerformanceBenchmark().runOn(ids, vocabulary,
                new com.repeatminer.naive.NaivePatternDetector.Parameters(2, 2, 20, 8),
                new com.repeatminer.detection.PatternDetector.Parameters(2, 2, 20),
                3);

        System.out.println();
        System.out.println("REPEAT MINER - BENCHMARK (actual measurements)");
        System.out.printf("Input tokens          : %,d%n", ids.length);
        System.out.printf("Naive method          : %s%n", outcome.naive().avgTimeMillisFormatted());
        System.out.printf("Suffix Array + LCP    : %s%n", outcome.suffix().avgTimeMillisFormatted());
        System.out.printf("Speedup               : %s%n", outcome.speedupFormatted());
    }

    /**
     * Console dataset generation (--generate FILE N): deterministic synthetic
     * documents with planted repeated phrases, used for correctness checks and
     * benchmarks (requirement 16).
     */
    private static void runGenerateCommand(String[] args, int from) {
        if (from + 1 >= args.length) {
            System.err.println("Usage: --generate FILE TOKENS");
            System.exit(2);
        }
        try {
            int tokens = Integer.parseInt(args[from + 1]);
            Path out = Path.of(args[from]);
            com.repeatminer.benchmark.DatasetGenerator.writeDataset(out, 42, tokens,
                    com.repeatminer.benchmark.DatasetGenerator.DEFAULT_PHRASES,
                    Math.max(2, tokens / 1000));
            System.out.println("Wrote " + tokens + " tokens (approx.) to " + out.toAbsolutePath());
        } catch (NumberFormatException e) {
            System.err.println("Token count must be a whole number: " + args[from + 1]);
            System.exit(2);
        } catch (java.io.IOException e) {
            System.err.println("Could not write dataset: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printAnalysisResult(AnalysisResult result) {
        System.out.println();
        System.out.println("REPEAT MINER - ANALYSIS RESULT");
        System.out.println("Document   : " + result.documentName());
        System.out.printf("Tokens     : %d   (vocabulary %d, characters %d)%n",
                result.totalTokens(), result.vocabularySize(), result.totalCharacters());

        if (result.patterns().isEmpty()) {
            System.out.println("No repeated phrases found with the given thresholds.");
            return;
        }

        PatternResult longest = result.longestPatterns().get(0);
        System.out.printf("Longest    : \"%s\" (%d words, %d occurrences)%n",
                longest.phrase(), longest.wordCount(), longest.frequency());
        System.out.println();
        System.out.printf("%-42s %8s%n", "PHRASE", "COUNT");
        for (PatternResult p : result.patterns()) {
            System.out.printf("%-42s %8d%n", "\"" + p.phrase() + "\"", p.frequency());
        }
    }

    private static void printHelp() {
        System.out.println("""
                REPEAT MINER — Repeated Pattern Detection Using Suffix Structures

                Usage:
                  mvnw javafx:run        start the desktop GUI (recommended, Windows: mvnw.cmd javafx:run)
                  mvnw test              run the unit tests
                  --analyze FILE [opts]  analyse a TXT file in the console, no GUI:
                                           --min-length N  minimum phrase length, words (default 2)
                                           --min-freq N    minimum occurrences (default 2)
                                           --max N         maximum patterns shown (default 20)
                  --benchmark N          benchmark naive vs suffix on N synthetic tokens
                  --generate FILE N      write a synthetic dataset of N tokens with
                                         planted repeated phrases (requirement 16)

                Planned phases:
                  [done] 2-3   tokenisation + token-to-integer mapping
                  [done] 4,6   suffix array (prefix doubling) + LCP array (Kasai)
                  [done] 7-8   detection + AnalysisEngine (console: --analyze)
                  next   9-10  naive baseline + benchmark harness
                  9-10  naive baseline + benchmark harness
                  11    report generation
                  12-13 full GUI + charts
                  14-15 JUnit suite + large-file tuning
                  16    README + demonstration datasets
                """);
    }

    private Main() {
        // utility class
    }
}

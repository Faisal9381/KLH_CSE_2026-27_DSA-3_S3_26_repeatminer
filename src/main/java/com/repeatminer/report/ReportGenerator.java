package com.repeatminer.report;

import com.repeatminer.model.AnalysisResult;
import com.repeatminer.model.BenchmarkResult;
import com.repeatminer.model.PatternResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Phase 11: exports analysis results as TXT (required) and CSV (optional)
 * (requirement 19). Pure formatting — receives an {@link AnalysisResult}, never
 * touches the algorithm core.
 */
public final class ReportGenerator {

    private static final String LINE = "--------------------------------------------------";

    /** Full TXT report in the master prompt's layout (requirement 19). */
    public String toTextReport(AnalysisResult result) {
        StringBuilder out = new StringBuilder();
        out.append("REPEAT MINER ANALYSIS REPORT\n\n");
        out.append("Document:\n").append(result.documentName()).append("\n\n");
        out.append("Total tokens:\n").append(result.totalTokens()).append("\n");
        out.append("Vocabulary size:\n").append(result.vocabularySize()).append("\n");
        out.append("Characters:\n").append(result.totalCharacters()).append("\n\n");
        out.append("Parameters: min phrase length = ").append(result.minPhraseLength())
                .append(", min frequency = ").append(result.minFrequency()).append("\n\n");

        if (result.longestPatterns().isEmpty()) {
            out.append("Longest repeated phrase:\n(none found with these thresholds)\n\n");
        } else {
            PatternResult longest = result.longestPatterns().get(0);
            out.append("Longest repeated phrase:\n").append(longest.phrase()).append("\n\n");
            out.append("Length:\n").append(longest.wordCount()).append(" words\n\n");
            out.append("Frequency:\n").append(longest.frequency()).append("\n\n");
        }

        out.append("TOP REPEATED PATTERNS\n\n");
        if (result.patterns().isEmpty()) {
            out.append("(no repeated patterns met the thresholds)\n\n");
        } else {
            out.append(String.format("%-4s %-40s %10s %8s%n", "#", "PHRASE", "WORDS", "COUNT"));
            int i = 1;
            for (PatternResult p : result.patterns()) {
                out.append(String.format("%-4d %-40s %10d %8d%n",
                        i++, "\"" + p.phrase() + "\"", p.wordCount(), p.frequency()));
            }
            out.append("\n");
        }

        out.append(LINE).append("\n");
        out.append("Generated ").append(LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n");
        return out.toString();
    }

    /** CSV of the pattern table: phrase,word_count,frequency — RFC-4180 quoting. */
    public String toCsv(AnalysisResult result) {
        StringBuilder out = new StringBuilder("phrase,word_count,frequency\n");
        for (PatternResult p : result.patterns()) {
            out.append(quote(p.phrase())).append(',')
                    .append(p.wordCount()).append(',')
                    .append(p.frequency()).append('\n');
        }
        return out.toString();
    }

    /** Optional performance section for reports that have benchmark data. */
    public String performanceSection(BenchmarkResult naive, BenchmarkResult suffix, double speedup) {
        return "\nPERFORMANCE\n\nNaive       : "
                + naive.avgTimeMillisFormatted()
                + "\nSuffix Array: " + suffix.avgTimeMillisFormatted()
                + String.format("\nSpeedup     : %.2fx%n", speedup);
    }

    /** Writes {@code report.txt} and {@code patterns.csv} into {@code directory}. */
    public List<Path> exportAll(AnalysisResult result, Path directory) throws IOException {
        return exportAll(result, directory, null);
    }

    /**
     * As {@link #exportAll(AnalysisResult, Path)}, with optional extra sections
     * (e.g. the performance section) appended to the TXT report.
     */
    public List<Path> exportAll(AnalysisResult result, Path directory,
                                String extraSections) throws IOException {
        Files.createDirectories(directory);
        Path txt = directory.resolve("report.txt");
        Path csv = directory.resolve("patterns.csv");
        String content = toTextReport(result)
                + (extraSections == null || extraSections.isBlank() ? "" : extraSections);
        Files.writeString(txt, content, StandardCharsets.UTF_8);
        Files.writeString(csv, toCsv(result), StandardCharsets.UTF_8);
        return List.of(txt, csv);
    }

    private String quote(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}

package com.repeatminer.report;

import com.repeatminer.detection.AnalysisEngine;
import com.repeatminer.detection.PatternDetector;
import com.repeatminer.model.AnalysisResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportGeneratorTest {

    private AnalysisResult catResult() {
        return new AnalysisEngine().analyzeText(
                "The cat sat on the mat.\nThe cat likes milk.\nThe cat sleeps.",
                "cats.txt", new PatternDetector.Parameters(1, 2, 50));
    }

    @Test
    @DisplayName("TXT report contains headline, longest phrase and pattern table")
    void textReportLayout() {
        String report = new ReportGenerator().toTextReport(catResult());

        assertTrue(report.contains("REPEAT MINER ANALYSIS REPORT"));
        assertTrue(report.contains("cats.txt"));
        assertTrue(report.contains("Total tokens:"));
        assertTrue(report.contains("the cat"));
        assertTrue(report.contains("TOP REPEATED PATTERNS"));
        assertTrue(report.contains("Longest repeated phrase:"));
    }

    @Test
    @DisplayName("CSV has header and RFC-4180 quoting for commas in phrases")
    void csvQuoting() {
        // Commas can never reach a phrase through tokenization (they are stripped),
        // so the quoting path is tested with a synthetic result directly.
        AnalysisResult synthetic = new AnalysisResult(
                "manual.txt", 100, 10, 5,
                java.util.List.of(new com.repeatminer.model.PatternResult("one, two", 2, 7)),
                java.util.List.of(), 2, 2);

        String csv = new ReportGenerator().toCsv(synthetic);
        String[] lines = csv.split("\n");

        assertEquals("phrase,word_count,frequency", lines[0].trim());
        assertTrue(csv.contains("\"one, two\""), "commas in phrases must be quoted");
    }

    @Test
    @DisplayName("Export writes both files and content round-trips")
    void exportRoundTrip(@TempDir Path tempDir) throws IOException {
        List<Path> written = new ReportGenerator().exportAll(catResult(), tempDir);

        assertEquals(2, written.size());
        Path txt = tempDir.resolve("report.txt");
        Path csv = tempDir.resolve("patterns.csv");
        assertTrue(Files.exists(txt) && Files.exists(csv));

        String report = Files.readString(txt);
        assertTrue(report.contains("REPEAT MINER ANALYSIS REPORT"));
        assertTrue(Files.readString(csv).startsWith("phrase,word_count,frequency"));
    }

    @Test
    @DisplayName("Performance section formats timings and speedup")
    void performanceSection() {
        String section = new ReportGenerator().performanceSection(
                new com.repeatminer.model.BenchmarkResult("Naive", 1000, 1_000_000, 1, 1_000_000, 4000, -1),
                new com.repeatminer.model.BenchmarkResult("Suffix Array + LCP", 1000, 250_000, 1, 250_000, 4000, -1),
                4.0);

        assertTrue(section.contains("PERFORMANCE"));
        assertTrue(section.contains("1.000 ms"));
        assertTrue(section.contains("0.250 ms"));
        assertTrue(section.contains("4.00x"));
    }
}

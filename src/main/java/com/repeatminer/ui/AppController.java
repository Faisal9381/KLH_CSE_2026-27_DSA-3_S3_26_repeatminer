package com.repeatminer.ui;

import com.repeatminer.benchmark.PerformanceBenchmark;
import com.repeatminer.detection.AnalysisEngine;
import com.repeatminer.detection.PatternDetector;
import com.repeatminer.model.AnalysisResult;
import com.repeatminer.naive.NaivePatternDetector;
import com.repeatminer.preprocessing.TextProcessor;
import com.repeatminer.preprocessing.Vocabulary;
import com.repeatminer.report.ReportGenerator;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Phase 12: wires the three views to the analysis core. ALL JavaFX-specific
 * orchestration lives here; the algorithm packages stay UI-free (requirement 25).
 * Analysis and benchmarking run on a background thread; UI updates hop back via
 * {@link Platform#runLater}.
 *
 * <p>Benchmark fairness: the naive detector gets {@code maxPhraseLength = 8}
 * (it must be told when to stop enumerating; the suffix detector needs no such
 * limit because the LCP sweep only visits phrases that actually repeat). On very
 * large documents (over 100k tokens) the benchmark runs a single iteration to
 * keep the UI responsive.
 */
public final class AppController {

    private static final int NAIVE_MAX_PHRASE_LENGTH = 8;
    private static final int LARGE_DOCUMENT_TOKENS = 100_000;

    private final Stage stage;
    private final AnalysisEngine engine = new AnalysisEngine();
    private final ReportGenerator reportGenerator = new ReportGenerator();

    private final MainView mainView;
    private final ResultsView resultsView;
    private final DashboardView dashboardView;

    private TextProcessor.TokenizationResult tokenization;
    private Path selectedFile;
    private AnalysisResult lastResult;
    private PerformanceBenchmark.Outcome lastOutcome;

    public AppController(Stage stage, MainView mainView,
                         ResultsView resultsView, DashboardView dashboardView) {
        this.stage = stage;
        this.mainView = mainView;
        this.resultsView = resultsView;
        this.dashboardView = dashboardView;

        mainView.setCallbacks(new MainView.Callbacks(
                this::onSelectFile, this::onAnalyze, this::onViewReport, this::onExport));
    }

    /** Opens a file chooser, then tokenizes on a background thread to show statistics. */
    public void onSelectFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select a TXT document");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text files", "*.txt", "*.md"));
        File chosen = chooser.showOpenDialog(stage);
        if (chosen == null) {
            return;
        }
        Path file = chosen.toPath();
        mainView.showFileSelected(file);
        mainView.showStatus("Reading document...");

        Thread reader = new Thread(() -> {
            try {
                TextProcessor.TokenizationResult result =
                        new TextProcessor().tokenizeFile(file);
                Platform.runLater(() -> {
                    tokenization = result;
                    selectedFile = file;
                    Vocabulary vocabulary = new Vocabulary();
                    vocabulary.internAll(result.tokens());
                    mainView.showStatistics(result.characterCount(),
                            result.tokens().size(), vocabulary.size());
                    mainView.showStatus("Ready. Set parameters and press ANALYZE.");
                });
            } catch (IOException | TextProcessor.InvalidDocumentException e) {
                Platform.runLater(() -> showError("Could not read document", e.getMessage()));
            }
        }, "repeatminer-file-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** Runs analysis + benchmark on a background thread and populates the views. */
    public void onAnalyze() {
        if (tokenization == null) {
            showError("No document", "Select a TXT file first.");
            return;
        }
        PatternDetector.Parameters params;
        try {
            params = mainView.readParameters();
        } catch (IllegalArgumentException e) {
            showError("Invalid parameters", e.getMessage());
            return;
        }

        mainView.setBusy(true);
        mainView.showStatus("Analyzing... (suffix construction + detection)");

        Thread worker = new Thread(() -> {
            try {
                AnalysisResult result = engine.analyzeTokens(tokenization,
                        selectedFile.getFileName().toString(), params);

                int iterations = tokenization.tokens().size() > LARGE_DOCUMENT_TOKENS ? 1 : 3;
                PerformanceBenchmark.Outcome outcome = runBenchmark(params, iterations);

                Platform.runLater(() -> {
                    lastResult = result;
                    lastOutcome = outcome;
                    resultsView.showResult(result);
                    resultsView.showPerformance(
                            outcome.naive().avgTimeNanos() / 1_000_000.0,
                            outcome.suffix().avgTimeNanos() / 1_000_000.0,
                            outcome.speedup());
                    dashboardView.showTopPhrases(result.patterns(), 8);
                    dashboardView.showPerformance(
                            outcome.naive().avgTimeNanos() / 1_000_000.0,
                            outcome.suffix().avgTimeNanos() / 1_000_000.0);
                    mainView.setBusy(false);
                    mainView.enableResultActions();
                    mainView.showStatus(String.format(
                            "Done: %d patterns (length>=%d, frequency>=%d).",
                            result.patterns().size(),
                            result.minPhraseLength(), result.minFrequency()));
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    mainView.setBusy(false);
                    showError("Analysis failed",
                            e instanceof TextProcessor.InvalidDocumentException
                                    ? e.getMessage()
                                    : "Unexpected problem: " + e.getMessage());
                });
            }
        }, "repeatminer-analyzer");
        worker.setDaemon(true);
        worker.start();
    }

    private PerformanceBenchmark.Outcome runBenchmark(PatternDetector.Parameters params,
                                                      int iterations) {
        Vocabulary vocabulary = new Vocabulary();
        int[] ids = vocabulary.internAll(tokenization.tokens());
        NaivePatternDetector.Parameters naiveParams = new NaivePatternDetector.Parameters(
                Math.min(params.minPhraseLength(), NAIVE_MAX_PHRASE_LENGTH),
                params.minFrequency(),
                params.maxPatterns(),
                NAIVE_MAX_PHRASE_LENGTH);
        return new PerformanceBenchmark().runOn(ids, vocabulary,
                naiveParams, params, iterations);
    }

    /** Shows the TXT report (with performance section) in a scrollable window. */
    public void onViewReport() {
        if (lastResult == null) {
            showError("Nothing to report", "Run an analysis first.");
            return;
        }
        String content = reportGenerator.toTextReport(lastResult)
                + (lastOutcome != null
                ? reportGenerator.performanceSection(lastOutcome.naive(),
                lastOutcome.suffix(), lastOutcome.speedup())
                : "");
        javafx.scene.control.TextArea area = new javafx.scene.control.TextArea(content);
        area.setEditable(false);
        area.setPrefSize(640, 560);
        Stage reportStage = new Stage();
        reportStage.setTitle("Repeat Miner — Report (" + lastResult.documentName() + ")");
        reportStage.setScene(new javafx.scene.Scene(area));
        reportStage.show();
    }

    /** Exports report.txt + patterns.csv into a user-chosen directory. */
    public void onExport() {
        if (lastResult == null) {
            showError("Nothing to export", "Run an analysis first.");
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose export folder");
        File dir = chooser.showDialog(stage);
        if (dir == null) {
            return;
        }
        try {
            String perf = lastOutcome != null
                    ? reportGenerator.performanceSection(lastOutcome.naive(),
                    lastOutcome.suffix(), lastOutcome.speedup())
                    : null;
            List<Path> written = reportGenerator.exportAll(lastResult, dir.toPath(), perf);
            StringBuilder listing = new StringBuilder();
            for (Path p : written) {
                listing.append(p).append('\n');
            }
            showInfo("Export complete", "Wrote:\n" + listing);
        } catch (IOException e) {
            showError("Export failed", "Could not write the report: " + e.getMessage());
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null || message.isBlank() ? "Unknown problem." : message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

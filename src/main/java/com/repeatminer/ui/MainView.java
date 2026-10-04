package com.repeatminer.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.nio.file.Path;

/**
 * Phase 12: the control panel — file selection, document statistics, the two
 * detection parameters, the Analyze button and report actions (requirement 17).
 * Pure view: behaviour is injected via {@link #setCallbacks}; all orchestration
 * lives in {@link AppController}.
 */
public final class MainView {

    /** Actions the controller binds after construction (avoids a circular dependency). */
    public record Callbacks(Runnable onSelectFile, Runnable onAnalyze,
                            Runnable onViewReport, Runnable onExport) {
    }

    private final Button selectButton = new Button("Select TXT File");
    private final Label fileLabel = new Label("No file selected");
    private final Label statsLabel = new Label("Document statistics appear here.");
    private final TextField minLengthField = new TextField("2");
    private final TextField minFreqField = new TextField("2");
    private final TextField maxPatternsField = new TextField("20");
    private final Button analyzeButton = new Button("ANALYZE");
    private final Button viewReportButton = new Button("View Report");
    private final Button exportButton = new Button("Export Results...");
    private final Label statusLabel = new Label("");

    private final VBox root = new VBox(10);

    public MainView() {
        Label title = new Label("REPEAT MINER");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Repeated Pattern Detection Using Suffix Structures");
        subtitle.getStyleClass().add("app-subtitle");

        HBox fileRow = new HBox(10, selectButton, fileLabel);
        fileRow.setAlignment(Pos.CENTER_LEFT);

        HBox paramsRow = new HBox(8,
                new Label("Min phrase length:"), minLengthField,
                new Label("Min frequency:"), minFreqField,
                new Label("Max patterns:"), maxPatternsField);
        paramsRow.setAlignment(Pos.CENTER_LEFT);

        HBox actionRow = new HBox(10, analyzeButton, viewReportButton, exportButton);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        minLengthField.setPrefColumnCount(4);
        minFreqField.setPrefColumnCount(4);
        maxPatternsField.setPrefColumnCount(4);
        statsLabel.setWrapText(true);
        statusLabel.setWrapText(true);
        statsLabel.getStyleClass().add("stat-line");
        statusLabel.getStyleClass().add("status-label");
        analyzeButton.getStyleClass().add("button-primary");

        root.getChildren().addAll(title, subtitle, fileRow, statsLabel, paramsRow, actionRow, statusLabel);
        root.setPadding(new Insets(14));
        root.getStyleClass().add("control-panel");
        fileLabel.getStyleClass().add("muted-label");
    }

    /** Wires button actions; called once by the controller after construction. */
    public void setCallbacks(Callbacks callbacks) {
        selectButton.setOnAction(e -> callbacks.onSelectFile().run());
        analyzeButton.setOnAction(e -> callbacks.onAnalyze().run());
        viewReportButton.setOnAction(e -> callbacks.onViewReport().run());
        exportButton.setOnAction(e -> callbacks.onExport().run());
        exportButton.setDisable(true);
        viewReportButton.setDisable(true);
    }

    public Node getNode() {
        return root;
    }

    public void showFileSelected(Path file) {
        fileLabel.setText(file.getFileName().toString());
        analyzeButton.setDisable(false);
    }

    public void showStatistics(long characters, int tokens, int vocabulary) {
        statsLabel.setText(String.format(
                "Words: %d    Characters: %d    Vocabulary: %d distinct words",
                tokens, characters, vocabulary));
    }

    /** Parses and validates the parameter fields; throws IllegalArgumentException with a friendly message. */
    public com.repeatminer.detection.PatternDetector.Parameters readParameters() {
        try {
            return new com.repeatminer.detection.PatternDetector.Parameters(
                    Integer.parseInt(minLengthField.getText().trim()),
                    Integer.parseInt(minFreqField.getText().trim()),
                    Integer.parseInt(maxPatternsField.getText().trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Phrase length, frequency and max patterns must be whole numbers.");
        }
    }

    private boolean hasResult = false;

    public void setBusy(boolean busy) {
        analyzeButton.setDisable(busy || fileLabel.getText().startsWith("No file"));
        selectButton.setDisable(busy);
        viewReportButton.setDisable(busy || !hasResult);
        exportButton.setDisable(busy || !hasResult);
    }

    public void enableResultActions() {
        hasResult = true;
        viewReportButton.setDisable(false);
        exportButton.setDisable(false);
    }

    public void showStatus(String message) {
        statusLabel.setText(message);
    }
}

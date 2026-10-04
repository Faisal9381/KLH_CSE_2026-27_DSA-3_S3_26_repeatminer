package com.repeatminer.ui;

import com.repeatminer.model.AnalysisResult;
import com.repeatminer.model.PatternResult;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Phase 12: results display — longest repeated phrase panel, the ranked table
 * of repeated phrases, and the performance comparison (requirements 12, 13, 17).
 *
 * <p>Note: {@code javafx.scene.control.cell.PropertyValueFactory} relies on
 * JavaBean {@code getX()} accessors, which records do not have, so the columns
 * use lambda cell-value factories instead.
 */
public final class ResultsView {

    private final Label longestLabel = new Label("Select a document and run an analysis.");
    private final TableView<PatternResult> table = new TableView<>();
    private final Label naiveTimeLabel = new Label("Naive method: n/a");
    private final Label suffixTimeLabel = new Label("Suffix Array: n/a");
    private final Label speedupLabel = new Label("Speedup: n/a");

    private final VBox root = new VBox(8);

    public ResultsView() {
        Label header = new Label("RESULTS");
        header.getStyleClass().add("section-title");

        longestLabel.setWrapText(true);
        longestLabel.getStyleClass().add("longest-phrase");

        TableColumn<PatternResult, String> phraseCol = new TableColumn<>("Phrase");
        phraseCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().phrase()));
        phraseCol.setPrefWidth(320);

        TableColumn<PatternResult, Number> lengthCol = new TableColumn<>("Words");
        lengthCol.setCellValueFactory(data -> new ReadOnlyIntegerWrapper(data.getValue().wordCount()));
        lengthCol.setPrefWidth(80);

        TableColumn<PatternResult, Number> freqCol = new TableColumn<>("Frequency");
        freqCol.setCellValueFactory(data -> new ReadOnlyIntegerWrapper(data.getValue().frequency()));
        freqCol.setPrefWidth(100);

        // Sortable columns per requirement 13: clicking headers sorts by that column.
        table.getColumns().setAll(phraseCol, lengthCol, freqCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No repeated phrases found with the given thresholds."));
        VBox.setVgrow(table, Priority.ALWAYS);

        naiveTimeLabel.getStyleClass().add("perf-label");
        suffixTimeLabel.getStyleClass().add("perf-label");
        speedupLabel.getStyleClass().add("perf-accent");

        HBox performanceRow = new HBox(24, naiveTimeLabel, suffixTimeLabel, speedupLabel);
        performanceRow.setPadding(new Insets(6, 0, 2, 0));

        root.getChildren().addAll(header, new Label("Longest Repeated Phrase:"), longestLabel,
                new Label("Top Repeated Phrases:"), table, performanceRow);
        root.setPadding(new Insets(14));
    }

    public Node getNode() {
        return root;
    }

    public void showResult(AnalysisResult result) {
        if (result.longestPatterns().isEmpty()) {
            longestLabel.setText("No repeated phrases met the thresholds.");
        } else {
            PatternResult longest = result.longestPatterns().get(0);
            longestLabel.setText(String.format("\"%s\"  —  %d words, %d occurrences",
                    longest.phrase(), longest.wordCount(), longest.frequency()));
        }
        table.getItems().setAll(result.patterns());
    }

    public void showPerformance(double naiveMillis, double suffixMillis, double speedup) {
        naiveTimeLabel.setText(String.format("Naive method: %.3f ms", naiveMillis));
        suffixTimeLabel.setText(String.format("Suffix Array: %.3f ms", suffixMillis));
        speedupLabel.setText(String.format("Speedup: %.2fx", speedup));
    }
}

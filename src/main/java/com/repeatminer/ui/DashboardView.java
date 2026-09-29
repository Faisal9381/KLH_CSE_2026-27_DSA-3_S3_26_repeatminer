package com.repeatminer.ui;

import com.repeatminer.model.PatternResult;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Phase 13: charts (requirement 18) — top repeated phrases bar chart and the
 * naive-vs-suffix execution time comparison. Deliberately no suffix-structure
 * drawing; the visualisations support the performance story only.
 */
public final class DashboardView {

    private final BarChart<String, Number> phraseChart;
    private final BarChart<String, Number> performanceChart;

    private final VBox root = new VBox(10);

    public DashboardView() {
        phraseChart = new BarChart<>(new CategoryAxis(), new NumberAxis());
        phraseChart.setTitle("Top Repeated Phrases (frequency)");
        phraseChart.setLegendVisible(false);
        phraseChart.setAnimated(false);

        performanceChart = new BarChart<>(new CategoryAxis(), new NumberAxis());
        performanceChart.setTitle("Detection Time by Strategy (ms, mean of runs)");
        performanceChart.setLegendVisible(false);
        performanceChart.setAnimated(false);

        root.getChildren().addAll(phraseChart, performanceChart);
    }

    public Node getNode() {
        return root;
    }

    /** Shows up to {@code limit} phrases as bars, longest bar = highest frequency. */
    public void showTopPhrases(List<PatternResult> patterns, int limit) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        int shown = 0;
        for (PatternResult p : patterns) {
            if (shown >= limit) {
                break;
            }
            String label = p.phrase();
            if (label.length() > 28) {
                label = label.substring(0, 25) + "...";
            }
            XYChart.Data<String, Number> data = new XYChart.Data<>(label, p.frequency());
            series.getData().add(data);
            shown++;
        }
        phraseChart.getData().setAll(series);
    }

    public void showPerformance(double naiveMillis, double suffixMillis) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("Naive", naiveMillis));
        series.getData().add(new XYChart.Data<>("Suffix Array + LCP", suffixMillis));
        performanceChart.getData().setAll(series);
    }

    public void clear() {
        phraseChart.getData().clear();
        performanceChart.getData().clear();
    }
}

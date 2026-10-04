package com.repeatminer.ui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * JavaFX entry point of Repeat Miner (Phases 12-13): assembles MainView,
 * ResultsView and DashboardView, delegates all behaviour to AppController, and
 * contains no algorithm logic itself (requirement 25).
 */
public class RepeatMinerApp extends Application {

    /** Design-only: shared dark stylesheet, attached to every scene and dialog. */
    public static final String STYLESHEET =
            RepeatMinerApp.class.getResource("/css/dark-theme.css").toExternalForm();

    @Override
    public void start(Stage stage) {
        DiagnosticProbe.startIfEnabled(); // no-op unless REPEATMINER_PROBE_PORT is set

        MainView mainView = new MainView();
        ResultsView resultsView = new ResultsView();
        DashboardView dashboardView = new DashboardView();
        new AppController(stage, mainView, resultsView, dashboardView);

        TabPane tabs = new TabPane();
        Tab resultsTab = new Tab("Results", resultsView.getNode());
        Tab chartsTab = new Tab("Charts", dashboardView.getNode());
        resultsTab.setClosable(false);
        chartsTab.setClosable(false);
        tabs.getTabs().addAll(resultsTab, chartsTab);

        BorderPane root = new BorderPane();
        root.setTop(mainView.getNode());
        root.setCenter(tabs);
        root.getStyleClass().add("app-root");
        VBox.setMargin(root, new Insets(4));

        stage.setTitle("Repeat Miner");
        stage.setMinWidth(860);
        stage.setMinHeight(620);
        Scene scene = new Scene(root, 980, 680);
        scene.getStylesheets().add(STYLESHEET);
        stage.setScene(scene);
        stage.show();
    }

    /** Shows the window from a plain (non-JavaFX) {@code main}; see {@code com.repeatminer.Main}. */
    public static void show() {
        new RepeatMinerApp().start(new Stage());
    }
}

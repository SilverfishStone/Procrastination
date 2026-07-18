package com.workgame.ui;

import com.workgame.model.GameStats;
import com.workgame.model.StatsHistory;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.*;

public class StatsScreen {

    private final Stage stage;
    private final Scene returnScene;

    public StatsScreen(Stage stage, Scene returnScene) {
        this.stage       = stage;
        this.returnScene = returnScene;
    }

    public Scene buildScene() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + SetupScreen.themeBackground() + ";");
        root.setPadding(new Insets(20));

        // ── Header ────────────────────────────────────────────────────────────
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 16, 0));

        Label title = new Label("GAME HISTORY");
        title.getStyleClass().add("setup-title");
        title.setStyle("-fx-font-size: 24px; -fx-text-fill: #f5d060; -fx-font-weight: bold;");

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button backBtn = new Button("← BACK");
        backBtn.getStyleClass().addAll("btn", "btn-skip");
        backBtn.setOnAction(e -> stage.setScene(returnScene));

        header.getChildren().addAll(title, spacer, backBtn);
        root.setTop(header);

        if (StatsHistory.isEmpty()) {
            Label empty = new Label("No games played yet.");
            empty.setStyle("-fx-text-fill: #607060; -fx-font-size: 14px;");
            root.setCenter(empty);
            BorderPane.setAlignment(empty, Pos.CENTER);
        } else {
            SplitPane split = new SplitPane();
            split.setDividerPositions(0.35);

            // Left: game list
            ListView<GameStats> list = new ListView<>();
            list.getItems().addAll(StatsHistory.getAll());
            list.setCellFactory(lv -> new ListCell<>() {
                @Override protected void updateItem(GameStats item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) { setText(null); setStyle(""); return; }
                    setText(item.getSummaryLine());
                    setStyle(
                            "-fx-background-color: rgba(10,25,12,0.85);" +
                                    "-fx-text-fill: #b0d090;" +
                                    "-fx-font-size: 11px;" +
                                    "-fx-padding: 6 10 6 10;");
                }
            });
            list.setStyle("-fx-background-color: rgba(5,15,8,0.90); -fx-control-inner-background: transparent;");

            // Right: detail pane
            ScrollPane detailScroll = new ScrollPane();
            detailScroll.setFitToWidth(true);
            detailScroll.setStyle(
                    "-fx-background-color: #0d1a0f; -fx-background: #0d1a0f;");
            VBox detailBox = new VBox(12);
            detailBox.setPadding(new Insets(14));
            detailBox.setStyle("-fx-background-color: #0d1a0f;");
            detailScroll.setContent(detailBox);

            list.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
                if (n != null) buildDetail(detailBox, n);
            });
            list.getSelectionModel().selectFirst();

            split.getItems().addAll(list, detailScroll);
            root.setCenter(split);
        }

        Scene scene = new Scene(root, 1000, 680);
        SetupScreen.applyStylesheet(scene);
        return scene;
    }

    private void buildDetail(VBox box, GameStats s) {
        box.getChildren().clear();
        box.setStyle("-fx-background-color: #0d1a0f;");

        String accent  = SetupScreen.themeAccent();
        String textCol = "#d8f0c0";   // bright light green — readable on dark
        String dimCol  = "#90b080";   // medium green — readable on dark
        String goldCol = "#f5d060";
        String gainCol = "#60e860";   // bright green
        String lossCol = "#ff7060";   // bright red

        // ── Game summary ──────────────────────────────────────────────────────
        Label sumTitle = sectionLabel("GAME SUMMARY", accent);
        GridPane sumGrid = grid();
        addRow(sumGrid, 0, "Date / Time",   s.getTimestamp(),    textCol);
        addRow(sumGrid, 1, "Winner",        s.getWinnerName() + " (" + s.getWinnerHours() + "h)", goldCol);
        addRow(sumGrid, 2, "Rounds played", String.valueOf(s.getRoundsPlayed()), textCol);

        // ── Per-player results ────────────────────────────────────────────────
        Label plTitle = sectionLabel("PLAYER RESULTS", accent);
        GridPane plGrid = grid();
        plGrid.add(colHeader("Player"),   0, 0);
        plGrid.add(colHeader("Final h"),  1, 0);
        plGrid.add(colHeader("Gained"),   2, 0);
        plGrid.add(colHeader("Lost"),     3, 0);
        plGrid.add(colHeader("Played"),   4, 0);
        plGrid.add(colHeader("Weapons"),  5, 0);
        plGrid.add(colHeader("Helpers"),  6, 0);

        List<GameStats.PlayerStat> pStats = new ArrayList<>(s.getPlayerStats());
        pStats.sort(Comparator.comparingInt(p -> p.rank));
        for (int i = 0; i < pStats.size(); i++) {
            GameStats.PlayerStat p = pStats.get(i);
            String nameStr = "#" + p.rank + " " + p.name + (p.isCpu ? " [CPU]" : "");
            String rowCol  = p.rank == 1 ? goldCol : textCol;
            plGrid.add(cell(nameStr, rowCol),                          0, i + 1);
            plGrid.add(cell(p.finalHours + "h",   rowCol),            1, i + 1);
            plGrid.add(cell("+" + p.hoursGained + "h", gainCol),      2, i + 1);
            plGrid.add(cell("-" + p.hoursLost + "h",   lossCol),      3, i + 1);
            plGrid.add(cell(String.valueOf(p.cardsPlayed),    dimCol), 4, i + 1);
            plGrid.add(cell(String.valueOf(p.weaponsPlayed),  dimCol), 5, i + 1);
            plGrid.add(cell(String.valueOf(p.helpersUsed),    dimCol), 6, i + 1);
        }

        // ── Economy ───────────────────────────────────────────────────────────
        Label ecoTitle = sectionLabel("HOUR ECONOMY", accent);
        GridPane ecoGrid = grid();
        addRow(ecoGrid, 0, "Total hours gained",          "+" + s.getTotalHoursGained() + "h",     gainCol);
        addRow(ecoGrid, 1, "Total hours lost",            "-" + s.getTotalHoursLost() + "h",        lossCol);
        addRow(ecoGrid, 2, "Hours transferred (weapons)", s.getTotalHoursTransferred() + "h",       textCol);

        // ── Card activity ─────────────────────────────────────────────────────
        Label cardTitle = sectionLabel("CARD ACTIVITY", accent);
        GridPane cardGrid = grid();
        addRow(cardGrid, 0, "Cards drawn",       String.valueOf(s.getCardsDrawn()),       textCol);
        addRow(cardGrid, 1, "Play cards played", String.valueOf(s.getPlayCardsPlayed()),  textCol);
        addRow(cardGrid, 2, "Weapons played",    String.valueOf(s.getWeaponsPlayed()),    textCol);
        addRow(cardGrid, 3, "Helpers used",      String.valueOf(s.getHelpersUsed()),      textCol);
        addRow(cardGrid, 4, "Alerts triggered",  String.valueOf(s.getAlertsTriggered()),  lossCol);
        addRow(cardGrid, 5, "Cards discarded",   String.valueOf(s.getCardsDiscarded()),   dimCol);
        addRow(cardGrid, 6, "Cards expired",     String.valueOf(s.getCardsExpired()),     lossCol);

        // ── Most played cards ─────────────────────────────────────────────────
        Label popTitle = sectionLabel("MOST PLAYED CARDS", accent);
        VBox popBox = new VBox(3);
        popBox.setStyle("-fx-background-color: #0a1a0c; -fx-padding: 8; -fx-background-radius: 4;");
        s.getCardPlayCounts().entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .forEach(entry -> {
                    HBox row = new HBox(10);
                    Label name = cell(entry.getKey(), textCol);
                    name.setMinWidth(160);
                    Label count = cell("×" + entry.getValue(), goldCol);
                    row.getChildren().addAll(name, count);
                    popBox.getChildren().add(row);
                });

        box.getChildren().addAll(
                sumTitle, sumGrid,
                plTitle, plGrid,
                ecoTitle, ecoGrid,
                cardTitle, cardGrid,
                popTitle, popBox
        );
    }

    // ── Styling helpers ───────────────────────────────────────────────────────

    private Label sectionLabel(String text, String accent) {
        Label lbl = new Label(text);
        lbl.setStyle(
                "-fx-font-size: 12px; -fx-font-weight: bold;" +
                        "-fx-text-fill: " + accent + ";" +
                        "-fx-padding: 12 0 4 0;");
        return lbl;
    }

    private GridPane grid() {
        GridPane g = new GridPane();
        g.setHgap(20); g.setVgap(5);
        g.setStyle("-fx-background-color: #0a1a0c; -fx-padding: 10; -fx-background-radius: 4;");
        return g;
    }

    private void addRow(GridPane g, int row, String key, String val, String valColor) {
        g.add(cell(key, "#90b880"), 0, row);   // key: readable medium green
        g.add(cell(val, valColor),  1, row);
    }

    private Label cell(String text, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 12px; -fx-text-fill: " + color + ";");
        return l;
    }

    private Label colHeader(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #70a860;");
        return l;
    }

}
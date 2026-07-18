package com.workgame.ui;

import com.workgame.model.Player;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class SetupScreen {

    private final Stage stage;
    private final Runnable onBack;

    private final VBox   playerRowBox = new VBox(8);
    private final List<TextField>         nameFields  = new ArrayList<>();
    private final List<ChoiceBox<String>> typeChoices = new ArrayList<>();

    private int    playerCount = 2;
    private int    maxRounds   = 20;
    private String difficulty  = "Medium";
    private String theme       = "Felt";

    // Only Human vs CPU (no hardness per player)
    private static final String[] PLAYER_TYPE_OPTIONS = {
            "Human (Hot Seat)", "CPU"
    };

    private static final String[] DIFFICULTIES = {
            "Easy", "Medium", "Hard"
    };

    public static final String[] THEMES = {
            "Felt", "Midnight", "Corporate", "Sunset", "Ocean"
    };

    // Current theme used by both screens
    static String  currentTheme    = "Felt";
    static boolean autoAdvanceCpu  = true;  // if false, show Next Turn button during CPU turns

    public SetupScreen(Stage stage, Runnable onBack) {
        this.stage  = stage;
        this.onBack = onBack;
    }

    public Scene buildScene() {
        StackPane root = new StackPane();
        applyThemeBackground(root);

        ScrollPane scroll = new ScrollPane();
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox card = new VBox(18);
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(540);
        card.setPadding(new Insets(40, 48, 40, 48));
        applyCardStyle(card);

        // ── Title ──────────────────────────────────────────────────────────────
        Label title    = new Label("WORK GAME");
        title.getStyleClass().add("setup-title");
        Label subtitle = new Label("a corporate card game of hours and despair");
        subtitle.getStyleClass().add("setup-subtitle");
        Separator sep1 = new Separator(); sep1.setMaxWidth(380);

        // ── Player count ───────────────────────────────────────────────────────
        Label countLbl = new Label("NUMBER OF PLAYERS");
        countLbl.getStyleClass().add("section-label");
        Spinner<Integer> countSpinner = new Spinner<>(2, 6, 2);
        countSpinner.getStyleClass().add("setup-spinner");
        countSpinner.setMaxWidth(100);
        countSpinner.valueProperty().addListener((obs, o, n) -> { playerCount = n; rebuildPlayerRows(); });
        VBox countBox = new VBox(4, countLbl, countSpinner);
        countBox.setAlignment(Pos.CENTER);

        // ── Player rows ────────────────────────────────────────────────────────
        Label rowsLbl = new Label("PLAYERS");
        rowsLbl.getStyleClass().add("section-label");
        playerRowBox.setAlignment(Pos.CENTER);
        rebuildPlayerRows();
        Separator sep2 = new Separator(); sep2.setMaxWidth(380);

        // ── Settings ───────────────────────────────────────────────────────────
        Label settingsLbl = new Label("SETTINGS");
        settingsLbl.getStyleClass().add("section-label");

        // Rounds
        Label roundsLbl = new Label("Max Rounds");
        roundsLbl.getStyleClass().add("section-label");
        roundsLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #8ab870;");
        Spinner<Integer> roundsSpinner = new Spinner<>(5, 100, 20, 5);
        roundsSpinner.getStyleClass().add("setup-spinner");
        roundsSpinner.setMaxWidth(110);
        roundsSpinner.valueProperty().addListener((obs, o, n) -> maxRounds = n);
        HBox roundsRow = new HBox(10, roundsLbl, roundsSpinner);
        roundsRow.setAlignment(Pos.CENTER_LEFT);

        // CPU Difficulty (global)
        Label diffLbl = new Label("CPU Difficulty");
        diffLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #8ab870;");
        ChoiceBox<String> diffBox = new ChoiceBox<>();
        diffBox.getItems().addAll(DIFFICULTIES);
        diffBox.setValue(difficulty);
        styleChoiceBox(diffBox);
        diffBox.valueProperty().addListener((obs, o, n) -> difficulty = n);
        HBox diffRow = new HBox(10, diffLbl, diffBox);
        diffRow.setAlignment(Pos.CENTER_LEFT);

        // Theme
        Label themeLbl = new Label("Table Theme");
        themeLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #8ab870;");
        ChoiceBox<String> themeBox = new ChoiceBox<>();
        themeBox.getItems().addAll(THEMES);
        themeBox.setValue(theme);
        styleChoiceBox(themeBox);
        themeBox.valueProperty().addListener((obs, o, n) -> {
            theme = n;
            currentTheme = n;
            applyThemeBackground(root);
            applyCardStyle(card);
            // Re-apply stylesheet
            Scene s = root.getScene();
            if (s != null) {
                s.getStylesheets().clear();
                applyStylesheet(s);
            }
        });
        HBox themeRow = new HBox(10, themeLbl, themeBox);
        themeRow.setAlignment(Pos.CENTER_LEFT);

        // Auto-advance CPU turns
        Label autoLbl = new Label("Auto-advance CPU turns");
        autoLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #8ab870;");
        CheckBox autoBox = new CheckBox();
        autoBox.setSelected(SetupScreen.autoAdvanceCpu);
        autoBox.setStyle("-fx-text-fill: #8ab870;");
        autoBox.selectedProperty().addListener((obs, o, n) -> SetupScreen.autoAdvanceCpu = n);
        HBox autoRow = new HBox(10, autoLbl, autoBox);
        autoRow.setAlignment(Pos.CENTER_LEFT);

        VBox settingsBox = new VBox(10, roundsRow, diffRow, themeRow, autoRow);
        Separator sep3 = new Separator(); sep3.setMaxWidth(380);

        // ── Start ──────────────────────────────────────────────────────────────
        Button startBtn = new Button("▶   START GAME");
        startBtn.getStyleClass().addAll("btn", "btn-primary");
        startBtn.setMaxWidth(Double.MAX_VALUE);
        startBtn.setOnAction(e -> startGame());

        Button statsBtn = new Button("📊  GAME HISTORY");
        statsBtn.getStyleClass().addAll("btn");
        statsBtn.setMaxWidth(Double.MAX_VALUE);
        statsBtn.setDisable(com.workgame.model.StatsHistory.isEmpty());
        statsBtn.setOnAction(e -> {
            Scene current = stage.getScene();
            stage.setScene(new StatsScreen(stage, current).buildScene());
        });

        card.getChildren().addAll(
                title, subtitle, sep1,
                countBox,
                rowsLbl, playerRowBox,
                sep2,
                settingsLbl, settingsBox,
                sep3,
                startBtn, statsBtn
        );

        scroll.setContent(card);
        StackPane.setAlignment(card, Pos.CENTER);
        root.getChildren().add(scroll);

        Scene scene = new Scene(root, 700, 680);
        applyStylesheet(scene);
        return scene;
    }

    // ── Player rows ───────────────────────────────────────────────────────────

    private void rebuildPlayerRows() {
        nameFields.clear(); typeChoices.clear();
        playerRowBox.getChildren().clear();

        for (int i = 0; i < playerCount; i++) {
            final int idx = i;

            TextField nameField = new TextField("Player " + (i + 1));
            nameField.getStyleClass().add("setup-field");
            nameField.setPrefWidth(170);

            ChoiceBox<String> typeBox = new ChoiceBox<>();
            typeBox.getItems().addAll(PLAYER_TYPE_OPTIONS);
            String defaultType = (i == 0) ? PLAYER_TYPE_OPTIONS[0] : "CPU";
            typeBox.setValue(defaultType);
            styleChoiceBox(typeBox);

            // Auto-set name for CPU defaults
            if (i > 0) nameField.setText("CPU " + (i + 1));

            typeBox.valueProperty().addListener((obs, o, n) -> {
                boolean isCpu = n.equals("CPU");
                if (isCpu && nameField.getText().startsWith("Player "))
                    nameField.setText("CPU " + (idx + 1));
                else if (!isCpu && nameField.getText().startsWith("CPU"))
                    nameField.setText("Player " + (idx + 1));
            });

            Label numLbl = new Label((i + 1) + ".");
            numLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #6a9060; -fx-min-width: 20px;");

            HBox row = new HBox(10, numLbl, nameField, typeBox);
            row.setAlignment(Pos.CENTER_LEFT);
            nameFields.add(nameField);
            typeChoices.add(typeBox);
            playerRowBox.getChildren().add(row);
        }
    }

    // ── Game start ────────────────────────────────────────────────────────────

    private void startGame() {
        List<String>            names = new ArrayList<>();
        List<Player.PlayerType> types = new ArrayList<>();

        for (int i = 0; i < playerCount; i++) {
            String n = nameFields.get(i).getText().trim();
            if (n.isEmpty()) n = "Player " + (i + 1);
            names.add(n);
            // All CPUs get the global difficulty
            boolean isCpu = typeChoices.get(i).getValue().equals("CPU");
            types.add(isCpu ? parseCpuType(difficulty) : Player.PlayerType.HUMAN);
        }

        currentTheme = theme;
        GameScreen gs = new GameScreen(stage, names, types, maxRounds, theme);
        stage.setScene(gs.buildScene());
        stage.setWidth(1200);
        stage.setHeight(780);
        stage.centerOnScreen();
    }

    private Player.PlayerType parseCpuType(String diff) {
        return switch (diff) {
            case "Easy" -> Player.PlayerType.CPU_EASY;
            case "Hard" -> Player.PlayerType.CPU_HARD;
            default     -> Player.PlayerType.CPU_MEDIUM;
        };
    }

    // ── Theme styling ─────────────────────────────────────────────────────────

    static void applyThemeBackground(StackPane root) {
        root.setStyle("-fx-background-color: " + themeBackground() + ";");
    }

    static String themeBackground() {
        return switch (currentTheme) {
            case "Midnight"  -> "radial-gradient(center 50% 50%, radius 80%, #1a1a3a 0%, #08080f 100%)";
            case "Corporate" -> "radial-gradient(center 50% 50%, radius 80%, #2a2a2a 0%, #111111 100%)";
            case "Sunset"    -> "radial-gradient(center 50% 50%, radius 80%, #4a2a1a 0%, #200a08 100%)";
            case "Ocean"     -> "radial-gradient(center 50% 50%, radius 80%, #1a2a4a 0%, #080f20 100%)";
            default          -> "radial-gradient(center 50% 50%, radius 80%, #1a4a2a 0%, #0a2010 100%)";
        };
    }

    static String themeAccent() {
        return switch (currentTheme) {
            case "Midnight"  -> "#6060d0";
            case "Corporate" -> "#909090";
            case "Sunset"    -> "#d06040";
            case "Ocean"     -> "#4080d0";
            default          -> "#5a9030";
        };
    }

    static String themeText() {
        return switch (currentTheme) {
            case "Midnight"  -> "#c0c0f0";
            case "Corporate" -> "#e0e0e0";
            case "Sunset"    -> "#f0c090";
            case "Ocean"     -> "#90c0f0";
            default          -> "#c0e0a0";
        };
    }

    private void applyCardStyle(VBox card) {
        String accent = themeAccent();
        card.setStyle(
                "-fx-background-color: rgba(8,10,20,0.92);" +
                        "-fx-background-radius: 16px;" +
                        "-fx-border-color: " + accent + "66;" +
                        "-fx-border-width: 1.5px;" +
                        "-fx-border-radius: 16px;" +
                        "-fx-effect: dropshadow(gaussian, #000000cc, 30, 0.6, 0, 8);");
    }

    private void styleChoiceBox(ChoiceBox<String> cb) {
        String accent = themeAccent();
        cb.setStyle(
                "-fx-background-color: rgba(10,12,22,0.90);" +
                        "-fx-border-color: " + accent + "88;" +
                        "-fx-border-width: 1px;" +
                        "-fx-font-family: 'JetBrains Mono','Courier New',monospace;" +
                        "-fx-font-size: 11px;" +
                        "-fx-text-fill: " + themeText() + ";");
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    static void applyStylesheet(Scene scene) {
        var url = SetupScreen.class.getResource("/com/workgame/css/" + cssFile() + ".css");
        if (url == null) url = SetupScreen.class.getResource("/com/workgame/css/game.css");
        if (url != null) scene.getStylesheets().add(url.toExternalForm());
    }

    static String cssFile() {
        return switch (currentTheme) {
            case "Midnight"  -> "theme-midnight";
            case "Corporate" -> "theme-corporate";
            case "Sunset"    -> "theme-sunset";
            case "Ocean"     -> "theme-ocean";
            default          -> "game";
        };
    }
}
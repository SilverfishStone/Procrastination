package com.workgame.ui;

import com.workgame.controller.GameController;
import com.workgame.controller.CpuPlayer;
import com.workgame.model.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.*;

/**
 * Top-down table view with four animation types:
 *
 *   1. DRAW      – face-down ghost slides from deck to hand, flips mid-flight.
 *   2. PLAY      – face-up ghost slides from hand to current player's play zone.
 *   3. WEAPON    – ghost slides from hand to opponent zone, flips face-down mid-flight.
 *   4. ALERT     – card zooms to screen center, colored flash overlay pulses, shrinks away.
 *
 * All animations use a transparent AnchorPane "glass pane" layered above the scene.
 * State is already mutated before the animation; the UI just suppresses updateUI()
 * until the animation's onDone callback fires.
 */
public class GameScreen {

    private static final int WINDOW_W = 1200;
    private static final int WINDOW_H = 780;

    // Card size constants used for ghost sizing
    private static final double GHOST_W = 100;
    private static final double GHOST_H = 130;

    private final Stage                   stage;
    private final List<String>            playerNames;
    private final List<Player.PlayerType> playerTypes;
    private final int                     maxRounds;
    private final String                  theme;
    private GameState                     state;
    private GameController                controller;
    private CpuPlayer                     cpuPlayer;

    // ── Persistent UI nodes ───────────────────────────────────────────────────
    private StackPane  sceneRoot;
    private AnchorPane glassPane;
    private StackPane  tableRoot;
    private VBox       opponentArea;
    private HBox       myPlayedZone;
    private HBox       handTray;
    private HBox       actionBar;
    private TextArea   logArea;
    private Label      roundLabel, turnLabel, poolLabel, myHoursLabel;
    private VBox       deckNode;

    // We keep a map of player → their zone VBox so we can read scene bounds for weapon targeting
    private final Map<Player, VBox> opponentZoneNodes = new LinkedHashMap<>();

    private CardView selectedCard      = null;
    private String   selectedCardName  = null; // persists across hand rebuilds
    private boolean  cpuTurnInProgress = false;
    private boolean  handRevealed      = false; // true once human has seen "pass device" screen
    private boolean  nextTurnRequested = false; // set by Next Turn button in manual mode

    // ── Constructor ───────────────────────────────────────────────────────────
    public GameScreen(Stage stage, List<String> playerNames,
                      List<Player.PlayerType> playerTypes, int maxRounds, String theme) {
        this.stage       = stage;
        this.playerNames = playerNames;
        this.playerTypes = playerTypes;
        this.maxRounds   = maxRounds;
        this.theme       = theme;
        SetupScreen.currentTheme = theme;
    }

    // ── Scene build ───────────────────────────────────────────────────────────
    public Scene buildScene() {
        state = new GameState(playerNames, playerTypes);
        state.setMaxRounds(maxRounds);
        controller = new GameController(state);
        cpuPlayer  = new CpuPlayer(controller, state);
        controller.setCpuPlayer(cpuPlayer);

        controller.setUpdateUICallback(this::updateUI);
        controller.setLogCallback(this::appendLog);
        // Route target requests: CPU handles its own; humans get dialogs
        controller.setTargetRequestCallback(req -> {
            if (!state.getCurrentPlayer().isHuman())
                cpuPlayer.handleTargetRequest(req);
            else
                handleTargetRequest(req);
        });
        controller.setDrawCallback(this::onCardDrawn);
        controller.setPlayCardCallback((card, player) -> onCardPlayed(card, player));
        controller.setWeaponSentCallback((card, target) -> onWeaponSent(card, target));
        controller.setAlertCallback((card, resolve) -> onAlertDrawn(card, resolve));
        controller.setBulkDiscardCallback((cards, onDone) -> onBulkDiscard(cards, onDone));
        controller.setDefendCallback((weapon, resolveAnyway) -> onDefendOpportunity(weapon, resolveAnyway));

        BorderPane inner = new BorderPane();
        inner.setTop(buildHud());
        inner.setCenter(buildTableAndLog());
        inner.setBottom(buildBottomSection());

        glassPane = new AnchorPane();
        glassPane.setMouseTransparent(true);
        glassPane.setPickOnBounds(false);

        sceneRoot = new StackPane(inner, glassPane);
        sceneRoot.setStyle("-fx-background-color: " + SetupScreen.themeBackground() + ";");
        controller.startGame();

        Scene scene = new Scene(sceneRoot, WINDOW_W, WINDOW_H);
        SetupScreen.applyStylesheet(scene);
        return scene;
    }

    // ── CPU turn trigger ──────────────────────────────────────────────────────

    /** If the current player is a CPU, schedule their turn (or wait for button if manual mode). */
    private void maybeTriggerCpuTurn() {
        Player p = state.getCurrentPlayer();
        if (state.getPhase() != GameState.Phase.PLAYER_TURN || p.isHuman()) return;
        if (cpuTurnInProgress) {
            System.out.println("[CPU] maybeTriggerCpuTurn: already in progress, skipping");
            return;
        }
        if (!SetupScreen.autoAdvanceCpu && !nextTurnRequested) {
            System.out.println("[CPU] maybeTriggerCpuTurn: manual mode, waiting for Next Turn button");
            return;
        }

        nextTurnRequested = false;
        cpuTurnInProgress = true;
        // In manual mode we still run the turn immediately once triggered — no delay needed
        long delayMs = SetupScreen.autoAdvanceCpu ? 900 : 50;

        System.out.println("[CPU] maybeTriggerCpuTurn: scheduling " + p.getName() + " in " + delayMs + "ms");
        PauseTransition pause = new PauseTransition(Duration.millis(delayMs));
        pause.setOnFinished(e -> {
            Player cur = state.getCurrentPlayer();
            System.out.println("[CPU] pause fired, current=" + cur.getName()
                    + " human=" + cur.isHuman() + " phase=" + state.getPhase());
            if (!cur.isHuman() && state.getPhase() == GameState.Phase.PLAYER_TURN) {
                cpuPlayer.takeTurn();
            } else {
                System.out.println("[CPU] skipping takeTurn — player changed or wrong phase");
                cpuTurnInProgress = false;
            }
        });
        pause.play();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Layout builders
    // ═════════════════════════════════════════════════════════════════════════

    private HBox buildHud() {
        HBox hud = new HBox(24);
        hud.getStyleClass().add("hud-bar");
        hud.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("WORK GAME");
        title.getStyleClass().add("title-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        turnLabel  = new Label("—");   turnLabel.getStyleClass().add("player-name-active");
        roundLabel = new Label("Round 1"); roundLabel.getStyleClass().add("hour-label");
        poolLabel  = new Label("Pool: —"); poolLabel.getStyleClass().add("section-label");
        myHoursLabel = new Label("⏱ —"); myHoursLabel.getStyleClass().add("my-hours-label");

        hud.getChildren().addAll(title, spacer, myHoursLabel, turnLabel, roundLabel, poolLabel);
        return hud;
    }

    private HBox buildTableAndLog() {
        HBox container = new HBox();
        StackPane table = buildTable();
        HBox.setHgrow(table, Priority.ALWAYS);
        container.getChildren().addAll(table, buildLogPanel());
        return container;
    }

    private StackPane buildTable() {
        tableRoot = new StackPane();
        tableRoot.setStyle("-fx-background-color: radial-gradient(center 50% 50%, radius 70%," +
                " #1e5530 0%, #0f3018 70%, #08200f 100%);");
        HBox.setHgrow(tableRoot, Priority.ALWAYS);

        Rectangle cloth = new Rectangle(820, 460);
        cloth.setArcWidth(200); cloth.setArcHeight(200);
        cloth.setFill(Color.web("#174a22", 0.40));
        cloth.setStroke(Color.web("#3a7030", 0.30)); cloth.setStrokeWidth(2);

        VBox content = new VBox(10);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(14, 14, 8, 14));

        opponentArea = new VBox(8);
        opponentArea.setAlignment(Pos.TOP_CENTER);

        HBox centerStrip = new HBox(20);
        centerStrip.setAlignment(Pos.CENTER);

        deckNode = buildDeckNode();
        VBox discardNode = buildDiscardNode();

        Label myPlayLabel = new Label("YOUR PLAY ZONE");
        myPlayLabel.getStyleClass().add("section-label");
        myPlayedZone = new HBox(10);
        myPlayedZone.setAlignment(Pos.CENTER_LEFT);
        VBox myZoneBox = new VBox(4, myPlayLabel, myPlayedZone);
        myZoneBox.getStyleClass().add("player-zone");
        myZoneBox.setAlignment(Pos.TOP_LEFT);
        myZoneBox.setMinWidth(360);

        centerStrip.getChildren().addAll(deckNode, discardNode, myZoneBox);
        content.getChildren().addAll(opponentArea, centerStrip);
        tableRoot.getChildren().addAll(cloth, content);
        StackPane.setAlignment(content, Pos.TOP_CENTER);
        return tableRoot;
    }

    private VBox buildDeckNode() {
        VBox deck = new VBox(4);
        deck.getStyleClass().add("deck-pile");
        deck.setAlignment(Pos.CENTER);

        Label icon  = new Label("🂠");
        icon.setStyle("-fx-font-size: 28px; -fx-text-fill: #90c0f0;");
        Label count = new Label("—");
        count.getStyleClass().add("deck-count");
        count.setId("deck-count-label");

        deck.getChildren().addAll(icon, count);
        deck.setOnMouseClicked(e -> {
            if (!state.isCardDrawnThisTurn() && !state.isActionTakenThisTurn())
                controller.drawCard();
        });
        return deck;
    }

    private VBox buildDiscardNode() {
        VBox discard = new VBox(4);
        discard.getStyleClass().add("discard-zone");
        discard.setAlignment(Pos.CENTER);
        Label icon  = new Label("✕");
        icon.setStyle("-fx-font-size: 22px; -fx-text-fill: #806040;");
        Label count = new Label("—");
        count.setId("discard-count-label");
        count.getStyleClass().add("section-label");
        discard.getChildren().addAll(icon, count);
        return discard;
    }

    private VBox buildLogPanel() {
        VBox panel = new VBox(6);
        panel.getStyleClass().add("log-panel");
        panel.setPrefWidth(220); panel.setMinWidth(180);

        Label title = new Label("EVENT LOG");
        title.getStyleClass().add("section-label");

        logArea = new TextArea();
        logArea.getStyleClass().add("log-area");
        logArea.setEditable(false); logArea.setWrapText(true);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        panel.getChildren().addAll(title, logArea);
        return panel;
    }

    private VBox buildBottomSection() {
        VBox bottom = new VBox(6);
        bottom.getStyleClass().add("hand-tray");
        bottom.setPadding(new Insets(8, 14, 10, 14));

        actionBar = new HBox(8);
        actionBar.setAlignment(Pos.CENTER_LEFT);

        Label handLabel = new Label("YOUR HAND");
        handLabel.getStyleClass().add("section-label");

        handTray = new HBox(6);
        handTray.setAlignment(Pos.CENTER_LEFT);

        ScrollPane handScroll = new ScrollPane(handTray);
        handScroll.setFitToHeight(true);
        handScroll.setPrefHeight(130);
        handScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        handScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        handScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        bottom.getChildren().addAll(actionBar, handLabel, handScroll);
        return bottom;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ANIMATION 1 — DRAW: face-down → flies to hand → flips face-up
    // ═════════════════════════════════════════════════════════════════════════

    private void onCardDrawn(Card drawnCard) {
        Platform.runLater(() -> {
            Bounds deckBounds = deckNode.localToScene(deckNode.getBoundsInLocal());
            Player drawer = state.getCurrentPlayer();
            boolean drawerIsHuman = drawer.isHuman();

            if (drawerIsHuman) {
                // Human draw: animate to hand tray (existing behavior)
                rebuildHandTrayWithPlaceholder(drawnCard, "draw-placeholder");
                rebuildActionBar(true);
                syncLabels();

                Platform.runLater(() -> {
                    CardView placeholder = findTagged("draw-placeholder");
                    Bounds dest = placeholder != null
                            ? placeholder.localToScene(placeholder.getBoundsInLocal())
                            : new BoundingBox(deckBounds.getMinX(), deckBounds.getMinY() + 300, GHOST_W, GHOST_H);

                    CardView ghost = makeGhost(CardView.faceDown(), deckBounds.getMinX(), deckBounds.getMinY());
                    glassPane.getChildren().add(ghost);

                    flyWithFlip(ghost, drawnCard,
                            deckBounds.getMinX(), deckBounds.getMinY(),
                            dest.getMinX(), dest.getMinY(),
                            false,   // face-down → face-up (flip)
                            () -> {
                                glassPane.getChildren().remove(ghost);
                                if (placeholder != null) placeholder.setOpacity(1.0);
                            });
                });
            } else {
                // CPU draw: animate face-down card flying to opponent's zone — no hand tray change
                syncLabels();
                VBox targetZone = opponentZoneNodes.get(drawer);
                Platform.runLater(() -> {
                    double toX, toY;
                    if (targetZone != null) {
                        Bounds z = targetZone.localToScene(targetZone.getBoundsInLocal());
                        toX = z.getMinX() + z.getWidth() * 0.6;
                        toY = z.getMinY();
                    } else {
                        toX = deckBounds.getMinX();
                        toY = 20;
                    }
                    CardView ghost = makeGhost(CardView.faceDown(), deckBounds.getMinX(), deckBounds.getMinY());
                    glassPane.getChildren().add(ghost);
                    flyNoFlip(ghost,
                            deckBounds.getMinX(), deckBounds.getMinY(), toX, toY,
                            () -> glassPane.getChildren().remove(ghost));
                });
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ANIMATION 2 — PLAY TO ZONE: face-up card slides from hand to play zone
    // ═════════════════════════════════════════════════════════════════════════

    private void onCardPlayed(Card card, Player player) {
        Platform.runLater(() -> {
            // Find the source: the card was removed from hand already,
            // so we use the hand tray's current scene position as origin fallback.
            // Rebuild hand (card is gone), add placeholder in play zone.
            rebuildHandTray(state.getCurrentPlayer(), false);
            rebuildMyPlayZoneWithPlaceholder(card, "play-placeholder");
            syncLabels();

            Platform.runLater(() -> {
                CardView placeholder = findTagged("play-placeholder");
                Bounds dest = placeholder != null
                        ? placeholder.localToScene(placeholder.getBoundsInLocal())
                        : myPlayedZone.localToScene(myPlayedZone.getBoundsInLocal());

                // Origin: center of hand tray
                Bounds origin = handTray.localToScene(handTray.getBoundsInLocal());
                double fromX  = origin.getMinX() + origin.getWidth() / 2 - GHOST_W / 2;
                double fromY  = origin.getMinY();

                CardView ghost = makeGhost(new CardView(card, CardView.Mode.HAND),
                        fromX, fromY);
                glassPane.getChildren().add(ghost);

                flyNoFlip(ghost,
                        fromX, fromY,
                        dest.getMinX(), dest.getMinY(),
                        () -> {
                            glassPane.getChildren().remove(ghost);
                            if (placeholder != null) placeholder.setOpacity(1.0);
                            controller.finishPlayCard();
                        });
            });
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ANIMATION 3 — WEAPON TO OPPONENT: slides to their zone, flips face-down
    // ═════════════════════════════════════════════════════════════════════════

    private void onWeaponSent(Card card, Player target) {
        Platform.runLater(() -> {
            rebuildHandTray(state.getCurrentPlayer(), false);
            syncLabels();
            rebuildOpponentArea(); // rebuild so target zone now shows placeholder

            Platform.runLater(() -> {
                VBox targetZone = opponentZoneNodes.get(target);
                Bounds dest = targetZone != null
                        ? targetZone.localToScene(targetZone.getBoundsInLocal())
                        : new BoundingBox(WINDOW_W / 2.0, 20, GHOST_W, GHOST_H);

                Bounds origin = handTray.localToScene(handTray.getBoundsInLocal());
                double fromX  = origin.getMinX() + origin.getWidth() / 2 - GHOST_W / 2;
                double fromY  = origin.getMinY();

                double toX = dest.getMinX() + dest.getWidth() / 2 - GHOST_W / 2;
                double toY = dest.getMinY();

                CardView ghost = makeGhost(new CardView(card, CardView.Mode.HAND), fromX, fromY);
                glassPane.getChildren().add(ghost);

                flyWithFlip(ghost, card,
                        fromX, fromY, toX, toY,
                        true,   // start face-up, flip to face-down
                        () -> {
                            glassPane.getChildren().remove(ghost);
                            controller.finishWeaponSent();
                        });
            });
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ANIMATION 4 — ALERT: card zooms to center, flash overlay, then resolves
    // ═════════════════════════════════════════════════════════════════════════

    private void onAlertDrawn(Card card, Runnable resolve) {
        Platform.runLater(() -> {
            // Determine flash color and flavor text by card
            Color flashColor = alertFlashColor(card);
            String flavor    = alertFlavor(card);

            // ── 1. Full-screen dim overlay ────────────────────────────────────
            Rectangle dim = new Rectangle(WINDOW_W, WINDOW_H);
            dim.setFill(Color.web("#000000", 0.55));
            dim.setOpacity(0);
            AnchorPane.setTopAnchor(dim, 0.0);
            AnchorPane.setLeftAnchor(dim, 0.0);

            // ── 2. Flash color wash ────────────────────────────────────────────
            Rectangle flash = new Rectangle(WINDOW_W, WINDOW_H);
            flash.setFill(flashColor.deriveColor(0, 1, 1, 0.28));
            flash.setOpacity(0);
            AnchorPane.setTopAnchor(flash, 0.0);
            AnchorPane.setLeftAnchor(flash, 0.0);

            // ── 3. Giant card in the center ────────────────────────────────────
            CardView bigCard = new CardView(card, CardView.Mode.HAND);
            bigCard.setScaleX(0.3); bigCard.setScaleY(0.3);
            bigCard.setOpacity(0);
            bigCard.setMinWidth(180); bigCard.setMaxWidth(180);
            bigCard.setMinHeight(240); bigCard.setMaxHeight(240);
            // Center it manually via anchors
            AnchorPane.setTopAnchor(bigCard,  (WINDOW_H - 240) / 2.0 - 40);
            AnchorPane.setLeftAnchor(bigCard, (WINDOW_W - 180) / 2.0);

            // ── 4. Flavor label below ──────────────────────────────────────────
            Label flavorLabel = new Label(flavor);
            flavorLabel.setStyle(
                    "-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " +
                            toHex(flashColor) + "; -fx-font-family: 'JetBrains Mono','Courier New',monospace;" +
                            "-fx-effect: dropshadow(gaussian, black, 12, 0.8, 0, 0);");
            flavorLabel.setOpacity(0);
            AnchorPane.setTopAnchor(flavorLabel, (WINDOW_H - 240) / 2.0 + 220);
            AnchorPane.setLeftAnchor(flavorLabel, 0.0);
            AnchorPane.setRightAnchor(flavorLabel, 0.0);
            flavorLabel.setAlignment(Pos.CENTER);

            glassPane.setMouseTransparent(false); // block input during alert
            glassPane.getChildren().addAll(dim, flash, bigCard, flavorLabel);

            // ── Phase A: fade in dim + zoom in card ────────────────────────────
            FadeTransition dimIn   = new FadeTransition(Duration.millis(200), dim);
            dimIn.setToValue(1.0);

            FadeTransition cardIn  = new FadeTransition(Duration.millis(250), bigCard);
            cardIn.setToValue(1.0);
            ScaleTransition zoomIn = new ScaleTransition(Duration.millis(350), bigCard);
            zoomIn.setToX(1.0); zoomIn.setToY(1.0);
            zoomIn.setInterpolator(Interpolator.EASE_OUT);

            FadeTransition labelIn = new FadeTransition(Duration.millis(250), flavorLabel);
            labelIn.setToValue(1.0); labelIn.setDelay(Duration.millis(180));

            ParallelTransition phaseA = new ParallelTransition(dimIn, cardIn, zoomIn, labelIn);

            // ── Phase B: flash pulse (color wash in + out) ─────────────────────
            FadeTransition flashIn  = new FadeTransition(Duration.millis(180), flash);
            flashIn.setToValue(1.0);
            FadeTransition flashOut = new FadeTransition(Duration.millis(300), flash);
            flashOut.setToValue(0.0);
            SequentialTransition flashPulse = new SequentialTransition(
                    new PauseTransition(Duration.millis(300)), flashIn, flashOut);

            // ── Phase C: card shake ────────────────────────────────────────────
            Timeline shake = buildShakeTimeline(bigCard);
            shake.setDelay(Duration.millis(280));

            ParallelTransition phaseABC = new ParallelTransition(phaseA, flashPulse, shake);

            // ── Phase D: hold for a beat, then zoom out + resolve ──────────────
            PauseTransition hold = new PauseTransition(Duration.millis(820));

            FadeTransition dimOut  = new FadeTransition(Duration.millis(280), dim);
            dimOut.setToValue(0);
            FadeTransition cardOut = new FadeTransition(Duration.millis(250), bigCard);
            cardOut.setToValue(0);
            ScaleTransition zoomOut = new ScaleTransition(Duration.millis(280), bigCard);
            zoomOut.setToX(0.2); zoomOut.setToY(0.2);
            FadeTransition labelOut = new FadeTransition(Duration.millis(200), flavorLabel);
            labelOut.setToValue(0);

            ParallelTransition phaseD = new ParallelTransition(dimOut, cardOut, zoomOut, labelOut);

            SequentialTransition full = new SequentialTransition(phaseABC, hold, phaseD);
            full.setOnFinished(e -> {
                glassPane.getChildren().removeAll(dim, flash, bigCard, flavorLabel);
                glassPane.setMouseTransparent(true);
                // Now let the controller actually resolve the alert
                resolve.run();
            });
            full.play();
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ANIMATION 5 — BULK DISCARD: cards fan-fly to the discard pile one by one
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Animates each card in the list flying from the hand tray area to the
     * discard pile in sequence (staggered by 120ms each).
     * Calls onDone after the last card lands.
     */
    private void onBulkDiscard(List<Card> cards, Runnable onDone) {
        Platform.runLater(() -> {
            // Clear the hand tray immediately — the cards are already removed from state
            handTray.getChildren().clear();
            syncLabels();

            // Determine discard pile scene coords
            Platform.runLater(() -> {
                // Find the discard zone node by ID
                var discardLookup = tableRoot.lookupAll("#discard-count-label");
                double destX = WINDOW_W / 2.0 - GHOST_W / 2;
                double destY = 160;
                for (var node : discardLookup) {
                    if (node.getParent() instanceof VBox vb) {
                        Bounds b = vb.localToScene(vb.getBoundsInLocal());
                        destX = b.getMinX();
                        destY = b.getMinY();
                        break;
                    }
                }

                // Source: center of where hand tray is
                Bounds trayBounds = handTray.localToScene(handTray.getBoundsInLocal());
                double baseFromX = trayBounds.getMinX() + 20;
                double baseFromY = trayBounds.getMinY();

                double finalDestX = destX;
                double finalDestY = destY;

                // Build a sequential chain: each card flies, then next starts
                // We stagger via delay rather than chaining so they overlap slightly
                int[] remaining = { cards.size() };
                for (int i = 0; i < cards.size(); i++) {
                    Card c = cards.get(i);
                    double staggerX = baseFromX + i * 14.0; // fan out slightly
                    double delayMs  = i * 130.0;

                    CardView ghost = makeGhost(new CardView(c, CardView.Mode.HAND),
                            staggerX, baseFromY);
                    glassPane.getChildren().add(ghost);

                    // Short quick toss to discard pile
                    double dx = finalDestX - staggerX;
                    double dy = finalDestY - baseFromY;
                    double dist = Math.hypot(dx, dy);
                    double ms   = Math.min(380, Math.max(200, dist * 0.4));

                    Timeline tl = new Timeline(
                            new KeyFrame(Duration.ZERO,
                                    new KeyValue(ghost.translateXProperty(), 0),
                                    new KeyValue(ghost.translateYProperty(), 0),
                                    new KeyValue(ghost.opacityProperty(), 1.0)),
                            new KeyFrame(Duration.millis(ms * 0.4),
                                    new KeyValue(ghost.translateYProperty(),
                                            dy / 2 - 30, Interpolator.EASE_OUT)),
                            new KeyFrame(Duration.millis(ms),
                                    new KeyValue(ghost.translateXProperty(), dx,
                                            Interpolator.SPLINE(0.4, 0, 0.6, 1)),
                                    new KeyValue(ghost.translateYProperty(), dy,
                                            Interpolator.EASE_IN),
                                    new KeyValue(ghost.opacityProperty(), 0.0))
                    );
                    tl.setDelay(Duration.millis(delayMs));

                    CardView finalGhost = ghost;
                    tl.setOnFinished(ev -> {
                        glassPane.getChildren().remove(finalGhost);
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            // All cards landed — let controller proceed
                            onDone.run();
                        }
                    });
                    tl.play();
                }

                // Edge case: no cards to discard
                if (cards.isEmpty()) onDone.run();
            });
        });
    }

    /**
     * Fly with an arc, no flip. Used for play-to-zone.
     */
    private void flyNoFlip(CardView ghost,
                           double fromX, double fromY,
                           double toX,   double toY,
                           Runnable onDone) {

        double dx = toX - fromX;
        double dy = toY - fromY;
        double dist = Math.hypot(dx, dy);
        double ms   = travelMs(dist);
        double arc  = Math.min(50, dist * 0.10);

        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(ghost.translateXProperty(), 0),
                        new KeyValue(ghost.translateYProperty(), 0)),
                new KeyFrame(Duration.millis(ms * 0.45),
                        new KeyValue(ghost.translateYProperty(), dy / 2 - arc, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(ms),
                        new KeyValue(ghost.translateXProperty(), dx, Interpolator.SPLINE(0.4,0,0.2,1)),
                        new KeyValue(ghost.translateYProperty(), dy, Interpolator.EASE_IN))
        );
        tl.setOnFinished(e -> onDone.run());
        tl.play();
    }

    /**
     * Fly with an arc and a ScaleX flip mid-journey.
     *
     * @param startFaceDown  true → ghost starts face-down and flips to face-up
     *                       false → ghost starts face-up and flips to face-down
     */
    private void flyWithFlip(CardView ghost, Card card,
                             double fromX, double fromY,
                             double toX,   double toY,
                             boolean startFaceDown,
                             Runnable onDone) {

        double dx = toX - fromX;
        double dy = toY - fromY;
        double dist    = Math.hypot(dx, dy);
        double ms      = travelMs(dist);
        double arc     = Math.min(60, dist * 0.12);
        double flipAt  = ms * 0.55;
        double halfFlip = 120;

        // Arc travel
        Timeline arc_tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(ghost.translateXProperty(), 0),
                        new KeyValue(ghost.translateYProperty(), 0)),
                new KeyFrame(Duration.millis(ms * 0.45),
                        new KeyValue(ghost.translateYProperty(), dy / 2 - arc, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(ms),
                        new KeyValue(ghost.translateXProperty(), dx, Interpolator.SPLINE(0.4,0,0.2,1)),
                        new KeyValue(ghost.translateYProperty(), dy, Interpolator.EASE_IN))
        );

        // Flip sequence: shrink → swap content → grow
        ScaleTransition shrink = new ScaleTransition(Duration.millis(halfFlip), ghost);
        shrink.setFromX(1.0); shrink.setToX(0.0);
        shrink.setInterpolator(Interpolator.EASE_IN);

        ScaleTransition grow = new ScaleTransition(Duration.millis(halfFlip), ghost);
        grow.setFromX(0.0); grow.setToX(1.0);
        grow.setInterpolator(Interpolator.EASE_OUT);

        shrink.setOnFinished(e -> {
            if (startFaceDown) swapGhostToFaceUp(ghost, card);
            else               swapGhostToFaceDown(ghost);
        });

        SequentialTransition flipSeq = new SequentialTransition(
                shrink, new PauseTransition(Duration.millis(1)), grow);
        flipSeq.setDelay(Duration.millis(flipAt));

        ParallelTransition all = new ParallelTransition(arc_tl, flipSeq);
        all.setOnFinished(e -> onDone.run());
        all.play();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Ghost helpers
    // ═════════════════════════════════════════════════════════════════════════

    private CardView makeGhost(CardView source, double sceneX, double sceneY) {
        source.setMinWidth(GHOST_W); source.setMaxWidth(GHOST_W);
        source.setMinHeight(GHOST_H); source.setMaxHeight(GHOST_H);
        AnchorPane.setLeftAnchor(source, sceneX);
        AnchorPane.setTopAnchor(source,  sceneY);
        return source;
    }

    private void swapGhostToFaceUp(CardView ghost, Card card) {
        ghost.getChildren().clear();
        ghost.getStyleClass().removeIf(s -> s.equals("card-facedown"));
        CardView faceUp = new CardView(card, CardView.Mode.HAND);
        ghost.getChildren().addAll(new ArrayList<>(faceUp.getChildren()));
        faceUp.getStyleClass().stream()
                .filter(s -> !s.equals("card-facedown"))
                .forEach(s -> { if (!ghost.getStyleClass().contains(s)) ghost.getStyleClass().add(s); });
        ghost.setAlignment(Pos.TOP_LEFT);
    }

    private void swapGhostToFaceDown(CardView ghost) {
        ghost.getChildren().clear();
        ghost.getStyleClass().removeIf(s -> !s.equals("card-facedown") && !s.equals("card"));
        if (!ghost.getStyleClass().contains("card-facedown"))
            ghost.getStyleClass().add("card-facedown");
        Label back = new Label("✦");
        back.setStyle("-fx-text-fill: rgba(80,120,220,0.40); -fx-font-size: 16px;");
        ghost.setAlignment(Pos.CENTER);
        ghost.getChildren().add(back);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Alert helpers
    // ═════════════════════════════════════════════════════════════════════════

    private Color alertFlashColor(Card card) {
        return switch (card.getName()) {
            case "Fired!"            -> Color.web("#ff4020");
            case "Recession"         -> Color.web("#ff2020");
            case "Amnesia"           -> Color.web("#40d0ff");
            case "Performance Review"-> Color.web("#ffa020");
            default                  -> Color.web("#ffffff");
        };
    }

    private String alertFlavor(Card card) {
        return switch (card.getName()) {
            case "Fired!"            -> "YOU'RE FIRED!";
            case "Recession"         -> "THE ECONOMY CRASHES";
            case "Amnesia"           -> "WHAT WERE WE DOING?";
            case "Performance Review"-> "PRODUCTIVITY ASSESSMENT";
            default                  -> card.getName().toUpperCase();
        };
    }

    private String toHex(Color c) {
        return String.format("#%02x%02x%02x",
                (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
    }

    /** Horizontal shake timeline on a node. */
    private Timeline buildShakeTimeline(CardView node) {
        double s = 12;
        return new Timeline(
                new KeyFrame(Duration.ZERO,         new KeyValue(node.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(60),   new KeyValue(node.translateXProperty(),  s, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(120),  new KeyValue(node.translateXProperty(), -s, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(180),  new KeyValue(node.translateXProperty(),  s, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(240),  new KeyValue(node.translateXProperty(), -s, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(300),  new KeyValue(node.translateXProperty(),  0, Interpolator.EASE_BOTH))
        );
    }

    private double travelMs(double dist) {
        return Math.min(620, Math.max(340, dist * 0.5));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Placeholder helpers
    // ═════════════════════════════════════════════════════════════════════════

    private void rebuildHandTrayWithPlaceholder(Card target, String tag) {
        handTray.getChildren().clear();
        Player p = state.getCurrentPlayer();
        for (Card c : p.getHand()) {
            CardView cv = new CardView(c, CardView.Mode.HAND);
            if (c == target) { cv.setOpacity(0.0); cv.setId(tag); }
            cv.setOnMouseClicked(e -> onHandCardClicked(cv));
            handTray.getChildren().add(cv);
        }
    }

    private void rebuildMyPlayZoneWithPlaceholder(Card target, String tag) {
        myPlayedZone.getChildren().clear();
        Player p = state.getCurrentPlayer();
        if (p.getPlayedCards().isEmpty()) {
            Label none = new Label("— play a card —");
            none.getStyleClass().add("section-label");
            myPlayedZone.getChildren().add(none);
            return;
        }
        for (Card c : p.getPlayedCards()) {
            CardView cv = new CardView(c, CardView.Mode.PLAYED);
            if (c == target) { cv.setOpacity(0.0); cv.setId(tag); }
            cv.setOnMouseClicked(e -> onPlayedCardClicked(p, c));
            myPlayedZone.getChildren().add(cv);
        }
    }

    private CardView findTagged(String tag) {
        for (var node : handTray.getChildren()) {
            if (node instanceof CardView cv && tag.equals(cv.getId())) return cv;
        }
        for (var node : myPlayedZone.getChildren()) {
            if (node instanceof CardView cv && tag.equals(cv.getId())) return cv;
        }
        return null;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Zone rebuilders
    // ═════════════════════════════════════════════════════════════════════════

    private void rebuildOpponentArea() {
        opponentArea.getChildren().clear();
        opponentZoneNodes.clear();
        Player current = state.getCurrentPlayer();
        List<Player> opponents = state.getPlayers().stream()
                .filter(p -> p != current).toList();

        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER);
        for (Player opp : opponents) {
            VBox zone = buildOpponentZone(opp);
            opponentZoneNodes.put(opp, zone);
            row.getChildren().add(zone);
        }
        opponentArea.getChildren().add(row);
    }

    private VBox buildOpponentZone(Player p) {
        VBox zone = new VBox(6);
        zone.getStyleClass().add("player-zone");
        zone.setAlignment(Pos.TOP_CENTER);
        zone.setMinWidth(180);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label name = new Label(p.getName());
        name.getStyleClass().add("player-name-label");
        Label hours = new Label("⏱ " + p.getHourTokens() + "h");
        hours.getStyleClass().add("hour-label");
        header.getChildren().addAll(name, hours);

        HBox playedRow = new HBox(6);
        playedRow.setAlignment(Pos.CENTER_LEFT);
        for (Card c : p.getPlayedCards()) {
            CardView cv = new CardView(c, CardView.Mode.PLAYED);
            cv.setOnMouseClicked(e -> onPlayedCardClicked(p, c));
            playedRow.getChildren().add(cv);
        }
        if (p.getPlayedCards().isEmpty()) {
            Label none = new Label("— no cards —");
            none.getStyleClass().add("section-label");
            playedRow.getChildren().add(none);
        }

        Label handCount = new Label("hand: " + p.handSize() + " card" + (p.handSize() == 1 ? "" : "s"));
        handCount.getStyleClass().add("section-label");

        HBox handRow = new HBox(-18);
        handRow.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < p.handSize(); i++) {
            CardView cv = CardView.faceDown();
            double spread = p.handSize() > 1 ? 16.0 / (p.handSize() - 1) : 0;
            cv.setRotate(-8 + i * spread);
            handRow.getChildren().add(cv);
        }

        zone.getChildren().addAll(header, playedRow, handCount, handRow);
        return zone;
    }

    private void rebuildMyPlayZone() {
        myPlayedZone.getChildren().clear();
        Player p = state.getCurrentPlayer();
        if (p.getPlayedCards().isEmpty()) {
            Label none = new Label("— play a card —");
            none.getStyleClass().add("section-label");
            myPlayedZone.getChildren().add(none);
            return;
        }
        for (Card c : p.getPlayedCards()) {
            CardView cv = new CardView(c, CardView.Mode.PLAYED);
            cv.setOnMouseClicked(e -> onPlayedCardClicked(p, c));
            myPlayedZone.getChildren().add(cv);
        }
    }

    private void rebuildHandTray(Player p, boolean viewOnly) {
        handTray.getChildren().clear();
        handTray.getStyleClass().removeAll("hand-tray", "hand-tray-viewonly");
        handTray.getStyleClass().add(viewOnly ? "hand-tray-viewonly" : "hand-tray");

        selectedCard = null; // re-find below by name

        for (Card c : p.getHand()) {
            CardView cv = new CardView(c, viewOnly ? CardView.Mode.VIEWONLY : CardView.Mode.HAND);
            if (!viewOnly) {
                cv.setOnMouseClicked(e -> onHandCardClicked(cv));
                // Restore selection if this card name was selected before rebuild
                if (selectedCardName != null && c.getName().equals(selectedCardName)) {
                    cv.setSelected(true);
                    selectedCard = cv;
                }
            } else {
                cv.setOnMouseClicked(e -> showHandCardInfo(c));
            }
            handTray.getChildren().add(cv);
        }
        // Card left the hand — clear the stored name
        if (selectedCard == null) selectedCardName = null;
    }

    /** Show a read-only info popup for a card in view-only mode. */
    private void showHandCardInfo(Card card) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle(card.getName());
        styleDialog(dlg);

        VBox content = new VBox(8);
        content.setPadding(new Insets(14));
        content.setStyle("-fx-background-color: #0f1520; -fx-min-width: 280px;");

        Label typeLbl = new Label(card.getType().toString().replace("_", " "));
        typeLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #6a9060; -fx-font-weight: bold;");
        Label nameLbl = new Label(card.getName());
        nameLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #e8dcc8;");
        Label descLbl = new Label(card.getDescription());
        descLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #c0d8a8;");
        descLbl.setWrapText(true);

        Label noteLbl = new Label("(Viewing only — not your turn)");
        noteLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #607080;");

        content.getChildren().addAll(typeLbl, nameLbl, new Separator(), descLbl, noteLbl);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.showAndWait();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Action bar
    // ═════════════════════════════════════════════════════════════════════════

    private void rebuildActionBar(boolean isActiveHuman) {
        actionBar.getChildren().clear();

        if (!isActiveHuman) {
            // View-only during CPU turn
            Label watchLbl = new Label("Watching " + state.getCurrentPlayer().getName() + "'s turn...");
            watchLbl.getStyleClass().add("section-label");
            actionBar.getChildren().add(watchLbl);

            // "Next Turn" button when auto-advance is disabled
            if (!SetupScreen.autoAdvanceCpu && !cpuTurnInProgress) {
                Button nextBtn = new Button("→ NEXT TURN");
                nextBtn.getStyleClass().addAll("btn", "btn-endturn");
                nextBtn.setOnAction(e -> {
                    nextTurnRequested = true;
                    maybeTriggerCpuTurn();
                    rebuildActionBar(false);
                });
                actionBar.getChildren().add(nextBtn);
            }

            // Helper note
            Player viewer = nextHotseatHuman();
            if (viewer != null) {
                boolean viewerHasHelper = viewer.getHand().stream()
                        .anyMatch(c -> c.getType() == CardType.HELPER);
                if (viewerHasHelper) {
                    Label helperNote = new Label("  (You may play a helper reactively)");
                    helperNote.setStyle("-fx-font-size: 10px; -fx-text-fill: #5a8090;");
                    actionBar.getChildren().add(helperNote);
                }
            }
            return;
        }

        boolean drawn   = state.isCardDrawnThisTurn();
        boolean acted   = state.isActionTakenThisTurn();
        boolean hasCard = selectedCard != null;
        boolean hasHelper  = hasCard && selectedCard.getCard().getType() == CardType.HELPER;

        // Draw
        Button drawBtn = new Button("↓ DRAW CARD");
        drawBtn.getStyleClass().addAll("btn", "btn-primary");
        drawBtn.setDisable(drawn || acted);
        drawBtn.setOnAction(e -> controller.drawCard());

        // Play
        Button playBtn = new Button("▶ PLAY");
        playBtn.getStyleClass().addAll("btn", "btn-primary");
        playBtn.setDisable(!drawn || acted || !hasCard || hasHelper);
        playBtn.setOnAction(e -> {
            if (selectedCard != null) { controller.playCardFromHand(selectedCard.getCard()); clearSelection(); }
        });

        // Discard
        Button discardBtn = new Button("✕ DISCARD");
        discardBtn.getStyleClass().addAll("btn", "btn-danger");
        discardBtn.setDisable(!drawn || acted || !hasCard);
        discardBtn.setOnAction(e -> {
            if (selectedCard != null) { controller.discardFromHand(selectedCard.getCard()); clearSelection(); }
        });

        // Use Helper — always available (not gated on drawn/acted) since helpers can be reactive
        Button helperBtn = new Button("✦ USE HELPER");
        helperBtn.getStyleClass().add("btn");
        helperBtn.setDisable(!hasHelper);
        helperBtn.setOnAction(e -> {
            if (selectedCard != null) { controller.useHelperCard(selectedCard.getCard(), null, null); clearSelection(); }
        });

        // End Turn — visible when drawn (replaces skip; more deliberate)
        Button endBtn = new Button(acted ? "→ END TURN" : "⊘ END TURN (skip action)");
        endBtn.getStyleClass().addAll("btn", acted ? "btn-endturn" : "btn-skip");
        endBtn.setDisable(!drawn);
        endBtn.setOnAction(e -> controller.skipTurn());

        actionBar.getChildren().addAll(drawBtn, playBtn, discardBtn, helperBtn, endBtn);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Click handlers
    // ═════════════════════════════════════════════════════════════════════════

    private void onHandCardClicked(CardView cv) {
        if (selectedCard != null) selectedCard.setSelected(false);
        selectedCard = (selectedCard == cv) ? null : cv;
        if (selectedCard != null) {
            selectedCard.setSelected(true);
            selectedCardName = selectedCard.getCard().getName();
        } else {
            selectedCardName = null;
        }
        rebuildActionBar(true);
    }

    private void clearSelection() {
        if (selectedCard != null) selectedCard.setSelected(false);
        selectedCard     = null;
        selectedCardName = null;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Target dialogs
    // ═════════════════════════════════════════════════════════════════════════

    private void handleTargetRequest(GameController.TargetRequest req) {
        Platform.runLater(() -> {
            switch (req.type()) {
                case SEND_WEAPON    -> showWeaponTargetDialog(req);
                case SHARING_TARGET -> showSharingTargetDialog(req);
                case QUIT_TARGET    -> showQuitTargetDialog(req);
                case HELPER_TARGET  -> showHelperTargetDialog(req);
                case TARDY_TARGET   -> showCardPickDialog(req, "Tardy",
                        "Add 1 round to which of " + req.exclude().getName() + "'s cards?",
                        req.exclude().getPlayedCards(),
                        chosen -> controller.resolveTardy(req.exclude(), chosen));
                case DEADLINE_TARGET -> showCardPickDialog(req, "Deadline",
                        "Expire which of " + req.exclude().getName() + "'s cards?",
                        req.exclude().getPlayedCards(),
                        chosen -> controller.resolveDeadline(req.exclude(), chosen));
            }
        });
    }

    private void showWeaponTargetDialog(GameController.TargetRequest req) {
        Player current = state.getCurrentPlayer();
        List<Player> targets = req.allPlayers().stream().filter(p -> p != current).toList();
        if (targets.isEmpty()) { appendLog("No valid targets."); return; }
        ChoiceDialog<Player> dlg = new ChoiceDialog<>(targets.get(0), targets);
        dlg.setTitle("Send Weapon");
        dlg.setHeaderText("Send \"" + req.card().getName() + "\" to:");
        styleDialog(dlg);
        dlg.showAndWait().ifPresent(t -> controller.resolveWeaponOnTarget(req.card(), current, t));
    }

    private void showSharingTargetDialog(GameController.TargetRequest req) {
        List<Player> targets = req.allPlayers().stream().filter(p -> p != req.actor()).toList();
        if (targets.isEmpty()) return;
        ChoiceDialog<Player> dlg = new ChoiceDialog<>(targets.get(0), targets);
        dlg.setTitle("Sharing is Caring");
        dlg.setHeaderText("Share hours with which player?");
        styleDialog(dlg);
        dlg.showAndWait().ifPresent(t -> controller.setSharingTarget(req.card(), t));
    }

    private void showQuitTargetDialog(GameController.TargetRequest req) {
        Player target = req.exclude();
        if (target == null || target.getPlayedCards().isEmpty()) {
            appendLog("No played cards to Quit."); return;
        }
        showCardPickDialog(req, "Quit",
                "Discard which of " + target.getName() + "'s cards?",
                target.getPlayedCards(),
                chosen -> controller.resolveQuitTarget(target, chosen));
    }

    /**
     * Generic styled card-pick dialog. Shows each card as a button with name + desc + expiry.
     */
    private void showCardPickDialog(GameController.TargetRequest req, String title,
                                    String header, List<Card> choices,
                                    java.util.function.Consumer<Card> onPick) {
        if (choices.isEmpty()) { appendLog("No valid cards."); return; }

        Dialog<Card> dlg = new Dialog<>();
        dlg.setTitle(title);
        dlg.setHeaderText(header);
        styleDialog(dlg);

        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: #0f2010;");

        dlg.getDialogPane().getButtonTypes().add(
                new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE));

        for (Card c : new ArrayList<>(choices)) {
            Button btn = new Button();
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.getStyleClass().add("btn");

            String exp = c.getExpiresAfterRound() > 0
                    ? "  [exp: " + c.roundsRemaining() + "r]" : "";
            btn.setText(c.getName() + exp + "\n" + c.getDescription());
            btn.setWrapText(true);

            btn.setOnAction(e -> { dlg.setResult(c); dlg.close(); onPick.accept(c); });
            box.getChildren().add(btn);
        }

        dlg.getDialogPane().setContent(box);
        dlg.showAndWait();
    }

    /**
     * Helper target dialog — lets the player pick which played card to apply a helper to.
     * Nepotism/Extension: any played card on the table.
     * Excused: only weapon cards.
     */
    private void showHelperTargetDialog(GameController.TargetRequest req) {
        Card helper = req.card();
        boolean excusedMode = helper.getName().equals("Excused");

        List<Card>   eligible = new ArrayList<>();
        List<Player> owners   = new ArrayList<>();
        for (Player pl : state.getPlayers()) {
            for (Card c : pl.getPlayedCards()) {
                if (excusedMode && !c.isWeapon()) continue;
                eligible.add(c);
                owners.add(pl);
            }
        }

        if (eligible.isEmpty()) {
            appendLog("No valid targets for " + helper.getName() + "."); return;
        }

        Dialog<Integer> dlg = new Dialog<>();
        dlg.setTitle(helper.getName());
        dlg.setHeaderText("Apply " + helper.getName() + " to which card?");
        styleDialog(dlg);

        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: #0f2010;");
        dlg.getDialogPane().getButtonTypes().add(
                new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE));

        for (int i = 0; i < eligible.size(); i++) {
            Card c = eligible.get(i);
            Player owner = owners.get(i);
            final int idx = i;

            Button btn = new Button();
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.getStyleClass().add("btn");

            String exp = c.getExpiresAfterRound() > 0 ? "  [exp: " + c.roundsRemaining() + "r]" : "";
            String ownerLabel = (owner == state.getCurrentPlayer()) ? "You" : owner.getName();
            btn.setText("[" + ownerLabel + "] " + c.getName() + exp + "\n" + c.getDescription());
            btn.setWrapText(true);

            btn.setOnAction(e -> {
                dlg.setResult(idx); dlg.close();
                controller.applyHelperToCard(helper, owner, c);
            });
            box.getChildren().add(btn);
        }

        dlg.getDialogPane().setContent(box);
        dlg.showAndWait();
    }

    /**
     * Rich card info popup — shown when any played card is clicked in any zone.
     */
    private void onPlayedCardClicked(Player owner, Card card) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle(card.getName());
        styleDialog(dlg);

        VBox content = new VBox(10);
        content.setPadding(new Insets(14));
        content.setStyle("-fx-background-color: #0f2010; -fx-min-width: 340px;");

        // Type + Name
        Label typeLbl = new Label(card.getType().toString().replace("_", " "));
        typeLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #6a9060; -fx-font-weight: bold;");
        Label nameLbl = new Label(card.getName());
        nameLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #e8dcc8;");
        String ownerStr = owner == state.getCurrentPlayer() ? "You" : owner.getName();
        Label ownerLbl = new Label("Owner: " + ownerStr);
        ownerLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #6a9060;");
        Label descLbl = new Label(card.getDescription());
        descLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #c0d8a8;");
        descLbl.setWrapText(true);
        content.getChildren().addAll(typeLbl, nameLbl, ownerLbl, descLbl, new Separator());

        // ── Current state ─────────────────────────────────────────────────────
        VBox stats = new VBox(4);
        stats.setStyle("-fx-padding: 4 0 4 0;");
        addStatRow(stats, "Hours on card:", hoursOnCardLabel(card), "#f5d060");
        if (card.getHoursPerRound() != 0) {
            String perRound = (card.getHoursPerRound() > 0 ? "+" : "") + card.getHoursPerRound() + "h / round";
            addStatRow(stats, "Per round:", perRound, "#c0e0a0");
        }
        if (card.getExpiresAfterRound() > 0) {
            int rem = card.roundsRemaining();
            String expStr = card.isNepotismProtected() ? "Protected (Nepotism)"
                    : rem == 0 ? "⚠ EXPIRING NOW" : rem + " round(s)";
            String expCol = rem <= 1 && !card.isNepotismProtected() ? "#e87060" : "#a0b890";
            addStatRow(stats, "Expires in:", expStr, expCol);
            if (card.getExtensionBonus() > 0)
                addStatRow(stats, "  (extended by:", "+" + card.getExtensionBonus() + " rounds)", "#7898c0");
        }
        if (card.getFinalHours() > 0)
            addStatRow(stats, "Timely discard bonus:", "+" + card.getFinalHours() + "h on round " + card.getExpiresAfterRound(), "#c0a060");

        // Parasite sender info
        if (card.getName().equals("Parasite") && card.getSender() != null)
            addStatRow(stats, "Parasite sender:", card.getSender().getName() + " (receives hours)", "#d080e8");

        content.getChildren().add(stats);

        // ── Attached helpers ──────────────────────────────────────────────────
        if (!card.getAttachedHelpers().isEmpty()) {
            content.getChildren().add(new Separator());
            Label hdrLbl = new Label("ATTACHED HELPERS:");
            hdrLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #5a8090; -fx-font-weight: bold;");
            content.getChildren().add(hdrLbl);
            for (Card h : card.getAttachedHelpers()) {
                Label hLbl = new Label("✦ " + h.getName() + " — " + h.getDescription());
                hLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #7898c0;");
                hLbl.setWrapText(true);
                content.getChildren().add(hLbl);
            }
        }

        // ── Discard preview ───────────────────────────────────────────────────
        content.getChildren().add(new Separator());
        Label discardHdr = new Label("IF DISCARDED NOW:");
        discardHdr.setStyle("-fx-font-size: 10px; -fx-text-fill: #c08040; -fx-font-weight: bold;");
        content.getChildren().add(discardHdr);

        VBox discardPreview = new VBox(3);
        discardPreview.setStyle("-fx-background-color: rgba(40,20,5,0.40); -fx-padding: 6; -fx-background-radius: 4;");
        buildDiscardPreview(discardPreview, card, owner);
        content.getChildren().add(discardPreview);

        // ── Buttons ───────────────────────────────────────────────────────────
        boolean canDiscard = owner == state.getCurrentPlayer() && !state.isActionTakenThisTurn();
        ButtonType discardType = new ButtonType("Discard Card", ButtonBar.ButtonData.LEFT);
        ButtonType closeType   = new ButtonType("Close",        ButtonBar.ButtonData.CANCEL_CLOSE);
        if (canDiscard) dlg.getDialogPane().getButtonTypes().addAll(discardType, closeType);
        else            dlg.getDialogPane().getButtonTypes().add(closeType);

        dlg.getDialogPane().setContent(content);
        dlg.showAndWait().ifPresent(r -> { if (r == discardType) controller.discardPlayedCard(card); });
    }

    /** Builds human-readable lines describing what discarding a card now would do. */
    private void buildDiscardPreview(VBox box, Card card, Player owner) {
        int onCard = card.getHoursOnCard();
        boolean isDebt = card.getImmediateHours() < 0 && onCard > 0;
        boolean isParasite = card.getName().equals("Parasite");
        Player sender = card.getSender();
        String ownerStr = owner == state.getCurrentPlayer() ? "You" : owner.getName();

        if (onCard == 0) {
            previewRow(box, "No hours at stake — discard is free.", "#708070");
        } else if (isDebt) {
            // Debt card: remaining debt paid
            if (isParasite && sender != null) {
                previewRow(box, ownerStr + " pay remaining debt: -" + onCard + "h", "#e07060");
                previewRow(box, sender.getName() + " receives:  +" + onCard + "h", "#80d060");
            } else {
                previewRow(box, ownerStr + " pay remaining debt: -" + onCard + "h", "#e07060");
                previewRow(box, "(Hours return to pool)", "#607060");
            }
        } else {
            // Positive card: player KEEPS accumulated hours
            previewRow(box, ownerStr + " keep +" + onCard + "h already earned.", "#80d060");
            previewRow(box, "(No hours lost — card just leaves play)", "#607060");
        }

        // Check Sharing is Caring link
        state.getSharingLinks().forEach((sharingCard, target) -> {
            if (sharingCard.getOwner() == owner) {
                previewRow(box, "⚠ Sharing is Caring link to " + target.getName() + " will break.", "#e0c060");
            }
        });

        // Timely discard bonus for Professional/Risky
        if (card.getRoundsPlayed() >= card.getExpiresAfterRound() - 1 && card.getFinalHours() > 0) {
            previewRow(box, "Bonus on timely discard: +" + card.getFinalHours() + "h", "#c0a060");
        }
    }

    private void previewRow(VBox box, String text, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: " + color + ";");
        l.setWrapText(true);
        box.getChildren().add(l);
    }

    private void addStatRow(VBox box, String key, String val, String valColor) {
        HBox row = new HBox(10);
        Label k = new Label(key);
        k.setStyle("-fx-font-size: 11px; -fx-text-fill: #607060;");
        k.setMinWidth(140);
        Label v = new Label(val);
        v.setStyle("-fx-font-size: 11px; -fx-text-fill: " + valColor + ";");
        row.getChildren().addAll(k, v);
        box.getChildren().add(row);
    }

    private String hoursOnCardLabel(Card card) {
        int on = card.getHoursOnCard();
        if (on == 0) return "0h";
        boolean isDebt = card.getImmediateHours() < 0 && on > 0;
        return isDebt ? "debt: " + on + "h" : "+" + on + "h earned";
    }

    private void styleDialog(Dialog<?> dlg) {
        var ss = SetupScreen.class.getResource("/com/workgame/css/game.css");
        if (ss != null) dlg.getDialogPane().getStylesheets().add(ss.toExternalForm());
        dlg.getDialogPane().setStyle("-fx-background-color: #0f2010; -fx-text-fill: #c0e0a0;");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Full UI refresh
    // ═════════════════════════════════════════════════════════════════════════

    private Player lastSeenPlayer = null;

    private void updateUI() {
        Platform.runLater(() -> {
            if (state.getPhase() == GameState.Phase.GAME_OVER) { showGameOver(); return; }

            Player cur = state.getCurrentPlayer();
            boolean isHotseat = cur.isHuman();
            long hotseatCount = state.getPlayers().stream().filter(Player::isHuman).count();

            // Detect turn change — reset hand reveal for new hotseat human turns
            if (cur != lastSeenPlayer) {
                lastSeenPlayer = cur;
                cpuTurnInProgress = false; // new turn means the old CPU turn is fully done
                if (!isHotseat) {
                    handRevealed = true;
                } else if (hotseatCount <= 1) {
                    handRevealed = true;
                } else {
                    handRevealed = false;
                }
            }

            // Show pass screen only for hotseat human turns when there are multiple humans
            if (isHotseat && !handRevealed && !state.isCardDrawnThisTurn() && hotseatCount > 1) {
                showPassDeviceScreen(cur);
                return;
            }

            syncLabels();
            rebuildOpponentArea();
            rebuildMyPlayZone();

            Player viewer = nextHotseatHuman();
            if (isHotseat && handRevealed) {
                // Active human turn — show their hand normally
                rebuildHandTray(cur, false);
            } else if (!isHotseat && viewer != null) {
                // CPU's turn — show the next human's hand face-up (not view-only)
                // They can play helpers reactively, but can't draw/play/discard
                rebuildHandTray(viewer, false);
            } else if (viewer != null) {
                rebuildHandTray(viewer, false);
            }
            // Never clear the hand tray — always show the human's cards

            rebuildActionBar(isHotseat && handRevealed);

            System.out.println("[UI] updateUI: cur=" + cur.getName()
                    + " isHuman=" + isHotseat
                    + " inProgress=" + cpuTurnInProgress
                    + " drawn=" + state.isCardDrawnThisTurn()
                    + " acted=" + state.isActionTakenThisTurn()
                    + " pending=" + state.getPendingResolutions());

            if (!isHotseat) {
                if (!cpuTurnInProgress) {
                    // Clean start of a CPU turn — trigger normally
                    maybeTriggerCpuTurn();
                } else if (state.isCardDrawnThisTurn()
                        && !state.isActionTakenThisTurn()
                        && state.getPendingResolutions() == 0) {
                    // Drew but no action yet, no pending dialogs — free helper was used
                    // Schedule continueAction, but only once and only for this player
                    final Player retryFor = cur;
                    PauseTransition retry = new PauseTransition(Duration.millis(350));
                    retry.setOnFinished(e -> {
                        System.out.println("[CPU] continueAction check: cur="
                                + state.getCurrentPlayer().getName()
                                + " retryFor=" + retryFor.getName()
                                + " acted=" + state.isActionTakenThisTurn());
                        if (state.getCurrentPlayer() == retryFor
                                && !state.isActionTakenThisTurn()) {
                            cpuPlayer.continueAction();
                        } else {
                            System.out.println("[CPU] continueAction: stale, clearing flag");
                            cpuTurnInProgress = false;
                        }
                    });
                    retry.play();
                }
                // Otherwise: turn is in flight with pending resolutions or action taken — do nothing
            }
        });
    }

    /** Show a "Pass device to [Name]" screen for hotseat human turns. */
    private void showPassDeviceScreen(Player p) {
        // Overlay on glass pane — blocks all interaction
        glassPane.setMouseTransparent(false);

        VBox screen = new VBox(24);
        screen.setAlignment(Pos.CENTER);
        screen.setStyle(
                "-fx-background-color: rgba(5,15,8,0.97);" +
                        "-fx-background-radius: 0;");
        AnchorPane.setTopAnchor(screen, 0.0);
        AnchorPane.setBottomAnchor(screen, 0.0);
        AnchorPane.setLeftAnchor(screen, 0.0);
        AnchorPane.setRightAnchor(screen, 0.0);

        Label passLbl = new Label("PASS DEVICE TO");
        passLbl.setStyle("-fx-font-size: 14px; -fx-text-fill: #6a9060; -fx-font-weight: bold;");

        Label nameLbl = new Label(p.getName());
        nameLbl.setStyle(
                "-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #f5d060;" +
                        "-fx-effect: dropshadow(gaussian, #f5d06080, 16, 0.5, 0, 0);");

        Label hoursLbl = new Label("⏱ " + p.getHourTokens() + " hours");
        hoursLbl.setStyle("-fx-font-size: 16px; -fx-text-fill: #90e060;");

        Button readyBtn = new Button("▶  I'M READY");
        readyBtn.getStyleClass().addAll("btn", "btn-primary");
        readyBtn.setStyle("-fx-font-size: 14px; -fx-padding: 10 30 10 30;");
        readyBtn.setOnAction(e -> {
            glassPane.getChildren().remove(screen);
            glassPane.setMouseTransparent(true);
            handRevealed = true;
            // Now do the full UI rebuild with hand shown
            syncLabels();
            rebuildOpponentArea();
            rebuildMyPlayZone();
            rebuildHandTray(state.getCurrentPlayer(), false);
            rebuildActionBar(true);
        });

        screen.getChildren().addAll(passLbl, nameLbl, hoursLbl, readyBtn);
        glassPane.getChildren().add(screen);
    }

    private void syncLabels() {
        Player p = state.getCurrentPlayer();
        turnLabel.setText("TURN: " + p.getName() + "   ");
        roundLabel.setText("Round " + state.getGlobalRound() + "/" + state.getMaxRounds());
        poolLabel.setText("Pool: " + state.getHourPoolRemaining() + "h");

        // Show hours for the next hotseat human (the "viewer")
        Player viewer = nextHotseatHuman();
        if (viewer != null)
            myHoursLabel.setText("⏱ " + viewer.getName() + ": " + viewer.getHourTokens() + "h");
        else
            myHoursLabel.setText("⏱ " + p.getName() + ": " + p.getHourTokens() + "h");

        tableRoot.lookupAll("#deck-count-label").forEach(n -> {
            if (n instanceof Label l) l.setText(String.valueOf(state.getDrawPile().size()));
        });
        tableRoot.lookupAll("#discard-count-label").forEach(n -> {
            if (n instanceof Label l) l.setText("discard: " + state.getDiscardPile().size());
        });
    }

    /** The hotseat human whose hand is currently shown (current if human, else next human in order). */
    private Player nextHotseatHuman() {
        Player cur = state.getCurrentPlayer();
        if (cur.isHuman()) return cur;
        // Find next human in turn order
        List<Player> players = state.getPlayers();
        int idx = players.indexOf(cur);
        for (int i = 1; i < players.size(); i++) {
            Player p = players.get((idx + i) % players.size());
            if (p.isHuman()) return p;
        }
        return null;
    }

    private void appendLog(String msg) {
        System.out.println("[GAME] " + msg);
        Platform.runLater(() -> logArea.appendText(msg + "\n"));
    }

    /** Show defense dialog when a harmful weapon targets this human player. */
    private void onDefendOpportunity(Card weapon, Runnable resolveAnyway) {
        Platform.runLater(() -> {
            Player target = state.getCurrentPlayer(); // weapon is hitting whoever the current player is
            // Find the actual target - it's whoever has the Excused in hand that received the weapon
            // We need to find the human target - search all players
            Player actualTarget = null;
            for (Player p : state.getPlayers()) {
                if (p.isHuman() && p.getHand().stream().anyMatch(c -> c.getName().equals("Excused"))) {
                    actualTarget = p;
                    break;
                }
            }
            if (actualTarget == null) { resolveAnyway.run(); return; }
            final Player defTarget = actualTarget;

            Dialog<ButtonType> dlg = new Dialog<>();
            dlg.setTitle("Incoming Weapon!");
            dlg.setHeaderText(weapon.getName() + " is targeting you!");
            styleDialog(dlg);

            VBox content = new VBox(12);
            content.setPadding(new Insets(14));
            content.setStyle("-fx-background-color: #0f2010; -fx-min-width: 300px;");

            Label weaponLbl = new Label("⚔ " + weapon.getName());
            weaponLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #d080e8;");
            Label descLbl = new Label(weapon.getDescription());
            descLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #c0d8a8;");
            descLbl.setWrapText(true);
            Label excusedLbl = new Label("You have an Excused card — play it to block this weapon?");
            excusedLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #90c0f0;");
            excusedLbl.setWrapText(true);

            content.getChildren().addAll(weaponLbl, descLbl, new Separator(), excusedLbl);
            dlg.getDialogPane().setContent(content);

            ButtonType blockType  = new ButtonType("Play Excused (Block)", ButtonBar.ButtonData.YES);
            ButtonType acceptType = new ButtonType("Accept the weapon",     ButtonBar.ButtonData.NO);
            dlg.getDialogPane().getButtonTypes().addAll(blockType, acceptType);

            dlg.showAndWait().ifPresent(result -> {
                if (result == blockType) {
                    // Find the Excused card and block
                    Card excused = defTarget.getHand().stream()
                            .filter(c -> c.getName().equals("Excused"))
                            .findFirst().orElse(null);
                    if (excused != null) {
                        controller.weaponBlocked(weapon, defTarget, excused);
                    } else {
                        resolveAnyway.run();
                    }
                } else {
                    resolveAnyway.run();
                }
            });
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Game over
    // ═════════════════════════════════════════════════════════════════════════

    private void showGameOver() {
        VBox root = new VBox(20);
        root.getStyleClass().add("setup-root");
        root.setAlignment(Pos.CENTER);

        Label title = new Label("GAME OVER");
        title.getStyleClass().add("gameover-title");

        VBox scores = new VBox(4);
        scores.setAlignment(Pos.CENTER);
        List<Player> sorted = new ArrayList<>(state.getPlayers());
        sorted.sort((a, b) -> Integer.compare(b.getHourTokens(), a.getHourTokens()));
        int best = sorted.get(0).getHourTokens();

        for (int i = 0; i < sorted.size(); i++) {
            Player p = sorted.get(i);
            HBox row = new HBox(20);
            row.getStyleClass().add("score-row");
            if (p.getHourTokens() == best) row.getStyleClass().add("score-winner");
            row.setAlignment(Pos.CENTER);
            Label rank = new Label("#" + (i + 1)); rank.getStyleClass().add("section-label");
            Label name = new Label(p.getName());   name.getStyleClass().add("player-name-label");
            Label hrs  = new Label(p.getHourTokens() + " hours"); hrs.getStyleClass().add("hour-label");
            Region sp  = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            row.getChildren().addAll(rank, name, sp, hrs);
            scores.getChildren().add(row);
        }

        Button replay = new Button("▶  PLAY AGAIN");
        replay.getStyleClass().addAll("btn", "btn-primary");
        replay.setOnAction(e -> stage.setScene(new SetupScreen(stage, null).buildScene()));

        Button statsBtn = new Button("📊  VIEW STATS");
        statsBtn.getStyleClass().addAll("btn");
        Scene currentScene = stage.getScene();
        statsBtn.setOnAction(e -> stage.setScene(new StatsScreen(stage, currentScene).buildScene()));

        HBox btns = new HBox(12, replay, statsBtn);
        btns.setAlignment(Pos.CENTER);

        root.getChildren().addAll(title, scores, btns);
        Scene scene = new Scene(root, 600, 420);
        SetupScreen.applyStylesheet(scene);
        stage.setScene(scene);
    }
}
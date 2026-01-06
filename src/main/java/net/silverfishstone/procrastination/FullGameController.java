package net.silverfishstone.procrastination;

import net.silverfishstone.procrastination.components.GameCard;
import net.silverfishstone.procrastination.components.CardStack;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;
import net.silverfishstone.procrastination.components.PlayedCard;
import net.silverfishstone.procrastination.game.CardDefinition;
import net.silverfishstone.procrastination.game.ComputerAI;
import net.silverfishstone.procrastination.game.RoundManager;

import java.util.*;
import java.util.stream.Collectors;
/**
 * COMPLETE GAME CONTROLLER - CLEANED VERSION
 *
 * Uses only GameCard (not Card base class).
 *
 * Implements full game with:
 * - Hour card system (10 per player at start)
 * - Round-based gameplay
 * - Card expiration
 * - All card effects
 * - 3-card play limit
 * - Helper cards
 * - Rolling weapons
 * - Victory conditions
 */
public class FullGameController {

    @FXML private Pane playArea;
    @FXML private Pane cardLayer;
    @FXML private Text roundText;
    @FXML private Text phaseText;

    private final Image cardBack = new Image(getClass().getResourceAsStream("/net/silverfishstone/procrastination/textures/card_back.png"));
    private final Image hourCardImage = new Image(getClass().getResourceAsStream("/net/silverfishstone/procrastination/textures/card_back.png"));

    // Core game state
    private RoundManager roundManager;
    private int currentPlayer = 0;
    private int numPlayers = 4;
    private boolean hasDrawnThisTurn = false;
    private boolean hasPlayedThisTurn = false;
    private boolean canSkipTurn = true;
    private boolean gameOver = false;

    // Game settings
    private int gameSpeed = 1; // 0=slow (2000ms), 1=normal (800ms), 2=fast (300ms)
    private int startingHours = 100;
    private int victoryHours = -1; // -1 means last player standing

    // Card stacks
    private CardStack actionDeck;
    private CardStack hourDeck;
    private CardStack discardPile;

    // Player areas
    private List<CardStack> playerHands = new ArrayList<>();
    private List<List<CardStack>> playerSlots = new ArrayList<>();
    private List<Text> playerLabels = new ArrayList<>();
    private List<Integer> playerHourCounts = new ArrayList<>();
    private List<Text> hourCountLabels = new ArrayList<>();

    // AI
    private List<ComputerAI> aiPlayers = new ArrayList<>();

    // Drag & Drop
    private List<GameCard> draggedCards = new ArrayList<>();
    private CardStack sourceStack = null;
    private Pane dragOverlay;
    private double offsetX, offsetY;
    private CardStack highlightedStack = null;

    // Card tracking
    private Map<GameCard, PlayedCard> cardToPlayedCard = new HashMap<>();
    private Map<PlayedCard, GameCard> playedCardToCard = new HashMap<>();

    // Constants
    private Random random = new Random();
    private static final double CARD_WIDTH = 100;
    private static final double CARD_HEIGHT = 145;
    private static final double PLAYAREA_WIDTH = 1200;
    private static final double PLAYAREA_HEIGHT = 800;
    private static final int MAX_CARDS_IN_PLAY = 3;
    private static final int STARTING_ACTION_CARDS = 5;
    private static final int DEFAULT_STARTING_HOURS = 100;

    @FXML
    private void initialize() {
        if (cardLayer == null) {
            cardLayer = new Pane();
            cardLayer.setMouseTransparent(false);
            playArea.getChildren().add(cardLayer);
        }

        roundManager = new RoundManager(numPlayers);

        // Initialize game settings
        startingHours = DEFAULT_STARTING_HOURS;

        for (int i = 0; i < numPlayers; i++) {
            playerHourCounts.add(startingHours);
        }

        createGameLayout();
        createDragOverlay();

        // Setup AI players (player 0 is human)
        for (int i = 1; i < numPlayers; i++) {
            aiPlayers.add(new ComputerAI(ComputerAI.Level.EASY));
        }

        playArea.setOnDragOver(this::handleDragOver);
        playArea.setOnDragDropped(this::handleDragDropped);
        playArea.setOnMouseClicked(this::handlePlayAreaClick);

        updateTurnIndicator();
        updateHourDisplay();
    }

    // ========== GAME SETUP ==========

    private void createDragOverlay() {
        dragOverlay = new Pane();
        dragOverlay.setMouseTransparent(true);
        dragOverlay.setVisible(false);
        cardLayer.getChildren().add(dragOverlay);
    }

    private void createGameLayout() {
        double cx = PLAYAREA_WIDTH / 2;
        double cy = PLAYAREA_HEIGHT / 2;

        createCenterArea(cx, cy);
        createPlayerAreas(cx, cy);
        createUIElements();
    }

    private void createCenterArea(double cx, double cy) {
        double deckSpacing = CARD_WIDTH + 40;
        double deckY = 50;

        // Action deck (left)
        addPlaceholder(cx - deckSpacing, deckY, false, 0);
        actionDeck = new CardStack();
        actionDeck.setStackType("stock");
        actionDeck.setLayoutX(cx - deckSpacing);
        actionDeck.setLayoutY(deckY);
        actionDeck.runFunction = this::onActionDeckClicked;
        cardLayer.getChildren().add(actionDeck);
        replenishActionDeck();

        // Hour deck (right)
        addPlaceholder(cx + deckSpacing - CARD_WIDTH, deckY, false, 0);
        hourDeck = new CardStack();
        hourDeck.setStackType("stock");
        hourDeck.setLayoutX(cx + deckSpacing - CARD_WIDTH);
        hourDeck.setLayoutY(deckY);
        cardLayer.getChildren().add(hourDeck);
        replenishHourDeck();

        // Discard pile (center)
        addPlaceholder(cx - CARD_WIDTH/2, cy - CARD_HEIGHT/2, true, 0);
        discardPile = new CardStack();
        discardPile.setStackType("discard");
        discardPile.setDraggable(false);
        discardPile.setLayoutX(cx - CARD_WIDTH/2);
        discardPile.setLayoutY(cy - CARD_HEIGHT/2);
        cardLayer.getChildren().add(discardPile);
    }

    private void createPlayerAreas(double cx, double cy) {
        String[] names = {"Player 1", "Player 2", "Player 3", "Player 4"};

        for (int p = 0; p < numPlayers; p++) {
            CardStack hand = new CardStack();
            hand.setStackType("hand");
            hand.setDraggable(p == currentPlayer);

            double handX, handY, labelX, labelY, hourLabelX, hourLabelY;
            double slotsStartX, slotsStartY;
            int rotation = 0;
            String labelText = names[p] + (p == 0 ? " (You)" : " (AI)");

            List<CardStack> thisPlayerSlots = new ArrayList<>();

            switch(p) {
                case 0: // Bottom (human)
                    handX = cx;
                    handY = PLAYAREA_HEIGHT - CARD_HEIGHT - 20;
                    labelX = cx - 150;
                    labelY = PLAYAREA_HEIGHT - 20;
                    hourLabelX = cx + 50;
                    hourLabelY = PLAYAREA_HEIGHT - 20;
                    rotation = 0;

                    slotsStartX = cx - (CARD_WIDTH * 1.5) - 22.5;
                    slotsStartY = PLAYAREA_HEIGHT - CARD_HEIGHT * 2 - 40;
                    for (int i = 0; i < MAX_CARDS_IN_PLAY; i++) {
                        addPlaceholder(slotsStartX + i * (CARD_WIDTH + 15), slotsStartY, true, rotation);
                        CardStack slot = createSlot(slotsStartX + i * (CARD_WIDTH + 15), slotsStartY);
                        thisPlayerSlots.add(slot);
                    }
                    break;

                case 1: // Right
                    handX = PLAYAREA_WIDTH - CARD_WIDTH - 40;
                    handY = cy;
                    hand.setRotate(90);
                    labelX = PLAYAREA_WIDTH - 150;
                    labelY = cy + 120;
                    hourLabelX = PLAYAREA_WIDTH - 150;
                    hourLabelY = cy + 135;
                    rotation = 90;

                    slotsStartX = PLAYAREA_WIDTH - CARD_WIDTH * 2 - 60;
                    slotsStartY = cy - (CARD_HEIGHT * 1.5) - 22.5;
                    for (int i = 0; i < MAX_CARDS_IN_PLAY; i++) {
                        addPlaceholder(slotsStartX, slotsStartY + i * (CARD_HEIGHT + 15), true, rotation);
                        CardStack slot = createSlot(slotsStartX, slotsStartY + i * (CARD_HEIGHT + 15));
                        thisPlayerSlots.add(slot);
                    }
                    break;

                case 2: // Top
                    handX = cx;
                    handY = 20;
                    hand.setRotate(180);
                    labelX = cx - 150;
                    labelY = 15;
                    hourLabelX = cx + 50;
                    hourLabelY = 15;
                    rotation = 180;

                    slotsStartX = cx - (CARD_WIDTH * 1.5) - 22.5;
                    slotsStartY = CARD_HEIGHT + 40;
                    for (int i = 0; i < MAX_CARDS_IN_PLAY; i++) {
                        addPlaceholder(slotsStartX + i * (CARD_WIDTH + 15), slotsStartY, true, rotation);
                        CardStack slot = createSlot(slotsStartX + i * (CARD_WIDTH + 15), slotsStartY);
                        thisPlayerSlots.add(slot);
                    }
                    break;

                case 3: // Left
                default:
                    handX = 40;
                    handY = cy;
                    hand.setRotate(270);
                    labelX = 20;
                    labelY = cy + 120;
                    hourLabelX = 20;
                    hourLabelY = cy + 135;
                    rotation = 270;

                    slotsStartX = CARD_WIDTH + 60;
                    slotsStartY = cy - (CARD_HEIGHT * 1.5) - 22.5;
                    for (int i = 0; i < MAX_CARDS_IN_PLAY; i++) {
                        addPlaceholder(slotsStartX, slotsStartY + i * (CARD_HEIGHT + 15), true, rotation);
                        CardStack slot = createSlot(slotsStartX, slotsStartY + i * (CARD_HEIGHT + 15));
                        thisPlayerSlots.add(slot);
                    }
                    break;
            }

            hand.setLayoutX(handX);
            hand.setLayoutY(handY);
            cardLayer.getChildren().add(hand);
            playerHands.add(hand);
            playerSlots.add(thisPlayerSlots);

            // Deal starting cards
            for (int i = 0; i < STARTING_ACTION_CARDS; i++) {
                GameCard actionCard = createRandomActionCard();
                hand.addCard(actionCard);
                if (p == 0) {
                    setupCardHandlers(actionCard);
                }
            }

            // Player label
            Text label = new Text(labelText);
            label.setFill(Color.WHITE);
            label.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
            label.setLayoutX(labelX);
            label.setLayoutY(labelY);
            playArea.getChildren().add(label);
            playerLabels.add(label);

            // Hour count label
            Text hourLabel = new Text("Hours: " + startingHours);
            hourLabel.setFill(Color.YELLOW);
            hourLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");
            hourLabel.setLayoutX(hourLabelX);
            hourLabel.setLayoutY(hourLabelY);
            playArea.getChildren().add(hourLabel);
            hourCountLabels.add(hourLabel);
        }
    }

    private CardStack createSlot(double x, double y) {
        CardStack slot = new CardStack();
        slot.setStackType("slot");
        slot.setLayoutX(x);
        slot.setLayoutY(y);
        slot.setDraggable(false);
        cardLayer.getChildren().add(slot);
        return slot;
    }

    private void createUIElements() {
        roundText = new Text("Round: 0");
        roundText.setFill(Color.WHITE);
        roundText.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");
        roundText.setLayoutX(PLAYAREA_WIDTH / 2 - 50);
        roundText.setLayoutY(30);
        playArea.getChildren().add(roundText);

        phaseText = new Text("Draw Phase");
        phaseText.setFill(Color.LIGHTGREEN);
        phaseText.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        phaseText.setLayoutX(PLAYAREA_WIDTH / 2 - 50);
        phaseText.setLayoutY(50);
        playArea.getChildren().add(phaseText);
    }

    private void addPlaceholder(double x, double y, boolean canHighlight, int rotation) {
        Rectangle r = new Rectangle(CARD_WIDTH, CARD_HEIGHT);
        r.setFill(Color.TRANSPARENT);
        r.setStroke(Color.rgb(100, 100, 100, 0.5));
        r.setStrokeWidth(2);
        r.getStrokeDashArray().addAll(5.0, 5.0);
        r.setArcWidth(10);
        r.setArcHeight(10);
        r.setLayoutX(x);
        r.setLayoutY(y);

        if (rotation != 0) {
            r.setRotate(rotation);
        }

        if (canHighlight) {
            r.setUserData("highlightable");
        }

        playArea.getChildren().add(r);
    }

    // ========== CARD CREATION ==========

    private GameCard createRandomActionCard() {
        double rand = random.nextDouble();

        CardDefinition def;
        if (rand < 0.4) {
            // 40% play cards
            CardDefinition[] plays = {
                    CardDefinition.ON_THE_CLOCK,
                    CardDefinition.PROFESSIONAL,
                    CardDefinition.RISKY,
                    CardDefinition.SHARING_IS_CARING,
                    CardDefinition.UNPREDICTABLE
            };
            def = plays[random.nextInt(plays.length)];
        } else if (rand < 0.7) {
            // 30% weapons
            CardDefinition[] weapons = {
                    CardDefinition.TARDY,
                    CardDefinition.DEADLINE,
                    CardDefinition.STOCK_MARKET,
                    CardDefinition.SCAMMER,
                    CardDefinition.QUIT,
                    CardDefinition.PARASITE,
                    CardDefinition.DOWNSIZING,
                    CardDefinition.FOREIGN_EXCHANGE
            };
            def = weapons[random.nextInt(weapons.length)];
        } else if (rand < 0.9) {
            // 20% helpers
            CardDefinition[] helpers = {
                    CardDefinition.EXCUSED,
                    CardDefinition.EXTENSION,
                    CardDefinition.NEPOTISM,
                    CardDefinition.NEWBIE
            };
            def = helpers[random.nextInt(helpers.length)];
        } else {
            // 10% alerts
            CardDefinition[] alerts = {
                    CardDefinition.AMNESIA,
                    CardDefinition.FIRED,
                    CardDefinition.PERFORMANCE_REVIEW,
                    CardDefinition.RECESSION
            };
            def = alerts[random.nextInt(alerts.length)];
        }

        GameCard card = new GameCard(def, cardBack);
        card.flip();
        return card;
    }

    private GameCard createHourCard() {
        GameCard card = new GameCard(CardDefinition.ON_THE_CLOCK, hourCardImage);
        return card; // Face down
    }

    private void replenishActionDeck() {
        while (actionDeck.getChildren().size() < 10) {
            GameCard back = createRandomActionCard();
            back.flip(); // Face down
            actionDeck.addCard(back);
        }
    }

    private void replenishHourDeck() {
        while (hourDeck.getChildren().size() < 20) {
            GameCard hourCard = createHourCard();
            hourDeck.addCard(hourCard);
        }
    }

    // ========== TURN MANAGEMENT ==========

    private void updateTurnIndicator() {
        for (int i = 0; i < numPlayers; i++) {
            Text label = playerLabels.get(i);
            CardStack hand = playerHands.get(i);

            if (i == currentPlayer) {
                label.setFill(Color.YELLOW);
                label.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");
                hand.setDraggable(true);
            } else {
                label.setFill(Color.WHITE);
                label.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
                hand.setDraggable(false);
            }
        }

        roundText.setText("Round: " + roundManager.getCurrentRound());

        if (!hasDrawnThisTurn) {
            phaseText.setText("Draw Phase - Click deck");
            phaseText.setFill(Color.LIGHTGREEN);
        } else if (!hasPlayedThisTurn) {
            phaseText.setText("Play Phase - Play/discard card");
            phaseText.setFill(Color.LIGHTYELLOW);
        } else {
            phaseText.setText("Turn complete");
            phaseText.setFill(Color.LIGHTGRAY);
        }

        System.out.println("\n===== Player " + (currentPlayer + 1) + "'s Turn =====");
        System.out.println("Draw: " + (!hasDrawnThisTurn ? "NEEDED" : "DONE"));
        System.out.println("Play: " + (!hasPlayedThisTurn ? "NEEDED" : "DONE"));
    }

    private void advanceTurn() {
        if (gameOver) return; // Don't advance if game is over

        currentPlayer = (currentPlayer + 1) % numPlayers;
        hasDrawnThisTurn = false;
        hasPlayedThisTurn = false;
        canSkipTurn = true;

        // Check if round is complete
        if (currentPlayer == 0) {
            advanceRound();
        }

        updateTurnIndicator();

        if (gameOver) return; // Check again after round advancement

        checkVictoryConditions();

        if (gameOver) return; // Check again after victory check

        // AI turn
        if (currentPlayer > 0) {
            executeAITurn();
        }
    }

    private void advanceRound() {
        RoundManager.RoundReport report = roundManager.advanceRound();

        // Update all visual cards in play
        for (int p = 0; p < numPlayers; p++) {
            List<PlayedCard> cardsInPlay = roundManager.getCardsInPlay(p);

            for (PlayedCard playedCard : cardsInPlay) {
                GameCard visualCard = playedCardToCard.get(playedCard);
                if (visualCard != null) {
                    visualCard.updateState(
                            playedCard.getCurrentHourValue(),
                            playedCard.getRoundsInPlay(),
                            playedCard.isProtectedByNepotism(),
                            playedCard.hasExpired()
                    );
                }
            }

            // Handle hour changes
            RoundManager.PlayerState state = roundManager.getPlayerState(p);
            if (state != null) {
                int hoursGained = state.getTotalHours();
                if (hoursGained != 0) {
                    adjustPlayerHours(p, hoursGained);
                    state.setHours(0);
                }
            }
        }

        updateHourDisplay();
    }

    private void skipTurn() {
        if (!canSkipTurn) {
            showMessage("You cannot skip this turn!");
            return;
        }

        System.out.println("Player " + (currentPlayer + 1) + " skipped turn");
        hasDrawnThisTurn = true;
        hasPlayedThisTurn = true;

        PauseTransition delay = new PauseTransition(Duration.millis(300));
        delay.setOnFinished(e -> advanceTurn());
        delay.play();
    }

    // ========== PLAYER ACTIONS ==========

    private void onActionDeckClicked() {
        if (currentPlayer != 0) return;
        if (hasDrawnThisTurn) {
            showMessage("You've already drawn this turn!");
            return;
        }
        drawActionCard(0);
    }

    public void drawActionCard(int playerIndex) {
        if (gameOver) return; // Don't draw if game is over

        CardStack targetHand = playerHands.get(playerIndex);
        GameCard newCard = createRandomActionCard();

        // Check for alert card
        if (newCard.getDefinition().isAlertCard()) {
            handleAlertCard(newCard.getDefinition(), playerIndex);
            replenishActionDeck();
            drawActionCard(playerIndex);
            return;
        }

        animateCardDraw(actionDeck, targetHand, newCard, () -> {
            if (playerIndex == 0) {
                setupCardHandlers(newCard);
            }
            System.out.println("Player " + (playerIndex + 1) + " drew " +
                    newCard.getDefinition().getDisplayName());
        });

        replenishActionDeck();

        if (playerIndex == currentPlayer) {
            hasDrawnThisTurn = true;
            updateTurnIndicator();
        }
    }

    public boolean playCardToSlot(GameCard card, int playerIndex, int targetSlotIndex) {
        CardDefinition def = card.getDefinition();
        CardStack hand = playerHands.get(playerIndex);
        CardStack targetSlot = playerSlots.get(playerIndex).get(targetSlotIndex);

        // Check 3-card limit
        if (roundManager.getCardCountInPlay(playerIndex) >= MAX_CARDS_IN_PLAY) {
            showMessage("Cannot play more than " + MAX_CARDS_IN_PLAY + " cards!");
            return false;
        }

        // Validate play
        if (!canPlayCardToSlot(def, playerIndex, targetSlot)) {
            return false;
        }

        // Create PlayedCard wrapper
        PlayedCard playedCard = new PlayedCard(card, def, playerIndex);
        cardToPlayedCard.put(card, playedCard);
        playedCardToCard.put(playedCard, card);

        // Handle special card setup
        if (def.hasSharingMechanic()) {
            selectSharingTarget(playedCard, playerIndex);
        }

        // Execute play
        hand.removeCard(card);
        targetSlot.addCard(card);
        roundManager.addCardToPlay(playerIndex, playedCard);

        // Update visual state
        card.updateState(
                playedCard.getCurrentHourValue(),
                playedCard.getRoundsInPlay(),
                false,
                false
        );

        // Apply immediate hour changes
        if (def.getImmediateHours() != 0) {
            adjustPlayerHours(playerIndex, def.getImmediateHours());
        }

        System.out.println("Player " + (playerIndex + 1) + " played " +
                def.getDisplayName() + " to slot " + (targetSlotIndex + 1));

        if (playerIndex == currentPlayer) {
            hasPlayedThisTurn = true;
            updateTurnIndicator();

            // Only auto-advance for human player (player 0)
            if (playerIndex == 0 && hasDrawnThisTurn && hasPlayedThisTurn) {
                PauseTransition delay = new PauseTransition(Duration.millis(500));
                delay.setOnFinished(e -> advanceTurn());
                delay.play();
            }
        }

        return true;
    }

    public boolean playWeaponOnOpponent(GameCard card, int playerIndex, int targetPlayerIndex, int targetSlotIndex) {
        CardDefinition def = card.getDefinition();
        if (!def.isWeaponCard()) return false;

        CardStack hand = playerHands.get(playerIndex);

        // Handle immediate effect weapons
        if (!def.isPlayWeapon()) {
            hand.removeCard(card);
            discardPile.addCard(card);

            executeImmediateWeaponEffect(def, playerIndex, targetPlayerIndex, targetSlotIndex);

            if (playerIndex == currentPlayer) {
                hasPlayedThisTurn = true;
                updateTurnIndicator();

                // Only auto-advance for human player (player 0)
                if (playerIndex == 0 && hasDrawnThisTurn && hasPlayedThisTurn) {
                    PauseTransition delay = new PauseTransition(Duration.millis(500));
                    delay.setOnFinished(e -> advanceTurn());
                    delay.play();
                }
            }

            return true;
        }

        // Play weapon (goes in target's slot)
        CardStack targetSlot = playerSlots.get(targetPlayerIndex).get(targetSlotIndex);

        // Check 3-card limit for target
        if (roundManager.getCardCountInPlay(targetPlayerIndex) >= MAX_CARDS_IN_PLAY) {
            forceDiscardForWeapon(targetPlayerIndex, card, playerIndex, targetSlotIndex);
            return true;
        }

        PlayedCard playedCard = new PlayedCard(card, def, targetPlayerIndex);
        if (def.hasParasiteMechanic()) {
            playedCard.setAttackerPlayerIndex(playerIndex);
        }

        cardToPlayedCard.put(card, playedCard);
        playedCardToCard.put(playedCard, card);

        hand.removeCard(card);
        targetSlot.addCard(card);
        roundManager.addCardToPlay(targetPlayerIndex, playedCard);

        // Update visual
        card.updateState(
                playedCard.getCurrentHourValue(),
                playedCard.getRoundsInPlay(),
                false,
                false
        );

        // Apply immediate hours
        if (def.getImmediateHours() != 0) {
            adjustPlayerHours(targetPlayerIndex, def.getImmediateHours());

            // Parasite: attacker gains hours
            if (def.hasParasiteMechanic()) {
                adjustPlayerHours(playerIndex, -def.getImmediateHours());
            }
        }

        System.out.println("Player " + (playerIndex + 1) + " weaponed Player " +
                (targetPlayerIndex + 1) + " with " + def.getDisplayName());

        if (playerIndex == currentPlayer) {
            hasPlayedThisTurn = true;
            updateTurnIndicator();

            // Only auto-advance for human player (player 0)
            if (playerIndex == 0 && hasDrawnThisTurn && hasPlayedThisTurn) {
                PauseTransition delay = new PauseTransition(Duration.millis(500));
                delay.setOnFinished(e -> advanceTurn());
                delay.play();
            }
        }

        return true;
    }

    public void discardCard(GameCard card, int playerIndex) {
        CardStack hand = playerHands.get(playerIndex);
        hand.removeCard(card);
        discardPile.addCard(card);

        System.out.println("Player " + (playerIndex + 1) + " discarded " +
                card.getDefinition().getDisplayName());

        if (playerIndex == currentPlayer) {
            hasPlayedThisTurn = true;
            updateTurnIndicator();

            // Only auto-advance for human player (player 0)
            if (playerIndex == 0 && hasDrawnThisTurn && hasPlayedThisTurn) {
                PauseTransition delay = new PauseTransition(Duration.millis(500));
                delay.setOnFinished(e -> advanceTurn());
                delay.play();
            }
        }
    }

    public void discardPlayedCard(GameCard card, int playerIndex) {
        PlayedCard playedCard = cardToPlayedCard.get(card);
        if (playedCard == null) return;

        // Get final hour value
        int finalHours = playedCard.getFinalHourValue();
        adjustPlayerHours(playerIndex, finalHours);

        // Remove from play
        for (CardStack slot : playerSlots.get(playerIndex)) {
            if (slot.getChildren().contains(card)) {
                slot.removeCard(card);
                break;
            }
        }

        discardPile.addCard(card);
        roundManager.removeCardFromPlay(playerIndex, playedCard);

        cardToPlayedCard.remove(card);
        playedCardToCard.remove(playedCard);

        // Handle rolling weapons
        if (playedCard.getDefinition().isRollingWeapon()) {
            int nextPlayer = (playerIndex + 1) % numPlayers;
            System.out.println("Rolling weapon passes to Player " + (nextPlayer + 1));
            GameCard newWeapon = createSpecificCard(playedCard.getDefinition());
            playWeaponOnOpponent(newWeapon, playerIndex, nextPlayer, 0);
        }

        System.out.println("Player " + (playerIndex + 1) + " discarded played card: " +
                playedCard.getDefinition().getDisplayName() +
                " (final hours: " + finalHours + ")");

        if (playerIndex == currentPlayer) {
            hasPlayedThisTurn = true;
            updateTurnIndicator();

            // Only auto-advance for human player (player 0)
            if (playerIndex == 0 && hasDrawnThisTurn && hasPlayedThisTurn) {
                PauseTransition delay = new PauseTransition(Duration.millis(500));
                delay.setOnFinished(e -> advanceTurn());
                delay.play();
            }
        }
    }

    // ========== CARD EFFECTS ==========

    private void executeImmediateWeaponEffect(CardDefinition weapon, int attackerIndex,
                                              int targetIndex, int targetSlotIndex) {
        switch (weapon) {
            case TARDY -> {
                List<PlayedCard> targetCards = roundManager.getCardsInPlay(targetIndex);
                if (targetSlotIndex < targetCards.size()) {
                    PlayedCard targetCard = targetCards.get(targetSlotIndex);
                    targetCard.addRound();
                    System.out.println("TARDY: Added 1 round to " +
                            targetCard.getDefinition().getDisplayName());

                    GameCard visual = playedCardToCard.get(targetCard);
                    if (visual != null) {
                        visual.updateState(targetCard.getCurrentHourValue(),
                                targetCard.getRoundsInPlay(),
                                targetCard.isProtectedByNepotism(),
                                targetCard.hasExpired());
                    }
                }
            }

            case DEADLINE -> {
                List<PlayedCard> targetCards = roundManager.getCardsInPlay(targetIndex);
                if (targetSlotIndex < targetCards.size()) {
                    PlayedCard targetCard = targetCards.get(targetSlotIndex);
                    targetCard.forceExpire();

                    int finalHours = targetCard.getFinalHourValue();
                    adjustPlayerHours(targetIndex, finalHours);

                    GameCard visual = playedCardToCard.get(targetCard);
                    for (CardStack slot : playerSlots.get(targetIndex)) {
                        if (slot.getChildren().contains(visual)) {
                            slot.removeCard(visual);
                            break;
                        }
                    }
                    discardPile.addCard(visual);
                    roundManager.removeCardFromPlay(targetIndex, targetCard);

                    System.out.println("DEADLINE: Expired " + targetCard.getDefinition().getDisplayName());
                }
            }

            case SCAMMER -> {
                if (playerHourCounts.get(targetIndex) > 0) {
                    adjustPlayerHours(targetIndex, -1);
                    adjustPlayerHours(attackerIndex, 1);
                    System.out.println("SCAMMER: Stole 1 hour from Player " + (targetIndex + 1));
                }
            }

            case QUIT -> {
                CardStack targetHand = playerHands.get(targetIndex);
                List<GameCard> handCards = targetHand.getChildren().stream()
                        .filter(n -> n instanceof GameCard)
                        .map(n -> (GameCard) n)
                        .collect(Collectors.toList());

                if (!handCards.isEmpty()) {
                    GameCard randomCard = handCards.get(random.nextInt(handCards.size()));
                    targetHand.removeCard(randomCard);
                    discardPile.addCard(randomCard);
                    System.out.println("QUIT: Player " + (targetIndex + 1) + " discarded random card");
                }
            }

            case FOREIGN_EXCHANGE -> {
                CardStack attackerHand = playerHands.get(attackerIndex);
                CardStack targetHand = playerHands.get(targetIndex);

                List<GameCard> attackerCards = attackerHand.getChildren().stream()
                        .filter(n -> n instanceof GameCard).map(n -> (GameCard) n).collect(Collectors.toList());
                List<GameCard> targetCards = targetHand.getChildren().stream()
                        .filter(n -> n instanceof GameCard).map(n -> (GameCard) n).collect(Collectors.toList());

                if (!attackerCards.isEmpty() && !targetCards.isEmpty()) {
                    GameCard attackerCard = attackerCards.get(random.nextInt(attackerCards.size()));
                    GameCard targetCard = targetCards.get(random.nextInt(targetCards.size()));

                    attackerHand.removeCard(attackerCard);
                    targetHand.removeCard(targetCard);
                    attackerHand.addCard(targetCard);
                    targetHand.addCard(attackerCard);

                    System.out.println("FOREIGN EXCHANGE: Cards traded!");
                }
            }
        }
    }

    private void handleAlertCard(CardDefinition alert, int playerIndex) {
        System.out.println("\n!!! ALERT: " + alert.getDisplayName() + " !!!");

        switch (alert) {
            case AMNESIA -> roundManager.resetAllCards();
            case FIRED -> roundManager.expireAllCardsForPlayer(playerIndex);
            case RECESSION -> roundManager.expireAllCards();
            case PERFORMANCE_REVIEW -> {
                CardStack hand = playerHands.get(playerIndex);
                List<GameCard> handCards = new ArrayList<>(hand.getChildren().stream()
                        .filter(n -> n instanceof GameCard)
                        .map(n -> (GameCard) n)
                        .toList());

                for (GameCard card : handCards) {
                    hand.removeCard(card);
                    discardPile.addCard(card);
                }

                for (int i = 0; i < STARTING_ACTION_CARDS; i++) {
                    GameCard newCard = createRandomActionCard();
                    hand.addCard(newCard);
                    if (playerIndex == 0) {
                        setupCardHandlers(newCard);
                    }
                }

                System.out.println("PERFORMANCE REVIEW: Player " + (playerIndex + 1) +
                        " discarded and redrew hand");
            }
        }
    }

    // ========== HELPER CARDS ==========

    private void useHelperCard(GameCard card, int playerIndex) {
        CardDefinition def = card.getDefinition();
        if (!def.isHelperCard()) return;

        CardStack hand = playerHands.get(playerIndex);

        switch (def) {
            case EXCUSED -> {
                showMessage("Excused: Select a weapon to deflect");
            }

            case EXTENSION -> {
                selectCardForExtension(playerIndex);
            }

            case NEPOTISM -> {
                selectCardForNepotism(playerIndex);
            }

            case NEWBIE -> {
                // Remove the Newbie card first to avoid it being in the discard list
                hand.removeCard(card);

                // Now discard all other cards
                List<GameCard> handCards = new ArrayList<>(hand.getChildren().stream()
                        .filter(n -> n instanceof GameCard)
                        .map(n -> (GameCard) n)
                        .toList());

                for (GameCard c : handCards) {
                    hand.removeCard(c);
                    discardPile.addCard(c);
                }

                // Draw new cards
                for (int i = 0; i < STARTING_ACTION_CARDS; i++) {
                    GameCard newCard = createRandomActionCard();
                    hand.addCard(newCard);
                    if (playerIndex == 0) {
                        setupCardHandlers(newCard);
                    }
                }

                // Discard the Newbie card last
                discardPile.addCard(card);

                System.out.println("NEWBIE: Player " + (playerIndex + 1) + " discarded and redrew");
                return; // Early return since we already discarded the card
            }
        }

        // For all other helper cards, discard after use
        hand.removeCard(card);
        discardPile.addCard(card);
    }

    private void selectCardForExtension(int playerIndex) {
        List<PlayedCard> cards = roundManager.getCardsInPlay(playerIndex);
        if (cards.isEmpty()) {
            showMessage("No cards to extend!");
            return;
        }

        PlayedCard card = cards.get(0);
        card.extendExpiration(5);
        System.out.println("EXTENSION: Extended " + card.getDefinition().getDisplayName() + " by 5 rounds");

        GameCard visual = playedCardToCard.get(card);
        if (visual != null) {
            visual.updateState(card.getCurrentHourValue(), card.getRoundsInPlay(),
                    card.isProtectedByNepotism(), card.hasExpired());
        }
    }

    private void selectCardForNepotism(int playerIndex) {
        List<PlayedCard> cards = roundManager.getCardsInPlay(playerIndex);
        if (cards.isEmpty()) {
            showMessage("No cards to protect!");
            return;
        }

        PlayedCard card = cards.get(0);
        card.setProtectedByNepotism(true);
        System.out.println("NEPOTISM: Protected " + card.getDefinition().getDisplayName());

        GameCard visual = playedCardToCard.get(card);
        if (visual != null) {
            visual.updateState(card.getCurrentHourValue(), card.getRoundsInPlay(),
                    true, card.hasExpired());
        }
    }

    private void selectSharingTarget(PlayedCard card, int playerIndex) {
        int targetPlayer = (playerIndex + 1) % numPlayers;
        card.setLinkedPlayerIndex(targetPlayer);
        System.out.println("SHARING: Linked to Player " + (targetPlayer + 1));
    }

    private void forceDiscardForWeapon(int targetPlayer, GameCard weapon, int attackerIndex, int slotIndex) {
        List<PlayedCard> cards = roundManager.getCardsInPlay(targetPlayer);
        if (cards.isEmpty()) return;

        PlayedCard toDiscard = cards.get(0);
        GameCard visual = playedCardToCard.get(toDiscard);

        discardPlayedCard(visual, targetPlayer);

        System.out.println("Player " + (targetPlayer + 1) + " forced to discard for weapon");

        playWeaponOnOpponent(weapon, attackerIndex, targetPlayer, slotIndex);
    }

    // ========== AI LOGIC ==========

    private void executeAITurn() {
        if (gameOver) return; // Don't execute AI turn if game is over

        ComputerAI ai = aiPlayers.get(currentPlayer - 1);

        // Speed settings: 0=slow (2000ms), 1=normal (800ms), 2=fast (300ms)
        int drawDelay = gameSpeed == 0 ? 2000 : (gameSpeed == 1 ? 800 : 300);
        int playDelay = gameSpeed == 0 ? 2000 : (gameSpeed == 1 ? 1000 : 400);
        int nextDelay = gameSpeed == 0 ? 1000 : (gameSpeed == 1 ? 500 : 200);

        PauseTransition drawPause = new PauseTransition(Duration.millis(drawDelay));
        drawPause.setOnFinished(e -> {
            if (gameOver) return;
            aiDrawCard(ai);

            PauseTransition playPause = new PauseTransition(Duration.millis(playDelay));
            playPause.setOnFinished(e2 -> {
                if (gameOver) return;
                aiPlayCard(ai);

                PauseTransition nextPause = new PauseTransition(Duration.millis(nextDelay));
                nextPause.setOnFinished(e3 -> {
                    if (gameOver) return;
                    advanceTurn();
                });
                nextPause.play();
            });
            playPause.play();
        });
        drawPause.play();
    }

    private void aiDrawCard(ComputerAI ai) {
        drawActionCard(currentPlayer);
    }

    private void aiPlayCard(ComputerAI ai) {
        if (gameOver) return;

        CardStack hand = playerHands.get(currentPlayer);
        List<GameCard> cardsInHand = hand.getChildren().stream()
                .filter(n -> n instanceof GameCard)
                .map(n -> (GameCard) n)
                .toList();

        if (cardsInHand.isEmpty()) {
            skipTurn();
            return;
        }

        // AI Strategy: Prioritize weapons on opponents, then play cards for self

        // 1. Try to play immediate weapons on the player with most hours
        for (GameCard card : cardsInHand) {
            CardDefinition def = card.getDefinition();

            if (def.isWeaponCard() && !def.isPlayWeapon()) {
                // Target player with most hours (biggest threat)
                int targetPlayer = findPlayerWithMostHours(currentPlayer);
                playWeaponOnOpponent(card, currentPlayer, targetPlayer, 0);
                return;
            }
        }

        // 2. Try to play weapon cards on opponents
        for (GameCard card : cardsInHand) {
            CardDefinition def = card.getDefinition();

            if (def.isWeaponCard() && def.isPlayWeapon()) {
                // Target player with most hours
                int targetPlayer = findPlayerWithMostHours(currentPlayer);

                if (roundManager.getCardCountInPlay(targetPlayer) < MAX_CARDS_IN_PLAY) {
                    playWeaponOnOpponent(card, currentPlayer, targetPlayer, 0);
                    return;
                }
            }
        }

        // 3. Try to play beneficial cards for self
        for (GameCard card : cardsInHand) {
            CardDefinition def = card.getDefinition();

            if (def.isPlayCard()) {
                for (int slot = 0; slot < MAX_CARDS_IN_PLAY; slot++) {
                    if (roundManager.getCardCountInPlay(currentPlayer) < MAX_CARDS_IN_PLAY) {
                        if (playCardToSlot(card, currentPlayer, slot)) {
                            return;
                        }
                    }
                }
            }
        }

        // 4. Try to use helper cards
        for (GameCard card : cardsInHand) {
            CardDefinition def = card.getDefinition();

            if (def.isHelperCard()) {
                useHelperCard(card, currentPlayer);
                hasPlayedThisTurn = true;
                return;
            }
        }

        // 5. No valid play - discard worst card
        GameCard worstCard = findWorstCard(cardsInHand);
        discardCard(worstCard, currentPlayer);
    }

    private int findPlayerWithMostHours(int excludePlayer) {
        int maxHours = -1;
        int targetPlayer = (excludePlayer + 1) % numPlayers;

        for (int i = 0; i < numPlayers; i++) {
            if (i == excludePlayer) continue;
            if (playerHourCounts.get(i) > maxHours) {
                maxHours = playerHourCounts.get(i);
                targetPlayer = i;
            }
        }

        return targetPlayer;
    }

    private GameCard findWorstCard(List<GameCard> cards) {
        // Prefer discarding low-value play cards or alerts
        for (GameCard card : cards) {
            if (card.getDefinition().isAlertCard()) {
                return card; // Alerts might hurt us, discard them
            }
        }

        // Otherwise discard first card
        return cards.get(0);
    }

    // ========== HOUR MANAGEMENT ==========

    private void adjustPlayerHours(int playerIndex, int amount) {
        int current = playerHourCounts.get(playerIndex);
        int newAmount = Math.max(0, current + amount);
        playerHourCounts.set(playerIndex, newAmount);

        System.out.println("Player " + (playerIndex + 1) + " hours: " +
                current + " -> " + newAmount + " (" +
                (amount >= 0 ? "+" : "") + amount + ")");
    }

    private void updateHourDisplay() {
        for (int i = 0; i < numPlayers; i++) {
            Text label = hourCountLabels.get(i);
            label.setText("Hours: " + playerHourCounts.get(i));
        }
    }

    // ========== VICTORY CONDITIONS ==========

    private void checkVictoryConditions() {
        // Check if any player ran out of hours
        int playersWithHours = 0;
        int lastPlayerStanding = -1;

        for (int i = 0; i < numPlayers; i++) {
            int hours = playerHourCounts.get(i);

            if (hours > 0) {
                playersWithHours++;
                lastPlayerStanding = i;
            } else if (hours <= 0) {
                showMessage("Player " + (i + 1) + " ran out of hours!");
            }
        }

        // Victory if only one player has hours left
        if (playersWithHours == 1) {
            declareWinner(lastPlayerStanding);
            return;
        }

        // Optional: victory by reaching hour goal (if set)
        if (victoryHours > 0) {
            for (int i = 0; i < numPlayers; i++) {
                if (playerHourCounts.get(i) >= victoryHours) {
                    declareWinner(i);
                    return;
                }
            }
        }
    }

    private void declareWinner(int playerIndex) {
        gameOver = true; // Stop all game actions

        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Game Over!");
            alert.setHeaderText("Player " + (playerIndex + 1) + " Wins!");
            alert.setContentText("Final hours: " + playerHourCounts.get(playerIndex) +
                    "\n\nWould you like to play again?");

            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    onNewGame();
                }
            });
        });
    }

    // ========== VALIDATION ==========

    private boolean canPlayCardToSlot(CardDefinition def, int playerIndex, CardStack slot) {
        List<CardStack> playerSlotsForPlayer = playerSlots.get(playerIndex);
        return playerSlotsForPlayer.contains(slot);
    }

    // ========== DRAG & DROP ==========

    private void setupCardHandlers(GameCard card) {
        card.setOnMousePressed(this::startDrag);
        card.setOnDragDetected(this::beginFullDrag);

        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                if (cardToPlayedCard.containsKey(card)) {
                    discardPlayedCard(card, currentPlayer);
                }
            }
        });
    }

    private void startDrag(MouseEvent event) {
        if (currentPlayer != 0) return;
        if (!hasDrawnThisTurn) {
            showMessage("Draw a card first!");
            return;
        }
        if (hasPlayedThisTurn) {
            showMessage("Already played this turn!");
            return;
        }

        GameCard clickedCard = (GameCard) event.getSource();
        CardStack parentStack = (CardStack) clickedCard.getParent();

        if (parentStack == null || !parentStack.isDraggable()) {
            event.consume();
            return;
        }

        draggedCards = List.of(clickedCard);
        sourceStack = parentStack;

        Point2D mouseInScene = new Point2D(event.getSceneX(), event.getSceneY());
        Point2D cardInScene = clickedCard.localToScene(0, 0);

        offsetX = mouseInScene.getX() - cardInScene.getX();
        offsetY = mouseInScene.getY() - cardInScene.getY();

        event.consume();
    }

    private void beginFullDrag(MouseEvent event) {
        if (draggedCards.isEmpty()) return;

        dragOverlay.toFront();
        dragOverlay.getChildren().setAll(draggedCards);
        dragOverlay.setVisible(true);

        GameCard card = draggedCards.get(0);
        card.setLayoutY(0);
        card.setDragging(true);

        updateDragOverlay(event.getSceneX(), event.getSceneY());

        Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
        ClipboardContent content = new ClipboardContent();
        content.putString("card-drag");
        db.setContent(content);

        event.consume();
    }

    private void updateDragOverlay(double sceneX, double sceneY) {
        dragOverlay.setLayoutX(sceneX - offsetX);
        dragOverlay.setLayoutY(sceneY - offsetY);
    }

    private void handleDragOver(DragEvent event) {
        if (!draggedCards.isEmpty()) {
            event.acceptTransferModes(TransferMode.MOVE);
            updateDragOverlay(event.getSceneX(), event.getSceneY());
            dragOverlay.toFront();

            Point2D dropPoint = new Point2D(event.getSceneX(), event.getSceneY());
            CardStack targetStack = findValidDropTarget(dropPoint);
            highlightStack(targetStack);
        }
        event.consume();
    }

    private void handleDragDropped(DragEvent event) {
        if (draggedCards.isEmpty()) return;

        Point2D dropPoint = new Point2D(event.getSceneX(), event.getSceneY());
        CardStack target = findValidDropTarget(dropPoint);

        GameCard card = draggedCards.get(0);

        if (target != null && target != sourceStack) {
            if (target == discardPile) {
                sourceStack.removeCard(card);
                discardCard(card, currentPlayer);
            } else {
                for (int s = 0; s < MAX_CARDS_IN_PLAY; s++) {
                    if (playerSlots.get(currentPlayer).get(s) == target) {
                        sourceStack.removeCard(card);
                        playCardToSlot(card, currentPlayer, s);
                        break;
                    }
                }
            }
        } else {
            sourceStack.addCard(card);
        }

        card.setDragging(false);
        dragOverlay.getChildren().clear();
        dragOverlay.setVisible(false);
        highlightStack(null);

        draggedCards.clear();
        sourceStack = null;

        event.setDropCompleted(true);
        event.consume();
    }

    private CardStack findValidDropTarget(Point2D dropPoint) {
        Optional<CardStack> stackOpt = findStackAt(dropPoint);

        if (stackOpt.isEmpty() || stackOpt.get() == sourceStack) {
            return sourceStack;
        }

        CardStack target = stackOpt.get();

        if (target == discardPile) {
            return target;
        }

        if (playerSlots.get(currentPlayer).contains(target)) {
            return target;
        }

        return sourceStack;
    }

    private void highlightStack(CardStack stack) {
        if (highlightedStack != null) {
            highlightedStack.setHighlighted(false);
        }
        highlightedStack = stack;
        if (highlightedStack != null) {
            highlightedStack.setHighlighted(true);
        }
    }

    private Optional<CardStack> findStackAt(Point2D scenePoint) {
        for (int i = cardLayer.getChildren().size() - 1; i >= 0; i--) {
            var node = cardLayer.getChildren().get(i);
            if (node instanceof CardStack stack && node != dragOverlay) {
                Bounds bounds = stack.localToScene(stack.getBoundsInLocal());
                if (bounds.contains(scenePoint)) {
                    return Optional.of(stack);
                }
            }
        }
        return Optional.empty();
    }

    private void handlePlayAreaClick(MouseEvent event) {
        if (currentPlayer != 0) return;

        Optional<CardStack> clicked = findStackAt(new Point2D(event.getSceneX(), event.getSceneY()));
        if (clicked.isPresent()) {
            clicked.get().runFunction.run();
        }
        event.consume();
    }

    // ========== HELPERS ==========

    private void animateCardDraw(CardStack from, CardStack to, GameCard card, Runnable onComplete) {
        Bounds fromBounds = from.localToScene(from.getBoundsInLocal());
        double startX = fromBounds.getMinX();
        double startY = fromBounds.getMinY();

        card.setLayoutX(startX);
        card.setLayoutY(startY);
        cardLayer.getChildren().add(card);

        Bounds toBounds = to.localToScene(to.getBoundsInLocal());
        double endX = toBounds.getMinX();
        double endY = toBounds.getMinY();

        TranslateTransition transition = new TranslateTransition(Duration.millis(400), card);
        transition.setByX(endX - startX);
        transition.setByY(endY - startY);

        transition.setOnFinished(e -> {
            cardLayer.getChildren().remove(card);
            card.setTranslateX(0);
            card.setTranslateY(0);
            to.addCard(card);
            if (onComplete != null) {
                onComplete.run();
            }
        });

        transition.play();
    }

    private GameCard createSpecificCard(CardDefinition def) {
        GameCard card = new GameCard(def, cardBack);
        card.flip();
        return card;
    }

    private void showMessage(String message) {
        System.out.println("MESSAGE: " + message);
    }

    // ========== MENU ACTIONS ==========

    @FXML
    private void onNewGame() {
        // Clear visual elements
        playArea.getChildren().clear();
        cardLayer.getChildren().clear();

        // Clear all collections
        playerLabels.clear();
        playerSlots.clear();
        playerHands.clear();
        playerHourCounts.clear();
        hourCountLabels.clear();
        aiPlayers.clear();
        draggedCards = new ArrayList<>();
        cardToPlayedCard.clear();
        playedCardToCard.clear();

        // Reset state
        sourceStack = null;
        highlightedStack = null;
        currentPlayer = 0;
        hasDrawnThisTurn = false;
        hasPlayedThisTurn = false;
        gameOver = false;

        // Reinitialize game
        roundManager = new RoundManager(numPlayers);

        // Reset starting hours
        for (int i = 0; i < numPlayers; i++) {
            playerHourCounts.add(startingHours);
        }

        // Recreate AI players
        for (int i = 1; i < numPlayers; i++) {
            aiPlayers.add(new ComputerAI(ComputerAI.Level.EASY));
        }

        // Recreate UI
        createDragOverlay();
        createGameLayout();

        // Start first turn
        updateTurnIndicator();
        updateHourDisplay();
    }

    @FXML
    private void onExit() {
        System.exit(0);
    }

    @FXML
    private void onSettings() {
        if (gameOver) return; // Can't change settings during game

        Platform.runLater(() -> {
            Alert settingsDialog = new Alert(AlertType.CONFIRMATION);
            settingsDialog.setTitle("Game Settings");
            settingsDialog.setHeaderText("Adjust Game Settings");

            String currentSpeed = gameSpeed == 0 ? "Slow" : (gameSpeed == 1 ? "Normal" : "Fast");
            String settingsText = "Current Settings:\n\n" +
                    "Game Speed: " + currentSpeed + "\n" +
                    "Starting Hours: " + startingHours + "\n" +
                    "Victory Condition: Last Player Standing\n\n" +
                    "Change game speed?\n" +
                    "OK = Next Speed | Cancel = Keep Current";

            settingsDialog.setContentText(settingsText);

            settingsDialog.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    // Cycle through speeds
                    gameSpeed = (gameSpeed + 1) % 3;
                    String newSpeed = gameSpeed == 0 ? "Slow (2s)" : (gameSpeed == 1 ? "Normal (1s)" : "Fast (0.3s)");
                    showMessage("Game speed changed to: " + newSpeed);
                }
            });
        });
    }
}
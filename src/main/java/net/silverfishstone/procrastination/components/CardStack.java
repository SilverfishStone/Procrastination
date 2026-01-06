package net.silverfishstone.procrastination.components;

import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Container for GameCard objects with visual effects and positioning.
 *
 * Supports different stack types:
 * - "hand": Cards spread horizontally with spacing
 * - "slot": Cards stacked with slight offset
 * - "stock": Draw pile with hover effects
 * - "discard": All cards at same position (top visible)
 */
public class CardStack extends Pane {
    private static final double CARD_OFFSET = -3;

    public String stackType = "tableau";
    private boolean draggable = true;
    public Runnable runFunction = () -> {};

    private static final DropShadow STACK_HOVER_SHADOW = new DropShadow(15, Color.GOLD);
    private static final DropShadow STOCK_HOVER_SHADOW = new DropShadow(12, Color.BLUE);
    private static final DropShadow HIGHLIGHT_SHADOW = new DropShadow(20, Color.LIMEGREEN);

    private Rectangle highlightRect;
    private boolean highlighted = false;

    static {
        STACK_HOVER_SHADOW.setSpread(0.4);
        STOCK_HOVER_SHADOW.setSpread(0.35);
        STOCK_HOVER_SHADOW.setOffsetY(3);
        HIGHLIGHT_SHADOW.setSpread(0.6);
    }

    public CardStack() {
        setPickOnBounds(false);

        // Create highlight rectangle
        highlightRect = new Rectangle(100, 145);
        highlightRect.setFill(Color.TRANSPARENT);
        highlightRect.setStroke(Color.LIMEGREEN);
        highlightRect.setStrokeWidth(4);
        highlightRect.setArcWidth(10);
        highlightRect.setArcHeight(10);
        highlightRect.setVisible(false);
        highlightRect.setMouseTransparent(true);
        getChildren().add(highlightRect);

        // Click handler for stock pile
        setOnMouseClicked(e -> {
            if (stackType.equals("stock")) {
                runFunction.run();
            }
            e.consume();
        });
    }

    public void setHighlighted(boolean highlight) {
        this.highlighted = highlight;
        highlightRect.setVisible(highlight);
        if (highlight) {
            highlightRect.toFront();
            setEffect(HIGHLIGHT_SHADOW);
        } else {
            setEffect(null);
        }
    }

    public boolean isDraggable() {
        return draggable;
    }

    public void setDraggable(boolean draggable) {
        if (this.draggable == draggable) return;
        this.draggable = draggable;
        updateAppearance();
    }

    public void setStackType(String type) {
        if (!type.equals(this.stackType)) {
            this.stackType = type;
            updateAppearance();
        }
    }

    private void updateAppearance() {
        getTransforms().clear();
        if (!highlighted) {
            setEffect(null);
        }
        setOnMouseEntered(null);
        setOnMouseExited(null);

        if ("stock".equals(stackType)) {
            setOnMouseEntered(e -> {
                if (!highlighted) setEffect(STOCK_HOVER_SHADOW);
            });
            setOnMouseExited(e -> {
                if (!highlighted) setEffect(null);
            });
        } else if (!draggable && !"slot".equals(stackType) && !"discard".equals(stackType)) {
            setOnMouseEntered(e -> {
                if (!highlighted) setEffect(STACK_HOVER_SHADOW);
            });
            setOnMouseExited(e -> {
                if (!highlighted) setEffect(null);
            });
        }
    }

    // ========== CARD MANAGEMENT ==========

    /**
     * Adds a single GameCard to this stack.
     */
    public void addCard(GameCard card) {
        getChildren().add(card);
        highlightRect.toFront();
        repositionCards();
        updateCardStates();
    }

    /**
     * Adds multiple GameCards to this stack.
     */
    public void addCards(List<GameCard> cards) {
        getChildren().addAll(cards);
        highlightRect.toFront();
        repositionCards();
        updateCardStates();
    }

    /**
     * Removes a GameCard from this stack.
     */
    public void removeCard(GameCard card) {
        getChildren().remove(card);
        repositionCards();
        updateCardStates();
    }

    /**
     * Removes multiple GameCards from this stack.
     */
    public void removeCards(List<GameCard> cards) {
        getChildren().removeAll(cards);
        repositionCards();
        updateCardStates();
    }

    /**
     * Updates the interactive state of all cards in the stack.
     * For hands, all cards are interactive.
     * For other stacks, only the top card is interactive.
     */
    private void updateCardStates() {
        for (int i = 0; i < getChildren().size(); i++) {
            Node node = getChildren().get(i);
            if (node instanceof GameCard card) {
                // For hands, all cards should be interactive
                boolean isTop = "hand".equals(stackType) || (i == getChildren().size() - 1);
                card.setTopCard(isTop);
            }
        }
    }

    /**
     * Repositions all cards based on stack type.
     */
    public void repositionCards() {
        if ("hand".equals(stackType)) {
            // Hand: spread horizontally with spacing
            double x = 0;
            double spacing = 25;
            for (Node node : getChildren()) {
                if (node instanceof GameCard) {
                    node.setLayoutX(x);
                    node.setLayoutY(0);
                    x += spacing;
                }
            }
        } else if ("discard".equals(stackType)) {
            // Discard pile: all cards at same position (top card only visible)
            for (Node node : getChildren()) {
                if (node instanceof GameCard) {
                    node.setLayoutX(0);
                    node.setLayoutY(0);
                }
            }
        } else {
            // Other stacks: slight offset to show depth
            double y = 0;
            for (Node node : getChildren()) {
                if (node instanceof GameCard) {
                    node.setLayoutX(0);
                    node.setLayoutY(y);
                    y += CARD_OFFSET;
                }
            }
        }
    }

    /**
     * Gets all cards in this stack from a specific card onwards.
     * Used for dragging multiple cards together.
     *
     * @param card The starting card
     * @return List of cards from the starting card to the end of the stack
     */
    public List<GameCard> getCardsFrom(GameCard card) {
        int index = getChildren().indexOf(card);
        if (index == -1) return List.of();

        return getChildren().subList(index, getChildren().size())
                .stream()
                .filter(n -> n instanceof GameCard)
                .map(n -> (GameCard) n)
                .collect(Collectors.toList());
    }

    /**
     * Gets all GameCards currently in this stack.
     *
     * @return List of all GameCards (excludes highlight rectangle)
     */
    public List<GameCard> getAllCards() {
        return getChildren().stream()
                .filter(n -> n instanceof GameCard)
                .map(n -> (GameCard) n)
                .collect(Collectors.toList());
    }

    /**
     * Returns the number of GameCards in this stack.
     */
    public int getCardCount() {
        return (int) getChildren().stream()
                .filter(n -> n instanceof GameCard)
                .count();
    }

    /**
     * Checks if this stack is empty (no GameCards).
     */
    public boolean isEmpty() {
        return getCardCount() == 0;
    }

    /**
     * Gets the top card in the stack (last added).
     *
     * @return The top GameCard, or null if stack is empty
     */
    public GameCard getTopCard() {
        for (int i = getChildren().size() - 1; i >= 0; i--) {
            Node node = getChildren().get(i);
            if (node instanceof GameCard card) {
                return card;
            }
        }
        return null;
    }
}
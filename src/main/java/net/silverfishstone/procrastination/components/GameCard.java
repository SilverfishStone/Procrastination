package net.silverfishstone.procrastination.components;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import net.silverfishstone.procrastination.components.CardDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced Card component that integrates with CardDefinition system.
 * 
 * This card displays:
 * - Card name and category
 * - Current hour value (with visual hour card stacks)
 * - Rounds remaining until expiry
 * - Status indicators (protected, expired, etc.)
 */
public class GameCard extends Pane {
    private final CardDefinition definition;
    private boolean faceUp = false;
    private boolean topCard = true;
    private boolean dragging = false;

    private Canvas canvas;
    private static final double CARD_WIDTH = 100;
    private static final double CARD_HEIGHT = 145;

    // Visual settings
    private Color borderColor = Color.BLACK;
    private double borderWidth = 2;
    private double cornerRadius = 10;

    private Image backImage;
    private double backImageX = 10;
    private double backImageY = 10;
    private double backImageWidth = 80;
    private double backImageHeight = 125;

    // Front image layers
    private List<CardImageLayer> frontImageLayers = new ArrayList<>();

    // Hour tracking visuals
    private int displayedHours = 0;
    private int roundsInPlay = 0;
    private boolean isProtected = false;
    private boolean hasExpired = false;

    // Effects
    private static final DropShadow HOVER_SHADOW = new DropShadow(10, Color.LIGHTBLUE);
    private static final DropShadow DRAG_SHADOW = new DropShadow(20, Color.BLACK);

    static {
        HOVER_SHADOW.setSpread(0.3);
        DRAG_SHADOW.setOffsetY(5);
        DRAG_SHADOW.setSpread(0.2);
    }

    public GameCard(CardDefinition definition, Image backImage) {
        this.definition = definition;
        this.backImage = backImage;

        canvas = new Canvas(CARD_WIDTH, CARD_HEIGHT);
        getChildren().add(canvas);

        setPickOnBounds(true);
        drawCard();
        updateHoverBehavior();
    }

    /**
     * Add an image layer to the card front.
     */
    public void addFrontImageLayer(Image image, double x, double y, double width, double height) {
        frontImageLayers.add(new CardImageLayer(image, x, y, width, height));
    }

    /**
     * Updates the card's displayed state (hours, rounds, etc.)
     */
    public void updateState(int hours, int rounds, boolean protected_, boolean expired) {
        this.displayedHours = hours;
        this.roundsInPlay = rounds;
        this.isProtected = protected_;
        this.hasExpired = expired;
        drawCard();
    }

    /**
     * Main drawing method
     */
    private void drawCard() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, CARD_WIDTH, CARD_HEIGHT);

        if (faceUp) {
            drawFront(gc);
        } else {
            drawBack(gc);
        }
    }

    /**
     * Draw card back
     */
    private void drawBack(GraphicsContext gc) {
        gc.setFill(borderColor);
        gc.fillRoundRect(0, 0, CARD_WIDTH, CARD_HEIGHT, cornerRadius, cornerRadius);

        gc.setFill(Color.WHITE);
        gc.fillRoundRect(borderWidth, borderWidth,
                CARD_WIDTH - borderWidth * 2,
                CARD_HEIGHT - borderWidth * 2,
                cornerRadius - borderWidth, cornerRadius - borderWidth);

        if (backImage != null) {
            gc.drawImage(backImage, backImageX, backImageY, backImageWidth, backImageHeight);
        }
    }

    /**
     * Draw card front with game state information
     */
    private void drawFront(GraphicsContext gc) {
        // Background color based on category
        Color bgColor = getCategoryColor();
        gc.setFill(bgColor);
        gc.fillRoundRect(0, 0, CARD_WIDTH, CARD_HEIGHT, cornerRadius, cornerRadius);

        // Border
        gc.setFill(borderColor);
        gc.setLineWidth(borderWidth);
        gc.strokeRoundRect(borderWidth/2, borderWidth/2, 
                          CARD_WIDTH - borderWidth, 
                          CARD_HEIGHT - borderWidth, 
                          cornerRadius, cornerRadius);

        // Inner background
        gc.setFill(Color.WHITE);
        gc.fillRoundRect(borderWidth + 2, borderWidth + 2,
                CARD_WIDTH - borderWidth * 2 - 4,
                CARD_HEIGHT - borderWidth * 2 - 4,
                cornerRadius - borderWidth, cornerRadius - borderWidth);

        // Draw category at top
        gc.setFill(Color.BLACK);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(definition.getCategory().toString(), CARD_WIDTH / 2, 15);

        // Draw card name
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        String name = definition.getDisplayName();
        if (name.length() > 12) {
            name = name.substring(0, 10) + "..";
        }
        gc.fillText(name, CARD_WIDTH / 2, 30);

        // Draw front image layers (if any)
        for (CardImageLayer layer : frontImageLayers) {
            if (layer.image != null) {
                gc.drawImage(layer.image, layer.x, layer.y, layer.width, layer.height);
            }
        }

        // Draw hour value (large in center)
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        Color hourColor = displayedHours >= 0 ? Color.GREEN : Color.RED;
        gc.setFill(hourColor);
        String hourText = (displayedHours >= 0 ? "+" : "") + displayedHours;
        gc.fillText(hourText, CARD_WIDTH / 2, 70);

        // Draw "hours" label
        gc.setFont(Font.font("Arial", 10));
        gc.setFill(Color.GRAY);
        gc.fillText("hours", CARD_WIDTH / 2, 85);

        // Draw rounds info
        if (definition.getExpiresAfterRounds() > 0) {
            gc.setFont(Font.font("Arial", 9));
            gc.setFill(Color.BLACK);
            int remaining = definition.getExpiresAfterRounds() - roundsInPlay;
            String roundText = "Round " + roundsInPlay + "/" + definition.getExpiresAfterRounds();
            gc.fillText(roundText, CARD_WIDTH / 2, 100);
            
            if (remaining <= 2 && remaining > 0) {
                gc.setFill(Color.ORANGE);
                gc.fillText("EXPIRING SOON!", CARD_WIDTH / 2, 112);
            } else if (hasExpired) {
                gc.setFill(Color.RED);
                gc.fillText("EXPIRED", CARD_WIDTH / 2, 112);
            }
        }

        // Draw protection indicator
        if (isProtected) {
            gc.setFill(Color.GOLD);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 9));
            gc.fillText("PROTECTED", CARD_WIDTH / 2, 125);
        }

        // Draw per-round info at bottom
        gc.setFont(Font.font("Arial", 8));
        gc.setFill(Color.DARKGRAY);
        int perRound = definition.getHoursPerRound();
        if (perRound != 0) {
            String perRoundText = (perRound > 0 ? "+" : "") + perRound + "/round";
            gc.fillText(perRoundText, CARD_WIDTH / 2, CARD_HEIGHT - 5);
        }
    }

    /**
     * Returns background color based on card category
     */
    private Color getCategoryColor() {
        return switch (definition.getCategory()) {
            case PLAY -> Color.rgb(200, 255, 200);     // Light green
            case WEAPON -> Color.rgb(255, 200, 200);   // Light red
            case HELPER -> Color.rgb(200, 220, 255);   // Light blue
            case ALERT -> Color.rgb(255, 255, 150);    // Light yellow
        };
    }

    public void setTopCard(boolean isTop) {
        if (this.topCard != isTop) {
            this.topCard = isTop;
            updateHoverBehavior();
        }
    }

    private void updateHoverBehavior() {
        setOnMouseEntered(null);
        setOnMouseExited(null);

        if (topCard) {
            setOnMouseEntered(e -> {
                if (!isDragging()) {
                    setEffect(HOVER_SHADOW);
                }
            });
            setOnMouseExited(e -> {
                if (!isDragging()) {
                    setEffect(null);
                }
            });
        } else {
            setEffect(null);
        }
    }

    public void setDragging(boolean dragging) {
        this.dragging = dragging;
        setEffect(dragging ? DRAG_SHADOW : (topCard ? HOVER_SHADOW : null));
    }

    public boolean isDragging() {
        return dragging;
    }

    public void flip() {
        faceUp = !faceUp;
        drawCard();
    }

    public boolean isFaceUp() {
        return faceUp;
    }

    public CardDefinition getDefinition() {
        return definition;
    }

    // Legacy compatibility
    public String getSuit() {
        return definition.getCategory().toString().toLowerCase();
    }

    public String getRank() {
        return definition.getDisplayName();
    }

    /**
     * Helper class for image layers
     */
    private static class CardImageLayer {
        Image image;
        double x, y, width, height;

        CardImageLayer(Image image, double x, double y, double width, double height) {
            this.image = image;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}

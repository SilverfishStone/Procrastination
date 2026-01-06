package net.silverfishstone.procrastination.components;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;

public class Card extends Pane {
    private final String suit;  // card type: play, hour, alert, gift, weapon
    private final String rank;  // card name: Play_1, Hour_23, etc.
    private boolean faceUp = false;
    private boolean topCard = true;
    private boolean dragging = false;

    private Canvas canvas;
    private static final double CARD_WIDTH = 100;
    private static final double CARD_HEIGHT = 145;

    // Visual settings (customizable later)
    private Color borderColor = Color.BLACK;
    private double borderWidth = 2;
    private Color backgroundColor = Color.WHITE;
    private double cornerRadius = 10;

    // Back image settings
    private Image backImage;
    private double backImageX = 10;
    private double backImageY = 10;
    private double backImageWidth = 80;
    private double backImageHeight = 125;

    // Front image layers
    private List<CardImageLayer> frontImageLayers = new ArrayList<>();

    // Effects
    private static final DropShadow HOVER_SHADOW = new DropShadow(10, Color.LIGHTBLUE);
    private static final DropShadow DRAG_SHADOW = new DropShadow(20, Color.BLACK);

    static {
        HOVER_SHADOW.setSpread(0.3);
        DRAG_SHADOW.setOffsetY(5);
        DRAG_SHADOW.setSpread(0.2);
    }

    public Card(String suit, String rank, Image frontImage, Image backImage) {
        this.suit = suit;
        this.rank = rank;
        this.backImage = backImage;

        // Create canvas for custom drawing
        canvas = new Canvas(CARD_WIDTH, CARD_HEIGHT);
        getChildren().add(canvas);

        // Add default front image layer if provided
        if (frontImage != null) {
            addFrontImageLayer(frontImage, 10, 10, 80, 80);
        }

        setPickOnBounds(true);
        drawCard();
        updateHoverBehavior();
    }

    /**
     * Add an image layer to the card front.
     * Multiple layers can be added to compose the card face.
     *
     * @param image The image to draw
     * @param x X position relative to card (0,0 = top-left of card)
     * @param y Y position relative to card
     * @param width Width to draw the image
     * @param height Height to draw the image
     */
    public void addFrontImageLayer(Image image, double x, double y, double width, double height) {
        frontImageLayers.add(new CardImageLayer(image, x, y, width, height));
    }

    /**
     * Configure the back image position and size
     */
    public void setBackImageLayout(double x, double y, double width, double height) {
        this.backImageX = x;
        this.backImageY = y;
        this.backImageWidth = width;
        this.backImageHeight = height;
    }

    /**
     * Set card visual style (for future customization)
     */
    public void setCardStyle(Color border, double borderWidth, Color background, double cornerRadius) {
        this.borderColor = border;
        this.borderWidth = borderWidth;
        this.backgroundColor = background;
        this.cornerRadius = cornerRadius;
    }

    /**
     * Main drawing method - renders the entire card
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
        // Draw border rectangle
        gc.setFill(borderColor);
        gc.fillRoundRect(0, 0, CARD_WIDTH, CARD_HEIGHT, cornerRadius, cornerRadius);

        // Draw white background
        gc.setFill(backgroundColor);
        gc.fillRoundRect(borderWidth, borderWidth,
                CARD_WIDTH - borderWidth * 2,
                CARD_HEIGHT - borderWidth * 2,
                cornerRadius - borderWidth, cornerRadius - borderWidth);

        // Draw back image
        if (backImage != null) {
            gc.drawImage(backImage, backImageX, backImageY, backImageWidth, backImageHeight);
        }
    }

    /**
     * Draw card front
     */
    private void drawFront(GraphicsContext gc) {
        // Draw border rectangle
        gc.setFill(borderColor);
        gc.fillRoundRect(0, 0, CARD_WIDTH, CARD_HEIGHT, cornerRadius, cornerRadius);

        // Draw white background
        gc.setFill(backgroundColor);
        gc.fillRoundRect(borderWidth, borderWidth,
                CARD_WIDTH - borderWidth * 2,
                CARD_HEIGHT - borderWidth * 2,
                cornerRadius - borderWidth, cornerRadius - borderWidth);

        // Draw all front image layers
        for (CardImageLayer layer : frontImageLayers) {
            if (layer.image != null) {
                gc.drawImage(layer.image, layer.x, layer.y, layer.width, layer.height);
            }
        }

        // Draw card name text at bottom
        gc.setFill(Color.BLACK);
        gc.setFont(Font.font("Arial", 12));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(rank, CARD_WIDTH / 2, CARD_HEIGHT - 10);

        // Draw type indicator at top
        gc.setFont(Font.font("Arial", 10));
        gc.fillText(suit.toUpperCase(), CARD_WIDTH / 2, 15);
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

    public String getSuit() {
        return suit;
    }

    public String getRank() {
        return rank;
    }

    /**
     * Helper class to store image layer information
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
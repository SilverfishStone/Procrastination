package com.workgame.ui;

import com.workgame.model.Card;
import com.workgame.model.CardType;
import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Visual representation of a card.
 *
 * HAND     – face-up in current player's hand tray (full info)
 * PLAYED   – face-up on the table in a play zone (compact)
 * FACEDOWN – back-of-card for opponent hands
 * VIEWONLY – face-up but dimmed; clicking shows info, can't play
 */
public class CardView extends VBox {

    public enum Mode { HAND, PLAYED, FACEDOWN, VIEWONLY }

    private final Card card;
    private boolean selected = false;

    public CardView(Card card, Mode mode) {
        this.card = card;
        setSpacing(0);
        setAlignment(Pos.TOP_LEFT);

        switch (mode) {
            case FACEDOWN -> buildFacedown();
            case HAND     -> buildFaceUp(false, false);
            case PLAYED   -> buildFaceUp(true, false);
            case VIEWONLY -> buildFaceUp(false, true);
        }
    }

    public static CardView faceDown() {
        return new CardView(null, Mode.FACEDOWN);
    }

    // ── Face-down ─────────────────────────────────────────────────────────────

    private void buildFacedown() {
        getStyleClass().add("card-facedown");
        Label back = new Label("✦");
        back.setStyle("-fx-text-fill: rgba(80,120,220,0.35); -fx-font-size: 14px;");
        setAlignment(Pos.CENTER);
        getChildren().add(back);
    }

    // ── Face-up ───────────────────────────────────────────────────────────────

    private void buildFaceUp(boolean played, boolean viewOnly) {
        // Explicit card background — cream/ivory that always shows regardless of theme
        String cardBg    = cardBodyColor();
        String bodyBg    = cardBodyColor();
        String borderCol = cardBorderColor();

        setStyle(
                "-fx-background-color: " + cardBg + ";" +
                        "-fx-background-radius: 8px;" +
                        "-fx-border-color: " + borderCol + ";" +
                        "-fx-border-width: 1px;" +
                        "-fx-border-radius: 8px;" +
                        "-fx-effect: dropshadow(gaussian,#000000aa,8,0.4,2,3);" +
                        "-fx-cursor: hand;" +
                        "-fx-min-width: " + (played ? "105" : "95") + "px;" +
                        "-fx-max-width: " + (played ? "125" : "110") + "px;" +
                        "-fx-min-height: " + (played ? "85" : "78") + "px;");

        if (viewOnly) setOpacity(0.82);

        // ── Header strip ──────────────────────────────────────────────────────
        VBox header = new VBox(1);
        // Inline style for header so it always renders, theme-independent
        header.setStyle(
                "-fx-background-color: " + headerColor() + ";" +
                        "-fx-background-radius: 7px 7px 0 0;" +
                        "-fx-padding: 4 6 4 6;");
        header.setAlignment(Pos.CENTER_LEFT);

        Label badgeLbl = new Label(badgeText());
        badgeLbl.setStyle("-fx-font-size: 7px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        header.getChildren().add(badgeLbl);

        // ── Body ──────────────────────────────────────────────────────────────
        VBox body = new VBox(2);
        body.setStyle(
                "-fx-background-color: " + bodyBg + ";" +
                        "-fx-padding: 4 6 4 6;" +
                        "-fx-background-radius: 0 0 7px 7px;");

        Label nameLbl = new Label(card.getName());
        nameLbl.setStyle(
                "-fx-font-size: 10px; -fx-font-weight: bold;" +
                        "-fx-text-fill: " + nameColor() + ";" +
                        "-fx-wrap-text: true;");
        body.getChildren().add(nameLbl);

        if (played) {
            String ht = hoursText();
            if (!ht.isEmpty()) {
                Label hLbl = new Label(ht);
                hLbl.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #8a3a08;");
                body.getChildren().add(hLbl);
            }
            if (card.getExpiresAfterRound() > 0) {
                int rem = card.roundsRemaining();
                Label eLbl = new Label(rem == 0 ? "⚠ EXPIRES" : "⏳ " + rem + "r");
                eLbl.setStyle("-fx-font-size: 8px; -fx-text-fill: " + (rem <= 1 ? "#cc2010" : "#7a6040") + ";" +
                        (rem <= 1 ? " -fx-font-weight: bold;" : ""));
                body.getChildren().add(eLbl);
            }
            if (card.isNepotismProtected()) {
                Label nLbl = new Label("🛡 Protected");
                nLbl.setStyle("-fx-font-size: 8px; -fx-text-fill: #4a8a9a;");
                body.getChildren().add(nLbl);
            }
        } else {
            Label descLbl = new Label(card.getDescription());
            descLbl.setStyle("-fx-font-size: 8px; -fx-text-fill: " + descColor() + "; -fx-wrap-text: true;");
            body.getChildren().add(descLbl);
        }

        getChildren().addAll(header, body);

        // Tooltip
        StringBuilder tt = new StringBuilder(card.getName() + "\n\n" + card.getDescription());
        if (!card.getAttachedHelpers().isEmpty()) {
            tt.append("\n\nAttached: ");
            card.getAttachedHelpers().forEach(h -> tt.append(h.getName()).append(", "));
        }
        Tooltip tip = new Tooltip(tt.toString().stripTrailing().replaceAll(", $", ""));
        tip.setWrapText(true); tip.setMaxWidth(250);
        Tooltip.install(this, tip);

        // Hover lift effect
        if (!viewOnly) {
            setOnMouseEntered(e -> setStyle(getStyle().replace("-fx-translate-y: 0px;", "") +
                    "-fx-translate-y: -4px; -fx-effect: dropshadow(gaussian,#f5d06080,14,0.5,0,-3);"));
            setOnMouseExited(e -> {
                String s = getStyle();
                s = s.replace("-fx-translate-y: -4px;", "-fx-translate-y: 0px;");
                s = s.replace("-fx-effect: dropshadow(gaussian,#f5d06080,14,0.5,0,-3);",
                        "-fx-effect: dropshadow(gaussian,#000000aa,8,0.4,2,3);");
                setStyle(s);
            });
        }
    }

    // ── Selection ─────────────────────────────────────────────────────────────

    public void setSelected(boolean selected) {
        this.selected = selected;
        if (selected) { if (!getStyleClass().contains("card-selected")) getStyleClass().add("card-selected"); }
        else          { getStyleClass().remove("card-selected"); }
    }

    public boolean isSelected() { return selected; }
    public Card    getCard()    { return card; }

    // ── Animations ────────────────────────────────────────────────────────────

    public void animateSlideFrom(double offsetX, double offsetY, double delayMs) {
        setTranslateX(offsetX); setTranslateY(offsetY); setOpacity(0);
        TranslateTransition slide = new TranslateTransition(Duration.millis(380), this);
        slide.setToX(0); slide.setToY(0);
        slide.setInterpolator(Interpolator.SPLINE(0.25, 0.1, 0.25, 1.0));
        FadeTransition fade = new FadeTransition(Duration.millis(260), this);
        fade.setToValue(1.0);
        ParallelTransition anim = new ParallelTransition(slide, fade);
        anim.setDelay(Duration.millis(delayMs));
        anim.play();
    }

    public void animateDeal(double delayMs) { animateSlideFrom(0, -150, delayMs); }

    // ── Color helpers — adapt to current theme ────────────────────────────────

    /** Card face/body background — light enough for dark text in all themes. */
    private String cardBodyColor() {
        return switch (SetupScreen.currentTheme) {
            case "Midnight"  -> "#e8e4f8"; // pale lavender
            case "Corporate" -> "#f0f0f0"; // light grey
            case "Sunset"    -> "#f8ede0"; // pale peach
            case "Ocean"     -> "#ddeeff"; // pale sky blue
            default          -> "#f8f4ec"; // warm cream (Felt)
        };
    }

    private String cardBorderColor() {
        return switch (SetupScreen.currentTheme) {
            case "Midnight"  -> "#9080c0";
            case "Corporate" -> "#b0b0b0";
            case "Sunset"    -> "#c09070";
            case "Ocean"     -> "#6090c0";
            default          -> "#c8b890";
        };
    }

    /** Name text — dark enough to read on the light card face. */
    private String nameColor() {
        return switch (SetupScreen.currentTheme) {
            case "Midnight"  -> "#1a1030";
            case "Corporate" -> "#1a1a1a";
            case "Sunset"    -> "#1a0808";
            case "Ocean"     -> "#0a1828";
            default          -> "#1a1208";
        };
    }

    private String descColor() {
        return switch (SetupScreen.currentTheme) {
            case "Midnight"  -> "#4a3870";
            case "Corporate" -> "#505050";
            case "Sunset"    -> "#6a3820";
            case "Ocean"     -> "#284060";
            default          -> "#5a4a30";
        };
    }

    /** Header gradient per card type. */
    private String headerColor() {
        if (card == null) return "#404040";
        return switch (card.getType()) {
            case PLAY             -> "linear-gradient(to right, #3a7a2a, #5aaa3a)";
            case HELPER           -> "linear-gradient(to right, #2a4a8a, #4a78c8)";
            case ALERT            -> "linear-gradient(to right, #8a2a1a, #c04a2a)";
            case WEAPON_IMMEDIATE,
                 WEAPON_PLAY      -> "linear-gradient(to right, #5a1a8a, #8a3ac8)";
            case WEAPON_ROLLING   -> "linear-gradient(to right, #7a1a5a, #b83a9a)";
        };
    }

    private String headerClass() {
        if (card == null) return "";
        return switch (card.getType()) {
            case PLAY             -> "card-header-play";
            case HELPER           -> "card-header-helper";
            case ALERT            -> "card-header-alert";
            case WEAPON_IMMEDIATE,
                 WEAPON_PLAY      -> "card-header-weapon";
            case WEAPON_ROLLING   -> "card-header-rolling";
        };
    }

    private String badgeText() {
        if (card == null) return "";
        return switch (card.getType()) {
            case PLAY             -> "PLAY";
            case HELPER           -> "HELPER";
            case ALERT            -> "⚠ ALERT";
            case WEAPON_IMMEDIATE -> "WEAPON";
            case WEAPON_PLAY      -> "WPN/PLAY";
            case WEAPON_ROLLING   -> "⟳ ROLLING";
        };
    }

    private String hoursText() {
        if (card == null) return "";
        int on = card.getHoursOnCard();
        if (on == 0) return "";
        if (card.getImmediateHours() < 0 && on > 0) return "debt: " + on + "h";
        return "+" + on + "h";
    }
}
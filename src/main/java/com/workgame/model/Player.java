package com.workgame.model;

import java.util.ArrayList;
import java.util.List;

public class Player {

    public enum PlayerType { HUMAN, CPU_EASY, CPU_MEDIUM, CPU_HARD }

    private final String name;
    private PlayerType playerType = PlayerType.HUMAN;
    private final List<Card> hand = new ArrayList<>();
    private final List<Card> playedCards = new ArrayList<>(); // max 3
    private int hourBank = 0; // total hours held (from hour draw pile)
    private int hourTokens = 10; // starting hour cards

    public static final int MAX_PLAYED_CARDS = 3;
    public static final int MAX_HAND_SIZE = 5;
    public static final int STARTING_HOURS = 10;

    public Player(String name) {
        this.name = name;
        this.hourTokens = STARTING_HOURS;
    }

    // ── Identity ─────────────────────────────────────────────────────────────

    public String getName() { return name; }
    public PlayerType getPlayerType()              { return playerType; }
    public void setPlayerType(PlayerType t)        { this.playerType = t; }
    public boolean isHuman()                       { return playerType == PlayerType.HUMAN; }

    // ── Hand management ──────────────────────────────────────────────────────

    public List<Card> getHand() { return hand; }

    public void addToHand(Card card) { hand.add(card); }

    public boolean removeFromHand(Card card) { return hand.remove(card); }

    public boolean handFull() { return hand.size() >= MAX_HAND_SIZE; }

    public int handSize() { return hand.size(); }

    // ── Played cards ─────────────────────────────────────────────────────────

    public List<Card> getPlayedCards() { return playedCards; }

    public boolean canPlayCard() { return playedCards.size() < MAX_PLAYED_CARDS; }

    public boolean playCard(Card card) {
        if (!canPlayCard()) return false;
        card.setOwner(this);
        playedCards.add(card);
        return true;
    }

    /** Remove a card from the play area (e.g. on discard or expiry). */
    public boolean removePlayedCard(Card card) { return playedCards.remove(card); }

    public boolean hasWeaponInPlay() {
        return playedCards.stream().anyMatch(Card::isWeapon);
    }

    /** True when a play weapon is present, blocking hours on non-weapon played cards. */
    public boolean isBlockedByWeapon() {
        return playedCards.stream()
                .anyMatch(c -> c.getType() == CardType.WEAPON_PLAY
                        || c.getType() == CardType.WEAPON_ROLLING);
    }

    // ── Hour tokens ───────────────────────────────────────────────────────────

    public int getHourTokens() { return hourTokens; }

    public void addHours(int n)  { hourTokens += n; }

    /**
     * Remove up to n hours. Returns the actual number removed
     * (may be less if the player doesn't have enough).
     */
    public int removeHours(int n) {
        int removed = Math.min(n, hourTokens);
        hourTokens -= removed;
        return removed;
    }

    public void setHourTokens(int n) { hourTokens = n; }

    // ── Utility ───────────────────────────────────────────────────────────────

    @Override
    public String toString() { return name + " (" + hourTokens + "h)"; }
}
package com.workgame.model;

/**
 * Represents a single card in the game.
 *
 * hoursPerRound: hours added (or removed if negative) each round while played
 * immediateHours: hours applied immediately when the card is first played/received
 * expiresAfterRound: the round number (from when the card was played) on which it expires
 * finalHours: bonus hours awarded when discarding at the correct round (e.g. Professional)
 */
public class Card {
    private final String name;
    private final CardType type;
    private final String description;

    // Core hour values
    private final int immediateHours;       // applied instantly on play
    private final int hoursPerRound;        // applied each round
    private final int expiresAfterRound;    // 0 = never expires naturally
    private final int finalHours;           // bonus on timely discard

    // State when the card is in play
    private int roundsPlayed = 0;
    private int extensionBonus = 0;          // added rounds from Extension helper cards
    private int hoursOnCard = 0;            // hour tokens physically on this card
    private boolean expired = false;
    private boolean nepotismProtected = false;

    // For weapon play cards: track which player slot it belongs to
    private Player owner  = null;
    // For Parasite: the player who sent it (receives hours on expiry/discard)
    private Player sender = null;

    // Helpers attached to this card (Nepotism, Extension recorded here for display)
    private final java.util.List<Card> attachedHelpers = new java.util.ArrayList<>();

    public Card(String name, CardType type, String description,
                int immediateHours, int hoursPerRound, int expiresAfterRound, int finalHours) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.immediateHours = immediateHours;
        this.hoursPerRound = hoursPerRound;
        this.expiresAfterRound = expiresAfterRound;
        this.finalHours = finalHours;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getName()            { return name; }
    public CardType getType()          { return type; }
    public String getDescription()     { return description; }
    public int getImmediateHours()     { return immediateHours; }
    public int getHoursPerRound()      { return hoursPerRound; }
    public int getExpiresAfterRound()  { return expiresAfterRound; }
    public int getFinalHours()         { return finalHours; }
    public int getRoundsPlayed()       { return roundsPlayed; }
    public int getHoursOnCard()        { return hoursOnCard; }
    public boolean isExpired()         { return expired; }
    public boolean isNepotismProtected() { return nepotismProtected; }
    public Player getOwner()           { return owner; }
    public java.util.List<Card> getAttachedHelpers() { return attachedHelpers; }
    public void attachHelper(Card helper)            { attachedHelpers.add(helper); }
    public void clearAttachedHelpers()               { attachedHelpers.clear(); }

    // ── Setters / mutators ───────────────────────────────────────────────────

    public void setOwner(Player owner)                     { this.owner = owner; }
    public Player getSender()                              { return sender; }
    public void setSender(Player sender)                   { this.sender = sender; }
    public void setNepotismProtected(boolean v)            { this.nepotismProtected = v; }
    public void setExpired(boolean v)                      { this.expired = v; }

    public void incrementRoundsPlayed()                    { roundsPlayed++; }
    public void resetRoundsPlayed()                        { roundsPlayed = 0; }
    public void addExtensionBonus(int rounds)              { extensionBonus += rounds; }
    public int  getExtensionBonus()                        { return extensionBonus; }
    public void addHoursOnCard(int h)                      { hoursOnCard += h; }
    public void removeHoursOnCard(int h)                   { hoursOnCard = Math.max(0, hoursOnCard - h); }
    public void setHoursOnCard(int h)                      { hoursOnCard = h; }

    /** True when this card should expire this round (before the player acts). */
    public boolean shouldExpireThisRound() {
        return !nepotismProtected && expiresAfterRound > 0
                && roundsPlayed >= expiresAfterRound + extensionBonus;
    }

    /** Effective rounds until expiry (for display). */
    public int roundsRemaining() {
        if (expiresAfterRound <= 0) return Integer.MAX_VALUE;
        return Math.max(0, expiresAfterRound + extensionBonus - roundsPlayed);
    }

    public boolean isWeapon() {
        return type == CardType.WEAPON_IMMEDIATE
                || type == CardType.WEAPON_PLAY
                || type == CardType.WEAPON_ROLLING;
    }

    public boolean isPlayType() {
        return type == CardType.PLAY
                || type == CardType.WEAPON_PLAY
                || type == CardType.WEAPON_ROLLING;
    }

    @Override
    public String toString() {
        return name + " [" + type + "]";
    }
}
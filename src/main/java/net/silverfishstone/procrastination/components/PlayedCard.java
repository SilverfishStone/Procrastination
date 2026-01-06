package net.silverfishstone.procrastination.components;

/**
 * Represents a card that is currently in play in front of a player.
 * Tracks rounds, hours accumulated, and special states.
 */
public class PlayedCard {
    private final GameCard visualCard;        // The JavaFX GameCard component
    private final CardDefinition definition;  // What type of card this is
    private final int ownerPlayerIndex;       // Who played this card

    private int roundsInPlay = 0;             // How many rounds this has been active
    private int currentHourValue = 0;         // Net hours on this card (can be negative)
    private boolean isProtectedByNepotism = false;
    private boolean hasExpired = false;       // For nepotism - card expired but protected

    // Special tracking for specific cards
    private int linkedPlayerIndex = -1;       // For Sharing is Caring
    private PlayedCard linkedCard = null;     // For Sharing is Caring
    private int attackerPlayerIndex = -1;     // For Parasite - who played this weapon

    public PlayedCard(GameCard visualCard, CardDefinition definition, int ownerPlayerIndex) {
        this.visualCard = visualCard;
        this.definition = definition;
        this.ownerPlayerIndex = ownerPlayerIndex;

        // Apply immediate hours
        this.currentHourValue = definition.getImmediateHours();
    }

    /**
     * Called at the start of each round to update this card's state.
     * Returns the number of hours gained/lost this round.
     */
    public int processRound() {
        if (hasExpired && isProtectedByNepotism) {
            return 0; // Nepotism cards don't gain hours after expiring
        }

        roundsInPlay++;

        int hoursGained = 0;

        // Handle special card mechanics
        if (definition.hasProfessionalBonus()) {
            // Professional: +10 hours on round 8 only
            if (roundsInPlay == 8) {
                hoursGained = 10;
            }
        } else if (definition.hasRiskyBonus()) {
            // Risky: +1 per round normally, +8 bonus on round 8
            hoursGained = definition.getHoursPerRound();
            if (roundsInPlay == 8) {
                hoursGained += 8; // Total +9 this round
            }
        } else if (definition.hasAlternatingMechanic()) {
            // Unpredictable: alternates +1, -1, 0, +1, -1, 0...
            int cycle = roundsInPlay % 3;
            if (cycle == 1) hoursGained = 1;
            else if (cycle == 2) hoursGained = -1;
            // cycle 0 (every 3rd round) = 0
        } else if (definition.hasSharingMechanic()) {
            // Handled externally by RoundManager
            hoursGained = 0;
        } else {
            // Normal cards: just add hoursPerRound
            hoursGained = definition.getHoursPerRound();
        }

        currentHourValue += hoursGained;

        // Check if card should expire
        if (!isProtectedByNepotism &&
                definition.getExpiresAfterRounds() > 0 &&
                roundsInPlay >= definition.getExpiresAfterRounds()) {
            hasExpired = true;
        }

        return hoursGained;
    }

    /**
     * Returns true if this card needs to be discarded due to expiration.
     */
    public boolean shouldAutoDiscard() {
        return hasExpired && !isProtectedByNepotism;
    }

    /**
     * Gets the final hour value when card is discarded or expires.
     * Positive values are kept, negative values are lost.
     */
    public int getFinalHourValue() {
        if (shouldAutoDiscard() && currentHourValue < 0) {
            // Expired with negative value - lose all hours
            return currentHourValue; // Return negative to subtract from total
        }
        return Math.max(0, currentHourValue); // Keep positive hours, lose negatives
    }

    /**
     * Extends the card's expiration by additional rounds.
     * Used by Extension helper card.
     */
    public void extendExpiration(int additionalRounds) {
        if (definition.getExpiresAfterRounds() > 0) {
            // Increases the threshold before expiration
            int newExpiry = definition.getExpiresAfterRounds() + additionalRounds;
            hasExpired = false; // Un-expire if it was expired
        }
    }

    /**
     * Adds 1 round to the card (makes it closer to expiring).
     * Used by Tardy weapon card.
     */
    public void addRound() {
        roundsInPlay++;
        if (definition.getExpiresAfterRounds() > 0 &&
                roundsInPlay >= definition.getExpiresAfterRounds()) {
            hasExpired = true;
        }
    }

    /**
     * Resets card to initial state.
     * Used by Amnesia alert card.
     */
    public void reset() {
        roundsInPlay = 0;
        currentHourValue = definition.getImmediateHours();
        hasExpired = false;
    }

    /**
     * Forces card to expire immediately.
     * Used by Deadline weapon and alert cards.
     */
    public void forceExpire() {
        hasExpired = true;
    }

    // Getters and setters
    public GameCard getVisualCard() { return visualCard; }
    public CardDefinition getDefinition() { return definition; }
    public int getOwnerPlayerIndex() { return ownerPlayerIndex; }
    public int getRoundsInPlay() { return roundsInPlay; }
    public int getCurrentHourValue() { return currentHourValue; }
    public void setCurrentHourValue(int value) { this.currentHourValue = value; }
    public void addHours(int hours) { this.currentHourValue += hours; }
    public boolean isProtectedByNepotism() { return isProtectedByNepotism; }
    public void setProtectedByNepotism(boolean protected_) { this.isProtectedByNepotism = protected_; }
    public boolean hasExpired() { return hasExpired; }

    // Sharing is Caring linking
    public int getLinkedPlayerIndex() { return linkedPlayerIndex; }
    public void setLinkedPlayerIndex(int index) { this.linkedPlayerIndex = index; }
    public PlayedCard getLinkedCard() { return linkedCard; }
    public void setLinkedCard(PlayedCard card) { this.linkedCard = card; }

    // Parasite tracking
    public int getAttackerPlayerIndex() { return attackerPlayerIndex; }
    public void setAttackerPlayerIndex(int index) { this.attackerPlayerIndex = index; }

    public int getRoundsUntilExpiry() {
        if (definition.getExpiresAfterRounds() == 0) return -1; // Never expires
        return Math.max(0, definition.getExpiresAfterRounds() - roundsInPlay);
    }

    @Override
    public String toString() {
        return String.format("%s (Round %d/%d, Hours: %d)",
                definition.getDisplayName(),
                roundsInPlay,
                definition.getExpiresAfterRounds(),
                currentHourValue);
    }
}
package net.silverfishstone.procrastination.game;

/**
 * Defines all card types, their effects, and behaviors in the game.
 * 
 * Card Categories:
 * - PLAY: Basic cards that generate hours over time
 * - WEAPON: Cards played on opponents (or self) with negative effects
 * - HELPER: Special cards that can be played at any time
 * - ALERT: Must be played immediately, affect all players
 */
public enum CardDefinition {
    
    // ========== PLAY CARDS ==========
    
    ON_THE_CLOCK(
        "On the Clock",
        CardCategory.PLAY,
        1,      // hoursPerRound
        0,      // immediateHours
        5,      // expiresAfterRounds
        "On the Clock: +1 hour each round, expires after 5 rounds"
    ),
    
    PROFESSIONAL(
        "Professional",
        CardCategory.PLAY,
        0,      // hoursPerRound (none until round 8)
        0,      // immediateHours
        9,      // expiresAfterRounds
        "Professional: +10 hours after 8 rounds, expires on 9th"
    ),
    
    RISKY(
        "Risky",
        CardCategory.PLAY,
        1,      // hoursPerRound
        -5,     // immediateHours (negative!)
        9,      // expiresAfterRounds
        "Risky: -5 hours now or +1 per round. +8 bonus on 8th round, expires on 9th"
    ),
    
    SHARING_IS_CARING(
        "Sharing is Caring",
        CardCategory.PLAY,
        0,      // hoursPerRound (special: mirrors another player)
        0,      // immediateHours
        9,      // expiresAfterRounds
        "Sharing is Caring: +1 hour for every hour another player gains. Expires after 9 rounds or if linked card expires"
    ),
    
    UNPREDICTABLE(
        "Unpredictable",
        CardCategory.PLAY,
        0,      // hoursPerRound (alternates, handled specially)
        0,      // immediateHours
        9,      // expiresAfterRounds
        "Unpredictable: Alternating +/-. +1 hour per 2 rounds. Expires after 9 rounds"
    ),
    
    // ========== WEAPON CARDS ==========
    
    TARDY(
        "Tardy",
        CardCategory.WEAPON,
        0, 0, 0,
        "Tardy: Add 1 round to expiration of any card (immediate effect)"
    ),
    
    DEADLINE(
        "Deadline",
        CardCategory.WEAPON,
        0, 0, 0,
        "Deadline: Immediately expire 1 card in play by another player"
    ),
    
    STOCK_MARKET(
        "Stock Market",
        CardCategory.WEAPON,
        1,      // hoursPerRound
        -4,     // immediateHours (negative weapon!)
        5,      // expiresAfterRounds
        "Stock Market: -4 hours or +1 per round. Expires after 5 rounds (Play Weapon)"
    ),
    
    SCAMMER(
        "Scammer",
        CardCategory.WEAPON,
        0, 0, 0,
        "Scammer: Steal 1 hour from any player (immediate effect)"
    ),
    
    QUIT(
        "Quit",
        CardCategory.WEAPON,
        0, 0, 0,
        "Quit: Receiver must discard random card. Draw to 5 if from hand (immediate effect)"
    ),
    
    PARASITE(
        "Parasite",
        CardCategory.WEAPON,
        -1,     // hoursPerRound (negative! drains from attacker back to receiver)
        4,      // immediateHours (attacker gains 4, receiver loses 4)
        5,      // expiresAfterRounds
        "Parasite: You gain +4 hours from receiver, or -1 per round. Expires after 5 rounds (Play Weapon)"
    ),
    
    DOWNSIZING(
        "Downsizing",
        CardCategory.WEAPON,
        1,      // hoursPerRound
        -4,     // immediateHours
        5,      // expiresAfterRounds
        "Downsizing: -4 hours or +1 per round. Passes to next player when discarded (Rolling Weapon)"
    ),
    
    FOREIGN_EXCHANGE(
        "Foreign Exchange",
        CardCategory.WEAPON,
        0, 0, 0,
        "Foreign Exchange: Trade one card with another player (immediate effect)"
    ),
    
    // ========== HELPER CARDS ==========
    
    EXCUSED(
        "Excused",
        CardCategory.HELPER,
        0, 0, 0,
        "Excused: Discard or deflect any weapon (except Alert cards)"
    ),
    
    EXTENSION(
        "Extension",
        CardCategory.HELPER,
        0, 0, 0,
        "Extension: Delays expiration of any card by 5 rounds"
    ),
    
    NEPOTISM(
        "Nepotism",
        CardCategory.HELPER,
        0, 0, 0,
        "Nepotism: Protect card from expiration (no hours gained after original expiry)"
    ),
    
    NEWBIE(
        "Newbie",
        CardCategory.HELPER,
        0, 0, 0,
        "Newbie: Discard all cards in hand (no actions) and draw new cards"
    ),
    
    // ========== ALERT CARDS ==========
    
    AMNESIA(
        "Amnesia",
        CardCategory.ALERT,
        0, 0, 0,
        "Amnesia: All cards in play reset to original values and deadlines"
    ),
    
    FIRED(
        "Fired!",
        CardCategory.ALERT,
        0, 0, 0,
        "Fired!: Immediately expires all played cards of the player who drew it"
    ),
    
    PERFORMANCE_REVIEW(
        "Performance Review",
        CardCategory.ALERT,
        0, 0, 0,
        "Performance Review: Discard all cards in hand (perform actions) and redraw"
    ),
    
    RECESSION(
        "Recession",
        CardCategory.ALERT,
        0, 0, 0,
        "Recession: All cards of all players expire immediately"
    );
    
    // ========== PROPERTIES ==========
    
    private final String displayName;
    private final CardCategory category;
    private final int hoursPerRound;
    private final int immediateHours;
    private final int expiresAfterRounds;
    private final String description;
    
    CardDefinition(String displayName, CardCategory category, int hoursPerRound, 
                   int immediateHours, int expiresAfterRounds, String description) {
        this.displayName = displayName;
        this.category = category;
        this.hoursPerRound = hoursPerRound;
        this.immediateHours = immediateHours;
        this.expiresAfterRounds = expiresAfterRounds;
        this.description = description;
    }
    
    public String getDisplayName() { return displayName; }
    public CardCategory getCategory() { return category; }
    public int getHoursPerRound() { return hoursPerRound; }
    public int getImmediateHours() { return immediateHours; }
    public int getExpiresAfterRounds() { return expiresAfterRounds; }
    public String getDescription() { return description; }
    
    public boolean isPlayCard() { return category == CardCategory.PLAY; }
    public boolean isWeaponCard() { return category == CardCategory.WEAPON; }
    public boolean isHelperCard() { return category == CardCategory.HELPER; }
    public boolean isAlertCard() { return category == CardCategory.ALERT; }
    
    /**
     * Returns whether this weapon card is a "Play Weapon" (stays in play) vs immediate effect
     */
    public boolean isPlayWeapon() {
        return isWeaponCard() && expiresAfterRounds > 0;
    }
    
    /**
     * Returns whether this is a rolling weapon (passes to next player)
     */
    public boolean isRollingWeapon() {
        return this == DOWNSIZING;
    }
    
    /**
     * Special cards with unique mechanics
     */
    public boolean hasSharingMechanic() { return this == SHARING_IS_CARING; }
    public boolean hasAlternatingMechanic() { return this == UNPREDICTABLE; }
    public boolean hasProfessionalBonus() { return this == PROFESSIONAL; }
    public boolean hasRiskyBonus() { return this == RISKY; }
    public boolean hasParasiteMechanic() { return this == PARASITE; }
    
    public enum CardCategory {
        PLAY,
        WEAPON,
        HELPER,
        ALERT
    }
}

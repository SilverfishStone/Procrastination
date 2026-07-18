package com.workgame.model;

import java.util.Random;

/**
 * Generates cards on demand using weighted random selection.
 * The deck is effectively infinite — cards are never exhausted.
 *
 * Weights:  Play 60% | Weapon 25% | Helper 10% | Alert 5%
 * Downsizing (rolling weapon) is excluded per game rules.
 */
public class DeckFactory {

    private static final Random RNG = new Random();

    // ── Card name pools ───────────────────────────────────────────────────────

    // Play cards: On the Clock weighted highest, Professional/Risky equal second
    private static final String[] PLAY_NAMES = {
            "On the Clock", "On the Clock", "On the Clock",   // 3x
            "Professional", "Professional",                   // 2x
            "Risky", "Risky",                                 // 2x
            "Sharing is Caring", "Unpredictable"              // 1x each
    };

    // Weapon cards: Stock Market slightly higher
    private static final String[] WEAPON_NAMES = {
            "Tardy", "Deadline",
            "Stock Market", "Stock Market",  // 2x weight
            "Scammer", "Quit", "Parasite", "Foreign Exchange"
    };

    private static final String[] HELPER_NAMES = {
            "Excused", "Extension", "Nepotism", "Newbie"
    };

    private static final String[] ALERT_NAMES = {
            "Amnesia", "Fired!", "Performance Review", "Recession"
    };

    // ── Weights ───────────────────────────────────────────────────────────────

    private static final int W_PLAY   = 60;
    private static final int W_WEAPON = 25;
    private static final int W_HELPER = 10;
    private static final int W_ALERT  = 5;
    private static final int W_TOTAL  = W_PLAY + W_WEAPON + W_HELPER + W_ALERT;

    // ── Public API ────────────────────────────────────────────────────────────

    /** Generate one random card according to the weighted distribution. */
    public static Card generateCard() {
        int roll = RNG.nextInt(W_TOTAL);
        if (roll < W_PLAY)                        return randomPlay();
        if (roll < W_PLAY + W_WEAPON)             return randomWeapon();
        if (roll < W_PLAY + W_WEAPON + W_HELPER)  return randomHelper();
        return randomAlert();
    }

    /** Generate a card that is guaranteed NOT to be an Alert (for initial deal). */
    public static Card generateNonAlert() {
        // Re-roll until non-alert (fast in practice — only 5% chance of alert)
        Card c;
        do { c = generateCard(); } while (c.getType() == CardType.ALERT);
        return c;
    }

    /** Hour pool starts large enough that it effectively never runs dry. */
    public static int buildHourPool(int playerCount) {
        return playerCount * Player.STARTING_HOURS + 200;
    }

    // ── Card constructors ─────────────────────────────────────────────────────

    private static Card randomPlay() {
        String name = pick(PLAY_NAMES);
        return buildPlay(name);
    }

    public static Card buildPlay(String name) {
        return switch (name) {
            case "On the Clock"     -> new Card(name, CardType.PLAY,
                    "+1 hour each round. Expires after 5 rounds.",
                    0, 1, 5, 0);
            case "Professional"     -> new Card(name, CardType.PLAY,
                    "+10 hours when discarded on round 8. No hourly gain before then. Expires on round 9.",
                    0, 0, 9, 10);
            case "Risky"            -> new Card(name, CardType.PLAY,
                    "-5 hours now, +1/round. Discard on round 8 for +8 bonus. Expires round 9.",
                    -5, 1, 9, 8);
            case "Sharing is Caring"-> new Card(name, CardType.PLAY,
                    "+1 hour for every hour a chosen player gains. Expires if any linked card expires. Expires round 9.",
                    0, 0, 9, 0);
            case "Unpredictable"    -> new Card(name, CardType.PLAY,
                    "Alternates +1/-1 each round. Net +1 hour per 2 rounds. Expires round 9.",
                    0, 0, 9, 0);
            default -> new Card(name, CardType.PLAY, name, 0, 0, 0, 0);
        };
    }

    private static Card randomWeapon() {
        String name = pick(WEAPON_NAMES);
        return buildWeapon(name);
    }

    public static Card buildWeapon(String name) {
        return switch (name) {
            case "Tardy"           -> new Card(name, CardType.WEAPON_IMMEDIATE,
                    "Add 1 round to any card in play.",
                    0, 0, 0, 0);
            case "Deadline"        -> new Card(name, CardType.WEAPON_IMMEDIATE,
                    "Immediately expire one card in play of another player.",
                    0, 0, 0, 0);
            case "Stock Market"    -> new Card(name, CardType.WEAPON_PLAY,
                    "-4 hours now, +1/round. Expires round 5.",
                    -4, 1, 5, 0);
            case "Scammer"         -> new Card(name, CardType.WEAPON_IMMEDIATE,
                    "Steal 1 hour from any player.",
                    0, 0, 0, 0);
            case "Quit"            -> new Card(name, CardType.WEAPON_IMMEDIATE,
                    "Target player discards a card of your choosing. They draw to 5 next turn if from hand.",
                    0, 0, 0, 0);
            case "Parasite"        -> new Card(name, CardType.WEAPON_PLAY,
                    "Receiver loses 4 hours now; you gain them. Receiver gains +1/round back. Expires round 5.",
                    -4, 1, 5, 0);
            case "Foreign Exchange"-> new Card(name, CardType.WEAPON_IMMEDIATE,
                    "Trade one random card from your hand with one random card from another player's hand.",
                    0, 0, 0, 0);
            default -> new Card(name, CardType.WEAPON_IMMEDIATE, name, 0, 0, 0, 0);
        };
    }

    private static Card randomHelper() {
        String name = pick(HELPER_NAMES);
        return buildHelper(name);
    }

    public static Card buildHelper(String name) {
        return switch (name) {
            case "Excused"   -> new Card(name, CardType.HELPER,
                    "Discard or deflect any weapon card (except Alert cards).",
                    0, 0, 0, 0);
            case "Extension" -> new Card(name, CardType.HELPER,
                    "Delay the expiration of any card by 5 rounds.",
                    0, 0, 0, 0);
            case "Nepotism"  -> new Card(name, CardType.HELPER,
                    "Protect any card from expiration. That card can no longer gain hours after its original expiry round.",
                    0, 0, 0, 0);
            case "Newbie"    -> new Card(name, CardType.HELPER,
                    "Dead hand: discard all cards in hand (no actions performed) and draw new ones.",
                    0, 0, 0, 0);
            default -> new Card(name, CardType.HELPER, name, 0, 0, 0, 0);
        };
    }

    private static Card randomAlert() {
        String name = pick(ALERT_NAMES);
        return buildAlert(name);
    }

    public static Card buildAlert(String name) {
        return switch (name) {
            case "Amnesia"             -> new Card(name, CardType.ALERT,
                    "All cards in play reset to original hour values and deadlines.",
                    0, 0, 0, 0);
            case "Fired!"              -> new Card(name, CardType.ALERT,
                    "Immediately expires all played cards of the player who drew this.",
                    0, 0, 0, 0);
            case "Performance Review"  -> new Card(name, CardType.ALERT,
                    "Player immediately discards hand (performing written actions) and redraws. This counts as their turn.",
                    0, 0, 0, 0);
            case "Recession"           -> new Card(name, CardType.ALERT,
                    "All played cards of all players expire immediately.",
                    0, 0, 0, 0);
            default -> new Card(name, CardType.ALERT, name, 0, 0, 0, 0);
        };
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static String pick(String[] arr) {
        return arr[RNG.nextInt(arr.length)];
    }
}
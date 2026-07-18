package com.workgame.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Accumulates statistics for a single game session.
 * Mutated by the controller throughout the game; read by StatsScreen at game end.
 */
public class GameStats {

    // ── Identity ──────────────────────────────────────────────────────────────
    private final String timestamp;
    private final List<String> playerNames;
    private final List<Player.PlayerType> playerTypes;
    private int roundsPlayed;
    private String winnerName;
    private int winnerHours;

    // ── Card counts ───────────────────────────────────────────────────────────
    private int cardsDrawn     = 0;
    private int playCardsPlayed = 0;
    private int weaponsPlayed  = 0;
    private int helpersUsed    = 0;
    private int alertsTriggered = 0;
    private int cardsDiscarded  = 0;
    private int cardsExpired    = 0;

    // Per-card-name play counts
    private final Map<String, Integer> cardPlayCounts  = new LinkedHashMap<>();
    private final Map<String, Integer> cardDrawCounts  = new LinkedHashMap<>();

    // ── Hour economy ──────────────────────────────────────────────────────────
    private int totalHoursGained = 0;  // across all players
    private int totalHoursLost   = 0;
    private int totalHoursTransferred = 0; // weapon/parasite transfers

    // ── Per-player stats ──────────────────────────────────────────────────────
    private final Map<String, PlayerStat> playerStats = new LinkedHashMap<>();

    public static class PlayerStat {
        public final String name;
        public final boolean isCpu;
        public int hoursGained    = 0;
        public int hoursLost      = 0;
        public int cardsPlayed    = 0;
        public int cardsDiscarded = 0;
        public int weaponsPlayed  = 0;
        public int helpersUsed    = 0;
        public int finalHours     = 0;
        public int rank           = 0;

        public PlayerStat(String name, boolean isCpu) {
            this.name  = name;
            this.isCpu = isCpu;
        }
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public GameStats(List<Player> players) {
        this.timestamp   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, HH:mm"));
        this.playerNames = new ArrayList<>();
        this.playerTypes = new ArrayList<>();
        for (Player p : players) {
            playerNames.add(p.getName());
            playerTypes.add(p.getPlayerType());
            playerStats.put(p.getName(), new PlayerStat(p.getName(), !p.isHuman()));
        }
    }

    // ── Mutation methods (called by controller) ───────────────────────────────

    public void recordDraw(String cardName) {
        cardsDrawn++;
        cardDrawCounts.merge(cardName, 1, Integer::sum);
    }

    public void recordPlay(String playerName, String cardName, boolean isWeapon, boolean isHelper, boolean isAlert) {
        cardPlayCounts.merge(cardName, 1, Integer::sum);
        PlayerStat ps = playerStats.get(playerName);
        if (ps != null) {
            ps.cardsPlayed++;
            if (isWeapon) { weaponsPlayed++; ps.weaponsPlayed++; }
            else if (isHelper) { helpersUsed++; ps.helpersUsed++; }
            else if (isAlert) alertsTriggered++;
            else playCardsPlayed++;
        }
    }

    public void recordDiscard(String playerName) {
        cardsDiscarded++;
        PlayerStat ps = playerStats.get(playerName);
        if (ps != null) ps.cardsDiscarded++;
    }

    public void recordExpiry() { cardsExpired++; }

    public void recordHoursGained(String playerName, int hours) {
        if (hours <= 0) return;
        totalHoursGained += hours;
        PlayerStat ps = playerStats.get(playerName);
        if (ps != null) ps.hoursGained += hours;
    }

    public void recordHoursLost(String playerName, int hours) {
        if (hours <= 0) return;
        totalHoursLost += hours;
        PlayerStat ps = playerStats.get(playerName);
        if (ps != null) ps.hoursLost += hours;
    }

    public void recordTransfer(int hours) {
        totalHoursTransferred += Math.abs(hours);
    }

    public void finalize(List<Player> players, int rounds) {
        this.roundsPlayed = rounds;
        List<Player> sorted = new ArrayList<>(players);
        sorted.sort((a, b) -> Integer.compare(b.getHourTokens(), a.getHourTokens()));
        winnerName  = sorted.get(0).getName();
        winnerHours = sorted.get(0).getHourTokens();
        for (int i = 0; i < sorted.size(); i++) {
            Player p = sorted.get(i);
            PlayerStat ps = playerStats.get(p.getName());
            if (ps != null) { ps.finalHours = p.getHourTokens(); ps.rank = i + 1; }
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getTimestamp()        { return timestamp; }
    public String getWinnerName()       { return winnerName; }
    public int    getWinnerHours()      { return winnerHours; }
    public int    getRoundsPlayed()     { return roundsPlayed; }
    public int    getCardsDrawn()       { return cardsDrawn; }
    public int    getPlayCardsPlayed()  { return playCardsPlayed; }
    public int    getWeaponsPlayed()    { return weaponsPlayed; }
    public int    getHelpersUsed()      { return helpersUsed; }
    public int    getAlertsTriggered()  { return alertsTriggered; }
    public int    getCardsDiscarded()   { return cardsDiscarded; }
    public int    getCardsExpired()     { return cardsExpired; }
    public int    getTotalHoursGained() { return totalHoursGained; }
    public int    getTotalHoursLost()   { return totalHoursLost; }
    public int    getTotalHoursTransferred() { return totalHoursTransferred; }

    public Map<String, Integer> getCardPlayCounts() { return cardPlayCounts; }
    public Map<String, Integer> getCardDrawCounts() { return cardDrawCounts; }
    public Collection<PlayerStat> getPlayerStats()  { return playerStats.values(); }
    public List<String> getPlayerNames()            { return playerNames; }

    public String getSummaryLine() {
        return timestamp + "  —  Winner: " + winnerName + " (" + winnerHours + "h)  —  " + roundsPlayed + " rounds";
    }
}

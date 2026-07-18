package com.workgame.model;

import java.util.*;

/**
 * Central game-state object.  The controller mutates this; the UI reads it.
 */
public class GameState {

    public enum Phase { SETUP, PLAYER_TURN, GAME_OVER }

    private final List<Player> players;
    private final Deque<Card>  drawPile   = new ArrayDeque<>();
    private final List<Card>   discardPile = new ArrayList<>();

    private int  currentPlayerIndex = 0;
    private int  globalRound        = 1;  // increments after every player has taken a turn
    private Phase phase             = Phase.SETUP;
    private int  hourPoolRemaining;       // draw-pile hour tokens

    // Per-turn state
    private boolean cardDrawnThisTurn    = false;
    private boolean actionTakenThisTurn  = false;
    private int     pendingResolutions   = 0;   // # of target dialogs still open
    private String  lastMessage          = "";

    // "Sharing is Caring" links: key = card, value = target player
    private final Map<Card, Player> sharingLinks = new LinkedHashMap<>();

    // Stats accumulator for this game
    private final com.workgame.model.GameStats stats;

    public GameState(List<String> playerNames) {
        this(playerNames, null);
    }

    public GameState(List<String> playerNames, List<Player.PlayerType> playerTypes) {
        players = new ArrayList<>();
        for (int i = 0; i < playerNames.size(); i++) {
            Player p = new Player(playerNames.get(i));
            if (playerTypes != null && i < playerTypes.size())
                p.setPlayerType(playerTypes.get(i));
            players.add(p);
        }
        stats = new com.workgame.model.GameStats(players);
        refillDrawPile(40);
        hourPoolRemaining = DeckFactory.buildHourPool(players.size());
    }

    public com.workgame.model.GameStats getStats() { return stats; }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public List<Player>  getPlayers()              { return players; }
    public Deque<Card>   getDrawPile()             { return drawPile; }
    public List<Card>    getDiscardPile()          { return discardPile; }
    public int           getCurrentPlayerIndex()   { return currentPlayerIndex; }
    public Player        getCurrentPlayer()        { return players.get(currentPlayerIndex); }
    public int           getGlobalRound()          { return globalRound; }
    public Phase         getPhase()                { return phase; }
    public int           getHourPoolRemaining()    { return hourPoolRemaining; }
    public boolean       isCardDrawnThisTurn()     { return cardDrawnThisTurn; }
    public boolean       isActionTakenThisTurn()   { return actionTakenThisTurn; }
    public String        getLastMessage()          { return lastMessage; }
    public Map<Card,Player> getSharingLinks()      { return sharingLinks; }

    public void setPhase(Phase phase)              { this.phase = phase; }
    public void setLastMessage(String msg)         { this.lastMessage = msg; }
    public void setCardDrawnThisTurn(boolean v)    { this.cardDrawnThisTurn = v; }
    public void setActionTakenThisTurn(boolean v)  { this.actionTakenThisTurn = v; }
    public int  getPendingResolutions()            { return pendingResolutions; }
    public void pushPendingResolution()            { pendingResolutions++; }
    public void popPendingResolution()             { if (pendingResolutions > 0) pendingResolutions--; }
    public boolean hasPendingResolutions()         { return pendingResolutions > 0; }

    // ── Turn management ──────────────────────────────────────────────────────

    public void nextPlayer() {
        cardDrawnThisTurn   = false;
        actionTakenThisTurn = false;
        pendingResolutions  = 0;
        currentPlayerIndex  = (currentPlayerIndex + 1) % players.size();
        if (currentPlayerIndex == 0) globalRound++;
    }

    // ── Draw pile ─────────────────────────────────────────────────────────────

    /**
     * Draw the top card from the pile.
     * If the pile is running low, top it up with newly generated cards.
     * During initial deal, alert cards are skipped (regenerated).
     */
    public Card drawCard(boolean initialDeal) {
        if (drawPile.size() < 10) refillDrawPile(30);

        Card card = drawPile.poll();
        if (card == null) card = DeckFactory.generateCard();

        if (initialDeal && card.getType() == CardType.ALERT) {
            // Don't put alert back in pile — just generate a replacement
            return drawCard(true);
        }
        return card;
    }

    private void refillDrawPile(int count) {
        for (int i = 0; i < count; i++) drawPile.addLast(DeckFactory.generateCard());
    }

    public void discard(Card card) {
        discardPile.add(card);
    }

    /** Deck size shown in HUD — always shows pile size, never zero. */
    public int drawPileSize() { return drawPile.size(); }

    // ── Hour pool ─────────────────────────────────────────────────────────────

    public int takeFromHourPool(int n) {
        int taken = Math.min(n, hourPoolRemaining);
        hourPoolRemaining -= taken;
        return taken;
    }

    public void returnToHourPool(int n) { hourPoolRemaining += n; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public Player getNextPlayer(Player p) {
        int idx = players.indexOf(p);
        return players.get((idx + 1) % players.size());
    }

    public Player getPreviousPlayer(Player p) {
        int idx = players.indexOf(p);
        return players.get((idx - 1 + players.size()) % players.size());
    }

    private int maxRounds = 20; // configurable

    public int  getMaxRounds()           { return maxRounds; }
    public void setMaxRounds(int rounds) { this.maxRounds = rounds; }

    public boolean isGameOver() {
        return globalRound > maxRounds;
    }
}
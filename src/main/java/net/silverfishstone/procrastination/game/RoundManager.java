package net.silverfishstone.procrastination.game;

import net.silverfishstone.procrastination.components.PlayedCard;

import java.util.*;

/**
 * Manages round progression and processes all card effects each round.
 * 
 * Core responsibilities:
 * - Track current round number
 * - Process hour gains/losses for all played cards
 * - Handle card expirations
 * - Manage special card interactions (Sharing, Unpredictable, etc.)
 */
public class RoundManager {
    
    private int currentRound = 0;
    private List<PlayerState> playerStates;
    
    public RoundManager(int numPlayers) {
        playerStates = new ArrayList<>();
        for (int i = 0; i < numPlayers; i++) {
            playerStates.add(new PlayerState(i));
        }
    }
    
    /**
     * Advances to the next round and processes all card effects.
     * Returns a report of what happened.
     */
    public RoundReport advanceRound() {
        currentRound++;
        RoundReport report = new RoundReport(currentRound);
        
        System.out.println("\n========== ROUND " + currentRound + " ==========");
        
        // First pass: process all normal cards
        for (PlayerState state : playerStates) {
            for (PlayedCard playedCard : state.getCardsInPlay()) {
                int hoursGained = playedCard.processRound();
                
                if (hoursGained != 0) {
                    report.recordHourChange(state.getPlayerIndex(), playedCard, hoursGained);
                    System.out.println("Player " + (state.getPlayerIndex() + 1) + 
                                     " gained " + hoursGained + " hours from " + 
                                     playedCard.getDefinition().getDisplayName());
                }
            }
        }
        
        // Second pass: handle Sharing is Caring cards
        for (PlayerState state : playerStates) {
            for (PlayedCard playedCard : state.getCardsInPlay()) {
                if (playedCard.getDefinition().hasSharingMechanic() && 
                    playedCard.getLinkedPlayerIndex() >= 0) {
                    
                    int linkedPlayerIdx = playedCard.getLinkedPlayerIndex();
                    if (linkedPlayerIdx < playerStates.size()) {
                        PlayerState linkedState = playerStates.get(linkedPlayerIdx);
                        int totalLinkedHours = 0;
                        
                        // Sum up all hours gained by linked player this round
                        for (PlayedCard linkedCard : linkedState.getCardsInPlay()) {
                            if (linkedCard.getRoundsInPlay() == playedCard.getRoundsInPlay()) {
                                // Same round = just processed
                                int gained = linkedCard.getDefinition().getHoursPerRound();
                                totalLinkedHours += gained;
                            }
                        }
                        
                        if (totalLinkedHours > 0) {
                            playedCard.addHours(totalLinkedHours);
                            report.recordHourChange(state.getPlayerIndex(), playedCard, totalLinkedHours);
                            System.out.println("Player " + (state.getPlayerIndex() + 1) + 
                                             " gained " + totalLinkedHours + 
                                             " shared hours from Player " + (linkedPlayerIdx + 1));
                        }
                    }
                }
            }
        }
        
        // Third pass: check for expirations
        for (PlayerState state : playerStates) {
            List<PlayedCard> toExpire = new ArrayList<>();
            
            for (PlayedCard playedCard : state.getCardsInPlay()) {
                if (playedCard.shouldAutoDiscard()) {
                    toExpire.add(playedCard);
                    report.recordExpiration(state.getPlayerIndex(), playedCard);
                    
                    int finalHours = playedCard.getFinalHourValue();
                    if (finalHours < 0) {
                        state.addHours(finalHours); // Lose hours
                        System.out.println("Player " + (state.getPlayerIndex() + 1) + 
                                         " loses " + Math.abs(finalHours) + " hours from expired " + 
                                         playedCard.getDefinition().getDisplayName());
                    } else {
                        state.addHours(finalHours); // Keep positive hours
                        System.out.println("Player " + (state.getPlayerIndex() + 1) + 
                                         " keeps " + finalHours + " hours from expired " + 
                                         playedCard.getDefinition().getDisplayName());
                    }
                }
            }
            
            // Remove expired cards
            for (PlayedCard expired : toExpire) {
                state.removeCardInPlay(expired);
            }
        }
        
        // Fourth pass: check Sharing is Caring for expired linked cards
        for (PlayerState state : playerStates) {
            List<PlayedCard> toExpire = new ArrayList<>();
            
            for (PlayedCard playedCard : state.getCardsInPlay()) {
                if (playedCard.getDefinition().hasSharingMechanic()) {
                    PlayedCard linked = playedCard.getLinkedCard();
                    if (linked != null && linked.hasExpired()) {
                        // Linked card expired - this card voids
                        playedCard.forceExpire();
                        playedCard.setCurrentHourValue(0); // Hours voided
                        toExpire.add(playedCard);
                        System.out.println("Player " + (state.getPlayerIndex() + 1) + 
                                         "'s Sharing card voided due to linked card expiring");
                    }
                }
            }
            
            for (PlayedCard expired : toExpire) {
                state.removeCardInPlay(expired);
            }
        }
        
        return report;
    }
    
    /**
     * Adds a card to a player's in-play area.
     */
    public void addCardToPlay(int playerIndex, PlayedCard card) {
        if (playerIndex >= 0 && playerIndex < playerStates.size()) {
            playerStates.get(playerIndex).addCardInPlay(card);
        }
    }
    
    /**
     * Removes a card from play (when player manually discards).
     */
    public void removeCardFromPlay(int playerIndex, PlayedCard card) {
        if (playerIndex >= 0 && playerIndex < playerStates.size()) {
            playerStates.get(playerIndex).removeCardInPlay(card);
            
            // Handle rolling weapons
            if (card.getDefinition().isRollingWeapon()) {
                int nextPlayer = (playerIndex + 1) % playerStates.size();
                // Card passes to next player (handled by game controller)
            }
        }
    }
    
    /**
     * Gets all cards currently in play for a player.
     */
    public List<PlayedCard> getCardsInPlay(int playerIndex) {
        if (playerIndex >= 0 && playerIndex < playerStates.size()) {
            return new ArrayList<>(playerStates.get(playerIndex).getCardsInPlay());
        }
        return new ArrayList<>();
    }
    
    /**
     * Returns how many cards a player has in play.
     */
    public int getCardCountInPlay(int playerIndex) {
        if (playerIndex >= 0 && playerIndex < playerStates.size()) {
            return playerStates.get(playerIndex).getCardsInPlay().size();
        }
        return 0;
    }
    
    /**
     * Resets all cards in play for all players (Amnesia effect).
     */
    public void resetAllCards() {
        for (PlayerState state : playerStates) {
            for (PlayedCard card : state.getCardsInPlay()) {
                card.reset();
            }
        }
        System.out.println("AMNESIA: All cards reset to original values!");
    }
    
    /**
     * Expires all cards for a specific player (Fired effect).
     */
    public void expireAllCardsForPlayer(int playerIndex) {
        if (playerIndex >= 0 && playerIndex < playerStates.size()) {
            PlayerState state = playerStates.get(playerIndex);
            List<PlayedCard> toRemove = new ArrayList<>();
            
            for (PlayedCard card : state.getCardsInPlay()) {
                int finalHours = card.getFinalHourValue();
                state.addHours(finalHours);
                toRemove.add(card);
            }
            
            for (PlayedCard card : toRemove) {
                state.removeCardInPlay(card);
            }
            
            System.out.println("FIRED: Player " + (playerIndex + 1) + "'s cards all expired!");
        }
    }
    
    /**
     * Expires all cards for all players (Recession effect).
     */
    public void expireAllCards() {
        for (PlayerState state : playerStates) {
            List<PlayedCard> toRemove = new ArrayList<>(state.getCardsInPlay());
            
            for (PlayedCard card : toRemove) {
                int finalHours = card.getFinalHourValue();
                state.addHours(finalHours);
                state.removeCardInPlay(card);
            }
        }
        System.out.println("RECESSION: All cards expired!");
    }
    
    public int getCurrentRound() { return currentRound; }
    
    public PlayerState getPlayerState(int index) {
        if (index >= 0 && index < playerStates.size()) {
            return playerStates.get(index);
        }
        return null;
    }
    
    /**
     * Tracks state for a single player.
     */
    public static class PlayerState {
        private final int playerIndex;
        private List<PlayedCard> cardsInPlay = new ArrayList<>();
        private int totalHours = 0;
        
        public PlayerState(int playerIndex) {
            this.playerIndex = playerIndex;
        }
        
        public void addCardInPlay(PlayedCard card) {
            cardsInPlay.add(card);
        }
        
        public void removeCardInPlay(PlayedCard card) {
            cardsInPlay.remove(card);
        }
        
        public List<PlayedCard> getCardsInPlay() {
            return cardsInPlay;
        }
        
        public int getPlayerIndex() { return playerIndex; }
        public int getTotalHours() { return totalHours; }
        public void addHours(int hours) { this.totalHours += hours; }
        public void setHours(int hours) { this.totalHours = hours; }
    }
    
    /**
     * Report of what happened during a round.
     */
    public static class RoundReport {
        private final int roundNumber;
        private Map<Integer, List<String>> playerEvents = new HashMap<>();
        
        public RoundReport(int roundNumber) {
            this.roundNumber = roundNumber;
        }
        
        public void recordHourChange(int playerIndex, PlayedCard card, int hours) {
            playerEvents.computeIfAbsent(playerIndex, k -> new ArrayList<>())
                       .add(card.getDefinition().getDisplayName() + ": " + 
                            (hours >= 0 ? "+" : "") + hours + " hours");
        }
        
        public void recordExpiration(int playerIndex, PlayedCard card) {
            playerEvents.computeIfAbsent(playerIndex, k -> new ArrayList<>())
                       .add(card.getDefinition().getDisplayName() + " EXPIRED");
        }
        
        public int getRoundNumber() { return roundNumber; }
        public Map<Integer, List<String>> getPlayerEvents() { return playerEvents; }
    }
}

package net.silverfishstone.procrastination;

import net.silverfishstone.procrastination.components.CardDefinition;
import net.silverfishstone.procrastination.components.GameCard;
import javafx.scene.image.Image;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DeckBuilder creates a fixed 80-card deck with balanced distribution.
 * 
 * Card Distribution:
 * - 32 Play Cards (40%)
 * - 24 Weapon Cards (30%)
 * - 16 Helper Cards (20%)
 * - 8 Alert Cards (10%)
 * 
 * This replaces random card generation to ensure balanced gameplay.
 */
public class DeckBuilder {
    
    private final Image cardBack;
    
    public DeckBuilder(Image cardBack) {
        this.cardBack = cardBack;
    }
    
    /**
     * Creates a complete shuffled deck of 80 cards
     * @return List of shuffled GameCards
     */
    public List<GameCard> createDeck() {
        List<GameCard> deck = new ArrayList<>();
        
        // PLAY CARDS (32 total = 40%)
        addCards(deck, CardDefinition.ON_THE_CLOCK, 10);      // Most common income
        addCards(deck, CardDefinition.PROFESSIONAL, 6);        // Reliable long-term
        addCards(deck, CardDefinition.RISKY, 6);               // Risk/reward
        addCards(deck, CardDefinition.SHARING_IS_CARING, 5);   // Strategic copying
        addCards(deck, CardDefinition.UNPREDICTABLE, 5);       // Interesting variance
        
        // WEAPON CARDS (24 total = 30%)
        // Immediate weapons
        addCards(deck, CardDefinition.TARDY, 4);               // Extend deadline
        addCards(deck, CardDefinition.DEADLINE, 4);            // Force expire
        addCards(deck, CardDefinition.SCAMMER, 4);             // Steal hours
        addCards(deck, CardDefinition.QUIT, 3);                // Random discard
        addCards(deck, CardDefinition.FOREIGN_EXCHANGE, 1);    // Rare powerful card
        
        // Play weapons (stay on board)
        addCards(deck, CardDefinition.STOCK_MARKET, 3);        // Negative income
        addCards(deck, CardDefinition.PARASITE, 3);            // Hour transfer
        addCards(deck, CardDefinition.DOWNSIZING, 2);          // Rolling weapon
        
        // HELPER CARDS (16 total = 20%)
        addCards(deck, CardDefinition.EXCUSED, 6);             // Defense is critical
        addCards(deck, CardDefinition.EXTENSION, 5);           // Save valuable cards
        addCards(deck, CardDefinition.NEPOTISM, 3);            // Protect from expiry
        addCards(deck, CardDefinition.NEWBIE, 2);              // Emergency reset
        
        // ALERT CARDS (8 total = 10%)
        addCards(deck, CardDefinition.AMNESIA, 3);             // Reset round
        addCards(deck, CardDefinition.FIRED, 2);               // Target elimination
        addCards(deck, CardDefinition.PERFORMANCE_REVIEW, 2);  // Hand refresh
        addCards(deck, CardDefinition.RECESSION, 1);           // Nuclear option - very rare
        
        // Verify total count
        if (deck.size() != 80) {
            throw new IllegalStateException("Deck must contain exactly 80 cards, but has " + deck.size());
        }
        
        // Shuffle the deck
        Collections.shuffle(deck);
        
        return deck;
    }
    
    /**
     * Helper method to add multiple copies of a card to the deck
     */
    private void addCards(List<GameCard> deck, CardDefinition definition, int count) {
        for (int i = 0; i < count; i++) {
            GameCard card = new GameCard(definition, cardBack);
            card.flip(); // Start face down
            deck.add(card);
        }
    }
    
    /**
     * Get the total number of cards in a standard deck
     */
    public static int getDeckSize() {
        return 80;
    }
    
    /**
     * Get card count distribution for debugging/display
     */
    public static String getDistributionInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Standard Deck Distribution (80 cards):\n");
        sb.append("\nPLAY CARDS (32 - 40%):\n");
        sb.append("  On the Clock: 10\n");
        sb.append("  Professional: 6\n");
        sb.append("  Risky: 6\n");
        sb.append("  Sharing is Caring: 5\n");
        sb.append("  Unpredictable: 5\n");
        
        sb.append("\nWEAPON CARDS (24 - 30%):\n");
        sb.append("  Tardy: 4\n");
        sb.append("  Deadline: 4\n");
        sb.append("  Scammer: 4\n");
        sb.append("  Quit: 3\n");
        sb.append("  Stock Market: 3\n");
        sb.append("  Parasite: 3\n");
        sb.append("  Downsizing: 2\n");
        sb.append("  Foreign Exchange: 1 (rare)\n");
        
        sb.append("\nHELPER CARDS (16 - 20%):\n");
        sb.append("  Excused: 6\n");
        sb.append("  Extension: 5\n");
        sb.append("  Nepotism: 3\n");
        sb.append("  Newbie: 2\n");
        
        sb.append("\nALERT CARDS (8 - 10%):\n");
        sb.append("  Amnesia: 3\n");
        sb.append("  Fired: 2\n");
        sb.append("  Performance Review: 2\n");
        sb.append("  Recession: 1 (nuclear option)\n");
        
        return sb.toString();
    }
}

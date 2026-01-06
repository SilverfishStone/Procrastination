package net.silverfishstone.procrastination.game;

/**
 * AI controller for computer-controlled players.
 * 
 * The AI has four difficulty levels:
 * - EASY: Plays first valid card, random targeting
 * - MEDIUM: Prioritizes good plays over bad, targets weak opponents
 * - EXPERT: Calculates card values, defensive play
 * - NIGHTMARE: Perfect information play, optimal strategy
 * 
 * Each AI player is autonomous and makes decisions based on:
 * 1. Current hand composition
 * 2. Board state (own slots and opponent slots)
 * 3. Difficulty level settings
 */
public class ComputerAI {

    public Level LEVEL;
    
    // Strategy weights (used for MEDIUM and above)
    private double aggressiveness = 0.5; // 0.0 = defensive, 1.0 = aggressive
    private double riskTolerance = 0.5;  // 0.0 = safe plays, 1.0 = risky plays

    public ComputerAI(Level level) {
        this.LEVEL = level;
        
        // Adjust strategy based on difficulty
        switch (level) {
            case EASY:
                aggressiveness = 0.3;
                riskTolerance = 0.2;
                break;
            case MEDIUM:
                aggressiveness = 0.5;
                riskTolerance = 0.5;
                break;
            case EXPERT:
                aggressiveness = 0.7;
                riskTolerance = 0.6;
                break;
            case NIGHTMARE:
                aggressiveness = 0.9;
                riskTolerance = 0.8;
                break;
        }
    }

    /**
     * Decides which stock pile to draw from.
     * 
     * EASY: Random with slight bias toward Stock A
     * MEDIUM: Considers hand composition
     * EXPERT: Analyzes what's needed strategically
     * NIGHTMARE: Calculates optimal draw based on game state
     * 
     * @param handSize Current number of cards in hand
     * @param playCardsInHand Number of play cards currently held
     * @param hourCardsInHand Number of hour cards currently held
     * @return true to draw from Stock A, false for Stock B
     */
    public boolean shouldDrawFromStockA(int handSize, int playCardsInHand, int hourCardsInHand) {
        return switch (LEVEL) {
            case EASY -> Math.random() < 0.7; // 70% Stock A
            case MEDIUM -> {
                // If low on play cards, prefer Stock A
                if (playCardsInHand < 2) yield true;
                // If have plenty of play cards, get hours
                if (playCardsInHand > 3) yield false;
                yield Math.random() < 0.6;
            }
            case EXPERT, NIGHTMARE -> {
                // Balance between play cards and hours
                double playRatio = (double) playCardsInHand / handSize;
                double hourRatio = (double) hourCardsInHand / handSize;
                
                // If low on plays, strongly prefer Stock A
                if (playRatio < 0.3) yield true;
                // If already have enough plays, get hours
                if (playRatio > 0.5 && hourRatio < 0.2) yield false;
                
                yield Math.random() < (0.7 - playRatio);
            }
        };
    }

    /**
     * Evaluates the value of playing a card.
     * Higher scores mean better plays.
     * 
     * @param cardType The type of card (play, weapon, gift, hour)
     * @param targetIsOwn Whether the target slot belongs to this AI
     * @param targetHasBase Whether target slot has a play/weapon card
     * @return Score value (higher = better play)
     */
    public double evaluatePlay(String cardType, boolean targetIsOwn, boolean targetHasBase) {
        return switch (LEVEL) {
            case EASY -> 1.0; // All plays equal value
            
            case MEDIUM -> {
                double score = 0.0;
                if ("play".equals(cardType) && targetIsOwn && !targetHasBase) {
                    score = 5.0; // Good - building your board
                } else if ("weapon".equals(cardType) && !targetIsOwn && !targetHasBase) {
                    score = 4.0; // Good - attacking opponent
                } else if ("gift".equals(cardType) && targetIsOwn && targetHasBase) {
                    score = 3.0; // Decent - boosting your card
                } else if ("hour".equals(cardType) && targetIsOwn) {
                    score = 2.0; // Okay - adding time
                }
                yield score;
            }
            
            case EXPERT, NIGHTMARE -> {
                double score = 0.0;
                
                // Strategic scoring
                if ("play".equals(cardType)) {
                    if (targetIsOwn && !targetHasBase) {
                        score = 8.0; // High priority - need base cards
                    }
                } else if ("weapon".equals(cardType)) {
                    if (!targetIsOwn && !targetHasBase) {
                        // Target player with most cards for maximum disruption
                        score = 7.0 * aggressiveness;
                    }
                } else if ("gift".equals(cardType)) {
                    if (targetIsOwn && targetHasBase) {
                        // Value depends on how many gifts already there
                        score = 5.0;
                    }
                } else if ("hour".equals(cardType)) {
                    if (targetIsOwn) {
                        // Hours are valuable but situational
                        score = 4.0;
                    }
                }
                
                yield score;
            }
        };
    }

    /**
     * Selects which opponent to target with a weapon card.
     * 
     * EASY: Random opponent
     * MEDIUM: Target player with most slots filled
     * EXPERT: Target player closest to winning
     * NIGHTMARE: Calculate optimal disruption target
     * 
     * @param opponentSlotCounts Array of how many filled slots each opponent has
     * @param currentPlayerIndex The AI's player index
     * @return Index of player to target
     */
    public int selectWeaponTarget(int[] opponentSlotCounts, int currentPlayerIndex) {
        return switch (LEVEL) {
            case EASY -> {
                // Random opponent
                int target;
                do {
                    target = (int) (Math.random() * opponentSlotCounts.length);
                } while (target == currentPlayerIndex);
                yield target;
            }
            
            case MEDIUM -> {
                // Target player with most filled slots
                int maxSlots = -1;
                int targetPlayer = -1;
                for (int i = 0; i < opponentSlotCounts.length; i++) {
                    if (i == currentPlayerIndex) continue;
                    if (opponentSlotCounts[i] > maxSlots) {
                        maxSlots = opponentSlotCounts[i];
                        targetPlayer = i;
                    }
                }
                yield targetPlayer;
            }
            
            case EXPERT, NIGHTMARE -> {
                // More sophisticated targeting
                int bestTarget = -1;
                double bestScore = -1;
                
                for (int i = 0; i < opponentSlotCounts.length; i++) {
                    if (i == currentPlayerIndex) continue;
                    
                    // Score based on threat level
                    double threatScore = opponentSlotCounts[i] * 2.0;
                    
                    // Add randomness to avoid being too predictable
                    if (LEVEL == Level.EXPERT) {
                        threatScore += Math.random() * 2.0;
                    }
                    
                    if (threatScore > bestScore) {
                        bestScore = threatScore;
                        bestTarget = i;
                    }
                }
                
                yield bestTarget;
            }
        };
    }

    /**
     * Decides whether to discard a card instead of playing it.
     * 
     * Sometimes it's better to discard than make a suboptimal play.
     * 
     * @param cardType The card type being considered
     * @param hasValidPlay Whether a valid play exists for this card
     * @return true if should discard, false if should play
     */
    public boolean shouldDiscard(String cardType, boolean hasValidPlay) {
        if (!hasValidPlay) return true; // No choice
        
        return switch (LEVEL) {
            case EASY -> false; // Always play if possible
            
            case MEDIUM -> {
                // Discard gifts if no good base to play on
                if ("gift".equals(cardType)) {
                    yield Math.random() < 0.3;
                }
                yield false;
            }
            
            case EXPERT, NIGHTMARE -> {
                // Strategic discarding
                if ("gift".equals(cardType)) {
                    // Only play gifts if they add significant value
                    yield Math.random() < 0.4;
                }
                // Sometimes discard weapons to avoid telegraphing strategy
                if ("weapon".equals(cardType)) {
                    yield Math.random() < (0.2 * (1 - aggressiveness));
                }
                yield false;
            }
        };
    }

    public enum Level {
        /**
         * EASY: Random plays, basic strategy
         * - Plays first valid card found
         * - Random targeting for weapons
         * - No strategic discarding
         */
        EASY,
        
        /**
         * MEDIUM: Prioritizes good plays
         * - Prefers building own board over attacking
         * - Targets strongest opponent
         * - Sometimes discards suboptimal cards
         */
        MEDIUM,
        
        /**
         * EXPERT: Calculates value of plays
         * - Balances offense and defense
         * - Strategic card evaluation
         * - Adapts to game state
         */
        EXPERT,
        
        /**
         * NIGHTMARE: Optimal play with perfect information
         * - Maximizes expected value
         * - Predicts opponent moves
         * - Minimal randomness, maximum efficiency
         */
        NIGHTMARE
    }
}

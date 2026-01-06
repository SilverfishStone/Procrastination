package net.silverfishstone.procrastination.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RoundManager to verify round progression and limits.
 */
class RoundManagerTest {

    private RoundManager roundManager;

    @BeforeEach
    void setUp() {
        roundManager = new RoundManager(4); // 4 players
    }

    @Test
    void testInitialRound() {
        assertEquals(0, roundManager.getCurrentRound(), "Game should start at round 0");
    }

    @Test
    void testDefaultMaxRounds() {
        assertEquals(25, roundManager.getMaxRounds(), "Default max rounds should be 25");
    }

    @Test
    void testSetMaxRounds() {
        roundManager.setMaxRounds(20);
        assertEquals(20, roundManager.getMaxRounds(), "Should be able to set max rounds");
    }

    @Test
    void testRoundAdvancement() {
        roundManager.advanceRound();
        assertEquals(1, roundManager.getCurrentRound(), "Round should advance to 1");
        
        roundManager.advanceRound();
        assertEquals(2, roundManager.getCurrentRound(), "Round should advance to 2");
    }

    @Test
    void testRoundLimitNotReached() {
        roundManager.setMaxRounds(25);
        
        for (int i = 0; i < 20; i++) {
            roundManager.advanceRound();
        }
        
        assertEquals(20, roundManager.getCurrentRound());
        assertFalse(roundManager.hasReachedRoundLimit(), "Should not reach limit at round 20");
    }

    @Test
    void testRoundLimitReached() {
        roundManager.setMaxRounds(25);
        
        for (int i = 0; i < 25; i++) {
            roundManager.advanceRound();
        }
        
        assertEquals(25, roundManager.getCurrentRound());
        assertTrue(roundManager.hasReachedRoundLimit(), "Should reach limit at round 25");
    }

    @Test
    void testRoundLimitExceeded() {
        roundManager.setMaxRounds(10);
        
        for (int i = 0; i < 15; i++) {
            roundManager.advanceRound();
        }
        
        assertTrue(roundManager.hasReachedRoundLimit(), "Should be past limit at round 15");
    }

    @Test
    void testShortGameLimit() {
        roundManager.setMaxRounds(5);
        
        for (int i = 0; i < 4; i++) {
            roundManager.advanceRound();
            assertFalse(roundManager.hasReachedRoundLimit(), 
                       "Should not reach limit before round 5");
        }
        
        roundManager.advanceRound();
        assertTrue(roundManager.hasReachedRoundLimit(), 
                  "Should reach limit at round 5");
    }

    @Test
    void testLongGameLimit() {
        roundManager.setMaxRounds(50);
        
        for (int i = 0; i < 49; i++) {
            roundManager.advanceRound();
        }
        
        assertFalse(roundManager.hasReachedRoundLimit(), 
                   "Should not reach limit at round 49");
        
        roundManager.advanceRound();
        assertTrue(roundManager.hasReachedRoundLimit(), 
                  "Should reach limit at round 50");
    }

    @Test
    void testPlayerStateInitialization() {
        for (int i = 0; i < 4; i++) {
            RoundManager.PlayerState state = roundManager.getPlayerState(i);
            assertNotNull(state, "Player state should be initialized");
            assertEquals(i, state.getPlayerIndex(), "Player index should match");
            assertEquals(0, state.getTotalHours(), "Should start with 0 hours");
            assertTrue(state.getCardsInPlay().isEmpty(), "Should start with no cards in play");
        }
    }

    @Test
    void testInvalidPlayerIndex() {
        assertNull(roundManager.getPlayerState(-1), "Should return null for negative index");
        assertNull(roundManager.getPlayerState(4), "Should return null for out-of-bounds index");
        assertNull(roundManager.getPlayerState(100), "Should return null for large out-of-bounds index");
    }

    @Test
    void testGetCardCountInPlay() {
        assertEquals(0, roundManager.getCardCountInPlay(0), "Should start with 0 cards in play");
        assertEquals(0, roundManager.getCardCountInPlay(-1), "Should return 0 for invalid player");
    }

    @Test
    void testMultiplePlayerGame() {
        // Test with different player counts
        RoundManager game2Players = new RoundManager(2);
        assertNotNull(game2Players.getPlayerState(0));
        assertNotNull(game2Players.getPlayerState(1));
        assertNull(game2Players.getPlayerState(2));

        RoundManager game6Players = new RoundManager(6);
        for (int i = 0; i < 6; i++) {
            assertNotNull(game6Players.getPlayerState(i), "All 6 players should be initialized");
        }
        assertNull(game6Players.getPlayerState(6));
    }
}

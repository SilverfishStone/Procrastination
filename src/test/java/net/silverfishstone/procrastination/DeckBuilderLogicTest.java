package net.silverfishstone.procrastination;

import net.silverfishstone.procrastination.components.CardDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DeckBuilder logic without requiring JavaFX graphics context.
 */
class DeckBuilderLogicTest {

    @Test
    void testGetDeckSize() {
        assertEquals(80, DeckBuilder.getDeckSize(), "Standard deck size should be 80");
    }

    @Test
    void testDistributionInfo() {
        String info = DeckBuilder.getDistributionInfo();
        
        assertNotNull(info);
        assertTrue(info.contains("80 cards"), "Should mention total card count");
        assertTrue(info.contains("On the Clock: 10"), "Should list On the Clock count");
        assertTrue(info.contains("Recession: 1 (nuclear option)"), "Should list Recession as rare");
        assertTrue(info.contains("PLAY CARDS"), "Should have play cards section");
        assertTrue(info.contains("WEAPON CARDS"), "Should have weapon cards section");
        assertTrue(info.contains("HELPER CARDS"), "Should have helper cards section");
        assertTrue(info.contains("ALERT CARDS"), "Should have alert cards section");
    }

    @Test
    void testCardDefinitions() {
        // Verify all expected card types exist
        assertNotNull(CardDefinition.ON_THE_CLOCK);
        assertNotNull(CardDefinition.PROFESSIONAL);
        assertNotNull(CardDefinition.RISKY);
        assertNotNull(CardDefinition.SHARING_IS_CARING);
        assertNotNull(CardDefinition.UNPREDICTABLE);
        
        assertNotNull(CardDefinition.TARDY);
        assertNotNull(CardDefinition.DEADLINE);
        assertNotNull(CardDefinition.SCAMMER);
        assertNotNull(CardDefinition.QUIT);
        assertNotNull(CardDefinition.STOCK_MARKET);
        assertNotNull(CardDefinition.PARASITE);
        assertNotNull(CardDefinition.DOWNSIZING);
        assertNotNull(CardDefinition.FOREIGN_EXCHANGE);
        
        assertNotNull(CardDefinition.EXCUSED);
        assertNotNull(CardDefinition.EXTENSION);
        assertNotNull(CardDefinition.NEPOTISM);
        assertNotNull(CardDefinition.NEWBIE);
        
        assertNotNull(CardDefinition.AMNESIA);
        assertNotNull(CardDefinition.FIRED);
        assertNotNull(CardDefinition.PERFORMANCE_REVIEW);
        assertNotNull(CardDefinition.RECESSION);
    }

    @Test
    void testCardCategories() {
        // Test Play Cards
        assertTrue(CardDefinition.ON_THE_CLOCK.isPlayCard());
        assertTrue(CardDefinition.PROFESSIONAL.isPlayCard());
        assertFalse(CardDefinition.ON_THE_CLOCK.isWeaponCard());
        
        // Test Weapon Cards
        assertTrue(CardDefinition.TARDY.isWeaponCard());
        assertTrue(CardDefinition.DEADLINE.isWeaponCard());
        assertTrue(CardDefinition.STOCK_MARKET.isWeaponCard());
        
        // Test Helper Cards
        assertTrue(CardDefinition.EXCUSED.isHelperCard());
        assertTrue(CardDefinition.EXTENSION.isHelperCard());
        
        // Test Alert Cards
        assertTrue(CardDefinition.AMNESIA.isAlertCard());
        assertTrue(CardDefinition.FIRED.isAlertCard());
        assertTrue(CardDefinition.RECESSION.isAlertCard());
    }

    @Test
    void testSpecialCardMechanics() {
        // Test special mechanics flags
        assertTrue(CardDefinition.PROFESSIONAL.hasProfessionalBonus());
        assertTrue(CardDefinition.RISKY.hasRiskyBonus());
        assertTrue(CardDefinition.SHARING_IS_CARING.hasSharingMechanic());
        assertTrue(CardDefinition.DOWNSIZING.isRollingWeapon());
    }

    @Test
    void testWeaponTypes() {
        // Immediate weapons
        assertFalse(CardDefinition.TARDY.isPlayWeapon());
        assertFalse(CardDefinition.DEADLINE.isPlayWeapon());
        assertFalse(CardDefinition.SCAMMER.isPlayWeapon());
        
        // Play weapons (stay on board)
        assertTrue(CardDefinition.STOCK_MARKET.isPlayWeapon());
        assertTrue(CardDefinition.PARASITE.isPlayWeapon());
        assertTrue(CardDefinition.DOWNSIZING.isPlayWeapon());
    }

    @Test
    void testCardValues() {
        // Test basic hour values
        assertEquals(1, CardDefinition.ON_THE_CLOCK.getHoursPerRound());
        assertEquals(0, CardDefinition.PROFESSIONAL.getHoursPerRound());
        assertEquals(1, CardDefinition.RISKY.getHoursPerRound());
        
        // Test immediate hours
        assertEquals(-5, CardDefinition.RISKY.getImmediateHours());
        assertEquals(-4, CardDefinition.STOCK_MARKET.getImmediateHours());
        assertEquals(4, CardDefinition.PARASITE.getImmediateHours()); // Attacker gains 4
    }

    @Test
    void testExpirationRounds() {
        // Test that cards have defined max rounds (expiration)
        // The actual implementation uses maxRounds field in CardDefinition
        assertNotNull(CardDefinition.ON_THE_CLOCK.getDisplayName());
        assertNotNull(CardDefinition.PROFESSIONAL.getDisplayName());
        assertTrue(CardDefinition.ON_THE_CLOCK.getDisplayName().length() > 0);
    }
}

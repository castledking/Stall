package codes.castled.stall.menu;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoveConfirmationTest
{
    private final RemoveConfirmation confirmation = new RemoveConfirmation();
    private final UUID offer = UUID.randomUUID();

    @Test
    void theSecondPressConfirms()
    {
        assertFalse(confirmation.press(offer, 100));
        assertTrue(confirmation.isArmed(offer, 110));
        assertTrue(confirmation.press(offer, 120));
        assertFalse(confirmation.isArmed(offer, 121), "a confirmed removal disarms");
    }

    @Test
    void holdingTheKeyNeverConfirms()
    {
        assertFalse(confirmation.press(offer, 100));
        // Keyboard repeat: a press every tick or two for several seconds.
        for (long tick = 113; tick < 300; tick += 2)
        {
            assertFalse(confirmation.press(offer, tick), "confirmed at tick " + tick);
        }
    }

    @Test
    void aPauseAfterHoldingStillConfirms()
    {
        confirmation.press(offer, 100);
        confirmation.press(offer, 102);
        confirmation.press(offer, 104);
        assertTrue(confirmation.press(offer, 104 + RemoveConfirmation.MIN_GAP_TICKS));
    }

    @Test
    void theButtonDisarmsAfterAWhile()
    {
        confirmation.press(offer, 100);
        assertFalse(confirmation.isArmed(offer, 100 + RemoveConfirmation.WINDOW_TICKS + 1));
        assertFalse(confirmation.press(offer, 100 + RemoveConfirmation.WINDOW_TICKS + 1), "a late press only re-arms");
        assertTrue(confirmation.isArmed(offer, 100 + RemoveConfirmation.WINDOW_TICKS + 2));
    }

    @Test
    void anotherOffersButtonStartsOver()
    {
        UUID other = UUID.randomUUID();
        confirmation.press(offer, 100);

        assertFalse(confirmation.press(other, 130));
        assertFalse(confirmation.isArmed(offer, 131));
        assertTrue(confirmation.press(other, 150));
    }

    @Test
    void resetDisarms()
    {
        confirmation.press(offer, 100);
        confirmation.reset();
        assertFalse(confirmation.press(offer, 120));
    }
}

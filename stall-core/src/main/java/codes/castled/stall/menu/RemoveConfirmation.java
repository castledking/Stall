package codes.castled.stall.menu;

import java.util.UUID;

/**
 * The owner menu's two-press removal: the drop key on an offer's remove button arms it, and a second
 * press removes the offer.
 *
 * <p>Holding the drop key down sends a press every tick or two once the keyboard starts repeating, so
 * presses close together count as one held press. The confirming press has to come after a pause of
 * at least {@link #MIN_GAP_TICKS}, and before the button disarms itself after {@link #WINDOW_TICKS}.
 */
public final class RemoveConfirmation
{
    /** Longer than common keyboard repeat delays, so holding the key never confirms. */
    public static final long MIN_GAP_TICKS = 15;

    /** How long an armed button waits for the second press. */
    public static final long WINDOW_TICKS = 100;

    private UUID armedOffer;
    private long lastPress;

    /** Records a press on {@code offerId}'s button at {@code tick}; returns whether it confirms the removal. */
    public boolean press(UUID offerId, long tick)
    {
        if (!isArmed(offerId, tick))
        {
            armedOffer = offerId;
            lastPress = tick;
            return false;
        }
        long gap = tick - lastPress;
        lastPress = tick;
        if (gap < MIN_GAP_TICKS)
        {
            return false;
        }
        reset();
        return true;
    }

    public boolean isArmed(UUID offerId, long tick)
    {
        return offerId.equals(armedOffer) && tick - lastPress <= WINDOW_TICKS;
    }

    public void reset()
    {
        armedOffer = null;
    }
}

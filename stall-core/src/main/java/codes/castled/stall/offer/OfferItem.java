package codes.castled.stall.offer;

import java.util.Objects;

/**
 * One slot of an offer: an item and how many of it.
 *
 * @param item a template of the item; its own count is ignored, {@code amount} is what counts
 * @param amount how many the trade moves. The offer editor keeps this to one stack of the item; the
 *               trade planner handles any amount, including more than a stack in a shop saved by an older
 *               version
 */
public record OfferItem<I>(I item, int amount)
{
    /**
     * The most a saved slot may ask for, a double chest full of 64-stacks. Only a sanity limit for loading
     * files; the offer editor stops at one stack per slot.
     */
    public static final int MAX_AMOUNT = 54 * 64;

    public OfferItem
    {
        Objects.requireNonNull(item, "item");
        if (amount < 1 || amount > MAX_AMOUNT)
        {
            throw new IllegalArgumentException("amount must be 1 to " + MAX_AMOUNT + ", was " + amount);
        }
    }
}

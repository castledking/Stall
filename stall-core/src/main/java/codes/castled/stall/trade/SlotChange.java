package codes.castled.stall.trade;

/**
 * One slot a trade changes, and what the slot holds afterwards.
 *
 * @param slot the slot index in the inventory the change belongs to
 * @param stack the slot's new contents, or {@code null} to empty it
 */
public record SlotChange<I>(int slot, I stack)
{
}

package codes.castled.stall.trade;

/**
 * A quantity of one item: what a trade needs in total, or how much of it is missing. Unlike an offer's
 * own slots, a total may add up both slots of a side and several repetitions of the trade.
 *
 * @param item a template of the item; its own count is ignored
 */
public record ItemAmount<I>(I item, int amount)
{
}

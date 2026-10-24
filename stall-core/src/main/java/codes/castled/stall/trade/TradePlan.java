package codes.castled.stall.trade;

import java.util.List;

/** The outcome of planning a trade: either every slot change it makes, or why it cannot happen. */
public sealed interface TradePlan<I>
{
    /**
     * A trade that fits completely. Writing every change, on both sides, completes it; writing only
     * some of them would not, so platforms apply all of them within the same tick.
     */
    record Success<I>(List<SlotChange<I>> buyerChanges, List<SlotChange<I>> stockChanges) implements TradePlan<I>
    {
        public Success
        {
            buyerChanges = List.copyOf(buyerChanges);
            stockChanges = List.copyOf(stockChanges);
        }
    }

    record Failure<I>(TradeFailure reason) implements TradePlan<I>
    {
    }
}

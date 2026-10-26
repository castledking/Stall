package codes.castled.stall.offer;

import java.util.Objects;
import java.util.UUID;

/**
 * A trade a shop offers: what the buyer pays, up to {@link OfferSlots#COST_SLOTS} items, and what they
 * get back, up to {@link OfferSlots#REWARD_SLOTS} items.
 *
 * <p>While the owner sets an offer up, one side may still be empty. Only a complete offer, with at least
 * one item on each side, is shown to buyers and can be traded.
 *
 * @param costs what the buyer pays, which goes into the shop's container
 * @param rewards what the buyer receives, which comes out of the shop's container
 */
public record Offer<I>(UUID id, OfferSlots<I> costs, OfferSlots<I> rewards)
{
    public Offer
    {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(costs, "costs");
        Objects.requireNonNull(rewards, "rewards");
        if (costs.size() != OfferSlots.COST_SLOTS || rewards.size() != OfferSlots.REWARD_SLOTS)
        {
            throw new IllegalArgumentException("An offer has " + OfferSlots.COST_SLOTS + " cost slots and "
                    + OfferSlots.REWARD_SLOTS + " reward slots, not " + costs.size() + " and " + rewards.size());
        }
    }

    public boolean isComplete()
    {
        return !costs.isEmpty() && !rewards.isEmpty();
    }

    public boolean isEmpty()
    {
        return costs.isEmpty() && rewards.isEmpty();
    }

    public OfferSlots<I> side(boolean cost)
    {
        return cost ? costs : rewards;
    }

    public Offer<I> withSide(boolean cost, OfferSlots<I> side)
    {
        return cost ? new Offer<>(id, side, rewards) : new Offer<>(id, costs, side);
    }
}

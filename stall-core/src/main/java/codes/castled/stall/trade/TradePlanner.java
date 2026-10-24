package codes.castled.stall.trade;

import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Plans trades without touching any real inventory. A plan simulates the whole trade on copies of
 * both inventories: the shop's stock gives up the rewards, the buyer gives up the payment, then each
 * side receives what the other gave. Only if every step fits does it return the slot changes, so
 * the platform either writes all of them or nothing at all.
 *
 * <p>Items are taken out of both inventories before anything is put in, so the space a payment or a
 * reward frees counts toward what comes back. The stacks moved are the actual stacks removed, not
 * copies of the offer's templates, so an item keeps all of its data on the way across.
 *
 * @param <I> the platform's item stack type
 */
public final class TradePlanner<I>
{
    private final StackRules<I> rules;

    public TradePlanner(StackRules<I> rules)
    {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /**
     * Plans {@code times} repetitions of {@code offer} as one trade.
     *
     * @param buyer the buyer's storage slots, see {@link SlotInventory#playerStorage}
     * @param stock the shop container's slots
     */
    public TradePlan<I> plan(Offer<I> offer, int times, SlotInventory<I> buyer, SlotInventory<I> stock)
    {
        Objects.requireNonNull(offer, "offer");
        if (times < 1)
        {
            throw new IllegalArgumentException("times must be at least 1, was " + times);
        }
        if (!offer.isComplete())
        {
            return new TradePlan.Failure<>(TradeFailure.INCOMPLETE);
        }
        List<ItemAmount<I>> costs = aggregate(offer.costs().items(), times);
        List<ItemAmount<I>> rewards = aggregate(offer.rewards().items(), times);
        if (costs == null || rewards == null)
        {
            return new TradePlan.Failure<>(TradeFailure.TOO_LARGE);
        }

        Workspace<I> buyerSide = new Workspace<>(buyer, rules);
        Workspace<I> stockSide = new Workspace<>(stock, rules);

        List<I> sold = new ArrayList<>();
        for (ItemAmount<I> reward : rewards)
        {
            if (!stockSide.remove(reward, sold))
            {
                return new TradePlan.Failure<>(TradeFailure.OUT_OF_STOCK);
            }
        }
        List<I> paid = new ArrayList<>();
        for (ItemAmount<I> cost : costs)
        {
            if (!buyerSide.remove(cost, paid))
            {
                return new TradePlan.Failure<>(TradeFailure.CANNOT_AFFORD);
            }
        }
        for (I stack : sold)
        {
            if (!buyerSide.insert(stack))
            {
                return new TradePlan.Failure<>(TradeFailure.INVENTORY_FULL);
            }
        }
        for (I stack : paid)
        {
            if (!stockSide.insert(stack))
            {
                return new TradePlan.Failure<>(TradeFailure.SHOP_FULL);
            }
        }
        return new TradePlan.Success<>(buyerSide.changes(), stockSide.changes());
    }

    /** How many complete trades the shop's stock holds rewards for, ignoring everything else. */
    public int timesInStock(Offer<I> offer, SlotInventory<I> stock)
    {
        List<ItemAmount<I>> rewards = aggregate(offer.rewards().items(), 1);
        if (rewards == null || rewards.isEmpty())
        {
            return 0;
        }
        long times = Integer.MAX_VALUE;
        for (ItemAmount<I> reward : rewards)
        {
            times = Math.min(times, stock.countMatching(reward.item(), rules) / reward.amount());
        }
        return (int) times;
    }

    /** The rewards the stock is short of for one trade, each with how many more it needs. */
    public List<ItemAmount<I>> missingStock(Offer<I> offer, SlotInventory<I> stock)
    {
        return shortfall(offer.rewards().items(), stock);
    }

    /** The costs the buyer is short of for one trade, each with how many more they need. */
    public List<ItemAmount<I>> missingPayment(Offer<I> offer, SlotInventory<I> buyer)
    {
        return shortfall(offer.costs().items(), buyer);
    }

    private List<ItemAmount<I>> shortfall(List<OfferItem<I>> items, SlotInventory<I> inventory)
    {
        List<ItemAmount<I>> wanted = aggregate(items, 1);
        if (wanted == null)
        {
            return List.of();
        }
        List<ItemAmount<I>> missing = new ArrayList<>();
        for (ItemAmount<I> item : wanted)
        {
            long have = inventory.countMatching(item.item(), rules);
            if (have < item.amount())
            {
                missing.add(new ItemAmount<>(item.item(), (int) (item.amount() - have)));
            }
        }
        return missing;
    }

    /** Whether {@code buyer} could make this trade once right now, and if not, why. */
    public OfferStatus status(Offer<I> offer, SlotInventory<I> buyer, SlotInventory<I> stock)
    {
        return switch (plan(offer, 1, buyer, stock))
        {
            case TradePlan.Success<I> ignored -> OfferStatus.AVAILABLE;
            case TradePlan.Failure<I> failure -> OfferStatus.of(failure.reason());
        };
    }

    /**
     * Merges entries for the same item, so an offer asking for the same item in both of its slots
     * needs both amounts together, and multiplies by {@code times}. Returns {@code null} if an amount
     * would overflow.
     */
    private List<ItemAmount<I>> aggregate(List<OfferItem<I>> items, int times)
    {
        List<I> distinct = new ArrayList<>();
        List<Long> amounts = new ArrayList<>();
        for (OfferItem<I> item : items)
        {
            int existing = -1;
            for (int i = 0; i < distinct.size(); i++)
            {
                if (rules.sameItem(distinct.get(i), item.item()))
                {
                    existing = i;
                    break;
                }
            }
            long amount = (long) item.amount() * times;
            if (existing < 0)
            {
                distinct.add(item.item());
                amounts.add(amount);
            }
            else
            {
                amounts.set(existing, amounts.get(existing) + amount);
            }
        }
        List<ItemAmount<I>> merged = new ArrayList<>(distinct.size());
        for (int i = 0; i < distinct.size(); i++)
        {
            long amount = amounts.get(i);
            if (amount > Integer.MAX_VALUE)
            {
                return null;
            }
            merged.add(new ItemAmount<>(distinct.get(i), (int) amount));
        }
        return merged;
    }

    /** One inventory's slots while a plan is being worked out, with a record of which slots changed. */
    private static final class Workspace<I>
    {
        private final SlotInventory<I> original;
        private final StackRules<I> rules;
        private final List<I> slots;

        Workspace(SlotInventory<I> original, StackRules<I> rules)
        {
            this.original = Objects.requireNonNull(original, "inventory");
            this.rules = rules;
            this.slots = new ArrayList<>(original.size());
            for (int slot = 0; slot < original.size(); slot++)
            {
                slots.add(original.get(slot));
            }
        }

        /** Takes {@code wanted.amount()} matching items out, adding what it took to {@code taken}. */
        boolean remove(ItemAmount<I> wanted, List<I> taken)
        {
            long available = 0;
            for (I stack : slots)
            {
                if (stack != null && rules.sameItem(stack, wanted.item()))
                {
                    available += rules.count(stack);
                }
            }
            if (available < wanted.amount())
            {
                return false;
            }

            int remaining = wanted.amount();
            for (int slot : original.removalOrder())
            {
                I stack = slots.get(slot);
                if (stack == null || !rules.sameItem(stack, wanted.item()))
                {
                    continue;
                }
                int count = rules.count(stack);
                int take = Math.min(count, remaining);
                taken.add(rules.withCount(stack, take));
                slots.set(slot, take == count ? null : rules.withCount(stack, count - take));
                remaining -= take;
                if (remaining == 0)
                {
                    break;
                }
            }
            return true;
        }

        /** Puts {@code stack} in, topping up matching stacks before using empty slots. */
        boolean insert(I stack)
        {
            int remaining = rules.count(stack);
            int limit = Math.min(rules.maxStackSize(stack), original.maxStackSize());
            if (limit < 1)
            {
                return false;
            }
            for (int slot : original.insertionOrder())
            {
                I existing = slots.get(slot);
                if (existing == null || !rules.sameItem(existing, stack))
                {
                    continue;
                }
                int count = rules.count(existing);
                if (count >= limit)
                {
                    continue;
                }
                int add = Math.min(limit - count, remaining);
                slots.set(slot, rules.withCount(existing, count + add));
                remaining -= add;
                if (remaining == 0)
                {
                    return true;
                }
            }
            for (int slot : original.insertionOrder())
            {
                if (slots.get(slot) != null)
                {
                    continue;
                }
                int add = Math.min(limit, remaining);
                slots.set(slot, rules.withCount(stack, add));
                remaining -= add;
                if (remaining == 0)
                {
                    return true;
                }
            }
            return false;
        }

        /** Every slot whose contents differ from the snapshot the plan started from. */
        List<SlotChange<I>> changes()
        {
            List<SlotChange<I>> changes = new ArrayList<>();
            for (int slot = 0; slot < slots.size(); slot++)
            {
                I before = original.get(slot);
                I after = slots.get(slot);
                if (!same(before, after))
                {
                    changes.add(new SlotChange<>(slot, after));
                }
            }
            return changes;
        }

        private boolean same(I before, I after)
        {
            if (before == null || after == null)
            {
                return before == after;
            }
            return rules.count(before) == rules.count(after) && rules.sameItem(before, after);
        }
    }
}

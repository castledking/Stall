package codes.castled.stall.trade;

/**
 * A stand-in for a platform item stack.
 *
 * @param data everything besides the item type that an exact match compares, such as a custom name
 * @param wear data a lenient rule set may ignore, like durability
 */
record FakeItem(String id, String data, String wear, int count, int maxStack)
{
    static FakeItem of(String id, int count)
    {
        return new FakeItem(id, "", "", count, 64);
    }

    static FakeItem unstackable(String id, int count)
    {
        return new FakeItem(id, "", "", count, 1);
    }

    FakeItem withData(String newData)
    {
        return new FakeItem(id, newData, wear, count, maxStack);
    }

    FakeItem withWear(String newWear)
    {
        return new FakeItem(id, data, newWear, count, maxStack);
    }

    FakeItem withCount(int newCount)
    {
        return new FakeItem(id, data, wear, newCount, maxStack);
    }

    /** Exact matching, like both platforms do it: type and every bit of data. */
    static final StackRules<FakeItem> EXACT = new Rules(false);

    /** Ignores {@code wear}, to check that trades move the real stacks rather than templates. */
    static final StackRules<FakeItem> IGNORE_WEAR = new Rules(true);

    private record Rules(boolean ignoreWear) implements StackRules<FakeItem>
    {
        @Override
        public boolean isEmpty(FakeItem stack)
        {
            return stack.count() <= 0;
        }

        @Override
        public boolean sameItem(FakeItem a, FakeItem b)
        {
            return a.id().equals(b.id()) && a.data().equals(b.data()) && (ignoreWear || a.wear().equals(b.wear()));
        }

        @Override
        public int count(FakeItem stack)
        {
            return stack.count();
        }

        @Override
        public int maxStackSize(FakeItem stack)
        {
            return stack.maxStack();
        }

        @Override
        public FakeItem withCount(FakeItem stack, int count)
        {
            return stack.withCount(count);
        }
    }
}

package codes.castled.stall.store;

/**
 * Turns a platform's item stacks into {@link StoredItem}s and back.
 *
 * @param <I> the platform's item stack type
 */
public interface ItemCodec<I>
{
    /** Saves one of the item; the stack's own count is not kept. */
    StoredItem encode(I item);

    /**
     * Reads an item back, upgrading it if an older Minecraft version saved it.
     *
     * @throws ItemDecodeException if the item can't be read on this server, for example because a newer
     *                             Minecraft version or a mod that is no longer installed saved it
     */
    I decode(StoredItem stored) throws ItemDecodeException;
}

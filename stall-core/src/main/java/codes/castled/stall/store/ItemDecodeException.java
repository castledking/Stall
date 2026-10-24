package codes.castled.stall.store;

/** A saved item that this server can't read. */
public final class ItemDecodeException extends Exception
{
    public ItemDecodeException(String message)
    {
        super(message);
    }

    public ItemDecodeException(String message, Throwable cause)
    {
        super(message, cause);
    }
}

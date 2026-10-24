package codes.castled.stall.store;

/** A shop file that can't be loaded as a shop at all. */
public final class ShopDataException extends Exception
{
    private final boolean newerVersion;

    public ShopDataException(String message, Throwable cause)
    {
        super(message, cause);
        this.newerVersion = false;
    }

    private ShopDataException(String message)
    {
        super(message);
        this.newerVersion = true;
    }

    static ShopDataException fromNewerVersion(int schema)
    {
        return new ShopDataException("written by a newer version of Stall (format " + schema + ")");
    }

    /**
     * Whether a newer version of Stall wrote the file. Such a file isn't broken, only unreadable here, so it
     * is left where it is and never overwritten.
     */
    public boolean isFromNewerVersion()
    {
        return newerVersion;
    }
}

package codes.castled.stall.store;

/** Where storage reports go: the platform's own logger. */
public interface StoreLog
{
    void info(String message);

    void warn(String message, Throwable error);
}

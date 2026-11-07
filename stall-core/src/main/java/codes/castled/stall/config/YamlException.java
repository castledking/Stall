package codes.castled.stall.config;

/** A config file that isn't YAML Stall can read, with the line it went wrong on. */
public final class YamlException extends Exception
{
    private final int line;

    public YamlException(int line, String message)
    {
        super("line " + line + ": " + message);
        this.line = line;
    }

    /** The 1-based line the problem is on. */
    public int line()
    {
        return line;
    }
}

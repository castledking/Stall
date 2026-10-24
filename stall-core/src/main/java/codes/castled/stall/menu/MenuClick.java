package codes.castled.stall.menu;

/** A click in a shop menu, translated from each platform's own click types. */
public enum MenuClick
{
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    /** The drop key, with or without Ctrl. */
    DROP,
    /** Everything else: number keys, the offhand key, double clicks, middle clicks, drags. */
    OTHER
}

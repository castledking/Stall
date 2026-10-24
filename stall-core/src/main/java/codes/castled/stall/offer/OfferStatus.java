package codes.castled.stall.offer;

import codes.castled.stall.trade.TradeFailure;

/** Whether a buyer can make an offer's trade right now, as the buyer menu shows it. */
public enum OfferStatus
{
    AVAILABLE("In stock"),
    OUT_OF_STOCK("Out of stock"),
    CANNOT_AFFORD("You can't afford this"),
    INVENTORY_FULL("Your inventory is full"),
    SHOP_FULL("This shop is full"),
    INCOMPLETE("Not set up yet"),
    UNAVAILABLE("Unavailable");

    private final String label;

    OfferStatus(String label)
    {
        this.label = label;
    }

    public String label()
    {
        return label;
    }

    public static OfferStatus of(TradeFailure failure)
    {
        return switch (failure)
        {
            case OUT_OF_STOCK -> OUT_OF_STOCK;
            case CANNOT_AFFORD -> CANNOT_AFFORD;
            case INVENTORY_FULL -> INVENTORY_FULL;
            case SHOP_FULL -> SHOP_FULL;
            case INCOMPLETE -> INCOMPLETE;
            case TOO_LARGE -> UNAVAILABLE;
        };
    }
}

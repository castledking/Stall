package codes.castled.stall.trade;

/** Why a planned trade cannot happen, with the message the buyer sees. */
public enum TradeFailure
{
    OUT_OF_STOCK("This shop is out of stock for that trade."),
    CANNOT_AFFORD("You don't have everything this trade costs."),
    INVENTORY_FULL("Your inventory doesn't have room for this trade."),
    SHOP_FULL("This shop doesn't have room for your payment."),
    INCOMPLETE("That offer isn't set up yet."),
    TOO_LARGE("That trade is too large.");

    private final String message;

    TradeFailure(String message)
    {
        this.message = message;
    }

    public String message()
    {
        return message;
    }
}

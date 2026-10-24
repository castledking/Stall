package codes.castled.stall.shop;

import codes.castled.stall.offer.Offer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A shop: an owner, the container that holds its stock, and its offers.
 *
 * <p>The revision goes up with every change to the offers. A buyer menu remembers the revision it was
 * drawn from, so a click on a menu that no longer matches the shop is refused instead of buying
 * something the buyer did not see.
 *
 * <p>Shops are only read and changed on the server thread.
 */
public final class Shop<I>
{
    public static final int MAX_OFFERS = 6;

    private final UUID id;
    private final UUID owner;
    private final String ownerName;
    private final ShopLocation location;
    private final boolean tokenConsumed;
    private final Instant created;
    private List<Offer<I>> offers = List.of();
    private List<String> preservedOffers = List.of();
    private int revision;

    /**
     * @param tokenConsumed whether making the shop used up a token, which is then handed back when the
     *                      shop is removed. Shops made without one, by a creative player for example,
     *                      give nothing back, so removing a shop can never create a token.
     */
    public Shop(UUID id, UUID owner, String ownerName, ShopLocation location, boolean tokenConsumed)
    {
        this(id, owner, ownerName, location, tokenConsumed, Instant.now());
    }

    public Shop(UUID id, UUID owner, String ownerName, ShopLocation location, boolean tokenConsumed, Instant created)
    {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.ownerName = Objects.requireNonNull(ownerName, "ownerName");
        this.location = Objects.requireNonNull(location, "location");
        this.tokenConsumed = tokenConsumed;
        this.created = Objects.requireNonNull(created, "created");
    }

    /**
     * Puts back what was saved: the offers that could be read, and the saved text of those that could
     * not. A shop loaded from disk may briefly hold more than {@link #MAX_OFFERS} offers if unreadable
     * ones became readable again; only adding new offers checks the limit.
     */
    public void restore(List<Offer<I>> offers, List<String> preservedOffers)
    {
        this.offers = List.copyOf(offers);
        this.preservedOffers = List.copyOf(preservedOffers);
    }

    public UUID id()
    {
        return id;
    }

    public UUID owner()
    {
        return owner;
    }

    public String ownerName()
    {
        return ownerName;
    }

    public ShopLocation location()
    {
        return location;
    }

    public boolean tokenConsumed()
    {
        return tokenConsumed;
    }

    public Instant created()
    {
        return created;
    }

    /**
     * Offers loaded from disk whose items this server can't read, for example because the mod that adds
     * them was removed. They're kept as saved, written back unchanged and never shown, so nothing is lost
     * if the items become readable again.
     */
    public List<String> preservedOffers()
    {
        return preservedOffers;
    }

    public boolean isOwner(UUID player)
    {
        return owner.equals(player);
    }

    public List<Offer<I>> offers()
    {
        return offers;
    }

    public Optional<Offer<I>> offer(UUID offerId)
    {
        return offers.stream().filter(offer -> offer.id().equals(offerId)).findFirst();
    }

    /** The offers buyers see: every offer with at least one item on each side. */
    public List<Offer<I>> completeOffers()
    {
        return offers.stream().filter(Offer::isComplete).toList();
    }

    public int revision()
    {
        return revision;
    }

    public boolean isFull()
    {
        return offers.size() >= MAX_OFFERS;
    }

    public void addOffer(Offer<I> offer)
    {
        Objects.requireNonNull(offer, "offer");
        if (isFull())
        {
            throw new IllegalStateException("A shop holds at most " + MAX_OFFERS + " offers");
        }
        List<Offer<I>> updated = new ArrayList<>(offers);
        updated.add(offer);
        setOffers(updated);
    }

    public void replaceOffer(int index, Offer<I> offer)
    {
        Objects.requireNonNull(offer, "offer");
        List<Offer<I>> updated = new ArrayList<>(offers);
        updated.set(index, offer);
        setOffers(updated);
    }

    public void removeOffer(int index)
    {
        List<Offer<I>> updated = new ArrayList<>(offers);
        updated.remove(index);
        setOffers(updated);
    }

    private void setOffers(List<Offer<I>> updated)
    {
        offers = List.copyOf(updated);
        revision++;
    }
}

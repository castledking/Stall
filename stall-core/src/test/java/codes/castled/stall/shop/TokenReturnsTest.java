package codes.castled.stall.shop;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenReturnsTest
{
    @Test
    void owedTokensAddUpAndAreClaimedOnce()
    {
        TokenReturns returns = new TokenReturns();
        UUID player = UUID.randomUUID();

        returns.add(player, 1);
        returns.add(player, 2);
        assertEquals(3, returns.owed(player));

        assertEquals(3, returns.claim(player));
        assertEquals(0, returns.claim(player));
        assertEquals(0, returns.owed(player));
    }

    @Test
    void playersAreKeptApart()
    {
        TokenReturns returns = new TokenReturns();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        returns.add(first, 1);
        assertEquals(0, returns.claim(second));
        assertEquals(1, returns.claim(first));
    }
}

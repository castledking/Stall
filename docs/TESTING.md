# Testing the prototype

This round is short. It covers the fix for amounts in the owner menu, plus the one round-four check whose
instructions were wrong.

Rounds one to four passed. The results are in [PLAN.md](PLAN.md#15-progress).

## Setup

- **Paper:** put `Stall-Paper-<version>.jar` in a Paper 26.3 server's `plugins` folder.
- **Fabric:** put `Stall-Fabric-<version>.jar` and Fabric API for 26.3 in a dedicated server's `mods`
  folder. Fabric Loader 0.19.3 or newer.
- Join with a vanilla 26.3 client. Nothing goes on the client.

## Checklist

**One stack per slot**

- [ ] Put 10 emeralds in an empty slot and shift-click it: it goes to 64, not 74, and the other slot stays
      empty.
- [ ] Shift-click or left-click it again: the extra goes into the side's other slot, which then fills up
      to 64.
- [ ] With both slots full, a further increase changes nothing. The most a side can ask for is 128.
- [ ] Increasing a full second slot carries on in an empty first slot the same way.
- [ ] If the other slot holds a different item, increasing a full slot changes nothing, and the other item
      stays.
- [ ] Left-clicking with a stack of the same item onto a slot that has room fills it up to 64.
- [ ] Items that stack lower follow their own stack: ender pearls stop at 16 per slot, swords at 1.
- [ ] Shift-right-click still takes a stack off, never below 1.

**Paused shops** (corrected from round four)

- [ ] Replace a shop's barrel: `/setblock <x> <y> <z> minecraft:air`, then
      `/setblock <x> <y> <z> minecraft:barrel`. Setting a barrel over an identical barrel does nothing,
      which is why the round-four instruction didn't work. A buyer's click now says the shop is paused,
      and the barrel stays shut.

## Known gaps

These are later milestones, not prototype bugs:

- Explosions can still destroy a shop, and its token is lost (milestone 3).
- A shop whose barrel is gone altogether stays listed until it's removed with `/stall remove` or by an
  operator. Cleaning these up automatically is milestone 3.
- While a shop is paused, only its owner or an operator can break its barrel or open it.
- A shop saved before this fix may still have a slot above one stack. It keeps trading that amount; editing
  the slot brings it back to a stack.

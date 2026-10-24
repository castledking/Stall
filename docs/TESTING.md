# Testing the prototype

This round is short. It covers the fix for amounts in the owner menu, plus the one round-four check whose
instructions were wrong.

Rounds one to four passed. The results are in [PLAN.md](PLAN.md#15-progress).

## Setup

- **Spigot / Paper:** put `Stall-Paper-<version>.jar` in a Spigot, Paper or Purpur 26.3 server's `plugins` folder.
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

## Automated Spigot and Paper checks

The plugin is compiled against Spigot API 26.3, so ordinary plugin code cannot accidentally call Paper-only
methods. The item codec binds Minecraft's native 26.3 codec at enable time to keep the same item format
as Paper and Fabric. Unit tests include real Spigot-written items and token identity on Fabric.

Build optional server-test artifacts with Java 25:

```bash
./gradlew :stall-paper:serverTestArtifacts
```

On a separate, disposable Spigot 26.3 server, install these two files in `plugins/`:

- `stall-paper/build/libs/Stall-Paper-<version>-server-tests.jar`
- `stall-paper/build/libs/Stall-Server-Test-Probe-<version>.jar`

Remove any ordinary Stall jar from that test server first. The probe calls checks bundled with the
test-only Stall jar, then **stops the server**. A successful log contains `STALL_SERVER_CHECKS_PASS`;
failures contain `STALL_SERVER_CHECKS_FAIL` with the cause. Do not deploy either test jar to a live server.
The same checks run on Paper 26.3.

Checks cover native item round trips (names, lore, damage, enchantments, custom data, potions and custom
stack sizes), old-item upgrades, future/unknown-item rejection, command permissions and amount bounds,
menu rendering and click cancellation, two-for-two purchases, repeated clicks, failed trades leaving both
inventories unchanged, barrel markers, hopper extraction/input and break protection. Player inventories
and clicks are simulated against real Bukkit inventories; this does not replace a vanilla-client test.

For cross-server item checks, start each test server with the same JVM property, for example
`-Dstall.test.exchangeDir=/tmp/stall-item-exchange`. Run Spigot, then Paper, then Spigot again. Each server
writes its items there and verifies every existing fixture, covering both migration directions. Use an
empty exchange directory when changing fixture definitions.

## Spigot client retest

- [ ] Obtain a token, turn a barrel into a shop, and restart: the shop and its offers reload.
- [ ] Configure two payment and two reward items; buy from a second vanilla client and check all quantities.
- [ ] Check shift-click, number keys, F, Q, double-click and drag in both menus; display items cannot escape.
- [ ] Check growing menus with an item on the cursor, ownership, permission limits and claim protection.
- [ ] Move a world between Spigot and Paper; offers, stock and token markers continue to work.

# Testing Stall

The current round covers Stall 1.0.1: three reward slots, chest shops and permission-based 54-slot barrels.
The completed amount checks below are retained as test history.

Rounds one to four passed. The results are in [PLAN.md](PLAN.md#15-progress).

## Setup

- **Spigot / Paper / Folia:** put `Stall-Paper-<version>.jar` in a Spigot, Paper, Purpur or Folia 26.3 server's `plugins` folder.
- **Fabric:** put `Stall-Fabric-<version>.jar` and Fabric API for 26.3 in a dedicated server's `mods`
  folder. Fabric Loader 0.19.3 or newer.
- Join with a vanilla 26.3 client. Nothing goes on the client.

## Checklist

**One stack per slot**

- [X] Put 10 emeralds in an empty slot and shift-click it: it goes to 64, not 74, and the other slot stays
      empty.
- [X] Shift-click or left-click it again: the extra goes into the side's other slot, which then fills up
      to 64.
- [X] With both slots full, a further increase changes nothing. The most a side can ask for is 128.
- [X] Increasing a full second slot carries on in an empty first slot the same way.
- [X] If the other slot holds a different item, increasing a full slot changes nothing, and the other item
      stays.
- [X] Left-clicking with a stack of the same item onto a slot that has room fills it up to 64.
- [X] Items that stack lower follow their own stack: ender pearls stop at 16 per slot, swords at 1.
- [X] Shift-right-click still takes a stack off, never below 1.

**Paused shops** (corrected from round four)

- [X] Replace a shop's barrel: `/setblock <x> <y> <z> minecraft:air`, then
      `/setblock <x> <y> <z> minecraft:barrel`. Setting a barrel over an identical barrel does nothing,
      which is why the round-four instruction didn't work. A buyer's click now says the shop is paused,
      and the barrel stays shut.

## Known gaps

These are later milestones, not prototype bugs:

- While a shop is paused, only its owner or an operator can break its barrel or open it.
- A shop saved before this fix may still have a slot above one stack. It keeps trading that amount; editing
  the slot brings it back to a stack.

## Automated Spigot, Paper and Folia checks

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
The same checks run on Paper 26.3. On Folia they use its region and entity schedulers; the simulated-player GUI/trade checks are skipped because those proxies are not Folia entities.

Checks cover native item round trips (names, lore, damage, enchantments, custom data, potions and custom
stack sizes), old-item upgrades, future/unknown-item rejection, command permissions and amount bounds,
menu rendering and click cancellation, two-payment/three-reward purchases across both chest halves, repeated
clicks, failed trades leaving inventories unchanged, markers, hopper guards, chest joining/breaking, real
explosions, unavailable chest halves, large-barrel live inventory composition and extra-stock round trips,
extra-item drops, and orphan cleanup/refunds. Folia additionally verifies ownership rejection between chunks 8,192 blocks apart, region scheduling, and an entity callback returning to its own region. Player inventories
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


## Milestone 3 and Folia client round

The earlier one-stack and paused-barrel checks above were marked passed by the user. Keep those results;
these are new checks for the new lifecycle and scheduler behavior. Use a fresh world backup for each
platform move, and let an unmarked replacement barrel be placed before the ten-second cleanup sweep.

- [ ] On Spigot/Paper, Fabric and Folia: make a stocked shop, explode TNT beside it, and check that its
      barrel, offers and stock survive while ordinary nearby blocks are destroyed. Repeat with a creeper
      and a bed/respawn-anchor explosion where applicable.
- [ ] Remove a shop barrel with `/setblock ... air`. Within ten seconds while its chunk is loaded, its
      registry entry disappears, `shops/_orphaned/<id>.json` keeps its offers and the owner gets exactly one
      token through `/stall reclaim`. A creative-mode shop never refunds a token.
- [ ] Restart before claiming, claim, then restart again: the token cannot be claimed twice. A normal
      owner/admin removal must still return its token through the existing path.
- [ ] An unmarked replacement barrel pauses; an empty-hand sneak-click reactivates it through claim
      protection. A barrel marked for a different shop is not overwritten during orphan cleanup.
- [ ] Unload a shop chunk, or leave its world unavailable: no refund occurs and no chunks load just for
      reconciliation. Check removal on the next chunk load.
- [ ] On Folia, use two players in distant regions to create/edit/buy simultaneously. Check shop lists,
      permission caps, and both two-for-two inventories, with no thread-ownership exceptions in the log.
- [ ] Move/teleport away while a menu grows or another buyer trades. The old menu closes without moving
      its display items or cursor stack. Reopen the nearby shop and trade again.
- [ ] On Folia, an operator in one region gives tokens to a player in another and removes an offline or
      distant player's shop. Tokens arrive on the owner's entity thread or stay queued if they disconnect.

Automated verification covers Spigot 26.3 (BuildTools), Paper 26.3 build 193, Folia 26.3 build 5 and
Fabric Loader 0.19.3 / Fabric API 0.162.0+26.3 on a dedicated 26.3 server. Real Folia player interaction
and third-party claim-plugin behavior remain client checks; startup alone does not verify those paths.

## Stall 1.0.1 client round

Run on Fabric, Spigot/Paper and Folia with a vanilla client. These new checks are pending; the earlier
checked boxes describe the previous version, whose reward side had two slots.

- [ ] Configure all three rewards, including different items and three stacks of the same item. Check
      increment/decrement, shift-click, Q, F, drags, growing menus and exact buyer quantities. Restart.
- [ ] Create single/double chest and trapped-chest shops. Both halves open the same shop, share stock,
      block unauthorized access/breaking and hopper extraction, and survive explosions. Removing either
      half once returns one token and clears the remaining marker. Copper chests stay ordinary blocks.
- [ ] Join a chest to your own shop. Someone else cannot join theirs; sneaking places a separate chest.
      Check this alongside the server's claim plugin, including claim borders and chunk borders.
- [ ] Without `stall.barrel.large`, a barrel stays 27 slots, including for operators. Grant the permission
      through LuckPerms; a new or existing barrel shop opens 54 slots on its owner's next normal click.
- [ ] Stock all 54 slots using shift-click, number keys and drags. Buyers use items in the extra slots,
      payments fit in either half, and a failed trade changes nothing. Hoppers/comparators see only the
      ordinary 27. A protected owner's click still respects the claim plugin.
- [ ] Restart and unload/reload the chunk with extra items stored. Counts and custom item data survive.
      Remove the permission: the existing large barrel stays large. Check concurrent owners/buyers on Folia.
- [ ] `/stall remove` refuses while extra slots contain items. Empty them and remove; it succeeds. Breaking
      a stocked large barrel drops every extra item and one token, without leaving an open usable view.
- [ ] Before switching between Fabric and Spigot/Paper or uninstalling Stall, empty extra slots. Ordinary
      stock and shop files still migrate; block attachments use different formats on the two platforms.
- [ ] Keep older two-reward barrel files readable; new third-reward/chest/large-barrel shops use schema 2.
      Rolling back to Stall 1.0.0 leaves those newer files untouched.

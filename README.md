<p align="center">
<img alt="Stall" width=100% height=auto src="https://castled.codes/assets/stall-banner.png">
</p>

<p align="center">
  <a href="https://discord.com/invite/pCKdCX6nYr"><img alt="Discord" src="https://img.shields.io/badge/Discord-Community-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
  <a href="https://github.com/castledking/Stall"><img alt="GitHub Repository" src="https://img.shields.io/badge/GitHub-Repository-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/castledking/Stall/issues"><img alt="GitHub Issues" src="https://img.shields.io/badge/GitHub-Issues-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/castledking/Stall/wiki"><img alt="GitHub Wiki" src="https://img.shields.io/badge/GitHub-Wiki-181717?style=for-the-badge&logo=github"></a>
  <a href="https://castled.codes"><img alt="CASTLED CODEX" src="https://castled.codes/assets/logo-banner.png" width="140" height="35"></a>
</p>

**Stall** is a player shop system for Minecraft servers. It runs as a plugin on Spigot, Paper, Purpur and Folia, and as a Fabric mod on dedicated servers. Players turn an ordinary barrel or chest into a shop, stock it with items, and set up trades that other players can buy with a click.

Stall shops are real blocks with real stock. There is no client mod and no resource pack, and no stock kept in a plugin file: what a shop sells is whatever sits in its container, and players on an unmodified client can use every part of it.

> **Status:** Stall is new. See [docs/PLAN.md](docs/PLAN.md) for the plan and progress.

---

## How it works

1. **Get a Stall token** with `/stall token`.
2. **Use the token on a barrel or chest** you're allowed to open. It becomes your shop, and whatever is inside is your stock.
3. **Set up offers.** Sneak and right-click your shop with an empty hand to add trades: up to two items the buyer pays, and up to three items they get back, each with its own quantity. Click an item from your inventory into a slot to add it; your item stays with you.
4. **Players buy with a click.** Anyone else who right-clicks your shop sees your offers and can buy any offer the shop has stock for.
5. **Move it whenever you like.** Break your shop or use `/stall remove` and the token comes back to you, ready to set up somewhere else.

Shops can be barrels, chests or trapped chests. A double chest is one shop with 54 slots of stock, and players with `stall.barrel.large` get barrels that hold 54 too.

## Features

- **Physical shops:** a shop is a vanilla barrel, chest or trapped chest, and its contents are the shop's stock. Payments go into the same container.
- **Double chests:** a double chest is one shop covering both halves, 54 slots of stock. Owners can expand their chest shops; other players must place separate chests.
- **Large barrels:** give players `stall.barrel.large` and their barrel shops hold 54 slots of stock instead of 27. The owner opens one 54-slot view: the barrel's own 27 slots on top, and 27 extra slots below, saved on the barrel itself so they stay in step with it.
- **Two-for-three trades:** every offer has two payment slots and three reward slots. Each slot holds up to one stack of its item, so a buyer pays up to two stacks and gets up to three, 192 of most items. A slot can be left empty. For example, *1 emerald → 16 cobblestone + 4 coal*, or *1 diamond + 2 gold ingots → 8 iron ingots + 4 redstone + 1 lapis*.
- **Several offers per shop:** one row per offer, so a shop with three offers fits in a barrel-sized menu and a shop with up to six still fits on one page.
- **Clear availability:** each offer shows whether it's in stock, whether you can afford it, and whether the shop has room for your payment.
- **All-or-nothing trades:** every trade is checked in full before anything moves. You get everything you paid for, or nothing changes at all. No partial trades and no lost items when an inventory is almost full.
- **Exact item matching:** enchantments, names, durability, potion types and other item data all count, so a renamed or damaged item is never mistaken for the item an offer asks for.
- **Shop limits by permission:** set how many shops each rank can own using permission tiers such as `stall.limit.5`. Works with LuckPerms on both Paper and Fabric.
- **Claim friendly:** you can only turn a container into a shop where you could open it anyway, so GriefPrevention and other protection plugins and mods decide where shops can go. Buyers can still use shops inside claims they aren't trusted in.
- **Protected stock:** other players can't break a shop or open its container, and hoppers can't drain it, though they can still fill it. Explosions leave shop containers and their stock intact while damaging nearby blocks normally. Copper chests can't be shops, because copper golems take items out of them.
- **Vanilla clients:** no client mod or resource pack is needed on either platform.
- **One data format:** Spigot, Paper and Fabric servers read and write the same shop data.

## Requirements

Stall targets Minecraft 26.3 only for now.

| Platform | Minecraft | Java | Also needs |
| --- | --- | --- | --- |
| Spigot / Paper / Purpur / Folia | 26.3 | 25 | A server running one of these platforms |
| Fabric (dedicated server) | 26.3 | 25 | Fabric Loader 0.19.3 or newer, Fabric API for 26.3 |

Folia was verified on 26.3 build 5; earlier Folia builds are unverified.

LuckPerms is optional on both platforms. Without a permission plugin or mod, Stall uses the defaults in the [permissions](#permissions) table: everyone can trade and own 3 shops, and operators can manage every shop.

## Commands

| Command | |
| --- | --- |
| `/stall` | Show how shops work and the commands below. |
| `/stall token [amount]` | Get Stall tokens. |
| `/stall list` | List your shops and your shop limit. |
| `/stall list <player>` | List another player's shops. Needs `stall.admin`. |
| `/stall info` | Show the owner and offers of the shop you're looking at. |
| `/stall remove` | Remove the shop you're looking at and give its token back to its owner. Operators can remove any shop. |
| `/stall reclaim` | Collect tokens from shops removed while you were offline. |
| `/stall preview` | See your own shops the way buyers do, to test them. |
| `/stall admin give <player> [amount]` | Give a player Stall tokens. |

## Permissions

On Fabric, the same permissions are checked through Fabric API's permission API, so LuckPerms nodes are written the same way on both platforms.

| Permission | Default | |
| --- | --- | --- |
| `stall.use` | everyone | Open shops and buy from them. |
| `stall.create` | everyone | Turn a barrel or chest into a shop with a token. |
| `stall.token` | everyone | Use `/stall token`. |
| `stall.limit.<n>` | none | Own up to `n` shops, from 0 to 100. The highest tier you have wins. |
| `stall.limit.unlimited` | op | Own any number of shops. |
| `stall.barrel.large` | none | Barrel shops hold 54 slots of stock instead of 27. Shops made before the permission was given grow the next time their owner opens them; a large barrel stays large. |
| `stall.admin` | op | Edit and remove any shop, list anyone's shops, and use `/stall admin`. |

Players without a `stall.limit` tier can own 3 shops.

For rank rewards, grant `/lp group <rank> permission set stall.barrel.large true`. If tokens should come
only from playtime rewards, deny `stall.token` for ordinary players and have the reward script run
`stall admin give <player> <amount>` from the server console. Otherwise `/stall token` is available to everyone.

## Where shops are saved

Stall keeps its data in the world's own save folder, so backing up the world backs up its shops too:

```
<world>/stall/
  shops/<shop id>.json   one file per shop: its owner, location and offers
  shops/_broken/         files that couldn't be loaded, moved aside instead of deleted
  shops/_orphaned/       removed shop records kept for recovery
  returns.json           tokens waiting for /stall reclaim
```

Spigot, Paper and Fabric use the same files. Every shop's container also carries the shop's id. If the container at a
shop's spot doesn't, for example because it was replaced, the shop pauses until its owner sneaks and
right-clicks it, rather than selling whatever is in the new container.

A large barrel's 27 extra slots are saved on the barrel itself, as block data written with the chunk, so a
world backup or rollback keeps them in step with the barrel's own contents. Vanilla can't see them, so empty a
large barrel's extra slots before removing Stall or moving the world between Fabric and Spigot/Paper: each of
those keeps block data its own way. Missing containers are checked on chunk load, on interaction, and every ten seconds while loaded. Their shop records are archived and any consumed token is queued for `/stall reclaim`; unloaded chunks are left alone.

## Building

Building needs Java 25.

```bash
./gradlew build
```

| Module | What it is | Output |
| --- | --- | --- |
| `stall-core` | Shop model, trade planner and menu layout, shared by both platforms. It has no Paper, Fabric or Minecraft code, and the build fails if any creeps in. | bundled into both jars |
| `stall-paper` | The plugin for Spigot, Paper, Purpur and Folia. | `stall-paper/build/libs/Stall-Paper-<version>.jar` |
| `stall-fabric` | The Fabric mod. Install it with Fabric API. | `stall-fabric/build/libs/Stall-Fabric-<version>.jar` |

[docs/TESTING.md](docs/TESTING.md) is the checklist for testing a build in game.

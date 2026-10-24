# Stall: Research and Implementation Plan

Status: **approved 2026-10-23, scoped to Minecraft 26.3 only for now.** Progress is logged in §15.

**Current implementation update (2026-10-24):** the plugin now supports Spigot as well as Paper. It
compiles against Spigot API 26.3. Item persistence uses a small native-codec bridge to preserve the
shared item format; the original Paper-only API assessment below predates that extension. See §15.

Evidence tags used throughout:

- **[V] Verified**: checked directly against local artifacts: `javap` on the Minecraft 26.1/26.2/26.3 jars in the
  Loom cache, the Fabric API and Paper API jars in `~/.gradle/caches/modules-2`, Paper's 26.3 dev bundle
  source, and the upstream LuckPerms source in `/mnt/storage/devops/LuckPerms`.
- **[I] Inferred**: from documentation, a reference project's usage, or well-established platform behavior, but
  not checked line by line.
- **[P] Prototype**: needs a running server, and sometimes a client, to confirm.
- **[W] Workaround**: needs platform-specific code such as a mixin or a different event.

---

## 0. Summary

| Question | Answer |
| --- | --- |
| Architecture | `stall-core` (platform-neutral model, offer rules, trade planner, persistence codec, limits, GUI layouts) plus thin `stall-paper` and `stall-fabric` adapters. The core's boundary is enforced at build time, as `gp3d-core`'s is. |
| Shop representation | A vanilla **container block** (barrels first, chests later) converted with a **Stall token**. The container is both the anchor and the stock. Ownership is recorded in Stall's registry **and** stamped on the block entity (PDC on Paper, a Fabric data attachment), so the two can be cross-checked. |
| Initial floor | **Minecraft 26.3, Java 25, Spigot/Paper 26.3 (`spigot-api 26.3-R0.1-SNAPSHOT`), Fabric Loader ≥ 0.19.3, Fabric API ≥ 0.160.7+26.3.** |
| Widening | **Deferred: 26.3 only for now.** 26.1.2 – 26.3.x stays feasible (every API is verified present), behind GP3D-style linkage and boot checks. |
| Not recommended | 1.21.x. It would need a second remapped Fabric adapter on Java 21 for no gameplay gain. |
| Biggest risks | (1) Server-side GUIs on Fabric with vanilla clients. (2) Event ordering against claim plugins. (3) Theft vectors around containers. (4) Item identity and serialization parity between platforms. |
| First prototype | Fabric dedicated server with a vanilla 26.3 client: a barrel shop with one hard-coded offer, a custom server-side `ChestMenu`, and the core trade planner. |

---

## 1. Reference projects

`/mnt/storage/repos/GriefPrevention` does not exist. The cross-platform GriefPrevention fork is
**`/mnt/storage/repos/GriefPrevention3D`** (GP3D), and that is what this plan inspects. I also consulted:

- **Arboretum**, the newest 26.x toolchain settings in your repos.
- **LuckPerms** source at `/mnt/storage/devops/LuckPerms` (remote `upstream` = LuckPerms/LuckPerms).
- **iShop** source, cloned read-only to a scratch directory.

### 1.1 GriefPrevention3D

| Area | What it does | For Stall |
| --- | --- | --- |
| Modules | Root Bukkit plugin (Java 8 target, legacy and modern compat source sets). `gp3d-core` is platform-neutral, with `checkCoreBoundary` failing the build on `org.bukkit`, `net.minecraft` or `net.fabricmc` imports. `platforms/fabric-common` holds shared adapter sources. `fabric-1.21.11` uses remap Loom (intermediary) and `fabric-26.1` uses unobfuscated Loom, relocated to `mc26_1`. `fabric-bootstrap` (Java 8) picks an adapter at runtime and gates mixin configs. `fabric-linkage-check` is an ASM tool. The output is a universal jar. | **Reuse** the core boundary check, the linkage check and the boot smoke. **Drop** Java 8 and legacy Bukkit support, the bootstrap/adapter selector and the remap path: a 26.x-only project doesn't need them. |
| Fabric init | A `main` entrypoint goes to `UniversalFabricBootstrap`, then `GriefPreventionFabric.onInitialize`. Uses `environment: "*"`. Fabric API modules are declared individually, both in Gradle and in `fabric.mod.json`. | **Reuse**: one `main` entrypoint, per-module Fabric API dependencies, `environment: "*"` (dedicated and integrated servers both work, which helps testing). |
| Paper lifecycle | Classic `plugin.yml` plugin, `onEnable`/`onDisable`, Bukkit listeners. The interact handler is at `EventPriority.LOW` [V]. | **Reuse** `plugin.yml`. GP's LOW priority matters for Stall's ordering (§7.3). |
| Shared logic | Claim model, geometry, policies, YAML document codec and messages live in core. Platforms translate positions and entities. | Same split. |
| Commands | Fabric uses `CommandRegistrationCallback`, ordered after other mods with `addPhaseOrdering`. Bukkit uses `plugin.yml` commands. | Brigadier on both platforms (§9.3). |
| Permissions | On Fabric, the LuckPerms API directly (`LuckPermsPermissionResolver`), else defaults copied from `plugin.yml` (`FabricPermissionDefaults`). It does not use Fabric API's permission module. | **Redesign**: use Fabric API's `fabric-permission-api-v1`, which LuckPerms now implements (§2.1). No LuckPerms compile dependency. |
| Config and data | YAML in a data folder shared by both loaders (`plugins/GriefPreventionData`), updated in place while preserving comments. | Shared schema, yes. Location reconsidered (§8.4). |
| Events | Fabric API callbacks in the default phase (`UseBlockCallback`, `PlayerBlockBreakEvents`, …), plus about 25 mixins where Fabric API has no event (explosions, pistons, fluids, fire, …). | Stall needs only 2–3 mixins (§2.4). |
| Version support | One 26.x adapter compiled against 26.1, linkage-checked and boot-smoked on 26.1, 26.1.1, 26.1.2, 26.2 and 26.3. The README notes 26.2/26.3 renames (`Blocks.WHITE_WOOL` removed, `AxeItem` removed, `FarmBlock` → `FarmlandBlock`). | Evidence that Minecraft's "API" moves within 26.x. Claim only boot-tested versions. |
| Tests | JUnit 5.12, Mockito, core unit tests, boot smoke on dedicated servers, linkage check, addon compile checks. | Same toolkit. |
| GP integration surface | Bukkit: `DataStore#getClaimAt`, `Claim#checkPermission(…, ClaimPermission, …)`. `ClaimPermission.Container` exists, with `Inventory` kept as a deprecated alias [V]. Fabric: `GriefPreventionFabric.getClaimRepository()` → `findClaimAt` is public, but `ClaimSnapshot` carries no trust data and `FabricClaimTrustEvaluator` is package-private [V]. | An explicit GP integration on Fabric needs a small public trust query added to GP3D (§7.3). |

### 1.2 CrowBar

| Area | What it does | For Stall |
| --- | --- | --- |
| Modules | `common` (Java 21, no Minecraft dependencies; Gson and SLF4J are `compileOnly` because the runtimes ship them), `bukkit` (compiled against Paper 1.21.1, with manifest `paperweight-mappings-namespace: mojang`), and a `versionXX_Y/{fabric,neoforge}` pair per Minecraft line. `gradle/universal-jar.gradle` bundles the plugin into every loader jar. | **Reuse** the `codes.castled` group and packages, `compileOnly` runtime-provided libraries, `processResources`-expanded metadata, and the manifest attribute if we ship a universal jar. **Avoid** copying loader sources per Minecraft line. |
| Fabric init | Client-only (`environment: client`) with client mixins. | Not a model for a server mod. |
| Paper plugin | `api-version: "1.21"`, `folia-supported: true`, optional GP integration through `compileOnly` GP3D. | Same optional-integration pattern. |
| Tests | JUnit 5 in `common` and `bukkit`, including a test that every loader's language file matches the options. | Copy the language-file consistency test idea. |

### 1.3 Others

- **Arboretum** `gradle.properties` holds the latest known-good 26.x toolchain: Loom 1.18.4, Gradle 9.7.1,
  Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, paper-api 26.3.build.141-beta.
- **iShop**, your reference for feel and simplicity:
  - It uses a 54-slot GUI with a header row and 5 trade rows per shop. Each row has 2 "sell" items, 2 "price" items and a buy button.
  - The shop block is a barrel, but stock lives in a **virtual per-player store** in SQLite, not in the block.
  - Shop limits come from `ishop.create.limit.N`.
  - Its buy path counts empty slots heuristically and ignores the leftovers returned by `removeItem` and `addItem`, so items can be lost when inventories are nearly full.

  Stall keeps iShop's simplicity: 2-for-2 rows, one shop block, one click to buy. It differs in two ways: the physical container is the stock, and trades are all-or-nothing (§6).

---

## 2. Deliverable A: API floor report

### 2.1 Findings

| # | Finding | Evidence |
| --- | --- | --- |
| A1 | Minecraft 26.1 (released 2026-03-24), 26.2 (2026-06-16) and 26.3 (2026-09-15) all require **Java 25**. 1.21.11 requires Java 21. | [V] `mojang_minecraft_info.json` |
| A2 | 26.x ships unobfuscated (Mojang names), so one Fabric binary compiled against an older 26.x can link on newer ones, as long as no member it uses was renamed. | [I] GP3D README, and GP3D's linkage check passing for 26.1 → 26.3 |
| A3 | Fabric API `0.160.7+26.3` and `0.162.0+26.3` declare `fabricloader >=0.19.3`, `java >=25`, `minecraft ~26.3-`. Every module Stall needs (api-base, command-api-v2, events-interaction-v0, lifecycle-events-v1, networking-api-v1, permission-api-v1, data-attachment-api-v1) declares loader `>=0.19.3`. | [V] `fabric.mod.json` in the jars |
| A4 | **`fabric-permission-api-v1` is new in 26.x.** It is absent from Fabric API 0.145.x (26.1 and 26.1.1). Version 1.0.1 ships in 0.150.0 and 0.152.1 for 26.1.2, with the package marked `@ApiStatus.Experimental`. Version 1.0.3 is in 0.155.x, 1.0.2+ is in 26.2, and 1.0.8 is in 26.3, where the Experimental marker is gone. The API is unchanged between 1.0.1 and 1.0.8 apart from that marker. | [V] nested jars, sources diff |
| A5 | **Upstream LuckPerms Fabric implements `fabric-permission-api-v1`** (commit a97bdba0a, 2026-05-21). It still bundles lucko's `fabric-permissions-api` 0.7.0. A boolean node `ns:path` is checked as the LuckPerms permission `ns.path`, with `/` mapped to `.`. A non-boolean node is read as the LuckPerms **meta** key `ns:path`, parsed to an int for integer nodes. Offline contexts load the user from storage. | [V] `FabricPermissionsApiV1Listener.java` on `upstream/master` |
| A6 | Every Minecraft class and member Stall needs exists with the same signature in **26.1 and 26.3**: `ItemStack.isSameItemSameComponents/CODEC/OPTIONAL_CODEC/copyWithCount`, `ItemStackTemplate` (an immutable item + count + component patch, with `CODEC`), `ItemInstance#getMaxStackSize`, `ChestMenu.oneRow/threeRows/sixRows`, `MenuType.GENERIC_9x1…9x6`, `AbstractContainerMenu.clicked(int,int,ContainerInput,Player)/quickMoveStack/removed`, `SimpleMenuProvider`, `ServerPlayer.openMenu → OptionalInt`, `BarrelBlockEntity`, `ChestBlock.getContainer`, `ChestBlock.getStateForPlacement`, `CopperChestBlock`, `Container.getMaxStackSize(ItemStack)`, `Inventory.getNonEquipmentItems`, `HopperBlockEntity.suckInItems(Level,Hopper)`, `ServerExplosion`, `Display$TextDisplay/$ItemDisplay`, `Interaction`, `Entity.addTag`, `DataComponents.CUSTOM_DATA/CUSTOM_NAME/ITEM_MODEL`, `SavedDataType`, `PermissionLevel`, and `NbtIo.writeCompressed/readCompressed`, `NbtUtils.addCurrentDataVersion`, `DataFixers`, `References.ITEM_STACK`. | [V] `javap` |
| A7 | Fabric API on 26.3: `Event.register(Identifier, T)` and `Event.addPhaseOrdering`, `UseBlockCallback`, `PlayerBlockBreakEvents.BEFORE/AFTER`, `ServerChunkEvents.CHUNK_LOAD/UNLOAD`, `AttachmentRegistry.create(...)` with a `BlockEntityMixin` in the attachment module. | [V] |
| A8 | Paper API **1.21.11, 26.1.2.build.74-stable and 26.3.build.193-stable** all have: `ItemStack#serializeAsBytes/deserializeBytes/isSimilar/asQuantity/getMaxStackSize`, `TileState#getPersistentDataContainer`, `Inventory#getStorageContents/setStorageContents/removeItemAnySlot`, `MenuType`, `TextDisplay`, `Interaction`, Brigadier `Commands` with `LifecycleEvents.COMMANDS`, `DataComponentTypes`, `PersistentDataContainerView`, `InventoryMoveItemEvent`, `RegionScheduler` and `DoubleChestInventory`. | [V] `javap` |
| A9 | `MenuType` and `DataComponentTypes` are `@ApiStatus.Experimental` on Paper 1.21.11 and 26.1.2, and **not** on 26.3 (`DataComponentTypes` is `@MinecraftVersionDependent` there). | [V] class annotations |
| A10 | Paper's `ItemStack#serializeAsBytes()` encodes the vanilla `ItemStack.CODEC` to NBT with registry context, adds `DataVersion`, and writes it with `NbtIo.writeCompressed` (gzip). `deserializeBytes` runs the data fixer from the stored `DataVersion`. A Fabric server can produce and consume the same format with the vanilla classes in A6. | [V] Paper 26.3 dev bundle: `MCUtil`, `CraftMagicNumbers`; byte-level parity is [P] |
| A11 | `UnsafeValues#serializeItemAsJson` exists, but `UnsafeValues` is `@Deprecated`, so don't build persistence on it. | [V] |
| A12 | Paper accepts `api-version: "26.1"` on 26.x servers (iShop ships it) and refuses plugins whose `api-version` is newer than the server. Paper 26.3 accepts `api-version: '26.3'`. | [I] iShop `plugin.yml`, comment in Karasu's `plugin.yml`. `'26.3'` on 26.3: [V] boot smoke (§15) |

### 2.2 Recommended floor

|  | **Initial (v0.x)** | If widened later |
| --- | --- | --- |
| Minecraft | **26.3.x** | 26.1.2 – 26.3.x |
| Java | **25**: toolchain and `--release 25` in every module | 25 |
| Fabric Loader | declare **`>=0.19.3`** (what Fabric API 26.3 itself requires), build with 0.19.5 | same |
| Fabric API | declare **`>=0.160.7+26.3`** (the oldest 26.3 build on hand, and CrowBar's floor), build with 0.162.0+26.3 | `>=0.150.0+26.1.2`, the first with the permission API |
| Fabric API modules | `fabric-api-base`, `fabric-command-api-v2`, `fabric-events-interaction-v0`, `fabric-lifecycle-events-v1`, `fabric-networking-api-v1`, `fabric-permission-api-v1`, `fabric-data-attachment-api-v1` | same |
| Paper | **`io.papermc.paper:paper-api:26.3.build.193-stable`** (compileOnly). The `plugin.yml` `api-version` is settled by prototype P7. | compile against the floor's paper-api |
| Build | Gradle 9.7.1, `net.fabricmc.fabric-loom` 1.18.x (the non-remapping plugin for unobfuscated 26.x) | |

Rationale:

- **26.3 first, and only 26.3 for now** (decided 2026-10-23). It's your candidate, every API is verified there,
  Paper has no Experimental APIs Stall would touch, Fabric's permission API is final there, and the test matrix
  has one cell.
- **26.1.2 is the realistic lower bound.** All Minecraft and Paper APIs are verified present on it. 26.1 and
  26.1.1 have no Fabric permission API. A fallback to lucko's `fabric-permissions-api` v0 would work, since
  LuckPerms bundles it, but that adds a second permission path for two patch releases. On Paper 26.1.2, avoid
  `MenuType`/`DataComponentTypes` (Experimental there) or accept them. Stall doesn't need either: classic
  `Bukkit.createInventory` with a custom `InventoryHolder` is stable on every version.
- **Not 1.21.x.** The Paper side could go back easily, but the Fabric side would need a second, remapped
  (intermediary) adapter compiled for Java 21, which is GP3D's bootstrap and adapter-selection machinery. That
  doubles the matrix for no gameplay benefit.
- **Claims.** Advertise only versions that pass a dedicated-server boot smoke, plus the linkage check when a
  range is claimed. Don't claim support for versions that haven't been verified.

### 2.3 APIs per feature

| Feature | Paper (public API) | Fabric / Minecraft |
| --- | --- | --- |
| Token item | `ItemMeta` PDC, display name, lore, glint override | `DataComponents.CUSTOM_DATA`, `CUSTOM_NAME`, `LORE`, `ITEM_MODEL`, `ENCHANTMENT_GLINT_OVERRIDE` |
| Use token on a container | `PlayerInteractEvent` (RIGHT_CLICK_BLOCK) | `UseBlockCallback` in a late phase |
| Block marker | `TileState#getPersistentDataContainer` + `BlockState#update()` | `AttachmentRegistry` persistent attachment on the block entity |
| Stock access | `Container#getInventory`, `DoubleChestInventory` | `BarrelBlockEntity` as `Container`, `ChestBlock.getContainer` (`CompoundContainer`) |
| Menus | `Bukkit.createInventory(holder, size, Component)`, `InventoryClickEvent`, `InventoryDragEvent`, `InventoryCloseEvent` | `ChestMenu` subclass for `MenuType.GENERIC_9xN`, `SimpleMenuProvider`, `ServerPlayer.openMenu` |
| Trades | `PlayerInventory#getStorageContents`, `Inventory#setItem`, `ItemStack#isSimilar`, `getMaxStackSize` | `Inventory.getNonEquipmentItems`, `Container.setItem/getMaxStackSize(ItemStack)`, `ItemStack.isSameItemSameComponents` |
| Break, explode, hoppers | `BlockBreakEvent`, `EntityExplodeEvent`, `BlockExplodeEvent`, `InventoryMoveItemEvent` | `PlayerBlockBreakEvents.BEFORE`; mixins for explosions and hoppers [W] |
| Commands | Brigadier via `LifecycleEvents.COMMANDS` | `CommandRegistrationCallback` (Brigadier) |
| Permissions | `Permissible#hasPermission` | `PermissionContextOwner.checkPermission(...)` on `ServerPlayer` (`fabric-permission-api-v1`) |
| Item persistence | `serializeAsBytes` / `deserializeBytes` | `ItemStack.CODEC` + `NbtOps` + `NbtUtils.addCurrentDataVersion` + `NbtIo` + DFU |
| Lifecycle | `onEnable`, `onDisable`, `ChunkLoadEvent`, `PlayerQuitEvent` | `ServerLifecycleEvents`, `ServerChunkEvents`, `ServerPlayConnectionEvents` |
| Optional visuals | `TextDisplay`, `ItemDisplay`, scoreboard tags | `Display$TextDisplay`, `$ItemDisplay`, `Entity.addTag` |

### 2.4 NMS, internals, mixins and unstable APIs

- **Paper: none needed.** Every feature uses public, non-Experimental API on 26.3. No NMS and no
  paperweight-userdev.
- **Fabric:** Minecraft's own classes *are* the API surface on Fabric. There is no Bukkit-style layer, so the
  adapter touches `net.minecraft` directly, which is normal. Mixins [W] are needed only where Fabric API has
  no hook:
  1. **Explosion protection**: `ServerExplosion` (GP3D does the same).
  2. **Hopper and hopper-minecart extraction**: `HopperBlockEntity.suckInItems(Level, Hopper)`, which serves
     both kinds of hopper.
  3. **Double-chest merge guard**, only once chests are supported: `ChestBlock.getStateForPlacement`.

  All three are server-side, so vanilla clients are unaffected.
- **No custom registry entries** (items, blocks, entities, menu types) on Fabric. Adding any would trigger
  registry sync and force clients to install the mod. Everything is a vanilla item, block, entity or menu with
  components and data. [I] Fabric registry-sync behavior, well established.

### 2.5 Can the data and rules be shared?

Yes. The shop, offer and limit models and the trade planner are pure Java. Item stacks are the only
platform-shaped data. Both platforms can persist them as the **same bytes**: vanilla `ItemStack.CODEC` NBT with
`DataVersion`, gzip-compressed (A10). Matching rules (exact type + components) map to
`isSameItemSameComponents` on Fabric and `isSimilar` on Paper. That parity still needs testing (P5).

---

## 3. Shop creation and ownership

### 3.1 Representation options

| Option | Vanilla client | Survives restarts | Protection plugins understand it | Problems | Verdict |
| --- | --- | --- | --- | --- | --- |
| **Container block as anchor, with a marker in its block-entity data** | yes | yes (block-entity data) | yes (container access) | double chests, hoppers, unobserved removal | **Recommended** |
| Interaction or display entity as anchor, linked to a container | yes | yes, but entities can be killed, duplicated or moved | entity rules differ from container rules | needs remote linking, `/kill @e` deletes shops, orphaned entities | Cosmetic layer only |
| Custom block or block entity | **no** on Fabric (registry sync); impossible on Paper | — | — | needs a client mod | Rejected |
| Sign on the container (ChestShop style) | yes | yes | yes | signs are editable and fragile; text config is clunky | Optional label later |
| Registry entry only (no on-block marker) | yes | yes | yes | **hijack risk**: if the container is removed without Stall noticing (WorldEdit, `/setblock`, rollback, regeneration), a new container at the same spot inherits someone else's shop and starts selling its new owner's items | Rejected as the only mechanism |

**Recommendation:** the shop *is* a container block. The registry holds the shop. The block entity holds a small
marker `{shop: <uuid>}`. Trades run only while **registry location, block type and marker agree**. The prompt
asks for the shop to be "linked to an inventory": here the link is the container itself.

Linking a **remote** container (a display stall in one place, stock elsewhere) is deferred. It brings
unloaded-chunk handling, cross-chunk atomicity and a larger theft surface. If it's wanted later, limit it to
containers within a few blocks in loaded chunks.

### 3.2 Container types

1. **Barrels in the MVP.** 27 slots, they never merge, pistons can't move them, and copper golems ignore them
   [I]. Only explosions and hoppers need guarding.
2. **Single and double chests and trapped chests** in milestone 6, together with the merge guard.
3. **Excluded:** shulker boxes (break and walk away with the whole stock), copper chests (copper golems pull
   from them [I]), ender chests (per-player), hoppers and droppers (they move items by themselves).

### 3.3 The Stall token

- A **vanilla, non-placeable item with no use action**. The default is `minecraft:paper` with a custom name,
  lore, glint and an optional `item_model`, all configurable. It carries a marker:
  - Paper: a PDC key `stall:token`.
  - Fabric: the same NBT path Paper's PDC uses, `custom_data.PublicBukkitValues."stall:token"`, so tokens
    survive a loader switch [P].
- Name tags and other usable items are avoided: using one on a mob or block would consume it.
- Obtained with `/stall token` (permission `stall.token`) or given by an admin.
- **Tokens are fungible and carry no entitlement.** The shop limit counts shops, not tokens, so a duplicated
  token grants nothing. Each shop records `token.consumed`, and deleting a shop returns a token **only if one
  was consumed**, so admin-created shops can't mint tokens.

### 3.4 Interaction map

| Who | Action | Result |
| --- | --- | --- |
| Holder of a token | right-click an allowed container they could open anyway | the container becomes a shop, one token is consumed, and the manager menu opens |
| Non-owner | right-click | the buyer menu opens and the vanilla container open is suppressed |
| Owner | right-click | the vanilla container opens, for restocking and collecting payments |
| Owner, or `stall.admin` | sneak + right-click with an empty hand | the manager menu opens: offers, *Open stock*, *Preview as buyer*, *Remove shop* |
| Owner | break the container | the shop is removed, its contents drop as in vanilla, and the token is returned |
| Non-owner | break | denied, unless they have `stall.admin` |

Owner right-click opening the stock (rather than the manager) keeps restocking a one-click job. The swap can be
made configurable.

### 3.5 Creation sequence (both platforms)

1. The interaction arrives **after** protection plugins have run (§7.3).
2. Check the held item is a token, the block is an allowed container type, and the world is allowed.
3. Check the container isn't already a shop and has no pending loot table: `Lootable#hasLootTable` on Paper,
   `RandomizableContainer#getLootTable` on Fabric.
4. Check the player has `stall.create` and is under their shop limit.
5. Stamp the marker on the block entity. On Paper this means `TileState` PDC followed by `state.update()`,
   because a forgotten `update()` is a classic snapshot pitfall.
6. Register and persist the shop.
7. Consume one token.
8. Open the manager menu.

Steps 5–7 run inside one main-thread call.

### 3.6 Removal, moving and token return

- **Removal paths:** the owner breaks the container, the owner uses *Remove shop* or `/stall remove`, or an
  admin removes it.
- **Return:** the token goes into the owner's inventory, is dropped at the owner's feet if their inventory is
  full, or waits in the owner's **pending returns** if they're offline (delivered on join, or with
  `/stall reclaim`).
- **Return order:** persist the pending return, give the token, then mark it delivered. A crash between the
  two writes can deliver one extra token (at-least-once), which is harmless because tokens confer nothing
  (§3.3). The alternative order could lose a token.
- **Moving** is removing the shop and creating it again, exactly as you described. Offers can optionally be
  kept on the owner's "last shop" so a moved shop starts pre-configured. This is a later nicety.

### 3.7 Edge cases

| Case | Handling |
| --- | --- |
| Container removed without Stall noticing (WorldEdit, `/setblock`, rollback, regeneration, explosion with protection off) | Lazy **reconciliation** on chunk load, on interaction, and in a periodic sweep of loaded shops. Container missing, or marker belongs to another shop → `ORPHANED`: trades are blocked, a token is queued for the owner, and the record is archived. |
| Registry entry present but marker missing (crash before chunk save; loader switch) | `UNVERIFIED`: no trades. The owner re-confirms from the manager menu, which re-stamps the marker. |
| Marker present but no registry entry (data folder restored from an older backup) | The marker is ignored. An admin can adopt it with `/stall admin adopt`. |
| Unloaded chunks | Stall never loads chunks. Shops in unloaded chunks stay in the registry, and reconciliation runs when the chunk loads, deferred to the next tick. |
| Protection plugins | Creation piggybacks on the player's own interaction, so any protection that blocks opening the container blocks shop creation (§7.3). |
| Restarts | The registry loads from disk at startup. Markers live in chunk data and are checked lazily. |

---

## 4. Offers and trade configuration

### 4.1 Model

- An offer has up to two **cost** slots (what the buyer pays) and up to two **reward** slots (what the buyer
  gets).
- Each slot is empty or holds an `OfferItem { item: ItemTemplate (count 1), amount: int, match: EXACT }`.
- Amounts may exceed a stack, up to a configurable cap (default one container's worth).
- An offer needs at least one cost and one reward.
- The per-side count is a list with a configurable maximum (default 2), so "at least two" isn't hard-coded.
- **Offers per shop:** config `max-offers` defaults to **6**, which always fits one page (§5).
  A `stall.offers.<n>` permission tier is possible later.
- **Validation** runs on save and on load:
  - items must not be air;
  - no blacklisted item types (config, like iShop's `disabledItems`);
  - each serialized template must be ≤ 16 KiB, which keeps oversized shulker or book NBT from bloating menu
    packets and kicking buyers;
  - amounts must be between 1 and the cap;
  - identical items within one side are merged into a single requirement.

### 4.2 Item identity and metadata

- **EXACT matching only in v1:** same item type and all data components equal (enchantments, damage, custom
  name, potion contents, written-book content, container contents, `custom_data` and PDC, and so on). Fabric
  uses `ItemStack.isSameItemSameComponents` [V]; Paper uses `ItemStack#isSimilar` [V]. Parity between the two
  is prototype P5.
- **Custom items** from other plugins are just components or PDC and match exactly.
- **Later match modes** (ignore damage, ignore name, type only, item tag) must move the **actual** removed
  stacks, never template copies. Otherwise a damaged sword paid in could come out repaired. The planner moves
  real stacks from v1 onward, so those modes can be added safely (§6.2).

### 4.3 How owners configure offers

- **Primary: a GUI editor using "ghost items".** The owner holds an item on the cursor and clicks an editor
  slot. The slot shows a copy with the cursor's count, and **the cursor keeps the real item**. With an empty
  cursor:

  | Click | Effect |
  | --- | --- |
  | left | amount +1 |
  | right | amount −1 |
  | shift-left | amount + one stack |
  | shift-right | amount − one stack |
  | drop key | clear the slot |

  No real item ever enters the menu, so the editor has no duplication surface.
- **Commands** are for admin and debug use (`/stall admin inspect`, export and import). Configuring four item
  slots by command is clumsy for players.
- Saving bumps `shop.revision`. Buyers' open menus see the new revision and refresh (§6.4).

### 4.4 Keeping offers in step with the container

Stock is never copied into Stall's data. The container *is* the stock. Availability is computed live by the same
planner the trade uses:

- **Times available:** how many complete trades the stock supports.
- **Room for payment:** whether the container can take the payment.

The figures are recomputed when a menu opens and after every trade, and shown in the menu.

---

## 5. Buyer menu

### 5.1 Does a barrel-sized menu work?

A barrel menu is 9 × 3 = 27 slots. An offer needs four item slots plus some indication of availability and a
way to buy. Laid out one offer per row, a barrel holds **3 offers clearly**. Packing two offers into a 9-slot row
(4 + divider + 4) fits 6, but buyers can no longer tell at a glance which items are paid and which received, and
there is nowhere to show status. **I don't recommend it.**

Adaptive layout, one row per offer, in villager order (pay ▸ receive):

```
col:   0      1      2      3       4      5      6      7      8
     [   ]  [P1 ]  [P2 ]  [ ▶ ]   [R1 ]  [R2 ]  [   ]  [BUY]  [   ]
```

- **1–3 offers → 9×3, barrel-sized.** **4–6 offers → 9×4 to 9×6, still a single page.** More than 6 (only if an
  admin raises `max-offers`) → 9×6 with 5 offer rows and a navigation row.
- Slot 3 is the **status arrow**:
  - lime pane: "In stock — 12 trades left"
  - red: "Out of stock"
  - orange: "You can't afford this"
  - gray: "Shop is full"
- `BUY` is a lime dye when the trade is possible, gray otherwise. Clicking anywhere in the row except BUY does
  nothing.
- Item lore says *You pay* or *You receive* plus the exact amount. Display copies show their amount as the stack
  count up to 99, using the `max_stack_size` component on the copy [I]; larger amounts show as "×128" in lore.
- The title names the owner, for example "Steve's Stall".
- **A compact alternative**, for servers that want many offers in a barrel: one slot per offer (18 per page plus a
  navigation row), then a **hopper-sized confirmation** `[P1][P2][BUY][R1][R2]`. Offer it later as
  `menu.layout: compact`.
- The **merchant (villager) screen** was considered: native scrolling, "A + B → C" rendering and an
  out-of-stock state. It was **rejected** because it has a single result slot, which can't express two reward
  items, and because vanilla trade logic moves the items, which makes stock bookkeeping harder to keep correct.

### 5.2 Menu safety (both platforms)

The menu is a **view**:

- Display items are copies.
- **Every** click in both halves of the screen is cancelled. That covers shift-click, number keys, the
  offhand-swap key, double-click collect, drag, and drop.
- The cursor therefore stays empty, and the player's inventory changes only through a committed trade.
- The menu closes if the player moves out of range or the shop stops being valid (`stillValid`).

---

## 6. Deliverable D: Transaction design

### 6.1 Threading guarantee

On Paper (not Folia) and on Fabric, game logic, events, packet-driven menu clicks, hopper ticks and container
access all run on **the main server thread** [I]. A trade that plans and commits inside one call is therefore
atomic with respect to every other player, hopper and owner action. No locks are needed, and none would help.
Folia is **not supported initially**; see §13.

### 6.2 The planner, in core and platform-neutral

The core works on platform-neutral slots:

```java
record SlotStack(ItemHandle item, int count, int maxStack)
// ItemHandle wraps a platform ItemStack copy and answers matches(other)
// via isSameItemSameComponents (Fabric) or isSimilar (Paper)

TradePlan plan(Offer offer, int times, SlotStack[] buyer, SlotStack[] stock):
    b = copy(buyer); s = copy(stock)
    paid    = removeAll(b, offer.costs   × times)  or FAIL(CANNOT_AFFORD)
    taken   = removeAll(s, offer.rewards × times)  or FAIL(OUT_OF_STOCK)
    insertAll(b, taken)                            or FAIL(INVENTORY_FULL)
    insertAll(s, paid)                             or FAIL(SHOP_FULL)
    return OK(diff(buyer, b), diff(stock, s))
```

- **Removals happen before insertions on each side**, so space freed by paying or selling counts. It also
  handles the same item appearing on both sides.
- `removeAll` returns the **actual** stacks it removed, and `insertAll` inserts exactly those (§4.2).
- `insertAll` first tops up matching stacks, then uses empty slots. Each slot's cap is
  `min(item max stack, container max stack)`, so unstackables, `max_stack_size` components and amounts above a
  stack all work.
- Requirements for identical items are summed before checking.
- The buyer side covers only the 36 storage slots: Paper `getStorageContents`, Fabric `getNonEquipmentItems`.
  Armor and offhand are never touched. Hotbar slots are drained last.
- The stock side is the whole container, including both halves of a double chest.
- All of this is **plain-JUnit testable with fake items**, which is where most of the correctness work happens:
  property tests that items are conserved, nothing is created, and nothing goes negative.

### 6.3 Execution sequence (per BUY click)

1. **Debounce.** Ignore the click if it comes within `buy-cooldown-ticks` (default 4) of the player's last BUY.
2. **Session check.** The menu session's `shopId`, `offerId` and `revision` must match the live shop. On a
   mismatch, refresh the menu and tell the buyer the shop changed.
3. **Validate.**
   - The shop is `ACTIVE`, and the buyer isn't the owner (configurable) and has `stall.use`.
   - The chunk is loaded, the block type is right, and the **marker matches**.
   - The player is within reach, in the same world, and still has this menu open.
   - Optionally, the explicit protection hook says the owner is still permitted there (§7.3).
4. **Snapshot** the buyer's storage slots and the container's slots as copies.
5. **Plan** (§6.2). On failure, send a specific message: can't afford, out of stock, inventory full or shop full.
   An out-of-stock or shop-full failure also sends the owner a rate-limited notice.
6. **Commit.** Write every changed slot on both sides. Then:
   - mark the container changed (`setChanged()` on Fabric; Paper writes through to the block);
   - sync the player inventory (automatic on Paper; on Fabric, broadcast the inventory menu).

   Slot writes on in-memory containers can't fail, so the commit can't stop partway.
7. **Afterwards.**
   - Append a line to the audit log (`trades/<date>.jsonl`, configurable).
   - Notify the owner: an online message, or a summary on their next join.
   - Re-render every open session for this shop on the next tick.

Shift-click BUY ("buy as many as possible") runs the planner with an increasing `times` on the cumulative copies,
then commits once. That's in milestone 6.

### 6.4 Failure handling and concurrency

| Situation | Outcome |
| --- | --- |
| Two buyers click in the same tick | Their clicks are handled one after the other. The second re-plans against the updated stock and fails cleanly if the stock ran out. |
| Repeated or rapid clicks | Each click is a full, independent trade (intended), with a debounce against accidental doubles. Nothing is pending between clicks. |
| Owner edits offers while a buyer is looking | The revision mismatch rejects the click and the menu refreshes. |
| Owner has the container open during a trade | It's the same `Container` object. Vanilla's open menu receives the slot changes through its normal broadcast. |
| Shop removed or orphaned while menus are open | All sessions for the shop close. |
| Buyer disconnects, dies or teleports | The session ends when the menu closes or the player quits. No trade spans calls, so nothing is half-done. |
| Container replaced or broken between menu open and click | The marker and block checks in step 3 fail. |
| Crash right after a commit | Inventories and container are whatever the world last saved, which is vanilla consistency. Stall persists no stock, so it has nothing to reconcile. |

### 6.5 Platform differences

| | Paper | Fabric |
| --- | --- | --- |
| Click entry point | `InventoryClickEvent` (top inventory holder is a Stall holder), fired **before** vanilla applies the click. Cancel it, then act. | Our `ChestMenu` subclass's `clicked(...)` is called from the packet handler on the main thread. Never call `super`. After `clicked` returns, the handler records the client's predicted slots and cursor as the client's state and broadcasts the real state, so a refused click is undone with no extra code [V]. |
| Drag | Cancel `InventoryDragEvent`. | Arrives as `ContainerInput.QUICK_CRAFT` through `clicked`; ignore it. |
| Shift-click from the player inventory | Cancel all clicks while the view is open. | `quickMoveStack` returns `ItemStack.EMPTY`. |
| Opening another menu from a click | Schedule it for the next tick. Opening or closing inside the click event is discouraged [I]. | Allowed, but done next tick for symmetry. |
| Inventory resync | Automatic when the event is cancelled. | Automatic: `handleContainerClick` broadcasts after every click [V]. |

---

## 7. Permissions, limits and protection

### 7.1 Shared abstraction (in core)

```java
interface PermissionService {
    boolean has(PlayerRef p, Perm node);          // Perm = {paperNode "stall.limit.5", fabricId "stall:limit/5", default}
    OptionalInt intOption(PlayerRef p, String key); // optional, for meta-based limits
}
```

Adapters:

- **Paper:** `Player#hasPermission`. This works with any Bukkit permission plugin, LuckPerms included.
- **Fabric:** `fabric-permission-api-v1`. Calls go through `ServerPlayer` (`checkPermission(Identifier, default)`
  or `checkPermission(Identifier, PermissionLevel)`) [V]. LuckPerms maps `stall:limit/5` to `stall.limit.5`
  [V], so **admins write the same LuckPerms nodes on both platforms**. With no permission provider installed,
  boolean nodes fall back to their default and admin nodes to vanilla operator level (`PermissionLevel`) [V API].
- **No LuckPerms compile dependency on either side.**

### 7.2 Shop limits

The core `LimitResolver` decides a player's limit in this order:

1. `stall.limit.unlimited` → no limit.
2. Otherwise, the highest `n` among the **configured tiers** (for example `[1, 3, 5, 10, 25, 50]`) for which the
   player has `stall.limit.<n>`.
3. Otherwise, the config value `limits.default` (default 3).

Tier nodes are recommended as the primary mechanism: they work with every permission plugin on Paper, and with
any Fabric permission provider. Meta-based limits are an optional extra. On Fabric they come through an
integer node read as LuckPerms meta `stall:shop_limit` [V mapping]. On Paper they'd need the LuckPerms API, so
they're deferred.

Limits are checked at creation, while the player is online, so offline lookups aren't needed. Lowering a limit
never deletes existing shops; it only blocks new ones.

### 7.3 Protection and event ordering

| Interaction | Must run | Paper | Fabric |
| --- | --- | --- | --- |
| **Shop creation** (token on container) | **after** protection, so claims decide | `PlayerInteractEvent` at `HIGH`; proceed only if `useInteractedBlock() != DENY` (GP handles it at `LOW` [V]) | `UseBlockCallback` registered in phase `stall:after_protection`, ordered after `Event.DEFAULT_PHASE`. GP3D registers in the default phase [V], so when it denies (`FAIL`), Stall never sees the click. |
| **Buyer access** (non-owner opens a shop) | **before** protection, so buyers can shop inside claims they aren't trusted in | `PlayerInteractEvent` at `LOWEST`: deny the vanilla open, cancel, and open the buyer menu next tick | `UseBlockCallback` in phase `stall:shop_access`, ordered **before** `DEFAULT_PHASE`. Return `SUCCESS`, which skips GP3D's callback and the vanilla open. |
| **Owner restocking** | normal | pass through untouched | pass through untouched |

Letting buyers bypass claims is safe:

- A buyer menu grants no container access, and trades touch only the shop's own container, under Stall's rules.
- The owner needed container access at that spot to create the shop in the first place.
- An optional check of the owner's continued trust covers revoked trust (below).

Prototype P2 still needs to confirm GP's Paper handler doesn't send its "no permission" message for clicks Stall
already cancelled.

**Explicit integrations (optional, a `ProtectionHook` interface in core):**

- **GriefPrevention on Paper:** `softdepend`. `DataStore#getClaimAt` plus `Claim#checkPermission(owner,
  ClaimPermission.Inventory, null)`; `Inventory` is a deprecated alias of `Container` in GP3D, so code compiled
  against either works [V]. Uses: "the shop owner still has container trust here" (deactivate on revocation) and
  "shops only inside claims" policies.
- **GP3D on Fabric:** the public `ClaimRepository.findClaimAt` exists, but **there is no public trust query**
  [V]. Until GP3D exposes one (a small addition, since you maintain it), rely on the ordering above.
- Others (WorldGuard flags, Towny, Lands) are later hooks, as iShop has.

**Stall's own protection of shop blocks**, which matters in unclaimed land:

| Threat | Paper | Fabric |
| --- | --- | --- |
| Non-owner breaks the shop | `BlockBreakEvent` (LOW) | `PlayerBlockBreakEvents.BEFORE` → `false` |
| Explosions | `EntityExplodeEvent` / `BlockExplodeEvent`: remove shop blocks from the block list | mixin on `ServerExplosion` [W] |
| Hopper or hopper-minecart extraction | `InventoryMoveItemEvent` from a shop source → cancel | mixin on `HopperBlockEntity.suckInItems` [W] |
| Double-chest merge by another player (chest milestone) | `BlockPlaceEvent` → deny | mixin on `ChestBlock.getStateForPlacement` → force `SINGLE` [W] |
| Pistons | block entities are immovable [I] | same |
| Copper golems | copper chests are excluded; golems depositing into shop chests only adds items [I] | same |

Hopper *insertion* into a shop stays allowed (owner automation), configurable.

---

## 8. Persistence and data model

### 8.1 Shop document (shared JSON schema)

As implemented in milestone 2 (`ShopCodec`):

```json
{
  "schema": 1,
  "id": "4b7c0c1e-…",
  "owner": "069a79f4-…",
  "ownerName": "Steve",
  "location": { "world": "minecraft:overworld", "x": 120, "y": 64, "z": -33 },
  "container": "minecraft:barrel",
  "tokenConsumed": true,
  "created": "2026-10-24T09:00:00Z",
  "offers": [
    {
      "id": "e1d2…",
      "cost":   [ { "amount": 1,  "item": { "id": "minecraft:emerald",     "nbt": "H4sIAAAA…" } }, null ],
      "reward": [ { "amount": 16, "item": { "id": "minecraft:cobblestone", "nbt": "H4sIAAAA…" } },
                  { "amount": 4,  "item": { "id": "minecraft:coal",        "nbt": "H4sIAAAA…" } } ]
    }
  ],
  "preservedOffers": []
}
```

Each field answers one of the persistence requirements:

- **Identifier and owner:** `id`, `owner`. `ownerName` is only a display cache.
- **World and location:** `location`. `world` is the dimension key (`World#getKey()` on Paper,
  `ResourceKey<Level>` on Fabric), which is the same on both platforms; GP3D uses the same `namespace:path`
  form.
- **Anchor and linked inventory:** `location` + `container`. The container *is* the anchor, and the on-block
  marker carries `id`.
- **Offers:** `offers`. Each side lists its slots in order, `null` for an empty one, so items keep their
  places.
  - `item.nbt` is Base64 of the Paper `serializeAsBytes` format (A10), with count 1. The Minecraft
    DataVersion travels inside the NBT, so items are upgraded by the data fixer on load. A separate `dv`
    field turned out to be unnecessary.
  - `item.id` is for humans and admin tools.
- **Creation item state:** `tokenConsumed` (§3.3).
- **Shop state:** whether a shop is paused isn't stored. It's worked out from the barrel's marker each
  time (§3.7). `revision` is runtime-only.
- **Forward compatibility:** `preservedOffers` keeps, word for word, any offer whose items this server
  can't read (a removed mod, say). Loading retries them each time, so nothing is lost. A file whose
  `schema` is newer than this version is left untouched, never moved and never overwritten.

Nothing in the schema depends on how a shop looks.

### 8.2 Other files

- `returns.json`: pending token returns per player (§3.6).
- `trades/YYYY-MM-DD.jsonl`: the append-only audit log, optional.
- `_schema`: the current schema version, migrated by a core migrator chain.
- Config `config.yml` and `messages.yml`: YAML. Paper ships SnakeYAML. On Fabric, shade and relocate it as GP3D
  does (about 330 KB).
- JSON is read and written with **Gson**, which both runtimes ship, so `compileOnly` as in CrowBar [I].

### 8.3 Write strategy

- At runtime the in-memory registry, indexed by location and by owner, is authoritative.
- Each change to a shop serializes an immutable snapshot **on the main thread**. A single I/O thread writes
  `shops/<id>.json` with write-temp, fsync and atomic move.
- On shutdown the queue is flushed synchronously.
- At startup:
  - all files load and are validated;
  - a corrupt file is moved to `shops/_broken/` and logged, never deleted;
  - an item blob that fails to decode makes its offer disabled, not discarded.
- One file per shop keeps writes small even with thousands of shops, and makes diffs and backups trivial.

### 8.4 Location

Recommended: put **data in the main world's save folder** (`<world>/stall/`) and **config in the platform's
usual place** (`plugins/Stall/`, or `config/stall/` on Fabric). A world backup or rollback then restores shops
together with their containers and markers, and that consistency matters more for a shop system than a shared
`plugins/` folder.

The alternative is GP3D's shared `plugins/StallData`, which makes switching loaders seamless but drifts from the
world on rollbacks. Paper 26.x's world-folder layout is [P].

Fabric's world-saved `SavedData` and Paper's world PDC were considered: they're tied tightly to world saves, but
they're platform-specific and harder to inspect or migrate. Plain files plus reconciliation (§3.7) cover the
divergence cases.

### 8.5 Loader switch caveat

Paper stores block PDC as `PublicBukkitValues`; Fabric attachments live under their own key. Each platform drops
the other's data when it re-saves a chunk. After a loader switch, shops come up `UNVERIFIED` until the owner
re-confirms. An admin bulk command, `/stall admin rebind --all-unverified`, re-stamps every unverified shop
whose container still exists.

---

## 9. Deliverable C: Architecture proposal

### 9.1 Repository layout

```
Stall/
├─ settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/wrapper
├─ stall-core/                 codes.castled.stall          (Java 25; no org.bukkit / net.minecraft / net.fabricmc imports; build-checked)
│  ├─ model/                   Shop, Offer, OfferItem, ShopLocation, ItemTemplate, ShopState
│  ├─ trade/                   TradePlanner, SlotStack, ItemHandle, TradeResult, FailureReason
│  ├─ service/                 ShopService (create/edit/remove/reconcile), SessionRegistry, ReturnQueue
│  ├─ store/                   ShopCodec (Gson), AtomicFileStore, SchemaMigrator
│  ├─ limits/                  LimitResolver, Perm
│  ├─ gui/                     BuyerLayout, ManagerLayout, EditorLayout: slot maps and click semantics, no rendering
│  ├─ config/ messages/        schema and defaults; a language-file consistency test
│  └─ port/                    PlatformItems, ContainerAccess, MarkerStore, PermissionService, ProtectionHook,
│                              Scheduler, Messenger, Clock, Audit
├─ stall-paper/                codes.castled.stall.paper    (plugin.yml; paper-api compileOnly; optional GP softdepend)
├─ stall-fabric/               codes.castled.stall.fabric   (fabric.mod.json, stall.mixins.json, assets/stall/icon.png)
└─ docs/
```

### 9.2 Shared vs platform-specific

| Shared in `stall-core` | Platform-specific |
| --- | --- |
| Data model and JSON schema; validation; migrations | Event wiring and priorities or phases |
| Trade planner and failure reasons | Converting live inventories to `SlotStack` arrays and committing diffs |
| Shop lifecycle state machine (create, verify, orphan, return) | Marker read and write (PDC vs attachment) |
| Limit resolution, permission node table | Permission lookups (Bukkit vs Fabric permission API) |
| GUI layouts and click semantics (which slot means what) | Menu implementation (InventoryHolder views vs `ChestMenu` subclass) |
| Command grammar and handlers on neutral types | Brigadier node registration (different source-stack types) |
| Config and message schema | YAML loading, data-folder locations |
| Reconciliation rules | Chunk load hooks; explosion, hopper and merge guards (mixins on Fabric) |
| Audit log format | Item handles: equality, max stack, serialization (same bytes, different calls) |

### 9.3 Notes

- **Commands:** both platforms use Brigadier, but with different source types. The core defines actions such as
  `createToken(player, n)` and `remove(player, target)`; each adapter builds its Brigadier tree in about 100
  lines.
- **Visuals:** an optional `ShopVisual` port, defaulting to none. Possible implementations are a `TextDisplay`
  "awning" or an `ItemDisplay` showing the first reward. Entities are tagged `stall.display` and
  `stall.shop.<id>`, and reconciled on chunk load. They're cosmetic and allowed to differ between platforms.
- **Economy later:** a cost slot becomes a sealed `Cost` type: `ItemCost` now, `CurrencyCost` later (Vault on
  Paper, an economy API on Fabric). This fits in `extensions` and needs no schema break.
- **Packaging:** separate `Stall-Paper-<v>.jar` and `Stall-Fabric-<v>.jar` first. A universal jar (one file for
  `plugins/` and `mods/`, as GP3D and CrowBar ship) is an optional step in milestone 8 that reuses CrowBar's
  `universal-jar.gradle` approach.

---

## 10. Deliverable B: Feature feasibility matrix

| Feature | APIs involved | Shared? | Risks and open questions | Evidence |
| --- | --- | --- | --- | --- |
| Shop creation and placement | Paper: item PDC, `PlayerInteractEvent`, `TileState` PDC. Fabric: `DataComponents`, `UseBlockCallback` + phases, data attachments | Validation, limits, lifecycle in core; event wiring per platform | Interaction ordering against claims (P2); off-hand handling; PDC `update()` | APIs [V]; behavior [P] |
| Inventory linking (anchor = container) | Paper: `Container`, `DoubleChestInventory`, `Lootable`. Fabric: `BarrelBlockEntity`, `ChestBlock.getContainer`, `RandomizableContainer` | Link rules and reconciliation in core | Double-chest merge (chest milestone, mixin [W]); markers lost on loader switch (§8.5); remote linking deferred | [V] / [P] |
| Multiple offers | none (core) | fully shared | Menu capacity: 6 per page by default | [V] (design) |
| Two item slots per trade side | Item templates: Paper `serializeAsBytes`, Fabric codec | model shared; serialization same bytes | Same item in both slots (aggregated); template size limit | [V] / [P] (P5) |
| Buyer GUI | Paper: `createInventory` + holder, click, drag and close events. Fabric: `ChestMenu.*Rows`, `MenuType.GENERIC_9xN`, `openMenu`, `clicked` override | layouts and click semantics shared | **Fabric client-prediction desync / ghost items** (P1); 3 offers per barrel-sized page | [V] / [P] |
| Transaction processing | Paper: `getStorageContents`, `setItem`, `isSimilar`, `getMaxStackSize`. Fabric: `getNonEquipmentItems`, `Container.setItem/getMaxStackSize(ItemStack)`, `isSameItemSameComponents` | planner shared; commit per platform | `isSimilar` vs vanilla equality parity (P5); Folia unsupported | [V] / [P] |
| Permission-based shop caps | Paper: `hasPermission`. Fabric: `fabric-permission-api-v1`; LuckPerms implements it | `LimitResolver` shared | No provider on Fabric → defaults and op level; meta key format (P6) | [V] |
| Persistence and recovery | Gson (runtime-provided), atomic files; item bytes per A10 | codec and store shared | Paper 26.x world-folder location; byte parity (P5); crash divergence handled by markers | [V] / [P] |
| Fabric dedicated-server support | `environment: "*"`, no registry additions, server-side mixins only, `main` entrypoint | — | Mixin target drift across 26.x (linkage check before widening) | [I] / [P] (boot smoke) |
| Paper plugin support | `plugin.yml`, Brigadier lifecycle commands, standard events; no NMS | — | `api-version` string for 26.x (P7); Folia not targeted | [V] / [I] |
| Protection (GP) | Ordering (above); Paper GP API optional; Fabric needs a GP3D API addition for explicit checks | `ProtectionHook` in core | GP3D Fabric has no public trust query today | [V] |
| Shop-block protection | Paper events; Fabric: break event + 2–3 mixins | rules shared | Copper golem and hopper behavior details (P8) | [V] / [I] |

---

## 11. Deliverable E: Development roadmap

Each milestone ends with a checklist run on **both** a Fabric dedicated server and a Paper server, using a vanilla
26.3 client. Core logic carries unit tests from milestone 0.

| # | Milestone | Scope | Done when |
| --- | --- | --- | --- |
| 0 | Scaffold | Gradle (Java 25, Loom 1.18.x, Gradle 9.7.1), three modules, core boundary check, empty plugin and mod that boot, CI build | Both artifacts load on dedicated servers; the boundary check fails on a planted import |
| 1 | **Obtain and use a shop token** | `/stall token`; token item with its marker; detect token use on a barrel after protection; nothing created yet (log only) | Token recognized on both platforms; GP-claimed barrels are refused for untrusted players |
| 2 | **Create and persist an owned shop** | Registry, JSON store, block marker, limit counting with the config default, `/stall list`, `/stall info` | Shop survives restart; marker verified on interaction; corrupt file quarantined |
| 3 | **Link the inventory and protect it** | Lifecycle: owner break removes the shop and returns the token; non-owner break denied; explosion and hopper guards (Fabric mixins); reconciliation (orphan, unverified); pending returns and `/stall reclaim` | All theft and loss scenarios in the test script fail safely |
| 4 | **Configure one offer** | Manager menu, ghost-item editor for one offer, validation, persistence, revisions | Offer round-trips through a restart; no item can leave the editor |
| 5 | **Complete a buyer transaction** | Buyer menu (one offer), core planner, commit, failure messages, debounce, audit log | Property tests pass; manual edge cases pass: full inventories, unstackables, same item on both sides, two buyers |
| 6 | **Multiple offers and the full GUI** | Adaptive 9×1–9×6 rows, status arrows, live refresh across viewers, shift-click multi-buy, owner notifications, **single and double chests with the merge guard** | 6 offers on one page; concurrent viewers stay consistent |
| 7 | **Limits and protection integrations** | `stall.limit.<n>` tiers, unlimited, admin commands, optional GP hook on Paper (owner-trust check), GP3D Fabric trust API (in the GP3D repo), optional displays | LuckPerms tiers work identically on both platforms; trust revocation deactivates the shop |
| 8 | **Verify on dedicated Fabric and Paper** | Scripted boot smoke on dedicated servers (GP3D pattern), exploit checklist, vanilla-client checks, release packaging (separate jars, universal jar optional), README and wiki | A clean server of each kind runs the full scenario script with only a vanilla client |

Stall targets 26.3 only for now. If it widens later, that comes after milestone 8: add `fabricTargets` boot
smokes and a linkage check, and compile against the floor.

---

## 12. Deliverable F: Prototype recommendations

In priority order. Each is a throwaway spike, a few hours to a day.

| # | Question | Why it matters | How |
| --- | --- | --- | --- |
| **P1** | Can a **server-side `ChestMenu` subclass** on Fabric give vanilla clients a glitch-free view? Every click type (pickup, shift, number keys, offhand swap 40, double-click collect, drag, drop, creative clone) must be inert, with no ghost items after resync. Does the ghost-item editor keep the cursor item? | If this fails, the whole GUI design changes. Paper's equivalent is well trodden. | Fabric 26.3 dedicated server, vanilla client, a hard-coded barrel shop with one offer, planner from core. Repeat on Paper with an `InventoryHolder` view. |
| **P2** | Interaction ordering against GP: do buyers open shops inside untrusted claims, and are untrusted players refused shop creation? Does GP on Paper still message clicks Stall already cancelled? | Wrong ordering either breaks shopping in claims or lets claims be bypassed. | GP3D on both platforms; Stall listeners at LOWEST/HIGH (Paper) and in early/late phases (Fabric). |
| **P3** | The planner against real containers: double chests, `max_stack_size` items, unstackables, nearly full inventories, two clients buying at once, the owner holding the barrel open. | Correctness core. | Extends P1. |
| **P4** | Do block markers persist? A Fabric attachment on a barrel through a restart and chunk unload (is `setChanged()` needed?), Paper `TileState` PDC; behavior on break, WorldEdit copy and loader switch. | Identity binding (§3.1). | Small mod and plugin pair. |
| **P5** | Item portability and matching parity: Paper `serializeAsBytes` ↔ Fabric vanilla-codec bytes; `isSimilar` vs `isSameItemSameComponents` for enchanted books, potions, player heads, written books, damaged tools, shulkers with contents, and PDC items. | Shared data and exact matching. | Encode on one platform, decode on the other; compare equality tables. |
| **P6** | Fabric permission API with LuckPerms: `stall:limit/10` ↔ `stall.limit.10`; the integer node ↔ meta `stall:shop_limit`; the no-provider fallback to op level. | Rank caps on Fabric. | LuckPerms-Fabric 26.3 jar. |
| **P7** | Which `api-version` strings Paper 26.x accepts (`26.1`, `26.3`) and how it treats older ones. | Loading on the intended server range only. | Paper 26.3 server. |
| **P8** | Hoppers, hopper minecarts, copper golems and explosions against shop barrels and chests. | Theft vectors. | Part of milestone 3. |

**First prototype I'd build: P1, with P3 folded in.** A Fabric 26.3 dedicated server and a vanilla client: one
barrel, one hard-coded offer, a custom server-side `ChestMenu`, and the real `TradePlanner` from a minimal
`stall-core`. It retires the largest unknown, vanilla-client GUI robustness on the platform with no Bukkit-style
safety net, and proves the shared planner end to end. The planner code carries straight into milestone 5.

---

## 13. Risks

1. **Fabric server-side GUI robustness.** Client prediction, resync and click-type coverage. Mitigation: P1 and
   a custom menu that never calls `super.clicked`. Vanilla 26.3 already resyncs the client after every click
   [V], so an ignored click needs no extra packets.
2. **Ordering against claim plugins.** Buyers could be blocked, or creation could bypass claims. Mitigation:
   explicit phases and priorities (§7.3), P2, and the optional explicit GP hook.
3. **Container theft vectors.** Hoppers, golems, double-chest merges, explosions and unobserved removal.
   Mitigation: barrels first, 2–3 targeted mixins, and marker-verified trades.
4. **Item identity and serialization parity** between Paper and Fabric. Mitigation: one byte format (A10), EXACT
   matching only, and P5.
5. **Minecraft API churn within 26.x.** GP3D's README documents renames in 26.2 and 26.3, and the permission API
   was Experimental on 26.1.2. Mitigation: 26.3 only for now; linkage check and boot smoke before claiming more.
6. **Persistence diverging from the world after crashes or rollbacks.** Mitigation: data in the world folder,
   marker reconciliation, and states that never trade when unverified.
7. **Folia:** not supported. Atomicity relies on the main thread. Buyer and container are normally in one region,
   so support is plausible later but needs its own design pass. Leave `folia-supported` unset.

---

## 14. Decisions assumed in this plan

Each is a recommendation; say so if you'd rather go another way.

1. **Barrels only in the MVP**, with single and double chests in milestone 6.
2. **`max-offers` defaults to 6**: one page always, barrel-sized up to 3 offers.
3. **Tokens are free through `/stall token`** (permission `stall.token`), with limits enforced by shop count. A
   token cost or economy hook can come later.
4. **Owner right-click opens the stock**, and sneak + right-click opens the manager.
5. **Separate Paper and Fabric jars** first; a universal jar is optional in milestone 8.
6. **Shop data lives in the world save folder** (`<world>/stall/`) rather than a folder shared between loaders.
7. **Package root `codes.castled.stall`**, Maven group `codes.castled`, mod id `stall`, plugin name `Stall`.

---

## 15. Progress

### 2026-10-23: milestone 0 and prototype P1

**Done**

- **Scaffold:** Gradle 9.7.1, Loom 1.18.4, Java 25, and the three modules `stall-core`, `stall-paper` and
  `stall-fabric`. `checkCoreBoundary` keeps platform imports out of the core.
- **Core:** `TradePlanner`, `SlotInventory`, `Offer`/`OfferItem`, `Shop`/`ShopRegistry`/`ShopViewers`,
  `BuyerLayout` and `ClickThrottle`. 29 unit tests, including a 5,000-case randomized test that no trade
  creates or destroys items and that the buyer gets exactly the offer.
- **Prototype P1, with P3 folded in:** barrel shops set up with `/stall debug`, the buyer menu on both
  platforms, and real trades through the shared planner. Everything is in memory. The steps are in
  [TESTING.md](TESTING.md).

**Verified on dedicated servers** (boot, command registration, console permission check, clean shutdown)

- **Fabric:** Minecraft 26.3, Fabric Loader 0.19.3 (the declared minimum), Fabric API 0.162.0+26.3.
- **Paper:** 26.3 build 141 (beta), running Stall compiled against paper-api build 193 (stable).

**Answered**

- **P7, for 26.3:** Paper 26.3 accepts `api-version: '26.3'`.
- **Fabric click resync** is automatic (§6.5).

**Found on 26.3**

- `Items` no longer has fields for colored items such as dyes and stained glass panes, so they're looked up
  by id.
- `LevelReader.hasChunkAt` is deprecated; `Level.isLoaded` replaces it.

**Client testing** (first pass, vanilla 26.3 client)

- Menu layout, stock and affordability statuses, and every click type pass, except one Fabric bug: the
  swap-with-offhand key (F) left a ghost of the display item in the client's offhand.
  - The menu's slots don't include the offhand, so vanilla never corrected it. In creative the ghost became
    real, because the creative inventory lets the client create items.
  - Paper already resyncs the offhand for every menu. The Fabric menu now does the same.
- Shop safety checks pass: sneak behavior, break protection, menus closing.
- A first stocking attempt looked like a bug but wasn't: the offer's second reward wasn't in the barrel. The
  status pane now lists the missing items for out-of-stock and can't-afford.

**Second pass:** everything passed, including the F fix on Fabric and every trade check: full inventories,
a full barrel, double-clicking Buy, an offer over a stack, renamed and damaged payments, the owner's open
barrel view, and stock showing up within a second.

### 2026-10-24: the owner menu (milestone 4, early)

Asked for after the first round of testing.

**Done**

- **Owner menu:** sneak + right-click with an empty hand. One row per offer plus an empty row for a new
  one, and a remove button in each offer's first slot (Q, then Q again).
- **Offer editing:** offer slots hold copies of items and the owner's cursor keeps the real item, as
  §4.3 planned.
  - Left- or right-click with an item puts it in.
  - Left- or right-click and shift-click change the amount.
  - Q takes the item out.
- **Removal is safe from held keys:** holding Q never confirms a removal. Key repeat sends a press every
  tick or two, so presses close together count as one held press.
- **Core model:** offer sides keep each item in its slot (`OfferSlots`). Offers may be half set up while
  being edited, and buyers only see complete ones. `OfferEditor`, `RemoveConfirmation` and
  `ShopMenuLayout` (replacing `BuyerLayout`) live in core. 50 unit tests.
- **Platform menus:**
  - Fabric menus are now built on `AbstractContainerMenu` with display slots that refuse items. Vanilla
    can safely handle clicks in the owner's own inventory, and the display slots stay inert whatever code
    path asks.
  - Paper's owner menu cancels clicks on the menu, plus shift-clicks and double-click collects from the
    owner's inventory.
- `/stall debug create` replaces the hotbar-pattern `/stall debug offer`.

**Fixed along the way**

- The planner used the offer-slot type for its own totals (amount × repetitions, both slots added up), so
  the new per-slot cap would have made a large multi-trade throw instead of being refused. Totals now use
  their own uncapped type, `ItemAmount`.

**Verified:** builds clean, 50 core tests pass, and both servers boot and run commands.

**Client testing:** everything in the owner-menu checklist passed, including holding Q, every leak attempt,
and two people editing at once.

### 2026-10-24: tokens, commands, hopper protection, growing menus (milestone 1, parts of 3 and 6)

**Done**

- **Menus that grow:**
  - The owner menu has a row per offer plus one empty row (`ShopMenuLayout.ownerRows`).
  - The buyer menu has a row per complete offer.
  - When the needed size changes, the menu is reopened at the end of the tick, never inside the click
    that caused it (Paper: next tick), and the player's cursor item moves to the new menu.
  - On Fabric the old menu is closed without telling the client, so the client swaps screens in place
    rather than closing first, which would re-center the mouse.
- **Tokens:**
  - A glowing "Stall Token" paper. The marker is `stall:token` under `custom_data.PublicBukkitValues`,
    which is Paper's persistent-data layout, so each platform should recognize the other's tokens [P].
  - Using one on a barrel makes a shop and opens its owner menu. It runs after protection: Paper at
    `HIGH` and only if the click wasn't denied; Fabric in a phase after the default one.
  - Checks `stall.create`, the shop limit, and pending structure loot.
  - Creative players don't use up a token, and their shops give none back (`Shop.tokenConsumed`).
- **Getting tokens back:**
  - The owner breaking the shop drops the token with the barrel.
  - `/stall remove` gives it to the owner.
  - An offline owner's token waits in `TokenReturns` for `/stall reclaim`, with a notice on join. This is
    in memory until milestone 2.
- **Shop limits:** `stall.limit.unlimited`, else the highest `stall.limit.<n>` from 0 to 100, else 3
  (`ShopLimits`). Fabric checks `stall:limit.<n>`, which LuckPerms reads as `stall.limit.<n>`.
- **Commands:** `/stall`, `token`, `list [player]`, `info`, `remove`, `reclaim`, `preview` and
  `admin give`. These replace `/stall debug`.
- **Hopper protection:**
  - Paper cancels `InventoryMoveItemEvent` when the source is a shop barrel.
  - Fabric uses a mixin at the head of `HopperBlockEntity.suckInItems`, which serves hoppers and hopper
    minecarts alike, and checks the same block vanilla pulls from (`containing(x, y + 1, z)`, read from the
    26.3 bytecode).
  - Inserting into a shop still works.

**Verified**

- Builds clean, and 58 core tests pass.
- Both servers boot and answer every new command from the console.
- On Fabric, the server's mixin export shows `suckInItems` calling Stall's guard first and returning early
  when it refuses.

**Waiting on**

- The round-three checklist in [TESTING.md](TESTING.md).

**Client testing:** every token, token-return, command and hopper check passed. The "menus that grow"
checks weren't ticked, so they carry into round four.

### 2026-10-24: saving shops and block markers (milestone 2)

**Done**

- **Shared storage in core (`codes.castled.stall.store`):**
  - `ShopCodec` writes and reads the JSON in §8.1. An offer whose items can't be read is kept word for word
    under `preservedOffers` and retried on every load.
  - `FileStore` writes files on one background thread. Writes to the same file coalesce, newest wins. Each
    write goes to a temp file, `fsync`, then an atomic move.
  - `ShopPersistence` is the facade. It loads every shop and owed token at startup and saves on each change:
    - a corrupt or invalid file is moved to `shops/_broken`
    - a file from a newer format is left alone
    - a second shop at the same spot is moved aside
    - leftover `.tmp` files are ignored
- **Data folder:** `<world>/stall/` on both platforms, so world backups and rollbacks keep shops and barrels
  together.
  - Since 26.1, vanilla saves every dimension, the overworld included, under `dimensions/<ns>/<path>`
    (from the 26.3 bytecode), and Paper 26.3 adopted that layout.
  - Paper's `World#getWorldPath()` returns the overworld's dimension folder, so Stall walks back up to the
    save root.
- **Item codecs:**
  - Paper uses its own `serializeAsBytes` / `deserializeBytes`.
  - Fabric builds the identical format from vanilla parts: `ItemStack.CODEC` to NBT, plus
    `DataVersion`, gzipped. Reading runs `DataFixers` from the saved version, and refuses items from a
    newer version or from a mod that isn't installed.
- **Block markers:**
  - Paper keeps the shop id in the barrel's persistent data (snapshot, set, `update()`). Fabric uses a
    persistent data attachment, registered in the mod initializer so spawn chunks never load before the
    attachment type exists.
  - Markers are stamped when a shop is made and cleared when a shop is removed from a barrel left standing.
  - A shop trades only while its barrel carries its marker (`stillValid`). Otherwise it's paused: buyers
    find it shut, hoppers are still kept out, and the owner's sneak-click reactivates it. Reactivation runs
    after protection, at Paper `HIGH` and in the Fabric create phase, so a claim the owner lost trust in
    blocks it.
  - This closes the gap where a replacement barrel inherited a shop.

**Verified**

- **Tests:** 71 core tests (codec, store, and everything earlier), plus 7 Fabric codec tests that run the
  real codec against vanilla's registries.
  - Those tests bind item components the way a server does: `BuiltInRegistries.DATA_COMPONENT_INITIALIZERS`
    built against `VanillaRegistries.createWorldLookup()`.
  - They cover round trips with data components, Paper's byte structure, reading Paper-format bytes from an
    independent NBT writer, upgrading a pre-1.20.5 item through the data fixer, and refusing newer and
    unknown items.
- **Real servers:** both were seeded with three shops, a corrupt file and owed tokens.
  - Both loaded all three shops from `smoke-world/stall`, moved the corrupt file to `_broken`, and kept the
    missing-mod offer hidden but saved.
  - Both upgraded the legacy item.
  - Both left every file byte-for-byte unchanged.
  - **Paper read items encoded by the Fabric codec**, which proves the cross-platform direction for
    reading. Fabric reading Paper-written items is covered by the independent writer, which follows
    Paper's layout. [P] until a real Paper-saved shop is moved to Fabric.

**Client testing:** round four passed:
- the carried-over "menus that grow" checks
- saving through real restarts, item data included
- shop files appearing, changing and being deleted
- owed tokens across a restart
- reactivation, including GriefPrevention refusing it once trust was revoked
- a corrupt file moved aside
- a real move between Paper and Fabric: shops loaded paused, and the owner reactivated them

One check didn't run: replacing a barrel with a single `/setblock`, because vanilla refuses to set a block
identical to the one already there. It's corrected in round five.

### 2026-10-24: one stack per offer slot

Found in round four: shift-clicking an offer slot past 64 went on to 65 and kept growing, up to the 3,456
sanity limit.

- **Fix:** `OfferEditor` keeps each slot to one stack of its own item: 64 of most items, 16 ender pearls,
  one sword. So a side asks for at most two stacks.
- **When a slot is full:** increasing it carries on in the side's other slot, if that slot is empty or holds
  the same item with room; otherwise the click changes nothing. Filling a slot never spills: shift-clicking
  10 gives 64, and the next increase goes to the other slot.
- **Older shops:** `OfferItem.MAX_AMOUNT` stays as a loading sanity limit, so shops saved before the fix
  still load and trade. Editing an over-stack slot brings it back to a stack.
- **Tests:** 75 core tests pass, with new cases for carrying over, the two-stack limit, a different item in
  the other slot, and low-stacking items.

**Waiting on**

- The round-five checklist in [TESTING.md](TESTING.md).

**Next**

- The rest of milestone 3: explosion protection, and reconciliation for barrels removed without a player
  breaking them.


### 2026-10-24: Spigot support

- One `Stall-Paper-<version>.jar` now runs on Spigot, Paper and Purpur 26.3. The module and artifact
  names stay unchanged for existing downloads and release automation.
- The plugin compiles against `spigot-api:26.3-R0.1-SNAPSHOT` instead of Paper API. Commands use
  `plugin.yml`, `CommandExecutor` and `TabCompleter`. Text, inventory, world lookup, marker snapshots,
  loot-table checks and the tick clock use the common Bukkit API. No client changes or new runtime
  dependencies are required.
- **Native-code exception:** Spigot lacks Paper's binary item serialization API. `PaperItemCodec` binds
  the unobfuscated 26.3 vanilla item codec, registry operations, NBT compression and data fixer through
  reflection, with a small CraftItemStack conversion bridge. It binds before loading shops and fails
  enable if the server is incompatible. The saved schema and item bytes remain compatible with Fabric
  and Paper, including legacy upgrades and exact metadata matching. This must be verified again when
  adding a Minecraft version.
- Differential server testing caught Spigot's legacy text setters adding explicit false formatting
  flags that Paper omits. Tokens now use the same component input on both servers, and match Fabric's
  token components exactly. The real Spigot fixtures are retained in the Fabric regression tests.
- **Verified on real servers:** Spigot 26.3 built with official BuildTools (Spigot `bed90fca`, Bukkit
  `29025a7e`, CraftBukkit `c4db4c38`) and Paper 26.3 build 193. Both enable the same plugin and pass the
  optional server checks: item fidelity/upgrades, cross-server item and token parity, commands,
  menu rendering/click cancellation, two-for-two trades, repeated/failed purchases, markers,
  hopper input/extraction and break protection. These checks simulate player calls against real
  Bukkit inventories, so vanilla-client packet behavior still needs the retest in `TESTING.md`.
- Release metadata and store pages now include Spigot. Folia remains unsupported.

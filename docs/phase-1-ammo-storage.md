# Phase 1: Isolated Guard Ammo Storage

Phase 1 removes the addon's nine-slot replacement of Guard Villagers' inventory. A guard again owns the upstream six slots (four armor, offhand, main hand), while the addon owns a separate three-slot Forge item handler for TaCZ ammunition.

## Storage and capability layout

| Combined capability slot | Owner | Meaning |
| --- | --- | --- |
| 0-3 | Guard Villagers | armor |
| 4 | Guard Villagers | offhand |
| 5 | Guard Villagers | main hand |
| 6-8 | Villager Guard Gunners | TaCZ ammo or ammo box |

The combined `ForgeCapabilities.ITEM_HANDLER` preserves Guard Villagers' original indices and appends the ammo handler. TaCZ can therefore discover and consume ammunition through its normal living-entity inventory path without a second inventory scan or a custom ammo capability.

The ammo handler accepts items implementing either TaCZ `IAmmo` or `IAmmoBox`. It does not require the item registry namespace to be `tacz`, allowing compatible gun packs to provide their own ammunition items.

## Persistence and migration

The three slots serialize under `villagerguardgunners:ammo_inventory`. Items that cannot fit or are invalid are queued under `villagerguardgunners:ammo_overflow` and dropped on the server instead of being silently discarded.

Old saves stored ammo in Guard Villagers' `Inventory` entries 6-8. Before Guard Villagers reads that list, Phase 1 removes every entry at index 6 or above and:

- imports it into the new handler when no new-format ammo tag exists;
- when both formats exist, restores the new-format inventory and merges only unmatched legacy quantities, preventing stale rollback copies from duplicating while preserving legacy-only ammunition;
- queues rejected or excess items for a recoverable world drop;
- retains slots 0-5 unchanged for Guard Villagers.

The overflow queue is itself serialized until the items can be dropped, covering a save or shutdown between migration and the guard's first server tick.

## Menu behavior

The custom menu contains six upstream guard slots, three `SlotItemHandler` ammo slots, 27 player-inventory slots, and nine hotbar slots. Quick-move boundaries use these explicit counts rather than `guardInventory.getContainerSize()`.

The menu now intercepts `Guard.openGui`, which is reached after Guard Villagers performs its reputation, owner, current-target, secondary-use, and server-side authorization checks. The previous unconditional `mobInteract` interception has been removed. Follow and patrol controls remain available and no longer overlap one another.

## Phase boundary

The legacy combat/reload code remains active until the native TaCZ controller replaces it in Phase 2. Its ammo lookup now uses the isolated handler and understands both loose ammo and ammo boxes. The custom shooting implementation itself is intentionally not expanded in this phase.

## Verification

- Plain JUnit covers the three-slot/type contract and legacy NBT slot extraction without starting Minecraft.
- The reobfuscated Forge build verifies the production jar and mixin refmap.
- `runGameTestServer` creates real transformed guards and verifies the three-slot handler, nine-slot combined capability, isolated save/load, mixed-format rollback deduplication, and 45-slot menu layout.

Forge/Minecraft cannot fully initialize `ItemStack` in an untransformed plain-JUnit worker. Item serialization and entity/capability behavior therefore remain in Forge GameTests instead of being represented by misleading plain-JUnit mocks.

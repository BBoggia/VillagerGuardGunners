# Quartermaster station

The craftable Quartermaster station stores supplies for manual and automatic guard service.
Craft it with two iron ingots, a book, a chest, and five planks. Place the iron
and book across the top row, with the chest between planks in the middle row
and three planks along the bottom.

Right-click the station to open its 27 stock slots and nine return slots.
Both sections accept ordinary items, including TaCZ guns and ammo, armor,
and medical supplies. Quartermaster stations cannot be stored inside another
station. Shulker boxes and other items that prohibit container nesting are also
excluded; stations cannot be placed inside shulker boxes or bundles. Shift-clicking a player item moves it into stock; place returned items
in the return row manually. Shift-clicking either station section moves items
back to your inventory.

To equip a guard manually, withdraw supplies and use the guard's inventory.
An adult villager can also claim the station as a Quartermaster job site and
serve guards that have opted into equipment service. See
[Quartermaster service](quartermaster-service.md) for policies, profiles, and
linked Village Armories. Stations work outside villages and do not require
Village Defense Buildings. Equipment service stocks medical supplies but does
not treat injuries.

## Access

The player who places a newly crafted station becomes its owner. The owner and
server operators with permission level 2 can change its settings and break it.
The access button cycles through three inventory policies:

| Policy | Players who can use stock and returns |
| --- | --- |
| Private, the default | Owner and server operators |
| Squad | Owner, server operators, and the selected squad's creator |
| Public | All living, non-spectator players |

The squad registry currently records a creator, rather than a list of player
members. Squad access therefore grants inventory use to that creator alone.
A generated squad with no creator grants no additional player access. Holding
or copying a Squad Command Ledger does not grant access to private storage.

To select a squad, the owner holds a bound Squad Command Ledger in either hand
and selects **Bind**. The squad must still exist in the registry. **Unbind**
removes that selection. In Squad mode, an absent, removed, or unreadable squad
record grants no extra access. Inventory users cannot change settings or break
the station unless they also have management permission.

Permission changes apply to menus that are already open. Walking more than
eight blocks from the station, changing dimensions, or removing the station
also ends access. Inventory contents are sent only to players opening its menu;
they are not included in general block-entity update packets.

## Hoppers and pipes

Automation is off by default and is independent of player access. The owner can
enable it with **Auto: off**. All faces then expose the same transport rules:

- Insert into stock slots only.
- Extract from return slots only.
- Never extract from stock or insert into returns.

Enabling automation allows connected hoppers and pipes to move those items,
including when the station uses Private access. Turning it off also blocks
handlers that a pipe has already cached. Simulation calls do not consume items,
and inventory inspection through the capability returns copies.

## Moving and recovering a station

Breaking a station immediately attempts to drop one packed station item containing its stock,
returns, owner, squad selection, and access settings. Place that item to restore
them. Passing a packed station to another player does not transfer ownership.
The station blocks piston movement and has high blast resistance. Creative
removal also preserves the packed station rather than destroying its contents.

If another mod cancels the packed item's spawn, the server retains it in saved
recovery data and retries at loaded, ticking locations. It checks at most two
pending removals per dimension every five seconds, without loading chunks.
A large backlog can take longer. Each accepted spawn clears its pending entry.
After a successful drop, normal dropped-item pickup and despawn rules apply.

Unsupported or malformed station data stays preserved inside the packed item;
it does not silently become empty storage. An unreadable station denies inventory
and automation access. A server operator can break and retain it for recovery
with a compatible version or external save repair.

## Integration contract

The block and item IDs are `villagerguardgunners:quartermaster`. The block entity
has no ticker. Its `Quartermaster` NBT compound is versioned independently of
Village Armory data and stores a fixed 36-slot Forge item-handler inventory.
Slots 0–26 are stock; slots 27–35 are returns. A stable station UUID survives
relocation; a separate removal UUID changes on each placement. Valid ItemStack payloads retain
native TaCZ state and custom NBT through transfers and packing. Items that cannot
round-trip on load cause the complete station payload to be preserved and locked.

The recovery outbox uses per-dimension SavedData under
`villagerguardgunners_quartermaster_recovery`. Malformed pending entries remain
quarantined, and unsupported versions remain preserved. Recovery uses stable
item-entity UUIDs to acknowledge an already-present drop. A still-present source
station retains ownership instead of generating a second packed item.

These are ordinary Minecraft save boundaries: block entities, SavedData, and
item entities are saved separately. They do not provide an atomic disk commit
across a process crash. Back up a world before external save repair; do not copy
individual pending records or packed-item NBT into multiple active locations.

Village Armories remain ammo-only and retain their squad supply restrictions.
Equipment service adds an ownership receipt to participating inventories;
ambiguous receipts suspend access until recovery. See the service documentation
for save boundaries and operator commands.

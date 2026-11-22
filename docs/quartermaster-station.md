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

## Quartermaster overview

For a first setup, let an unemployed adult villager claim the station. Buy supplies
through **Trade**, then deposit the purchased items through **Open Stock**. Purchases
stay in your inventory until you deposit them. Use **Manage** to choose guards,
select **Restock Current** or **Maintain Loadout**, and set **Station: This station**.
Preview and apply the settings, then check **Needs** for remaining shortages.
Restock Current needs an equipped gun and an existing medical supply in the pouch
to know what to replenish; Maintain Loadout uses its selected profile instead.

Choose **Overview** in the station screen, or right-click a nearby adult
Quartermaster with both hands empty. The villager must still claim a loaded
Quartermaster station. Holding an item or sneaking keeps the normal villager
interaction available, including name tags and interactions added by other mods.

The overview has four views:

- **Stock** lists viewable equipment, ammo types and medical supplies. Counts show
  available / total; hover a row for reserved amounts and equipment details.
- **Needs** shows requested / available / incoming / missing supplies for assigned
  guards and current Auto visitors. Select a shortage to see affected guards. Conditional or
  unavailable needs are marked **Unknown**. Incoming supplies are reserved for an
  active delivery and have not arrived yet; they reduce the missing amount.
- **Guards** separates assigned guards, current visitors and nearby candidates.
  Hover a guard for its group, policy and loadout. Nearby candidates do not add
  demand or enroll themselves in service. **All guards** clears a shortage filter.
- **Service** shows recent completed deliveries to visible guards. This short
  history resets when the server stops.

Scroll within a page and use the arrow buttons for more pages. **Refresh** requests
an updated estimate; the screen also refreshes automatically. The footer shows the
report's age, checked/unavailable guard counts and whether the report is limited.
A limited report can omit guards or linked stock. Linked Village Armories must be
loaded, readable and within eight blocks of you to show their supplies.

Unloaded guards remain unavailable with last-seen information. In **Guards**, a
station manager can select **Forget record** on an unavailable guard's row to remove
that last-seen entry. This leaves the guard's saved settings unchanged; its record
returns if it comes back with the assignment intact. Loaded guards cannot be forgotten,
including guards loaded in another dimension.

Missing stock,
protected equipment, full return storage and unknown gun definitions have separate
explanations. Estimates do not reserve supplies or guarantee a service trip.
**Open Stock** returns to the station inventory and requires normal permission and
the eight-block range.

**Trade** opens the villager's ordinary merchant screen. Quartermasters sell
[bandages, ammo and equipment](quartermaster-trades.md), with optional gun sales
disabled by default. Keep both hands empty and stop sneaking to use it. The villager must be
within four blocks, visible, awake, and free to trade. A villager responding to
danger or already trading with another player cannot start another trade. If it
has no offers, a message explains that. Prices, XP and restocking follow normal
villager trading; purchases go to you for manual deposit into station stock.

Visitors can reach Trade through the villager without seeing private supplies or
guard management. Browsing leaves the Quartermaster free to work and move. Walking
out of range, losing permission, replacing the station, or losing the villager's
job-site association closes the affected overview. Trading pauses equipment service
until the merchant interaction ends.

## Manage guard equipment

Open **Manage** from the overview. You need permission to manage both the station
and each selected guard. Select guards individually, or use a guard's **Squad**
button to replace the selection with its loaded members within 48 blocks.
Browse all eligible loaded guards with the page arrows, then apply changes in
batches of up to 64. The screen counts excluded loaded squad
members. Guards assigned to another station are excluded. Unloaded guards and
members who join later are not added to the selection.

Choose each setting separately:

- **Policy:** Keep, Manual, Restock Current, or Maintain Loadout.
- **Loadout:** Keep or one of the available equipment profiles. Hover for the full
  profile ID. Maintain Loadout requires a valid profile.
- **Replace:** Keep, No, or Yes. Keep preserves each guard's existing permission.
  Hover to see the current selection, including Mixed permissions. Yes permits
  replacing named equipment; Binding Curse and items that cannot enter returns
  remain protected.
- **Station:** Keep, Auto, or This station. Manual can retain an assignment for
  later use, including after that station revokes service access; Auto lets normal
  service choose a nearby station. Reenabling service still requires valid access.

Choose **Preview**, then inspect **Changes** and **Needs**. Hover rows for guard IDs,
before/after settings, service delays, supply amounts, and protected equipment.
Use the arrows for more pages. Estimates share the available stock across selected
guards and reserve nothing. Missing supplies do not prevent saving the settings.
Large estimates finish over several ticks and show **Updating supply estimate…**
while pending. You can apply valid settings before the supply estimate finishes.

Choose **Apply** to save the batch. A preview lasts 30 seconds. Changes to a selected
guard's settings, squad membership, permissions, availability, or loadout definitions
require a fresh preview. Stock changes update the estimate without canceling it.
The result shows how many guards were updated; hover guard rows for current service
waiting conditions. Guards collect equipment during normal service when supplies,
work hours, and their duties allow it. Their movement orders remain unchanged.

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
enable it with **Pipes: Off**. All faces then expose the same transport rules:

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

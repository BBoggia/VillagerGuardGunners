# Quartermaster equipment service

Place a [Quartermaster station](quartermaster-station.md), stock it, and let an
adult unemployed villager claim it as a job site. One villager can claim each
station. A living Quartermaster works near the station between daytime ticks
2000 and 9000. Service pauses during raids, trading, sleep, operator absence,
profession loss, or station unload. No Village Defense Buildings are required.

Open a guard's inventory to choose its equipment policy:

| Policy | Behavior |
| --- | --- |
| Manual, the default | No Quartermaster trips or equipment changes |
| Restock current | Add loose ammo for the current primary and configured sidearm; top up medical supplies already present in the pouch |
| Maintain loadout | Fill equipment slots and replenish supplies accepted by the selected profile |

Selecting a behavior role alone never opts a guard into service. When first
selecting Loadout, a matching role association can suggest a profile;
otherwise the first profile by ID is selected. The Loadout button cycles through
available profiles. Hover it to see the full ID.

Existing equipment, including named items, stays protected by default. Enable
replacement explicitly to allow a profile to replace it. Binding Curse items
remain protected. An accepted gun stays equipped even if another accepted gun
is in stock. Missing replacements or full return storage leave old gear in
place. The Quartermaster never repairs guns, creates ammo, changes attachments,
refills magazines, or rewrites item tags.

Guards visit one station and receive one slot transfer at a time. Combat,
player follow/hold/patrol orders, saved squad patrol routes, and native gun
actions take priority. After a completed trip they release their reservation
and can resume ordinary duties. The status below the guard inventory explains
waiting conditions and shortages. A completely empty medical pouch in Restock
current has no supply type to infer; seed it manually or select a profile.

The inventory calls equipment-profile service **Loadout**. This is separate
from a guard’s tactical profile. **Pipes** on the station controls hopper and
pipe access, independently of Equipment Service.

## Station settings

Guard service permissions are separate from player inventory access and hopper
access. The guard permission button cycles through Off, Owner's guards,
Bound squad, and All opted-in guards. Owner's guards is the default. Only the
station owner or an operator can change settings.

**Link Armories** selects up to four nearby loaded Village Armories within
16 blocks. **Clear links** removes them. Links use their saved block positions;
a replacement Armory at the same position is eligible under its current squad
lock. Links grant no bypass of those locks. Linked Armories supply loose ammo;
guns, armor, and medical supplies come from the station's stock. Existing
Village Armory resupply still handles ammo boxes independently.

A guard searches for loaded stations within 48 blocks. Stock remains available
for authorized manual use while a guard travels; changing it cancels a stale
reservation. A station with missing or malformed service settings cannot serve
guards. Relocating a packed station retains its saved links, but links beyond 16 blocks
remain inactive. Use Link Armories again to choose supplies at the new location.

## Pack-author profiles

Put JSON files under `data/<namespace>/quartermaster_loadouts/<name>.json`.
The built-in `villagerguardgunners:rifleman` profile is an example:

```json
{
  "format": 1,
  "primary": ["tacz:ak47", "tacz:m4a1"],
  "sidearm": ["tacz:glock_17"],
  "armor": {"head": ["minecraft:iron_helmet"]},
  "ammo_reserve": 128,
  "medical": ["villagerguardgunners:bandage"],
  "medical_reserve": 16,
  "roles": ["villagerguardgunners:generalist"]
}
```

Primary and sidearm IDs identify TaCZ guns. Armor and medical IDs identify
Minecraft items. A `#namespace:tag` selector uses a Minecraft **item tag**, even
in a gun list; it is not a TaCZ gun-pack tag. Roles require exact role IDs.
Unknown or unavailable selectors match no valid stock. Item eligibility is
checked against the current native gun definitions and destination slot when
planning a transfer. Medical items must also resolve to a treatment profile.

Supported armor keys are `head`, `chest`, `legs`, and `feet`. Each selector list
accepts at most 16 unique canonical IDs, each at most 96 characters. Ammo
reserves range from 0 to 512 per compatible ammo type; medical reserves range
from 0 to 192 across accepted medical items. Actual transfers respect available
slot capacity. Omitted reserves default to 64 ammo and 16 medical supplies.
Omitted equipment lists leave those slots alone. Zero reserves request no top-up.
There are at most 128 profiles. Invalid format, fields, bounds, or syntax reject
the complete reload and retain the previous registry. An accepted reload
invalidates outstanding reservations.

## Server integration and recovery

The loaded-only scheduler inspects at most 16 guards and 16 villagers per
server dimension per second, with up to eight reservations and one durable
transfer in that interval. Each index accepts at most 4096 actors or blocks.
Search examines at most 32 indexed blocks; dense placements and large actor
counts can delay service. No station has a block ticker, and service does not
force-load chunks. Navigation retries are bounded; an unloaded path region
pauses the trip. Reservations expire after 600 ticks, with a 100-tick retry
interval after completion or cancellation.

Before moving items, service rechecks the operator, permissions, profile
revision, source fingerprint, guard equipment, return capacity, and native gun
state. A synchronous transfer moves physical items and stores revision receipts
on both owners. Its write-ahead journal is
`<world>/data/villagerguardgunners_equipment_service.dat`. The journal contains
ownership watermarks and an operation ID, never item copies to replay. It uses
a forced temporary-file write followed by atomic replacement and supports up
to 32768 recorded owners. A full or unreadable journal stops new transfers.

Minecraft saves entities, block entities, and dropped items separately. This is
not an atomic disk save across those owners. A mismatched receipt is
quarantined rather than automatically issuing another item. Guard gun use,
menus, medical access, and affected inventory transport pause. Existing
third-party references to mutable stacks and external save editing are outside
this boundary. Back up the world and reconcile physical items before recovery.

Permission level 2 operators can use:

```text
/vgg equipment-service inspect guard <entity>
/vgg equipment-service inspect block <x> <y> <z>
/vgg equipment-service recover-empty guard <entity>
/vgg equipment-service recover-empty block <x> <y> <z>
```

Inspection reports saved and expected ownership revisions. Recovery only
acknowledges a **physically empty** loaded owner with a known receipt and a
readable journal; it never deletes, creates, or replays items. Reconcile items
with a compatible backup or external save repair first. Guard recovery returns
its policy to Manual. Unknown payloads and an unreadable journal remain
preserved and require compatible-version or external repair. Do not remove the
journal or copy one owner's receipt to another inventory to bypass recovery.

## Villager AI compatibility

Service requires a live adult Quartermaster with matching job-site memory and
an occupied Quartermaster POI ticket at the station. The operator UUID alone
does not authorize service. A released ticket stops service even if the villager
still remembers the station. Ticket inspection uses the already cached POI
section and does not load a missing section. Native POIs store ticket counts,
not owner identities; profession, entity presence, distance and job memory are
checked separately on the live villager.

The station's 2000–9000 service window remains authoritative. Service does not
require the brain's `WORK` activity, so a modified schedule alone does not
override that window; trading, sleeping, panic, raids and other existing gates
still prevent service. Settings that prevent job acquisition do not cause the
addon to create a replacement operator or assign a profession.

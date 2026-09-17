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

## Station assignments

Players can choose **Auto** or **This station** through the overview’s
[Manage screen](quartermaster-station.md#manage-guard-equipment). Integrations use
`ServiceControls.applyAtStation`; bounded settings batches use
`ServiceControls.applyBatch`. Existing inventory buttons change only policy,
profile, and replacement permission, preserving any assignment.

Auto retains nearby station discovery. An explicit assignment selects the saved
station UUID and dimension, within the existing 48-block service radius. Its saved
position is only a hint. A loaded relocated station in the same dimension retains
its identity; another station placed at its former position is not a replacement.
Missing, out-of-range, cross-dimension, or duplicate loaded identities stop assigned
service with a status message. They do not trigger fallback to another station.
Direct Village Armory ammo resupply remains independent of this assignment.

Guard service data version 2 reads version-1 records as Auto while preserving their
policy, profile, replacement setting, and ownership receipt. Manual mode retains
an assignment without making trips. Unknown or malformed records stay preserved
and cannot be edited through service controls.

Station requests carry an immutable settings value and a context identifying the
station UUID, current placement UUID, dimension, and position. Each action rechecks
the live station, the player's normal eight-block station access, station management,
the guard's existing owner/reputation/Hero permission, the guard's 48-block service
radius, service access, readable data, and active item-transfer locks. Work hours
and a working operator are required for transfers, not for configuring a station.
Disabling service remains possible after service access is revoked, either clearing
the assignment or retaining the unchanged assignment for later. Manual mode can
also retain an unresolved assignment; it grants no new station access. New assignments
and reenabling service still require the normal destination and service checks.
Changing settings invalidates existing reservations, even if those
settings are subsequently changed back. Settings requests move no items.

A server-wide roster stores at most 4096 assigned guards' last-seen identities,
names, dimensions, and positions. It contains no equipment copies or deferred
settings changes. Live guard settings are authoritative. Unloading keeps the last
observation; known death/removal deletes it. A station manager can select **Forget
record** in the overview's Guards view to remove an unavailable entry, but cannot
use that action on a guard loaded in any dimension. If that guard later
returns with its assignment intact, observation restores the entry. A full roster
does not evict another guard; assignments beyond its capacity can still function
without a retained roster entry. Unreadable roster data is preserved and does not
replace live guard settings.

Identity lookup uses loaded station indexes and never loads chunks. If the existing
4096-block index limit is exceeded, explicit identity resolution stops conservatively
until a server restart can build a complete index. Auto discovery retains its
existing limits. `/vgg equipment-service inspect guard <entity>` includes the saved
assignment for readable guards.

## Read-only assessments for integrations

`ServiceAssessment.preview` accepts the station management context and up to 64
guard requests on the server thread. Each request contains a guard UUID and optional
proposed `ServiceSettings`; null settings inspect the current policy. It requires
station management and the existing guard-management permission. Only loaded,
living guards in the station's dimension and 48-block area are inspected. Proposed
settings are evaluated without applying them. The [overview](quartermaster-management.md)
uses the same copied-input assessment in incremental batches for its Stock and Needs
views and bulk settings estimates. Those menus share a per-dimension work budget.
The direct integration API remains synchronous; callers must budget their own usage.

Reports contain the observation game tick, equipment-profile revision, requested
and inspected counts, unavailable count, truncation, source restrictions, compact
stock totals, and per-guard needs. Inspected means an authorized loaded guard was
found; an unreadable guard is also counted as unavailable and has no equipment
details. Unauthorized, missing, duplicate, and out-of-range requests count as
unavailable within the first 64 requests. Requests beyond that limit are truncated,
not inspected. No offline inventory or chunk loading is used.

Stock rows identify the item, gun or ammo ID where readable, category, physical
count, and reserved count. Ammo boxes are a separate category and cannot satisfy
loose-ammo requests. Reports contain no item NBT, native inventory handlers, or
transfer plans. Current Armory inventory viewing is public; its squad lock limits
guard supply. Unreadable or recovery-locked sources are omitted. Missing loaded
links and guard supply restrictions mark the report as restricted or incomplete.

The evaluator and live planner share these rules:

- Existing accepted equipment stays in place. Protected equipment has no
  deliverable replacement shortage. Binding Curse and invalid return items remain
  protected even with replacement enabled.
- Replacement requires a valid stock item and one free return slot when old gear
  is present. Missing stock, unavailable definitions, incompatible stock, full guard
  slots, and full return storage have separate reasons.
- A valid proposed replacement determines projected ammo needs. When replacement
  is unresolved, future ammo is marked conditional; the equipped gun's known
  reserve can still be assessed.
- Primary and configured sidearm share one reserve for a common caliber. Native
  compatibility checks receive item copies. A shared reserve must be usable by
  both guns. Dummy and native infinite reserves request no physical loose ammo.
- Item tags must match for stacks to merge. Different variants can use empty
  slots but cannot overwrite existing supplies. Profile medical reserves are
  shared across accepted treatments. Restock with an empty pouch invents no type.
- Physical stock sources are deduplicated. Existing reservations exclude their
  entire claimed source slot or return slot, matching the runtime reservation model.
  Current-policy reports list a guard's valid pending delivery separately as incoming,
  without releasing its source stock for another guard. Changed proposed settings
  do not reuse those incoming quantities. Invalid, expired, hidden or canceled
  deliveries provide no incoming coverage. Reporting compares receipts without
  quarantining them and never applies a projected transfer.
  The requested guards are estimated in UUID order against shared stock and returns;
  one stack or return slot cannot be promised twice within that report.

`ServiceNeeds` evaluates immutable scalar facts without native objects or callbacks.
The native adapter copies inventory stacks before reading gun definitions, item
tags, or native validation. `ServiceJournal.matches` compares receipts without
quarantining or writing; actual transfers still use journal verification. Preview
does not change inventory, settings, status, navigation, reservations, or receipts.

These are estimates of physical supplies, not reservations or promises of immediate
service. No work-hour, combat, route, or travel success is implied. Independent
previews can share an Armory; do not add them together as global availability. The
runtime retains its scheduling order and performs one revalidated physical transfer
at a time. Integrations must recheck authority and settings before applying a request;
an assessment is not permission to change settings or move items.

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

See [Quartermaster compatibility](quartermaster-compatibility.md) for tested
installations and the interaction, trading, and transport boundaries.

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

The Quartermaster POI is included in Minecraft's
`minecraft:acquirable_job_site` tag. Pack authors replacing that tag must retain
`villagerguardgunners:quartermaster` if unemployed villagers should discover the
station. A registered POI and profession alone are insufficient for an unemployed
villager's normal search. Nitwits, babies, and villagers already committed to another
profession do not qualify as new operators.

## Interruptions and troubleshooting

Changing service settings through a guard's inventory preserves its station
assignment and cancels an outdated delivery. Changing squad membership or guard
permissions requires a fresh bulk preview. Moving a packed station invalidates
open menus; assigned guards find its retained identity only while the new location
is loaded, in the same dimension, and within 48 blocks.

Trading, panic, raid activity, and medical treatment pause the affected service.
Losing a job site or its operator pauses the station; another unemployed adult can
claim the unoccupied station normally. Guard settings and deposited stock remain.
An active hopper or pipe can change stock after a preview or reservation. Service
checks the physical stack again before delivery; the overview remains an estimate.

Large groups take time to supply. The limit of one completed transfer per dimension
per second is a maximum; travel, reserved stock and retries can make service slower.
Adding stations in the same dimension does not multiply that limit. Several stations
sharing one Armory also compete for its available stacks. A large queue can require
more than one working period to finish.

If Needs shows missing stock while ammo is visible, check the ammo ID for both
the primary gun and configured sidearm, linked Armory squad locks, and your distance
from each Armory. The overview excludes linked stock more than eight blocks from
you, even when a guard can collect it. A limited report or unavailable guard does
not prove every guard is supplied. Recovery-required messages need operator
inspection using the recovery commands above, rather than repeated service requests.

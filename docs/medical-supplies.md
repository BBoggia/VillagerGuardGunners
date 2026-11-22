# Medical supplies and medic guards

Every guard has a three-slot medical pouch, separate from ammo and sidearm
storage. It remains available when equipment or roles change. A Quartermaster
station or Village Defense Building is not required.

## Medic duty and retreat

Open a guard's inventory and select **Medic: On** to let it treat nearby injured
allied guards using its own pouch. Medic duty starts off for every guard.
It does not turn the support combat profile into a medic. Enabling duty assigns
the medic role only when the guard has no role already; changing roles or turning
duty off keeps its pouch contents.

Stock bandages or supported healing potions. A medic chooses an injured ally,
approaches it, and needs two uninterrupted seconds close to the patient with a
clear view. More urgent injuries take priority among the candidates checked.
Each patient can have one medic at a time. Damage, movement during treatment,
combat, native hand actions, or player orders can interrupt care. Canceled or
unneeded healing spends nothing. Existing food, regeneration, and villager
healing remain available; medical treatment does not repair armor.

**Retreat: On** independently lets a living guard seek a supplied nearby medic
at 35% health or less. The guard stays in recovery until it reaches 75% health.
A medic must be safely away from the guard's current target. Retreat pauses new
gunfire and waits for an active native gun action to finish before moving.
Unavailable or unreachable destinations cause a bounded retry. Guards do not
teleport or load chunks to obtain treatment.

Following, holding, player patrols, squad commands, and an open guard interaction
take priority over automatic medical movement. After care, guards become eligible
for their existing automatic duties again. Retreat is off by default for each
guard. There is no downed state, revival, or casualty transport in this feature.

For automatic supplies, configure the existing [Quartermaster service](quartermaster-service.md).
The medic equipment profile requests bandages alongside a compatible gun and
ammo. Service policy and station permissions still need to be enabled; Medic
alone does not authorize equipment replacement or create supplies.

## Server bounds and persistence

`medical.enabled` controls all treatment. `medical.medicAi` controls automatic
care, and `medical.retreat` allows opted-in medical retreat. All three server
switches default to true; per-guard Medic and Retreat choices default to off.
Disabling AI cancels care without spending a pending dose. Choices and pouch
contents persist, while treatment leases and movement restart after loading.
Unknown medic-state records are preserved and disable automatic medical duty.

The loaded index holds at most 4,096 guards per dimension and repairs 32 records
every ten server ticks. A medic inspects at most 64 actors across 25 chunk cells
per search and considers patients within 32 blocks. Each medic resumes its own
cell and actor search progress, so crowded cells cannot permanently hide later
patients. Search progress is transient and covers only the current search area.
There are at most 16 concurrent treatment leases per dimension. A retreat attempt lasts at most 240 ticks. A treatment lease allows 240 ticks
for approach plus the selected treatment channel (up to 600 ticks). Dense populations can delay discovery
and care. Navigation checks loaded chunks before requesting paths, retries paths
at most once per second, and abandons failed or stalled approaches. A medic
skips its most recently unreachable patient for ten seconds so it can try another ally. Medic
pathfinding declines custom guard follow ranges above 64 blocks to keep native
path searches bounded.

## Player controls

- Craft four bandages with one paper, one string, and one wool of any color.
- Use a bandage or supported healing potion on an injured guard to treat it
  from your hand. Sneak-use the supply to stock its medical pouch instead.
- Open the guard inventory to move supplies into or out of the three medical
  slots. Shift-clicking a valid supply stocks the pouch first.
- Select **Treat** to use the first valid supply in pouch order. Select
  **Pack** to put all pouch contents into one recovery item in an empty
  player inventory slot. Use that item on a guard with an empty pouch to restore
  its contents. Packing needs a free slot and preserves the treatment cooldown.

Supply access follows Guard Villagers' equipment permissions. Treatment needs
an alive, non-hostile guard within four blocks and line of sight. Each successful
treatment consumes one item, including in Creative mode. Full health, cooldown,
denied access, canceled healing, and ineffective healing consume nothing.

| Supply | Health restored | Cooldown | Returned item |
| --- | ---: | ---: | --- |
| Bandage | 4 points (2 hearts) | 2 seconds | None |
| Potion of Healing | 4 points (2 hearts) | 2 seconds | Glass bottle |
| Potion of Healing II | 8 points (4 hearts) | 2 seconds | Glass bottle |

Health cannot exceed the guard's maximum. Drinkable vanilla healing potions are
supported; splash, lingering, and custom-effect potions are excluded. A bottle
replaces the consumed potion in its source slot, including when other slots are
full. Remove empty bottles manually before restocking that slot.

Set `medical.enabled = false` to disable treatment while keeping supplies
accessible. Medic duty and retreat are separate per-guard choices. Optional
[downing and revival](casualty-recovery.md) uses the same supplies and starts off.
[Quartermaster service](quartermaster-service.md) can replenish the pouch when
the guard explicitly opts in.

## Datapack treatment profiles

Profiles live at `data/<namespace>/medical_treatments/<name>.json`. For example:

```json
{
  "format": 1,
  "item": "villagerguardgunners:bandage",
  "healing": 4.0,
  "cooldown_ticks": 40
}
```

`format`, `item`, `healing`, and `cooldown_ticks` are required. Optional fields
are `potion` and `return_item`. Unknown fields are rejected. IDs must use their
canonical namespaced form. Healing must be a finite positive number no greater
than 64; cooldown must be an integer from 1 through 1200 ticks. One application
always consumes exactly one item. Profiles declare health restoration only;
they do not execute arbitrary item-use callbacks or apply potion effects.

Potion profiles require `item: "minecraft:potion"`, a `potion` selector of
`minecraft:healing` or `minecraft:strong_healing`, and
`return_item: "minecraft:glass_bottle"`. Any source with a returned item must
have a maximum stack size of one. Items that cannot be placed in containers,
air, TaCZ guns, and TaCZ ammo are invalid treatment sources.

Up to 128 profiles can be loaded. Selectors must be unique across the complete
registry. A malformed, invalid, or ambiguous reload keeps the previous valid
registry and logs a warning. A valid reload can remove profiles: existing
supplies stay in their slots and remain withdrawable, but cannot treat guards
or be newly stocked until a valid profile is available again.

## Integration and persistence

Treatment runs on the server and invokes the normal cancellable Forge
`LivingHealEvent` boundary once. After callbacks, it rechecks permissions,
health, location, source items, and the profile before consuming a dose and
applying its accepted health gain. Reentrant treatment of the same patient or
by the same player is refused. Independent healing performed by another event
handler is not counted as this treatment. Guard Villagers' food healing retains
its existing behavior.

Compatible automation can insert through the dedicated
`MedicalCapabilities.SUPPLIES` capability. Its three slots expose copies,
simulation does not mutate storage, extraction is refused, and invalidated
handles remain unusable after capability revival. The generic Forge item
handler used by TaCZ retains its existing equipment and ammo slots.

The guard saves sparse slot contents and cooldown in a versioned NBT record.
Profile removal does not erase supplies. Unreadable or future records are kept
verbatim and lock the pouch against editing; **Pack** can move that raw
record into a recovery item without killing the guard. Restoring an unsupported
record keeps it locked and preserved.

Normal death and zombie or lightning conversion move supplied medical items
into a packed recovery item. Medical supplies are recovered independently of
ordinary equipment drop chance, `doMobLoot`, and cancellation of ordinary death
loot. Canceled recovery-item spawns remain in per-dimension saved data. At most
two pending entries are retried every 100 server ticks in already loaded,
entity-ticking chunks; recovery does not load or force chunks. Matching live
item identities prevent duplicate insertion during retry. Once spawned, the
pouch follows normal dropped-item pickup and despawn behavior.

Entity, item, and saved-data writes use Minecraft's normal separate save
boundaries. This is not a guarantee of crash-atomic persistence between saves.
Back up worlds before downgrading across inventory schema changes.

## Validation

Native Forge tests cover finite dosing, event cancellation and mutation,
reentry, source changes, potion containers, sparse persistence, unsupported
records, capability invalidation, stale menus, reload rejection, and recovery.
The integration probe suite checks the upstream food, death, conversion, totem,
and TaCZ damage boundaries.

The combined performance matrix includes 100 loaded guards with medical
supplies, an idle interval, and 20,000 accepted treatments. The fixture resets
health, cooldown, and stock to exercise a heavier workload than normal player
use. Reports distinguish whole-server tick times from treatment batch time and
bind JSON/JFR evidence to the production artifact and performance harness.
`verifyMedicalPerformanceReport` validates that evidence and dose conservation;
hardware-specific timings are observations, not a universal TPS guarantee.

The medic channel stress fixture exercises 3,200 accepted channels over 32
isolated actors, with artificial channel-time advancement. It measures channel
processing and conservation, not village navigation throughput. Native behavior
tests separately exercise live approach and treatment. Run
`verifyMedicAiPerformanceReport` after the combined performance matrix to check
its artifact-bound JSON/JFR evidence.

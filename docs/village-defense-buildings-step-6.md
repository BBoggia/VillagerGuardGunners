# Village Defense Buildings Step 6: bounded garrisons

Step 6 activates one-shot staffing for newly generated Barracks, Sentry Posts,
and integrated Watchtowers. It deliberately leaves Armories and Training Yards
for their later functional steps. The `villageBuildings.enabled` master switch
remains off by default while natural-village visuals and mixed-mod generation
are still awaiting manual review.

## Building behavior

| Building | Datapack group | Intrinsic ceiling | Spawn anchor |
| --- | --- | ---: | --- |
| Barracks | `villagerguardgunners:village_barracks` | 4 | Building anchor |
| Sentry Post | `villagerguardgunners:village_sentry_post` | 1 | Building anchor |
| Integrated Watchtower | `villagerguardgunners:village_watchtower` | 2 | Upper platform, five blocks above the building anchor |
| Armory | none | 0 | Inert until the Armory step |
| Training Yard | none | 0 | Remains passive with no controller |

Each bundled group has an Easy, Normal, and Hard variant. Easy groups are
small and use the Easy pistol pool. Normal Barracks and Watchtowers add
role-aware marksman or optional support members. Hard groups can use rifleman,
marksman, support, and bounded optional breacher compositions. Modpacks may
replace these three ordinary format-1 `guard_post_groups` resources with the
same IDs; the existing strict, atomic reload parser and four-member document
limit remain authoritative.

Peaceful generation consumes the building's `GARRISON` action with zero
occupants. A later difficulty change therefore cannot turn an already generated
empty building into a retrofit. A configured zero-guard limit behaves the same
way. Unsafe or unloaded spawn cells fail closed without loading a chunk.

## Existing systems remain authoritative

The village lifecycle does not create equipment, roles, or AI directly. It
creates Guard Villagers through the existing `MobSpawnType.STRUCTURE` path and
attaches the same persistent guard-post identity and pending assignment used by
the standalone 1.4 Watchtower. The normal entity-join handoff then performs the
existing deterministic role, squad, natural-loadout, ammunition, and TaCZ
issuance work on the server thread.

Because a village-garrison guard is explicitly created for a declared gun
assignment, it is eligible even when the general `replaceCrossbowsOnly` safety
setting is enabled. This narrow exception does not affect converted, command,
spawn-egg, generic structure, or standalone Watchtower guards. The main add-on
switch, natural-loadout switch, enabled structure source, difficulty pool,
nearby population cap, inventory transaction, explosive-weapon setting, and
all combat safety gates still apply. The default 3.0 garrison multiplier makes
the normal Easy, Normal, and Hard gun chances reach 100% before those other
gates are evaluated.

Every generated building owns a deterministic squad identity. Player-visible
names distinguish Barracks, Sentry Post, and Village Watchtower squads from
the independent standalone Watchtower feature.

## One-shot and performance boundary

World-generation workers still publish only immutable anchor identity and
rotation. The server task claims the anchor, selects at most one four-member
group, verifies only its prospective cells in already loaded chunks, and
claims `GARRISON` before entity creation. If the newly generated chunk has not
yet reached the live server, the handoff may defer for at most 40 ticks; it
never loads or tickets the chunk. Entity preparation is all-or-none: if any
member cannot be prepared or added, the complete batch is discarded rather
than leaving a partial group.

The feature adds zero recurring controllers or permanent ticks, village scans, entity scans, chunk
tickets, forced chunks, respawn managers, repair passes, or retro-generation.
Ordinary guards use the already existing event-driven combat and loadout
systems after creation. SavedData and entity-region saves are not one atomic
crash transaction, so the persisted at-most-once claim intentionally favors
preventing duplicates over repairing a group lost during an exceptional crash
between those two saves.

## Configuration

Config schema 14 adds three settings while preserving the schema-13 master and
per-building choices:

- `villageBuildings.spawnGarrisons` defaults to `true` and suppresses all three
  staffing actions without removing the building shells.
- `villageBuildings.maximumGuardsPerBuilding` defaults to `4` and is clamped
  from 0 through 4. Sentry Posts remain capped at 1 and integrated Watchtowers
  at 2 even when this value is higher.
- `villageBuildings.guardGunChanceMultiplier` defaults to `3.0`, is clamped
  from 0 through 3, and changes only the one-time gun chance for these village
  groups.

Migration from schema 13 initializes only these new values. Existing master,
building-type, combat, loadout, Watchtower, ammunition, squad, and Armory
choices are retained.

# Natural Guard Loadout Datapacks

Natural Guard Villager weapon pools are server datapack data. A pool file at

`data/<namespace>/natural_guard_loadouts/<path>.json`

has the pool ID `<namespace>:<path>`. For example,
`data/example/natural_guard_loadouts/village/guards.json` is selected in Fzzy
Config as `example:village/guards`.

## Spawn eggs and existing configurations

New configurations allow structure and spawn-egg origins to receive automatic
loadouts. When updating an existing server, add **Spawn Egg** to **Eligible Spawn
Sources** (`naturalLoadouts.enabledSpawnSources`) if it is absent. Existing guards
are not re-equipped; test with a new guard after changing the server setting.

This enables the normal loadout rules, rather than guaranteeing a gun from every
egg. With the default settings, the guard must start with a crossbow, pass the
world difficulty's upgrade chance, and remain below the nearby automatic-gunner
limit. Missing or excluded datapack guns also prevent issuance.
`/vgg inspect` reports the selection and issuance
result for a guard.

## Peaceful difficulty

**Natural Loadouts → Peaceful Difficulty** controls gun loadouts for new guards
on Peaceful, including guards created with enabled spawn eggs. It starts with
the Easy defaults: a **35% chance**, **one nearby automatic gunner**, and the
**Easy pistol pool**. The settings are independent of Easy; changing one
difficulty does not change the other. Set Peaceful's chance or nearby cap to
zero to disable its automatic loadouts.

Existing configurations receive this new section when updated. Their master
switches and spawn-source choices still apply, and existing guards keep their
equipment. Village Defense Buildings and Village Watchtowers use a separate
population policy, which creates no guards on Peaceful.

## File formats

The bundled pools remain on format 1:

```json
{
  "format": 1,
  "entries": [
    {
      "gun": "tacz:glock_17",
      "weight": 3
    },
    {
      "gun": "example:service_rifle",
      "weight": 1
    }
  ]
}
```

Format 2 conditional pools are strictly parsed, published, and selected against
the guard's one-shot spawn context. Their complete schema is documented in
[natural-loadout-v2-contract.md](natural-loadout-v2-contract.md). Different
condition categories combine with AND, include values combine with OR, and
exclusions veto matches. If no entry matches, the attempt ends before the nearby
population query and random roll. Existing format-1 pools continue on their
original context-free path.

Format 3 retains all format-2 conditions and adds an optional `sidearm` ID.
Its complete rules are documented in
[natural-loadout-v3-sidearms.md](natural-loadout-v3-sidearms.md). Natural
sidearms are a deliberate modpack opt-in: the bundled pools remain format 1
and contain none.

Format 4 retains the complete format-3 schema and adds an optional canonical
`role` ID. The role is a behavioral default set, not an item grant; see
[guard-roles.md](guard-roles.md). Formats 1 through 3 reject the field and
retain their prior selection and issuance meaning.

`gun` must be a canonical namespaced TaCZ gun ID. `weight` must be a positive
32-bit integer. Selection probability is an entry's weight divided by the sum
of all weights in that pool; weights do not need to add to 100.

The parser deliberately rejects unknown fields, missing fields, unsupported
format versions, fractional or non-positive weights, ambiguous duplicates,
empty pools, and total-weight overflow. Format 1 rejects repeated gun IDs.
Format 2 permits one gun in multiple entries only when their normalized
conditions differ; an exact gun-and-condition duplicate is invalid even if its
weight differs. Format 3 uses primary gun, optional sidearm, and conditions as
the duplicate identity. Format 4 additionally includes the optional role ID.
A pool may contain at most 256 entries, each condition
collection may contain at most 64 values, and one pool may reference at most
128 distinct biome and structure selectors in total. These limits bound both
spawn-time matching and explicit administrator diagnostics. Invalid syntax or
semantics are logged and that pool is omitted from the newly published snapshot
while other valid pools remain available. A missing or invalid pool therefore
fails closed when a later spawn attempts to select it; it never falls back to
stale data from before the reload.

Datapack priority replaces an entire pool file. Pools are not merged by
entry, so a higher-priority pack can reliably replace
`villagerguardgunners:hard` without depending on pack iteration order.

## Copy-ready examples and migration

The repository includes a complete Minecraft 1.20.1
[example datapack](examples/natural-loadout-datapack/README.md). Its
`vgg_example:migrated_normal` pool demonstrates a probability-preserving
format-1 to format-2 conversion, while `vgg_example:contextual_guards` covers
every contextual selector category and includes an unconditional fallback.
`vgg_example:rifle_sidearms` demonstrates the strict format-3 opt-in.
`vgg_example:role_marksmen` demonstrates format 4 with an item-neutral role
assignment.
The pack is inert until one of those IDs is selected in Fzzy Config, so copying
it cannot replace the built-in balance accidentally.

Follow the [migration guide](natural-loadout-migration.md) for a staged rollout,
scoreboard-tag timing, validation commands, common failure modes, and rollback.
Every example pool is parsed by the production strict parser during the normal
test suite, and its accompanying biome and structure tag files are checked for
the Minecraft 1.20.1 datapack layout and canonical resource IDs.

## Built-in progression

| Pool | Gun | Weight | Probability |
| --- | --- | ---: | ---: |
| Easy | `tacz:glock_17` | 4 | 40% |
| Easy | `tacz:m1911` | 3 | 30% |
| Easy | `tacz:p320` | 3 | 30% |
| Normal | `tacz:glock_17` | 3 | 37.5% |
| Normal | `tacz:m1911` | 2 | 25% |
| Normal | `tacz:uzi` | 2 | 25% |
| Normal | `tacz:hk_mp5a5` | 1 | 12.5% |
| Hard | `tacz:glock_17` | 3 | 30% |
| Hard | `tacz:uzi` | 3 | 30% |
| Hard | `tacz:hk_mp5a5` | 2 | 20% |
| Hard | `tacz:ak47` | 1 | 10% |
| Hard | `tacz:m4a1` | 1 | 10% |

The built-in pools contain no explosive weapons. A custom pool may contain an
explosive gun, but issuing it also requires
`naturalLoadouts.allowExplosiveWeapons = true`; normal explosive standoff,
terrain preservation, rules of engagement, and ally protection continue to
apply afterward.

## Reload and compatibility behavior

`/reload` builds a complete immutable, generation-tagged publication snapshot
and publishes it in one atomic replacement. The snapshot contains the usable
pool map, deterministic format/entry-count/weight metadata for published pools,
and bounded rejection reasons for invalid documents. Spawn handling and
diagnostic commands therefore see either the old complete generation or the
new complete generation, never a mixture. The per-generation unavailable-gun
warning tracker is replaced in the same publication.

## Administrator loadout diagnostics

Permission-level-2 administrators can inspect the current generation without
changing guards, inventories, provenance, population accounting, or random
state:

```text
/vgg loadouts validate [pool]
/vgg loadouts inspect <pool> [page]
/vgg loadouts preview <difficulty> <spawn_source> [pool]
```

`validate` checks all published entries, or one pool, through the same
non-mutating TaCZ compatibility analyzer used before issuance. Its summary is
capped at 20 actionable warnings/errors. Compatible guns whose explosive
property script needs a living guard are counted separately as
`contextDependent`; they are informational rather than warnings because actual
issuance evaluates them against the spawned guard. `inspect` shows format,
weights, conditions, and compatibility for eight entries per one-based page.
Invalid pages fail before emitting a partial report.

`preview` uses the command source's dimension and position, so `/execute in` and
`/execute positioned` can test alternate locations. It calculates contextual
matching and effective weights without a chance roll or nearby-guard query.
Guard-tag and runtime-script results remain context-dependent for console or
player sources; `/execute as` a Guard Villager guard supplies a real guard
context. Preview displays at most 20 matching entries and reports any omitted
results.

Reloading adds no entity scan, tick listener, TaCZ index lookup,
property-script evaluation, compatibility analysis, or gun-item construction.

## One-time spawn integration

Step 4 connects the policy to Guard Villagers without adding a recurring guard
scan. Guard Villagers' finalized structure, spawn-egg, command, and standard
conversion reasons map to the corresponding config source; every other
finalized reason maps to `OTHER`. Guard Villagers' player-driven villager
conversion bypasses Minecraft's normal `finalizeSpawn` method, so the add-on
marks that specific creation path as `CONVERSION` immediately before the new
guard joins the level.

Structure guards require a special handoff. Guard Villagers creates them in a
world-generation region and writes them into a protochunk, where querying the
live server entity manager would be unsafe and incomplete. The add-on therefore
saves only a pending source marker with that protochunk entity. When the guard
eventually joins the live `ServerLevel`, the marker is consumed exactly once
and selection is queued on the server executor. Failed selection is not retried
after config changes or a later reload.

The live pipeline reads the synchronized config and current world difficulty,
then applies source, starter-crossbow, difficulty, chance, and pool checks. The
bounded nearby-guard query runs only after those cheaper gates succeed. It uses
a cubical entity-index lookup followed by an exact squared-distance filter for
loaded guards, then consults a dimension-local, chunk-bucketed saved-data index
for authenticated guards in unloaded chunks. It counts only guards whose
effective primary role authenticated against a successful natural-loadout identity when
last loaded or saved. Selection stores immutable pool, primary gun, optional
sidearm, optional role, difficulty, and source
metadata, then immediately passes that candidate to the one-shot issuance stage
on the same server task. Only a completely issued guard counts toward the
nearby cap.

Plain `/summon guardvillagers:guard` uses Minecraft's `COMMAND` finalization
path. A summon command with explicit entity NBT deliberately bypasses
finalization and is left untouched, because its supplied state is intentional.
Existing guards loaded without the pending marker are likewise never revisited.

TaCZ gun IDs are syntax-checked but are not resolved during the datapack
listener. TaCZ gun definitions are reloadable data too, and listener ordering
should not determine whether a pool is accepted. Issuance instead resolves the
selected ID against the current TaCZ indexes after reload completion. It builds
the gun with TaCZ's native item builder, validates its ID, initial fire mode,
loaded magazine, reserve model, attachment-aware explosive trait, and runtime
explosive script result, then derives the ammunition ID directly from the gun
definition.

If a selected ID is unavailable, the server logs one warning for that pool/gun
pair per datapack generation. Repeated spawns do not spam the log, and `/reload`
resets the warning set so a still-broken replacement pack remains visible.

Only guns using physical ammunition are eligible. Dummy-ammunition and
native-infinite definitions are rejected rather than receiving a reserve stack
they cannot consume. The add-on resolves TaCZ's per-ammo stack limit, builds
exactly one complete loose-ammunition stack, and verifies its ID and native
`IAmmo.isAmmoOfGun` result. Missing, malformed, incompatible, unreadable, or
disallowed explosive definitions leave the guard's original equipment and
inventory unchanged.

A selected format-4 role must exist in the current atomic role publication.
Missing definitions reject issuance before a weapon or ammunition stack is
changed. A successful issuance persists the role ID in the same transaction
boundary as equipment, ammunition, sidearm state, and provenance.

A format-3 sidearm must also satisfy the reviewed sidearm eligibility contract:
it is a single non-explosive pistol in this release. It cannot inherit the
primary's `allowExplosiveWeapons` exception. The administrator commands report
sidearm compatibility, eligibility, ammunition, and explosive state separately.

Before replacing the starter crossbow, issuance snapshots the guard's original
main hand and three dedicated ammunition slots, then preflights all requested
reserve stacks against a scratch inventory. Format 3 requires one complete
compatible stack for each weapon, even for a shared caliber. The real commit
covers ammunition, private configured-sidearm storage, primary hand, and guard
provenance. Any unexpected handler, storage, equipment, or provenance failure
restores the snapshots; another mod's existing sidearm is never displaced. A
failed issuance remains a completed one-time attempt and is not retried after a
later config or datapack reload.

## Issuance identity and lifecycle

Step 6 gives every successful transaction a random issuance UUID and a strict
version-1 provenance record containing the selected pool, gun ID, difficulty,
and spawn source. The freshly built gun is stamped only after all Step 5
validation has succeeded, and the matching guard record is published only
after the gun/ammunition transaction commits. A record is active only when both
copies decode under the current schema, agree exactly, and TaCZ still identifies
the effective primary as the recorded gun. When a sidearm is active, that
primary is authenticated in private stowed-primary storage rather than assumed
to be in the main hand.

The guard-side record survives chunk unloads and server restarts. Equipment is
restored by Minecraft and Guard Villagers first; the add-on authenticates it at
the end of entity loading and reconstructs the immutable nearby-cap candidate.
Successful issuance, entity saves, chunk unloads, removals, and chunk reloads
also maintain the saved population index. Loaded guards are excluded from the
saved-data portion of a query and counted through the live entity index, so the
two paths cannot double-count. This keeps the cap effective while neighboring
chunks are unloaded without adding a repeating guard scan.

Manually supplied guns never receive this identity. Replacing or removing a
naturally issued gun makes its guard record inactive immediately and removes it
from the next save, even if the replacement has the same TaCZ gun ID. Moving a
marked gun to another guard is likewise inert because that guard does not have
the matching record. Missing fields, extra fields, invalid IDs or UUIDs,
unknown enum values, mismatched guard/item identities, and unsupported schema
versions all fail closed and reserve no nearby slot.

Natural provenance does not create a second equipment or drop system. The gun
remains an ordinary main-hand item, including its harmless identity NBT, so
Guard Villagers' existing equipment-drop chance and Minecraft's normal
equipment-copy behavior remain authoritative. The add-on continues to handle
only its three dedicated ammunition slots: normal death uses Guard Villagers'
equipment-drop chance, while zombie and lightning conversions drain those
otherwise-unmapped ammunition slots once before the original guard is
discarded. Avoiding a second gun-drop hook prevents duplicate guns during these
lifecycle transitions.

This is spawn/load/save work only. Gun definition resolution, the explosive
check, item construction, transaction validation, UUID generation, and NBT
stamping run once for a candidate. Authentication runs at load/save and inside
the already-lazy, bounded nearby-cap predicate; it adds no recurring guard scan
or combat-tick cost.

## Step 7 validation and diagnostics

`/vgg inspect <guard>` now includes an on-demand `Natural loadout` line. It
reports `NO_RECORD`, `ACTIVE`, or `GUN_MISMATCH`, whether selection was claimed,
the most recent in-memory selection and issuance results, and—when present—the
schema, pool, gun, difficulty, spawn source, and issuance UUID. A mismatch is
shown before the next save clears the stale guard record, which makes manual or
third-party equipment replacement diagnosable without weakening the
authentication rule. This capture runs only for the explicit administrator
command and installs no logger, listener, scheduler, or tick callback.

The automated matrix separates inexpensive deterministic policy checks from
real transformed integration checks:

- Plain JUnit covers every preflight gate and its lazy-query boundary, default
  difficulty progression, exact weighted selection, strict datapack parsing,
  immutable atomic reload publication, provenance schema/identity rejection,
  and diagnostic snapshot invariants.
- Forge GameTests load the bundled pools, exercise the protochunk source
  handoff through Guard Villagers' real first-AI-step equipment pass, build real
  TaCZ guns and full native ammunition stacks, reject an RPG and insufficient
  inventory without mutation, authenticate Guard/ItemStack save data, reject
  tampering, and execute the administrator command. A deterministic test-only
  crossbow loot table prevents the spawn-timing regression from passing by
  chance and is excluded from the production JAR.
- The live population test forces one Normal structure loadout, saves and
  reloads that guard, then proves its restored identity causes a second eligible
  guard inside the configured radius to receive `NEARBY_CAP_REACHED` while
  retaining its original crossbow.

The normal transformed suite contains 59 required GameTests. Pack-specific
equipment rendering and Guard Villagers death/conversion presentation remain in
the manual release matrix because a headless server cannot validate their
client visuals.

## Step 8 balance and release guidance

The built-in settings are deliberately a progression rather than a direct
weapon-power rebalance. TaCZ and the installed gun pack remain authoritative for
damage, recoil, fire rate, capacity, attachments, and every other firearm
statistic.

| Difficulty | Chance after eligibility | Nearby cap | Built-in weapon mix |
| --- | ---: | ---: | --- |
| Peaceful | 35% | 1 | Easy pistol pool: Glock 40%, M1911 30%, P320 30% |
| Easy | 35% | 1 | Pistols only: Glock 40%, M1911 30%, P320 30% |
| Normal | 50% | 2 | Pistols 62.5%, SMGs 37.5% |
| Hard | 65% | 2 | Pistol 30%, SMGs 50%, rifles 20% |

The chance is evaluated only for an otherwise eligible initial spawn and before
the nearby query. It is not a promise that the displayed percentage of all
guards will receive guns: a disabled source, protected non-crossbow weapon,
missing pool, reached cap, invalid TaCZ definition, disallowed explosive trait,
or unavailable inventory capacity can still reject the candidate. Successful
issuance intentionally includes a loaded native gun and one complete
TaCZ-defined stack of compatible loose ammunition, matching the requested
natural-guard supply model without altering later native consumption.

For modpacks, tune in this order:

1. Replace the datapack pool to define the intended weapon tier and relative
   rarity without touching server policy.
2. Adjust `upgradeChance` to change how often eligible spawns advance to the
   pool/cap stage.
3. Adjust `maximumNearby` to set the hard local population ceiling.
4. Adjust `nearbyCapRadius` only when the desired settlement scale requires it.
   A larger radius is still queried only once per candidate, but it can inspect
   more entities during that event.

Keep explosive entries opt-in and rare. Enabling natural explosive weapons does
not disable blast standoff, ally protection, rules of engagement, terrain
preservation, or per-round safety. When validating a custom pack, test every gun
ID after `/reload`, verify its physical ammunition model and stack limit, and use
`/vgg inspect <guard>` to distinguish pool, issuance, capacity, and provenance
problems.

The fastest rollback controls are `naturalLoadouts.enabled = false`, a zero
chance or zero cap for one difficulty, removal of a spawn source, or restoration
of the bundled pool ID. These changes affect only future spawn attempts; they do
not delete or rewrite existing equipment. Manually replacing an issued gun
removes that guard from future authenticated cap counts without deleting either
item.

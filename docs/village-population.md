# Village population and initial guard generation

Villager Guard Gunners can size the initial guard allowance for a new village
and share it between ordinary guards and Village Defense Building garrisons.
It also counts known residents, including guards that are unloaded, unarmed,
following players, injured, or occupied with other work.

Enable `villagePopulation.managedGeneration` to try managed generation. Its
development default is `false`. Existing villages remain observation-only:
enabling the setting does not add guards to them or remove excess guards.
Guard generation remains independent of automatic patrol coverage.

## Three separate counts

- **Staffing target** estimates how many guards a village could use.
- **Known living population** counts observed residents, including unloaded
  residents. Permanent removal, including death or conversion out of a guard,
  reduces this count. Older affiliations not yet assessed are shown separately.
- **Initial allowance and spent credits** describe one-time generation. Death
  does not refund a credit. There is no automatic replacement or recruitment.

A player can recruit or create more guards than the staffing target. Guards
already present are retained. Weapon upgrade chance, ammo, and the natural
gunner limit continue to use the existing loadout rules; a guard allowance does
not promise the same number of guns.

## Sizing a new village

The initial formula uses a frozen generation snapshot:

```text
target = clamp(minimumGuards, maximumGuards,
    ceil(residentialUnits / homesPerGuard)
  + ceil(occupiedCells / areasPerGuard)
  + min(defenseBonusCap, garrisonBuildings)
  + hardDifficultyBonusOnHard)
```

Defaults are a minimum of 2, maximum of 16, 3 residences per guard, 2 occupied
cells per guard, a defense bonus capped at 2, and no Hard difficulty bonus.
The absolute configurable maximum is 32. An inverted minimum/maximum or an
unsupported observation prevents enrollment of that new structure.

Occupied cells are 32-block horizontal cells containing classified houses or
Village Defense Buildings. They provide stable generation-time size evidence;
they are not the live patrol system's connected walking areas. Roads and
unclassified decoration do not independently add residences or occupied cells.
Native vertical terrain adjustments do not change a source's identity.

Vanilla village house templates are recognized directly. Pack authors can add
up to 128 exact IDs through `villagePopulation.residentialTemplates`. The
upstream Guard Villagers template list still controls where ordinary spawn
requests exist. A listed nonresidential template, including the default
`minecraft:village/common/iron_golem` marker, can receive the configured native
count up to the village's remaining allowance. Such markers do not add
residences or occupied cells to the staffing estimate. Each listed residence
can receive at most two ordinary slots, further limited by the native count.
Managed generation does not rewrite the upstream configuration or create
requests at templates absent from that list. Later beds never award generation credits.
Cached home observations provide only a rough diagnostic estimate for existing
unclassified villages.

Known Village Defense Building pieces reserve their bounded shares first,
with sentry/watchtower sites ahead of barracks and training sites. Allocation
uses stable ordering and rounds, so a late-loading garrison keeps its share.
Ordinary slots use the remainder. Local building caps, selected group size,
difficulty and safe placement still apply. The actual allowance can therefore
be below the target, and an allowance can contain slots whose request has not
yet arrived. Unused building shares are not repeatedly redistributed.

## Placement, retries, and restart behavior

The pinned Guard Villagers spawn hook is intercepted during structure-piece
placement. Managed ordinary requests become persistent, invisible vanilla
marker entities in the generation chunks. Extra native requests are suppressed
without canceling the house placement. Native requests within a garrison piece
use the garrison's own activation path. Unrelated structures and standalone
Watchtowers retain their behavior.

The frozen basis is saved with the structure start. Starts from an existing
world without the new-generation marker remain unmanaged. A saved, unplaced
new start can retain its eligibility across restart. Once enrolled, changing
settings or observing more homes cannot enlarge its initial allowance.
Plan format 2 records whether each source contributes residential demand.
Format 3 permits targets and nonresidential source capacities up to 64 for
expanded-plan validation. Formats 1/2 retain their 32-slot limits. Plans fitting
format-2 bounds continue to encode as format 2. The configurable ceiling remains
32 pending population-scale runtime benchmarks.
Format-1 plans still load with their original quotas and slot identities, and
their immutable journal files remain unchanged. This includes older plans
with zero allowance: the spawn-source correction applies to newly planned
villages and does not automatically add guards to previously enrolled ones.

Loaded requests are processed on the server thread. The adapter checks up to
486 nearby standing positions within four horizontal blocks and two blocks
below/three above the original request. It uses cached blocks and does not load
missing chunks. Native equipment initialization and the existing TaCZ loadout
handoff continue after a guard is created.

Ordinary requests and managed garrisons share a limit of two guard creation
attempts per dimension per game tick. Up to 1,024 ordinary request markers are
queued per dimension. Ordinary requests receive at most ten automatic checks
per load, with a 100-tick retry delay after an unsuccessful check. Missing
entity tracking receives a shorter bounded wait. Unfinished markers remain
saved and can resume when loaded again. Garrison retries use the existing
building activation queue. Disabling managed generation pauses enrolled work;
it does not hand those slots back to an independent native spawn path.

A small journal seals the plan and records the exact slot/guard UUID before
insertion. A known canceled insertion can retry with that same identity. A
successful insertion consumes the slot once. If a restart leaves an insertion
uncertain, the intent remains closed until that exact guard is observed. An
empty loaded chunk is not treated as proof that its guard no longer exists.
This can leave part of the allowance unissued instead of risking a duplicate.

## Storage and diagnostics

Population data is stored separately from village identity, patrol state, and
gun provenance. Each dimension keeps `villagerguardgunners_population.dat` and
a `villagerguardgunners_population_journal` directory under its normal `data`
directory. Back up and restore these with the rest of the world. The journal
contains immutable village plans, building bindings, and slot intents. Corrupt
or conflicting journal entries block affected issuance. A missing binding for a
building already associated with a managed village also blocks issuance rather
than selecting the ordinary building spawn path; future or malformed
population saved data is preserved and suspends the population service.

The census is assessed through the existing staggered guard update, with no
historical entity scan. Limits are 16,384 plans, 128 classified sources per plan,
64 slots in the plan model (32 through current configuration), and 262,144
known residents per dimension. Resident
and credit lookups are indexed. Journal work is tied to initial issuance or
reconciliation, not ordinary movement ticks. Reconciliation validates the exact
claim fields, source, ordinal, and expected guard UUID. Failed claim recovery
waits 600 ticks before retrying, and unload clears that transient delay. Saved
plans are checked against the journal once per loaded runtime before use.
Conflicting or over-limit plans are rejected before creating journal files.

Server operators can use `/vgg defense population` for the current village or
`/vgg defense population <village UUID>` for one recorded village in the current
dimension. Output shows known living guards, unassessed affiliations, target,
initial allowance, spent credits, and the dimension's request queue/error count.
The Squad Command Ledger includes a nearby population summary when inspecting
a route. These views do not initialize villages or spawn guards.

Terrain playtesting and release balance are still needed before enabling
managed generation by default. See [village records](village-records.md) and
[automatic patrol coverage](village-patrol-coverage.md) for their independent
behavior and limitations.

# Village Defense Buildings

Villager Guard Gunners 1.5 adds optional defense buildings to vanilla village pools.
A default-off master switch lets server operators decide whether newly
generated villages should use the additional buildings.

## Building Types

The system supports:

- **Barracks** with a difficulty-scaled guard garrison;
- **Village Armories** with public access and optional starting ammunition;
- **Sentry Posts** with one position-bound roadside checkpoint guard;
- **Integrated Watchtowers** with a fortified ready room, internal stairs,
  and an enclosed deck for as many as two ranged guards; and
- **Training Yards** with decorative practice targets and a small garrison.

Each building has plains, desert, savanna, snowy, and taiga variants. These
integrated Watchtowers are separate from the rare standalone Village
Watchtower structure.

The built-in relative frequencies, guard-population bounds, nearby armed-guard
caps, and Village Armory reward are documented in the
[Village Defense Building balance guide](village-defense-balance.md).

Source authors can add independent layout variants with their own geometry and
duty positions. The [layout authoring guide](village-building-layouts.md) describes
the build format and its limits.

Optional [CTOV Barracks](custom-village-barracks.md) adapt both furnished layouts
to six CTOV regional themes, alongside the [custom Armory packs](custom-village-armories.md)
[CTOV Sentry Posts](custom-village-sentry-posts.md), and
[CTOV Watchtowers](custom-village-watchtowers.md), and
[CTOV Training Yards](custom-village-training-yards.md).

The [vanilla village provider guide](vanilla-village-provider-buildings.md)
covers the regional packs with Better Villages, Lios, and Liberty's Villagers,
including tested layouts and terrain limitations.

## Village Pool Integration

Building entries are added during server startup. Existing vanilla and
third-party pool entries retain their original order, and repeated installation
attempts do not add duplicate entries.

Every configured pool, processor list, template, and matching building anchor
is checked before village pools are changed. Missing or invalid inputs are
reported without preventing the server from starting.

Datapack changes accepted by `/reload` update the validated definition set, but
village-pool changes take effect only after a server restart.

## Datapacks and Configuration

Pack authors can provide namespaced building definitions selecting:

- the building type;
- target village pools;
- structure templates;
- processor lists;
- placement projection; and
- entry weight and optional `selection_chance` from 0 through 1; and
- optional rigid-land [terrain screening](village-building-terrain-safety.md); and
- optionally, one to four rotation-aware guard duty slots relative to the
  building anchor.

Custom duty slots define a unique key, bounded local offset, horizontal
facing, tactical kind, and `any` or `ranged` weapon requirement. Omitting the
field retains the built-in reviewed layout for that building type. The exact
resolved layout is stored when the building generates, so a later datapack
reload or removal cannot move an existing building's duty positions. New
accepted definitions take effect with the rest of the village-pool entry after
a server restart. The copyable example documents the closed format and safe
coordinate bounds.

The synchronized configuration includes a master switch and individual
suppression controls for every building type. Configuration can disable a
datapack building but cannot silently restore one removed by the datapack.

A copyable example is available under
`docs/examples/village-buildings-datapack`.

## Garrisons and Armories

Barracks, Sentry Posts, integrated Watchtowers, and Training Yards can create
difficulty-aware guard groups. Peaceful creates no occupants. Garrisons reuse
the existing Guard Villagers spawn, role, squad, natural-loadout, ammunition,
and TaCZ systems. The built-in Sentry Post uses a three-block-wide road
opening, a clear center lane, alternating solid and low side cover, an
unobstructed raised firing bay, a framed rear windbreak, one sheltered sentry
cell, and a small crafting bench. Its dedicated Sentry role inherits the
general-purpose Rifleman behavior but remains bound to its checkpoint during
ordinary combat. It adds no door, villager job site, or storage reward.

The built-in integrated Watchtower uses a fortified lower ready room, a compact
one-block-wide internal stair that leaves more usable floor space, a connected
biome-matched fence railing, a two-block-high observation opening below the
roof frame, and a detailed gabled roof. Its guards are first created in the ready
room so their final natural loadout can be resolved safely. A guard holding a
TaCZ gun or crossbow is then moved once to the matching deck position; an
unarmed or melee-equipped guard remains downstairs. Later player equipment
changes do not teleport a guard between levels, and there is no recurring
equipment scan or tower-position controller.

Proposed compact and gatehouse layouts are documented in the
[integrated Watchtower design brief](integrated-watchtower-designs.md).
The [furnished Plains preview pack](watchtower-preview.md) supports manual
review. Natural village integration remains separate from these previews.

The built-in Training Yard is an open drill court rather than another enclosed
building. A wide road entrance leads through a covered two-guard firing line
and a clear center lane. Two raised decorative targets stand in front of a
reinforced backstop, while low side boundaries keep the yard readable without
trapping players or Guard Villagers. The targets add no training controller,
practice firing, ammunition use, storage, loot, or villager workstation.

The proposed village practice yard and drill courtyard are documented in the
[Training Yard design brief](training-yard-designs.md), with floor plans and
regional directions. The separate [Plains preview pack](training-yard-preview.md)
provides both furnished layouts for manual placement. A
[regional preview pack](regional-training-yard-preview.md) adds seven pairs with
local materials, roofs, and platforms.
The [optional Training Yard generation packs](regional-training-yard-integration.md)
connect all sixteen furnished layouts to vanilla, Tidal Towns, and MMV roads,
with two spaced drill duties and the existing initial garrison rules.

Village Armory buildings use the existing Village Armory block and its normal
automation and resupply behavior. Initial stock is ammunition-only and is
applied once. The built-in structure is a compact reinforced quartermaster
depot with a covered road entrance, secure slit windows, a clear central aisle,
one workbench, and a rear supply counter. It deliberately adds no secondary
chests, barrels, or villager job-site blocks that could be mistaken for the
ammunition store or change village professions.

## Persistence and Player Changes

Each generated building receives a stable identity and records completed
anchor, garrison, Armory, and initial-stock work. Once an action succeeds, later
visits do not regenerate removed guards, replaced blocks, or consumed stock.

Completed buildings add no recurring village scan, forced chunk, respawn
controller, or repair system. Unfinished work persists its rotation,
generation-time difficulty, and required one-shot action. It uses limited
delayed retries, then resumes when a chunk containing its anchor or a possible
guard cell loads again. Initial recovery waits at least one tick. Retry delays
increase from 1 to 40 ticks, with at most 16 queued activation attempts processed
per server tick across dimensions. Recovery never loads a chunk or scans
unrelated records, and the transient retry queue is cleared when the server stops.

## Operator Diagnostics

Permission-level-2 operators can inspect the village-building system with:

- `/vgg villagebuildings status` for synchronized settings, the current
  definition generation, and the startup installation result;
- `/vgg villagebuildings entries [page]` for accepted definitions, configured
  weights, target pools, built-in or custom duty-slot counts, and whether the
  current configuration would allow each entry at restart;
- `/vgg villagebuildings pools [page]` for each live pool's VGG entry count,
  VGG weight, total weight, and relative share; and
- `/vgg villagebuildings errors [page]` for the latest reload failures and
  startup-rejected entry IDs.

Each listing shows at most eight items per page. The command reads at most the
twenty-four startup target pools plus twenty-four newly accepted target pools after a reload.
It does not locate villages, scan chunks or entities, load chunks, change
registries, or enable recurring diagnostics. The live weight share describes
the current pool only; it is not a guaranteed per-village appearance rate.

## Compatibility and Validation

Integrated village templates opt into Minecraft's normal post-placement block
shape reconciliation. Fences, iron bars, walls, stairs, doors, and other
neighbor-sensitive blocks therefore settle against the blocks that actually
generated beside them after palette selection and rotation. This work runs
only once while the bounded structure piece is placed; it adds no tick scan or
runtime maintenance.

Definitions and saved records are checked for invalid, conflicting, oversized,
or unsupported data. Invalid reloads keep the complete last working definition
set. Only the original plains integration probe may omit the final
building-type field; every current or pack-authored entry must include it.

Automated checks cover resource decoding, pool installation, templates,
rotations, anchors, garrisons, Armories, persistence, and the isolated
performance workload. They also execute every village-building diagnostic,
check its permission boundary, and verify that it changes no definition,
startup snapshot, or live pool state.

The combined Step 13 performance run uses one production-artifact checksum for
the village-building workload and the existing 20-scenario combat, ammunition,
sidearm, and Armory-resupply matrix. It measures 100 disabled candidates, 100
same-tick passive initializations, 100 completed loaded records over 200 idle
ticks, preservation of every preexisting target-pool entry, 100 armed idle
guards, and bounded missing-supply and unreachable-Armory pressure. Timing is
retained as same-machine evidence rather than an absolute release threshold.

Natural-terrain appearance and mixed-mod village behavior remain manual release
requirements.

The full validation boundary is recorded in the
[Village Defense Buildings release matrix](village-defense-buildings-release-matrix.md).

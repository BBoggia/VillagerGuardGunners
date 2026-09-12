# Regional Barracks village integration

The regional integration datapacks add the compact and courtyard Barracks to
village street ends. They use the same architecture as the
[regional preview](regional-barracks-preview.md), with entrance connectors,
Barracks registration, three assembly duties, and one entrance sentry duty.
Normal difficulty, population, and garrison rules still apply.

## Build and install

Build the current mod and the optional datapacks:

```sh
./gradlew build regionalBarracksIntegration
```

The generated packs are in `build/distributions/`:

| Pack filename suffix | Designs | Required village provider |
| --- | --- | --- |
| `regional-barracks-vanilla.zip` | Plains, Desert, Savanna, Taiga, Snowy | Vanilla village street ends |
| `regional-barracks-tidal-towns.zip` | Deep ocean | Tidal Towns 1.3.4 |
| `regional-barracks-mmv.zip` | Jungle and Swamp | Moog's Missing Villages 2.1.1 |

Each filename begins with `villager-guard-gunners-`. Install the packs you need
in a fresh test world's `datapacks` directory and use the current packaged mod
with Guard Villagers and Timeless and Classics Zero (TaCZ). Restart the server
or reopen the world after changing building entries; `/reload` alone does not
mutate the live village pools. Existing generated villages remain unchanged.

The packs add weight-1 entries. Vanilla land layouts have
`selection_chance: 0.5`; Tidal Towns and MMV retain `0.25`. This is the chance
of entering a candidate list before Minecraft checks the connection and
available space, not a percentage of villages or a building-count limit.
Both layouts can still appear more than once. Each vanilla regional pack also
reduces its matching built-in family's chance to `0.25`; see
[frequency balancing](village-defense-balance.md#regional-pack-frequency).
The packs retain the existing Barracks and other Village Defense
Buildings. Installing all three packs adds 16 definitions to the 25
built-in definitions and uses thirteen target pools (five built-in house pools and eight regional
street-end pools). The current runtime permits twenty-four pools and 192 definitions in total. The optional
[regional Armory packs](regional-armory-integration.md) add another sixteen
definitions in the same pools, bringing the combined inventory to 57.

Install provider-specific packs only with their matching mods. Missing pools
are rejected and reported during startup. These packs contain no replacement
provider pools or structures and do not silently redirect an ocean design into
a land village.

## Placement strategy

These footprints exceed the house plots enclosed by ordinary road templates.
The land and MMV packs therefore add Barracks to the existing `terminators`
pools, where a road can end at the building rather than fitting it inside a
small house plot. Tidal Towns has no terminator pool: its pack adds weight-1
road-end candidates to `joshie:village/ocean/roads`. Selecting one ends that
branch, which can change the size and shape of the resulting floating village.
All of these pools can select several Barracks in one village. The reduced
selection chance limits how often the new layouts are considered. The packs
remain opt-in, and occasional awkward terrain placement is still possible.

## Entrances and foundations

The five ordinary land designs connect at their standing floor level. Jungle
and Swamp use a two-block-wide half-slab threshold to reach the elevated floor.
The ocean threshold also uses a half slab, above Tidal Towns' unlit campfire
road surface. Slabs avoid a directional final-state dependency when Minecraft
rotates the entrance jigsaw.

Connector names match the supplied provider jars: `minecraft:street`
for vanilla and Tidal Towns, and `mmv:street` for MMV. Each building
has one outward-facing entrance jigsaw with an empty continuation pool; it does
not grow another road network from inside the building. The connector is at
local Z = 0 on a two-block-wide apron outside the original front wall.
The integration templates are one block deeper than the architecture previews;
this preserves the Snowy courtyard lobby doors. Foundations remain finite, including the ocean raft underside;
terrain and water placement still depend on the selected village road.

The templates use `vggregional:regional/<region>/<layout>`. Definitions use
`vggregional:barracks/<region>/<layout>`. Their anchor is the center initial
guard cell. Duty offsets are relative to that anchor and rotate with the
building. A direct `/place template` command leaves metadata blocks and does
not run village registration; use the architecture pack for manual decoration
previews.

## Verification boundaries

The packaged QA helper checks all layouts in four rotations and can run
four-guard groups through their entrances in a flat comparison scene. Those
movement trials use scripted destinations with native navigation, doors, and
entity collision. They do not exercise combat decisions or casualty carrying.

A separate natural-generation observation loads a selected village from an
ordinary seeded world, inspects the regional pieces actually selected by the
jigsaw generator, checks finalized anchors, and constructs paths from their
adjacent road surface to the assembly point. An empty regional-piece sample
is not a compatibility pass. The QA sampler checks up to nine search origins
for a village containing a regional piece, records every distinct start it
inspects, and keeps the installed pack's weights and selection chances. The
optional `regionalSampleCount` runner setting inspects a fixed number of search
origins instead of stopping at the first village with a regional piece; it
includes zero-building observations. The default search-for-presence samples do
not measure how often buildings spawn. Individual samples do not establish all-seed,
all-terrain, or other-provider compatibility; large village mods that replace
the same vanilla pools need their own terrain and traffic trials.

The read-only `tools/audit_regional_barracks_world.py` tool inspects saved test
chunks after shutdown. It cross-checks the saved road block against the native
observation and reports water or lava at the four initial guard cells, sentry,
and adjacent road position. This supplements navigation checks; it does not
predict later flooding or replace terrain review.

The current vanilla ZIP also enables [terrain screening](village-building-terrain-safety.md).
Earlier layout samples in this guide predate the land-pack setting changes.

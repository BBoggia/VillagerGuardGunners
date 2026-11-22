# Armories in custom villages

These optional datapacks add the furnished compact Village Armory and supply
yard to selected custom villages. Buildings contain public ammo storage with
one-time initial stock and an entrance sentry duty. Install each pack with its
matching village mod and enable **Generate Village Defense Buildings** and
**Village Armory Buildings**.

## Coverage and installation

Build the mod and packs with `./gradlew build customArmoryIntegration`.
Archives in `build/distributions/` begin with `villager-guard-gunners-`:

| Filename suffix | Village mod | Included themes |
| --- | --- | --- |
| `armories-ctov.zip` | ChoiceTheorem's Overhauled Village 3.4.14 | Plains, Desert, Taiga, Snowy Igloo, Swamp, Jungle |
| `armories-sky-villages.zip` | Sky Villages 1.0.4 | Floating villages |

Copy the selected ZIPs into the world's `datapacks` folder and reopen the
world or restart the server. Use the current mod JAR, Guard Villagers,
Timeless and Classics Zero (TaCZ), and the village providers' dependencies.
Try a fresh world first; changes affect newly generated villages. `/reload`
alone does not reinstall the live village pools.

Install only packs whose village providers are present. A missing target pool
rejects its affected building entries at startup. These packs add entries without
replacing a provider's pool JSON. Keep other mods' pool changes in mind when
checking a combined installation.

The first collection includes fourteen layouts. Alongside the three
[regional Armory packs](regional-armory-integration.md), the three
[regional Barracks packs](regional-barracks-integration.md), and the built-ins,
there are 71 building definitions across 20 target pools. Runtime limits are
192 definitions and 24 pools; older builds with a 64-definition limit cannot
load the complete collection.

The [CTOV Barracks](custom-village-barracks.md) and
[CTOV Sentry Post pack](custom-village-sentry-posts.md) extend the same six
reviewed themes. The [Sky Villages guide](sky-village-defense-buildings.md) lists the expanded
floating-island collection and current combined resource totals.

## Appearance and placement

The six CTOV themes reuse the corresponding reviewed regional architecture.
Villages&Pillages’ Witch villages remain excluded from friendly defense buildings, including
entries targeting their road and terminator pools. A hostile supply depot
needs its own design and behavior before it can be included.

Sky Armories use Plains timber buildings on finite stone-and-dirt islands.
A grass skirt and railing enclose the platform, leaving a two-block entrance
at bridge-floor height. The building's interior, doors, and supply counter
remain at their original relative heights.

CTOV layouts have weight 1 and `selection_chance: 0.25`. Sky layouts use
weight 5 and chance 0.5 because that provider gives its normal houses weight
50. These settings control candidate-list selection, not the percentage of
villages containing an Armory.
Space, road orientation, and other pieces determine whether it fits. Armories
can terminate a branch, so the resulting village arrangement can change.

## Pack-author connection contracts

| Theme | Target pool | Incoming connector name |
| --- | --- | --- |
| CTOV Plains, Desert, Taiga, Snowy Igloo, Swamp | `ctov:village/<theme>/terminator` | `minecraft:street` |
| CTOV Jungle | `ctov:village/jungle/roads` | `minecraft:street` |
| Sky | `skyvillages:skyvillage_houses` | `skyvillages:house` |

CTOV's Snowy Igloo theme uses `snowy_igloo` in resource paths. The inspected
Jungle road pool has an empty fallback; its separately named terminator pool
is not used as the adapter target. Every adapter has one north-facing outward
jigsaw, an empty continuation pool, rigid projection, and empty processors.
The native NBT writer preserves the larger reviewed building envelopes.

Sky jigsaws replace floor planks. Other adapters retain the regional air or
bottom-slab entrance connection. All Armory blocks start as DATA markers so
the runtime assigns generated identity and claims initial stock exactly once.
Duty coordinates remain relative to the Armory anchor after island shifts.

## Further coverage

The experimental Clockwork adapter is excluded from player packs. A natural
trial in Integrated Villages 1.3.2 placed its entrance three blocks below the
adjoining road. That provider applies enhanced terrain adaptation to its own
pool elements; the current Village Defense Building element does not receive
that treatment. Further work needs a terrain-aware runtime adapter, followed
by repeated slope and road-access trials. Raising every template by a fixed
amount would not establish reliable placement across different terrain.

Other CTOV and Integrated Villages themes still need separate designs or
placement trials, especially treetop, underground, airship, and submerged
villages. The packs do not claim coverage for those themes. [CTOV Barracks](custom-village-barracks.md) cover the same six regional themes.
Other custom-provider building families remain follow-up work.

## Validation scope

Validation uses the packaged mod JAR and downloaded provider JARs in ordinary
Minecraft 1.20.1 / Forge 47.4.10 clients. The observation helper watches
naturally selected pieces using the packs’ normal weights and selection
chances. A disposable guard follows native navigation from the adjoining road
to the supply counter and back. These trials verify access; they do not
measure ordinary resupply frequency or village-wide patrol coverage.

CTOV trials target the large village starts. Small and medium villages share
the targeted pools, but their appearance rates are not separately measured.
The saved-world audit checks occupied floor positions for flooding. Terrain
outside the entrance and occasional cosmetic foundation exposure still need
normal playtesting across additional seeds.

The supported pack contents passed sixteen natural-placement client runs:
all fourteen layouts were observed, with 76 completed walking legs. Initial
stock was checked in 31 generated Armories; seven were checked after a full
world reopen, including consumed stock and player-added item tags. Repeated
activation did not refill them. The saved-world audit found no flooding in
2,635 occupied positions across these observations.

A separate negative trial installed candidate Armory entries in Witch road-end
pools. The hostile classification remained active and only the 25 ordinary
built-in definitions were injected. The profile generator and bundled-profile
regression tests also cover child pools and unused branches while leaving
shared vanilla pools available.

The Java suite passed 886 tests and the Python tooling suite passed 67 tests.
These checks establish representative placement and persistence, not perfect
generation on every seed.

An ordinary dedicated Forge server, with no observation helper, loaded all
eight supported datapack ZIPs together: 71 definitions across twenty pools.
Startup resource checks, `/reload`, and a full process restart passed without
missing or invalid Village Defense Building resources. The server also had
Integrated Villages and Villages&Pillages installed. This checks combined pack
loading and restart behavior; it is not a multiplayer movement trial.

The provider logs still contain references to absent optional content, such
as CTOV's Wares tags and a Supplementaries recipe for Farmer's Delight soil.
Those messages are separate from the successful building-resource checks.

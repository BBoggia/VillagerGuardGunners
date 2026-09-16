# Installing optional Village Defense Building packs

The mod includes five small building families. Optional datapacks add larger,
furnished Barracks, Village Armories, Sentry Posts, Watchtowers, and Training
Yards with regional materials and two layouts per region. Install any families
you want; the redesigned buildings are separate downloads from the mod JAR.

## Universal pack

For all supported villages, install **`villager-guard-gunners-village-buildings.zip`**.
This single download contains all five building families for vanilla villages,
ChoiceTheorem's Overhauled Village, Tidal Towns, Moog's Missing Villages, and
Sky Villages. You do not need to install every village mod. Buildings use the
village pools available in your world; content for absent providers stays inactive.

The pack preserves the resources and building IDs from all five provider packs.
It works with Villager Guard Gunners 1.7. That original JAR can report missing-pool
warnings for absent village mods; those entries are skipped and the other
buildings still load. Updated builds skip known absent providers before the
installation checks, while retaining warnings for missing pools in installed
providers. Pools supplied by datapacks also remain supported without a mod wrapper.

To switch from separate packs, close the world or stop the server, remove the
old provider or individual-family ZIPs, and install the universal ZIP. Do not
leave the universal pack and its individual alternatives enabled together.
Reopen the world or restart the server before exploring new villages. Existing
buildings remain in place.

## Provider-specific alternatives

For more selective installation, use one combined pack per village provider:

| Combined ZIP filename | Install with |
| --- | --- |
| `villager-guard-gunners-all-buildings-vanilla.zip` | Vanilla villages, including Better Villages and Lios replacements |
| `villager-guard-gunners-all-buildings-ctov.zip` | ChoiceTheorem's Overhauled Village |
| `villager-guard-gunners-all-buildings-tidal-towns.zip` | Tidal Towns |
| `villager-guard-gunners-all-buildings-mmv.zip` | Moog's Missing Villages |
| `villager-guard-gunners-all-buildings-sky-villages.zip` | Sky Villages |

Each combined pack contains Barracks, Village Armories, Sentry Posts,
Watchtowers, and Training Yards, with the same regional layouts and settings
as its five individual packs. It is a normal datapack ZIP; no extraction is
needed. You can mix choices across providers, such as all CTOV buildings and
only Tidal Towns Watchtowers.

Use either the combined pack or the individual packs for a given provider.
To switch, stop the server or close the world, remove that provider's five
individual ZIPs, and install its combined ZIP. Building resource IDs remain
the same, preserving saved references. Restart before continuing play.

## Individual family packs

Every filename starts with `villager-guard-gunners-`. Replace `<family>` below
with `barracks`, `armories`, `sentry-posts`, `watchtowers`, or `training-yards`.

| Filename after the prefix | Install with | Regional designs |
| --- | --- | --- |
| `regional-<family>-vanilla.zip` | Vanilla villages, including Better Villages and Lios replacements | Plains, Desert, Savanna, Taiga, Snowy |
| `regional-<family>-tidal-towns.zip` | Tidal Towns | Deep ocean |
| `regional-<family>-mmv.zip` | Moog's Missing Villages | Jungle, Swamp |
| `<family>-ctov.zip` | ChoiceTheorem's Overhauled Village | Plains, Desert, Taiga, Snowy Igloo, Swamp, Jungle |
| `<family>-sky-villages.zip` | Sky Villages | Floating islands |

Liberty's Villagers changes villager behavior and needs no separate building
pack. Villages&Pillages and Integrated Villages have village recognition support;
this collection does not provide dedicated redesigned building packs for their
custom settlements. Friendly defense buildings are excluded from the recognized
hostile Witch villages. Additional CTOV themes are outside this pack collection.

## Install

1. Install the matching Villager Guard Gunners JAR and its required mods in
   `mods` on both client and server. Install the village providers and their
   dependencies separately.
2. Close the world or stop the server. Put selected ZIPs, still zipped, into
   the world's `datapacks` directory. For a new singleplayer world, use the
   creation screen's Data Packs option before generating the world.
3. Enable Village Defense Building generation in Fzzy Config
   (`villageBuildings.enabled`). It starts disabled. Enabling the building
   system does not automatically enable size-based staffing or patrol coverage.
4. Reopen the world or restart the server. Use `/datapack list enabled` and
   `/vgg villagebuildings status` to check that the packs loaded. Building pool
   changes require a restart; `/reload` alone is insufficient.
5. Explore newly generated villages. Existing buildings remain unchanged.

The ZIPs belong in the world's `datapacks` directory, not `mods` or
`resourcepacks`. Dedicated servers install them in the server world; players
joining that server do not need to install the ZIPs in their own local worlds.
Do not install preview or QA packs for normal village generation. Replace an
older copy when updating a pack instead of keeping both ZIPs enabled.

## Generation and updating

Back up a world before changing its mod or datapacks. Keep the building packs
installed while exploring that world; removing them does not remove buildings
already placed and can leave saved structure references without their templates.

Vanilla regional packs favor the larger designs and reduce their matching small
built-in family's frequency. They also screen some flooded or deeply buried
sites. Terrain checks cannot prevent every awkward placement, and do not repair
old villages. Larger buildings can be rare where roads, terrain, or nearby
structures leave little room. Garrison and stock initialization happen once;
buildings do not replace killed guards or refill removed supplies.

Install provider-specific packs only when their required village mod is present.
The universal pack, all five provider packs, or their 25 individual equivalents use 175
building definitions and 20 target pools, within the
current limits of 192 definitions and 24 pools. Other custom building packs
share these limits.

See [provider compatibility](village-mod-compatibility.md),
[terrain screening](village-building-terrain-safety.md), and
[generation balance](village-defense-balance.md) for version-specific findings
and configuration details.

## Build from source

Run `./gradlew combinedBuildingIntegration` to build the universal ZIP, the five
provider ZIPs, and their individual source packs under `build/distributions/`.
`./gradlew prepareReleaseCandidate` includes these download choices. Combined
packs preserve every source resource byte and reject conflicting definitions
instead of silently choosing one family's version.

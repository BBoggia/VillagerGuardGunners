# Regional Sentry Post village integration

Optional packs add the roadside shelter and gateway lodge to newly generated
villages. Each has a sheltered watch bay, seating, a crafting table, and a
supported entrance beside the sentry. Both layouts retain the existing
Sentry Post policy: at most one initial guard and one `road_sentry` duty.
Difficulty, garrison, population, and gun-loadout settings still apply.

The separate [CTOV Sentry Post pack](custom-village-sentry-posts.md) adapts
both layouts to six reviewed CTOV themes.

## Installation

Build the packs with `./gradlew regionalSentryPostIntegration`. The ZIPs appear
in `build/distributions/`:

| ZIP suffix after `villager-guard-gunners-regional-sentry-posts-` | Villages | Layouts |
| --- | --- | --- |
| `vanilla.zip` | Plains, Desert, Savanna, Taiga, Snowy | 10 |
| `tidal-towns.zip` | Tidal Towns deep-ocean villages | 2 |
| `mmv.zip` | Moog's Missing Villages Jungle and Swamp | 4 |

Place the desired ZIPs in the world's `datapacks` directory. Install Tidal Towns
or Moog's Missing Villages and its dependencies for the corresponding pack.
Enable **Generate Village Defense Buildings** (`villageBuildings.enabled`) and
leave **Sentry Posts** (`villageBuildings.sentryPosts`) enabled. The master
building switch is off by default.

Restart the server or close and reopen the world after changing generation
packs. `/reload` alone does not replace installed village pools. Existing
villages remain unchanged. These packs add choices alongside the smaller
built-in Sentry Posts; they do not replace existing buildings.

The [Plains](sentry-post-preview.md) and
[regional](regional-sentry-post-preview.md) preview packs remain separate tools
for manual architectural review. They do not spawn guards or join village
pools. The integration ZIPs contain player resources only.

## Road and garrison contracts

Each template has one outward road connector, a two-block-wide apron, and a
DATA marker that becomes clear standing space. The marker lies in the watch
bay beside the walking route. Its authored `road_sentry` duty has offset
`[0,0,0]`, faces local north, and accepts any weapon. Rotation transforms the
building and its duty together.

The apron explicitly clears three blocks of body and headroom above each
entrance lane. Land entrances use a full floor. Ocean, Jungle, and Swamp use a bottom-slab
apron leading onto the raised, railed deck. The connector's final slab state
has no facing property, so it remains valid in every rotation. Furniture,
doors, roof shapes, and perimeter rails match the reviewed previews; adding
the apron increases template depth by one block.

The vanilla and MMV entries use their region's `terminators` pool. Tidal Towns
uses `joshie:village/ocean/roads`. MMV connectors use `mmv:street`; the others
use `minecraft:street`. These are branches or street-end shelters, not gates
that require a road to continue through the building.

Each entry has weight `1` and rigid projection. Selection chance is `0.5`
for vanilla land layouts and `0.25` for Tidal Towns and MMV.
These values describe individual placement choices, not the percentage of
villages containing a post. They do not impose a village-wide count cap.
Natural fitting and the other installed pool entries affect selection.

The existing garrison lifecycle records its initial action once. Reopening a
world or reactivating the same building must not add another initial guard.
An unstaffed duty can later receive an eligible guard through the ordinary
village defense system; the pack does not lock one guard permanently to it.
The larger lodge provides shelter and circulation, not a larger garrison.

## Scope and validation

The generation packs target the named pools above. CTOV, Integrated Villages,
Sky Villages, and other providers with custom pools need separate placement
reviews; installing a regional pack does not establish support for those
custom roads. Other mods that continue to use the targeted pools can select
these entries, but their terrain and road fit still need verification.

Foundations have finite depth. Terrain can leave exposed undersides, uneven
edges, or foliage near a building. These packs do not level the whole village
or guarantee every seed fits perfectly. A Savanna lodge trial found a vanilla
road descending into a river/ravine, placing the lodge floor below water level.
Water flowed into its watch bay after initial generation. That sample failed
the dry-access check and remains a known limitation; the packs do not yet
reject submerged road branches or lift buildings independently of their roads.

The generation test procedure uses the packaged Villager Guard Gunners JAR
in ordinary Minecraft 1.20.1 / Forge 47.4.10, with Guard Villagers 1.6.19 and
Timeless and Classics Zero (TaCZ) 1.1.8-hotfix. The observer selects actual
naturally generated pieces at unchanged pack weights, walks a disposable
guard from the real road to the duty cell, into the work area, and back out,
then checks saved garrison and duty identities after a full restart. It does
not place substitute roads or flatten the sampled terrain.

The architectural previews separately cover all rotations, passing villagers,
shelter, lighting, and live gunfire. A successful sample establishes that
sample's behavior, not universal terrain compatibility.

## Measured coverage

The final packs passed 32 ordinary Forge client launches: a fresh-generation
case and a full-restart case for each of the 16 layouts. These observed 17
new posts and the same 17 posts after reopening, with 102 completed walking
legs and 1,220 dry standing-cell checks. Every observed post had exactly one
initial guard, one correctly rotated duty slot, and one guard assigned to
that slot at the observation checkpoint. Repeating garrison activation
created no additional guards.

| Region | Roadside shelter | Gateway lodge | Full restart |
| --- | --- | --- | --- |
| Plains | Passed | Passed | Both layouts |
| Desert | Passed | Passed | Both layouts |
| Savanna | Passed | Passed on second seed | Both successful layouts |
| Taiga | Passed | Passed | Both layouts |
| Snowy | Passed | Passed | Both layouts |
| Ocean / Tidal Towns | Passed | Passed | Both layouts |
| Jungle / MMV | Passed | Passed | Both layouts |
| Swamp / MMV | Passed | Passed | Both layouts |

The failed Savanna river sample remains separate from these successful
counts. With seed `314159`, its watch position was `(-2721, 57, 4970)`;
nearby vanilla street pieces descended as low as Y=39. The second seed,
`314160`, supplied the successful lodge and restart sample without changing
pack weights, templates, or the surrounding terrain.

Tidal Towns 1.3.4, Moog's Missing Villages 2.1.1, and Moog's Structure Lib 3.1.2
were installed in the client matrix. All 41 definitions from the built-ins
and three Sentry Post packs were accepted. Installed template and definition
bytes were compared with the player ZIPs. The main Villager Guard Gunners
1.6 JAR remained unchanged by this integration.

An ordinary dedicated Forge server also loaded all eleven optional building
ZIPs together: the regional Barracks, Armories, and Sentry Posts, plus the
CTOV and Sky Armory packs. All 87 definitions across 20 target pools were
accepted with no missing pools, processors, or templates and no invalid
building templates. Startup, `/reload`, full process restart, and clean
shutdown passed with the player ZIPs and no QA helper mod. This validates
combined resource installation; it does not extend Sentry Post road support
to CTOV or Sky Villages.

## Updated land-pack settings

The vanilla ZIP also reduces the matching built-in family's selection chance
to `0.25` and enables [terrain screening](village-building-terrain-safety.md)
for its regional layouts. See [frequency balancing](village-defense-balance.md#regional-pack-frequency).
Earlier layout samples in this guide predate these land-pack setting changes.

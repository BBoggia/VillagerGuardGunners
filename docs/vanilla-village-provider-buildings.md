# Defense buildings with vanilla village overhauls

Better Villages and Lios Overhauled Villages change resources used by vanilla
villages. Liberty's Villagers changes villager behavior. The regional Village
Defense Building packs use vanilla village connections with these mods;
these installations do not need the CTOV or Sky Villages adapter packs.

## Installation

Build the five regional integration packs:

```sh
./gradlew regionalBarracksIntegration regionalArmoryIntegration regionalSentryPostIntegration regionalWatchtowerIntegration regionalTrainingYardIntegration
```

Install the ZIPs you want from `build/distributions/`. Filenames start with
`villager-guard-gunners-regional-` and end with `-vanilla.zip`. The family names
between them are `barracks`, `armories`, `sentry-posts`, `watchtowers`, and
`training-yards`.

Use the current Villager Guard Gunners JAR, Guard Villagers, Timeless and
Classics Zero (TaCZ), and each village mod's dependencies. Enable **Generate
Village Defense Buildings** and the desired building types. Reopen the world
or restart the server after installing the packs. Generation changes apply to
new villages; `/reload` alone does not reinstall the live village pools.

| Village mod in the test installation | Version | Additional dependencies used |
| --- | --- | --- |
| Better Villages | 3.3.1 | Library Ferret 4.0.0 |
| Liberty's Villagers | 2.0.0 | Architectury 9.2.14, Cloth Config 11.1.136 |
| Lios Overhauled Villages | 0.1.1 | No additional external dependency in this installation |

These are Forge 1.20.1 installations. The complete five-pack vanilla regional
collection and built-ins use 75 building definitions across 10 pools. The
[larger optional collection](sky-village-defense-buildings.md) shares the same
runtime limits; other installed packs count toward them too.

## Current land-pack settings

The updated vanilla packs use [terrain screening](village-building-terrain-safety.md)
and [family frequency balancing](village-defense-balance.md#regional-pack-frequency).
The broader client checks below used the earlier pack settings; they remain
layout evidence, while the new terrain and frequency checks are recorded in
those guides.

## Earlier packaged-game validation

Minecraft 1.20.1 / Forge 47.4.10 client trials used the packaged Villager Guard
Gunners 1.6 JAR with Guard Villagers 1.6.19 and TaCZ 1.1.8-hotfix.
All five regional vanilla packs were installed together. Each accepted layout
was checked in a fresh world and after a full client restart.

| Installation | Accepted Plains layouts | Fresh and restart cases |
| --- | --- | --- |
| Better Villages | Both layouts of Barracks, Sentry Posts, Watchtowers, and Training Yards | 16 |
| Liberty's Villagers | Both layouts of the same four families | 16 |
| Lios Overhauled Villages | Both layouts of the same four families | 16 |
| Better Villages and Lios together | Courtyard Barracks, gateway lodge, gatehouse lookout, drill courtyard | 8 |

These 56 accepted runs completed 384 scripted native walking legs and 4,812
saved-world standing-position checks without water or lava in those checked
spaces. The fresh samples included 29 buildings and 53 generated garrison
guards. Garrison identities and recorded building actions survived restart,
and repeated activation did not add more guards. All four rotations appeared
across the set; each layout was not tested in every rotation. Installed pack
resources matched the distribution ZIPs.

Eleven earlier unsuccessful trials remain part of the findings: eight empty
searches and the three flooded sites described below. Each empty search
covered 25 distinct villages. Better Villages' compact Barracks passed on
seed 314163 and its courtyard on 314161. Its gateway, compact Watchtower,
and drill courtyard passed on 314160, as did Lios' compact Barracks and
practice yard. The remaining accepted cases used seed 314159. No template,
selection weight, or selection chance was changed to obtain these samples.

An ordinary dedicated Forge server also loaded all three village mods with
the five actual player ZIPs, without the observation helper. Startup,
`/reload`, and a full process restart passed: 75 definitions across 10 pools,
with no missing pools, processors, templates, or invalid templates. Both runs
exited cleanly and preserved the installed JAR and ZIP hashes. This server
check does not establish multiplayer behavior.

## Scope and placement limits

The building checks focus on both Plains layouts of Barracks, Sentry Posts,
Watchtowers, and Training Yards, with all five regional vanilla packs installed
together. Earlier [Armory checks](regional-armory-integration.md) cover storage
and resupply behavior separately. These Plains samples do not establish
coverage for every biome, automatic staffing, patrol coverage, or combat.

Layouts attach to village street ends. Available space and candidate selection
can make an individual layout rare. A search that finds no building is not
counted as a successful placement test. Larger structures can affect the
village's arrangement by ending a road branch.

A Better Villages gateway Sentry Post failed at an entrance flooded by flowing
water. Its adjoining road lay around Y=50, while nearby water sources extended
to Y=62. Water occupied both entrance lanes and entered the lodge. The failed
site remains in its saved test world. A compact Barracks sample with Lios also
had flowing water across its road connection at Y=40, and a Lios practice
yard had flowing water over its service anchor at Y=120. The updated
terrain screening excludes all three recorded placements in new generation.
Other flooding mechanisms remain possible; it does not repair existing sites.

The existing Training Yard direct cross-range navigation limitation also
remains. Its checked circulation route returns from the western drill position
through the center before continuing around the range. The targets are
decorative, and these packs do not change guard navigation.

## Better Villages and Lios together

The inspected JARs contain 242 overlapping village resource paths, all with
different contents. Decoded structure contents also differ at every shared
path. Minecraft selects one effective resource for each path;
it does not merge the competing NBT templates. Resource priority therefore
matters when interpreting a combined installation, even when it loads cleanly.
The combined tests record the selected resource provider as well as the
building's physical access and guard persistence.

In the observed combined installation, Lios takes priority over Better
Villages for all 44 shared template paths recorded across the four layouts. Changing resource-pack priority can change the
result; the combined samples establish the recorded order only.

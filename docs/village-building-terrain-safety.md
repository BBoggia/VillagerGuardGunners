# Terrain screening for village buildings

The vanilla regional building packs screen candidate sites before Minecraft
adds a building to a village. The check avoids some flooded and deeply buried
placements while allowing another piece to use the available connection.
Install the updated regional ZIPs with the current mod JAR. Changes affect
newly generated villages; existing buildings are preserved.

## Pack-author setting

A format-1 building definition can opt in with `"terrain_safety": true`.
The field must be a boolean and requires `"projection": "rigid"`. Omitted
fields default to false, including elements saved by earlier builds.

The five vanilla regional packs enable this setting for both layouts in
Plains, Desert, Savanna, Taiga, and Snowy villages. The built-in buildings,
CTOV adapters, Sky Villages adapters, Tidal Towns packs, and MMV packs retain
their existing placement rules. Those settings need separate regional review.

The check samples a three-by-three grid spanning the footprint and extending
one block outside its edges. It uses the transformed anchor's standing height
and the generator's base terrain heights. A candidate is rejected if a sample
has either:

- Water or another fluid surface above the standing height.
- Terrain more than four blocks above the standing height.

The check happens during jigsaw assembly, before the candidate reserves space
or creates junctions. It does not skip individual chunks during placement,
remove already generated blocks, or change the parent road. A rejected candidate
leaves Minecraft free to try another piece. Checks consume no randomness and
use a bounded height cache belonging to that village assembly.

## Limits

Nine sampled columns do not describe every block around a building. Base
terrain heights also exclude neighboring structure blocks and later features.
This check cannot guarantee dry interiors, prevent a nearby fountain or later
waterfall from flooding an entrance, or prove a guard can reach every doorway.
It does not repair villages already affected by those problems.

Steep terrain can reduce how often the screened buildings appear. Raising
selection weights cannot make an unsafe site pass. Use the
[building balance guide](village-defense-balance.md) when reviewing frequency
with multiple packs installed.

## Regression checks

The natural-generation comparisons reproduce all three earlier failed sites
with the previous packs: the flooded Better Villages gateway Sentry Post,
the flooded Lios compact Barracks approach, and the Lios practice-yard anchor.
The screened runs exclude those exact entry/bounding-box placements, and the
terrain checks reject all three recorded coordinates. Ordinary land buildings
still appear in every installation. See the [frequency comparison](village-defense-balance.md#natural-generation-comparison)
for the effect on selected piece counts.

Ten additional packaged dedicated-server runs checked physical movement and
full restarts using the updated packs:

| Installation | Layouts checked |
| --- | --- |
| Better Villages | Roadside Sentry Post and supply-yard Armory |
| Lios Overhauled Villages | Compact Barracks |
| Better Villages and Lios together | Compact Watchtower |
| Vanilla villages | Drill courtyard |

These runs completed 110 scripted native walking legs and 1,314 saved-world
standing-position checks without water or lava in the tested spaces. Garrison
identities, one-time actions, and Armory stock checks survived restart. Saved
structure elements retained `terrain_safety` and their selection chances.
The installed pack resources matched the final distribution ZIPs, and all
50 regional NBT templates matched their previous geometry exactly.

The final movement and frequency trials used ordinary dedicated Forge because
the client launcher could not initialize a primary monitor. They establish
server generation and scripted movement behavior; they do not add new visual,
autonomous combat, or multiplayer-client coverage.

A separate ordinary dedicated server loaded all nine village mods and all
25 player ZIPs without the observation helper: 175 definitions across 20
pools. Startup, a CTOV village locate, `/reload`, and a full process restart
passed. Defense-building resource checks reported no missing pools,
processors, templates, or invalid templates. Both server processes exited
cleanly and retained their JAR and ZIP hashes. Existing optional CTOV/Wares
and Supplementaries/Farmer's Delight reference errors remain separate findings.

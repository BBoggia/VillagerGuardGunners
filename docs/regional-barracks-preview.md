# Regional Barracks preview

The regional architecture preview contains compact and courtyard Barracks for
Plains, Desert, Savanna, Taiga, Snowy, deep ocean, Jungle, and Swamp settings.
It turns the [architectural detail schedules](barracks-detail-design.md) into
Minecraft structure templates with furnishings, glazing, paired wooden doors,
supported lanterns, regional framing, and stepped slab roofs.

For optional natural village generation and entrance transitions, see the
[regional integration datapacks](regional-barracks-integration.md).

This separate datapack does not change the mod's default buildings or village
pools. The ocean, Jungle, and Swamp names identify design settings; they do not
yet establish natural placement compatibility with Tidal Towns or Moog's Missing
Villages.

## Build and place

Run either command from the repository root:

```sh
./gradlew regionalBarracksPreview
# Or, with Python 3 and no Gradle dependency:
python3 tools/build_regional_barracks.py
```

The result is
`build/distributions/villager-guard-gunners-regional-barracks-preview.zip`.
Place the zip in a Minecraft 1.20.1 test world's `datapacks` directory, then
reload the world or run `/reload`. Use the packaged Villager Guard Gunners,
Guard Villagers, and Timeless and Classics Zero (TaCZ) jars when reviewing guard
behavior.

For example:

```mcfunction
/place template vggpreview:regional/plains/compact ~ ~ ~
/place template vggpreview:regional/ocean/courtyard ~ ~ ~
```

Replace the region with `plains`, `desert`, `savanna`, `taiga`, `snowy`, `ocean`,
`jungle`, or `swamp`, and the layout with `compact` or `courtyard`. Place each
structure in clear space. The template includes air above its floor and clears
that part of its bounding box.

The command position is the template origin. Relative standing height is 1 for
the five land regions, 2 for Jungle and Swamp, and 3 for ocean. These templates
have finite foundations. The ocean design has a raft underside; none of the
templates extends supports to terrain or the seabed automatically. Road steps
and water-level transitions need a separate integration pass.

## Authoring contract

The build input is
[`regional-barracks-details.json`](../src/structureDesign/resources/regional-barracks-details.json),
initialized from the detail schedule. Edit this source for template changes;
the JSON under `docs/designs/` preserves the drawing proposal. Neither the build
nor the preview requires local agent notes.

Source `size` is width, depth, height. The compiler converts it to Minecraft's
width, height, depth order. It seals roof rises using half-block slab courses,
resolves overlaps using the scheduled roof ownership, and leaves each court
open above its floor. Windows and doors override framing. Decorative bedrolls
use carpets; shelves contain no inventory or loot.

The output namespace is `vggpreview`, separate from release structure IDs.
There are no jigsaw blocks, guard-spawn markers, building definitions, pool
entries, or loot tables. Manual placement does not register a defense building,
create a garrison, or assign duties. The manifest records planned standing cells
and navigation targets for the packaged test helper only.

## Packaged validation

The compatibility runner's existing `barracksDesignPreview` mode detects
`vggpreview:regional_barracks_manifest.json` when this datapack is enabled.
It loads all sixteen templates through Minecraft's structure manager and checks
each in all four horizontal rotations. The checks cover four initial guard
cells, the separate sentry cell, door and lantern counts, attachment survival,
and Guard Villagers path construction with wooden-door traversal enabled.
The client also captures exterior and roof views of every variant, plus selected
interior and night views.

These checks do not simulate a group walking through the doors, casualty
transport, combat, or naturally generated roads. They establish a repeatable
architecture review before those integration trials.

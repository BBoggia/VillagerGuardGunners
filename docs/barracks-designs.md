# Plains Barracks designs

For the newer sixteen-layout architecture pack, see the
[regional Barracks preview](regional-barracks-preview.md). This page describes
the earlier Plains prototypes and their separate generation experiment.

The compact and courtyard Barracks are furnished Plains prototypes for Village
Defense Buildings. Both use oak framing, pale terracotta infill, cobblestone
foundations, and spruce roofs with darker exposed edges. They have four
decorative bedroll spaces, a common room, and an entrance sentry position.

These designs are available through a separate preview datapack. They are not
included in the mod's default village pools. Review them in a fresh comparison
world before choosing generation weights and terrain placement rules.

## Compact Barracks

The 15 × 13 footprint combines a lower sleeping wing with a taller common hall.
Two overlapping gables break up the roof, and a timber porch marks the main
entrance. Shuttered windows, exposed beams, and small patches of moss at ground
level give it the character of an established village guardhouse.

Inside, four gray bedrolls with white pillows line the sleeping wing. Small oak
shelves provide personal storage decoration. A central aisle leads through a
two-block opening into the common hall, which contains a shared table, chairs,
a crafting table, and a rear bench. The hall's center stays open for assembling
the guards. A second two-block exit opens on the east side.

Three lanterns light the sleeping wing, hall, and porch. A green banner over the
porch identifies the building from the street. The building has no upper floor;
the roof volume remains open above the exposed ceiling beams.

## Courtyard Barracks

The 16 × 16 footprint forms an L around an open court. A long, lower sleeping
wing runs along the west side, while the taller common hall closes the rear.
Covered walks connect the two wings. Their posts stand beside the circulation
space, with an open three-block entrance and a secondary opening on the east.

The court uses full gravel, coarse dirt, and stone blocks to keep walking heights
level. Low stone borders define the yard. The center stays open for arrivals and
mustering; the entrance sentry has a separate place near the front edge.

The sleeping wing divides into two bays with two bedrolls each, shelves, and
small seats. Both bays connect through an open passage. The rear hall contains
a dining table, a crafting table, and a bench, with clear floor space around the
guard assembly positions. Four lanterns serve the two sleeping bays, common
hall, and covered walk.

## Functional boundaries

- Bedrolls use carpets. They do not create bed POIs or add a sleeping routine.
- Shelves and furniture are decorative blocks. Neither design adds a container,
  loot reward, Village Armory, or villager profession block.
- Each design retains the Barracks' existing four possible initial guard cells.
  Difficulty and population rules still determine the actual garrison.
- Authored duties include three assembly positions and one entrance sentry.
- Full floors support the entrances, secondary exits, and guard positions.
  Normal circulation aims for three blocks of headroom; wooden doors are two
  blocks tall. Casualty transport requires a separate movement trial.
- These are level-ground designs. Their template foundations do not extend
  down hillsides automatically.

## Build and preview

The editable block layers and palette are in
[`src/structureDesign/resources/barracks-layouts.json`](../src/structureDesign/resources/barracks-layouts.json).
They use the [layout authoring format](village-building-layouts.md).

```sh
./gradlew barracksDesignPack
```

This writes `build/distributions/villager-guard-gunners-barracks-designs.zip`.
Install it in a new Minecraft 1.20.1 world's `datapacks` directory with the
packaged mod and its dependencies. It adds two weight-1 entries; this increases
the Barracks' total pool share in that preview world.

The templates are `villagerguardgunners:village/plains/barracks/compact` and
`villagerguardgunners:village/plains/barracks/courtyard`. Direct `/place template`
placement leaves the road jigsaw and anchor metadata blocks and does not run
normal village garrison setup. Natural village placement uses the mod's building
element to finalize those markers and record the building.

The packaged compatibility runner's `barracksDesignPreview` case creates a fresh
flat world, places the original and both prototypes beside a shared road, checks
the new templates in four rotations, and captures exterior, roof, interior, and
night views. It checks native initial standing cells, sentry clearance,
decoration attachment, and guard path construction with wooden-door opening
enabled. This is an architectural preview, not a natural-generation, combat,
group-traffic, or casualty-transport test.

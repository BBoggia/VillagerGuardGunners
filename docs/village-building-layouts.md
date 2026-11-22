# Authoring Village Defense Building layouts

The source generator supports separate layouts for the same building type and
biome. Each layout can have its own dimensions, block arrangement, palette
overrides, lantern count, pool weight, and guard duty positions. This is a
build-time authoring format; generated datapack definitions retain runtime
format 1.

The existing 25 buildings retain their template paths, definition IDs, palettes,
and weights. The new Barracks designs are not included yet. The small platforms
under `src/templateTest/` are verification fixtures, not finished buildings.

## Source and generated identities

Edit `src/main/templates/villagerguardgunners/village_buildings.json`. Its
existing `buildings`, `biomes`, and `commonPalette` fields continue to describe
the original layouts. The optional top-level `layoutVariants` array contains
additional, independent layouts. An absent array and an empty array are equivalent.

For a variant with `building: barracks`, `biome: plains`, and `layout: compact`,
the generator produces:

| Resource | Identity |
| --- | --- |
| Template | `villagerguardgunners:village/plains/barracks/compact` |
| Building definition | `villagerguardgunners:barracks/plains/compact` |
| Existing template | `villagerguardgunners:village/plains/barracks` |
| Existing definition | `villagerguardgunners:barracks/plains` |

Layout names use 1–48 lowercase letters, digits, or underscores, starting with a
letter. `default` is reserved. A building/biome/layout combination must be unique.
The same layout name can be used for another biome with different geometry.
Each entry supplies its complete geometry; dimensions, layers, and duty positions
are not inherited from another layout.

## Variant fields

| Field | Meaning |
| --- | --- |
| `building` | `barracks`, `armory`, `sentry_post`, `watchtower`, or `training_yard` |
| `biome` | `plains`, `desert`, `savanna`, `snowy`, or `taiga` |
| `layout` | Stable layout name used in the generated paths |
| `size` | Integer `[width, height, depth]`; each axis 1–16, height at least 3, volume at most 4,096 |
| `layers` | Complete Y/Z/X symbol grid, exactly matching `size` |
| `palette` | Optional array replacing existing symbol entries or adding new symbols for this layout only |
| `weight` | Explicit integer pool weight, 1–5 |
| `lanternCount` | Exact integer number of supported lanterns, 1–16 |
| `dutySlots` | One to four explicit duty definitions, using the runtime slot fields below |

Unknown variant fields are rejected. At most 25 additional layouts can be
compiled alongside the original 25, for at most 50 compiled definitions. The
runtime accepts up to 192 definitions in total, including separately authored
datapack entries, across at most twenty-four target pools. The compiler's own
layout-count, axis, and volume bounds are unchanged.

An entry's `layers[y][z]` is a string whose characters run along X. A schematic
entry has the following shape; substitute complete, reviewed `layers` before use:

```json
{
  "building": "barracks",
  "biome": "plains",
  "layout": "compact",
  "size": [15, 12, 13],
  "layers": [],
  "palette": [
    { "symbol": "P", "name": "minecraft:oak_planks" },
    { "symbol": "w", "name": "minecraft:white_terracotta" }
  ],
  "weight": 1,
  "lanternCount": 3,
  "dutySlots": [
    { "key": "center_muster", "offset": [0, 0, 0], "facing": "north", "kind": "muster", "weapon_requirement": "any" },
    { "key": "west_muster", "offset": [-1, 0, 0], "facing": "west", "kind": "muster", "weapon_requirement": "any" },
    { "key": "east_muster", "offset": [1, 0, 0], "facing": "east", "kind": "muster", "weapon_requirement": "any" },
    { "key": "entry_sentry", "offset": [-1, 0, -6], "facing": "north", "kind": "sentry", "weapon_requirement": "any" }
  ]
}
```

The complete test entries in `src/templateTest/resources/layout-variants.json`
illustrate the format with two plains geometries and one taiga geometry. They
must remain outside the release blueprint.

## Palette and marker rules

The effective palette starts with `commonPalette`, then the selected biome's
palette. Variant overrides replace an entire entry by symbol; replacing a block
also replaces its properties and NBT. New entries append to that layout's palette.
Overrides do not mutate the shared palette or another variant.

Symbols `.`, `V`, `J`, and `A` are reserved for air, structure void, the entrance
jigsaw, and the building anchor. A layout must contain exactly one `J` and one
`A`. The generator writes the appropriate `village_anchor:<building>` metadata
and the selected biome's street connector. The connector stays at Y=1, has
support below and air above, and finalizes as air. Additional marker blocks
cannot be introduced through an alternate palette symbol.

Lantern symbols are `q` for hanging and `Q` for standing. Their `hanging`
property must be the matching string `true` or `false`; a standing `Q` needs a
palette entry. Both regular and soul lanterns are supported. The source checks
the adjacent support cell and the exact declared count. Other lantern symbols
are rejected. Native attachment and collision still require Minecraft testing.

Palette `properties` and `nbt` are maps of string keys and string values. Nested
NBT, item lists, entities, and numeric NBT values are not supported by this
compiler. Use correctly authored external structure NBT when a datapack needs
those features. Adding a bed, job-site block, or container changes gameplay and
should be an explicit design decision.

In an independent layout, `V` omits a cell from the generated NBT and preserves
the existing world block there. Use `.` where placement must clear space. The
original built-in resources retain their existing encoding for compatibility.

## Duty positions and building behavior

The generator writes source `dutySlots` as runtime `duty_slots`. Keys must be
distinct, 1–32 lowercase letters, digits, or underscores. Offsets must be distinct
integer triples, with each component between -16 and 16. Facing is horizontal;
kinds are `muster`, `sentry`, `overwatch`, or `drill`; weapon requirements are
`any` or `ranged`. Every position is relative to the layout's anchor and rotates
with the building.

The generator checks bounds, two cells of standing clearance, and a non-air
support cell for authored duties and the building's possible initial guard cells.
These are conservative source checks, not native collision, support-face,
reachability, or carrier-clearance tests. Initial guard offsets remain:

| Building | Initial cells relative to anchor |
| --- | --- |
| Barracks | `[0,0,0]`, `[-1,0,0]`, `[1,0,0]`, `[0,0,-1]` |
| Sentry Post | `[0,0,0]` |
| Training Yard / integrated Watchtower | `[0,0,0]`, `[-1,0,0]` |
| Village Armory | No generated garrison; anchor becomes the Armory block |

Integrated Watchtowers also need clear supported cells five blocks above both
initial positions, matching their existing one-time ranged placement. An Armory
duty position cannot occupy its anchor. Layout variants do not change guard
counts, spawn offsets, storage rewards, or the Watchtower's upper-deck behavior.

The runtime keeps each entry's identity separate and saves the resolved duty
layout when a building generates. A later definition change does not move an
existing building's saved positions. See the
[datapack example](examples/village-buildings-datapack/README.md) for runtime
reload and configuration behavior.

## Generation and validation

```sh
./gradlew generateVillageBuildingTemplates testVillageBuildingLayouts
```

The generator validates into a staging directory before publishing successful
output. Validation failure preserves the previous published resources. A
successful run removes stale outputs for variants that were removed from the
source. Both authoring scripts are declared task inputs, so changes rerun generation.

Release verification requires every configured template and definition in the
jar. The layout checks also compare all 50 original files, decode generated NBT,
verify independent palettes and duty definitions, reject malformed inputs, and
exercise failed publication and stale-file cleanup.

Each extra variant creates an additional pool entry. Its weight affects the
building family's total generation share; weights are not automatically divided
among layouts. Prototype datapacks should use isolated worlds until placement
frequency, terrain support, entrances, and guard movement have been reviewed.

For packaged Forge validation, `testVillageBuildingLayouts` produces the
isolated datapack `build/generated/village-layout-fixture-pack`. The compatibility
runner's `layoutVariantChecks` option verifies those three definitions, native
pool entries, distinct identities, template sizes and palettes, placement in all
four rotations, standing cells, and duty-layout serialization. It explicitly
finalizes metadata cells after raw native template placement. It does not test
natural site selection, actual garrison activation, or finished building appearance.

# Village Defense Buildings Step 5: biome-aware visual templates

Step 5 replaces the inert compatibility probe with the first complete physical
asset matrix for Village Defense Buildings. It packages Barracks, Armory,
Sentry Post, integrated Watchtower, and Training Yard shells for plains,
desert, savanna, snowy, and taiga village pools.

The master `villageBuildings.enabled` switch deliberately remains `false` by
default until the templates pass the retained manual road, terrain, palette,
and mixed-village-mod checks. Enabling it makes the shells eligible only in
newly generated village pieces. It does not retrofit existing villages.

## Reviewable source and generated resources

The complete source blueprint is
`src/main/templates/villagerguardgunners/village_buildings.json`. It contains:

- exactly five biome palette profiles;
- exactly five building layouts;
- dimensions no larger than 16 blocks on any axis or 4,096 blocks in volume;
- one weight-1 definition for each biome/building pair;
- exactly one outward-facing `minecraft:building_entrance` jigsaw per layout;
  and
- exactly one `village_anchor:<building>` marker per layout.

`generateVillageBuildingTemplates` validates that contract and writes 25
compressed structure templates beneath
`data/villagerguardgunners/structures/village/<biome>/<building>.nbt` plus 25
strict format-1 definitions beneath
`data/villagerguardgunners/village_buildings/<building>/<biome>.json`.
Generated output is rebuilt from scratch so removed variants cannot remain in
the release JAR. The artifact gate requires every one of the 50 resources. The
runtime document ceiling is 50, leaving 25 bounded slots for modpack-owned
replacements or extensions beyond the 25 built-in definitions.

The old `integration_probe/plains` definition is no longer packaged. Its stable
ID remains reserved only so a development world containing an older Step 2
pool-element encoding can still decode safely.

## Visual identities

- **Barracks** — the largest low building, with a broad fortified footprint,
  symmetrical windows, interior lantern, and room for a later bounded
  garrison.
- **Armory** — a compact reinforced storehouse with crafting and target-block
  details. No VGG Armory block or ammunition is placed in Step 5.
- **Sentry Post** — the smallest roadside shelter with an open front and
  covered observation position.
- **Integrated Watchtower** — a narrow multi-level tower with an internal
  ladder and upper platform. It does not reuse or affect the standalone 1.4
  Watchtower structure.
- **Training Yard** — a mostly open paved yard with target and hay details. It
  has no combat target entity, training AI, or recurring controller.

Each layout uses a biome palette rather than a global plains palette: oak and
cobblestone for plains, sandstone and acacia details for desert, acacia for
savanna, spruce with cold glass accents for snowy villages, and spruce with a
mossy foundation for taiga villages.

## Runtime and performance boundary

Step 5 changes no combat or entity runtime. Template compilation occurs only at
build time. At server startup the existing bounded definition listener reads
25 small documents; when integration is enabled, the existing one-shot pool
preflight validates all templates before committing five additive pool
mutations. Ordinary selection and placement then use Minecraft's jigsaw
system.

Placed anchors reserve only the passive schema-1 building identity introduced
in Step 4. Garrison spawning, Armory placement or stocking, role/loadout
issuance, loot, repairs, scans, forced chunks, and recurring ticks remain
absent.

## Validation boundary

Automated coverage validates the blueprint dimensions and symbols, requires all
50 generated resources in the JAR, loads all 25 templates through Minecraft's
template manager, verifies every target pool and street connector, and performs
real rotated placement for one template of each building type. The placement
test also proves anchor consumption and SavedData publication.

Actual village generation across terrain and third-party village overhauls is
still a manual release gate. Step 5 does not claim that every palette, doorway,
road transition, or terrain edge has passed visual approval merely because the
NBT and jigsaw contracts are valid.

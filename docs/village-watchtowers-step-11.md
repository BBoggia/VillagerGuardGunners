# Village Watchtowers Step 11: template and bounded placement

Step 11 activates the first physical Village Watchtower while deliberately
leaving guard groups, data markers, Armories, and loot for their reserved later
steps. The result is a normal Minecraft structure start with one persisted
template piece and no post-generation manager, scan, or recurring work.

## Built-in tower

The first tower is a compact 9 x 14 x 9 village-style structure with a stone
foundation and lower room, an internal ladder, an oak observation deck, fence
railings, and a slab roof. Its source is the reviewable layer blueprint at
`src/main/templates/villagerguardgunners/watchtower.json`.

`generateWatchtowerTemplate` validates the blueprint dimensions, palette,
symbols, and layer widths before deterministically compiling it to
`data/villagerguardgunners/structures/watchtower.nbt` in the generated main
resource directory. The production artifact gate requires that NBT. Keeping
the layer blueprint as the source of truth avoids an opaque hand-edited binary
while still presenting Minecraft with its native compressed structure format.

The template includes air where the tower must clear vegetation and
`structure_void` around its ground-level footprint so placement cannot dig an
unintended one-block trench around the foundation. Both structure blocks and
structure voids are ignored by the piece processor. The template contains no
entities or data markers in this step.

## Placement policy

The random-spread structure set remains at spacing 40, separation 12, and salt
17290527. Step 11 activates it through the five vanilla village biome tags for
desert, plains, savanna, snowy, and taiga villages. This composes with datapack
priority and does not modify a vanilla village template pool or search for a
nearby village.

Each rare candidate performs a fixed nine-point sample over the 9 x 9
footprint: four corners, four edge centers, and the center. The candidate is
accepted only when:

- world-surface and ocean-floor heights agree at all nine points;
- the surface and its immediate support are dry, including below solid ice;
- the highest and lowest samples differ by at most two blocks; and
- the complete 14-block template fits inside the dimension build height.

Obvious water, excessive slope, void terrain, and build-height failures stop
after the 18 height queries. Only a preliminarily accepted candidate performs
the nine bounded base-column support checks. There is no block scan, structure
lookup, synchronous chunk acquisition, or chunk ticket.

The template is centered inside the selected chunk and placed at the lowest
accepted sample, allowing its solid foundation and air volume to level at most
two blocks of terrain without floating. Rotation is selected once from the
world-generation random source. The piece rotates around the template center,
persists the chosen rotation, and uses a safe `NONE` fallback if old or damaged
piece data lacks a valid rotation value.

## Deliberately deferred

Step 11 does not add:

- guard, Armory, loot, or post-identity markers;
- entity spawning or item issuance;
- an `afterPlace` scan or retro-generation path;
- configuration or operator commands; or
- a tower ticker, loaded-tower registry, chunk ticket, or other recurring task.

Those surfaces remain isolated for Steps 12 and later. A Step 11 tower is only
the structure itself.

## Validation

The Java 17 Step 11 gate passes 400 plain JUnit/source/artifact tests, both
development source sets compile, and all 88 required Forge correctness
GameTests pass. The added tests cover the exact blueprint bounds and palette,
dry/wet/frozen-support decisions, two-block slope ceiling, build-height bounds,
generated NBT loading, all four rotation-safe 9 x 14 x 9 bounding boxes,
dynamic structure lookup, and agreement between the runtime terrain decision
and the generated structure start.

The verified development artifact is `villagerguardgunners-1.4-dev.jar`, size
821205 bytes, SHA-256
`53dc78cf6107dc7f15d8019218d560e813153e15d4baad94408505438bd9b953`.
The retained manual matrix still requires visual generation across real terrain
and fixed seeds before the placement rows can be promoted from Partial to Pass.

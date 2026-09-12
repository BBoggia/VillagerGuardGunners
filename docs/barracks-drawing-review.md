# Regional Barracks drawing review

The drawing set defines compact and courtyard Barracks for eight village
settings. Each layout has a furnished floor plan, roof plan, front and east
mass elevations, and an entrance/foundation section. These are architectural
proposals for review. They do not add templates to Minecraft or alter the
existing Plains preview datapack.

The subsequent [architectural detail pass](barracks-detail-design.md) specifies
window bays, door hinges, furniture blocks, lantern supports, and roof courses.
Its detail drawings supersede the mass outlines where heights and assembly
labels differ.

The [regional brief](regional-barracks-designs.md) describes the intended
materials and character. The [drawing data](designs/barracks/drawings.json)
records the dimensions and reserved spaces. The elevations show the building
volumes and roof silhouettes; window arrangements, individual block courses,
roof intersections, shutters, trim, and final door hinges remain to be drawn.

## Decisions reflected in the drawings

| Setting | Compact envelope | Courtyard envelope | Direction used in this draft |
| --- | --- | --- | --- |
| Plains | 15 × 13 × 12 | 16 × 16 × 12 | Unequal timber gables, side sleeping wing, open side exit. |
| Desert | 13 × 13 × 9 | 16 × 16 × 10 | Cut sandstone with smooth trim, stepped parapets, additional east courtyard shade. |
| Savanna | 15 × 13 × 10 | 16 × 16 × 10 | Broad shallow hips, veranda frontage, paired rear rest alcoves. |
| Taiga | 13 × 15 × 12 | 16 × 16 × 13 | Long hall, separate rear rest room, steep spruce roof. |
| Snowy | 15 × 14 × 12 | 16 × 16 × 13 | Offset entry doors, enclosed lobby, sheltered circulation around an open court. |
| Deep ocean / Tidal Towns | 16 × 15 × 10 | 16 × 16 × 11 | Timber raft, one gangway, full deck and protected refuge. |
| Jungle / MMV | 15 × 14 × 12 | 16 × 16 × 12 | Low raised floor, deep veranda, jungle timber and stone. |
| Swamp / MMV | 15 × 13 × 11 | 16 × 16 × 12 | Raised dry floor, dark oak and mud brick, protected wet-site boundary. |

Dimensions are width × depth × reserved total height, in blocks. They include
approaches and roof overhangs. An envelope is a ceiling, not an instruction to
fill its entire height. In particular, the Savanna roof stays lower than its
height allowance.

The compact Savanna grows from the original 12-block depth to 13 blocks. This
keeps the four rear sleeping places and their approach space. The compact
ocean raft grows from 15 × 14 to 16 × 15 so the east and aft walks can be two
blocks wide inside the railing. Both changes remain inside the current
16-block-per-axis authoring limit.

The Snowy courtyard moves its common hall's front wall from depth 9 to depth
11 and its rear wall to depth 15. This fits a clear 3 × 3 gate lobby with offset
outer and inner doorways, followed by a separate 4 × 4 open court. The common
hall consequently has only three clear blocks of depth. Its furniture sits
against the edges; passing traffic and assembly still need a native trial.

These choices are provisional design recommendations. Selecting them for the
drawings does not freeze the final palettes or approve village generation.

## Reading the floor plans

The entrance is at the top. X increases left to right; depth Z increases from
the entrance toward the rear. Each grid square represents one block. Filled
walls and posts reserve occupied cells; paired diagonal lines indicate doors
symbolically open. Their final hinge and swing clearance are not specified by
that symbol.

Four colored bedrolls provide decorative sleeping places. T marks the common
table, W the work surface, and B a bench. The work surface is intended to use
one crafting table. Shelves, dishes, equipment decoration, and chairs need a
later detail pass and must fit outside the reserved routes.

A marks the anchor and central possible initial guard cell. Circles 1–3 mark
three additional possible initial cells and proposed assembly duties. S marks
the separate entrance sentry duty. These five marked positions do not increase
the existing maximum of four possible initial guards. Existing population and
difficulty rules still determine how many guards appear.

Shaded routes reserve two clear blocks across principal passages. Short
one-block approaches beside bedrolls in the Taiga and ocean compact rooms are
secondary access spaces. A clear route can cross the open court, but furniture
and posts cannot occupy its marked 4 × 4 center. Every courtyard preserves that
center outside the projected roof footprints.

The wet-site courtyard drawings use a protected refuge within the boundary.
They do not place an outward exit into the water. The ocean compact drawing
reserves two-block east and aft deck walks; it does not promise an exterior
loop around every cabin side.

## Roofs and climate

Roof plans identify each roof volume and its approximate ridge direction.
Overlapping outlines show where a later block design must resolve a junction.
They are not additional floors. Desert parapets remain decorative, with no
roof access. Savanna hips use lower ridges to preserve the broad silhouette.
Taiga and Snowy roofs reserve more height for steep profiles.

The roof outline must eventually leave three full air blocks above ordinary
routes. Beams, lantern supports, slabs, snow, doors, and shutters need a
three-dimensional clearance check. Lantern symbols mark proposed locations;
they do not prove sufficient light levels or valid attachments.

## Ocean and raised entrances

The Tidal Towns section uses a local comparison datum with the road campfire
block based at Y = 2. Minecraft 1.20.1's `CampfireBlock` shape reaches 7/16 of a
block above its base. The proposed top surfaces are therefore:

| Surface | Local top height | Rise from previous surface |
| --- | --- | --- |
| Unlit campfire road | 2 + 7/16 | — |
| Half-slab transition | 2 + 1/2 | 1/16 |
| Full-block deck | 3 | 1/2 |

This defines a candidate connection, not a measured final village elevation.
The full approach, neighboring road blocks, jigsaw final state, rotations, and
actual guard walking need to be checked together in a naturally generated
Tidal Towns village. The water line in the section is schematic. Raft members
have finite depth; they do not extend automatically to the seabed.

The jungle and swamp sections similarly propose two half-block rises from a
road datum at Y = 1 to a full floor at Y = 2. Actual Moog's Missing Villages
roads can use stair final states. The final connector offset must come from a
site trial. Short stone supports must meet the ground or use a deliberate
wet-site foundation; a raised floor alone does not solve uneven terrain.

The reference artifacts for these proposals are Tidal Towns 1.3.4 and Moog's
Missing Villages 2.1.1 for Minecraft 1.20. The inspected Tidal Towns structure
is `joshie:village_ocean`; its house connectors use
`minecraft:building_entrance`. The inspected MMV jungle and swamp roads use
`mmv:building_entrance`. Those distinctions belong in the later connector
implementation; the diagrams do not change the existing compiler.

## Review before block construction

Start with the floor plans and silhouettes, especially the Snowy rear hall,
Savanna roof proportions, and ocean deck size. Then draw the actual facade
openings, roof junctions, window screens, furniture blocks, entrance banner,
rail corners, and lantern attachments. Repeat the clearance review after those
details exist. Decorative beds, containers, medical services, new loot, and
additional guard spawns remain outside this architectural change.

The drawing checks cover occupied-cell overlap, floor support in plan,
reachability with doors notionally open, bedroll access, reserved standing
positions, envelope bounds, and an unroofed court center. They do not model
block collision shapes, vertical movement, sight lines, gun clearance, crowds,
casualty transport, night lighting, or natural terrain placement.

After the geometry is approved, build preview templates and test them with
packaged mod jars in ordinary Minecraft. Validate natural generation and the
specific village-mod connectors as well as direct placement. The existing
[Plains preview](barracks-designs.md) remains the starting reference for that
work.

## Drawing index

Each drawing is a standalone vector image. Floor-plan symbols are explained
above; roof numbers identify the individual volumes on each roof sheet.

| Layout | Floor | Roof | Front | East | Section |
| --- | --- | --- | --- | --- | --- |
| Plains / Compact | [floor](designs/barracks/plains-compact-floor.svg) | [roof](designs/barracks/plains-compact-roof.svg) | [front](designs/barracks/plains-compact-front.svg) | [side](designs/barracks/plains-compact-side.svg) | [section](designs/barracks/plains-compact-section.svg) |
| Plains / Courtyard | [floor](designs/barracks/plains-courtyard-floor.svg) | [roof](designs/barracks/plains-courtyard-roof.svg) | [front](designs/barracks/plains-courtyard-front.svg) | [side](designs/barracks/plains-courtyard-side.svg) | [section](designs/barracks/plains-courtyard-section.svg) |
| Desert / Compact | [floor](designs/barracks/desert-compact-floor.svg) | [roof](designs/barracks/desert-compact-roof.svg) | [front](designs/barracks/desert-compact-front.svg) | [side](designs/barracks/desert-compact-side.svg) | [section](designs/barracks/desert-compact-section.svg) |
| Desert / Courtyard | [floor](designs/barracks/desert-courtyard-floor.svg) | [roof](designs/barracks/desert-courtyard-roof.svg) | [front](designs/barracks/desert-courtyard-front.svg) | [side](designs/barracks/desert-courtyard-side.svg) | [section](designs/barracks/desert-courtyard-section.svg) |
| Savanna / Compact | [floor](designs/barracks/savanna-compact-floor.svg) | [roof](designs/barracks/savanna-compact-roof.svg) | [front](designs/barracks/savanna-compact-front.svg) | [side](designs/barracks/savanna-compact-side.svg) | [section](designs/barracks/savanna-compact-section.svg) |
| Savanna / Courtyard | [floor](designs/barracks/savanna-courtyard-floor.svg) | [roof](designs/barracks/savanna-courtyard-roof.svg) | [front](designs/barracks/savanna-courtyard-front.svg) | [side](designs/barracks/savanna-courtyard-side.svg) | [section](designs/barracks/savanna-courtyard-section.svg) |
| Taiga / Compact | [floor](designs/barracks/taiga-compact-floor.svg) | [roof](designs/barracks/taiga-compact-roof.svg) | [front](designs/barracks/taiga-compact-front.svg) | [side](designs/barracks/taiga-compact-side.svg) | [section](designs/barracks/taiga-compact-section.svg) |
| Taiga / Courtyard | [floor](designs/barracks/taiga-courtyard-floor.svg) | [roof](designs/barracks/taiga-courtyard-roof.svg) | [front](designs/barracks/taiga-courtyard-front.svg) | [side](designs/barracks/taiga-courtyard-side.svg) | [section](designs/barracks/taiga-courtyard-section.svg) |
| Snowy / Compact | [floor](designs/barracks/snowy-compact-floor.svg) | [roof](designs/barracks/snowy-compact-roof.svg) | [front](designs/barracks/snowy-compact-front.svg) | [side](designs/barracks/snowy-compact-side.svg) | [section](designs/barracks/snowy-compact-section.svg) |
| Snowy / Courtyard | [floor](designs/barracks/snowy-courtyard-floor.svg) | [roof](designs/barracks/snowy-courtyard-roof.svg) | [front](designs/barracks/snowy-courtyard-front.svg) | [side](designs/barracks/snowy-courtyard-side.svg) | [section](designs/barracks/snowy-courtyard-section.svg) |
| Deep ocean · Tidal Towns / Compact | [floor](designs/barracks/ocean-compact-floor.svg) | [roof](designs/barracks/ocean-compact-roof.svg) | [front](designs/barracks/ocean-compact-front.svg) | [side](designs/barracks/ocean-compact-side.svg) | [section](designs/barracks/ocean-compact-section.svg) |
| Deep ocean · Tidal Towns / Courtyard | [floor](designs/barracks/ocean-courtyard-floor.svg) | [roof](designs/barracks/ocean-courtyard-roof.svg) | [front](designs/barracks/ocean-courtyard-front.svg) | [side](designs/barracks/ocean-courtyard-side.svg) | [section](designs/barracks/ocean-courtyard-section.svg) |
| Jungle · MMV / Compact | [floor](designs/barracks/jungle-compact-floor.svg) | [roof](designs/barracks/jungle-compact-roof.svg) | [front](designs/barracks/jungle-compact-front.svg) | [side](designs/barracks/jungle-compact-side.svg) | [section](designs/barracks/jungle-compact-section.svg) |
| Jungle · MMV / Courtyard | [floor](designs/barracks/jungle-courtyard-floor.svg) | [roof](designs/barracks/jungle-courtyard-roof.svg) | [front](designs/barracks/jungle-courtyard-front.svg) | [side](designs/barracks/jungle-courtyard-side.svg) | [section](designs/barracks/jungle-courtyard-section.svg) |
| Swamp · MMV / Compact | [floor](designs/barracks/swamp-compact-floor.svg) | [roof](designs/barracks/swamp-compact-roof.svg) | [front](designs/barracks/swamp-compact-front.svg) | [side](designs/barracks/swamp-compact-side.svg) | [section](designs/barracks/swamp-compact-section.svg) |
| Swamp · MMV / Courtyard | [floor](designs/barracks/swamp-courtyard-floor.svg) | [roof](designs/barracks/swamp-courtyard-roof.svg) | [front](designs/barracks/swamp-courtyard-front.svg) | [side](designs/barracks/swamp-courtyard-side.svg) | [section](designs/barracks/swamp-courtyard-section.svg) |

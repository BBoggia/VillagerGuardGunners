# Barracks architectural details

This design pass specifies the exterior openings, timber and masonry trim,
decorative furniture, door leaves, lantern supports, and roof courses for all
sixteen regional Barracks proposals. It develops the
[schematic drawing review](barracks-drawing-review.md) into a detail schedule for
building the preview templates. The [regional preview](regional-barracks-preview.md)
now implements these proposals in a separate datapack. The mod and the existing
Plains preview remain unchanged.

The [detail data](designs/barracks/details/details.json) records each placement
and its proposed Minecraft block. This is a design document, not a format read
by the mod. Its `baseDrawingSha256` identifies the exact schematic data used.
The drawings are block-course proposals, not screenshots from Minecraft.

## Regional character

| Setting | Walls and frame | Openings and exterior detail | Roof and interior detail |
| --- | --- | --- | --- |
| Plains | White terracotta between stripped oak posts; cobblestone base. | Two-block-high glass bays, oak shutter panels, timber window heads, green entrance banner. | Spruce courses with dark oak verges; oak tables, benches, and shelves. |
| Desert | Cut sandstone with smooth sandstone corner and lintel bands. | High one-block glazing slits; deep-looking reveals made within the existing wall cells; yellow banner. | Low stepped flat roofs with half-block parapets; acacia furniture. |
| Savanna | Terracotta panels between stripped acacia posts. | Wide glass bays, acacia shutter panels, open-looking shaded frontage, orange banner. | Shallow acacia hips; acacia furniture and broad trim. |
| Taiga | Spruce plank panels between stripped spruce posts; restrained stone weathering. | Tall glazed bays, substantial timber jambs, spruce shutters, green banner. | Steep spruce courses with dark verges; spruce furnishings. |
| Snowy | Pale terracotta, spruce posts, stone-brick base. | Small glazed bays, offset paired doors in the lobby, restrained projecting trim, red banner. | Steep spruce roofs; shelves fixed to the solid wall or header above each sleeping place. |
| Deep ocean / Tidal Towns | Oak cabin panels and stripped oak framing on the raft. | Horizontal glazing, spruce shutter panels, protected deck boundaries, cyan banner. | Low spruce roofs and spruce furnishings; lantern beams stay within the roof footprints. |
| Jungle / Moog's Missing Villages | Jungle planks and stripped jungle posts with a mossy stone base. | Taller glass bays with limited bamboo shutter accents; green banner. | Deep jungle-wood roof verges; jungle furniture. Keep vegetation outside circulation. |
| Swamp / Moog's Missing Villages | Mud-brick panels and stripped dark oak posts on finite stone supports. | Small glazed bays, spruce shutter panels, protected boardwalk edges, purple banner. | Dark oak roof courses and spruce furniture; moisture detail stays near the base. |

Glazing closes every scheduled window bay. The screened appearance in the
Savanna and Jungle comes from framing and exterior panels; these windows are
not additional movement openings. Shutters only appear where their complete
reserved cells fit outside the routes, courtyard core, and guard positions.
Wet-site facades consequently have fewer projecting panels beside narrow
walks.

The four decorative bedrolls use white carpet pillows and regional-color
blankets. These are proposed textile accents, not extra functional beds.

## Coordinate and placement conventions

All coordinates are local X, Y, Z. X increases west to east, Z increases from
the front toward the rear, and `floorY` is the guards' standing level. The
supporting floor block occupies `floorY - 1`. Rectangles use
`[x, z, width, depth]` with exclusive upper endpoints. Roof surface records use
`[x, z, topY]`; roof columns use `[x, z, bottomY, topY]`.

The footprints remain the revised schematic envelopes: 15 × 13 for Savanna
compact, 16 × 15 for ocean compact, and 16 × 16 for every courtyard. The Snowy
courtyard retains its 3 × 3 gate lobby, 4 × 4 open court, and shallow rear hall.

Use the following placement order when constructing a preview:

1. Full supporting floors, finite foundations, enclosed walls, and existing
   boundary cells.
2. Corner posts, horizontal frame bands, window jambs, and support columns.
3. Window bays, door openings, and their lintels. Openings take priority over
   decorative frame patterns.
4. Visible roof columns and the risers that close their junctions. Omit hidden
   lower roofs in overlaps.
5. Full lantern support beams, replacing roof infill at their scheduled cells.
6. Door leaves, glazing, shutters, banner, lanterns, furniture, and shelves.

Every full-height lantern anchor column occupies an existing wall or boundary
cell. It must remain solid through the scheduled beam height. No such column
crosses a scheduled window or door bay.

## Windows, shutters, and trim

Each window record names its face, wall rectangle, bottom Y, height, sill Y,
upper lintel Y, and glazing block. The face identifies the outward direction.
The bay replaces wall cells; it does not add a second layer inside the room.
Keep full framing blocks at the corners and a continuous band at
`floorY + 3`, except where an explicit opening takes priority.

Use the regional frame for solid jamb cells immediately beside glazing. Desert
jambs and heads use smooth sandstone. Where two trim rules meet, place one
block. Do not widen the wall or shrink the window to accommodate both rules.

Shutter records specify their own outward cell and adjacent supporting wall
cell. Use the named wooden trapdoor with `open=true`, `half=bottom`,
`powered=false`, and `waterlogged=false`; its facing equals the window's
outward direction. This is the upright decorative position against the wall.
Repeat it vertically for the scheduled height. Treat its entire cell as
reserved during architectural checks, even though the upright shape is thin.
Players can still interact with ordinary wooden trapdoors; both positions need
a native check before release.

The banner has one exact attachment coordinate and a supporting wall/post
coordinate. Use the regional wall banner facing outward. The Snowy courtyard
banner moves to the outer side of the gate corner so it stays inside the
16-block envelope. Its support is part of the gate frame.

Verge trim replaces the outer roof course with the regional trim material; it
does not add another block of overhang. Base weathering stays in the lowest
masonry course. Snow, vines, roots, and hanging vegetation need a later site
and weather trial before they can be added around an entrance.

## Doors and usable width

Every paired doorway now has explicit left and right leaves. Both face north
in the unrotated drawing. The west leaf has `hinge=left`; the east leaf has
`hinge=right`. Each has matching lower and upper halves, starts closed and
unpowered, and stands on a full floor block. Rotate both facing and position
when constructing rotated previews. The door lintel starts at `floorY + 2`.

Minecraft 1.20.1's `DoorBlock` shape uses leaves 3/16 of a block thick. For this
pair, the open leaves lie along the outer jambs. The nominal two-block opening
therefore has **1 5/8 blocks of clear width**, with two blocks of clear height.
The source check establishes this geometric width; it does not establish
whether two guards or a casualty carrier can pass together.

Keep the route on both sides two blocks wide. Interior arches and the
supported secondary exits retain their existing open treatment; their lintels
start at `floorY + 3`. The Snowy lobbies keep their offset inner and outer
pairs. Test ordinary guard traffic through those doors before deciding whether
to widen an opening or substitute an arch in a later design.

## Furniture and equipment decoration

Each layout retains four bedrolls, one common table, one crafting table, and
one bench group. The schedules give the actual blocks within the previously
reserved footprints:

| Detail | Construction |
| --- | --- |
| Bedroll | White carpet at the head, regional carpet at the foot; no bed block. |
| Common table | Regional wooden stairs with `half=top` and `shape=straight`, forming a continuous flat top and recessed underside. |
| Bench | Regional wooden stairs with `half=bottom`, oriented with their backs toward the nearest wall. |
| Work surface | One crafting table in the existing work position. |
| Personal shelf | A regional top slab above each bedroll footprint, touching a solid wall or header cell. |

Shelf Y is chosen separately where a window occupies the preferred attachment.
Those shelves attach higher, at the solid header, instead of touching a glass
pane. They stay over the bedroll footprint and add no floor obstacle.

Use the framed wall bands as equipment-board decoration. This pass adds no
barrels, chests, item frames with weapons, armor stands, extra chairs, or loose
floor crates. Those objects would require additional space and behavior
choices. Tables and shelves use ordinary building blocks and have no inventory.

## Lanterns and support beams

Each lantern record gives its block, `hanging=true`, `waterlogged=false`, and
a full supporting block directly above it. A beam record lists the blocks
connecting that support to a solid wall or boundary post. Where a short beam
turns, its log axes follow the two runs. The wall anchor column is also named
explicitly.

All lantern block coordinates begin at least three blocks above `floorY`.
Beams and support blocks sit above that clearance. Low porch and shade roof
courses rise where needed to cover the beam. This is a change from the first
mass drawings, whose low roof outlines did not reserve those supporting
blocks. It fits within every existing height envelope.

The support rule was checked against the local Minecraft 1.20.1
`LanternBlock` source: a hanging lantern checks support above. The proposed
full blocks provide the intended support geometry. Native survival, rotated
placement, neighboring roof states, and final light levels still need the
packaged-game preview.

## Roof courses and junctions

Each roof retains its regional shape and footprint. The detailed schedule
quantizes its surface to half-block heights. Gables rise across X with their
ridge along Z; hips also fall toward the front and rear. Shade roofs use low
courses. Desert roofs use a flat surface with a half-block perimeter parapet,
rather than filling the whole former parapet envelope with solid roof blocks.

`roofTop` identifies the visible height and owning roof at every covered X/Z
cell. The highest scheduled surface owns an overlap. The earlier roof in the
list wins equal-height ties. `junctions` lists each intersecting pair and the
resulting owner of every shared cell. Concealed lower roofs are removed from
that overlap, including any hidden parapets.

`roofColumns` gives the bottom and top of the visible roof material. The
bottom extends to half a block below the lowest adjacent scheduled top. This
closes the vertical riser where courses step up and makes adjacent columns
overlap by at least half a block. The rule also seals the seam between two
roof owners. It does not extend a roof into the open court.

Realize those intervals on a half-block grid. A lower half uses a bottom slab;
an upper half uses a top slab. Two filled halves in one cell use a full block
or double slab of the same material. Full timber support beams take priority
at their listed cells, and the top schedule includes their caps. Use the
regional roof material for the surface and the named infill for the risers.
Verge trim changes material only. For Desert, add a half-block parapet along
exposed perimeter edges, excluding covered seams.

This gives a specified course profile without relying on automatic stair
corner changes. A later visual trial can refine selected verges with stairs,
but that is optional finishing work rather than a missing roof-junction rule.
Roof corners, wall-to-roof closure, and weather exposure must still be checked
in the assembled preview.

## Guard positions and verification limits

The detailed drawings label the center, west, and east assembly duties as
A, 1, and 2. P is the fourth possible initial guard cell and has no additional
assembly duty in this proposal. S is the separate entry sentry duty. This
matches the center/west/east duty arrangement of the existing Plains preview;
it corrects the first drawings' ambiguous “1–3 assembly” legend.

The detail checks cover wall/window/door intersections, shelf attachments,
beam-column attachments, roof height envelopes, roof space above lanterns,
reserved route and duty cells, reachable furniture approaches and exits, and
the uncovered court. Shutter cells are treated conservatively as full floor
obstacles. They preserve the current four possible initial spawn cells and
the existing architectural floor footprints.

These checks are design-space checks. They do not run Minecraft navigation,
block-neighbor updates, natural jigsaw placement, combat, weather, or casualty
transport. Before the new templates enter village pools, build a preview pack
and test the actual packaged jars in normal Forge. Include all four rotations,
attachment survival, doors in use, group traffic, night lighting, live firing
lines, terrain support, and naturally generated Tidal Towns and MMV joins.

The entrance/foundation sections in the schematic review remain proposals.
This detail pass does not settle the Tidal Towns road height, MMV stair final
states, generation weights, or foundation rejection rules. Those belong to
template integration and natural-site testing.

## Detail drawing index

The four facades, detail floor plan, and roof ownership plan are saved as
standalone vector drawings. The coordinate schedules are in the detail data.

| Layout | Front | East | Rear | West | Floor | Roof |
| --- | --- | --- | --- | --- | --- | --- |
| Plains / Compact | [front](designs/barracks/details/plains-compact-north.svg) | [east](designs/barracks/details/plains-compact-east.svg) | [rear](designs/barracks/details/plains-compact-south.svg) | [west](designs/barracks/details/plains-compact-west.svg) | [floor](designs/barracks/details/plains-compact-plan.svg) | [roof](designs/barracks/details/plains-compact-roof.svg) |
| Plains / Courtyard | [front](designs/barracks/details/plains-courtyard-north.svg) | [east](designs/barracks/details/plains-courtyard-east.svg) | [rear](designs/barracks/details/plains-courtyard-south.svg) | [west](designs/barracks/details/plains-courtyard-west.svg) | [floor](designs/barracks/details/plains-courtyard-plan.svg) | [roof](designs/barracks/details/plains-courtyard-roof.svg) |
| Desert / Compact | [front](designs/barracks/details/desert-compact-north.svg) | [east](designs/barracks/details/desert-compact-east.svg) | [rear](designs/barracks/details/desert-compact-south.svg) | [west](designs/barracks/details/desert-compact-west.svg) | [floor](designs/barracks/details/desert-compact-plan.svg) | [roof](designs/barracks/details/desert-compact-roof.svg) |
| Desert / Courtyard | [front](designs/barracks/details/desert-courtyard-north.svg) | [east](designs/barracks/details/desert-courtyard-east.svg) | [rear](designs/barracks/details/desert-courtyard-south.svg) | [west](designs/barracks/details/desert-courtyard-west.svg) | [floor](designs/barracks/details/desert-courtyard-plan.svg) | [roof](designs/barracks/details/desert-courtyard-roof.svg) |
| Savanna / Compact | [front](designs/barracks/details/savanna-compact-north.svg) | [east](designs/barracks/details/savanna-compact-east.svg) | [rear](designs/barracks/details/savanna-compact-south.svg) | [west](designs/barracks/details/savanna-compact-west.svg) | [floor](designs/barracks/details/savanna-compact-plan.svg) | [roof](designs/barracks/details/savanna-compact-roof.svg) |
| Savanna / Courtyard | [front](designs/barracks/details/savanna-courtyard-north.svg) | [east](designs/barracks/details/savanna-courtyard-east.svg) | [rear](designs/barracks/details/savanna-courtyard-south.svg) | [west](designs/barracks/details/savanna-courtyard-west.svg) | [floor](designs/barracks/details/savanna-courtyard-plan.svg) | [roof](designs/barracks/details/savanna-courtyard-roof.svg) |
| Taiga / Compact | [front](designs/barracks/details/taiga-compact-north.svg) | [east](designs/barracks/details/taiga-compact-east.svg) | [rear](designs/barracks/details/taiga-compact-south.svg) | [west](designs/barracks/details/taiga-compact-west.svg) | [floor](designs/barracks/details/taiga-compact-plan.svg) | [roof](designs/barracks/details/taiga-compact-roof.svg) |
| Taiga / Courtyard | [front](designs/barracks/details/taiga-courtyard-north.svg) | [east](designs/barracks/details/taiga-courtyard-east.svg) | [rear](designs/barracks/details/taiga-courtyard-south.svg) | [west](designs/barracks/details/taiga-courtyard-west.svg) | [floor](designs/barracks/details/taiga-courtyard-plan.svg) | [roof](designs/barracks/details/taiga-courtyard-roof.svg) |
| Snowy / Compact | [front](designs/barracks/details/snowy-compact-north.svg) | [east](designs/barracks/details/snowy-compact-east.svg) | [rear](designs/barracks/details/snowy-compact-south.svg) | [west](designs/barracks/details/snowy-compact-west.svg) | [floor](designs/barracks/details/snowy-compact-plan.svg) | [roof](designs/barracks/details/snowy-compact-roof.svg) |
| Snowy / Courtyard | [front](designs/barracks/details/snowy-courtyard-north.svg) | [east](designs/barracks/details/snowy-courtyard-east.svg) | [rear](designs/barracks/details/snowy-courtyard-south.svg) | [west](designs/barracks/details/snowy-courtyard-west.svg) | [floor](designs/barracks/details/snowy-courtyard-plan.svg) | [roof](designs/barracks/details/snowy-courtyard-roof.svg) |
| Deep ocean · Tidal Towns / Compact | [front](designs/barracks/details/ocean-compact-north.svg) | [east](designs/barracks/details/ocean-compact-east.svg) | [rear](designs/barracks/details/ocean-compact-south.svg) | [west](designs/barracks/details/ocean-compact-west.svg) | [floor](designs/barracks/details/ocean-compact-plan.svg) | [roof](designs/barracks/details/ocean-compact-roof.svg) |
| Deep ocean · Tidal Towns / Courtyard | [front](designs/barracks/details/ocean-courtyard-north.svg) | [east](designs/barracks/details/ocean-courtyard-east.svg) | [rear](designs/barracks/details/ocean-courtyard-south.svg) | [west](designs/barracks/details/ocean-courtyard-west.svg) | [floor](designs/barracks/details/ocean-courtyard-plan.svg) | [roof](designs/barracks/details/ocean-courtyard-roof.svg) |
| Jungle · MMV / Compact | [front](designs/barracks/details/jungle-compact-north.svg) | [east](designs/barracks/details/jungle-compact-east.svg) | [rear](designs/barracks/details/jungle-compact-south.svg) | [west](designs/barracks/details/jungle-compact-west.svg) | [floor](designs/barracks/details/jungle-compact-plan.svg) | [roof](designs/barracks/details/jungle-compact-roof.svg) |
| Jungle · MMV / Courtyard | [front](designs/barracks/details/jungle-courtyard-north.svg) | [east](designs/barracks/details/jungle-courtyard-east.svg) | [rear](designs/barracks/details/jungle-courtyard-south.svg) | [west](designs/barracks/details/jungle-courtyard-west.svg) | [floor](designs/barracks/details/jungle-courtyard-plan.svg) | [roof](designs/barracks/details/jungle-courtyard-roof.svg) |
| Swamp · MMV / Compact | [front](designs/barracks/details/swamp-compact-north.svg) | [east](designs/barracks/details/swamp-compact-east.svg) | [rear](designs/barracks/details/swamp-compact-south.svg) | [west](designs/barracks/details/swamp-compact-west.svg) | [floor](designs/barracks/details/swamp-compact-plan.svg) | [roof](designs/barracks/details/swamp-compact-roof.svg) |
| Swamp · MMV / Courtyard | [front](designs/barracks/details/swamp-courtyard-north.svg) | [east](designs/barracks/details/swamp-courtyard-east.svg) | [rear](designs/barracks/details/swamp-courtyard-south.svg) | [west](designs/barracks/details/swamp-courtyard-west.svg) | [floor](designs/barracks/details/swamp-courtyard-plan.svg) | [roof](designs/barracks/details/swamp-courtyard-roof.svg) |

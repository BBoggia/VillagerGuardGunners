# Village Defense Buildings Step 9: integrated Village Watchtower

Step 9 completes the compact village-integrated Watchtower that was first
made functional through Step 6's shared garrison lifecycle. It is a 7x9x7,
road-connected village jigsaw piece with five biome palettes, an enclosed
lower level, an elevated observation platform, and one continuous internal
ladder back to ground level.

This is deliberately independent from the standalone 1.4 Watchtower. The
integrated tower uses the `watchtower/<biome>` village-building definitions,
`village_watchtower` guard group, village-building UUID domain, village master
switch, and normal village jigsaw pools. It never creates the standalone
structure start, `watchtower` guard group, squad-locked Armory, tower loot, or
standalone marker state.

## Staffing and access

The existing difficulty-aware group remains bounded to one guard on Easy, one
or optionally two on Normal, and two on Hard. Both possible occupants spawn on
the upper platform five blocks above the persistent building anchor. The
platform cells have solid floors and two blocks of collision-free headroom in
every rotation.

The source template now requires an uninterrupted six-block ladder column.
Every ladder segment has a solid backing block, including the upper landing;
the build fails before NBT generation if either continuity or support is lost.
This closes a template issue where the previous upper segment could lose
support after a neighbor update and leave the garrison without a reliable
route to ground level.

## Runtime boundary

The integrated Watchtower has no Armory, loot inventory, block-entity ticker,
training controller, entity scan, chunk ticket, forced load, repair pass, or
respawn manager. Its one-shot anchor and garrison work reuse the existing
Step 4 and Step 6 paths. Once initialized, the building itself contributes
zero recurring work.

## Validation

The build-time generator locks the compact dimensions, continuous supported
ladder, and two safe upper cells. A Forge GameTest loads all five biome
variants and places each under all four rotations. The 20-case matrix verifies
the biome road connector, safe upper guard cells, live ladder survival, and
absence of an Armory block. The ordinary group tests retain the intrinsic
two-guard ceiling and independent `village_watchtower` datapack identity.

Natural road attachment, terrain blending, villager navigation, and visual
review remain manual release checks; rotated fixture placement is not treated
as proof of every naturally generated village layout.

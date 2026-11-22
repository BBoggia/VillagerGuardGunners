# Village Watchtowers

Villager Guard Gunners can generate rare standalone Village Watchtowers in
suitable village-compatible Overworld biomes.

These Watchtowers are independent structures. They are not inserted into
vanilla village roads or building pools, and they generate only in newly
explored chunks.

## Structure

Each tower uses a compact stone-and-oak design with a lower guardroom, internal
route, observation deck, railings, and roof. Placement supports all four
rotations and checks representative terrain points before accepting a start.

A tower is rejected when the complete structure would exceed build height or
when its checked terrain contains water, unsupported frozen surfaces, or a
slope beyond the configured limit.

## Guards and Loadouts

An enabled tower may create up to four persistent Guard Villagers. The selected
group depends on the world difficulty:

- Peaceful creates no guards.
- Easy favors smaller rifleman groups.
- Normal can add marksman and support positions.
- Hard can add support, breacher, and demolitions positions.

Tower members use the existing natural-loadout, role, squad, ammunition, and
TaCZ equipment systems. Server configuration controls the guard limit and a
tower-only multiplier for the normal natural-gun chance. Existing nearby caps,
inventory checks, TaCZ compatibility, and explosive restrictions still apply.

Datapacks define the available tower groups, guard roles, and weapon pools.
Invalid group data keeps the last working set active.

## Generated Village Armory

An enabled tower can place a stocked Village Armory associated with its
generated squad. Initial ammunition is selected from datapack loot suited to
the built-in rifleman, marksman, support, breacher, and demolitions roles.

The Armory retains normal storage, comparator, automation, access, and guard
resupply behavior. If a tower creates no usable squad, its one-time setup makes
the Armory public instead of leaving it locked to an unavailable group.

## Persistence and Player Changes

Generated identities prevent the same tower work from running more than once.
After a tower is completed, revisiting its structure pieces does not:

- respawn a removed guard;
- reverse a manual squad change;
- rebuild a broken or replaced Armory;
- reroll the Armory's initial stock; or
- add a recurring tower manager, repair pass, or forced chunk.

Unreadable or future-version tower records remain inactive rather than being
executed or rewritten.

## Configuration and Datapacks

Fzzy Config provides controls for:

- new Watchtower starts;
- guard creation;
- the maximum guards per tower;
- generated Armory placement;
- initial Armory stock; and
- the tower-only natural-gun chance multiplier.

Datapacks control biome selection, spacing, structure settings, the physical
template, guard groups, role-specific loadout pools, and generated Armory
stock. Removing a datapack definition cannot be reversed by server
configuration.

## Operator Commands

Permission-level-2 operators can use:

- `/vgg watchtowers status`
- `/vgg watchtowers groups [page]`
- `/vgg watchtowers inspect <guard>`
- `/vgg watchtowers errors [page]`

These commands provide configuration and loaded-record diagnostics without
locating structures, loading chunks, or changing a tower.

The complete manual validation cases are recorded in the
[Village Watchtowers release matrix](village-watchtowers-release-matrix.md).

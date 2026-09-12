# Village Defense Building balance

Village Defense Buildings use conservative relative weights so new villages can
gain useful defenses without replacing their normal houses and professions.
The system adds entries only during server startup and then relies on
Minecraft's normal weighted jigsaw selection. It does not scan villages,
guarantee a building, or run a population controller.

## Built-in relative weights

Each supported plains, desert, savanna, snowy, and taiga houses pool receives
the same five entries:

| Building | Relative weight |
| --- | ---: |
| Sentry Post | 2 |
| Barracks | 1 |
| Village Armory | 1 |
| Integrated Village Watchtower | 1 |
| Training Yard | 1 |

The five entries have a combined relative weight of six. A Sentry Post is
twice as likely as any one other Villager Guard Gunners building when one of
these entries is selected. This does not mean it has a fixed chance to appear:
the effective chance also depends on the existing entries in the target pool,
the village's jigsaw expansion, terrain, depth, and other mods or datapacks.

The compact 7 x 7 x 8 Sentry Post receives the extra weight because its open,
shallow checkpoint plan remains unlikely to crowd a village. Much of its
footprint is a covered road apron, alternating solid and low defensive cover,
or observation clearance rather than enclosed floor area. Its crafting bench
offers ordinary player utility without creating another villager job site or
loot source.
Larger buildings and the loot-bearing Village Armory retain weight one.

## Regional pack frequency

Installing a vanilla regional family pack now gives its larger layouts a
`selection_chance` of `0.5` and reduces that family's built-in entries to
`0.25`. Relative weights stay unchanged. For example, the Barracks pack
adjusts only the five built-in Barracks definitions; it does not adjust
Sentry Posts or another mod's buildings. The base mod by itself retains its
existing defaults.

The pack copies the generated built-in definitions and changes only their
selection chance. These replace the same definition IDs, so they do not add
to the definition count. Packs that override those IDs can compete by
resource priority; pack authors should combine their intended settings in
the winning definition.

Land variants also use [terrain screening](village-building-terrain-safety.md).
It can reject a site after candidate selection. Tidal Towns, MMV, CTOV, and
Sky Villages retain their earlier selection settings. These choices do not
impose a village-wide building cap or guarantee a specific layout.

## Natural-generation comparison

Packaged Minecraft 1.20.1 / Forge 47.4.10 dedicated-server trials compared the
same 25 Plains village locations in each installation, using seed 314159 and
all five vanilla regional packs. Each installation was measured with the
previous packs, terrain screening alone, and the combined terrain/frequency
profile above. The table shows selected piece counts across each 25-village
sample, including the base mod's buildings.

| Installation | Previous total / regional | Terrain only total / regional | Updated total / regional | Largest village, previous → updated |
| --- | ---: | ---: | ---: | ---: |
| Vanilla villages | 81 / 50 | 73 / 42 | 64 / 55 | 6 → 6 |
| Better Villages 3.3.1 | 237 / 6 | 235 / 1 | 77 / 2 | 23 → 10 |
| Lios Overhauled Villages 0.1.1 | 119 / 26 | 113 / 20 | 53 / 26 | 10 → 4 |
| Better Villages and Lios together | 132 / 24 | 125 / 17 | 51 / 28 | 9 → 4 |

The profile reduces the small built-in buildings that dominated the Better
Villages sample. Larger regional buildings remain rare there because of
terrain and available street-end space. Increasing admission chance cannot
overcome those constraints. The other three samples preserve or increase the
regional designs' representation while reducing total building counts.

These are 300 village observations across three profiles, not 300 independent
villages or a probability guarantee. The inventories describe selected
structure pieces. They do not establish physical access at every site,
comfortable spacing, total guards, or recovered loot. Additional seeds and
non-Plains biomes still need review before enabling the system by default.

`tools/report_village_building_frequency.py` summarizes complete 25-village
inventories from the packaged-game observer. It rejects incomplete inventories
and searches that selected a target layout; those would bias frequency counts.

## Population bounds

Each staffed building creates at most one difficulty-aware group. The global
`villageBuildings.maximumGuardsPerBuilding` setting can reduce any group below
these built-in limits.

| Building | Built-in guard limit |
| --- | ---: |
| Barracks | 4 |
| Village Armory | 0 |
| Sentry Post | 1 |
| Integrated Village Watchtower | 2 |
| Training Yard | 2 |

If a generated village happened to contain exactly one of every built-in
building, its four staffed buildings would request the following combined
population. These are review bounds, not a guaranteed per-village population.

| Difficulty | Minimum guards | Maximum guards |
| --- | ---: | ---: |
| Easy | 4 | 6 |
| Normal | 6 | 8 |
| Hard | 8 | 9 |

The existing natural-loadout rules separately cap nearby guards that receive
guns. The default nearby caps are one on Easy and two on Normal or Hard inside
the configured 64-block radius. Reaching that limit does not delete a generated
guard; it prevents another natural gun loadout from being issued. This keeps
armed-guard density bounded even when a village happens to select several
staffed buildings.

Integrated Watchtower occupants resolve their final one-time loadout in the
lower ready room. Guards holding a TaCZ gun or crossbow take the corresponding
upper-deck position; guards without a supported ranged weapon remain below.
This decision adds no recurring equipment check and does not move guards again
when players later replace their weapons.

## Village Armory reward

A built-in Village Armory starts with seven half-stacks of loose TaCZ
ammunition, one for each supported built-in caliber, for a maximum of 224
rounds. It contains no gun. The building retains relative weight one, and the
existing configuration can disable its initial stock or the Armory block
without disabling other Village Defense Buildings.

This supply is intentionally spread across calibers, so only part of it will
usually match a particular guard or player weapon. Its gameplay value and
frequency still require natural-generation review before release.

## Performance and customization

Changing the built-in weights changes only the size of Minecraft's existing
flattened selection list: five raw entries and six weighted selections are
added to each supported pool. No new tick handler, entity query, village scan,
chunk ticket, or persistent population manager is introduced.

Pack authors can replace the format-1 building definitions with weights from
one through five. Server operators can also disable individual building types,
garrisons, or Armory stock. Datapack definition changes are validated by
`/reload`, but a server restart is required before they change live village
pools.

Before enabling Village Defense Buildings by default, review at least 20 to 30
new villages across several seeds and all supported biome styles. Record the
number and type of defense buildings, total and armed guards by difficulty,
road or farm obstruction, large-building crowding, and recovered Armory loot.
Also repeat the review with representative village-overhaul mods because their
pool weights change the effective frequency.

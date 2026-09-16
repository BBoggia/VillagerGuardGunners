# Villager Guard Gunners

Villager Guard Gunners lets you equip Guard Villagers with Timeless and
Classics Zero (TaCZ) guns, ammo, and sidearms.

Guards can aim, move, fire, reload, use backup pistols, follow individual
orders, join squads, restock from Village Armories, and protect their allies.

## Features

- TaCZ gun aiming, firing, reloads, magazines, fire modes, attachments, sounds,
  and effects.
- Weapon-aware movement and aiming for pistols, SMGs, rifles, shotguns, sniper
  rifles, machine guns, and launchers.
- Configurable [guard aim error and target point](docs/configuration.md#guard-accuracy-and-player-combat),
  with optional separate settings against players.
- Three persistent ammo slots for loose ammo and ammo boxes.
- Optional sidearms for empty, restricted, or unsafe primary weapons.
- Quick ammo deposits, nearby drop pickup, reusable ammo-box preservation, and
  emergency ammo sharing.
- Per-guard rules of engagement, tactics, explosive permission, ammo
  behavior, movement, and firing range.
- Reusable Orders Ledgers for copying behavior without copying equipment or
  ownership.
- Squads and temporary Rally, Defend, Hold, Focus Target, Fall Back, and Resume
  Orders commands.
- Saved squad patrol routes authored with the Squad Command Ledger.
- Craftable Village Armories with hopper and pipe support, squad access, and
  automatic guard resupply.
- Craftable Quartermaster stations with working villagers, opt-in equipment
  profiles, protected player equipment, and linked Village Armory supplies.
- Difficulty-scaled natural guard loadouts controlled by configuration and
  datapacks.
- Standalone Village Watchtowers with generated squads and stocked Armories.
- Optional Village Defense Buildings integrated into vanilla village pools.
- Optional regional building packs with furnished layouts for vanilla villages,
  CTOV, Tidal Towns, Moog's Missing Villages, and Sky Villages.
- Village readiness and automatic sentry, drill, overwatch, and muster duties.
- Opt-in village population sizing and automatic patrol coverage, including
  ordinary villages without Village Defense Buildings.
- Medical pouches, supplied medic guards, and optional casualty recovery with
  Recovery Cots and visible transport.
- Friendly-fire, ally, and explosive-weapon protection.
- Clear ammo and combat status in the guard inventory.

## Requirements

Villager Guard Gunners 1.7 targets this dependency combination:

| Component | Version |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.10 or newer 47.x release |
| Guard Villagers | 1.20.1-1.6.19 |
| Timeless and Classics Zero | 1.1.8-hotfix |
| Fzzy Config | 0.7.6+1.20.1 |
| Kotlin for Forge | 4.12.0 |
| Java | 17 |

Install Guard Villagers, TaCZ, Fzzy Config, Kotlin for Forge, and Villager Guard
Gunners on both the client and server. The current build supports the version
combination listed above.

See [Quartermaster service](docs/quartermaster-service.md) for automatic
equipment stocking and station setup.
See [building pack installation](docs/building-pack-installation.md) to choose
and install the optional redesigned structures. The universal building pack
covers vanilla and supported modded villages; village mods you don't have stay
inactive. Provider-specific and individual building packs remain alternatives.

## Getting Started

1. Create a guard through normal Guard Villagers gameplay.
2. Open the guard inventory and place a TaCZ gun in its main-hand slot.
3. Add compatible loose ammo or ammo boxes to the dedicated ammo slots.
4. You can also equip a valid pistol in the sidearm slot.
5. Once the guard finds a valid target, it handles aiming, firing, reloading,
   and movement on its own.

The guard inventory shows its current ammo and combat status. Hover a
status for a plain-language explanation of conditions such as aiming, range,
visibility, friendly-fire safety, reloads, cooldowns, or empty reserves.

## Orders, Roles, and Squads

The Guard Orders panel can override an individual guard's:

- rules of engagement;
- tactical profile;
- explosive permission;
- ammo pickup and sharing;
- combat movement; and
- maximum firing range.

Each order can inherit the server default. An Orders Ledger can capture and
apply these settings without moving equipment, ammo, ownership, patrol
positions, or other guard data.

Datapack-defined guard roles provide reusable defaults beneath explicit Guard
Orders. You can assign them through natural loadouts, Orders Ledgers, or
operator commands. Roles don't grant items.

A Squad Command Ledger assigns authorized guards to a squad and issues
temporary field commands to nearby loaded members. Squad commands don't bypass
rules of engagement, ally protection, ammo requirements, or explosive
safety.

The ledger also includes a Patrol Route Editor mode. Use it on open ground to
add waypoints, crouch-use it on a block to remove the last waypoint, or use it
in the air to inspect the route. A route becomes active after its second
waypoint. Guards pause briefly at each point, skip unreachable points after a
limited number of attempts, and return to ordinary duty when every route chunk
is unloaded.

See [per-guard orders](docs/per-guard-orders.md),
[guard roles](docs/guard-roles.md), and
[squad commands](docs/squad-commands.md) for the complete behavior and
authorization rules.

## Medical supplies

Craft bandages or use drinkable healing potions to treat injured guards. Sneak-use
supplies to stock a guard's three-slot medical pouch, or use its inventory screen
to stock, treat, and pack supplies. Treatment consumes one item only when healing
succeeds. Medical supplies remain separate from guns and ammo.

Enable **Medic** in a guard inventory to treat nearby allies from its pouch.
Enable **Retreat** separately to let an injured guard seek a supplied medic.
Both choices start off and respect player orders.

Optionally enable `medical.downed` for two-dose stabilization and revival after
lethal injuries. It starts off; see [Casualty recovery](docs/casualty-recovery.md)
for treatment, timers, and bypass damage.

Enable `medical.transport` alongside downed guards to let supplied medics carry
stabilized allies to craftable Recovery Cots. Transport starts off; see
[casualty transport](docs/casualty-transport.md) for setup and route requirements.

See [Medical supplies and medic guards](docs/medical-supplies.md) for controls,
healing amounts, datapack profiles, permissions, and recovery behavior.

## Quartermaster station

Craft a Quartermaster station to organize guns, ammo, armor, and other supplies
in 27 stock slots and nine return slots. Withdraw items to equip guards through
their inventory screen. The owner can choose private, squad-creator, or public
access and enable hopper or pipe transport. Breaking the station packs its
contents and settings into one item for relocation.

See [Quartermaster stations](docs/quartermaster-station.md) for crafting,
permissions, and transport rules. A working Quartermaster can provide
[automatic equipment service](docs/quartermaster-service.md) to enrolled guards.

## Ammo and Village Armories

Authorized players can crouch-right-click an armed guard with compatible
ammo to deposit it without opening the full inventory. Guards can also
collect compatible nearby drops and make one emergency request to a nearby
ally when they cannot reload from their own reserves.

The Village Armory is a nine-slot, ammo-only container. It supports
hoppers, Forge item handlers, comparator output, persistent contents, and
optional squad access. Low-ammo guards can locate a compatible loaded Armory,
travel to it, and restock. Armories don't force chunks or scan
blocks throughout the world.

See [ammo quality of life](docs/ammunition-quality-of-life.md),
[Village Armories](docs/village-armories.md), and
[Armory resupply](docs/armory-resupply-contracts.md) for details.

## Natural Loadouts and Village Structures

Newly generated guards can receive a difficulty-scaled TaCZ gun and matching
ammo. The defaults favor pistols on Easy, add SMGs on Normal,
and allow occasional rifles on Hard. Server settings and datapacks control
which spawn sources can receive loadouts, weapon pools, local population
limits, crossbow replacement, and explosive weapons.

Rare standalone Village Watchtowers can generate in newly explored,
village-compatible Overworld biomes. A Watchtower can create a difficulty-scaled
squad and a stocked squad Armory. These towers are independent structures and
are not inserted into vanilla village roads or building pools.

The optional Village Defense Buildings system adds biome-aware Barracks,
public Armories, Sentry Posts, integrated Watchtowers, and Training Yards to
vanilla village pools. Its master switch remains off by default so server
operators can choose whether to extend village generation. Existing vanilla
and third-party pool entries are preserved. Optional [regional Barracks](docs/regional-barracks-integration.md),
[regional Armory packs](docs/regional-armory-integration.md),
[regional Sentry Post packs](docs/regional-sentry-post-integration.md),
[regional Watchtower packs](docs/regional-watchtower-integration.md), and
[regional Training Yard packs](docs/regional-training-yard-integration.md) add furnished
local designs for vanilla villages, Tidal Towns, and Moog's Missing Villages.
Additional [custom village Armory packs](docs/custom-village-armories.md) cover
selected CTOV themes and Sky Villages. The [Sky Villages building packs](docs/sky-village-defense-buildings.md)
extend floating-island placement to all five building families. Optional [CTOV Barracks](docs/custom-village-barracks.md)
add compact and courtyard layouts to six reviewed CTOV themes.
The [CTOV Sentry Post pack](docs/custom-village-sentry-posts.md) adds roadside
shelters and gateway lodges to the same themes.
The [CTOV Watchtower pack](docs/custom-village-watchtowers.md) adds compact
towers and gatehouse lookouts with two sheltered lookout positions.
The [CTOV Training Yard pack](docs/custom-village-training-yards.md) adds
practice yards and drill courtyards to complete these five building families
for the six reviewed themes.
For Better Villages, Lios, and Liberty's Villagers, see the
[vanilla village provider guide](docs/vanilla-village-provider-buildings.md).

These buildings form local defense networks. Bells place a network on Watch,
confirmed threats raise an Alert, and raids begin a Siege. Loaded guards rotate
through sentry, drill, overwatch, and muster positions as readiness changes.
Positions that are blocked or no longer safe to stand on are left vacant until
they are repaired.

See [natural guard loadouts](docs/natural-guard-loadouts.md),
[Village Watchtowers](docs/village-watchtowers.md), and
[Village Defense Buildings](docs/village-defense-buildings.md). The
[Village defense duties guide](docs/village-defense-duties.md) explains
readiness, staffing, and patrol-route precedence. The
[manual validation guide](docs/village-defense-buildings-manual-validation.md)
covers natural terrain and modpack checks that cannot be inferred from the
automated structure tests.

## Safety and Behavior

- Guards avoid firing through guards, villagers, iron golems, owners, tamed or
  owned entities, scoreboard allies, and additional protected entity types.
- Direct TaCZ hits and explosions repeat the ally check.
- Guard-owned TaCZ explosions preserve blocks by default.
- Unsafe burst rounds are canceled individually.
- Rules of engagement can use normal Guard Villagers targets, hostile mobs
  only, defensive retaliation, or hold fire.
- Explosive weapons require a safe minimum distance.
- Guards with empty or unusable guns seek safer reachable positions and keep
  retrying their weapons. If no route passes the checks, they hold and retry.
  Explicit movement commands still apply.
- A usable sidearm stays equipped across target changes and brief target gaps
  while its primary is dry.
- Completed generated structures do not repair themselves, replace removed
  guards, reroll supplies, or run recurring world scans.

Modpack ally extensions are documented in
[modpack ally protection](docs/modpack-ally-protection.md). Target selection is
documented in [rules of engagement](docs/rules-of-engagement.md).

## Configuration

Fzzy Config provides synchronized server settings in:

```text
config/villagerguardgunners/guard_gunners.toml
```

Settings cover combat, movement, weapon tactics, rules of engagement, ally
protection, explosive safety, ammo sharing, squads, Armory resupply, natural
loadouts, Watchtowers, Village Defense Buildings, and performance-related
scheduling. Sidearm behavior is selected through guard equipment and Guard
Orders rather than a separate global settings section.

See the [configuration guide](docs/configuration.md) for every option and its
default.

## Operator Commands

Permission-level-2 operators can use:

- `/vgg inspect <guard>` for combat, weapon, ammo, order, and natural
  loadout status;
- `/vgg loadouts` commands to validate, inspect, and preview datapack pools;
- `/vgg squads` commands to inspect and maintain squad records;
- `/vgg roles` commands to inspect and assign datapack roles;
- `/vgg watchtowers` commands to review Watchtower configuration, groups,
  generated guards, and rejected datapack entries; and
- `/vgg villagebuildings` commands to review configuration, accepted entries,
  live pool weights, and reload or startup errors; and
- `/vgg villages` commands to inspect ordinary village records, observed homes,
  discovery progress, guard affiliations, and active compatibility profiles.

These diagnostics do not issue equipment, locate structures, or load chunks.

## Documentation

- [Version 1.7 release notes](docs/releases/1.7.md)
- [Current development changelog](CHANGELOG.md)
- [CurseForge description](docs/curseforge-description.md)
- [Configuration](docs/configuration.md)
- [Village records and discovery](docs/village-records.md)
- [Village population and initial guard generation](docs/village-population.md)
- [Village mod compatibility assessment](docs/village-mod-compatibility.md)
- [Settlement compatibility definitions](docs/village-compatibility-definitions.md)
- [Village template support and limitations](docs/village-template-support.md)
- [Automatic village patrol coverage](docs/village-patrol-coverage.md)
- [Sidearms](docs/sidearms.md)
- [Tactical intent and threat observations](docs/guard-tactical-intent.md)
- [Tactical defaults and tuning tradeoffs](docs/guard-tactical-tuning.md)
- [Per-guard orders](docs/per-guard-orders.md)
- [Guard roles](docs/guard-roles.md)
- [Squad commands](docs/squad-commands.md)
- [Village Armories](docs/village-armories.md)
- [Natural guard loadouts](docs/natural-guard-loadouts.md)
- [Large TaCZ pack synchronization](docs/tacz-large-pack-sync.md)
- [GitHub release process](docs/github-release-process.md)
- [Release checklist](docs/release-checklist.md)

## Building and Testing

Use JDK 17 from the repository root:

```text
./gradlew check
./gradlew runGameTestServer
```

`check` runs unit and source checks, builds and reobfuscates the JAR, and
verifies the packaged artifact. GameTests provide the Forge runtime validation.
Manual client, multiplayer, third-party gun-pack, terrain, and mod-compatibility
checks remain in the [release checklist](docs/release-checklist.md).

The complete performance matrix runs both isolated suites in one server so
every report is bound to the same production artifact. It also includes a
100-guard automatic-duty workload that exercises unloaded patrol checks and
maximum-size duty-slot validation:

```text
./gradlew runGameTestServer -Pstep13Performance=true
./gradlew verifyPhase8PerformanceReports verifyVillageBuildingPerformanceReport assembleVillageDefenseBuildingEvidence
```

The earlier `phase8Performance` and `villageBuildingPerformance` properties
remain available when only one component workload needs investigation.

The release artifact is written to
`build/libs/villagerguardgunners-<version>.jar`.

## Project Layout

- `src/main` — production code and assets
- `src/test` — JUnit and source/artifact checks
- `src/gametest` — Forge correctness GameTests
- `src/perftest` — opt-in scalability workloads and JFR evidence
- `docs` — configuration, feature, compatibility, validation, and release docs

Continuous integration runs `./gradlew check` for pushes and pull requests.

## License

All Rights Reserved. See [LICENSE.txt](LICENSE.txt).

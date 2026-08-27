# Villager Guard Gunners

Villager Guard Gunners is a focused Minecraft Forge add-on that lets Guard
Villagers guards operate Timeless and Classics Zero (TaCZ) guns through TaCZ's
native server-side firearm system.

The add-on decides when a guard should aim, move, and pull the trigger. TaCZ
remains responsible for projectiles, damage, spread, recoil, fire modes,
magazines, reloads, bolt actions, heat, charge behavior, attachments, sounds,
and synchronization. The old custom projectile and hand-authored weapon
rebalance are not part of the current implementation.

## Supported versions

The 1.0.0 branch is deliberately locked to this reviewed combination:

| Component | Version |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.23 |
| Guard Villagers | 1.20.1-1.6.19 |
| Timeless and Classics Zero | 1.1.8-hotfix |
| Fzzy Config | 0.7.6+1.20.1 |
| Kotlin for Forge | 4.11.0 |
| Java | 17 |

All four mods listed after Forge are required on the client and server. Exact
runtime dependency ranges are also declared in `META-INF/mods.toml`; an
unreviewed upgrade is intentionally rejected by the source and artifact tests.

## Using the add-on

1. Create and interact with a guard according to Guard Villagers' normal rules.
2. Open the guard inventory with a player that Guard Villagers authorizes.
3. Put a TaCZ gun in the guard's main-hand slot.
4. Put compatible loose TaCZ ammunition or ammo boxes in the three dedicated
   ammo slots.
5. Give the guard a valid hostile target through normal Guard Villagers
   behavior. The guard will aim, approach or retreat as needed, find a safer
   firing lane, and delegate firing and reloads to TaCZ.

The inventory keeps Guard Villagers' armor, follow, patrol, ownership, and
reputation behavior. Ammunition persists with the guard and is exposed through
Forge's item-handler capability for modded inventory automation.

## Safety and behavior

- Guards do not fire through guards, villagers, iron golems, their owner, or
  entities Minecraft considers allied to them.
- The same protection is enforced again for direct TaCZ hits and explosions.
- Unsafe native burst rounds are canceled individually.
- TaCZ's effective range is the guard's preferred movement distance. The
  configurable maximum engagement range is the hard firing limit.
- Visibility, movement, path recalculation, unsafe-shot caching, and TaCZ data
  refresh work are bounded and staggered between guards.
- A permanently dry gun temporarily yields to Guard Villagers' normal melee
  goal instead of retrying every tick.

## Configuration

Fzzy Config provides the server-authoritative file and synchronized UI:

```text
config/villagerguardgunners/guard_gunners.toml
```

Combat, movement, safety-clearance, and advanced scheduling settings are
documented in [docs/configuration.md](docs/configuration.md). TaCZ weapon damage,
reload speed, accuracy, recoil, fire rate, and other balance values are not
overridden by this add-on.

## Diagnosing a guard

Operators with permission level 2 can inspect one guard on demand:

```mcfunction
/vgg inspect <guard>
```

Example selector for the nearest guard:

```mcfunction
/vgg inspect @e[type=guardvillagers:guard,sort=nearest,limit=1]
```

The report separates the current AI decision from TaCZ's native state. It
includes target distance, preferred and maximum range, cached line of sight,
visible ticks, aim error, movement, friendly-fire blockers, gun ID, fire mode,
loaded and reserve ammunition, reload/bolt/cooldown/charge state, and the last
TaCZ shoot result. The command registers no recurring tick handler or overlay;
its descriptive entity query runs only when an administrator invokes it.

## Building and testing

From the repository root:

```bash
./gradlew check
./gradlew runGameTestServer
```

`check` runs unit and source contracts, compiles the correctness and performance
GameTest source sets, builds/reobfuscates the JAR, and verifies that the release
artifact contains the supported integration while excluding development tests
and removed legacy systems.

The opt-in scalability matrix is intended for release comparisons on the same
machine:

```bash
./gradlew runGameTestServer -Pphase8Performance=true
./gradlew verifyPhase8PerformanceReports
```

See [docs/phase-8-validation-and-performance.md](docs/phase-8-validation-and-performance.md)
for the reference measurements and interpretation. Headless tests cannot prove
client animation or observer synchronization, so a release candidate must also
complete [docs/release-checklist.md](docs/release-checklist.md).

## Development layout

- `src/main` — production code and assets
- `src/test` — plain JUnit and source/artifact contracts
- `src/gametest` — transformed Forge correctness GameTests
- `src/perftest` — opt-in scalability workloads and JFR evidence
- `docs` — architecture, configuration, validation, and release notes

Continuous integration runs `./gradlew check` on pushes and pull requests. It
does not claim to replace the client, multiplayer-observer, or third-party gun
pack checks in the manual release matrix.

## License

All Rights Reserved. See [LICENSE.txt](LICENSE.txt).

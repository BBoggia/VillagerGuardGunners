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

The 1.4 development branch is deliberately locked to this reviewed combination:

| Component | Version |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.23 |
| Guard Villagers | 1.20.1-1.6.19 |
| Timeless and Classics Zero | 1.1.8-hotfix |
| Fzzy Config | 0.7.6+1.20.1 |
| Kotlin for Forge | 4.12.0 |
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

The inventory's **Guard Orders** panel can specialize that individual guard's
rules of engagement, tactical profile, explosive permission, ammunition
pickup and sharing, combat movement, and maximum firing range. Every order
defaults to inheriting the server configuration, and the server's safety and
performance limits remain authoritative.

Newly generated structure guards can also receive a difficulty-scaled TaCZ
loadout automatically. The default pools favor pistols on Easy, add SMGs on
Normal, and allow occasional rifles on Hard. Each successful guard receives a
loaded native TaCZ gun plus one complete stack of its own compatible loose
ammunition. Spawn sources, chance, nearby population caps, crossbow-only
replacement, explosive opt-in, and datapack pool IDs are all controlled by the
server configuration. Invalid or unsupported definitions fail without changing
the guard, and custom pools can be supplied by datapacks. Successful natural
issuance is authenticated across chunk unloads and server restarts so nearby
population caps remain accurate; manually replacing or moving the issued gun
does not leave a stale guard counted against the cap.

Format-2 datapack pools can filter entries by dimension, biome, structure,
difficulty, spawn source, raid state, and guard scoreboard tags while retaining
an unconditional fallback. Permission-level-2 operators can validate TaCZ
compatibility, inspect parsed weights and conditions, and preview a pool at a
world position through `/vgg loadouts validate`, `/vgg loadouts inspect`, and
`/vgg loadouts preview` without issuing items or consuming spawn random state.

Craft an **Orders Ledger** shapelessly from a Book and Quill and an Emerald to
reuse a setup across guards. Use a blank ledger on a guard to capture its seven
orders and optional datapack role, use the written ledger on another guard to apply them, or sneak-use it
to overwrite the stored template. It copies behavior parameters only—never
equipment, ammunition, ownership, patrol positions, or other guard data—and
all operations recheck Guard Villagers permissions and distance on the server.

Datapack-defined **guard roles** provide reusable behavioral defaults beneath
those explicit orders. Roles never grant or move items, remain subordinate to
all global safety controls and temporary squad-command precedence, and add no
scan or tick handler. They can be assigned through natural-loadout format 4,
an Orders Ledger, or permission-level-2 `/vgg roles` commands. The strict,
bounded format and built-in unassigned examples are documented in
[docs/guard-roles.md](docs/guard-roles.md).

The separate **Squad Command Ledger** assigns authorized guards to a stable
squad and issues temporary Rally, Defend, Hold, Focus Target, Fall Back, and
Resume Orders commands. Crouch-right-click in the air to select a mode, then
use the ledger in the air, on a block, or directly on a Focus target. Commands
affect only nearby loaded squad members, are deterministically capped, and
expire across chunk unloads and restarts. They can override movement but never
bypass rules of engagement, ally protection, explosive restrictions, or
TaCZ's native firing and ammunition state. Rally clears on arrival; Resume
Orders restores the unchanged Guard Villagers patrol/follow behavior.

The craftable **Village Armory** provides nine dedicated slots for TaCZ loose
ammunition and ammo boxes. Players can stock it directly, and hoppers, pipes,
and other Forge item-handler integrations can insert or extract from every
side. A public armory accepts any guard for resupply; sneak-use a bound
Squad Command Ledger on it to assign that squad, and repeat with the same
ledger to return it to public access. Inventory and lock identity persist,
contents drop when the block is broken, and comparator output reports
fullness. The block has no ticker or world scan. The 1.3 development line now
activates bounded automatic resupply: otherwise-idle guards below their reserve
trigger query only the transient loaded-Armory index, reserve compatible supply,
travel with interruptible low-priority navigation, and withdraw transactionally.
Checks are UUID-staggered, candidate/path work is capped, and failures use
cooldown or exponential backoff without loading a chunk.

Once a guard has a TaCZ gun equipped, an authorized player may crouch-right-
click it while holding compatible loose ammunition or an ammo box. Quick
deposit fills only the three dedicated ammo slots, accepts partial stacks when
space is limited, and leaves rejected ammunition in the player's hand. Normal
right-click still opens the inventory for equipment changes and pre-stocking.
Quick deposit follows Guard Villagers' separate **give guard stuff** permission:
with its default `giveGuardStuffHOTV=false`, a player may supply ammunition even
when the player lacks the reputation or ownership needed to open and take from
the inventory. It never grants inventory access. Guards with disabled AI and
guns using TaCZ dummy or native-infinite reserves reject physical handoffs.

Active armed guards also collect compatible TaCZ ammunition that comes within
the configured passive-pickup radius. This does not make guards search or
pathfind for drops. The dropped ammunition performs a staggered, twice-per-
second bounded check, respects vanilla pickup delays and player-targeted
items as well as the `mobGriefing` game rule, and leaves any capacity-limited
remainder in the world. Empty finite ammo boxes do not trigger pickup checks.

TaCZ's reusable finite boxes remain real items after their ammunition reaches
zero. On the next reload or restocking action, the guard drops each depleted
box intact beside itself to free the dedicated slot; this is event-driven and
does not add a guard tick scan.

When TaCZ proves an armed guard cannot reload from its own reserves, that dry
guard can make one bounded emergency request to nearby guards. By default the
nearest guard with the same owner supplies up to one recipient magazine of
loose ammunition while retaining one attachment-adjusted magazine for itself;
two unowned guards may also share. The request does not move either guard,
transfer ammo boxes, or continuously balance inventories.

The inventory keeps Guard Villagers' armor, follow, patrol, ownership, and
reputation behavior. Ammunition persists with the guard and is exposed through
Forge's item-handler capability for modded inventory automation. The Ammo
heading is color-coded and shows a detailed TaCZ-native status tooltip when
hovered, including feed type, magazine, chamber, compatible reserve, and
incompatible or unreadable ammunition. A second color-coded status line explains
the guard's current combat state, including weapon-order restrictions, aiming,
range, line-of-sight,
friendly-lane, reload, action-cycle, charge, cooldown, and dry-ammunition
conditions. Hover it for a plain-language explanation. Quick deposit uses the
same TaCZ-native compatibility result as the ammunition display.

## Safety and behavior

- Guards do not fire through guards, villagers, iron golems, their owner,
  owned/tamed entities, entities Minecraft considers allied, or entity types
  supplied by the modpack ally tag, config, or compatibility API.
- The same protection is enforced again for direct TaCZ hits and explosions.
- Guard-owned TaCZ explosions preserve blocks by default while retaining native
  effects against hostile entities. Server operators can disable this behavior.
- Unsafe native burst rounds are canceled individually.
- Server rules of engagement can preserve Guard Villagers' normal targets,
  restrict proactive fire to hostile mobs, require recent aggression against a
  guard or protected ally, or hold fire. Config and datapack type overrides
  use deterministic deny-before-allow precedence.
- TaCZ's effective range is the guard's preferred movement distance. The
  selected tactical profile can adjust that approach distance and the guard's
  retreat threshold, continuous-visibility requirement, and acceptable aim
  error without changing TaCZ weapon statistics. Profiles may also impose a
  narrower firing cap, while explosive weapons enforce a blast-aware minimum
  standoff. The configurable maximum engagement range remains the global cap.
- Visibility, movement, path recalculation, unsafe-shot caching, and TaCZ data
  refresh work are bounded and staggered between guards.
- The inventory combat status is derived from cached goal decisions and TaCZ's
  synchronized action fields. Its single integer menu value is evaluated only
  while that inventory is open and transmitted only when it changes.
- Per-guard orders use the existing container-button protocol and seven small
  change-detected values only while the inventory is open. The combat path
  performs constant-time enum reads and refreshes existing cached TaCZ data
  only when an order revision changes.
- Orders Ledgers are interaction-driven and perform no idle scan or tick work.
  Their strict versioned template is decoded only when used or displayed and
  applies all seven orders atomically or not at all.
- A permanently dry gun temporarily yields to Guard Villagers' normal melee
  goal instead of retrying every tick.

## Configuration

Fzzy Config provides the server-authoritative file and synchronized UI:

```text
config/villagerguardgunners/guard_gunners.toml
```

Combat, movement, weapon-specific tactics, rules of engagement, safety,
ammunition quality-of-life, explosion preservation, and advanced scheduling settings are documented in
[docs/configuration.md](docs/configuration.md). TaCZ weapon damage, reload speed,
accuracy, recoil, fire rate, and other balance values are not overridden by
this add-on.

Datapack authors and compatibility mods can extend the shared friendly-fire
predicate without patching the add-on. See
[docs/modpack-ally-protection.md](docs/modpack-ally-protection.md) for the entity
tag, config precedence, provider API, and performance contract.

Target firing policy, restricted-mode overrides, and defensive retaliation are
documented in [docs/rules-of-engagement.md](docs/rules-of-engagement.md).

Ammunition handoff, passive pickup, emergency sharing, ownership rules,
supported reserve types, rollback controls, troubleshooting, and deliberately
deferred features are documented in
[docs/ammunition-quality-of-life.md](docs/ammunition-quality-of-life.md).

Individual guard policy, override precedence, authorization, persistence, and
performance behavior are documented in
[docs/per-guard-orders.md](docs/per-guard-orders.md).

Squad identity, ledger interaction, temporary command behavior, precedence,
persistence, and performance boundaries are documented in
[docs/squad-commands.md](docs/squad-commands.md).

Armory crafting, inventory, automation, squad locking, comparator behavior,
and the boundary between the storage foundation and guard resupply are
documented in [docs/village-armories.md](docs/village-armories.md).

Natural spawn eligibility, difficulty progression, datapack pool format,
atomic gun/ammunition issuance, versioned save identity, lifecycle behavior,
world-generation handoff, and bounded population-cap behavior are documented
in [docs/natural-guard-loadouts.md](docs/natural-guard-loadouts.md).
The repository also includes a copy-ready
[example datapack](docs/examples/natural-loadout-datapack/README.md) and a
[format-1 migration guide](docs/natural-loadout-migration.md).

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
includes the guard's orders and effective policy, target distance, minimum,
preferred, and maximum range, firing-window validity, cached line of sight,
active visible-tick and aim requirements, movement, the ROE mode and decision
reason, progressive muzzle/projectile/blast safety padding, friendly-fire blockers and their ally-classification reason, gun ID, fire mode,
magazine and chamber state, actual compatible reserve units, reserve source,
compatibility scan results, reload/bolt/cooldown/charge state, and the last
TaCZ shoot result. It also shows the cached TaCZ gun type, tactical profile and
selection source, projectile/explosion traits, and the cached tactical
envelope. The command registers no recurring tick handler or overlay; its
descriptive entity query runs only when an administrator invokes it.

The report also identifies a guard's natural-loadout record as absent, active,
or mismatched and includes its last selection/issuance result and persisted
pool, gun, difficulty, source, schema, and issuance identity when available.

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
./gradlew assembleReleaseCandidateEvidence
./gradlew verifyAmmunitionReleaseCandidate
```

See [docs/phase-8-validation-and-performance.md](docs/phase-8-validation-and-performance.md)
for the reference measurements and interpretation. Headless tests cannot prove
client animation or observer synchronization, so a release candidate must also
complete [docs/release-checklist.md](docs/release-checklist.md).

The final command verifies the release JAR and exact 20-report/20-JFR set, parses
each JFR, enforces same-run baseline-relative p95/p99 budgets, and requires every
JSON report to name the SHA-256 of that exact production JAR. It then writes a
deterministic artifact manifest containing the byte size and SHA-256 of the JAR
and every performance evidence file, plus a conventional JAR checksum under
`build/reports/release-candidate`. It deliberately records the separate
correctness and manual-validation boundaries instead of claiming they ran as
part of the bundle task.

The final aggregate task must be run without `-Pphase8Performance`. It consumes
the separately generated performance reports, runs the normal 88-case
correctness GameTest server, and verifies the complete ammunition release
evidence boundary. It does not replace the recordable multiplayer rows in the
release checklist.

`./gradlew prepareCurseForgeRelease` additionally runs
`verifyManualReleaseChecklist` and intentionally refuses to create a public
release handoff while any manual row is `Not run`/`Failed` or any checklist item
remains unchecked.

## Development layout

- `src/main` — production code and assets
- `src/test` — plain JUnit and source/artifact contracts
- `src/gametest` — transformed Forge correctness GameTests
- `src/perftest` — opt-in scalability workloads and JFR evidence
- `docs` — architecture, configuration, validation, and release notes

Continuous integration runs `./gradlew check` on pushes and pull requests. It
does not claim to replace the client, multiplayer-observer, or third-party gun
pack checks in the manual release matrix.

Version numbers, immutable annotated tags, GitHub Actions artifacts, and the
GitHub/CurseForge publishing sequence are documented in
[docs/github-release-process.md](docs/github-release-process.md).

The 1.3 Village Defense Logistics line begins with the
[Phase 0 baseline and compatibility contract](docs/village-defense-logistics-phase-0.md).
Its feature-specific validation cases are recorded separately in the
[Village Defense Logistics release matrix](docs/village-defense-logistics-release-matrix.md)
alongside the main release checklist for the final 1.3 candidate.
The first implementation boundary—the inactive state, need, request,
precedence, expiry, and retry contracts—is documented in
[Armory resupply contracts](docs/armory-resupply-contracts.md).

The 1.4 Village Watchtowers line begins with the
[Step 9 world-generation baseline](docs/village-watchtowers-phase-0.md). Its
structure placement, guard-group, loadout, lifecycle, compatibility, and
performance cases are tracked in the
[Village Watchtowers validation matrix](docs/village-watchtowers-release-matrix.md)
throughout the incremental world-generation implementation.
The [Step 10 registry skeleton](docs/village-watchtowers-step-10.md) registers
the codec and validates the built-in structure data while an empty biome tag
and empty generation point keep incomplete towers from generating.
The [Step 11 template and bounded placement](docs/village-watchtowers-step-11.md)
adds the first 9 x 14 x 9 tower, centered persisted rotations, nine-point dry
terrain validation, and village-compatible Overworld biome activation while
leaving all guard, Armory, marker, and loot work disabled for later steps.
The [Step 12 guard-post composition layer](docs/village-watchtowers-step-12.md)
adds strict datapack-defined weighted groups, inherited loadout references,
optional members, deterministic difficulty selection, and atomic
last-known-good reload publication. It remains identifier-only: guard spawning
and equipment issuance are reserved for later steps.

## License

All Rights Reserved. See [LICENSE.txt](LICENSE.txt).

# Fzzy Config Guide

Villager Guard Gunners has one server-authoritative Fzzy Config file:

`config/villagerguardgunners/guard_gunners.toml`

The same settings are available through Fzzy Config's UI. The server owns the active values, synchronizes them to clients, and requires permission level 4 to edit them. Most changes take effect on the next relevant AI decision. Cached TaCZ range and tactical metadata may take up to the configured gun-data refresh interval to update.

## Choosing settings

Use the in-game Fzzy Config screen to edit server values. The top-level
**Enable Combat and Village AI** switch controls gun combat, automatic gun
loadouts, command movement, squad patrols, village discovery, initial guard
staffing, fixed defense duties, and Village Armory resupply. Medical treatment,
Quartermaster service, and building generation have separate controls.

| What you want | Where to configure it |
| --- | --- |
| Reduce guard accuracy, including only against players | Combat → Guard Accuracy; see [guard accuracy and player combat](#guard-accuracy-and-player-combat) |
| Equip new guards with guns | Automatic Gun Loadouts; difficulty chance and nearby gunner cap are separate from total guard counts |
| Scale initial guards to village size | Initial Village Guards; enable size-based generation for new villages |
| Patrol ordinary villages automatically | Village Patrols and Defense → Automatic Village Patrols; available residents need weapons and at least half health, with no manual squad or movement orders |
| Follow a route you choose | Squads and Commands → Follow Saved Squad Patrol Routes; create the route with a Squad Command Ledger |
| Staff generated defense posts | Village Patrols and Defense → Automatic Building Defense Posts |
| Heal injured guards | Medical Treatment and Recovery; allow treatment and medic duty, then enable Medic Duty on a supplied guard |
| Rescue and transport casualties | Also enable Downed Guards and Cot Care and Transport, stock the medic’s pouch, and place accessible Recovery Cots |
| Supply equipment through a Quartermaster | Use the guard inventory’s Service control and the station’s independent Equipment Service permission |

**Ticks and units.** At normal game speed, 20 ticks = 1 second; 100 ticks =
5 seconds; 1200 ticks = 1 minute. Durations take longer in real time if the
server runs slowly. Speed values are movement factors, not blocks per second:
1 uses normal guard movement speed and 0.5 uses half. Chances use 0–1:
0.5 means 50%. Multiplier 1 leaves a value unchanged; 1.5 multiplies it by one
and a half. Tactical profiles can adjust base combat values.

**When changes apply.** Most AI changes apply at the next relevant decision.
Medical actions and readiness deadlines already in progress keep their starting
duration. Natural loadout changes affect new guards; initial population targets
are fixed when a new village is enrolled. Reopen the world or restart the server
after changing Village Defense Building layout switches, then explore newly
generated villages. The six layout switches carry a restart notice in Fzzy.
Garrison and Armory activation controls are read separately during activation.
Existing buildings are preserved.

**Related values.** In the editor, the minimum initial guard target cannot
exceed the maximum, and the Armory low-ammo trigger cannot exceed its target
spare ammo. Raise the upper value first when increasing both; lower the lower
value first when decreasing both. To choose a zero managed population target,
set both population limits to 0. This is not a universal spawning switch.
For hand-edited files, an inverted population pair prevents new managed
village enrollment; an inverted Armory pair stops automatic resupply.

**Medical timings.** Cot and field timing controls apply only with Medical
Treatment, Downed Guards, and Cot Care and Transport enabled. Revival time is
the second-dose treatment channel; recovery time is rest before returning to
duty, not time to full health. Initial stabilization and ordinary healing use
separate fixed behavior. Field timings use the larger of their saved value and
the matching cot value. The screen shows effective cot/field seconds and the
current cot-care permission state without changing stored values. Travel,
assessment, dose cooldowns, and interrupted care can add time. Manual revival
remains possible with medic AI disabled; carrying requires a medic.

**Quartermaster controls.** Service: Manual disables only Quartermaster trips.
Restock tops up current ammo and medical item types already in the pouch; seed
an empty pouch or choose Service: Loadout to supply it. Loadout is the equipment
profile, separate from tactical behavior. On the station, Pipes controls hopper
and pipe access; Equipment Service controls which guards it supplies. Owner’s
guards means the station owner’s guards. Linked Village Armories supply ammo;
guns, armor, and medicine come from station Stock. See
[Quartermaster service](quartermaster-service.md) for details.

**Quartermaster Trades.** `quartermasterTrades.suppliesEnabled` defaults to `true`;
`quartermasterTrades.gunSalesEnabled` defaults to `false`. They control separate
merchant offer categories, including whether saved offers can be purchased.
They do not enable guard service or deposit purchased items in station stock.
Enabling a category does not reroll levels whose offers were already generated;
use a new villager or unlock another level. See [trade definitions and defaults](quartermaster-trades.md).

## Supported settings

Defaults are the recommended native-TaCZ guard profile. TaCZ remains authoritative for hostile-entity effects, ammunition, reloads, fire modes, projectile behavior, recoil, spread, sounds, heat, and attachments. The block-preservation option only filters the mutable block list from a guard-owned native explosion after TaCZ and Forge have computed it.

| Setting | Default | Allowed range | Purpose and cost tradeoff |
| --- | ---: | ---: | --- |
| `medical.cotRevivalTicks` | 40 (2 seconds) | 20–600 ticks | Second-dose channel on a cot; requires medical treatment, downing, and transport/cot care. |
| `medical.fieldRevivalTicks` | 160 (8 seconds) | 20–600 ticks | Second-dose field channel; effective duration is at least the cot channel. Same prerequisites. |
| `medical.cotRecoveryTicks` | 100 (5 seconds) | 20–2400 ticks | Rest after cot revival before returning to duty. Same prerequisites; blocked exits can extend the stay. |
| `medical.fieldRecoveryTicks` | 400 (20 seconds) | 20–2400 ticks | Rest after field revival; effective duration is at least the cot rest. Same prerequisites. |
| `medical.downed` | `false` | boolean | Enables optional lethal-injury stabilization and revival. Requires treatment and valid profiles; see [casualty recovery](casualty-recovery.md). |
| `medical.transport` | `false` | boolean | Lets supplied medics carry stabilized guards to nearby Recovery Cots. Requires downed guards and medic AI; see [casualty transport](casualty-transport.md). |
| `medical.medicAi` | `true` | boolean | Enables care by guards with Medic duty enabled. Individual guards start with duty off. |
| `medical.retreat` | `true` | boolean | Allows medical retreat for opted-in guards; individual guards start with retreat off. |
| `medical.enabled` | `true` | boolean | Enables manual and medic treatment; disabling it leaves pouch stocking, withdrawal, and recovery available. See [medical supplies](medical-supplies.md). |
| `enabled` | `true` | boolean | Enables combat and village AI, including loadouts, command movement, patrols, village discovery/staffing, and Armory resupply. Medical, Quartermaster, and building controls are separate. |
| `naturalLoadouts.enabled` | `true` | boolean | Master switch for one-time automatic loadouts on otherwise eligible newly spawned guards. |
| `naturalLoadouts.enabledSpawnSources` | `STRUCTURE` | enum set | Limits automatic loadouts by initial spawn origin. Conversion, spawn-egg, command, and unknown origins are opt-in. |
| `naturalLoadouts.replaceCrossbowsOnly` | `true` | boolean | Prevents automatic loadouts from overwriting starting equipment supplied by another mod. |
| `naturalLoadouts.nearbyCapRadius` | 64 | 8–256 blocks | Radius for the one-time nearby natural-gunner population check, including authenticated reservations in unloaded chunks. This is not a repeating scan. |
| `naturalLoadouts.allowExplosiveWeapons` | `false` | boolean | Permits explosive entries from datapack pools; all existing explosive safety still applies. |
| `naturalLoadouts.easy.upgradeChance` | 0.35 | 0–1 | Chance that an eligible Easy guard advances to pool and population-cap evaluation. |
| `naturalLoadouts.easy.maximumNearby` | 1 | 0–32 guards | Nearby cap for naturally equipped Easy guards. Zero disables Easy loadouts. |
| `naturalLoadouts.easy.poolId` | `villagerguardgunners:easy` | resource ID | Datapack pool selected on Easy. |
| `naturalLoadouts.normal.upgradeChance` | 0.50 | 0–1 | Chance that an eligible Normal guard advances to pool and population-cap evaluation. |
| `naturalLoadouts.normal.maximumNearby` | 2 | 0–32 guards | Nearby cap for naturally equipped Normal guards. Zero disables Normal loadouts. |
| `naturalLoadouts.normal.poolId` | `villagerguardgunners:normal` | resource ID | Datapack pool selected on Normal. |
| `naturalLoadouts.hard.upgradeChance` | 0.65 | 0–1 | Chance that an eligible Hard guard advances to pool and population-cap evaluation. |
| `naturalLoadouts.hard.maximumNearby` | 2 | 0–32 guards | Nearby cap for naturally equipped Hard guards. Zero disables Hard loadouts. |
| `naturalLoadouts.hard.poolId` | `villagerguardgunners:hard` | resource ID | Datapack pool selected on Hard. |
| `squads.commandDispatchRadius` | 48 | 8–128 blocks | Bounds the one-time loaded squad-member query performed only when a player issues a command. It does not create a recurring squad scan. |
| `squads.maximumAffectedGuards` | 32 | 1–64 guards | Hard limit on authorized members changed by one command. Additional authorized matches are reported as capped. |
| `squads.positionalCommandDurationTicks` | 1200 | 20–72000 ticks | Active lifetime of Rally, Defend, Hold, and Fall Back before unchanged permanent behavior resumes. |
| `squads.focusCommandDurationTicks` | 400 | 20–12000 ticks | Active lifetime of a safety-validated Focus Target command. |
| `squads.defendRadius` | 12 | 2–64 blocks | Area a Defend guard returns to and keeps combat navigation within. |
| `squads.airCommandRange` | 32 | 4–64 blocks | Maximum projection distance for positional commands issued while looking into the air. |
| `squads.patrolRouteExecution` | `true` | boolean | Allows guards to follow saved squad patrol routes. Disabling execution restores ordinary Guard Villagers checkpoint behavior without deleting routes or disabling the editor. |
| `squads.patrolMovementSpeed` | 0.8 | 0.1–2.0 | Navigation speed while following a saved squad patrol route. |
| `squads.patrolWaypointDwellTicks` | 20 | 0–1200 ticks | Pause after reaching each waypoint. Zero advances on the next AI tick. |
| `squads.patrolMaximumFailuresPerWaypoint` | 3 | 1–8 attempts | Failed or stalled path attempts before the guard skips one unreachable waypoint and keeps the route moving. |
| `villagePopulation.managedGeneration` | `false` | boolean | Sizes one-time initial generation in new villages. Existing villages remain observation-only. See [village population](village-population.md) for persistence and retry behavior. |
| `villagePopulation.minimumGuards` | 2 | 0–32 | Minimum target; must not exceed the maximum. Actual allowance is limited by available sources. |
| `villagePopulation.maximumGuards` | 16 | 0–32 | Maximum target shared by managed village guards and Village Defense Building garrisons. |
| `villagePopulation.homesPerGuard` | 3 | 1–64 | Classified residences per guard added to the target, rounded up. |
| `villagePopulation.areasPerGuard` | 2 | 1–64 | Occupied 32-block generation cells per guard added to the target, rounded up. |
| `villagePopulation.defenseBonusCap` | 2 | 0–32 | Maximum target bonus from buildings with garrison capacity. |
| `villagePopulation.hardDifficultyBonus` | 0 | 0–32 | Extra target on Hard, within the configured maximum. |
| `villagePopulation.residentialTemplates` | empty | resource-ID set, at most 128 | Additional exact residential template IDs. Ordinary requests still require the upstream Guard Villagers template configuration. |
| `villageDefense.automaticCoverage` | `false` | boolean | Enables outdoor coverage for eligible uncommanded village residents, including ordinary villages. Manual squads/routes and higher-priority work retain control. See [patrol coverage](village-patrol-coverage.md) for limits and diagnostics. |
| `villageDefense.automaticDuties` | `true` | boolean | Enables transient assignment and travel to generated defense-duty positions. Disabling it preserves buildings, saved readiness state, Guard Orders, and patrol routes. |
| `villageDefense.watchDurationTicks` | 600 | 20–72000 ticks | Quiet time after a bell before Watch returns to Peace. |
| `villageDefense.alertDurationTicks` | 1200 | 20–72000 ticks | Quiet time after a confirmed threat before Alert enters Recovery. |
| `villageDefense.recoveryDurationTicks` | 600 | 20–72000 ticks | Quiet time in Recovery before returning to Peace. |
| `villageDefense.dutyMovementSpeed` | 0.8 | 0.1–2.0 | Navigation speed while travelling to an automatic defense post. |
| `combat.requiredVisibleTicks` | 5 | 0–40 ticks | Base continuous visibility required before firing; the selected tactical profile may adjust the final cached requirement. |
| `combat.maximumEngagementRange` | 32 | 8–64 blocks | Hard firing-distance cap. TaCZ's effective range remains the preferred distance used for movement and damage falloff. |
| `combat.closeRangeDistance` | 4 | 1–8 blocks | Distance at which a guard tries to retreat. |
| `combat.aimToleranceDegrees` | 10 | 1–45 degrees | Maximum difference between facing direction and the intended target direction before firing; tactical profiles can adjust it. This does not add shot error. |
| `combat.aiming.errorDegrees` | 0.0 | 0–10 degrees | Maximum added guard aim error before native TaCZ spread. Zero preserves exact intended aim. |
| `combat.aiming.aimPoint` | `EYE_LEVEL` | `EYE_LEVEL`, `CENTER_MASS` | Target point used before adding guard aim error. |
| `combat.aiming.overridePlayers` | `false` | boolean | Applies both player-specific settings when a guard targets a player. |
| `combat.aiming.playerErrorDegrees` | 2.0 | 0–10 degrees | Added error against players when the override is enabled. Recommended starting value with center-mass player aim; adjust for your server. |
| `combat.aiming.playerAimPoint` | `CENTER_MASS` | `EYE_LEVEL`, `CENTER_MASS` | Target point against players when the override is enabled. |
| `movement.approachSpeed` | 1.0 | 0.1–2.0 | Navigation speed while approaching. |
| `movement.retreatSpeed` | 0.75 | 0.1–1.0 | Navigation speed while retreating. |
| `movement.repositionSpeed` | 0.75 | 0.1–1.0 | Navigation speed while finding a clear firing lane. |
| `movement.friendlyRepositionTicks` | 20 | 5–100 ticks | Delay before retrying a lane blocked by a protected entity. |
| `tactics.typeProfiles` | seven mappings | validated map | Maps TaCZ type labels to profiles. Keys are trimmed and matched without regard to capitalization; conflicting normalized aliases are ignored. |
| `tactics.gunOverrides` | empty | resource-ID map | Selects a profile for an exact TaCZ gun ID. Individual Guard Orders take precedence; this overrides gun-type mappings. |
| `engagement.mode` | `GUARD_TARGETS` | enum | Filters the target already selected by Guard Villagers using compatibility, hostile-only, defensive, or hold-fire policy. |
| `engagement.defensiveMemoryTicks` | 200 | 20–1200 ticks | Time that an attack against the guard or a protected ally remains engageable in `DEFENSIVE` mode. |
| `engagement.additionalTargets` | empty | resource-ID set | Permits entity types in restricted modes. Cannot bypass ally protection, exclusions, or `HOLD_FIRE`. |
| `engagement.excludedEntities` | empty | resource-ID set | Prevents gun engagement of entity types and takes precedence over additions. |
| `safety.baseClearance` | 0.75 | 0.25–4 blocks | Extra protected-entity clearance added to TaCZ-derived spread and explosion margins. |
| `safety.preserveBlocksFromGuardExplosions` | `true` | boolean | Prevents guard-owned TaCZ projectile explosions from destroying blocks. Hostile entities retain TaCZ's native explosion effects. Player-owned and other explosions are unchanged. |
| `safety.explosiveStandoffRadiusMultiplier` | 2.0 | 1.0–8.0 | Multiplies the resolved TaCZ blast radius for minimum firing distance. |
| `safety.explosiveStandoffAdditionalBlocks` | 4.0 | 0–32 blocks | Extra standoff added after the blast-radius multiplier. |
| `safety.allies.protectOwnedEntities` | `true` | boolean | Protects living vanilla or modded entities that expose an owner UUID through Minecraft's ownership contract. |
| `safety.allies.protectAlliedEntities` | `true` | boolean | Protects Minecraft alliance relationships, including entities on the same scoreboard team. |
| `safety.allies.additionalProtectedEntities` | empty | resource-ID set | Adds living entity types to every pre-fire and damage safety layer without requiring a datapack. |
| `safety.allies.excludedEntities` | empty | resource-ID set | Excludes types from broader ally rules. Cannot bypass guard or owner protection. |
| `ammunition.pickupDroppedAmmo` | `true` | boolean | Enables passive pickup of compatible TaCZ ammunition a guard naturally walks over; it does not make guards pathfind to items. |
| `ammunition.droppedAmmoPickupRadius` | 1.5 | 0.5–3 blocks | Bounds the small-area guard query performed around eligible dropped ammunition. |
| `ammunition.shareAmmoBetweenGuards` | `true` | boolean | Enables recipient-driven emergency sharing after a guard runs out of compatible reserve ammunition. |
| `ammunition.ammoSharingRadius` | 8 | 1–16 blocks | Bounds the donor search performed by a dry guard. It is not a continuous balancing radius. |
| `ammunition.donorReserveMagazines` | 1 | 0–4 magazines | Whole attachment-adjusted magazines a donor retains before loose ammunition may be shared. |
| `ammunition.requireMatchingOwnerForSharing` | `true` | boolean | Restricts sharing to guards with the same owner while allowing two unowned guards to share. |
| `armoryResupply.enabled` | `true` | boolean | Enables automatic Armory resupply beneath the add-on's global `enabled` switch. |
| `armoryResupply.checkIntervalTicks` | 40 | 20–200 ticks | Staggered interval between ammunition-need assessments. Higher values reduce server work at the cost of slower response. |
| `armoryResupply.searchRadius` | 48 | 8–64 blocks | Hard radius supplied to the loaded-chunk-only Armory index query. It never authorizes chunk loading. |
| `armoryResupply.maximumCandidateArmories` | 8 | 1–32 Armories | Maximum indexed Armories inspected for one request before candidate selection fails closed. |
| `armoryResupply.lowReserveMagazines` | 1 | 0–64 magazines | Requests resupply below this many attachment-adjusted magazines. Zero disables automatic requests. |
| `armoryResupply.targetReserveMagazines` | 3 | 0–64 magazines | Desired physical reserve after withdrawal. A value below the trigger is reported as invalid and creates no request. |
| `armoryResupply.movementSpeed` | 0.8 | 0.1–2.0 | Movement speed for a trip that already owns an expiring reservation. |
| `armoryResupply.requestLifetimeTicks` | 1200 | 20–2400 ticks | Maximum lifetime of one immutable resupply request. |
| `armoryResupply.reservationLifetimeTicks` | 400 | 20–1200 ticks | Maximum ownership time for one reserved Armory source slot. |
| `armoryResupply.successCooldownTicks` | 100 | 20–2400 ticks | Reassessment delay after a successful transaction. |
| `armoryResupply.noSupplyRetryTicks` | 200 | 20–2400 ticks | Retry delay after bounded selection finds no compatible supply. |
| `armoryResupply.unreachableRetryTicks` | 200 | 20–2400 ticks | Base retry delay after bounded navigation exhausts its approaches. |
| `watchtowers.enabled` | `true` | boolean | Allows new Watchtower structure starts. Existing towers are never removed or rewritten. |
| `watchtowers.spawnGuards` | `true` | boolean | Allows guard markers in newly placed towers to create guards. |
| `watchtowers.maximumGuardsPerPost` | 4 | 0–4 guards | Caps the deterministic group selected for a newly placed tower. |
| `watchtowers.placeArmory` | `true` | boolean | Allows Armory markers in newly placed towers to create an Armory. |
| `watchtowers.stockArmory` | `true` | boolean | Allows a newly generated tower Armory to receive its datapack-defined initial stock. |
| `watchtowers.guardGunChanceMultiplier` | 1.5 | 0–3 | Multiplies only a new tower guard's difficulty chance, clamped to 1.0. The default changes Easy/Normal/Hard chances from 35%/50%/65% to 52.5%/75%/97.5%; it does not bypass any other natural-loadout or safety gate. |
| `villageBuildings.enabled` | `false` | boolean | Adds Villager Guard Gunners entries to supported village pools during startup, affecting only future generation. It remains off by default so server operators choose whether to extend village generation. |
| `villageBuildings.barracks` | `true` | boolean | Allows datapack-defined Barracks entries when the master switch is enabled. |
| `villageBuildings.armories` | `true` | boolean | Allows datapack-defined village Armory entries when the master switch is enabled. |
| `villageBuildings.sentryPosts` | `true` | boolean | Allows datapack-defined roadside Sentry Post entries when the master switch is enabled. |
| `villageBuildings.integratedWatchtowers` | `true` | boolean | Allows datapack-defined village Watchtower entries without affecting standalone Watchtowers. |
| `villageBuildings.trainingYards` | `true` | boolean | Allows datapack-defined Training Yard entries when the master switch is enabled. |
| `villageBuildings.spawnGarrisons` | `true` | boolean | Allows newly generated Barracks, Sentry Posts, integrated Watchtowers, and Training Yards to create one bounded guard group. Existing buildings are not staffed later. |
| `villageBuildings.maximumGuardsPerBuilding` | 4 | 0–4 guards | Global staffing ceiling. Barracks may use 4, Sentry Posts at most 1, and integrated Watchtowers or Training Yards at most 2. |
| `villageBuildings.guardGunChanceMultiplier` | 3.0 | 0–3 | Multiplies only village-garrison guards' one-time difficulty gun chance, clamped to 1.0. Other natural-loadout and safety gates remain active. |
| `villageBuildings.placeArmoryBlocks` | `true` | boolean | Allows a newly generated Village Armory building to place one public Armory block. |
| `villageBuildings.stockArmories` | `true` | boolean | Allows a newly placed village Armory to receive its bounded ammunition-only starting stock. Disabling this still places an empty public Armory. |
| `performance.visibilitySampleIntervalTicks` | 2 | 1–10 ticks | Lower values update visibility sooner but perform more line-of-sight checks. |
| `performance.movementDecisionIntervalTicks` | 5 | 1–20 ticks | Lower values reconsider movement more often. |
| `performance.pathRecalculationIntervalTicks` | 20 | 5–100 ticks | Lower values request navigation paths more often. |
| `performance.gunDataRefreshIntervalTicks` | 20 | 5–200 ticks | Lower values refresh cached TaCZ weapon data more often. |
| `performance.unsafeShotCacheTicks` | 2 | 0–5 ticks | Retained for older configurations; native shot safety is now checked per round. |
| `performance.repositionProgressCheckTicks` | 10 | 5–40 ticks | Replan after twice this duration without route progress, with a minimum of 20 ticks. |

The performance defaults are the recommended starting point. Increasing an interval generally reduces server work at the cost of a small response delay. Decreasing an interval does the reverse. Safety checks, per-tick shot limits, native burst cancellation, and other hard fail-closed limits are deliberately not configurable.

Config version 16 adds the village-defense and patrol-route controls above.
Migration initializes only these new fields. It does not rewrite saved squad
routes, defense networks, readiness state, building records, guard orders, or
any earlier administrator setting. Timing changes apply to the next relevant
state transition; an already scheduled deadline is not silently rebased.

Patrol settings never relax the route format's hard limits of 64 waypoints,
256 blocks per segment, loaded-destination-only navigation, bounded path retry,
or one route per squad. Automatic defense duties retain the existing loaded-
guard, slot, candidate, review, and travel-distance ceilings. These safeguards
remain fixed so increasing a movement speed or duration cannot create an
unbounded scan, chunk load, or pathfinding loop. `/vgg inspect <guard>` reports
the effective patrol policy, and `/vgg defense status` reports the effective
readiness timings and duty policy.

The built-in relative frequencies, per-building guard limits, combined
difficulty population bounds, nearby armed-guard caps, and Armory reward are
documented in [Village Defense Building balance](village-defense-balance.md).

## Guard accuracy and player combat

Open **Combat → Guard Accuracy** in the server's Fzzy Config screen. To reduce
accuracy only against players, enable **Override Against Players**, start with
**Aim Error Against Players = 2.0**, and select **Aim Point Against Players =
Center Mass**. Leave the general Aim Error at 0 and Aim Point at Eye Level to
preserve existing mob combat. The override applies to every guard targeting a
player; it does not change ownership or which players a guard can attack.

The shipped defaults remain zero added error, eye-level aim, and the player
override off. The saved 2°/Center Mass player values take effect only after the
override is enabled. Existing guards use changed aiming settings on subsequent
shots; they do not need to respawn.

After updating the mod, open the world or start the server once so it creates
the new aiming settings. For a file edit, then stop the server or close the
singleplayer world. In `config/villagerguardgunners/guard_gunners.toml`, replace the existing
`[combat.aiming]` section with this starting preset, then restart. Keep the rest
of the file and its version field intact; do not add a duplicate section.

```toml
[combat.aiming]
errorDegrees = 0.0
aimPoint = "EYE_LEVEL"
overridePlayers = true
playerErrorDegrees = 2.0
playerAimPoint = "CENTER_MASS"
```

| Player adjustment | Error | Aim point | Use |
| --- | ---: | --- | --- |
| Light | 1.0° | Center Mass | Smaller deviation when the starting preset feels too inaccurate |
| Starting preset | 2.0° | Center Mass | A moderate adjustment supported by the controlled tests |
| Stronger | 3.0° | Center Mass | More deviation if guards remain too accurate |

These are manual settings, not named preset buttons or hit percentages. Keep
weapons, distance, armor, and guard counts consistent when comparing values.
Test with the guns and terrain your players actually encounter. Groups remained
very lethal in the [controlled tests](guard-accuracy-validation.md), even with
added error. Values up to 10° are supported, but the gameplay comparison tested
1–3°; larger values have not been established as balanced.

**Aim Error** adds a bounded deviation before TaCZ's own gun spread. It is
measured in degrees, so the same error covers more space farther away. Zero
does not remove gun spread. **Aim Tolerance** is the separate facing threshold
that permits firing; reducing it does not make shots randomly less accurate.
Center Mass changes the intended target point and usually reduces headshots,
but low cover can block that point while the target's head is visible.

When the player override is off, players inherit both general settings. To
adjust all targets, leave the override off and change general Aim Error and
Aim Point. To return to the shipped aiming behavior, set general error to 0,
general point to `EYE_LEVEL`, and turn the override off. Saved player values
can remain in the file while inactive.

Shot safety remains active at every setting. Guards can withhold fire around
allies, including allies behind a target, or near an unsafe explosive impact.
Some custom gun scripts use projectile behavior the guard safety checks cannot
evaluate; those guns can be refused even at zero added error. Use
`/vgg inspect @e[type=guardvillagers:guard,sort=nearest,limit=1]` to inspect the
firing decision and last shot-safety result. `UNSUPPORTED_TRAJECTORY`
identifies an unsupported trajectory such as a custom `calcSpread` script.
See [shot safety](guard-shot-safety.md) for the integration limits.

## Village Defense Building integration

Config version 12 introduced `villageBuildings.enabled`. Version 13 added the
five default-on building-type controls. Version 14 adds one-shot garrisons, a
global guard ceiling, and the village-garrison gun-chance multiplier. Migration
preserves existing master and building choices, initializes newly introduced
settings to their safe defaults, and keeps the complete 1.4 Watchtower,
loadout, combat, safety, ammunition, squad, and Armory policy. The master switch
stays off by default so server operators can choose whether to extend village generation.

When enabled, the mod performs one bounded, additive pool update during server
startup. It does not scan villages or chunks and cannot change existing village
pieces. The supported buildings, strict datapack format, atomic reload behavior,
configuration precedence, restart requirement, compatibility limits, and
performance boundaries are documented in
[Village Defense Buildings](village-defense-buildings.md).

## Watchtower loadout chance

Config version 11 adds only `watchtowers.guardGunChanceMultiplier`. Migrating
a version-10 file initializes that field to 1.5 and preserves every existing
watchtower, natural-loadout, combat, and logistics value. The multiplier is
read once when a newly generated tower guard reaches the existing loadout
transaction; it creates no recurring scan or AI task. A value of 1 matches
ordinary natural guards, 0 prevents tower gun upgrades, and results above 100%
are clamped. The configured difficulty pool, enabled source, starting weapon,
nearby cap, pool conditions, inventory capacity, TaCZ resolution, and
explosive permission remain authoritative.

## Armory resupply policy

Config version 9 adds the synchronized `armoryResupply` section. Migrating a
version-8 file initializes only these new fields and retains every prior
combat, safety, ammunition, natural-loadout, and squad-command choice. Fzzy
Config validates each numeric hard bound. The cross-field rule that the target
reserve must be at least the low-reserve trigger is checked at the request
boundary; an invalid pair fails closed and `/vgg inspect` reports it rather
than silently rewriting either administrator choice.

The configuration snapshot is immutable and contains no entity, level,
inventory, path, reservation, or Armory reference. Search radius and candidate
cap bound a transient section index populated only by block-entity load and
unload events; they do not enable a block scan or chunk load. Guards assess
need on stable UUID-staggered ticks, skip the index entirely when blocked or
above the reserve trigger, and use cooldown/backoff rather than per-tick
retries. `/vgg inspect` reports the registered runtime state, last outcome,
current gate and need, selected Armory slot, bounded candidate/path counts,
last transfer, and next action. Inspection itself performs no search, path, or
mutation.

## Natural loadout and squad-command policy

Config version 8 adds bounded temporary squad-command dispatch. A dispatch
queries only loaded guards around the issuing player, applies only to matching
stable squad identities, rechecks the existing owner/reputation/Hero
authorization per guard, and stops at the configured member cap. Unloaded and
distant members are deliberately unchanged. Command records are separate from
permanent Guard Orders, equipment, patrol positions, and squad membership.

Config version 7 established the synchronized server policy for natural guard
loadouts. Peaceful is deliberately disabled and does not expose a pool setting.
Easy, Normal, and Hard each select a namespaced datapack pool independently, so
modpack authors can replace the built-in progression without editing code. The
complete versioned JSON format, built-in weights, replacement semantics, and
reload behavior are documented in
[natural-guard-loadouts.md](natural-guard-loadouts.md).

Eligibility is evaluated only during initial spawn handling. A failed master
switch, disallowed spawn source, non-crossbow starting weapon, disabled
difficulty, failed chance roll, unavailable pool, or reached population cap
stops the pipeline immediately. In particular, the nearby entity query is
deferred until every cheaper condition and the selected pool have succeeded.
Changing these settings never revisits existing guards or modifies manually
equipped weapons.

Guard Villagers' finalized spawn paths and separate player-conversion path feed
the one-time selection pipeline. Structure source markers survive the
world-generation protochunk handoff, then selection and the bounded cap query
run once on the live server level. A selected candidate is resolved against the
current TaCZ data and issued only when its validated native gun and one complete
compatible physical-ammunition stack can be committed together. Invalid,
infinite/dummy-reserve, disallowed explosive, or capacity-limited candidates
leave the original weapon and inventory unchanged and do not consume a nearby
population slot. Successful issuance stores matching versioned identities on
the guard and gun, allowing authentic natural loadouts to keep counting after
chunk/server reloads. Removing, replacing, transferring, or tampering with the
gun makes that identity inactive; ordinary manually equipped guns never count.

## Per-guard orders

The right side of an authorized guard's inventory contains persistent orders
for that guard. Inherit uses the guard’s role defaults, then server policy; Auto selects tactical behavior from the role or gun settings. These are intentionally separate from Fzzy Config: the config
defines server-wide policy and hard bounds, while an order specializes one
guard within those bounds. All new and existing guards default to Inherit/Auto.

| Order | Choices | Server boundary |
| --- | --- | --- |
| Rules of engagement | Inherit, Normal, Hostiles, Defensive, Hold Fire | Server-wide Hold Fire, exclusions, deny tags, and ally protection still win. |
| Tactical profile | Auto or any of the six profiles | Structural weapon safety remains active; per-guard selection does not change TaCZ weapon statistics. |
| Explosive weapons | Inherit, Allow, Deny | Allow never bypasses blast standoff or friendly-fire protection. |
| Dropped-ammo pickup | Inherit, Allow, Deny | The global pickup switch remains authoritative. |
| Ammunition sharing | Inherit, Allow, Deny | The global sharing switch remains authoritative; Deny applies to donating and receiving. |
| Combat movement | Inherit, Tactical, Hold, Patrol Bound | Hold affects only this add-on's combat movement; Patrol Bound still permits urgent retreat and safety repositioning. |
| Maximum range | Inherit or 8–64 block presets | This can only narrow the server cap and any profile-specific firing cap. |

Order changes use the existing server-validated container button protocol and
seven change-detected integers while the menu is open. They add no packet
channel, entity scan, or global tick handler. The active gun controller reads
immutable values and invalidates its existing TaCZ metadata cache once when an
order revision changes. See [per-guard-orders.md](per-guard-orders.md) for the
full precedence, permission, persistence, and troubleshooting contract.

An Orders Ledger can copy these seven raw values between guards without
duplicating any server configuration. It is an interaction item rather than a
config control: a blank ledger captures, a written ledger applies, and
sneak-use overwrites its stored template. Global configuration and all safety
boundaries are resolved after application and therefore cannot be widened by
the ledger.

The version-6 ammunition section establishes the synchronized, independently
switchable policy boundary for the ammunition quality-of-life work. The guard
inventory now exposes an on-demand, color-coded ammunition tooltip backed by
TaCZ's compatibility callbacks, while `/vgg inspect` reports the same model in
administrator detail. The screen caches its snapshot, refreshes immediately
when a synchronized stack changes, and otherwise refreshes at most once per
second. These inspection paths do not move ammunition or add a combat-tick
scan. Authorized players may crouch-right-click an armed guard with compatible
ammunition to perform a server-authoritative, simulate-before-commit handoff
into the dedicated slots. The handoff is interaction-driven and therefore has
no idle cost. Passive pickup is item-driven: only eligible dropped TaCZ
ammunition performs a check, at most once every 10 ticks and staggered by
entity ID. That item issues one spherical, radius-bounded guard query; guards
do not scan for drops or pathfind toward them. Pickup respects the vanilla
`mobGriefing` rule, pickup delay, and target UUID, rejects inactive guards,
depleted finite boxes, incompatible calibers, and guns using dummy or native-
infinite reserves, and leaves any transactional partial-insertion remainder in
the world. When TaCZ depletes a finite ammo box, the next native reload or
restocking action ejects that reusable box intact and frees its dedicated slot.
This bounded maintenance scans only the guard's three ammunition slots and has
no idle or per-tick cost. Emergency sharing runs only after a TaCZ-native reload
fails and both TaCZ and the add-on's complete reserve model confirm usable
ammunition is absent. The dry
recipient issues one spherical query within `ammoSharingRadius`; failure enters
the existing 20-tick dry retry window instead of searching every tick. The
nearest eligible donor may transactionally transfer no more than one recipient
magazine of loose ammunition compatible with both guns. Ammo boxes never move,
and the donor retains `donorReserveMagazines` whole attachment-adjusted
magazines. Matching owner UUIDs are required by default, with two unowned
guards considered a match. Sharing never adds navigation or continuous
inventory balancing. `/vgg inspect` reports the last sharing result and amount.
The complete user-facing stocking, ownership, reserve-type, rollback, and
troubleshooting guide is in
[ammunition-quality-of-life.md](ammunition-quality-of-life.md).
For this give-only action, authorization deliberately uses Guard Villagers'
`giveGuardStuffHOTV` policy rather than its broader inventory-opening gate. The
default `false` value permits supplying a guard without granting access to take
or replace its equipment. A guard with disabled AI rejects the interaction, as
does a gun whose TaCZ data selects dummy or native-infinite reserves that cannot
consume physical inventory ammunition.

## Rules of engagement

Rules of engagement filter Guard Villagers' existing selected target; they do
not replace its target selectors or make guards search for explicitly added
types. `GUARD_TARGETS` preserves the established behavior, `HOSTILE_MOBS_ONLY`
requires Minecraft's hostile-enemy contract, `DEFENSIVE` requires recent
aggression against the guard or a currently protected ally, and `HOLD_FIRE`
tracks an otherwise permitted target while holding position and never
triggering TaCZ.

Creative and spectator players cannot be engaged, even through target additions
or direct target assignment. Denied allied, excluded, non-hostile, and non-aggressive targets are released
so Guard Villagers may select another threat. `HOLD_FIRE` retains its target.
Every scheduled native burst round rechecks the policy, so config changes and
expired defensive memory take effect before another round consumes ammunition.
Datapacks can extend `#villagerguardgunners:always_engage` and
`#villagerguardgunners:never_engage`. Complete precedence and examples are in
[rules-of-engagement.md](rules-of-engagement.md).

## Tactical profile selection

The resolver uses this precedence:

1. A tactical profile selected in the guard's individual orders.
2. An exact entry in `tactics.gunOverrides`.
3. The gun's TaCZ type in `tactics.typeProfiles`.
4. An unambiguous structural trait: an explosive weapon becomes Demolitions, or a multi-projectile weapon becomes Breacher.
5. Generalist for missing or unknown metadata.

An override selects behavior only. It never removes structural safety traits, so assigning an explosive gun to Generalist does not disable explosive standoff or friendly-fire protection.

Type-map keys are normalized by trimming whitespace and ignoring capitalization. Duplicate aliases that select the same profile are harmless. If aliases normalize to the same type but select different profiles, the type mapping is treated as ambiguous and the resolver continues to structural traits or Generalist instead of depending on map iteration order.

The default type map is:

| TaCZ type | Profile |
| --- | --- |
| `pistol`, `smg` | Skirmisher |
| `rifle` | Generalist |
| `shotgun` | Breacher |
| `sniper` | Marksman |
| `mg` | Support |
| `rpg` | Demolitions |

Exact gun overrides use a namespaced TaCZ gun ID such as `example:precision_rifle`. Profile values are serialized as `GENERALIST`, `SKIRMISHER`, `BREACHER`, `MARKSMAN`, `SUPPORT`, or `DEMOLITIONS`.

## Tactical profile controls

Each profile section under `tactics.<profile>` exposes the same six bounded controls:

| Profile field | Range | Meaning |
| --- | ---: | --- |
| `preferredRangeMultiplier` | 0.25–4.0 | Multiplies TaCZ effective range when choosing where to approach. |
| `retreatDistanceMultiplier` | 0.25–4.0 | Multiplies `combat.closeRangeDistance`. |
| `visibleTicksAdjustment` | -40–40 ticks | Adds to `combat.requiredVisibleTicks`; the final result remains bounded to 0–40. |
| `aimToleranceMultiplier` | 0.25–4.0 | Multiplies `combat.aimToleranceDegrees`; smaller values require more precise aim. |
| `limitFiringToEffectiveRange` | boolean | Adds a profile-specific effective-range limit beneath the global hard cap. |
| `maximumFiringRangeMultiplier` | 0.25–4.0 | Multiplies TaCZ effective range when that profile-specific limit is enabled. |

Recommended defaults are intentionally conservative and do not alter TaCZ weapon statistics:

| Profile | Preferred | Retreat | Visible ticks | Aim tolerance | Effective-range limit | Limit multiplier |
| --- | ---: | ---: | ---: | ---: | --- | ---: |
| Generalist | 1.00 | 1.00 | +0 | 1.00 | No | 1.00 |
| Skirmisher | 0.75 | 1.00 | -1 | 1.00 | Yes | 1.25 |
| Breacher | 0.65 | 1.00 | +0 | 1.00 | Yes | 1.10 |
| Marksman | 1.00 | 2.00 | +3 | 0.65 | No | 1.00 |
| Support | 0.90 | 1.50 | +1 | 0.80 | No | 1.00 |
| Demolitions | 1.00 | 1.00 | +3 | 0.65 | No | 1.00 |

Explosive minimum firing distance is calculated independently as the greater of the profile retreat distance and `explosion radius × explosiveStandoffRadiusMultiplier + explosiveStandoffAdditionalBlocks`. If that minimum exceeds the global maximum firing range, the firing window fails closed.

For non-explosive weapons, a custom profile whose firing cap is narrower than its requested retreat or preferred distance is normalized into that cap. This keeps movement inside the only usable firing window. Explosive standoff is never reduced this way; an impossible explosive window continues to fail closed.

The firing window is inclusive at both boundaries. Missing, stale, unreadable, or contradictory tactical metadata cannot bypass it: the gun goal yields to Guard Villagers' lower-priority combat and retries only at the bounded gun-data refresh cadence. Unreadable effective-range properties also fail closed for profiles that derive their firing cap from that value. These checks are constant-time reads of cached values and do not invoke TaCZ property scripts or scan configuration maps from the combat tick.

Config version 8 adds the `squads` section. Loading a version-7 file selects a
48-block one-shot query, a 32-member hard cap, 1200-tick positional commands,
400-tick Focus commands, a 12-block Defend radius, and a 32-block air projection.
Existing natural-loadout, combat, movement, tactical, engagement, safety,
ammunition, and performance values are retained. Config version 7 added the
`naturalLoadouts` section. Loading a version-6 file
selects structure spawns only, preserves non-crossbow equipment, disallows
explosive natural weapons, uses a 64-block cap radius, and initializes the
35%/1, 50%/2, and 65%/2 Easy/Normal/Hard defaults. Existing combat, movement,
tactical, engagement, safety, ammunition, and performance values are retained.
Config version 6 added the `ammunition` section. Loading a version-5 file enables
passive pickup and emergency sharing, selects 1.5-block and 8-block radii,
retains one donor magazine, and requires matching guard owners by default.
Existing combat, movement, tactical, engagement, safety, and performance values
are retained. Config version 5 added the `engagement` section. Loading a version-4 file selects
`GUARD_TARGETS`, a 200-tick defensive window, and empty override sets, preserving
the previous target behavior. Version 4 added `safety.allies`; loading a
version-3 file initializes its safe empty-list and enabled-relationship
defaults without changing existing settings. Version 3 added tactical
mappings, profile sections, and explosive-standoff controls; loading a
version-2 file initializes those values while retaining its existing combat,
movement, safety, and performance choices. Loading a version-1 file also
initializes block preservation to its safe `true` default.

Static modpack integrations should prefer the additive
`#villagerguardgunners:guard_allies` entity-type tag. Explicit config exclusions
win over additions, the tag, broad villager/golem checks, ownership, alliances,
and compatibility providers; another guard and the shooter's owner remain
non-bypassable. The complete datapack and Java-provider contract is documented
in [modpack-ally-protection.md](modpack-ally-protection.md).

## Gun metadata and update timing

The live guard controller caches each gun's TaCZ ID, type, attachment/script-aware projectile count and explosion traits, resolved profile, and normalized tactical envelope during its existing low-frequency gun-data refresh. Guard movement consumes the cached `preferredRangeMultiplier` and `retreatDistanceMultiplier` results, while trigger eligibility consumes the cached visibility, aim, minimum-range, and maximum-range results. Generalist exactly retains the previous movement, visibility, aim, and global-cap behavior. Initial shots and every scheduled native burst round must be inside the same cached firing window. `/vgg inspect` exposes the active requirements and complete cached envelope.

## On-demand diagnostics

An operator with permission level 2 can inspect a guard without enabling a
recurring debug mode:

`/vgg inspect <guard>`

For example:

`/vgg inspect @e[type=guardvillagers:guard,sort=nearest,limit=1]`

The report shows the active decision gate, target, rules-of-engagement mode and
reason, retreat/preferred/maximum ranges, cached visibility,
facing error, movement, the last shot-safety
result and its recorded geometry, gun identity, magazine/chamber state,
actual compatible reserve units and scan completeness, TaCZ reload/bolt/cooldown/charge state, and the cached tactical
profile, resolution source, structural traits, and normalized envelope. Its
shot-safety result is the last actual firing check; inspection does not sample
another shot or advance the guard’s aim state.

It also reports natural-loadout authentication, the last in-memory
selection/issuance result, and persisted provenance fields. This is an
on-demand NBT/item check and does not enable recurring diagnostics.

Village Defense Building configuration, accepted definitions, live pool
weights, and reload or startup errors are available through the permission-
level-2 `/vgg villagebuildings status`, `entries`, `pools`, and `errors`
commands. These commands inspect bounded registry and immutable publication
state only. They do not search for generated villages or load chunks.

## Legacy configuration policy

Older development builds produced `general.toml`, `guard_gunner.toml`, `tacz.toml`, `villager.toml`, and `blueprint.toml`. Those files controlled the removed debug, custom shooting, reload, infinite-ammo, accuracy, hand-authored rebalance, gunsmith, and blueprint systems. The new implementation uses the distinct `guard_gunners.toml` identifier and does not read or migrate the legacy values.

This separation is intentional. For example, the old ranges mixed squared and linear distances, while old accuracy and reload multipliers have no safe equivalent in TaCZ's native weapon path. Automatically copying those values could silently alter weapon behavior or restore parts of the removed rebalance.

When any legacy file is present, the mod logs one startup warning and leaves the file untouched. It is safe to retain the old files for reference or remove them after making a backup. New settings should be configured from the defaults in `guard_gunners.toml`.

## Release checks

For a release candidate, validate the config in this order:

1. Run `./gradlew test` for schema, bounds, legacy-file, and runtime-consumer contracts.
2. Run `./gradlew runGameTestServer` for server loading, synchronization-facing registration, enable/disable behavior, native TaCZ firing, and safety integration.
3. Run `./gradlew runGameTestServer -Pstep13Performance=true` for the combined village-building and 20-scenario guard, ammunition, sidearm, and Armory scalability matrix.
4. Run `./gradlew verifyPhase8PerformanceReports verifyVillageBuildingPerformanceReport` and `./gradlew verifyReleaseArtifact`.
5. Run `./gradlew assembleVillageDefenseBuildingEvidence` to bind the verified JAR, exact village-building evidence, and the complete companion performance manifest with byte sizes and SHA-256 digests.
6. Run `./gradlew verifyAmmunitionReleaseCandidate` without the performance property to execute the aggregate automated ammunition gate, including the normal correctness GameTest server.
7. Run `./gradlew prepareVillageDefenseBuildingManualValidation` to create the exact-candidate JAR, checksum, manifests, guide, and results handoff.
8. Complete the retained Village Defense Building matrix and run `./gradlew verifyVillageDefenseBuildingManualChecklist`. Public packaging remains blocked until this and the broader manual checklist pass.

Performance JSON reports include the complete active config snapshot and the
SHA-256 of the production JAR built before the matrix. The performance working
directory's generated `guard_gunners.toml` is reset before a matrix so stale
local tuning cannot be mistaken for the default reference profile. Verification
also parses every JFR and checks p95/p99 against the unarmed baseline captured
in that same run.

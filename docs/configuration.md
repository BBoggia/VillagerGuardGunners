# Fzzy Config Guide

Villager Guard Gunners has one server-authoritative Fzzy Config file:

`config/villagerguardgunners/guard_gunners.toml`

The same settings are available through Fzzy Config's UI. The server owns the active values, synchronizes them to clients, and requires permission level 4 to edit them. Most changes take effect on the next relevant AI decision. Cached TaCZ range and tactical metadata may take up to the configured gun-data refresh interval to update.

The live guard controller caches each gun's TaCZ ID, type, attachment/script-aware projectile count and explosion traits, resolved profile, and normalized tactical envelope during its existing low-frequency gun-data refresh. Guard movement consumes the cached `preferredRangeMultiplier` and `retreatDistanceMultiplier` results, while trigger eligibility consumes the cached visibility, aim, minimum-range, and maximum-range results. Generalist exactly retains the previous movement, visibility, aim, and global-cap behavior. Initial shots and every scheduled native burst round must be inside the same cached firing window. `/vgg inspect` exposes the active requirements and complete cached envelope.

## Supported settings

Defaults are the recommended native-TaCZ guard profile. TaCZ remains authoritative for hostile-entity effects, ammunition, reloads, fire modes, projectile behavior, recoil, spread, sounds, heat, and attachments. The block-preservation option only filters the mutable block list from a guard-owned native explosion after TaCZ and Forge have computed it.

| Setting | Default | Allowed range | Purpose and cost tradeoff |
| --- | ---: | ---: | --- |
| `enabled` | `true` | boolean | Enables the guard gun goal. Disabling it leaves Guard Villagers' normal behavior available. |
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
| `combat.requiredVisibleTicks` | 5 | 0–40 ticks | Base continuous visibility required before firing; the selected tactical profile may adjust the final cached requirement. |
| `combat.maximumEngagementRange` | 32 | 8–64 blocks | Hard firing-distance cap. TaCZ's effective range remains the preferred distance used for movement and damage falloff. |
| `combat.closeRangeDistance` | 4 | 1–8 blocks | Distance at which a guard tries to retreat. |
| `combat.aimToleranceDegrees` | 10 | 1–45 degrees | Base maximum aiming error accepted before firing; the selected tactical profile may multiply the final cached tolerance. |
| `movement.approachSpeed` | 1.0 | 0.1–2.0 | Navigation speed while approaching. |
| `movement.retreatSpeed` | 0.75 | 0.1–1.0 | Navigation speed while retreating. |
| `movement.repositionSpeed` | 0.75 | 0.1–1.0 | Navigation speed while finding a clear firing lane. |
| `movement.friendlyRepositionTicks` | 20 | 5–100 ticks | Delay before retrying a lane blocked by a protected entity. |
| `tactics.typeProfiles` | seven mappings | validated map | Maps TaCZ type labels to profiles. Keys are trimmed and matched without regard to capitalization; conflicting normalized aliases are ignored. |
| `tactics.gunOverrides` | empty | resource-ID map | Selects a profile for an exact TaCZ gun ID. This has the highest classification precedence. |
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
| `performance.visibilitySampleIntervalTicks` | 2 | 1–10 ticks | Lower values update visibility sooner but perform more line-of-sight checks. |
| `performance.movementDecisionIntervalTicks` | 5 | 1–20 ticks | Lower values reconsider movement more often. |
| `performance.pathRecalculationIntervalTicks` | 20 | 5–100 ticks | Lower values request navigation paths more often. |
| `performance.gunDataRefreshIntervalTicks` | 20 | 5–200 ticks | Lower values refresh cached TaCZ weapon data more often. |
| `performance.unsafeShotCacheTicks` | 2 | 0–5 ticks | Reuses fail-closed unsafe-shot results. Zero preserves same-tick deduplication but disables cross-tick reuse. |
| `performance.repositionProgressCheckTicks` | 10 | 5–40 ticks | Lower values check blocked repositioning progress more often. |

The performance defaults are the recommended starting point. Increasing an interval generally reduces server work at the cost of a small response delay. Decreasing an interval does the reverse. Safety checks, per-tick shot limits, native burst cancellation, and other hard fail-closed limits are deliberately not configurable.

## Natural loadout policy

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
for that guard. These are intentionally separate from Fzzy Config: the config
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

Denied allied, excluded, non-hostile, and non-aggressive targets are released
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

## On-demand diagnostics

An operator with permission level 2 can inspect a guard without enabling a
recurring debug mode:

`/vgg inspect <guard>`

For example:

`/vgg inspect @e[type=guardvillagers:guard,sort=nearest,limit=1]`

The report shows the active decision gate, target, rules-of-engagement mode and
reason, retreat/preferred/maximum ranges, cached visibility,
aim error, movement, muzzle/projectile/blast safety padding, friendly-fire blocker and ally-classification reason, gun identity, magazine/chamber state,
actual compatible reserve units and scan completeness, TaCZ reload/bolt/cooldown/charge state, and the cached tactical
profile, resolution source, structural traits, and normalized envelope. Its
fresh descriptive safety query runs only for the requested command.

It also reports natural-loadout authentication, the last in-memory
selection/issuance result, and persisted provenance fields. This is an
on-demand NBT/item check and does not enable recurring diagnostics.

## Legacy configuration policy

Older development builds produced `general.toml`, `guard_gunner.toml`, `tacz.toml`, `villager.toml`, and `blueprint.toml`. Those files controlled the removed debug, custom shooting, reload, infinite-ammo, accuracy, hand-authored rebalance, gunsmith, and blueprint systems. The new implementation uses the distinct `guard_gunners.toml` identifier and does not read or migrate the legacy values.

This separation is intentional. For example, the old ranges mixed squared and linear distances, while old accuracy and reload multipliers have no safe equivalent in TaCZ's native weapon path. Automatically copying those values could silently alter weapon behavior or restore parts of the removed rebalance.

When any legacy file is present, the mod logs one startup warning and leaves the file untouched. It is safe to retain the old files for reference or remove them after making a backup. New settings should be configured from the defaults in `guard_gunners.toml`.

## Release checks

For a release candidate, validate the config in this order:

1. Run `./gradlew test` for schema, bounds, legacy-file, and runtime-consumer contracts.
2. Run `./gradlew runGameTestServer` for server loading, synchronization-facing registration, enable/disable behavior, native TaCZ firing, and safety integration.
3. Run `./gradlew runGameTestServer -Pphase8Performance=true` for the isolated 14-workload scalability matrix, including dropped-ammo and dry-sharing pressure cases.
4. Run `./gradlew verifyPhase8PerformanceReports` and `./gradlew verifyReleaseArtifact`.
5. Run `./gradlew assembleReleaseCandidateEvidence` to bind the verified JAR and exact performance evidence set into a deterministic manifest containing each file's byte size and SHA-256 digest, plus the conventional JAR checksum.
6. Run `./gradlew verifyAmmunitionReleaseCandidate` without the performance property to execute the aggregate automated ammunition gate, including the normal correctness GameTest server.

Performance JSON reports include the complete active config snapshot and the
SHA-256 of the production JAR built before the matrix. The performance working
directory's generated `guard_gunners.toml` is reset before a matrix so stale
local tuning cannot be mistaken for the default reference profile. Verification
also parses every JFR and checks p95/p99 against the unarmed baseline captured
in that same run.

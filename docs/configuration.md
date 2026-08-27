# Fzzy Config Guide

Villager Guard Gunners has one server-authoritative Fzzy Config file:

`config/villagerguardgunners/guard_gunners.toml`

The same settings are available through Fzzy Config's UI. The server owns the active values, synchronizes them to clients, and requires permission level 4 to edit them. Most changes take effect on the next relevant AI decision. A cached TaCZ range may take up to the configured gun-data refresh interval to update.

## Supported settings

Defaults reproduce the native-TaCZ implementation from before the config was added. TaCZ remains authoritative for damage, ammunition, reloads, fire modes, projectile behavior, recoil, spread, sounds, heat, and attachments.

| Setting | Default | Allowed range | Purpose and cost tradeoff |
| --- | ---: | ---: | --- |
| `enabled` | `true` | boolean | Enables the guard gun goal. Disabling it leaves Guard Villagers' normal behavior available. |
| `combat.requiredVisibleTicks` | 5 | 0–40 ticks | Continuous visibility required before firing. |
| `combat.maximumEngagementRange` | 32 | 8–64 blocks | Hard firing-distance cap. TaCZ's effective range remains the preferred distance used for movement and damage falloff. |
| `combat.closeRangeDistance` | 4 | 1–8 blocks | Distance at which a guard tries to retreat. |
| `combat.aimToleranceDegrees` | 10 | 1–45 degrees | Maximum aiming error accepted before firing. |
| `movement.approachSpeed` | 1.0 | 0.1–2.0 | Navigation speed while approaching. |
| `movement.retreatSpeed` | 0.75 | 0.1–1.0 | Navigation speed while retreating. |
| `movement.repositionSpeed` | 0.75 | 0.1–1.0 | Navigation speed while finding a clear firing lane. |
| `movement.friendlyRepositionTicks` | 20 | 5–100 ticks | Delay before retrying a lane blocked by a protected entity. |
| `safety.baseClearance` | 0.75 | 0.25–4 blocks | Extra protected-entity clearance added to TaCZ-derived spread and explosion margins. |
| `performance.visibilitySampleIntervalTicks` | 2 | 1–10 ticks | Lower values update visibility sooner but perform more line-of-sight checks. |
| `performance.movementDecisionIntervalTicks` | 5 | 1–20 ticks | Lower values reconsider movement more often. |
| `performance.pathRecalculationIntervalTicks` | 20 | 5–100 ticks | Lower values request navigation paths more often. |
| `performance.gunDataRefreshIntervalTicks` | 20 | 5–200 ticks | Lower values refresh cached TaCZ weapon data more often. |
| `performance.unsafeShotCacheTicks` | 2 | 0–5 ticks | Reuses fail-closed unsafe-shot results. Zero preserves same-tick deduplication but disables cross-tick reuse. |
| `performance.repositionProgressCheckTicks` | 10 | 5–40 ticks | Lower values check blocked repositioning progress more often. |

The performance defaults are the recommended starting point. Increasing an interval generally reduces server work at the cost of a small response delay. Decreasing an interval does the reverse. Safety checks, per-tick shot limits, native burst cancellation, and other hard fail-closed limits are deliberately not configurable.

## On-demand diagnostics

An operator with permission level 2 can inspect a guard without enabling a
recurring debug mode:

`/vgg inspect <guard>`

For example:

`/vgg inspect @e[type=guardvillagers:guard,sort=nearest,limit=1]`

The report shows the active decision gate, target and ranges, cached visibility,
aim error, movement, friendly-fire blocker, gun identity, loaded and reserve
ammunition, and TaCZ reload/bolt/cooldown/charge state. Its fresh descriptive
safety query runs only for the requested command.

## Legacy configuration policy

Older development builds produced `general.toml`, `guard_gunner.toml`, `tacz.toml`, `villager.toml`, and `blueprint.toml`. Those files controlled the removed debug, custom shooting, reload, infinite-ammo, accuracy, hand-authored rebalance, gunsmith, and blueprint systems. The new implementation uses the distinct `guard_gunners.toml` identifier and does not read or migrate the legacy values.

This separation is intentional. For example, the old ranges mixed squared and linear distances, while old accuracy and reload multipliers have no safe equivalent in TaCZ's native weapon path. Automatically copying those values could silently alter weapon behavior or restore parts of the removed rebalance.

When any legacy file is present, the mod logs one startup warning and leaves the file untouched. It is safe to retain the old files for reference or remove them after making a backup. New settings should be configured from the defaults in `guard_gunners.toml`.

## Release checks

For a release candidate, validate the config in this order:

1. Run `./gradlew test` for schema, bounds, legacy-file, and runtime-consumer contracts.
2. Run `./gradlew runGameTestServer` for server loading, synchronization-facing registration, enable/disable behavior, native TaCZ firing, and safety integration.
3. Run `./gradlew runGameTestServer -Pphase8Performance=true` for the isolated 12-workload scalability matrix.
4. Run `./gradlew verifyPhase8PerformanceReports` and `./gradlew verifyReleaseArtifact`.

Performance JSON reports include the complete active config snapshot. The performance working directory's generated `guard_gunners.toml` is reset before a matrix so stale local tuning cannot be mistaken for the default reference profile.

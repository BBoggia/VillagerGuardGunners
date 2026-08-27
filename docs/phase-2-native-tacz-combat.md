# Phase 2: Native TaCZ Guard Combat

Phase 2 replaces the addon's custom firearm simulation with TaCZ 1.1.8-hotfix's server-side `IGunOperator` implementation for living entities.

## Ownership boundary

The addon now owns only guard decisions:

- choose and look at the Guard Villagers target;
- approach an obstructed or distant target;
- decide whether the trigger may be pressed;
- request a TaCZ reload or manual bolt operation;
- provide charge-trigger progress for datapack-defined charge guns;
- withhold a due shot when a protected friendly occupies the firing corridor.

TaCZ owns draw and attachment cache state, aim progress, cooldown validation, shotgun pellets, projectile creation, spread, heat and overheat, ammunition consumption, magazine and chamber rules, scripted reloads, gun events, sounds, and observer synchronization. Phase 3 adds the small amount of server-side trigger timing needed because non-player entities do not run TaCZ's client input scheduler.

## Runtime flow

1. `GuardGunCombatGoal` activates only for a live target and an `IGun` in the guard's main hand.
2. `NativeGunController` detects a changed `ItemStack` by identity and calls `IGunOperator.draw` once, allowing TaCZ to rebuild its attachment property cache. While active it re-resolves the gun definition once per second, so a TaCZ resource reload can replace or restore data without per-tick lookup work.
3. The goal samples visibility at a fixed cadence and recalculates a path no more than once every 20 ticks while movement is needed.
4. A due trigger delegates to `IGunOperator.shoot`. The returned `ShootResult` determines whether the controller waits, requests `reload`, requests `bolt`, or applies a bounded dry/overheat retry delay.
5. TaCZ's own living-entity tick advances reload, bolt, aim, heat, and synchronized state.

## Fire modes and special gun data

- Semi-automatic and automatic cadence are validated against TaCZ's native shoot timing.
- Burst trigger calls use TaCZ's native server-side `CycleTaskHelper`; TaCZ expands the configured count/BPM, consumes each round, creates every projectile, and sends its normal observer packets.
- Multiple-projectile weapons use TaCZ's configured bullet count and spread.
- Manual-action guns request TaCZ's bolt operation when their chamber is empty.
- Inventory-fed guns consume compatible ammunition through the combined Guard/Phase 1 item capability.
- Charge guns use the TaCZ `ChargeData` increase, maximum, cooldown, type, and post-fire decrease values. The addon supplies trigger progress because an AI entity has no physical client input, while TaCZ validates and applies the charged shot.

## Safety and performance

- No custom projectile, damage, spread, recoil, sound, burst timer, or shot packet remains.
- The controller and its suppliers are allocated once with the goal.
- Gun data and charge data are cached and checked for TaCZ resource-reload replacement once every 20 ticks while the gun goal is active.
- Visibility is sampled every two ticks.
- Navigation is recalculated at most every 20 ticks while needed.
- Reload waits through TaCZ's post-shot cooldown; only a confirmed absence of compatible ammunition enters the 20-tick dry/melee fallback.
- Friendly-fire checks occur only for a due shot or while TaCZ still owns a scheduled native burst. Guards, villagers, iron golems, the guard owner, and allied living entities block a corridor expanded for runtime attachment/script inaccuracy, heat, blast radius, and plausible movement during the burst. Safe cache entries never cross ticks.

## Removed legacy systems

Phase 2 removes the custom guard bullet and renderer, handcrafted firing/reload helper, manual recoil and inaccuracy handler, duplicated fire-mode strategies, custom movement and melee experiments, dormant behavior-tree implementation, patrol extension, the copied Guard Villagers arm-pose mixin, and the Fzzy/Kotlin rebalance configuration dependencies. TaCZ applies its third-person animation to humanoid `LivingEntity` models holding a gun. Phase 6 adds only narrow tail injections for the two Guard models because Guard Villagers performs additional arm animation after TaCZ's base humanoid injection.

## Verification

Run:

1. `./gradlew test`
2. `./gradlew build`
3. `./gradlew runGameTestServer`

The automated layer validates charge/backoff state, firing-corridor geometry, dependency cleanup, absence of the custom projectile, and source-level native delegation. Forge GameTests additionally construct a real Guard and TaCZ Glock, initialize TaCZ's operator/property cache, and verify persistence, migration, capability, menu integration, and Phase 4 shield-goal compatibility. Observed client animation remains in the manual validation matrix.

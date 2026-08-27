# Phase 3: Native Weapon State and Bounded Cadence

Phase 3 completes the server-side state boundary around TaCZ's `IGunOperator`. The addon decides when a guard may attempt a trigger input; TaCZ remains authoritative for every actual shot, projectile, ammo mutation, reload, bolt, heat check, event, and observer update.

## Why timestamps are used

TaCZ updates `getSynShootCoolDown()` at the end of a living entity's tick. A mob goal runs earlier in that tick, so reading the synchronized value from the goal observes the previous tick and can unnecessarily skip an additional tick.

The controller instead uses the same inputs as TaCZ 1.1.8-hotfix's server validator:

- `GunData.getShootInterval` for semi/automatic fire;
- `BurstData.getMinInterval` for burst-mode validation;
- `ShooterDataHolder.shootTimestamp` for the last accepted trigger;
- TaCZ's five-millisecond cooldown tolerance.

Every trigger attempt uses an `IGunOperator.shoot(..., timestamp)` overload. This is not a validation bypass: TaCZ independently checks the timestamp, network window, reload/draw/bolt/sprint state, ammunition, heat, charge progress, Forge event, and gun data before returning `SUCCESS`.

## State transitions

| Input/result | Controller action |
| --- | --- |
| `SUCCESS` | Advance cadence, clear dry state, and apply TaCZ charge decrease/reset data. |
| `NO_AMMO` | Wait through native shoot cooldown, then request reload. Yield the gun goal for 20 ticks only when TaCZ confirms there is no compatible ammunition. |
| `NEED_BOLT` | Request native bolt and apply a short retry delay. |
| `NOT_DRAW` | Request native draw and apply a short retry delay. |
| `IS_SPRINTING` | Stop sprinting and retry on the next tick. |
| `COOL_DOWN`, reloading, drawing, bolting | Yield to TaCZ's state machine and retry briefly. |
| `OVERHEATED` | Back off for five ticks while TaCZ cools the weapon. |
| `FORGE_EVENT_CANCEL`, `NETWORK_FAIL`, `UNKNOWN_FAIL` | Back off for five ticks to avoid hot-looping a rejected action. |
| `NOT_GUN`, `ID_NOT_EXIST` | Suspend firing and retry the TaCZ gun definition at a 20-tick cadence, allowing resource reload recovery without a weapon swap. |

Dry and invalid state is intentionally separate from TaCZ state. A dry gun is retried after about one second; while it is dry, `GuardGunCombatGoal` stops and Guard Villagers' lower-priority melee goal is free to run. Invalid gun/resource state is also removed from the active goal, but a cheap map lookup is allowed once per second so `/reload` can recover the same held stack.

## Charge behavior

The guard supplies the input progress a player client would normally produce:

- `AUTO` and `DELAY` charge to the datapack maximum and fire;
- `HOLD` charges fully, then emulates trigger release by firing;
- `DELAY` resets completely after a successful shot;
- other charge types retain progress according to `decrease_on_fire`;
- lost aim or an unsafe firing corridor decays progress using `decrease_per_tick`.

TaCZ validates the supplied progress against its own charge definition before accepting the shot.

## High-rate automatic fire

Minecraft normally provides one AI update every 50 ms. For automatic guns whose resolved interval is below 50 ms, `NativeFireCadence` carries timestamp deadlines across ticks so the native trigger rate is not silently reduced to 1,200 RPM.

The scheduler has hard performance and safety bounds:

- it is used only for uncharged `AUTO` fire, never semi or burst;
- at most four native trigger calls may occur for one guard in one server tick;
- at most one healthy server tick of timing debt is retained;
- a lag spike discards old debt rather than emitting a delayed catch-up burst;
- zero-length intervals are clamped to one millisecond, while unusable negative/non-finite data suspends the weapon;
- it allocates no collections, futures, timers, tasks, or threads during combat.

The default TaCZ attachment RPM modifier is capped at 1,200 RPM, but heat interpolation and third-party datapacks can resolve differently. The bounded path protects those cases without adding overhead to ordinary weapons.

## Native burst safety

TaCZ 1.1.8-hotfix's public server-side `IGunOperator.shoot` enters `ModernKineticGunScriptAPI.shootOnce`, which expands burst mode through the server-ticked `CycleTaskHelper`. One accepted guard trigger therefore produces TaCZ's configured burst count and BPM without an add-on scheduler, thread, projectile, or packet.

TaCZ posts a cancellable `GunFireEvent` immediately before every scheduled round consumes ammunition, applies heat, creates projectiles, or produces sound and recoil. Before the initial trigger, the add-on expands the friendly-fire corridor for TaCZ's runtime inaccuracy, explosion radius, burst duration, and plausible protected-entity movement. Once TaCZ accepts the burst, the add-on attaches a transient validator to that guard. Each later native round rechecks the current target, aim, engagement range, and protected-entity corridor from inside `GunFireEvent`; a stale reference, invalid callback, lost combat state, or newly unsafe corridor fails closed by canceling that round.

Safety work remains bounded: there is no background work, ordinary safe query results do not cross ticks, unsafe results may delay a new trigger for two ticks, and the fresh scheduled-burst query runs only for a native round TaCZ is already about to process. Extreme or malformed envelopes over 32 blocks fail closed instead of issuing an unbounded entity query.

## Verification

Automated checks cover:

- exact dry retry and suspension reset transitions;
- TaCZ-compatible cooldown arithmetic;
- ordinary and sub-tick timestamp cadence;
- lag-backlog discard and the four-call per-tick cap;
- use of timestamped native shoot overloads;
- explicit handling of every TaCZ 1.1.8-hotfix `ShootResult`;
- absence of custom projectile creation and asynchronous firing work.
- real Guard construction, combined capability, ammo save/migration, menu layout, TaCZ gun/operator initialization, and shield compatibility in five Forge GameTests.

Run `./gradlew test`, `./gradlew build`, and `./gradlew runGameTestServer`. In-world validation is still required for reload animation, manual bolt timing, charged weapons, heat recovery, observer synchronization, and representative third-party gun datapacks.

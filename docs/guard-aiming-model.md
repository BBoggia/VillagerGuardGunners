# Guard aiming model

Combat → Guard Accuracy controls guards' added aim error and selected target
point. Server settings apply to normal, automatic, and scheduled burst rounds.
Each round uses the same direction for [shot safety](guard-shot-safety.md) and
native TaCZ firing. Defaults retain zero added error and eye-level targeting.
Player tuning remains opt-in. Start with 2° and center-mass player aim, then
adjust for the server; see the [configuration preset](configuration.md#guard-accuracy-and-player-combat)
and [measured limits](guard-accuracy-validation.md).

## Configuration

All keys below are relative to `combat.aiming` in the existing server-controlled
guard configuration. Existing edit permissions remain unchanged.

| Key | Default | Meaning |
| --- | --- | --- |
| `errorDegrees` | `0.0` | Maximum added aim deviation, from 0 to 10 degrees, before native gun spread |
| `aimPoint` | `EYE_LEVEL` | Target eye position or `CENTER_MASS` using current bounds |
| `overridePlayers` | `false` | Use both player-specific choices when the target is a player |
| `playerErrorDegrees` | `2.0` | Added error against players when the override is enabled; recommended starting value with center-mass player aim |
| `playerAimPoint` | `CENTER_MASS` | Aim point against players when the override is enabled |

When the override is off, players inherit both general settings. Mob targets
always use the general settings. This policy resolver does not select targets
or grant permission to attack them. Creative and spectator players cannot be
engaged, even if another mod assigns them as a guard's target. Game mode is
rechecked for each native round, including queued burst rounds.
Native gun spread and the existing
`combat.aimToleranceDegrees` facing threshold remain separate.

Config version 22 initializes only these new controls when upgrading an older
configuration. Existing combat, safety, medical, loadout, and building choices
are retained. Re-reading a version-22 configuration preserves its aiming choices.
Finite values and the 0–10 degree range are validated; constructing resolved
settings with invalid values throws rather than creating an invalid direction.

## Temporary state and geometry

`GuardAimSettings` is an immutable resolved policy. `GuardAimState` belongs to
one guard on the server thread and has a per-guard seed. Its context contains
target UUID, weapon revision, policy revision, and resolved settings. Revisions
must describe actual identity/policy changes, not ordinary periodic refreshes.

`prepare` receives current geometry and game time. It returns an immutable
`GuardShotAim`, which is a proposal, not permission to fire. Invalid coordinates,
empty target bounds, and a coincident aim origin and aim point produce no
proposal. No world queries, target selection, or TaCZ actions occur in the model.

Eye-level aiming uses the supplied eye position. Center mass uses the center of
the supplied current bounding box, accommodating different sizes and poses.
The aim origin and actual projectile origin are separate: this preserves the
controller's zero-error angles while safety uses TaCZ's final projectile origin.
TaCZ 1.1.8 places the projectile halfway between the shooter's previous and
current positions, plus current eye height. Its earlier eye-minus-0.1 position
is overwritten before the constructor returns.

The drift uses two seeded disk samples interpolated with a smoothstep over
12-tick intervals. It contributes 80% of the offset; a sample indexed by the
successful-shot count contributes 20%. Their convex combination stays inside
the unit disk. Its radius scales the configured angular error around the target
direction. The cone calculation covers vertical aim without division by a zero
horizontal distance. Supplied float angles introduce ordinary numerical rounding;
the directional tests allow 0.001 degrees of numerical tolerance.

Drift depends on absolute game time, so skipped updates do not require catch-up
loops. Shot variation advances only on `commit`, not on diagnostic reads,
cooldowns, denied firing attempts, or canceled rounds. The same guard/context,
tick, successful-shot index, and geometry produce the same proposal. Different
guards need different seeds to avoid synchronized patterns.

The proposal includes its game tick, shot index, both origins, target point,
direction, yaw, and pitch. Direction represents the supplied angles before
native spread and shooter-velocity effects. It is not a prediction of the final
projectile trajectory.

## Lifecycle contract

| Situation | Required handling |
| --- | --- |
| Repeated reads or checks | Read `pending()`; it has no random or state-changing effects |
| Repeated preparation with unchanged inputs in the same tick | Reuse the same proposal object |
| New tick or changed shot geometry | Prepare a new proposal and obtain new safety approval |
| Cooldown or canceled/unsafe shot | Do not commit; retain that shot's variation |
| Visibility, movement, or eligibility interruption | `invalidateProposal()`; an old proposal cannot be committed |
| Target, weapon, or relevant settings change | Prepare with a changed context; pending state and shot index reset |
| Death, unload, dimension change, disabling combat | `reset()` and close any native burst latch |
| Game time moves backward | Preparation resets temporary state and rejects the old proposal |
| Successful round | Commit the exact current proposal once, after native success is established |

`commit` requires object identity with the current pending proposal. A duplicate,
foreign, superseded, or invalidated proposal cannot consume another round. The
caller must invalidate state immediately when eligibility changes, including
when it will not call `prepare` again that tick. Nothing in the model clears a
native burst latch by itself.

`NativeGunController` binds each native trigger to its target, held stack, gun ID,
orders revision, resolved settings, and level. The TaCZ hook checks those values
and current eligibility before each round. After fire-event subscribers return,
it rechecks eligibility, prepares the aim, and validates the current scene and
ballistic snapshot. Pitch/yaw suppliers only read the approved angles. The
projectile constructor consumes the same safety-relevant properties, even when
native ammo or heat updates would change a script's result.

One successful native projectile insertion commits the enclosing round once.
All pellets share that round's aim. A canceled event or a round with every
projectile insertion rejected does not advance the sequence. TaCZ still controls
ammo consumption, heat, charge, sounds, spread, and cadence; an insertion rejected
after ammo consumption does not refund ammo.
See [the inspected TaCZ firing sequence](guard-accuracy-testing.md).

Aiming state is not written to entity NBT or world data. Aim settling, limited
tracking speed, and per-weapon error multipliers are not part of this model.

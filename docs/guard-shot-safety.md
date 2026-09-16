# Proposed-shot safety

Every supported native round is checked against its proposed aim immediately
after native fire-event subscribers return and before ammo consumption. Normal
fire and queued burst rounds use the same check. Existing damage-event protections remain active.

## Geometry and native inputs

`GuardFriendlyFireSafety.diagnoseShot` and the proposed-shot overload of
`hasClearShot` share one implementation. They receive a `GuardShotAim`, immutable
`GuardShotEnvelope.Ballistics`, and clearance. The sampled direction is fixed;
the checker adds native spread, not the entire configured guard-error cone.
The origin matches TaCZ's final constructor position: the midpoint of the
shooter's previous and current positions plus current eye height. Invalid
directions, mismatched angles, outdated game ticks, changed projectile origins,
and changed inherited shooter velocity cannot receive approval.

`NativeGunController.captureShotBallistics` resolves speed including attachments,
runtime properties, TaCZ's global speed multiplier, and an explicitly supplied
script speed multiplier. During firing, the hook instead uses the actual speed
and spread captured by TaCZ's scheduled round, including script speed changes.
Lifetime, gravity, friction, explosion state, and fuse also use runtime properties.
The controller resolves these values after firing-event subscribers return and
before the round changes ammo or heat. `GuardShotProperties` retains their native
float values. The TaCZ projectile constructor consumes this same snapshot for
every pellet in the round instead of evaluating those properties again against
changed ammo or heat. Native clamping remains in place; a zero-delay explosion
has the same one-tick minimum fuse as TaCZ. Spread follows TaCZ's current stance and heat
inputs, with the existing 0.5 native-inaccuracy safety margin. This margin is
not an additional guard aim-error setting. Unreadable inputs return no snapshot.
The full-flight envelope uses twice the native explosion radius as a conservative
damage reach, including TaCZ's distance-based explosion damage calculation.

The flight model bounds every tick through native projectile lifetime. It does
not stop at the intended target or assume a block necessarily stops penetration.
For each tick, it bounds travel between maximum and minimum drag, including
TaCZ's water drag of 0.4. Gravity contributes a downward interval; water's reduced
gravity lies within it. Native component spread grows with travel from the
projectile origin. Inherited shooter motion is included in initial velocity.
These intervals contain possible trajectories; they are deliberately broader
than a prediction of one bullet flying through a known scene.

## World checks and limits

Each evaluation makes one bounded entity query. It includes protected entities
and other pickable entities that could cause an explosive impact, such as boats.
Candidate bounds include both current bounds and TaCZ's movement-adjusted
hitboxes. The query includes TaCZ's one-block entity-search margin.

Protected entities in the projectile envelope or forward blast envelope block
the shot, including entities beyond the target. Allies beside or behind the
muzzle do not automatically create a blast prohibition in otherwise open space.
Those exceptions and the shooter receive explicit nearby entity-impact,
block-impact, and fuse checks. Block checks include possible spread contacts,
not only the center ray. They read collision shapes without loading chunks.
They do not remove blocks, alter penetration, or create explosions.

Hard limits make the work finite:

| Limit | Value |
| --- | --- |
| Projectile lifetime | 200 ticks |
| Initial-motion travel plus maximum fall | 1,024 blocks |
| Spread, clearance, and blast padding combined | 32 blocks |
| Entity query footprint | 1,024 chunks |
| Entity candidates | 256; a 257th refuses the shot |
| Distinct nearby block positions | 8,192 |
| Collision boxes per block | 64 |

Oversized geometry, exceeded budgets, unavailable nearby impact chunks, and
invalid world data refuse the shot with a descriptive status. These limits are
not new gun range settings. A longer-lived or unusually wide custom projectile
can exceed them even when the intended target is close.

Wide hip-fire profiles can exceed the padding limit before TaCZ completes aiming,
including ordinary rifles with its default global bullet-speed multiplier. Fully
aimed profiles are substantially narrower. Guards wait for a supported safe profile; the hook never substitutes the narrower
aimed profile for a captured hip-fire round. Gameplay validation must measure
time to first shot and safety refusals separately from misses.

## Integration and diagnostic contract

The checker caches neither approvals nor refusals. Reusing immutable geometry
does not reuse a world result: allies can move and blocks can change within one
tick. Each actual round must evaluate the current scene. The combat goal retains historical results for tactical reactions but never
reuses them as permission to fire. `performance.unsafeShotCacheTicks` remains
readable in older configurations but no longer controls native shot checks.

Diagnostics retain the exact envelope they evaluated. They do not call the aim
sampler, advance shot variation, or resolve native property scripts. Obtain a
ballistics snapshot during discharge preparation and pass it to diagnostics;
do not call the controller's capture method from a read-only status command.

The native hook checks target/weapon/settings identity, orders, visibility,
readiness, and current eligibility before dispatching the firing event. After
subscribers return, it checks eligibility again, resolves the ballistic snapshot,
and evaluates the current scene once. Event-time ally movement, team changes,
and block edits therefore reach the final check. The exact proposal is committed
only after native projectile insertion.

When a guard's native round is refused, subsequent ammo operations through that
shooting script's API instance return without changing magazine, chamber, or
reserve ammo. This covers the bundled SPAS-12's post-shot chamber feed. TaCZ uses
separate API instances for reload and bolt scripts, so those operations remain
available. This does not roll back arbitrary script side effects or unrelated
changes made by event subscribers. A projectile insertion canceled after native
ammo consumption keeps TaCZ's existing ammo behavior.

These hooks apply to guards. Other shooters retain TaCZ's native property and
ammo handling.
Native bursts capture spread and speed when scheduled, before individual rounds
fire. The hook validates those captured values with each round's new proposal.
The inspection command reports an applicable recent check, including its tick
and angles, without evaluating another shot. Refusal display is independent of
the tactical reposition timer. Unsupported guns, exceeded safety limits, and
unavailable world data have distinct player-facing messages. A result applies
for at most 20 ticks and only to the same target, weapon stack, gun ID, fire mode,
weapon revision, orders, aim settings, and level. A stopped controller clears it.
The full historical result remains available to internal diagnostics.

This is support for the inspected TaCZ kinetic firing behavior, not a guarantee
for arbitrary Lua projectile code. A gun with a `calcSpread` callback is refused as `UNSUPPORTED_TRAJECTORY` because
that callback can return an arbitrary pellet pattern. Safety-relevant property
callbacks are supported through the per-round snapshot described above. Homing,
teleporting projectiles, and scripts that bypass the native shooting API need
separate compatibility work.
Ordinary script speed multipliers are captured from the actual scheduled round. Unusual collision shapes extending more than one
block from their block origin also need separate compatibility work.

The checker observes loaded entities at evaluation time. It cannot predict every
later entity movement or world edit during flight. Existing gun-damage and
explosion event protections remain necessary. The full-travel and air/water
bounds can also refuse a shot because of an ally behind cover or well below the
likely dry-air trajectory. Packaged combat validation must measure those
refusals separately from misses before release.

## Verification

`GuardShotSafetyTest` exercises off-axis and behind-target allies, repeated checks,
nearby explosive impacts, vertical shots, limits, and sampled trajectories with
changing air/water drag. Existing `GuardFriendlyFireSafetyTest` cases continue to
cover the legacy path.

The optional observer JAR includes `accuracyprobe.SafetyProbe`. Build it with
`./gradlew -I tools/accuracy-qa.gradle reobfAccuracyProbeJar`, then install it
alongside the production JAR and normal dependencies in an isolated Forge server.
The `-Dvgg.safety.output=/absolute/path/result.json` JVM property enables the
fixture. It creates test entities and a temporary wall, records assertions, and
stops the server. Use a disposable world. This fixture tests the packaged safety
API and native property resolution; it does not claim live firing integration
or multiplayer balance acceptance.

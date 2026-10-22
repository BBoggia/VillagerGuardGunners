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

Each evaluation searches bounded strips along the dominant horizontal flight
axis. Strip widths are 64 blocks; their other horizontal extent includes every
possible spread offset over that part of the flight. This avoids scanning the
empty corners of a long diagonal's bounding box. The full native lifetime and
water/air bounds still apply. Vertical trajectories retain their full horizontal
extent.

Queries include protected entities and other pickable entities that could cause
an explosive impact, such as boats. Candidates shared by multiple strips count
once against the total entity budget. Candidate bounds include both current
bounds and TaCZ's movement-adjusted hitboxes. Each strip includes TaCZ's one-block
entity-search margin, and the work budget also counts the entity-section search's
two-block margin.

Protected entities in the projectile envelope or forward blast envelope block
the shot, including entities beyond the target. Allies beside or behind the
muzzle do not automatically create a blast prohibition in otherwise open space.
Those exceptions and the shooter receive explicit nearby entity-impact,
block-impact, and fuse checks. Block checks include possible spread contacts,
not only the center ray. They read collision shapes without loading chunks.
They do not remove blocks, alter penetration, or create explosions. Block-origin
positions whose entire supported collision region misses the possible impact
path are skipped before reading their shapes. The origin search includes shapes
extending one block beyond their owner, including tall fences.

For a muzzle-side blast check, the checker can use a shorter dry-flight prefix
only after verifying that its entire possible flight volume, plus a one-block
water-contact margin, contains no fluid in loaded chunks. The prefix ends only
when every possible pellet has passed the vulnerable area's horizontal extent.
Horizontal drag cannot reverse that motion, and gravity cannot bring it back
horizontally. This removes false near-shooter ground impacts from hypothetical
water on dry ground without cutting off protection farther downrange. Vertical
shots, water, unknown chunks, an unproven escape, or an exhausted fluid-check
budget retain the full air/water envelope. Nearby impacts and fuses are still
checked; the optimization does not approve a shot by itself.

Hard limits make the work finite:

| Limit | Value |
| --- | --- |
| Projectile lifetime | 400 ticks |
| Initial-motion travel plus maximum fall | 8,192 blocks |
| Spread, clearance, and blast padding combined | 256 blocks |
| Entity search work across strips, including repeated chunks and margins | 8,192 chunks |
| Entity query strips | 256 |
| Entity candidates | 256; a 257th refuses the shot |
| Block-origin positions considered across nearby impact checks | 65,536 |
| Distinct nearby collision-shape reads | 8,192 |
| Distinct fluid checks | 8,192 |
| Collision boxes per block | 64 |

Oversized geometry, exceeded budgets, unavailable nearby impact chunks, and
invalid world data refuse the shot with a descriptive status. These limits are
not new gun range settings. A longer-lived or unusually wide custom projectile
can exceed them even when the intended target is close.

Very wide or long-lived profiles can still exceed the budgets, especially before
TaCZ completes aiming. Guards wait for a supported safe profile; the hook never
substitutes the narrower aimed profile for a captured hip-fire round. Gameplay validation must measure
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

The scheduling and projectile-insertion hooks use MixinExtras `WrapOperation`.
Each calls the existing operation exactly once, preserving other mods' redirects
and wrappers, including TacZ:Arcana's burst scheduling and bullet-spawn event.
Successful shot accounting uses the insertion operation's returned value; rejected
insertions never commit the guard's aim proposal. Fire-event validation and ammo
preservation remain required independently of these wrappers.

MixinExtras is provided by the required TaCZ JAR. The build compiles against the
embedded API and does not bundle a second copy or require an extra player download.
These hooks apply guard-specific changes only to guards. Other shooters retain
TaCZ's native property and ammo handling and any installed firing extensions.
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
cover the legacy path. Community-pack profile regressions cover long travel,
wide spread, all firing directions, shared entity budgets, dry escape, water,
nearby collisions, and fuses.

The optional `accuracyprobe.PackSafetyProbe` exercises real native firing with
installed community gun packs. Run `tools/run_guard_firing_qa.py --mode packSafety`
with the production JAR, the observer JAR, and repeated `--extra-mod` arguments
for the published Elite X Quality Guns, Daffa's Arsenal, and Maxstuff JARs and
their dependencies. It checks clear shots, allies in front and behind the target,
fire-event ally movement, refusal ammo preservation, resumed firing, and nearby
explosive impacts. Its output records exact installed-JAR hashes. Use the normal
installed-client gun-pack tests separately for rendering, normal AI, and reload
coverage.

The optional observer JAR includes `accuracyprobe.SafetyProbe`. Build it with
`./gradlew -I tools/accuracy-qa.gradle reobfAccuracyProbeJar`, then install it
alongside the production JAR and normal dependencies in an isolated Forge server.
The `-Dvgg.safety.output=/absolute/path/result.json` JVM property enables the
fixture. It creates test entities and a temporary wall, records assertions, and
stops the server. Use a disposable world. This fixture tests the packaged safety
API and native property resolution; it does not claim live firing integration
or multiplayer balance acceptance.

The packaged `accuracyprobe.ShotRegressionProbe` covers semi-auto, automatic,
burst, and pellet firing, reloads, canceled fire events and projectile insertions,
allies crossing between burst rounds, event-time scene changes, and refusal ammo
preservation. Run `tools/run_guard_firing_qa.py --mode regression` with and without
optional compatibility JARs. `--spawn-event` accepts an external Forge event class
with one `EntityKineticBullet` field and checks that it runs exactly once per spawn
attempt. Supply the class from the exact dependency version under test; obfuscated
event names can differ between versions. This observer remains separate from the
production mod. Arcana's special ammo and altered projectile trajectories need
separate safety validation beyond firing-hook coexistence.

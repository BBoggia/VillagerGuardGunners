# Guard aiming and accuracy measurements

This document describes the active [guard aiming controls](guard-aiming-model.md)
and the packaged combat fixtures used to evaluate them. Aim Error changes the
guard's supplied direction before native gun spread. Aim Tolerance controls when
a guard faces its intended target closely enough to fire; it does not add error.

## Supported runtime inspected

The integration findings below were checked against the local TaCZ
`1.1.8-hotfix` JAR, Minecraft `1.20.1`, and Forge `47.4.10`.
Recheck them when changing the supported TaCZ version.

The guard controller supplies yaw and pitch to TaCZ rather than creating its
own projectiles. It selects eye-level or center-mass aim and applies the configured bounded error. Visibility, facing, tactical range, movement, orders, and shot safety
determine whether the controller attempts to fire.

## TaCZ firing sequence

The inspected standard gun implementation follows this sequence:

1. `LivingEntityShoot` checks native readiness and posts `GunShootEvent` for the
   trigger request. On acceptance it updates shooting timestamps/charge state and
   calls the gun's `shoot` implementation with the pitch/yaw suppliers.
2. `ModernKineticGunItem` delegates shooting through its script API. The standard
   `ModernKineticGunScriptAPI.shootOnce` resolves properties, projectile count,
   speed, inaccuracy, and burst scheduling.
3. Each invocation of the scheduled round checks that the shooter is alive and
   still holds the expected stack, then posts `GunFireEvent`.
4. If that event is canceled, that round skips the subsequent firing work. On
   acceptance, the standard path sends its firing notification, reduces ammo
   where requested, and processes heat before reading the pitch/yaw suppliers.
5. Pitch and yaw are each read once for that round, before the pellet loop.
   Each pellet gets the same supplied aim, followed by native/scripted spread.
   The resulting projectile is then added to the level.

`TaCZGuardShotMixin` wraps each scheduled round and intercepts its fire-event
post to prepare and check round-specific aim before ammo consumption. It observes
successful native projectile insertion to commit once per round. Pitch/yaw
suppliers must return a single already-approved sample without advancing random
state. A trigger-level success or an uncanceled firing event is not by itself
proof that a projectile was created; the fixture records projectile creation
separately.

`CycleTaskHelper` can invoke an initial round immediately and retains subsequent
rounds for later ticks. Its timing uses wall-clock intervals and compensation.
The guard controller also supports multiple automatic shots within one server
tick. Each path receives a distinct approved proposal per round, including several
rounds in the same game tick.

Custom scripts and other implementations of the gun API can differ from this
standard path. These findings are not a guarantee for arbitrary scripted guns.

## Projectile geometry and damage

The standard kinetic projectile constructor initially places it at the shooter's
X/Z and `eyeY - 0.10000000149011612`, then overwrites that position. Its final
position uses `xOld + (x - xOld) / 2` on X/Z and the equivalent Y midpoint plus
current eye height. Safety uses this final position, including while moving.
The intentional direction still uses current eye position.

TaCZ applies weapon/attachment properties and script modifiers to spread.
`ModernKineticGunItem.doBulletSpread` supports a script `calcSpread` result,
otherwise using its normal projectile spread path. The scripted path also adds
shooter velocity. Error introduced by the guard should change the supplied aim
while leaving these native effects intact.

Projectile travel has a lifetime, speed, gravity, and friction. The standard
collision path clips against blocks and processes permitted entity penetration.
Block-hit events can change ordinary collision handling. Explosive rounds can
detonate on impact or after their delay. An intended target position is therefore
not a universal endpoint for a missed shot or an explosion-safety check.

`EntityHurtByGunEvent.Pre` exposes the target, bullet, headshot flag, base damage,
and cancellation state. After cancellation checks, TaCZ applies its headshot
multiplier and damage handling. Its ordinary and armor-piercing damage portions
can produce separate damage events. A nonfatal hit posts a hurt Post event; a
fatal hit posts a kill event. Counting only hurt Post events would omit lethal
hits.

The fixture records uncanceled Pre hits and their headshot flags, unique bullet
IDs, and Forge `LivingDamageEvent` amounts separately. Damage event amounts can
include overkill; they are not identical to health removed.

## Reproducing the packaged baseline

Use Java 17. First build the separate observation mod:

```sh
./gradlew --offline --no-daemon -I tools/accuracy-qa.gradle reobfAccuracyProbeJar
```

This optional source set produces
`build/accuracy-qa/vgg-accuracy-probe.jar`. Its classes do not enter the release
JAR. The fixture activates only when the launcher supplies `vgg.accuracy.dir`.

Run the unchanged production artifact with its four required dependencies in
an isolated client directory:

```sh
python3 tools/run_guard_accuracy_baseline.py \
  --jar /absolute/path/to/villagerguardgunners.jar \
  --mods /absolute/path/to/dependency-jars \
  --template /absolute/path/to/test-world/level.dat \
  --output build/guard-accuracy-baseline/new-run \
  --trials 3
python3 tools/report_guard_accuracy.py build/guard-accuracy-baseline/new-run
```

The installed-runtime launcher currently supports macOS Apple silicon with
Forge `1.20.1-forge-47.4.10`. It uses an isolated offline client profile and a
local integrated server, not the user's account credentials. The supplied
world metadata is read to create a fresh flat arena; existing world chunks and
player data are not copied or modified. The output directory must be new.

The runner loads production JARs through the ordinary Forge launch target. It
does not put development classes or resources on the gameplay classpath. The
manifest records the launch, dependency/library hashes, observer identity, and
production artifact hash. An observer-assisted run is not an observer-free
smoke test or dedicated multiplayer acceptance.

## Encounter protocol

- Normal difficulty; no target armor, effects, absorption, or natural healing.
- One invulnerable guard with Hold Position and Guard Targets orders, a loaded
  gun, no attachments, and no reserve ammo. The fixture selects its target.
- Glock 17 single fire, AK-47 automatic fire, and M16A4 burst fire at 8, 16, and
  32 blocks against a real survival-mode client player and an adult husk.
- A scripted strafing player at 16 blocks and a villager blocking the firing
  lane to a husk at 16 blocks.
- Eighty live-player ticks between encounters allow native spawn/respawn damage
  protection to expire. The player's ordinary damage immunity is cleared when
  an encounter begins. No health restoration occurs during an encounter.
- Fixed target positions remove knockback displacement. Husks have AI disabled;
  the strafing trajectory is positioned by the fixture. These are controlled
  accuracy observations, not free-moving combat or natural target-acquisition
  tests.
- Encounters end at target death or 160 ticks; the blocked-lane condition ends
  after 100 ticks. Repeat each condition three times for 60 encounters.

Reports separate no-fire cases from observed hits. A gun outside its tactical
range must not count as zero accuracy. Periodic firing diagnostics identify
range and safety decisions for longer encounters.

The fixture removes remaining bullets when an encounter ends. A projectile
without an observed target hit can still have been in flight at cleanup; the
report calls this `withoutObservedTargetHit`, not a confirmed miss. Kill times
are reported both from encounter start and from first firing event, in game
ticks. Wall time is recorded separately.

Three short trials are sufficient to establish basic behavior and a comparison
procedure. They do not establish a precise long-run hit rate or balanced defaults.
Further balance work needs sustained sampling, player input, multiple guards,
armor, cover, attachments, other weapon categories, and dedicated multiplayer.

## Native firing integration fixture

`accuracyprobe.FiringProbe` activates with
`-Dvgg.firing.output=/absolute/path/result.json` in an isolated normal Forge server.
It uses the packaged controller and native TaCZ projectiles with scripted,
invulnerable no-AI targets. It checks approved aim identity, exact projectile
origin, containment of initial native velocity, ammo use, one committed sample
per round, pellet grouping, cancellations, and interruption of queued bursts.
It also exercises movement, dry ammo/resupply, reload, goal restart, and sidearm
transitions. This is mechanics coverage, not multiplayer balance acceptance.

The high-rate fixture uses a test gun pack with a constant heat RPM multiplier:
TaCZ clamps the ordinary cached RPM to 1,200 before applying that multiplier.
A test that only writes 2,400 to `rpm` does not establish multiple-round-per-tick
coverage. Charge fixtures use native `charging` data and ordinary charge timing.
Test gun definitions and observer classes are excluded from the production JAR.

For integrated client observations, the launcher accepts `--aim-error` (0–10),
`--aim-point EYE_LEVEL|CENTER_MASS`, and optional `--player-error` (0–10).
The player option enables the override with center-mass aim, leaving the general
mob policy unchanged. Projectile records include approved aim, configured error,
observed angular offset, and sample index. These flags modify only the isolated
test configuration. Multiple seeds and dedicated multiplayer balance comparisons
are required before recommending a PvP preset.

To run the complete server fixture with its generated test gun pack:

```sh
python3 tools/run_guard_firing_qa.py \
  --jar /absolute/path/to/villagerguardgunners.jar \
  --mods /absolute/path/to/dependency-jars \
  --libraries /absolute/path/to/installed-forge-server/libraries \
  --output build/guard-firing/new-run --accept-eula
```

The EULA flag records acceptance only in the new disposable server directory.
Use `--mode safety` for geometry/property fixtures or `--mode smoke` for a normal
production-only startup and shutdown without the observer or test gun pack.
All modes bind to loopback, record artifact hashes, and require a new directory.

## Gameplay comparison suites

Build the observer as above, then run `tools/run_guard_balance_qa.py` with the
production JAR, dependency directory, a version-matched `level.dat` template, and
a new output directory. `--suite core` compares eye-level zero-error aim with
center-mass errors of 0, 1, 2, and 3 degrees at 8, 16, and 32 blocks over three
seeds. Core and group case order is shuffled reproducibly to reduce ordering
bias. `--suite groups` compares one, three, and six guards and checks mob policy
inheritance. `--suite situations` covers movement, crouching, cover, elevation,
commands, protection, additional weapons, an attachment, and extended range.
`--suite protection` runs a shorter regression set for owner protection,
creative/spectator targets, and changing game mode after the first burst round.
The [validation results](guard-accuracy-validation.md) explain the measured
accuracy trend and its limits for PvP balance.

```sh
python3 tools/run_guard_balance_qa.py \
  --jar /absolute/path/to/villagerguardgunners.jar \
  --mods /absolute/path/to/dependency-jars \
  --template /absolute/path/to/test-world/level.dat \
  --output build/guard-balance/new-core --suite core \
  --dedicated --accept-eula \
  --libraries /absolute/path/to/installed-forge-server/libraries
python3 tools/report_guard_balance.py build/guard-balance/new-core
```

Omit `--dedicated`, `--libraries`, and `--accept-eula` for an integrated-client
run. Dedicated mode starts a loopback server and a real client connection in
one controlled workload. Both load production JARs, with the separate observer
installed for measurement. No fake player substitutes for the connected player.
The fixture creates its arena in the disposable world and uses ordinary guard
AI. Target movement is scripted, so results do not establish human-input or
network-latency behavior.

The reports separate native fire-event refusals, emitted projectiles, unique
projectiles with observed target hits, headshot events, damage, and kill times.
They also compare emitted rounds with ammo consumed, verify the resolved policy
for each projectile, and reject missing firing evidence or failed transition
coverage. A completed process alone is not a successful gameplay result.
Bullets still in flight at cleanup are counted as lacking an observed target
hit, not proven misses. Kill-time medians include only kills; timeouts remain
visible. Command and live-setting cases temporarily make the target invulnerable
to ensure the transition occurs; exclude them from accuracy estimates.
Server tick measurements include the observer and local client workload. They
are useful for identifying stalls but are not a population or load benchmark.

`tools/run_guard_combat_smoke.py` checks ordinary command-created guard combat,
an ally blocking the lane, and persisted aiming settings over two server boots.
It requires `--jar`, `--mods`, `--libraries`, `--config` (an existing current
configuration used as a template), a new `--output`, and `--accept-eula`. It
copies and edits settings only in the disposable server, installs no observer,
and uses ordinary console commands to inspect ammo, target survival, and ally
health. Its report records the exact production/dependency hashes.


## Shot-approval regression fixtures

The packaged firing runner also accepts `--mode regression`. This uses the
separate observer and generated gun definitions to check blocked/canceled SPAS-12
ammo, resumed firing and reloads, ammo-dependent lifetime/gravity/friction and
explosion properties, pellet consistency, event-time ally/team/block changes,
and refusal display/history. The run fails on an emitted unsafe round, lost ammo
for a refused shot, mismatched native ballistic fields, or an incorrect status.

The balance observer counts completed native round callbacks through an
observer-only mixin. It records the outcome after the full firing event returns,
so late safety refusals are included. The production JAR does not contain this
observer mixin. Older measurements retain their original observer semantics and
artifact hashes; do not relabel them as measurements of a rebuilt candidate.

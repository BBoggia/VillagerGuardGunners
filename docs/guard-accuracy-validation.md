# Guard accuracy validation

The adjustable aim error changes shot placement and reduces the observed target-hit
fraction in controlled encounters. It does not by itself make groups of guards
easy to fight. These measurements support configurable accuracy; they do not
establish a universally balanced PvP preset.

## Runtime and method

Measurements use Minecraft 1.20.1, Forge 47.4.10, Guard Villagers 1.6.19,
Timeless and Classics Zero (TaCZ) 1.1.8-hotfix, Fzzy Config 0.7.6, and Kotlin
for Forge 4.12.0. Normal installed Forge launches load packaged JARs. A separate
observer records ordinary guard AI fighting a real connected client player.
The [test procedure](guard-accuracy-testing.md) describes reproduction and the
observer-free checks.

The core comparison and group measurements used candidate SHA-256
`d8df18f8f53eb9a28c1137abc95d5669ecc5e65b1fce45703ed8820e7599cf5d`.
Subsequent situational testing identified missing protection for directly
assigned creative/spectator targets. The corrected candidate is
`4cbee3cec85d53216707ad8917e5c3a861ff0ce8932507dc56c92b1a01be1b61`.
That correction checks game-mode eligibility; it does not change the aim
distribution or survival-target weapon behavior. The balance table below is
from the earlier candidate, not a repeated measurement of the corrected JAR.

## Earlier accuracy candidate

The candidate prepared before the follow-up review was SHA-256
`5ae09bf3972c58e1c4af47dfb3b9f25d1fbaace9847d913715247d2cd12e6197`.
Compared with the corrected gameplay candidate, the JAR changes only English
translations and config-comment annotations. Disassembly of the two changed
config classes confirms identical executable instructions. The aiming model,
shot safety, firing hooks, and defaults are unchanged; the earlier measurements
remain attributed to their original artifact hashes above.

The recommended starting preset is now documented as 2° with center-mass aim
against players. Player overrides remain disabled by default. This is an
Unreleased local candidate retaining the existing 1.7 build identity, not a
replacement for the published 1.7 release.

This exact final JAR passed the full build and artifact checks with 928 Java
tests. The report tools passed 12 Python tests. Eight fresh dedicated-server
encounters with a real client passed, including game-mode changes during a
burst. An observer-free server again verified open-lane combat, no ammo loss or
ally damage with a villager blocking the lane, and full config preservation
across two boots. No observer classes are included in the candidate JAR.

## Current accuracy fixes

The follow-up candidate is SHA-256
`8a147bd442a21d7dbd3bf8a59deb9db76c2213768bf7c111afbc2e860a695bb6`.
It supersedes the earlier candidate above, which had three reproduced firing
issues and incomplete refusal messages.

Blocked or canceled SPAS-12 shots now preserve ammo. Native projectiles consume
the same safety-relevant ballistic values as the approved round. The final world
check runs after firing-event subscribers, so event-time ally movement, team
changes, and nearby block edits reach that check. Refusal messages cover
unsupported guns, safety limits, and unavailable checks; outdated context and
malformed fire-mode data do not produce stale messages or throw from the status
history reader.

This exact JAR passed 934 Java tests, artifact verification, 16 targeted
packaged regressions, 18 native safety cases, and 21 native firing cases. Eight
real-client dedicated-server encounters passed, including creative/spectator
changes during a burst. An observer-free server verified ordinary combat,
blocked-shot ammo preservation, and complete saved-config preservation across
two starts. The report tools passed 12 Python tests.

The targeted fixtures observed zero ammo loss on blocked/canceled SPAS shots,
resumed firing and reloads, matching lifetime/gravity/friction/explosion fields
for native projectiles and pellets, and no emission after event-time ally, team,
or impact-block changes. They also verified refusal messages, context expiry,
and malformed fire-mode handling. These are regression results, not a new PvP
balance measurement or proof of compatibility with every community gun pack.

The [shot-safety contract](guard-shot-safety.md) describes the supported script
behavior and remaining limits. Defaults and the aim-error sampler are unchanged.
This remains an Unreleased test candidate with the existing internal 1.7 identity.
The earlier balance and performance figures remain measurements of the hashes
explicitly named alongside them.

## Controlled accuracy comparison

Each row combines nine dedicated-server encounters: three seeds at each of
8, 16, and 32 blocks. Each encounter has one guard, a loaded AK-47, an unarmored
stationary survival player, and Normal difficulty. Player overrides leave
general mob settings at zero error and eye-level aim.

| Player aim | Error | Emitted rounds / ammo used | Projectiles with observed target hits | Observed hit fraction | Median defeat time |
| --- | ---: | ---: | ---: | ---: | ---: |
| Eye level | 0° | 21 | 18 | 85.7% | 12 ticks |
| Center mass | 0° | 30 | 27 | 90.0% | 14 ticks |
| Center mass | 1° | 31 | 27 | 87.1% | 14 ticks |
| Center mass | 2° | 40 | 27 | 67.5% | 14 ticks |
| Center mass | 3° | 54 | 27 | 50.0% | 15 ticks |

All 45 encounters ended in a kill. None had a canceled native round, an aim
bound violation, or observed ally damage. Reduced observed hits therefore came
from shot placement rather than a refusal to fire. Eye-level zero-error shots
produced 18 headshot events; the center-mass rows produced zero at 0–2° and one
at 3°. Native gun spread still applies with zero added guard error.

Three seeds are a small sample. Pooling distances separately for each seed,
the center-mass observed hit fractions ranged from 90–90% at 0°, 81.8–90% at
1°, 56.3–81.8% at 2°, and 31.0–90% at 3°. Individual short encounters can
contradict the aggregate trend. These ranges are descriptive, not confidence
intervals. Per-guard seeds are paired, but absolute game time also affects drift;
the conditions do not replay identical trajectories.

Bullets still in flight when the target dies are removed. A projectile without
an observed target hit is therefore not necessarily a miss. This matters
especially for groups, where several guards can fire before the first bullets
kill the target. Defeat times start at encounter setup, with targets explicitly
assigned; they do not measure natural target detection or a human's reaction.

## Groups, mob policy, and performance

At 16 blocks, three trials per condition compared 0° and 2° center-mass player
aim with one, three, and six guards. All 18 player targets died. Median defeat
times were 14 ticks for one guard and 10 ticks for three or six guards at
both error settings. At 20 ticks per second these are approximately 0.7 and
0.5 seconds. Accuracy alone offers limited protection against concentrated fire.

Six matched mob encounters compared player error overrides of 0° and 3°.
Both sets used six rounds total, recorded six target-hit projectiles, and had
12-tick median defeat times. The resolved mob policy remained zero error with
eye-level targeting.

The observed server-tick 95th percentiles were approximately 2.0–2.4 ms across
the core policy groups and 3.1 ms across the group suite. These short local
measurements include the observer and a client running on the same machine.
They are not a populated-server load test or a performance guarantee.

## Corrected-candidate gameplay checks

Eight dedicated-server checks passed on the corrected JAR: normal player/mob
combat, owner protection, creative and spectator targets, and changing game
mode immediately after the first M16 burst round. The remaining queued rounds
did not fire. The protection applies even to targets assigned directly by
another mod or command system.

Fifty integrated-client cases passed the recorded assertions. They covered
strafe/approach/crouch motion, partial and complete cover, elevation, changing
targets, orders and settings, player protection, and allies beside/across/behind
the shot path. Native pistol, rifle, SMG, burst, bolt-action, shotgun, rocket,
and generated charge guns fired. An allowed ACOG attachment and a 48-block shot
with a 64-block engagement cap also worked.

The suite emitted 297 projectiles from 252 rounds, consuming 252 rounds of ammo.
It recorded no aim-bound or forbidden-shot violations and no ally damage.
Crossing allies caused three native round cancellations in each of the two
crossing cases. Allies in the lane or behind the target prevented firing. A
slab blocking the center-mass rocket trajectory while preserving eye-level
visibility produced 11 explosive-impact refusals and no emitted rocket.

The 50 cases had 38 kills and 12 timeouts. Ten timeouts were the intended
protection/close-impact cases, including two game-mode changes after the first
round. One was the low-cover limitation below. The target-change case exhausted
its ammo after the transition without killing the replacement target; it still
recorded firing with the replacement target's policy. These cases are not
included in the controlled accuracy table. Live-setting and command cases
include a temporary invulnerable period and are also excluded from hit-rate
comparisons.

The corrected candidate also passed all 21 packaged native-firing cases,
covering reload/resupply, goal restart, weapon swaps, canceled shots, charge,
pellets, rapid fire, and scheduled bursts. The full build and artifact checks
passed with 928 Java tests; the report tooling passed 12 Python tests.

A separate server run installed only the production mod and its four required
dependencies, with no observer. An ordinary guard defeated a husk in an open
lane and consumed ammo. After refilling the gun, placing a villager in the lane
kept ammo unchanged, the villager at 20 health, and the enemy alive. Two server
boots preserved the entire saved configuration, including nondefault general
and player aiming settings.

## Gameplay limits

- Center-mass aim can strike low cover while ordinary eye-level visibility
  remains clear. In one zero-error partial-cover encounter the guard used all
  31 loaded rounds without a target hit. Choosing an exposed body point or
  repositioning around that cover would be separate AI work.
- Scripted strafing, approach, and crouching exercise target geometry, but do
  not reproduce human movement decisions or network latency.
- The available default gun pack and a generated pack using native charge and
  fire-rate parameters cover standard behavior. No additional community gun
  pack was available for this run. Arbitrary scripted spread remains outside
  the supported trajectory contract.
- Changing acquisition delay or tracking behavior is separate from steady
  aim error. The short group defeat times justify considering those features
  later, without silently adding them to the accuracy controls.

Existing defaults remain zero added error, eye-level aim, and player override
disabled. The recommended opt-in starting point is 2° with center-mass player
aim; use 1° or 3° for lighter or stronger adjustment after testing your server.
See the [configuration example](configuration.md#guard-accuracy-and-player-combat).

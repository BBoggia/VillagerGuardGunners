# Guard tactical intent

Gun combat separates observations, intent selection, and movement execution.
`GuardThreatTracker` records nearby evidence; `CombatIntentPolicy` selects an
intent from immutable inputs; `GuardTacticalController` retains that decision
across brief target gaps. TaCZ continues to own weapon actions, and the existing
rules of engagement, visibility, aim, range, and friendly-fire checks authorize
shots independently of tactical intent.

## Observation limits

Each active gun goal samples a 24-block area on a staggered 10-tick cadence.
The first subsequent sample can take up to 19 ticks to establish the stagger.
A query retains at most 33 candidates, processes at most 32, and performs at most
12 line-of-sight checks. The extra candidate detects truncation. The current
target and recent attacker receive priority. A truncated query, exhausted sight
budget, full memory, or failed integration hook marks the assessment incomplete;
it cannot authorize an ordinary advance.

Memory holds at most 16 UUIDs with dimension, last visible position and velocity,
observation time, attack evidence, and coarse ranged/explosive traits. Observations
expire after 40 ticks and lose confidence with age. Unseen entities never update
those coordinates. Velocity only estimates closing pressure from the last
observation; it does not move a remembered position. Loaded entity lookups remove
known invalid or protected threats without requesting chunks. Dimension changes
and goal cleanup clear the memory.

Nearby hostile mobs are potential threats. A neutral entity becomes evidence only
when selected as the current target, recently attacking the guard or a protected
ally, or visibly targeting a protected ally. Creative and spectator players,
dead entities, and protected allies are excluded. An unfamiliar attacker receives
conservative close-range pressure even without recognizable weapon metadata.
These classifications never select a target or expand permission to fire.

Recent health loss updates every goal tick and interrupts the normal decision
cadence. Support counts visible allied guards within 16 blocks only when their
own combat goal has recently cached both a ready weapon and a safe shot against
the current or another observed threat. Downed and busy medic guards do not count.
Support is capped at three and refreshed with perception; observers never tick
another controller or scan its ammo inventory.

## Intent and stability

| Condition | Intent |
| --- | --- |
| Explicit hold or active command movement | Respect command movement ownership. |
| Nearby primed creeper, health at or below 30%, substantial recent damage, surrounding pressure, or overwhelming pressure | Withdraw. |
| Confirmed dry gun with threat evidence | Withdraw. |
| Reloading, drawing, or cycling a gun with a threat within sixteen blocks | Create distance. |
| Unavailable weapon, forbidden fire, hidden target, or incomplete assessment | Hold. |
| Ready gun with a close observed threat | Create distance. |
| Ready gun with a blocked friendly-fire lane | Reposition. |
| Manageable visible engagement with a ready gun | Engage. |
| No current engagement evidence after the continuation window | Finish combat. |

Withdrawal normally commits for at least 20 ticks. Re-entry requires a ready gun,
at least 50% health, eight blocks of separation, and lower pressure than the entry
threshold. Weapon/readiness changes and orders force immediate reconsideration;
new damage, danger, or a more restrictive intent interrupts the ordinary configured
movement-decision cadence. Revoked engagement permission, incomplete observations,
and a blocked shot cannot retain a cached advance. DEFEND intent uses the same
horizontal area and boundary margin as command movement.
A target replacement alone does not reset withdrawal commitment.

Defensive movement uses [screened native routes](guard-tactical-routes.md).
The guard turns along the escape route and releases aim and trigger while moving.
A failed search holds and retries with a bounded delay. Explicit command movement
retains priority. Ordinary approach remains restricted to a ready weapon; dry and
reloading guards can take defensive routes without permission to pursue enemies.

## Combat continuation and sidearms

A directly observed permitted target, recent damage, or credible attack on an ally
can retain combat context for up to 40 ticks after that evidence. Merely seeing an
idle hostile during a target gap does not renew ownership indefinitely. A backup
activated for a dry primary stays equipped during this bounded gap. The goal
releases aim and trigger, performs native weapon maintenance, and never invents
a firing target from memory. Expiration restores the primary through the existing
native swap-safety checks and releases combat control to other goals.

An upstream target that remains hidden is released when its evidence expires.
This is ordinary combat completion; it does not clear targets to bypass supply
or medical service restrictions. Fresh upstream alerts can start another
engagement; repeatedly reacquired threats can keep those service gates closed.
Feature disablement, denied targets, invalid
backup definitions, life-state priorities, and equipment recovery retain their
existing cleanup behavior. No persistent storage schema changes are required.

## Diagnostics

`/vgg inspect` reports intent, reason, readiness, decision age, continuation,
assessment age and completeness, nearest remembered threat, pressure, support,
occupied sectors, explosive danger, recent damage, and scan counts. These are
observations and policy explanations, not a guarantee of a safe shot or route.

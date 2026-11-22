# Guard tactical routes

Guards can withdraw, make room while reloading, and seek another firing position
using screened Minecraft navigation paths. The gun goal keeps combat ownership
through these moves. An unavailable gun can authorize defensive movement; it
cannot authorize an offensive approach or a melee handoff.

## Bounded planning

`TacticalPositionPlanner` compares the current position with 48 horizontal
candidates on rings six, ten, and fourteen blocks away for defensive movement.
Firing-lane changes use nearer rings at three, six, and ten blocks. Each candidate checks
at most five elevations. Ranking considers separation from all remembered
threats, explosive pressure, nearby room, travel cost, assignment distance, and
positions of allies whose useful support was already observed. Only those allies
contribute; nearby civilians and busy medics are not retreat attractions.

A known blocked firing lane reduces the current-position score. Lane candidates
must fit the current gun's firing window and prefer its approach range relative
to the selected visible target. A single loaded-entity query captures at most 64
protected bodies around the candidate area and target; overflow declines lane
planning. Candidates whose direct corridor crosses a protected body are rejected.
Explosive guns also reserve space around the anticipated target impact. The best
eight remaining lane candidates receive terrain sight checks to that target.
Defensive movement retains four finalists and exposure checks against remembered
threats. Clearance checks around finalists run only for these shortlists and the
current position. Planning uses at most ten sight rays.

This destination screening is an estimate of a useful firing lane. It does not
model every native spread, flight, or impact possibility and never approves a
shot. Each native discharge still checks current entities and the full projectile
envelope through the weapon, visibility, rules-of-engagement, and friendly-fire
safety checks.

Only the highest-ranked remaining candidate receives a native path in a planning
slot. A rejected destination is temporarily excluded so later slots can try
alternatives. The native search uses an explicit 24-block range, with its full
32-block navigation-region padding checked for loaded chunks before construction.
No terrain check or geometry ray requests an unloaded chunk.

## Firing-position reservations

A lane-change destination has a short server-side reservation. Other lane planners
exclude positions within two blocks of it. Each world retains at most 128 leases;
full capacity declines another reservation. A plan reads one bounded reservation
snapshot, and installation rechecks availability. Moving guards refresh leases
every ten ticks. Arrival retains the position for up to 40 ticks, while failure,
combat cleanup, death, removal, or expiry releases it. Leases contain only a guard
UUID, position, and expiry, and world keys are weak references. They do not move
an idle guard or override Hold Position, medical work, or player commands.

## Lost-contact investigation

`GuardInvestigationController` retains one last-visible sighting separately from
the 40-tick threat memory. It can search for at most 80 ticks, and never beyond
100 ticks after that sighting. Hidden target coordinates are not passed to the
planner. Reacquiring a visible contact, changing targets, readiness, orders, or
commands, or encountering greater tactical danger cancels the search.

A peek compares 48 candidate columns at three, six, and ten blocks from the guard,
checks at most five elevations each, and traces at most eight finalist sight lines
to the recorded position. Each shared combat path slot constructs at most one
native route. A search permits two attempts, at least 20 ticks apart. Its entire
route must remain within ten blocks of the starting position, the combat duty or
Defend boundary, and sixteen blocks of remaining travel. Ground and threat
clearance checks match other tactical routes. Patrol-bound, following, and strict
hold behavior cannot start a search. Incomplete danger assessments or unavailable
guns also prevent investigation.

Routes revalidate every ten ticks, stop after 30 ticks without progress, and reject
native unscreened recomputation. Completion, failure, or interruption starts a
100-tick cooldown. Guards release aim while searching and resume normal engagement
only after visibility returns. `/vgg inspect` reports the frozen sighting,
destination, attempt count, and cancellation or failure status.

## Terrain and route constraints

Destinations require standing support, full body clearance, world-border and
height validity, and loaded terrain. Combat, coverage, patrol destinations, and defense-post validation use `GuardGroundTraversal` for loaded-only support and
headroom checks. Dry slabs, stairs, unlit campfires, and waterlogged top slabs or
closed top trapdoors can provide support. Standing height follows the collision
shape, including fractional floors. Immersed bottom slabs, open trapdoors,
fire, magma, cacti, berry bushes, wither roses, powder snow, cobwebs, and lit
campfires are rejected. Combat additionally checks the guard's body and harmful
or forbidden modded path types. Closed doors and swimming remain outside this
conservative walking graph.

Paths must reach the selected endpoint, contain at most 48 nodes, and have at most
48 blocks of remaining travel. Nodes and intervening segments are checked at
half-block spacing for hazards and assignment boundaries. A route cannot move
closer than the permitted clearance from known threats merely to reach a safer
endpoint. Observed explosive danger uses eight blocks of clearance; other threats
use four. A guard already inside that clearance must avoid getting closer. A guard already standing in danger gets a short allowance to leave its
starting hazard; that does not allow crossing another hazard farther along.

DEFEND routes remain inside the command area, including ordinary combat approach
paths. The existing return-to-area command handles guards displaced outside it.
PATROL_BOUND and inherited patrol behavior retain a local anchor from the patrol
position or active route destination, falling back to the engagement position.
Local movement is limited by the effective engagement envelope, capped at 32
blocks. Following also remains bounded locally. No patrol, route, or follow
assignment is rewritten, and changed bounds invalidate an active route immediately.

An automatically assigned defense guard uses a 16-block local boundary around
its current post. An ordinary guard explicitly affiliated with a recorded friendly
village captures a 24-block boundary at its safe engagement origin inside that
village. This applies even when automatic coverage is disabled. An executing squad patrol route retains a 24-block local combat boundary at
the guard’s engagement origin, then resumes its saved waypoints when combat ends.
Unassigned squad members, following, and active commands do not acquire a village
boundary. Explicit patrol-bound orders keep their existing local patrol bound. Approach, lane change, and withdrawal paths use
the same boundary; target changes cannot move the anchor.

After combat, the automatic duty goal returns the guard to that origin before
resuming coverage. Return uses loaded, screened native paths and the shared path
cadence. It yields to combat, player orders, medical work, and active services.
Changed membership, post, route revision, orders, or dimension revokes the anchor. Return state
expires 1,200 ticks after its last combat refresh and is never saved. A return ends
after four failed path attempts or 160 ticks of return activity, releasing automatic
movement to resume other duties. It does not teleport or load the destination.
Origins on fractional-height surfaces use the native air-cell destination above
the supporting block, so a guard standing on a bottom slab can return to it.

## Execution and recovery

One route runs at a time. Guards turn along it and release aim and trigger while
escaping. Starting an escape releases aim and suppresses scheduled burst rounds
in that same tick; a moving guard does not count as covering support. A loaded guard can return safe fire while holding when a route cannot
start. Movement uses the existing retreat or reposition speed. Useful route
progress can include a detour away from the endpoint: the controller tracks node
advancement and remaining route distance instead of relying on straight-line
endpoint distance alone.

Route validation runs every ten ticks. A lack of progress for twice the configured
reposition progress interval, with a minimum of twenty ticks, stops the path.
Lane movement also rechecks the destination against current protected bodies and
the target at that interval. Its commitment survives expiration of the short
blocked-shot history, because moving deliberately suppresses new shots. A lane
ends on arrival, failed progress, invalid geometry, or a 120-tick deadline.
Target loss/replacement, weapon swaps, restrictive orders, lost fire permission,
and defensive emergencies interrupt it. Changed command context also invalidates
the lane before another route can be planned.

After installing a path, the controller validates and owns the actual object
retained by Minecraft, including equivalent-path reuse. Losing that ownership
cannot stop the replacement path. Moving and look-direction diagnostics require
ownership of a live unfinished path; lane movement reports Repositioning even
after shot history expires and native aim is released.
Changed terrain also invalidates an owned route through
`TacticalPathNavigationMixin`, preventing native automatic recalculation from
bypassing tactical screening. The controller also clears the queued native retry
when taking responsibility for replanning. Screened combat approach, duty return, coverage, and emergency resupply paths use terrain-notification interception. Combat approach paths also use
ten-tick route checks. They cannot use native automatic recalculation to leave
the command area or bypass the shared path budget. Other navigation owners keep
their normal behavior.

When native navigation retains an equivalent existing path, the DEFEND controller
tracks that installed path so terrain invalidation and periodic screening remain
active. A rejected installation stops the approach.

Failures retain at most eight destination cooldowns, each lasting eighty ticks.
Each excludes a three-block horizontal neighborhood within two blocks of height,
so small displacements do not immediately retry the same blocked approach.
Airborne guards wait to land without consuming a path slot or blacklisting a
destination; native navigation can also attempt an exit from water or lava.
The controller waits at least twenty ticks before another planning attempt.
A trapped guard holds and keeps retrying; an opening can therefore restore
movement without resetting its orders. Reaching a destination allows a short
pause before another plan. A valid withdrawal detour can finish even when the immediate close-contact
intent eases, provided combat still continues and the route remains valid. A
firing-lane reposition ends when its blocked-lane reason clears, allowing the
guard to resume aiming instead of completing an unnecessary detour.

Command and tactical movement share the configured path-recalculation interval
across combat and positional-command goals. Idle ticks do not consume a slot, and command replacement
does not reset the deadline. Command movement runs first, so it cannot receive a
path and then have a tactical search overwrite that path in the same tick.

`/vgg inspect` reports route status, destination, actual path ownership, movement,
whether the route is a lane change, the current combat duty anchor and radius,
emergency resupply state, total path attempts, recently
failed destinations, and the last candidate count. Explicit no-route and failure
states describe a bounded fallback, not a guarantee that no possible route exists.

See [Guard combat handoffs](guard-combat-handoffs.md) for interruption, service,
and lifecycle ownership rules.

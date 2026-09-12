# Guard tactical routes

Guards can withdraw, make room while reloading, and seek another firing position
using screened Minecraft navigation paths. The gun goal keeps combat ownership
through these moves. An unavailable gun can authorize defensive movement; it
cannot authorize an offensive approach or a melee handoff.

## Bounded planning

`TacticalPositionPlanner` compares the current position with 48 horizontal
candidates on rings six, ten, and fourteen blocks away. Each candidate checks
at most five elevations. Ranking considers separation from all remembered
threats, explosive pressure, nearby room, travel cost, assignment distance, and
positions of allies whose useful support was already observed. Only those allies
contribute; nearby civilians and busy medics are not retreat attractions.

A known blocked firing lane reduces the current-position score, so a comparable
nearby position can be worth trying without first improving threat separation.
The best four candidates receive bounded geometry checks for exposure, nearby
room to keep moving, or a possible firing lane. Expensive neighboring clearance
queries run only for those finalists and the current position. At most ten rays are cast, including the baseline. These
rays use remembered coordinates and do not authorize a shot. The existing weapon,
visibility, rules-of-engagement, and friendly-fire checks remain authoritative.

Only the highest-ranked remaining candidate receives a native path in a planning
slot. A rejected destination is temporarily excluded so later slots can try
alternatives. The native search uses an explicit 24-block range, with its full
32-block navigation-region padding checked for loaded chunks before construction.
No terrain check or geometry ray requests an unloaded chunk.

## Terrain and route constraints

Destinations require standing support, full body clearance, world-border and
height validity, and loaded terrain. Fluids, fire, magma, cacti, berry bushes,
wither roses, powder snow, campfires, and harmful or forbidden modded path types
are rejected. The initial implementation conservatively requires sturdy ground;
it is not a general swimming, jumping, or global escape planner.

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
Changed terrain also invalidates an owned route through
`TacticalPathNavigationMixin`, preventing native automatic recalculation from
bypassing tactical screening. The controller also clears the queued native retry
when taking responsibility for replanning. Screened DEFEND approach paths use the same terrain-notification interception
and ten-tick route checks. They cannot use native automatic recalculation to leave
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

`/vgg inspect` reports route status, destination, total path attempts, recently
failed destinations, and the last candidate count. Explicit no-route and failure
states describe a bounded fallback, not a guarantee that no possible route exists.

See [Guard combat handoffs](guard-combat-handoffs.md) for interruption, service,
and lifecycle ownership rules.

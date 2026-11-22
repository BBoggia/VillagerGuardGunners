# Guard navigation

Automatic duties, saved patrols, coverage, resupply, tactical movement, and duty
return share route-progress measurements. Each movement controller retains its
own orders, arrival rules, combat restrictions, deadlines, and retry policy.
These refinements also work when automatic village coverage is disabled.

## Progress and recovery

Progress means advancing a node or improving the best remaining route distance.
The distance suffix is cached when a path is installed; ordinary sampling takes
constant time. A useful detour can move away from the destination. Repeated
jostling over ground already covered cannot keep a stalled trip alive.

A nearby living blocker, passage wait, or airborne transition grants up to 60
additional ticks against the same progress clock. That allowance also lets a
guard resume when the blocker clears. Permanent obstruction still expires.
Replacing a path starts a new route measurement, but retains the controller's
journey deadline and retry history. Arrival, interruption, and inventory service
transactions remain separate decisions.

Accepted replacement routes also start a fresh native waypoint timeout, including
paths constructed by the local planner. Registering the same installed path again
preserves its existing clocks and passage state. Ownership uses an opaque token
as well as path identity, because Minecraft can retain an equivalent path object
when a different movement controller takes over. Old trackers cannot sample,
release, or stop that replacement owner's route.

Ordinary combat approaches also retain their installed path identity for
cancellation. When a guard switches to investigating a lost sighting, it stops
its old chase and movement command before waiting for the peek's planning slot.
This does not add local-route screening to ordinary unbounded combat approaches.

Unknown terrain and a busy local-search budget defer path construction without
counting as an unreachable destination. Invalid search parameters, unsupported
loaded starts, and targets beyond local-search limits use the owner's normal
failure and retry policy instead of waiting for terrain to load. Saved patrols can rotate to a loaded
waypoint without recording the unloaded waypoint as a failure. Terrain changes
retry through the owning controller rather than unrestricted native recalculation.
Only the installed path owned by that controller is stopped.

## Loaded routes

Native path construction requires its entire search region to be cached. When
that condition fails, or native navigation returns an incomplete path, the addon
can search a loaded ground graph. It first tries an existing compatible coverage
map, then a standalone search. Unknown cells remain unavailable; the planner
creates no chunk tickets.

Local requests share four fallback slots per dimension per tick. Waiting requests
retain FIFO priority for one reserved slot, with a 256-request cap and
100-tick expiry. The other three slots stay usable while the oldest requester
waits for its owner's retry interval. Interrupted requests therefore cannot
block the entire queue, and slower callers retain their place among fast callers.
A slot allows at
most 256 coverage-graph expansions and 768 standalone expansions. Routes contain
at most 256 nodes. Standalone search stays within the requested horizontal
radius, capped at 128 blocks, and eight vertical blocks from its start. Existing
combat bounds and tactical route limits still apply after planning.

Only a complete connection is installed. This supports same-level courtyard
and wall detours without treating an incomplete path as arrival. General journey
segmentation through distant landmarks remains future work.
Native results must match the requested target and endpoint: Minecraft's cached
target can outlive a separately installed local path. Planning does not replace
that installed path until its movement owner accepts the new route.
Routes converted from verified local or coverage ground graphs explicitly mark
their native nodes as `WALKABLE`. Minecraft's default for a newly constructed
node is `BLOCKED`, which tactical screening would correctly reject. This metadata
describes the verified ground route; live terrain and transition checks still
reject a route that becomes unsafe.
Both native and fallback entry points reject a missing target or a requested
range outside 1–128 blocks before checking terrain or constructing a path. A
rejected request clears its pending fallback request and preserves the installed
route and its owner.

Short investigations rank peek positions separately at three, six, and ten
blocks from the guard, then alternate between those rings for visibility checks.
Nearby blocked positions therefore cannot consume every check before wider
peeks are considered. Each attempt retains its limits of 48 candidates, eight
visibility rays, and one path construction. Assignment boundaries, threat
clearance, the ten-block origin radius, and the sixteen-block travel limit still
apply.

## Entrances, doors, and arrival

Coverage prefers outdoor approaches two blocks from fingerprint-verified entrance
metadata. Coordinates use the placed piece's rotation and origin. Both steps
between the entrance and approach must be walkable in the current world.
Unknown or obstructed entrances retain the bounded building-side/home fallback.
Guards do not receive a destination simply because metadata calls it an entrance.

A closed wooden door is a conditional planning transition only when the actual
guard's ground navigator permits opening and passing doors. Execution opens it
within reach, checks the resulting state, and continues with terrain screening.
This also allows a capable guard to plan an exit when a wooden door closes
around its starting position. Capability checks read the node evaluator's
opening flag because Minecraft 1.20.1's navigator getter reports the passing flag.
The expected opening does not cancel the guard's own trip. Iron doors, fences,
closed gates, redstone mechanisms, and modded locks receive no special opening
support. Doors are not automatically closed behind guards.

Ground-duty and coverage arrival require the actual support height and a clear
final approach. A nearby position across a wall or on another floor is not
arrival. Resupply retains its existing service-distance and transaction checks.

## Narrow passages

The movement adapter identifies short straight passages with safe wide ends,
such as single-width bridges and doorways. It admits one registered guard at a
time and retains the claim while that guard crosses. Waiting guards pause their
native path without replacing it. Outside combat, a waiter can move one block
sideways onto a verified safe spot, let departing traffic clear the exit, and
rejoin at the wide end. Duty bounds and manual holds still apply. FIFO admission prevents newcomers
from repeatedly overtaking an existing waiter.

The sidestep and return segment are screened again on each movement tick.
Displacement, terrain changes, combat, follow orders, or a new hold order can
cancel staging. Cancellation clears the old sidestep's movement command while
preserving a replacement goal's command. Passage occupancy uses the support
node, including fractional slab floors.

Passages are limited to twelve blocks between their end nodes. Each dimension
holds at most 128 passage records and sixteen waiters per passage. Claims expire
after thirty ticks without renewal; waiting and rejoining together have a
100-tick deadline, including paths without a controller progress tracker. Path
replacement, completion, and interruption release the old claim. Weak entity
references and claim expiry cover removal and unload.

Waiting and rejoining pause the native waypoint clock while retaining its accrued
active movement time. The passage deadline and controller journey deadlines still
run, and native stall detection resumes when the guard starts following the route.
Repeated registration of a retained path does not restart the passage deadline
or move its guard to the back of the queue.

This is admission at safe ends, not general crowd steering. It does not move
players or villagers, reverse guards already crossing, or force an idle guard
off a fixed post. Bent bridges, long tunnels, tactical pull-offs during combat, and traffic
already inside a passage need additional handling.

## Coverage repair and route cost

A block/chunk event masks its chunk and a one-block boundary in the active map.
Routes using that region lose their leases. Unaffected verified trips continue;
new allocation waits for repaired connectivity. Repair reuses clean graph edges
but rebuilds connectivity under the normal survey budget. It never deletes an
edge while retaining the old union-find component claim.

Completed repairs preserve unchanged destination records and valid leases,
reset navigation-failure suppression, and retain visit history where area identity
survives. Changed components or destinations are reevaluated. Edits are coalesced
until the next scheduled village review, which restarts the pending repair using
the accumulated dirty regions. Periodic full
surveys remain the fallback for changes without useful events. Footprint changes
and observation errors still invalidate the whole publication.

Graph routes use bounded A* with travel distance, a small elevation penalty, and
(for coverage graphs) a small penalty for nodes with few exits. These are soft
preferences applied only to authorized edges. Installed routes are not switched
merely because another route becomes slightly cheaper. Road classification,
stronger congestion costs, and observed-danger costs beyond tactical screening
remain future refinements.

## Avoiding repeated work

Each synchronous ground check reuses its most recently accessed loaded chunk
for floor, headroom, and body checks. Block states are still read from the chunk
on demand. The reader ends with the check, so a later check observes terrain
changes immediately. Distant arrival checks reject horizontal distance before
reading the destination's floor; nearby arrival retains the full floor and
approach screening.

Passage reservation expiry runs once per dimension per game tick when requests
arrive. Renewals, releases, FIFO admission, and the thirty-tick lease duration
retain their existing behavior.

Coverage checks dirty terrain once per distinct chunk intersecting its one-block
halo (at most four chunks). Clean graph searches use the existing adjacency lists;
repairing graphs still filter affected nodes. Graph comparisons check unique
neighbors without constructing temporary sets for every node. These changes
preserve route costs, search limits, and the frequency of movement safety checks.

## Diagnostics and limits

`/vgg inspect` reports shared progress state, next node, remaining distance,
last-progress tick, conditional-transition state, and the last search result.
Coverage status reports `repairing` while a local replacement is pending.
Controller diagnostics still report the movement purpose and retry state.
Progress sampling updates one diagnostic record per route. Inspecting a guard
creates an immutable snapshot of its last sample; ordinary samples do not create
new diagnostic objects after the first sample. Unsampled, released, and replaced routes report idle,
and clearing an old tracker preserves a replacement owner's diagnostics.

Swimming recovery, ladders, modded transport mechanisms, broad static-failure
sharing, and general multi-level landmark routing are not implemented here.
Work limits are enforced bounds, not a claim about TPS at every village size or
with every modded block. Generated and player-built village playtesting remains
useful alongside the automated movement scenarios.

For a focused development run, use
`./gradlew runGameTestServer -PnavigationRefinementTests=true`. The normal GameTest run includes these
navigation scenarios; village repair and chunk-access probes also run with
`-PvillageIntegrationProbes=true`.

## Native stair-detour retry

The existing native walking retry still helps nearby destinations on another
level, including Watchtower stairs. It runs for living, grounded guards using
ground navigation, with one loaded destination two to eight blocks above or
below the guard and within 24 blocks in a straight line. Complete normal paths
are returned immediately.

After an incomplete ordinary request, one additional search can use twice the
follow range, capped at forty blocks, while preserving the original native
search region and node-visit limit. Follow ranges below four or at least forty
blocks receive no retry. Each guard has one retry per twenty game ticks, shared
across destinations. Follow-range attributes and detection distances stay unchanged.

The retry excludes active combat, owned tactical paths, screened approaches,
casualty transport participants, passengers, and guards carrying passengers.
Explicit-range requests, including the loaded-route adapter described above,
keep their own planning budgets. An unsuccessful retry returns the original
result, and the caller decides whether a partial result is usable.

`GuardDetourPathNavigationMixin` invokes the explicit-range overload to avoid
recursion. `GuardDetourPathPolicy` reduces search padding by the added range;
range twenty plus padding eight becomes range forty plus padding minus twelve,
keeping the same region radius of twenty-eight. See the
[Plains Watchtower preview guide](watchtower-preview.md) for the separate manual
placement previews and packaged Forge movement trials.

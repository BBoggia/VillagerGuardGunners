# Guard tactical tuning

The shipped defaults favor short local withdrawals and conservative recovery.
They do not guarantee escape from every enclosure or enemy formation. Keep
terrain reachability, actual guard movement, and native TaCZ actions in the
acceptance criteria when changing these values; an intent label alone does not
establish that the guard escaped or reloaded.

## Cadence and work limits

| Setting or limit | Default | Reason and tradeoff |
| --- | --- | --- |
| Target visibility interval | 2 ticks | Refreshes firing evidence frequently without tracing every tick. |
| Movement decision interval | 5 ticks | Limits routine decision work; damage, readiness changes, and orders can interrupt it. |
| Shared path recalculation interval | 20 ticks | Prevents command and combat goals from constructing competing paths in one interval. A failed search can delay the next attempt. |
| Reposition progress interval | 10 ticks | A stall lasts twice this interval, with a minimum of 20 ticks. Route safety is checked separately every 10 ticks. |
| Threat observation interval | 10 staggered ticks | Spreads nearby queries across guards. Initial staggering can take up to 19 ticks. |
| Threat memory | 16 entries, 40 ticks | Bridges brief target gaps without continuously following unseen entities. |
| Nearby threat work | 32 candidates, 12 sight checks | Truncation reduces confidence and prevents ordinary advance. |
| Position search | 48 horizontal candidates, five elevations each | Samples rings at 6, 10, and 14 blocks. Only four finalists receive additional geometry scoring. |
| Native path search | One per slot, 24-block range | Tries alternatives over successive slots instead of issuing many searches at once. |
| Route limits | 48 nodes and 48 blocks of remaining travel | Supports local detours while bounding validation work. |
| Failed destinations | Eight neighborhoods, 80 ticks | Avoids repeatedly selecting small variations of the same blocked approach. |

Ticks assume the normal 20 TPS. These are per-guard work limits, not a fixed
server-wide time allowance. Large active populations still require profiling.
The four configurable cadence values are documented in
[Configuration](configuration.md); the remaining limits are internal constants.

## Behavior thresholds

Withdrawal commits for at least 20 ticks. Health at or below 30% can initiate it;
re-entry needs at least 50% health, a ready weapon, lower threat pressure, and
eight blocks of separation. The different entry and exit thresholds reduce rapid
switching. A ready gun alone does not cancel a useful retreat detour.

Reloading, drawing, and cycling prompt distance creation when threats are within
16 blocks. Route screening preserves eight blocks of clearance from observed
explosive danger and four from other threats. A guard already closer must avoid
getting closer. These distances are safety preferences based on observed threats,
not new firing permissions or a guarantee against damage.

Position ranking balances separation, nearby room, exposure, travel, assignment
bounds, and recently observed useful allied support. It does not treat civilians
or busy medics as combat support. Conservative sturdy-ground and hazard checks
can reject routes that a player could traverse. Swimming, long-distance escape,
and arbitrary jumps are outside this planner's scope.

## Validation boundaries

Use [Guard combat validation](guard-combat-validation.md) for retained moving
encounters and the full validation matrix before accepting a tuning change.
Unit policies cover decision boundaries; native GameTests cover lifecycle,
commands, hazards, route invalidation, and integration. The moving fixtures retain
normal selectors and wall-clock TaCZ action timing. Artificial floors and durable
targets make those measurements repeatable but do not establish natural-terrain
survival rates.

Client terrain observations complement those fixtures. Record the generated seed,
unchanged terrain, client samples, and explicit server assertions. A successful
integrated-client run does not establish synchronization between two independent
clients. Keep dedicated-server observations and their artifact identity separate.

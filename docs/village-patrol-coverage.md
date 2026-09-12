# Automatic village patrol coverage

Villager Guard Gunners can send eligible resident guards between useful parts
of an observed village, including ordinary villages without Village Defense
Buildings. Guards rotate destinations, resume coverage after combat, and give
up blocked routes with a retry delay instead of continually attempting them.

Enable `villageDefense.automaticCoverage` in the server's Guard Gunners
configuration to try this feature. Its development default is `false`, so
upgrading an existing world does not automatically change guard movement.
Village discovery remains independent of this switch. Disabling coverage stops
its navigation and releases its claims as guards and the server process their
next tick; it does not delete guards or village records.

## Which guards participate

Automatic coverage uses loaded guards with a recorded village affiliation.
A guard must have a sword, axe, trident, crossbow, or gun, at least half health,
and no higher-priority work. Unarmed guards remain residents but do not count as
available coverage defenders. This check does not issue guns or guarantee ammo.

Follow orders, including orders whose player is offline, Hold Position, Patrol
Bound, upstream patrol, squad commands, saved routes, combat, resupply, native
gun actions, NoAI, and passenger/carrier states prevent coverage. Queued burst
and charge work also blocks it. These gates are checked again during movement.

This rollout keeps all squad members under their existing squad controls.
It does not enroll or split a manual squad. To use automatic coverage for an
individual resident, release that guard from its squad through the existing
ledger interaction and clear its manual movement orders. A squad-wide opt-in
control is not part of this rollout. Generated garrisons continue using their
existing defense duties and squad behavior.

Coverage never changes a guard's follow flag, patrol flag, patrol position,
Guard Orders, equipment, or membership. An assignment is counted as a visit only
when the guard actually reaches the destination. Even a small village with one
patrol area rotates among that area's available destinations.

## Destinations and terrain

The observer uses known vanilla village house templates, registered Village
Defense Building templates, and cached home POIs. Roads help connect walking
positions but do not establish another occupied area. Unknown templates need
home evidence; they are not treated as houses merely because of their name.

Coverage chooses nearby standing positions and builds a conservative walking
map from loaded blocks. It supports full blocks, broad partial-height footing
such as paths, slabs, and dry unlit campfire decks. Waterlogged top slabs and
closed top trapdoors are valid when the walking space above them is dry.
Steps compare the actual
surface heights and permit at most one block of elevation change with headroom.
Positions require room for a guard, dry footing, and no recognized vanilla fire,
magma, cactus, lit campfire, berry bush, wither rose, cobweb, or powder-snow hazard.
Closed doors, ladders, swimming routes, narrow footing, and other paths outside
this walking model can leave an area unreachable. The observer does not open
doors, alter terrain, teleport guards, or claim that every modded block is safe.

Separate connected regions produce separate coverage areas even when they share
one spatial cell. Unloaded destinations remain observations and receive no work.
Changes from block placement/breakage, fluid placement, and chunk load/unload
invalidate affected maps. A periodic review also catches changes made through
other mechanisms. During a rebuild, diagnostics report that observation is in
progress and previous coverage navigation is released.

Path nodes are constructed from this walking map. The coverage controller does
not call native `createPath` or construct a `PathNavigationRegion`, whose chunk
access could load absent data. Before movement it validates every path position;
while moving it checks the next eight nodes. It samples progress every 40 ticks.
A blocked, exhausted, or stalled path releases its assignment and applies the
policy's retry delay. Alternate destinations remain eligible. Terrain repair
is recognized through a relevant event or the periodic review.

## Readiness and other duties

For ordinary villages, a bell ring supplies Watch, a living combat target or
damage to a guard/villager supplies Alert, and a nearby ongoing native raid
supplies Siege. Alert decays through Recovery after the configured quiet period.
These village-wide readiness observations are transient. Existing defense
networks remain the persistent readiness owners for their own duties.

The existing fixed-post allocator publishes into the shared coverage guard
reservation index on the server thread. Publishing a fixed post preempts that
guard's coverage claim before movement goals run. The coverage scheduler also
checks the current fixed-post assignment directly, including when its mirror
lease has expired. Manual routes retain their existing precedence. Only one
branch of the existing automatic movement goal owns navigation at a time.

Alert/Siege raises priority around confirmed threats. The policy can leave a
free guard in reserve when staffing allows, after honoring fixed duties. A
reserve is an available guard, not an invented destination or a guarantee that
every area is staffed. More guards, ammo, medics, or replacement recruits are
not generated by coverage.

## Diagnostics

| Interface | Result |
| --- | --- |
| `/vgg defense coverage` | Summary for the unambiguous recorded village at the command position. |
| `/vgg defense coverage <village UUID> [page]` | That village's summary and up to four area reports; pages 1–4. |
| Squad Command Ledger route inspection | A compact coverage summary for the player's current village, after a valid route-information result. |

Commands require permission level 2 and use the source's current dimension.
The ledger summary exposes counts and status for the local village, not remote
guard identities or full registry contents. These views do not initiate scans
or load chunks.

Reports distinguish surveying, resource limits, observation errors, and inactive
coverage. Ready plans show loaded/available guards, assigned and visited areas,
reserves, and reasons areas are uncovered. Assigned work is not proof that an
area is safe. Reports refresh on bounded village reviews rather than every
rendered frame. Use `/vgg villages` to inspect the underlying discovery and
membership records when no coverage village has become active.

## Work limits and lifecycle

| Work or storage | Bound |
| --- | --- |
| Active villages / tracked guards per dimension | 64 / 4,096 |
| Candidates per active village | 128 |
| Village review turns | At most four per dimension per tick, rotating fairly |
| Terrain survey | At most 512 node/seed advances per dimension per tick, in slices of 128 |
| Node advance | One seed probe, or one node with at most twelve neighboring step probes; cached collision reads are required for each probe |
| Terrain per village | At most 16,384 nodes, 64 anchors, 256 chunk-index entries, and 4,096 piece-to-chunk references |
| Registered defense template classification | At most 256 definitions; oversized definition sets fall back to home evidence for those templates |
| Path construction | At most four queued graph searches per dimension per tick; at most 16,384 explored nodes and 256 output nodes per search |
| Navigation checks | At most 256 positions when accepting a path; up to eight next nodes per moving guard per tick, plus its destination |
| Stable village planning | At least 20 ticks between ordinary reviews; the queue can extend this |
| Terrain refresh | 600 ticks after the previous completed survey, or a relevant invalidation |
| Observation exception retry | At least 600 ticks; previous movement claims are released |

The [pure coverage policy](village-coverage.md) supplies additional area,
reservation, lease, retry, and travel-distance limits. These limits bound work;
they are not hardware-independent TPS guarantees. Oversized observations are
reported without publishing a partial supported village.

Guard registration reuses the existing staggered 32-tick assessment. There is
no startup scan of historical village records or every entity in a dimension.
Guard leave, level unload, server stop, and disabling coverage release transient
state. Guards rejected at a tracking limit need a later assessment after capacity
becomes available. Path requests are queued by guard and lease token, and old
results cannot take over a newer assignment.

Coverage adds no saved village schema or population allowances. Unloaded guards
retain their village affiliation. Transient maps, paths, reservations, readiness,
and visit history are discarded when their active village is disposed. No chunk
tickets or offline movement are created.

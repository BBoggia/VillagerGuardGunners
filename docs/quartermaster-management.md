# Quartermaster overview integration

The overview connects stock, equipment needs, guard service status and native
villager trading. See [station instructions](quartermaster-station.md#quartermaster-overview)
for player controls. Manage adds bulk equipment settings;
[Quartermaster trades](quartermaster-trades.md) describes merchant offers and
datapack definitions.

## Entry and authority

The stock menu's Overview button opens a station-origin session. NPC interaction
opens a villager-origin session when both hands are empty and the player is not
sneaking. A lowest-priority Forge `EntityInteract` listener preserves canceled,
held-item and offhand interactions.

NPC association requires an adult living Quartermaster, a same-dimension job-site
memory, a loaded station and an occupied matching POI. It never claims a POI or
loads a chunk. Cached operator UUID is not authority; ambiguous nearby operators
cannot select a station-origin trader.

Sessions bind player instance, menu ID, random session UUID, origin, dimension,
station UUID, placement UUID, block position and optional NPC UUID. Actions recheck
that context. Station-origin sessions require stock access within eight blocks.
NPC-origin sessions require the living visible villager within four blocks and a
valid station association. Open Stock always retains normal station access/range.
Captured stock/management capability changes close the session.

Guests can reach native Trade without receiving stock, guard or history rows.
Ordinary stock users receive stock only. Guard discovery, needs and history require
station management and current `GuardOrderPermissions` for loaded guard details.
Unloaded assigned records contain anonymous assignment/last-seen metadata, never
current equipment. Living guards outside the service area are unavailable.
An absent guard's row offers **Forget record** to a station manager. The request
binds its row index to the displayed generation and rechecks the current scope,
station authority, saved assignment and absence from every loaded dimension.
It removes only the roster hint. A returning assigned guard restores the record.
Ownership, settings/profile revisions, permission changes and visible-source
changes invalidate cached rows before another page is serialized.

Linked Armories expose public inventories through their normal menus. Overview
reads additionally require loaded, readable stock and the normal eight-block
player viewing range. Guard squad locks independently restrict allocation. Hidden
or unreadable linked supplies are excluded and mark the estimate as restricted;
physical service retains its own existing source-access rules.

## Stock, needs and service

Stock rows keep NBT-distinct variants separate internally. Packets contain bounded
plain names/IDs, counts, durability and enchantment descriptions, never raw item NBT
or serialized chat components. Ammo boxes remain distinct from loose ammo. Reserved
physical slots are excluded from available counts and free returns.

Assigned guards and Auto guards with an active plan at this station contribute to
needs. Nearby unassigned candidates appear separately and never inflate those
needs. Readable loaded indexes and saved assignment metadata are bounded at 4096;
the visible selection prioritizes assigned guards, then visitors, then candidates,
with a 64-guard cap. Overflow, missing authority and unreadable coverage produce a
limited report. Unloaded/out-of-range guards are unknown, not supplied.

A `ServiceSurvey` freezes visible stock and captures one guard at a time using the
same native compatibility and copied-input logic as `ServiceAssessment`. It remaps
per-guard variant identities into one immutable input, then runs `ServiceNeeds`
with shared physical stock and return-slot budgets. One assessment cannot allocate
the same stock twice. Changing stock, reservations, profile definitions or return space
invalidates a scan before publication. Up to two bounded restarts inside the
same work budget prevent regular deliveries from repeatedly interrupting a scan
at the same point. Capture spans multiple ticks; the displayed
age starts at the first capture. It is an estimate, never a reservation.

Need rows include requested, allocatable, incoming and missing amounts. Incoming
supplies belong to the guard's current valid reservation and remain excluded from
other guards' allocations. Missing is requested minus allocatable and incoming.
Reports recheck the live reservation, unchanged settings and inventories, source
visibility/access, expiry and receipts without quarantining owners. Changed proposed
settings cannot reuse a current delivery. Pending replacements use their own
reserved return slot; pending ammo reserves its destination capacity. The physical
planner continues to allocate against actual inventory without projected deliveries.

Conditional ammo,
missing definitions/profiles and unavailable guards are labeled unknown. Protected
gear and inaccessible sources remain separate reasons. Selecting a shortage binds
its affected-guard filter to the displayed snapshot generation. A refresh updates
that filter; a stale selection is rejected. Guard statuses come from published
runtime state. Reporting never attempts navigation or invents a path failure.

`ServiceHistory` records successful physical transfers only: at most 16 entries per
placement and 128 placements per dimension, in memory. Failed transfers add no
entry. Recent Service rows are filtered again against current visible guards and
permissions. History is not a journal, recovery input or persistent audit log.

## Work and freshness limits

Only open authorized management menus request assessments. Overview scans and bulk
estimates share a limit of 32 jobs per dimension and 16 guard/job work units each tick.
The scheduler alternates between the two queues and rotates jobs within each queue.
A unit captures at most one guard; stock-only work also consumes a unit. Before
publishing or reusing incoming quantities, reporting also revalidates their live
plans; equipment service allows at most eight simultaneous reservations per dimension.
Overview jobs are
shared only when station/placement, management authority, visible guard metadata,
profile revision and visible sources are equivalent. Every recipient is rechecked
before publication. Captures and retries retain the job's original source visibility;
if the primary viewer moves or closes the menu, an equivalent active viewer can take
over with a fresh capture. A departing viewer cannot receive a completed page.

Each player can request fresh work at most once per 20 ticks, including across
reopened menus. Permission-equivalent cached results can be reused within that
interval. Cached frames with expired or invalid incoming deliveries are discarded.
Generation ordering applies within a scope; returning to an earlier valid cached
scope does not blank the page. Packet revisions still increase across scopes.
Menus periodically refresh even without mutation callbacks, picking up
external inventory changes. Changed guard/settings/access scope clears stale rows;
stock/reservation changes invalidate an in-progress scan. Busy/changing sources or
a full work queue leave a visible refreshing estimate. No idle guard/inventory
scans, journal verification/writes, chunk loads or pathfinding are added. Server
stop clears pending work, caches and history.

## Messages and screen bounds

The separate `villagerguardgunners:quartermaster_management` channel requires exact
protocol version `5` on both ends; update server and clients together. TaCZ gun-pack
synchronization keeps its separate existing channel.

- Message 0: navigation with menu/session, increasing sequence, action, page,
  selected shortage and snapshot generation. Actions: Stock=0, Guards=1, Trade=2,
  Open Stock=3, Refresh=4, Needs=5, Service=6, Filter=7, Clear Filter=8, Forget Record=9.
- Message 1: capability/trade summary and matching view/page, with increasing revision.
- Message 2: overview page with matching menu/session/revision, generation, observation
  time, refreshing/limited/restricted flags, coverage, station status and at most
  20 rows, including a separate incoming count for Needs. The GUI shows five rows at a time; scroll within a page and use arrows
  between pages. Stock/Needs columns are explained in each row's tooltip.
- Message 3: bulk action with menu/session, increasing sequence, bounded action/value,
  and guard UUID or preview UUID only for actions requiring them.
- Message 4: server-owned bulk editor state with choice/mixed values, selected/excluded
  counts, preview UUID/expiry, result status, estimate-refreshing flag, and at most
  five 96/1024-character rows.
- Message 5: acknowledgment of bulk Open/Close with menu/session, the original
  action sequence and the server's actual editor-active state. Rate-limited
  requests receive this response too. The client changes screens only for its
  latest pending navigation request, so rejected clicks cannot strand a loading screen.
- Navigation and summaries are capped at 96 bytes. Row pages are capped at 32 KiB;
  names, details and status strings have separate 96/256/64-character limits.
  No collection size is allocated before checking its bound. Decoders reject
  oversized/trailing data, invalid IDs, bounds and unauthorized row categories.
- Actions reject stale UUIDs, wrong players/menus, replayed sequences and invalid
  pages. Navigation accepts at most one action every four ticks; requested refresh
  additionally has a 20-tick limit. The client only renders a page matching its
  active menu/session and current capability revision.

The overview and existing storage screens both fit a 304x228 GUI-pixel area.

## Bulk settings transactions

`BulkSession` lives only within one management menu. Candidate discovery uses the
bounded loaded guard index, current guard authority and a 48-block station radius.
Assigned guards take priority over nearby Auto candidates. Discovery pages cover
the full eligible loaded index; only the selected batch is limited to 64. After
saving one batch, players can reach later guards and select another batch.
A squad button replaces the selection with a snapshot of authorized loaded members;
excluded counts cover loaded members only. Index overflow marks limited coverage.
Selected members remain individually visible if the loaded index fills. The wire
format allows 832 five-row pages for 4096 indexed guards plus up to 64 retained
selections; only visible candidate rows need formatted equipment/service details.
Guards assigned elsewhere are excluded from this station's editor.

All four choices default to Keep. `BulkChoices.resolve` preserves each guard's
individual values, including mixed replacement permissions. Policy, profile,
replacement permission and assignment are independent; changing a profile alone
does not enroll a Manual guard. Every resolved request uses the existing station
and guard permission checks before preview and immediately before apply.

A 600-tick preview binds a random UUID to exact live guard instances, settings and
settings revisions, squad revisions, requested values, equipment-profile revision,
and the management session's player/station/placement context. Changing selection
or choices clears it. Page/view navigation preserves it. Reopening a menu cannot
reuse it. Current membership/authority/range/availability and revisions are checked
again before publishing or applying; one stale guard rejects the entire batch.
New squad members never join an existing selection automatically.

Supply estimates use incremental `ServiceSurvey` captures over the resolved settings,
copied inventories and shared stock/return budgets. Preview creation queues the
estimate; each shared work unit captures at most one of the 64 selected guards.
Automatic refresh uses the same queue. Up to two restarts handle changing sources;
closing the editor cancels its pending work. Estimates refresh at most once per
20 ticks per player, including across reopened menus, with another 20-tick interval
after completion. The client labels pending work, and authorized settings can be
saved before the estimate completes.
Source visibility is rechecked before returning cached details. Stock-only changes
refresh amounts without replacing or extending the settings token. Estimates
reserve nothing and are not promises of delivery; assessment failures show an
unavailable estimate while still allowing authorized settings changes. Deferred
service reasons use read-only eligibility checks with the requested policy.

`ServiceControls.applyBatch` validates every entry before any settings update on
the server thread. The mutation loop uses the same state core as individual
controls, cancels old equipment reservations, and defers advisory roster callbacks
until all settings are written. It touches no item slots, tactical roles, movement
orders, ownership, or squad membership. The result reports the saved count; current
waiting reasons remain in guard row details. This is an in-memory all-or-nothing
validation boundary, not an atomic disk save across entities. There are no saved
preview tokens or queued edits for unavailable guards.

The bulk screen uses the same 304x228 area as the overview. Guard rows and preview
rows are paginated in groups of five; full IDs, settings, current mixed values and
supply reasons are available in tooltips. Bulk messages share the overview's
monotonic sequence and four-tick action budget. Protocol version 5 requires the
same mod build on server and client.

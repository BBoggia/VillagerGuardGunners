# Armory resupply contracts

Step 1 defines the server-side state and request contracts used by later
Village Defense Logistics phases. Step 3 adds the dormant reservation and
withdrawal service boundary. This work does not register a goal, add a guard
tick hook, index an Armory, construct a path, or activate resupply behavior.
In particular, creating an `ArmoryResupplyRequest` never authorizes a transfer;
a candidate snapshot does not either, and withdrawal revalidates every live
input.

## Precedence gate

The constant-time gate is evaluated before an ammunition summary and before
any Armory query. The first blocking condition wins in this order:

1. feature disabled;
2. client side, dead/removed guard, or disabled AI;
3. sidearm recovery required;
4. active combat;
5. active squad command;
6. following a player;
7. Hold Position or effective patrol-bound movement; and
8. a TaCZ native weapon action that must finish first.

An explicit Tactical movement order may release an inherited patrol movement
restriction, matching the existing per-guard order contract. None of these
checks changes the guard's target, follow state, patrol data, orders, weapon,
or navigation.

## Effective ammunition need

Only the active stable weapon role is assessed. `PRIMARY_ACTIVE` requests for
the primary; `SIDEARM_ACTIVE` requests for the sidearm while retaining the
stowed primary untouched. `RECOVERY_REQUIRED` cannot request. This keeps a dry
active sidearm from accidentally searching for primary ammunition and avoids
weapon-role thrashing.

The need calculation consumes `GuardWeaponAmmunition.LoadoutSummary`, which is
already limited to two weapon stacks and the guard's three ammunition slots.
It accepts only a readable TaCZ gun with a complete finite physical-reserve
scan. Dummy-ammo, native-infinite, creative/infinite physical supply,
unreadable definitions, incomplete scans, and contradictory role data fail
closed without an Armory search.

The default trigger is below one magazine of reserve and the default target is
three magazines. A request asks only for the deficit to that target. Reserve
at the trigger does not request. A zero trigger disables automatic requests.
Thresholds are limited by `HARD_MAX_RESERVE_MAGAZINES`, and one request is
limited by `HARD_MAX_REQUESTED_UNITS`; unusually large weapons can therefore
be filled through later bounded attempts rather than one unbounded claim.

Urgency is `OUT_OF_AMMO` only when the active gun has no displayed loaded round
and no physical reserve. Every other request is `LOW_RESERVE`. Urgency is
descriptive and must not bypass combat, squad-command, ally, rules-of-
engagement, explosive, movement, or native TaCZ safety.

## Immutable request identity

Each request records:

- contract version and per-guard generation;
- guard UUID, dimension, immutable origin, and optional squad UUID;
- active weapon role and TaCZ gun ID;
- urgency and observed magazine/load/reserve quantities;
- target and requested reserve units; and
- creation and expiry ticks.

Nil identities, negative quantities, zero-unit requests, excessive quantities,
time overflow, and lifetimes beyond the hard two-minute ceiling are rejected.
A request that observes a time before its creation fails expired. Dimension,
weapon role, and gun ID form the minimum live-weapon match, but matching them
still does not authorize withdrawal.

## Authoritative transient states

The successful path is:

`IDLE -> SEARCHING -> RESERVED -> TRAVELLING -> WITHDRAWING -> COOLDOWN -> IDLE`

An already-adjacent Armory may move from `RESERVED` directly to `WITHDRAWING`.
No compatible candidate enters `COOLDOWN`; path failure enters `UNREACHABLE`.
Both are retry states rather than per-tick loops. Cancellation returns to
`IDLE`. Invalid event/state pairs throw immediately so a programming error
cannot silently retain an owned reservation or navigation path.

`RESERVED`, `TRAVELLING`, and `WITHDRAWING` own the future candidate
reservation. Only `TRAVELLING` owns logistics navigation. The transition
contract reports every exit that must release either resource before the
state changes.

States and requests are transient and are never written to guard or Armory
NBT. Save, unload, restart, death, conversion, removal, or a future runtime
controller being discarded therefore cannot preserve an indefinite claim.

## Expiring reservations

`ArmoryReservationBook` is an in-memory, server-thread service with no level,
entity, block-entity, item-handler, or chunk references. It owns at most
`HARD_MAX_ACTIVE_RESERVATIONS` immutable entries and removes expired entries
lazily during already-requested operations; it has no ticker. Reservation
lifetimes are capped at one minute and clamped to the owning request expiry.
An unload/restart discards the entire transient book rather than preserving a
stale claim.

One request key owns at most one reservation. Repeating the same request is
idempotent, while reservations from different guards contend on the exact
dimension, Armory position, source slot, item ID, and full item tag. The sum of
claims cannot exceed the candidate's bounded live item-count snapshot. Guard
and Armory bulk-release operations support death, conversion, reassignment,
block removal, and chunk-unload cleanup; expiry remains the final safety net.

A reservation records both physical item count and ammunition units. Loose
ammunition uses one unit per item. A finite TaCZ ammo box is an indivisible
one-item supply and may contain more units than the request's remaining ideal
target; it is never split or rewritten. Empty, unreadable, incompatible,
creative, and infinite boxes fail closed. Candidate snapshots retain no live
`ItemStack` and are bookkeeping inputs only, not transfer authority.

## Transactional withdrawal

Withdrawal atomically consumes reservation ownership before checking live
state, so success, cancellation, expiry, and every failure release the claim
exactly once. It then verifies the live server-side guard identity, dimension,
AI/lifecycle state, squad identity, active primary-or-sidearm role, gun ID,
magazine capacity, finite physical reserve, Armory identity and access lock,
four-block proximity, source slot fingerprint, TaCZ compatibility, remaining
demand, and destination capacity. The loaded-state check uses `hasChunkAt`
before reading the block entity and never requests or loads a chunk.

Immediately before inventory access it also re-evaluates the Step 1 precedence
gate. Combat, squad commands, following, Hold Position, patrol-bound movement,
sidearm recovery, and an active or unreadable TaCZ weapon action all preempt
withdrawal without changing either inventory.

The final move reuses `GuardAmmoTransactions.transfer`, whose
simulate-before-commit snapshots and rollback checks cover both inventories.
If a player, hopper, or pipe reduces a loose-ammunition stack after reservation,
only the still-present compatible items can move. Replaced, emptied, locked,
unloaded, incompatible, satisfied, or full-destination cases move nothing. A
successful partial transfer or indivisible-box transfer ends that reservation;
a later bounded request may obtain any remaining deficit.

The transaction derives expiry time from the guard's live server level rather
than accepting a caller-provided clock. Candidate inspection likewise requires
the Armory to be the currently loaded block entity at its position and uses the
level's current game tick. This prevents stale or synthetic timestamps from
extending ownership.

## Step 3 validation matrix

| Boundary | Automated evidence |
| --- | --- |
| Two guards contend for one loose-ammo stack | Unit test proves claims total no more than the candidate snapshot and a third claim fails closed |
| Duplicate request or withdrawal | Unit test proves reservation is idempotent; Forge GameTest proves a claimed reservation cannot withdraw twice |
| Expiry, request clamp, Armory/guard release | Unit tests cover lazy expiry, expiry clamping, exact claim, and bulk release |
| Global memory and source bounds | Unit tests fill the 1,024-entry ceiling and reject an additional reservation |
| Player/hopper/pipe removes items after reservation | Forge GameTest removes most of the stack and proves only the ten remaining items transfer with exact total preservation |
| Source item or NBT is replaced | Forge GameTest replaces the reserved caliber and proves neither inventory changes during withdrawal |
| Finite ammo box | Unit and Forge tests prove one box remains indivisible and preserves its 120 encoded units |
| Combat or another higher-priority guard state starts during travel | Forge GameTest proves the final precedence gate consumes the claim but moves no ammunition |
| Transaction implementation | Source contract requires `ArmoryResupplyGate`, `GuardAmmoTransactions.transfer`, loaded-state probing, and no runtime AI activation |

The Step 3 checkpoint passes 352 plain JUnit/source-contract tests and all 77
transformed Forge correctness GameTests on JDK 17. The four new GameTests use
real Guard Villagers entities, the Armory block entity, and TaCZ loose-ammo and
ammo-box implementations. They remain development-only and are excluded from
the production JAR.

## Timing and performance limits

The provisional defaults use a deterministic two-second per-guard assessment
interval, a one-minute request lifetime, five-second success cooldown,
ten-second no-supply retry, and exponential ten-second unreachable retry capped
at two minutes. Stable UUID-derived offsets spread assessments across the
interval. Tick arithmetic saturates instead of wrapping into an immediate
retry.

Step 2 may use these contracts to build a loaded-chunk-only Armory index, and
Step 4 may invoke withdrawal after bounded movement reaches its target. Until
then, the services are not referenced by `GuardMixin`, the gun combat goal, or
the Armory block entity, and runtime behavior remains identical to 1.2.

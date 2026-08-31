# Armory resupply contracts

Step 1 defines the server-side contracts used by later Village Defense
Logistics phases. It does not register a goal, add a guard tick hook, index an
Armory, construct a path, reserve an item, or move ammunition. In particular,
creating an `ArmoryResupplyRequest` never authorizes a transfer; the eventual
withdrawal transaction must revalidate every live input.

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

## Timing and performance limits

The provisional defaults use a deterministic two-second per-guard assessment
interval, a one-minute request lifetime, five-second success cooldown,
ten-second no-supply retry, and exponential ten-second unreachable retry capped
at two minutes. Stable UUID-derived offsets spread assessments across the
interval. Tick arithmetic saturates instead of wrapping into an immediate
retry.

Step 2 may use these contracts to build a loaded-chunk-only Armory index. Until
then, the new classes are not referenced by `GuardMixin`, the gun combat goal,
or the Armory block entity, and runtime behavior remains identical to 1.2.

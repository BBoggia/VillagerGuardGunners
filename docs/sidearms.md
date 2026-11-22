# Sidearm System

Sidearms are being added as a server-authoritative weapon-loadout system. A
guard will still hold and fire exactly one main-hand TaCZ gun. The add-on will
move complete weapon stacks between private storage and the main hand; TaCZ
will continue to own drawing, aiming, fire modes, magazines, chambers, reloads,
bolting, heat, projectiles, sounds, animations, and synchronization.

Step 1 defines and tests the behavior contract. Step 2 adds the private,
versioned storage and complete entity lifecycle handling. Step 3 adds the
server-authoritative stack transaction. Step 4 connects that transaction to
the bounded combat goal with native-state gates and hysteresis. Step 5 extends
the existing ammunition quality-of-life paths across both stable weapon roles.
Step 6 adds opt-in format-3 natural sidearms and atomic dual-ammunition
issuance. Step 7 adds the player-facing configuration slot and closes the
remaining ownership, recovery, and lifecycle edge cases.

## Stable ownership states

| State | Main hand | Sidearm slot | Internal stowed-primary slot |
| --- | --- | --- | --- |
| `PRIMARY_ACTIVE` | Primary or ordinary equipment | Empty or configured sidearm | Empty |
| `SIDEARM_ACTIVE` | Sidearm | Empty | Primary |
| `RECOVERY_REQUIRED` | Preserved as found | Preserved as found | Preserved as found |

Only the first two layouts are normal. Every contradictory combination enters
recovery without guessing which stack should overwrite another. The sidearm
slot will be player-visible; the stowed-primary slot will exist only to retain
the primary during an active fallback or a recoverable failure. Neither weapon
slot will be appended to the item handler TaCZ uses to discover ammunition.

## Step 2 storage and persistence

Every transformed guard now owns a `GuardSidearmStorage` containing exactly two
single-item TaCZ gun slots. `GuardSidearmAccess` exposes a defensive one-slot
view of only the configured sidearm; the complete storage requires an internal
unforgeable access key, so other integrations cannot reach the stowed primary.
The view is not registered as a Forge capability and is not part of the existing
six-equipment-plus-three-ammunition TaCZ inventory view.

The record is stored under `villagerguardgunners:sidearm_loadout` with schema
version 3. A normal record saves the configured sidearm, a temporarily stowed
primary, the active-role flag, and—only while active—the activation reason,
activation game tick, target UUID, and primary standoff used for return
hysteresis, and an ownership token that detects replacement of the actively held
sidearm without depending on mutable TaCZ ammo/action data. Blank storage emits
no NBT. Version-1 and version-2 records remain readable. Because version 1 did
not retain the standoff, an active explosive-standoff record
migrates with a conservative threshold that waits for the engagement to end or
change rather than guessing an unsafe return distance.

Current-schema records are strict: unknown fields, wrong NBT types, invalid
weapon stacks, and incomplete active context are corrupt. Future versions and
corrupt records are retained byte-for-byte, exposed read-only, and reported as
`RECOVERY_REQUIRED` instead of being normalized or overwritten. The two stable
weapon fields can still be recovered when the source entity is about to be
removed.

Normal death applies Guard Villagers' equipment-drop chance and Vanishing
Curse behavior independently to every recoverable private stack. Zombie and
lightning conversion drain and drop the private stacks before the original
guard is discarded, matching the isolated ammunition lifecycle and preventing
either duplication or silent loss.

## Step 3 atomic transactions

`GuardSidearmTransactions` is the only production layer that moves a weapon
between the guard's main hand and private sidearm storage. Activation first
captures the complete primary, sidearm, storage metadata, and main-hand state;
it then clears the main hand, stows the primary, records the engagement
context, and equips the sidearm. Return performs the inverse operation. Every
postcondition compares the full item, count, and NBT, preserving magazines,
chambers, attachments, fire mode, durability, and third-party gun state.

Both directions are server-thread-only, require a living guard and a final
`nativeSwapSafe` result, and reject contradictory layouts without mutation.
They do not read targets, scan inventories or entities, perform pathfinding,
or run from the combat tick yet.

If a commit throws or fails verification, the transaction restores exact
snapshots and verifies the original stable layout. If that rollback cannot be
verified, it clears the main hand and retains the original sidearm and primary
in the two private slots. The inactive flag plus occupied stowed-primary slot
is deliberately persisted as `RECOVERY_REQUIRED`, preventing either weapon
from being silently overwritten or mistaken for a stable loadout.

An authorized player can resolve that fail-closed state from the guard
inventory with **Recover Weapons**. Recovery never guesses a role or replaces
the current main-hand item. It removes only this add-on's authentication marker,
returns every recognizable private gun through vanilla inventory/overflow
handling, clears the contradictory or unreadable record, and then allows normal
combat and inventory access to resume. A red **Recovery Required** combat status
makes the otherwise deliberate AI suppression visible instead of appearing as
an unexplained frozen guard.

Natural-loadout authentication now follows the issued primary into the private
stowed-primary slot only while the storage reports a valid `SIDEARM_ACTIVE`
layout. This preserves population-cap provenance across activation, save/load,
and return while continuing to fail closed for corrupt or contradictory data.

## Step 4 bounded tactical runtime

`GuardSidearmRuntimeController` now evaluates at most once per guard and server
tick. The ordinary path consumes only primitive target facts and the tactical,
dry, explosive, order, and action state already cached by
`NativeGunController`. It performs no entity query, path construction, task,
thread, or recurring inventory scan.

Activation is considered after the native weapon tick. That ordering means a
dry-primary fallback cannot bypass TaCZ's reload attempt or the add-on's
emergency ammunition-sharing attempt. Stored-sidearm definition, explosion,
eligibility, and ammunition checks run only after a real activation reason
exists. Their result is cached by private-storage revision and retried at a
bounded cadence when readiness or third-party metadata is unavailable.

An active sidearm evaluates return before another trigger attempt. Unsafe
reload, draw, bolt, cooldown, charge, or native-burst states defer the stack
transaction; the goal releases the trigger and suppresses new sidearm fire
until the return can settle. Failed or recovery-required returns also suppress
fire rather than allowing a stale engagement context to continue shooting.

The gun goal temporarily retains ownership when the target dies, is denied by
rules of engagement, or the add-on is disabled, so it can restore the
primary without exposing melee fallback. Positional squad movement yields for
that cleanup window and resumes afterward. Every successful stack change
immediately invalidates combat-safety state and rebinds TaCZ's native cached
weapon data through its normal draw path.

A backup activated for a dry primary also survives a brief target gap while
[bounded tactical context](guard-tactical-intent.md) remains active. The guard
releases aim and trigger during that gap; remembered enemies do not authorize
shots. Expiration resumes the same native-safe return and cleanup path.

## Step 5 ammunition quality-of-life

`GuardWeaponAmmunition` resolves the effective primary and configured sidearm
from the stable ownership state without exposing the private stowed-primary
slot. Crouch handoff and item-driven dropped-ammo pickup accept a physical TaCZ
supply when either role is compatible. A recovery-required record fails closed
without moving ammunition.

Emergency sharing remains recipient-driven and runs only after TaCZ rejects a
native reload. The active main-hand gun defines the requested caliber, so an
active sidearm uses the same path naturally. A donor may contribute loose ammo
used by either configured weapon, but first retains the configured number of
attachment-adjusted magazines for every donor weapon compatible with that
caliber. Ammo boxes remain reserve evidence and are never transferred.

Operator diagnostics capture both reserve summaries from the same fixed
three-slot handler and label the active role. Native dry-retry and last-share
state is displayed only beside that active role. All new work is event-driven:
two weapon reads, at most three reserve slots, and the existing bounded donor
or dropped-item query only when its original trigger occurs.

## Eligibility contract

A sidearm must be one unstacked TaCZ gun with readable common gun data and a
readable non-explosive classification. The default accepted normalized gun
type is `pistol`.

Modpack configuration will be able to add exact gun IDs or additional gun
types and exclude exact IDs. An exclusion wins over both inclusion routes.
Exact inclusion never bypasses the one-item, readable-definition,
readable-explosion, or non-explosive requirements. Missing and malformed
third-party metadata fails closed without moving the stack.

## Activation contract

The only supported activation reasons are evaluated in this order:

1. TaCZ has confirmed that the primary is dry after its native reload and
   ammunition-sharing paths have completed.
2. The primary is explosive and the guard's orders prohibit explosives.
3. The primary is explosive and the target is inside its mandatory standoff.

A request mutates nothing unless the feature is enabled, the configured
sidearm is eligible and ammunition-ready, and the native controller confirms a
weapon swap will not interrupt a burst or another unsafe action state.

Reloading, drawing, bolting, cooling, overheating, an obstructed friendly-fire
lane, ordinary range, and malformed primary metadata are not activation
reasons.

## Return and hysteresis contract

A dry-primary fallback remains active across live, permitted target replacements,
even if primary reserve ammo appears later. It returns after the engagement ends,
allowing the primary to retry normally on the next engagement. If the backup also
runs dry, the guard retains defensive gun-combat ownership and retries its ammo
supply. It does not cycle between two empty guns. Invalid backup definitions still
use the existing safe return and recovery handling.

An explosive fallback may return during the same engagement only after the
minimum active time and when:

- an order that prohibited the primary now permits it; or
- the target is at least the primary standoff plus the configured return
  margin away.

The initial defaults are 40 active ticks and a two-block return margin.
Feature disablement, engagement completion, and explosive-fallback target replacement bypass the
minimum active time, but no return may interrupt an unsafe native TaCZ state.
Non-finite, negative, or overflowing distance inputs fail closed.

## Transaction postconditions

The runtime transaction reports one of these outcomes:

- `ACTIVATED`: `PRIMARY_ACTIVE` became `SIDEARM_ACTIVE`.
- `RETURNED`: `SIDEARM_ACTIVE` became `PRIMARY_ACTIVE`.
- `ROLLED_BACK`: every stack and the original stable state were restored.
- `RECOVERY_REQUIRED`: the observed layout was already contradictory.
- `ROLLBACK_FAILED`: restoration could not be verified and every remaining
  stack was retained in a recoverable layout.

A result whose status contradicts its before/after states is rejected by the
contract. No failed transaction may be described as a successful activation or
return.

## Performance boundary

Eligibility is cached by weapon-slot revision and rechecked at a bounded
triggered cadence only while a transition is requested. Decision classification
uses enum and primitive inputs without entity scans, inventory scans,
collections, tasks, threads, pathfinding, or per-check result allocation.

The pre-sidearm reference baseline is:

- 279 unit and source-contract tests;
- 52 normal Forge GameTests;
- 14 performance scenarios with 14 JSON and 14 JFR evidence files.

Step 4 retains those performance boundaries. Its stored-gun and fixed inventory
assessment is outside the ordinary combat path and only follows a real switch
reason.

## Step 7 configuration and correctness hardening

The Guard Villagers inventory now exposes one authorized sidearm slot without
exposing the internal stowed-primary slot or expanding TaCZ's ammunition
capability. The server accepts only a complete eligible non-explosive sidearm;
client prediction remains structural. Shift-click continues to prefer an empty
primary, then the sidearm, and ammunition remains confined to the three ammo
slots. Primary and sidearm removal are locked while a sidearm transaction or
recovery layout owns the weapons; only the explicit recovery action is enabled
for that layout.

The public one-slot handler intentionally remains non-modifiable. Menu writes
use its guarded insert/extract transactions, so Forge's modifiable-handler slot
assumption cannot bypass storage isolation. Active layouts now carry a schema-3
ownership token on the held sidearm and in private metadata. Ammo, reload, heat,
fire-mode, and other TaCZ state may change normally without changing that
identity, while replacement by a player, command, or another mod fails into
`RECOVERY_REQUIRED` and suppresses combat and positional-command movement. The
token is metadata, not a recoverable stack, so it cannot duplicate a weapon on
death or conversion; it is removed when the sidearm returns to storage.

## Step 1 through 8 verification

Run:

```bash
./gradlew test compileGameTestJava compilePerformanceGameTestJava
./gradlew runGameTestServer
```

The Step 1 tests cover default and third-party eligibility, exclusion and
explosive precedence, narrow activation reasons, readiness and native-state
gates, dry-engagement retention, return hysteresis, invalid numeric inputs,
structural ownership, successful rollback, and recovery-required outcomes.
The Step 2 transformed cases cover an active main-hand/stowed-primary
save/reload, strict capability isolation, blank and corrupt records,
future-version read-only preservation and known-stack recovery, plus zombie
and lightning conversion drops. Step 3 adds full-NBT activation/return,
native-busy and contradictory-layout rejection, plus natural-primary
provenance across activation, save/load, and return. Step 3 finished at 306
unit/source-contract tests. Step 4 adds live dry-primary activation and
engagement-end restoration, explosive standoff/time hysteresis, conservative
version-1 migration, ordering/caching source contracts, and squad-movement
cleanup precedence. Step 5 adds a two-caliber AK-47/Glock scenario covering
manual handoff, dropped pickup, active-sidearm sharing, donor reserve floors,
and role-stable summaries. Step 6 adds strict format-3 parsing and diagnostics,
primary-plus-sidearm issuance, all-or-nothing dual-stack capacity checks, and
natural-primary authentication while stowed. Step 7 adds authorized menu and
shift-click behavior, sharing-before-switch ordering, explosive-order fallback,
shield preservation, dry-primary target-change continuity, both-weapons-dry rejection, active
save/load, active death ownership, and external-replacement recovery coverage.
Step 8 extends the opt-in performance matrix from 14 to 17 cases: 50 guards
switching simultaneously from dry primaries, an exact ten-sidearm/ten-launcher
explosive-standoff split, and 100 armed-idle guards with configured sidearms.
The reports bind exact loadout counts and bounded or exact active-sidearm counts
to the release JAR, retain every earlier timing threshold, and add no scan,
scheduler, thread, or pathfinding behavior to production code.
The current result is 316 unit/source-contract tests and all 73 required Forge
GameTests passing, followed by all 17 performance GameTests and their JSON/JFR
evidence verifier.

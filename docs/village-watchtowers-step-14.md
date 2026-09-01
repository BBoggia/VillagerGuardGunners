# Village Watchtowers Step 14: post integration

Step 14 connects the bounded watchtower marker result to the add-on's existing
natural-loadout, role, squad, Village Armory, and ammunition systems. It adds
no second weapon grant path, post-generation manager, recurring block/entity
scan, block-entity ticker, chunk ticket, or recurring tower task.

## Guard handoff and one-time selection

The structure thread still creates only a finalized Guard Villagers entity and
identifier-only metadata. Each member receives a strict pending assignment
containing its resolved natural-loadout pool, optional role ID, and the exact
difficulty that selected the group. That record survives the
protochunk/entity save handoff.

After Guard Villagers completes its normal initial equipment pass, the existing
entity-join bridge queues the handoff without consuming either persisted
record. The authoritative server-thread task first revalidates that the same
live entity is still in the same level, then claims the assignment and
`STRUCTURE` spawn source. An unload or shutdown before that task executes
therefore leaves the complete handoff retryable on the next live join:

- the member's explicit role must resolve before any equipment is attempted;
- every valid member is assigned the deterministic post squad only after its
  registry metadata was successfully created or found;
- upgrade chance, enabled spawn sources, crossbow-only replacement, difficulty,
  nearby natural-gunner cap, conditional pool matching, explosive permission,
  inventory capacity, and TaCZ compatibility remain authoritative;
- selection uses a stable seed derived from the post and member index; and
- `NaturalGuardLoadoutIssuance` remains the sole transaction that may create
  and commit the primary, optional sidearm, reserve ammunition, and provenance.

A missing/malformed assignment never falls through to the generic structure
pool. A missing role, missing pool, rejected entry, full inventory, explosive
restriction, or cap rejection leaves Guard Villagers' ordinary equipment with
no partial ammunition or false loadout provenance. Explicit group roles and
squad identity do not grant or move items. The nearby natural-gunner cap uses
one bounded, one-time query during selection and stops both its loaded and
unloaded index traversal as soon as the configured cap is proven; it is not a
recurring scan.

## Deterministic squad identity

One squad UUID is domain-separated from the post UUID and shared by every
successfully created member and the generated locked Armory. Metadata is
registered idempotently on the server thread without searching for nearby
guards or blocks. Automatic tower registration stops 256 entries before the
world registry's hard limit, preserving capacity for player-created or
recovered squads. If registration is unavailable, the guard is left
unassigned rather than receiving an unmanaged membership. Existing Squad
Command Ledger authorization, reassignment, command radius/caps, expiry, and
safety precedence remain unchanged.

The generated locked Armory independently performs this same persisted,
one-time registration resolution. This covers Peaceful towers, disabled guard
spawning, a zero occupant ceiling, or total member-spawn failure. If the
automatic registry reserve prevents registration, the Armory deliberately
falls back to public access instead of retaining an unusable squad lock.

## Closed Armory markers

Step 14 recognizes these additional marker forms:

```text
armory:public:empty
armory:squad:empty
armory:public:<canonical_loot_table_id>
armory:squad:<canonical_loot_table_id>
```

The grammar accepts no SNBT, item stack, command, or Java class name. One
piece-owned `ARMORY` action bit enforces the one-Armory ceiling. The built-in
tower uses:

```text
armory:squad:villagerguardgunners:chests/watchtower_armory
```

at local position `(2, 1, 2)`, against the interior wall and outside the
center doorway aisle. Its facing rotates with the template. Marker
placement stores a strict pending identity and stable non-zero, post-derived
seed without evaluating the declared chest loot table on the worldgen worker.
The first live server-thread load queues one one-shot task, validates a closed
bounded subset of the table, evaluates raw output through an aborting 64-stack
collector, verifies that every loose-ammo or ammo-box ID resolves in TaCZ, and
plans the result against a temporary nine-slot ammo-only handler. Supported
tables contain at most 64 constant total rolls, 64 pools/entries per level, and
256 combined entries, conditions, and functions; item and empty entries are
accepted, while nested/composite/dynamic entries and variable roll providers
fail empty. The inventory is committed only if the entire plan succeeds. A
missing, malformed, oversized, undefined, non-ammunition, or over-capacity
result produces a completed empty Armory rather than partial stock or
retryable duplication.

Generated Armory tables intentionally do not run Forge global loot modifiers:
an arbitrary modifier can allocate or execute without an enforceable bound.
Accepted table conditions and functions still run on the server thread, with
their invocation count bounded by the closed definition. As with any mod hook,
the internal work of a third-party condition/function cannot be preempted by
Minecraft. This keeps the host table traversal and output bounded.

The Armory persists a strict generated-post identity after setup. A piece
revisit with intact state is rejected by the claimed action; an interrupted
revisit that encounters the same generated Armory also leaves its inventory
unchanged. Public, squad-locked, empty, and declared-loot forms all use the same
existing Armory inventory, automation, comparator, drop, reservation, and
resupply code.

The built-in group maps Marksman, Support, Breacher, and Demolitions positions
to dedicated weapon pools. The corresponding Armory table includes compatible
rifle, machine-gun, shotgun, and precision-rifle ammunition while retaining
the global natural explosive-weapon permission as the final authority.

## Persistence and upgrade boundary

The piece marker ledger advances to schema 2 because its supported action mask
now includes `ARMORY`. Missing, schema-1, future, or malformed piece state is
disabled and therefore cannot retroactively add an Armory or respawn guards in
an already generated tower. Guard post identity remains schema 1. Pending
member assignment and generated Armory identity use separate strict schema-2
records. The assignment persists generation difficulty; the Armory persists
pending/complete state and its loot seed. Completed schema-1 Armory identities
migrate as completed and are never rerolled. Corrupt optional integration data
cannot invalidate unrelated Guard Villagers or Armory inventory data.

## Performance bounds

One new tower performs at most two built-in marker actions, four constant-size
member handoffs, four one-time server-thread selections (including their
early-terminating nearby-cap query), at most one idempotent squad registration,
one one-shot Armory completion task, one raw loot-table evaluation aborted after 64 emitted
stacks, and one nine-slot transaction plan. After completion, Step 14
contributes zero tower scans, manager ticks, scheduled retries, block-entity
ticks, or chunk loads. Normal
guard combat, squad commands, and demand-triggered Armory resupply continue to
use their existing bounded systems.

## Validation

The Step 14 gate includes strict assignment and Armory identity codec tests,
closed marker grammar tests for every access/stock mode, schema-2 action-ledger
tests, deterministic domain-separated identity tests, template rotation checks,
source/artifact contracts, and a Forge integration test covering declared
roles, natural issuance, shared squad registration, locked stocked Armory
state, valid ammunition, and duplicate-resistant piece revisits.

Final Step 14 evidence:

- `./gradlew test compileGameTestJava`: 443 JVM tests, zero failures, and a
  successful GameTest source compilation;
- `./gradlew runGameTestServer`: all 94 required Forge GameTests passed;
- `./gradlew build`: production build and `verifyReleaseArtifact` passed; and
- `villagerguardgunners-1.4-dev.jar`: 924,356 bytes, SHA-256
  `be24bab3cbd36137e64747b1a06afcb835229f53f82fe054fe6eef9b8bb9cc2b`.

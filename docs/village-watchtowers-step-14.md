# Village Watchtowers Step 14: post integration

Step 14 connects the bounded watchtower marker result to the add-on's existing
natural-loadout, role, squad, Village Armory, and ammunition systems. It adds
no second weapon grant path, post-generation manager, block or entity scan,
block-entity ticker, chunk ticket, or recurring tower task.

## Guard handoff and one-time selection

The structure thread still creates only a finalized Guard Villagers entity and
identifier-only metadata. Each member receives a strict pending assignment
containing its resolved natural-loadout pool and optional role ID. That record
survives the protochunk/entity save handoff.

After Guard Villagers completes its normal initial equipment pass, the existing
entity-join bridge atomically claims the assignment and the `STRUCTURE` spawn
source. The actual work then runs on the authoritative server thread:

- the member's explicit role must resolve before any equipment is attempted;
- every valid member is assigned the deterministic post squad;
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
squad identity do not grant or move items.

## Deterministic squad identity

One squad UUID is domain-separated from the post UUID and shared by every
successfully created member and the generated locked Armory. Metadata is
registered idempotently on the server thread without searching for nearby
guards or blocks. Existing Squad Command Ledger authorization, reassignment,
command radius/caps, expiry, and safety precedence remain unchanged.

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

at local position `(4, 1, 2)`. Its facing rotates with the template. The
declared chest loot table is evaluated once during marker placement with a
stable post-derived seed. Loot is planned against a temporary nine-slot,
ammo-only Armory handler and committed only if every generated stack fits. A
missing, malformed, oversized, non-ammunition, or over-capacity result produces
a completed empty Armory rather than partial stock or retryable duplication.

The Armory persists a strict generated-post identity after setup. A piece
revisit with intact state is rejected by the claimed action; an interrupted
revisit that encounters the same generated Armory also leaves its inventory
unchanged. Public, squad-locked, empty, and declared-loot forms all use the same
existing Armory inventory, automation, comparator, drop, reservation, and
resupply code.

## Persistence and upgrade boundary

The piece marker ledger advances to schema 2 because its supported action mask
now includes `ARMORY`. Missing, schema-1, future, or malformed piece state is
disabled and therefore cannot retroactively add an Armory or respawn guards in
an already generated tower. Guard post identity remains schema 1. Pending
member assignment and generated Armory identity use separate strict schema-1
records, so corrupt optional integration data cannot invalidate unrelated Guard
Villagers or Armory inventory data.

## Performance bounds

One new tower performs at most two built-in marker actions, four constant-size
member handoffs, four one-time server-thread selections, one squad registration,
one loot-table evaluation capped to 64 returned stacks, and one nine-slot
transaction plan. After placement, Step 14 contributes zero tower scans,
manager ticks, scheduled retries, block-entity ticks, or chunk loads. Normal
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

- `./gradlew test compileGameTestJava`: 441 JVM tests, zero failures, and a
  successful GameTest source compilation;
- `./gradlew runGameTestServer`: all 93 required Forge GameTests passed;
- `./gradlew build`: production build and `verifyReleaseArtifact` passed; and
- `villagerguardgunners-1.4-dev.jar`: 913,561 bytes, SHA-256
  `11a0472b9d1446a44f9c5df0cb9c574d7ee3ed7e66fa39d389e72fa511338572`.

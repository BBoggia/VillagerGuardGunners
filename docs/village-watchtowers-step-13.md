# Village Watchtowers Step 13: marker spawning and post identity

Step 13 connects the Step 12 composition layer to Minecraft structure
placement. The built-in tower now contains one data-mode structure marker at
local position `(4, 1, 4)`. When that marker is processed, it performs one
bounded guard-group selection, creates at most four Guard Villagers through
the normal `MobSpawnType.STRUCTURE` lifecycle, removes the marker, and records
enough state to make every later piece visit a no-op.

Role assignment, member-specific natural-loadout issuance, squad membership,
Armory creation, and loot remain Step 14 responsibilities. Until that
integration is added, generated guards can still enter the add-on's existing
generic `STRUCTURE` natural-loadout path; the Step 12 member role and loadout
IDs are not interpreted by Step 13.

## Closed marker grammar

Format 1 recognizes only:

```text
guard_post:<canonical_resource_location>
```

The metadata is capped at 128 characters and contains only a guard-post group
ID. It cannot contain commands, SNBT, item stacks, entity classes, or arbitrary
Java names. Unknown actions, malformed IDs, noncanonical IDs, unavailable
groups, and unsupported data fail closed. Every marker block handled inside
the current placement clip is replaced with air even when its metadata is
unknown or invalid.

One piece may claim at most eight recognized marker positions. The first
recognized guard-post action wins; repeated positions, duplicate actions, and
work beyond the ceiling are consumed without creating another group. The
built-in blueprint has exactly one marker, and its generator validates all
marker block-entity keys plus the eight-marker ceiling before writing the NBT.

## One-shot piece state

Each new piece stores a strict schema-1 `VggPost` record containing:

- the 64-bit selection seed captured from the structure-generation random
  source;
- up to eight claimed marker positions;
- a claimed-action bit mask;
- the deterministic post UUID, once allocated;
- the selected group ID and stable variant name, when selection succeeds; and
- the number of guards successfully added, from zero through four.

The marker is claimed before group lookup, selection, or entity creation. This
ordering deliberately favors safe absence over duplication if processing is
interrupted. Missing state on a piece saved by an earlier development build,
future versions, extra keys, malformed UUIDs, duplicate positions, impossible
counts, and inconsistent state all load disabled. This enforces the Phase 0
no-retro-generation rule instead of silently adding occupants to an existing
tower after an upgrade.

Peaceful follows the same one-shot rule: the marker is claimed once and creates
zero guards. Changing the world to Easy, Normal, or Hard later does not reroll
or populate that completed post.

## Deterministic identity

The post UUID is a name-based UUID derived from a versioned domain plus the
world seed, dimension resource ID, and final template origin. Each possible
guard slot receives another name-based UUID derived from the post UUID and its
member index. This provides stable identities without global registries,
random UUID state, nearby-entity queries, or chunk scans.

Every successfully created guard also persists a strict schema-1 provenance
record under `villagerguardgunners:guard_post`:

```text
Version, PostId, Group, Variant, Member
```

The record identifies provenance only. It cannot issue an item, execute an
action, or reconstruct a missing entity. Unknown, future, noncanonical, or
malformed records recover as an ordinary unassociated guard.

## Spawn boundary and performance

The four fixed local slots are the marker center, west, east, and north, then
rotated with the template. Before creating a guard, Step 13 checks only that
the slot's floor, feet, and head are inside the current placement bounding box,
the floor has a sturdy dry top face, and the feet/head cells are dry and have
empty collision shapes. It does not query nearby entities, acquire chunks, or
search for alternate positions.

Each accepted guard receives its deterministic UUID and persisted post record,
is marked persistent, is finalized with `MobSpawnType.STRUCTURE`, and is added
through the current `ServerLevelAccessor`. There is no tower manager, retry
task, tick handler, forced chunk, post-generation scan, or reconstruction
pass. Once structure placement returns, Step 13 adds zero recurring work.

## Validation

The Step 13 automated boundary includes:

- pure tests for deterministic post/member UUIDs, all four rotated slot maps,
  exact difficulty mapping, closed marker parsing, piece-state transitions,
  strict NBT round-trips, future/malformed rejection, and every hard limit;
- source contracts that reject commands, item creation, scans, chunk
  acquisition, and recurring work from the marker/spawn layer;
- blueprint contracts for the exact central marker and generated-NBT writer;
- a Forge test that inspects marker NBT under every rotation, places the real
  template, creates the selected bounded group, verifies distinct identities,
  saves and reloads both guards and structure state, and proves live and
  reloaded revisits do not duplicate guards; and
- a Forge test that generates on Peaceful, changes to Hard, and proves the
  consumed marker remains empty.

The clean Java 17 Step 13 gate passes 426 plain JUnit/source/artifact tests,
both development source sets compile, the reobfuscated release artifact passes
its content boundary, and all 91 required Forge correctness GameTests pass.

The verified development artifact is `villagerguardgunners-1.4-dev.jar`, size
878725 bytes, SHA-256
`51ac3d9eb598b91e303344c1cdee99cd6b94bb49d029d2960175db1790aa4969`.

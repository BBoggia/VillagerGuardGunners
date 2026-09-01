# Village Watchtowers Step 16: lifecycle and compatibility hardening

Step 16 hardens completed and partially integrated Village Watchtowers across
entity removal, block replacement, reloads, missing datapack definitions, and
future save formats. It retains the central rule that a tower is not a runtime
manager: after its marker actions finish, ordinary Guard Villager, squad,
Armory, and Minecraft structure lifecycles remain authoritative.

## Opaque future-data quarantine

Guard post identity, pending member assignment, and generated Armory identity
remain strict versioned formats. A malformed or future record still produces
no active Java object and cannot select a loadout, assign a role or squad,
evaluate loot, or authorize Armory access.

Step 16 additionally preserves each unreadable NBT tag exactly while the
containing guard or Armory is saved by this older version. This prevents a
temporary downgrade or partial modpack upgrade from erasing data that a newer
version may understand. Recognized state may replace opaque state only through
an explicit normal setter or initialization transaction.

An unreadable guard post identity quarantines its pending natural-spawn source
and assignment together. It does not fall through to the generic
structure-spawn loadout pool. A valid identity paired with an unreadable
assignment is handled the same way. The source remains unclaimed so the future
version retains the complete transaction boundary.

An unreadable generated-Armory identity blocks both pending preparation and
fresh initialization. Its inventory and separately versioned squad lock remain
ordinary preserved Armory data. No older-version loot task is queued for the
opaque record.

## Existing-object lifecycle

Completed marker claims remain the only respawn/rebuild authority. After a
tower has generated:

- a killed, converted, discarded, or otherwise removed guard is not recreated;
- a guard moved to another squad is not reassigned by proximity or by a piece
  revisit;
- a broken or replaced Armory is not reconstructed and its former marker cell
  is not rewritten;
- an unloaded Armory leaves the transient loaded index and releases its
  reservations through the existing block-entity lifecycle;
- guard death and conversion continue through the established ammunition,
  sidearm, equipment-drop, provenance, role, and squad behavior; and
- removing a guard-post group, role, loadout pool, or loot table does not scan
  completed chunks or rewrite already persisted entities and blocks.

A pending current-format assignment is still a one-shot operation. If its
declared role, loadout pool, or TaCZ definition is unavailable when consumed,
the established selector/issuer fails closed and leaves normal Guard Villagers
equipment intact. A missing pending Armory loot table completes empty rather
than retrying indefinitely. Neither failure starts a reload listener that
mutates existing posts.

## Datapack and structure compatibility

The built-in tower continues to use only its namespaced structure, structure
set, biome tag, template, group, role/loadout references, and loot table. The
worldgen package does not access or mutate vanilla jigsaw/template pools and
contains no client-only class reference. Standard datapack priority therefore
remains the only replacement mechanism.

Removing or filtering the structure-set resource prevents Minecraft from
scheduling new starts. Replacing the watchtower biome tag with an empty tag has
the same new-generation result. Existing guard and Armory records contain
canonical resource identifiers but do not require the watchtower structure,
structure set, template, or guard-group registry entry to deserialize.

Minecraft remains responsible for ordering and placing structures from
different structure sets. This add-on performs no nearby-village, outpost,
stronghold, road, or third-party-structure query and creates no order-sensitive
exclusion or pool mutation. Visual overlap testing with representative packs
remains part of the Step 17 compatibility matrix.

## Attributable reload failures

`/vgg watchtowers errors [page]` exposes the latest rejected guard-post group
documents to permission-level-2 operators. It reports at most eight entries per
page. Each parser reason is normalized, stripped of control characters, and
capped at 512 characters before publication. The command reads the immutable
last reload snapshot and performs no reload, file read, structure lookup,
chunk access, or post mutation.

Atomic last-known-good group publication remains unchanged: a rejected reload
keeps the previous accepted map and generation. Unrelated datapack listeners
continue independently.

## Server and client boundary

Structure selection, marker claims, guard creation, post integration, squad
registration, and generated loot completion remain server-only. Clients receive
the already established Guard Villagers/TaCZ entity state and Armory block-
entity update data; post provenance and pending assignment are not executable
client state and need no new packet. Step 16 adds no network channel or
client-side worldgen class load.

## Retained validation

The automated boundary includes opaque current/future/malformed NBT unit
tests, a live Forge case that proves future guard and Armory records remain
inactive and exact across save, the existing structure persistence cases, and
an expanded integrated-post case that removes a guard, reassigns another,
replaces the Armory, and proves a piece revisit performs no repair.

Source and artifact contracts retain the standard datapack resources, absence
of vanilla template-pool mutation and client imports from worldgen, bounded
error reporting, and the absence of a tower manager, scan, chunk ticket, or
retro-generation path. Actual client joins, process-kill interruption,
third-party structure-pack combinations, and multi-seed visual checks remain
Step 17 release evidence.

Final Step 16 evidence:

- `./gradlew test compileGameTestJava verifyReleaseArtifact`: 457 JVM tests,
  zero failures, both Forge source sets
  compiled, and the production artifact boundary passed;
- `./gradlew runGameTestServer`: all 96 required Forge GameTests passed; and
- `villagerguardgunners-1.4-dev.jar`: 938,503 bytes, SHA-256
  `95d79c53f5f0f111689a4791f2002e2e15a8ec4d09a59cdff96fab0231ae0de6`.

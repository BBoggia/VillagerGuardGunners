# Village Watchtowers Step 15: configuration and diagnostics

Step 15 adds server-owned configuration, bounded operator diagnostics, and a
clear modpack override contract to the Step 14 watchtower implementation. It
does not add a tower manager, retrofit pass, structure locator, chunk ticket,
or recurring task.

## Schema-10 configuration

Fzzy Config schema 10 adds a `watchtowers` section with five synchronized
server values:

| Setting | Default | Bound | Effect on new work |
| --- | --- | --- | --- |
| `enabled` | `true` | Boolean | Rejects new generation points before terrain sampling. |
| `spawnGuards` | `true` | Boolean | Consumes guard markers without spawning occupants when disabled. |
| `maximumGuardsPerPost` | `4` | 0–4 | Caps the already selected deterministic group without creating replacement members. |
| `placeArmory` | `true` | Boolean | Consumes Armory markers as air when disabled. |
| `stockArmory` | `true` | Boolean | Creates the declared generated Armory identity and lock but completes it empty when disabled. |

Release hardening advances the current config to schema 11 with one additional
synchronized value:

| Setting | Default | Bound | Effect on new work |
| --- | --- | --- | --- |
| `guardGunChanceMultiplier` | `1.5` | 0–3 | Multiplies each new tower member's configured difficulty chance once and clamps the result to 100%, without bypassing any remaining loadout or safety gate. |

Schema-10 files initialize only this new multiplier. At the default, the Easy,
Normal, and Hard tower chances are 52.5%, 75%, and 97.5% instead of the regular
35%, 50%, and 65%. A value of 1 matches regular guards and 0 prevents tower gun
upgrades. The calculation happens only during initial issuance and adds no
recurring tower or guard work.

Schema-9 files receive these defaults exactly once. All prior combat, loadout,
role, squad, ammunition, and Armory-resupply values remain unchanged. Fzzy's
validated integer corrects values outside 0–4 before a snapshot can reach
world generation, and the immutable policy rejects any out-of-range value if
constructed outside that validator.

Disabling `enabled` prevents only new structure starts. It deliberately does
not remove a serialized start, interrupt a partially placed piece, or rewrite
an existing tower. Marker controls are read once for each marker action. A
disabled marker is still claimed and removed, so re-enabling a setting later
does not retrofit guards or Armories and cannot duplicate a post.

## Modpack precedence

**Datapack > configuration** for definition; configuration is a one-way
restriction on the result. Datapacks define the structure set and spacing,
allowed biome tag, structure/template resources, guard-post groups, natural
loadout pools, roles, and initial Armory loot. Configuration cannot invent a
missing resource, broaden a datapack pool, select an unlisted biome, or repair
a rejected reload.

Modpacks can disable watchtower placement without Java integration by removing
or filtering the built-in structure-set resource, or by replacing
`#villagerguardgunners:has_structure/watchtower` with an empty tag.
The server configuration is an additional emergency switch. Either boundary
may prevent new starts; neither changes already generated chunks. Guard gun
combat, ordinary natural loadouts, player-created squads, Village Armories,
and resupply remain independent.

## Operator diagnostics

All diagnostics inherit the permission-level-2 `/vgg` root and operate only on
immutable or already loaded snapshots:

- `/vgg watchtowers status` reports structure/piece registration, the resolved
  policy snapshot, accepted guard-group generation, publication count, and
  rejection count.
- `/vgg watchtowers groups [page]` lists at most eight accepted group IDs per
  page in stable lexical order, with bounded variant and parent counts.
- `/vgg watchtowers inspect <guard>` reads one explicitly selected loaded Guard
  Villager's persisted post identity, pending/consumed integration state, role,
  and squad identity.

The commands never locate a structure, acquire a chunk, inspect an unloaded
entity, generate a start, evaluate loot, retry integration, or mutate a post.
An arbitrarily large requested page clamps to the final page before index
arithmetic.

## Retained validation

Plain tests cover schema-9 migration, preservation of current explicit values,
Fzzy bound correction, immutable hard-bound rejection, early generation
disablement, one-way marker gates, command pagination, read-only source
boundaries, translations, documentation, and packaged-class requirements.

The Forge configuration case places a Normal watchtower with a one-guard cap
and initial Armory stock disabled, executes every watchtower diagnostic,
verifies the permission gate and unchanged post state, then consumes a second
tower's guard and Armory markers while disabled. Re-enabling the controls and
revisiting that piece creates nothing. Full new-world/existing-world datapack
removal and retained restart evidence remain part of Steps 16–17.

## Step 15 exit boundary

Step 15 is complete when schema 10 migrates without disturbing schema-9
choices, every new action is bounded by an immutable policy snapshot, modpack
precedence is explicit, and the permission-gated diagnostics remain paginated
and read-only. Lifecycle recovery, missing-resource upgrade behavior,
third-party compatibility, dedicated-server evidence, and 100-start/100-tower
measurements remain reserved for Steps 16 and 17.

Final Step 15 evidence:

- `./gradlew clean test compileGameTestJava compilePerformanceTestJava
  verifyReleaseArtifact`: 450 JVM tests, zero failures, both Forge source sets
  compiled, and the production artifact boundary passed;
- `./gradlew runGameTestServer`: all 95 required Forge GameTests passed; and
- `villagerguardgunners-1.4-dev.jar`: 933,712 bytes, SHA-256
  `e72fce155e719789dccaf42a28df89bec87aa4c0488871177ef8be21511c6585`.

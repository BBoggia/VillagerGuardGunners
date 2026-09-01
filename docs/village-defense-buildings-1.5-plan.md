# Village Defense Buildings 1.5 baseline and integration contract

Steps 1 through 4 establish the 1.5 village-integration line. Step 5 adds the
five biome-aware visual building shells. Step 6 activates bounded garrisons for
Barracks, Sentry Posts, and integrated Watchtowers. Step 7 activates one public,
optionally pre-stocked VGG Armory in each eligible Armory building while
training behavior remains passive. Development artifacts use `1.5-dev`;
they must not be presented as a finished 1.5 release.

## Exact inherited baseline

| Identity | Value |
| --- | --- |
| Development branch | `v1.5-support` |
| 1.4 baseline branch | `v1.4-support` |
| 1.4 baseline commit | `be63f09fc9697666aa67a8eb66055214fac2d568` |
| 1.4 baseline artifact | `villagerguardgunners-1.4.jar` |
| 1.4 artifact SHA-256 | `2ebf9920e7916bc9d7e81e85a55c522020e7149eaabf74d2354bfe6c838e59ac` |
| Minecraft | `1.20.1` |
| Forge | `47.4.23` |
| Guard Villagers | `1.20.1-1.6.19` |
| TaCZ | `1.1.8-hotfix` |
| Fzzy Config | `0.7.6+1.20.1` |
| Java | `17` |

The baseline is a local recovery point, not a claim that every manual 1.4
release row passed. No public `v1.4` tag was created.

## Feature boundary

Version 1.5 reserves five village-attached building identities:

1. `barracks` — guard housing and the main bounded village-defense garrison.
2. `armory` — village-scale ammunition storage and quartermaster space.
3. `sentry_post` — a small roadside defensive position with minimal staffing.
4. `watchtower` — a village-road-connected tower, distinct from the existing
   rare standalone 1.4 Watchtower structure.
5. `training_yard` — an unstaffed or lightly staffed visual/training space that
   can later support non-combat village flavor without a recurring controller.

The standalone 1.4 Watchtower keeps its structure set, spacing, marker state,
configuration, and datapack contracts. The integrated `watchtower` is a
village jigsaw piece and must not reuse the standalone structure start or make
the two features depend on one another.

Only new village pieces selected while generating new chunks are eligible.
There is no retro-generation, loaded-village scan, village repair pass, chunk
ticket, or attempt to guarantee that every village receives a defense
building.

## Reserved resource and persistence schemas

The following names are reserved before functional placement work begins:

- pool-element type: `villagerguardgunners:village_building`;
- injection entry IDs: `villagerguardgunners:<building>/<variant>`;
- templates: `villagerguardgunners:village/<biome>/<building>`;
- definitions: `data/<namespace>/village_buildings/<id>.json`;
- one optional template data marker: `village_anchor:<building>`;
- persistent building record root: `VggVillageBuilding` with schema `1`;
- stable building types: `barracks`, `armory`, `sentry_post`, `watchtower`, and
  `training_yard`.

The removed Step 2 compatibility probe had the internal entry ID
`villagerguardgunners:integration_probe/plains`. It is not a sixth building
type. Step 5 removes its definition when the reviewed VGG templates become
available, while retaining the ID only for backward codec compatibility with
older development structure starts.

Format-1 definition documents select only a target pool, template, processor
list, stable building type, projection, and positive bounded weight. They may
not contain commands,
entity SNBT, item NBT, Java class names, code, arbitrary registry mutation, or
unbounded collections.

## Hard limits and ownership

- at most five target village pools per publication;
- at most 50 injected entries across those pools: 25 built-in biome variants
  plus 25 bounded replacement or extension slots for modpacks;
- entry weight from 1 through 5;
- one stable entry ID per injected element;
- one anchor marker per placed functional building piece;
- at most 65,536 passive building records per dimension, with no scan or
  per-record tick;
- at most four generated guards for one future building;
- at most one generated Armory for one future building;
- zero recurring ticks, post-generation scans, synchronous chunk loads, or
  forced chunks owned by village integration; and
- one startup publication attempt using immutable input and bounded output.

These are ceilings rather than target content counts. Later building-specific
steps may choose stricter limits.

## Additive pool integration

Step 2 registers a serializable `VillageBuildingPoolElement` and performs one
startup-only integration attempt after dynamic registries have loaded. The
implementation copies the target pool's raw weighted entries and flattened
selection list, appends the VGG element, swaps both lists, and invalidates the
pool's cached maximum height. Existing objects and their order are preserved.

Idempotence is content-based: the stable VGG entry ID is checked in the live
pool before mutation. A second registration attempt or a duplicated lifecycle
callback therefore records `already_present` and changes neither list. The
logic does not rely on a static “ran once” flag, so a stopped integrated server
cannot poison a later server in the same client process.

Missing target pools, processor lists, and structure templates are skipped
independently and reported in one bounded startup diagnostic. Existing
templates are also checked for malformed, duplicate, or type-mismatched anchor
markers before any target pool is changed. Pool changes are prepared first and
committed only after the complete bounded preflight. A missing third-party or
replaced vanilla pool cannot crash the server or cause fallback mutation of a
different pool. Third-party entries already present remain untouched.

The schema-12 Fzzy Config master switch is `villageBuildings.enabled`. It
remains `false` through Step 5's visual-review stage. With the switch off,
registry contents are not inspected or mutated. Once functional templates have
passed the retained visual and compatibility matrix, this development default
may be reviewed before release.
Five default-on, one-way type switches can suppress Barracks, Armories, Sentry
Posts, integrated Watchtowers, or Training Yards without rewriting datapack
definitions. The integrated Watchtower switch never affects the standalone
1.4 structure.

## Step 3 datapack publication

Step 3 originally moved the inert probe declaration to a format-1 resource and
registers a strict reload listener for
`data/<namespace>/village_buildings/<id>.json`. Documents from modpack-owned
namespaces are supported and compile in stable ID order. Step 5 removes that
probe resource and publishes the reviewed building matrix through the same
format.

Publication is atomic and last-known-good. One invalid document, duplicate
placement tuple, or exceeded hard limit rejects the complete candidate while
retaining the previously accepted definitions. Individual files are capped at
16,384 characters and depth 16; diagnostics are normalized and bounded.

Last-known-good state is scoped to one server session. The stopped-server
event clears the singleton snapshot, so a rejected initial reload in a second
integrated world cannot inherit definitions from the first world.

The initial resource reload supplies the snapshot used by startup pool
integration. Later `/reload` operations validate a future snapshot but do not
rewrite live pools; accepted changes require a server restart. This preserves
third-party pool entries and avoids unsafe removal of pieces already available
to world generation. The complete format and example are documented in
[`village-defense-buildings-step-3.md`](village-defense-buildings-step-3.md).

## Step 4 anchor and one-shot lifecycle

Step 4 implements the reserved `village_anchor:<building>` structure-data
marker and `VggVillageBuilding` schema-1 record. A definition-backed template
may have zero or one anchor. Duplicate, malformed, unknown, or type-mismatched
anchors reject placement before blocks are written. A valid transformed marker
cell is consumed and produces a deterministic identity from the world seed,
dimension, entry ID, building type, and anchor position.

Records live in passive per-dimension SavedData and reserve bounded at-most-once
claims for the anchor, garrison, Armory, and initial stock. UUID and physical-
anchor indexes make repeat processing O(1) and reject datapack-change
conflicts. Future or malformed files disable the complete index and preserve
the unreadable root unchanged, preventing a downgrade or corruption from
rerunning generation work.

World-generation workers create only immutable identity data. A one-shot
server-executor handoff performs the SavedData claim without a chunk ticket,
chunk load, village/entity scan, ticker, or repair manager. Step 4 claims only
the anchor; functional actions remain dormant. The complete contract is in
[`village-defense-buildings-step-4.md`](village-defense-buildings-step-4.md).

The custom element accepts the missing `building_type` field only when reading
the default-off Step 2 compatibility probe and treats that legacy value as
`barracks`; every new encoding writes the field. Actual anchor processing runs
after successful placement through the same rotation and projection processor
chain, with only vanilla's structure-block ignore removed for the validated
marker pass.

## Step 5 biome-aware visual templates

Step 5 replaces the inert probe with 25 generated assets: the five stable
building layouts crossed with plains, desert, savanna, snowy, and taiga village
palettes. Each weight-1 definition targets only its matching vanilla houses
pool and each template contains exactly one outward-facing connector to that
biome's streets pool plus one type-matching anchor.

The source blueprint is strict and bounded to 16 blocks per axis and 4,096
blocks per template. Its generator deletes stale output, compiles compressed
NBT and format-1 definitions, and the artifact gate requires all 50 generated
resources. The complete visual and runtime boundary is documented in
[`village-defense-buildings-step-5.md`](village-defense-buildings-step-5.md).

This step activates no functional action beyond the passive anchor claim.
Barracks do not spawn guards, Armories do not place or stock VGG Armory blocks,
Sentry Posts and Watchtowers do not assign posts, and Training Yards create no
entity or controller. The default-off master switch remains the manual visual-
review gate.

## Step 6 bounded village garrisons

Step 6 joins the shared anchor lifecycle to the existing Guard Villagers
structure-spawn, guard-post assignment, role, squad, natural-loadout,
ammunition, and TaCZ issuance systems. Barracks select at most four occupants,
Sentry Posts at most one, and integrated Watchtowers at most two on their upper
platform. Three strict format-1 `guard_post_groups` resources provide Easy,
Normal, and Hard compositions and remain replaceable by datapacks.

The anchor's existing `GARRISON` bit is the sole persistent action boundary.
Peaceful or configured-zero buildings claim it without occupants; exact repeat
processing is a no-op. Candidate cells must already be loaded and safe, and a
selected group is prepared and added all-or-none. No village scan, entity
search, chunk load, ticket, persistent ticker, respawn manager, retrofit, or
repair pass is introduced. A not-yet-live generation chunk receives at most 40
one-tick deferred checks before failing closed. Config schema 14 adds only a garrison switch, a
global 0–4 ceiling, and a clamped one-time gun-chance multiplier. The complete
contract is in
[`village-defense-buildings-step-6.md`](village-defense-buildings-step-6.md).

## Step 7 public village Armories

Step 7 routes only the `armory` building type from the existing anchor handoff
to the generated-Armory placer. The transformed anchor must be loaded and still
air. One public Armory uses the deterministic building UUID as its generated
identity and receives at most one bounded, ammo-only evaluation of
`villagerguardgunners:chests/village_armory`.

The persistent `ARMORY` and `INITIAL_STOCK` decisions are claimed together
before placement. Repeats cannot duplicate stock, a matching already-saved
Armory repairs only the missing claims, and a removed completed Armory is not
regenerated. Config schema 15 adds independent placement and initial-stock
suppressors beneath the default-off master switch. Existing loaded-index,
lock, reservation, travel, withdrawal, and resupply contracts remain
authoritative. The complete contract is in
[`village-defense-buildings-step-7.md`](village-defense-buildings-step-7.md).

## Performance and compatibility invariants

The integration cost is proportional only to the small number of declared
entries plus the existing contents of the specifically named target pools. It
runs once before gameplay, retains no server/level/pool reference, schedules
no task, and adds no runtime query. Selection after startup remains Minecraft's
normal weighted jigsaw selection.

Datapack and village-overhaul compatibility is additive rather than
replacement-based. The implementation never clears a pool, reconstructs a
fallback holder, changes an existing weight, sorts entries, or writes a
vanilla namespace resource. If a future overhaul removes a named pool, VGG
skips it. Actual mixed-mod village generation remains a mandatory manual gate.

## Steps 1 through 7 exit criteria

- the exact 1.4 commit and artifact are recorded;
- `v1.5-support` produces `1.5-dev` artifacts;
- all five building identities and their namespace boundaries are reserved;
- hard limits and standalone/integrated Watchtower ownership are explicit;
- the custom pool element serializes its stable entry ID;
- additive mutation preserves order and weight, is idempotent, and invalidates
  only the target pool's height cache;
- disabled and missing-dependency paths fail closed;
- definitions are strict, bounded, deterministic, atomic, and pack-namespace
  friendly;
- schema-12 migration preserves the master choice while initializing only the
  five schema-13 type suppressors;
- `/reload` cannot destructively mutate live village pools;
- anchor identity is deterministic, marker grammar is closed, and at most one
  matching anchor is accepted per template;
- one-shot claims persist strictly and unreadable/future state fails closed
  without data loss;
- marker publication performs no synchronous chunk load, scan, recurring task,
  or repair action;
- the probe performs no marker, guard, Armory, loot, or persistent-state work;
- the probe resource is absent while its legacy ID remains decodable;
- all 25 biome/building templates and definitions are generated from bounded,
  reviewable source;
- every template has exactly one matching road connector and anchor;
- all five building types pass real rotated placement without activating later
  gameplay actions;
- the three combat buildings select difficulty-aware groups through the
  existing strict datapack registry and never exceed their intrinsic or global
  guard ceilings;
- garrison guards retain deterministic post identity, role/loadout assignment,
  and squad membership through the existing event-driven pipeline;
- Peaceful, configured-zero, repeated, unsafe, unloaded, missing-group, and
  non-garrison paths fail closed without partial or retroactive staffing;
- each eligible Armory building places at most one public Armory and evaluates
  its bounded ammo-only datapack stock at most once;
- disabled, empty, repeated, removed, blocked, wrong-owner, and unloaded
  Armory paths fail closed without overwriting blocks or duplicating stock;
- focused JVM and Forge integration tests pass; and
- visual shells remain default-off until manual generation review passes.

## Verified Step 2 artifact

The final Steps 1–2 Java 17 gate passed 464 JVM/source-contract tests, compiled
both Forge test source sets, and passed the packaged-artifact inspection. The
live Forge server then passed all 97 required GameTests. The resulting
development JAR is `villagerguardgunners-1.5-dev.jar`, 954,554 bytes, with
SHA-256
`2c973bbc32ac7baf870fb355826e48e2a488b8cf4e6090e031d2f5cc7b02322d`.

## Verified Step 3 artifact

The Step 3 Java 17 gate passed 476 JVM/source-contract tests with no failures,
errors, or skips, compiled both Forge test source sets, and passed the packaged-
artifact inspection. The live Forge server loaded one built-in format-1
definition before startup and passed all 97 required GameTests. The resulting
development JAR is `villagerguardgunners-1.5-dev.jar`, 975,797 bytes, with
SHA-256
`4dbb920dcb030f8b650f38fdd58a2072e8d6ba0c5856364c56b66b8d3f253cbc`.

## Verified Step 4 artifact

The Step 4 clean Java 17 gate passed 487 JVM/source-contract tests with no
failures, errors, or skips, compiled both Forge test source sets, and passed the
packaged-artifact inspection. The exact cleaned fixture then passed all 97
required live Forge GameTests, including marker consumption, schema-1 SavedData
publication, and repeated-anchor idempotence. The resulting development JAR is
`villagerguardgunners-1.5-dev.jar`, 997,182 bytes, with SHA-256
`1724bfd38d8099cf36f1cc3afce86725c71a080f7c6461ad3d1072d851bc87c4`.

## Post-Step-4 audit hardening

The full review added session isolation, legacy codec compatibility, atomic
template preflight, provenance-checked persistence, bounded lifecycle
diagnostics, and real-template plus worker-thread integration coverage. The
clean Java 17 gate passed 488 JVM/source-contract tests with no failures or
errors, and the clean Forge server passed all 99 required GameTests. The
resulting development JAR is `villagerguardgunners-1.5-dev.jar`, 1,003,497
bytes, with SHA-256
`2833e99a8234e020c84496b94f96d5780686687c7a51f5e5a971c50ac7c4168d`.

## Verified Step 5 artifact

The Step 5 Java 17 gate passed 490 JVM/source-contract tests with no failures
or errors, compiled both Forge test source sets, and required all 25 generated
templates plus all 25 generated definitions in the release JAR. The Forge
server loaded the complete definition matrix and passed all 100 required
GameTests, including connector inspection across all five biome palettes and
real rotated placement of every building type. The resulting development JAR
is `villagerguardgunners-1.5-dev.jar`, 1,048,711 bytes, with SHA-256
`29f5af34e1539bbce5ddcfcade0ff593ec880ba98046ac399ec2b3e2c1f621a3`.

## Verified Step 6 artifact

The Step 6 Java 17 gate passed 497 JVM/source-contract tests with no failures,
errors, or skips, compiled both Forge test source sets, and required all three
village-garrison group resources plus their runtime classes in the release
JAR. The isolated Forge server loaded all four guard-post groups and passed all
101 required GameTests, including atomic Barracks staffing, duplicate
prevention, and completion of the existing TaCZ loadout, role, and squad
handoff while crossbow-only replacement remained enabled. The resulting
development JAR is `villagerguardgunners-1.5-dev.jar`, 1,066,498 bytes, with
SHA-256
`8650e519f1d573f2a82e0072ef29b6418560531eae187464d65ed6de5d55b9b0`.

## Verified Step 7 artifact

The Step 7 Java 17 gate passed 503 JVM/source-contract tests with no failures,
errors, or skips, compiled both Forge test source sets, and required the public
village-Armory runtime classes plus its bounded loot table in the release JAR.
The isolated Forge server passed all 102 required GameTests, including stocked,
empty, disabled, repeated, removed, public-lock, and ammo-only village Armory
paths. The resulting development JAR is
`villagerguardgunners-1.5-dev.jar`, 1,075,732 bytes, with SHA-256
`ab4dd30f530edd2a6f83c699d30b353bd91d71236a45eecd897346f57d84c1a7`.

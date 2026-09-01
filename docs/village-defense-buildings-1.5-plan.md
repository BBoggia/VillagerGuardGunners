# Village Defense Buildings 1.5 baseline and integration contract

Steps 1 and 2 establish the 1.5 village-integration line without shipping a
functional Barracks, Armory, Sentry Post, integrated Watchtower, or Training
Yard. Development artifacts use `1.5-dev`; they must not be presented as a
finished 1.5 release.

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

The Step 2 compatibility probe has the internal entry ID
`villagerguardgunners:integration_probe/plains`. It is not a sixth building
type and must be removed when the first reviewed VGG template becomes active.
It wraps the existing road-connected vanilla plains small-house template, has
no marker behavior, and is disabled by default.

Future definition documents may select only a declared target pool, template,
processor list, stable building type, positive bounded weight, and feature
flags already represented by the schema. They may not contain commands,
entity SNBT, item NBT, Java class names, code, arbitrary registry mutation, or
unbounded collections.

## Hard limits and ownership

- at most five target village pools per publication;
- at most 25 injected entries across those pools;
- entry weight from 1 through 5;
- one stable entry ID per injected element;
- one anchor marker per placed functional building piece;
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

Missing target pools and processor lists are skipped independently and
reported in one bounded startup diagnostic. A missing third-party or replaced
vanilla pool cannot crash the server or cause fallback mutation of a different
pool. Third-party entries already present remain untouched.

The schema-12 Fzzy Config master switch is `villageBuildings.enabled`. It is
`false` during the compatibility-probe stage. With the switch off, registry
contents are not inspected or mutated. Once functional templates have passed
the release matrix, this development default may be reviewed before release.

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

## Step 1 and Step 2 exit criteria

- the exact 1.4 commit and artifact are recorded;
- `v1.5-support` produces `1.5-dev` artifacts;
- all five building identities and their namespace boundaries are reserved;
- hard limits and standalone/integrated Watchtower ownership are explicit;
- the custom pool element serializes its stable entry ID;
- additive mutation preserves order and weight, is idempotent, and invalidates
  only the target pool's height cache;
- disabled and missing-dependency paths fail closed;
- the probe performs no marker, guard, Armory, loot, or persistent-state work;
- focused JVM and Forge integration tests pass; and
- no functional village-defense building is claimed until later steps.

## Verified Step 2 artifact

The final Steps 1–2 Java 17 gate passed 464 JVM/source-contract tests, compiled
both Forge test source sets, and passed the packaged-artifact inspection. The
live Forge server then passed all 97 required GameTests. The resulting
development JAR is `villagerguardgunners-1.5-dev.jar`, 954,554 bytes, with
SHA-256
`2c973bbc32ac7baf870fb355826e48e2a488b8cf4e6090e031d2f5cc7b02322d`.

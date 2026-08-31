# Village Watchtowers Step 9: 1.4 baseline and contracts

Step 9 establishes the post-1.3 world-generation line without registering a
structure, changing biome generation, placing a block, spawning an entity, or
modifying an existing world. Development artifacts use `1.4-dev` so they
cannot be mistaken for the 1.3 beta or a final release.

## Baseline identity

| Identity | Value |
| --- | --- |
| Baseline branch | `v1.3-support` |
| Baseline commit | `346d953` |
| Baseline artifact | `villagerguardgunners-1.3-beta1.jar` |
| Baseline artifact SHA-256 | `d498dabe6e87de95034a70befc92997a6a9d8b931211362471606242cf480417` |
| Plain JUnit/source-contract tests | 386 passed |
| Forge correctness GameTests | 87 passed |
| Performance/JFR workloads | 20 passed against the pre-beta production surface |
| Minecraft | `1.20.1` |
| Forge | `47.4.23` |
| Guard Villagers | `1.20.1-1.6.19` |
| TaCZ | `1.1.8-hotfix` |
| Fzzy Config | `0.7.6+1.20.1` |
| Kotlin for Forge | `4.12.0` |

The 1.3 beta was intentionally not tagged or published while this local branch
was created. The commit and checksum above identify the exact inherited
baseline; they do not claim that its unfinished manual release matrix passed.

## Feature boundary

The first 1.4 implementation targets a rare standalone Village Watchtower in
ordinary village-compatible Overworld biomes. A watchtower is a world-generated
defensive post, not a replacement village and not a runtime village upgrade.
It may contain a Village Armory and a small, bounded group of Guard Villagers.

The initial implementation will use Minecraft's normal data-driven structure
registries, biome tags, structure set, placement rules, template data, and loot
tables wherever those systems can express the behavior. A narrowly scoped
custom structure and template piece may handle explicit structure data markers
for Guard Villager creation, because embedded entity NBT does not provide the
same safe `MobSpawnType.STRUCTURE` initialization boundary.

The default structure is independent from vanilla village jigsaw pools. Step 9
forbids runtime mutation of vanilla template pools and any requirement to find
a nearby village while generating a chunk. This avoids order-sensitive
conflicts with village overhauls and avoids recursive or unbounded structure
lookups. Datapacks can disable the structure set, replace its template and
placement data, or change its allowed biome tag.

Only newly generated chunks are eligible. Version 1.4 will not search existing
chunks, retrofit existing villages, place a tower in response to a ticking
guard, or promise that every village has a tower.

## World-generation ownership

Each generated tower must have one deterministic placement identity derived
from its dimension, structure start, and world seed. Structure placement owns
only blocks, bounded template markers, initial loot containers, and the guards
created at those markers. It must not create a recurring controller, chunk
ticket, global entity query, or block-entity ticker.

Template markers are consumed during structure placement. A marker may request
one guard post or one Armory setup, but it may not contain arbitrary commands,
entity SNBT, item stacks, or Java class names. Unknown markers fail closed and
are removed without performing an action. The implementation must tolerate a
piece being clipped or revisited by chunk-by-chunk structure placement without
duplicating guards, Armories, loot, or post identity.

The initial hard limits are:

- at most four generated guards for one watchtower;
- at most eight recognized data markers for one structure start;
- one generated Village Armory for one watchtower;
- one bounded loadout/role decision for each successfully created guard; and
- zero post-generation scans, ticks, tasks, or forced chunks.

These are safety ceilings, not a promise that every tower uses the maximum.
The default composition should remain smaller than the hard cap.

## Guard-group contract

Natural group composition controls who occupies a post; it does not directly
construct a TaCZ gun or ammunition stack. Each member definition selects a
Guard Villagers guard role and an existing natural-loadout pool or inherits the
server's difficulty pool. The established natural-loadout transaction remains
the only authority that may issue a primary, optional sidearm, and ammunition.

Every generated guard must be finalized with the structure spawn source before
loadout selection. Difficulty, pool compatibility, explosive permission,
inventory capacity, nearby natural-gunner caps, provenance, and role existence
remain fail-closed. If equipment issuance fails, the valid Guard Villager may
remain with Guard Villagers' normal equipment; no partial items or false
provenance may survive.

A post identity may later support a shared squad and Armory lock, but that
association must be created once from the structure identity and persisted
through the existing versioned squad and Armory formats. It must not infer a
squad by periodically searching nearby guards or blocks.

## Preserved compatibility inputs

Version 1.4 treats every shipped 1.3 format as an immutable migration input:

- Fzzy Config schema 9;
- natural-loadout formats 1 through 4 and provenance schema 1;
- guard-role format 1;
- sidearm-storage schema 3;
- Orders Ledger version 2;
- squad membership, ledger binding, metadata, and command schema 1;
- Village Armory squad-lock schema 1; and
- Armory resupply request/reservation contracts, which remain transient.

World-generation data uses its own resource paths and versions. It may refer to
roles and loadout pools by canonical resource ID, but it may not reinterpret or
silently migrate those documents.

## Safety and performance invariants

All later watchtower work must preserve these boundaries:

- no retro-generation or loaded-world repair scan;
- no synchronous chunk acquisition, chunk ticket, or cross-chunk entity scan;
- no vanilla village-pool mutation in the initial implementation;
- deterministic random choices from the world-generation random source;
- strict document, member, marker, and collection bounds before publication;
- atomic reload publication with last-known-good behavior for malformed custom
  group data;
- no equipment creation outside natural-loadout issuance or a declared loot
  table evaluated during structure placement;
- no generated guard may bypass ally protection, rules of engagement,
  explosive safety, per-guard orders, role precedence, or native TaCZ state;
- no generated Armory ticker and no automatic chunk loading for resupply; and
- no client-only class reference from structure registration, parsing, or
  placement code.

The completed feature must add retained evidence for 100 generated starts with
bounded marker work, duplicate-placement resistance, unloaded-chunk behavior,
datapack replacement, and a normal server tick after generation with zero
watchtower-owned recurring work.

## Reserved implementation sequence

Step 9 records this baseline and its validation matrix. Later 1.4 steps are
reserved for:

1. registry and datapack structure skeletons;
2. the first tower template, terrain checks, rotation, and placement;
3. strict datapack-defined guard-post group composition;
4. deterministic marker-driven guard spawning and persistent post identity;
5. natural-loadout, role, squad, Armory, and loot integration;
6. configuration, operator diagnostics, and modpack overrides;
7. lifecycle, upgrade, and compatibility hardening; and
8. correctness, dedicated-server, worldgen-performance, and release evidence.

Each boundary must remain usable with the feature disabled and must keep old
worlds loadable when the watchtower datapack resources are removed.

## Step 9 validation

The Java 17 `clean check` gate passes with 389 plain JUnit/source-contract
tests, both development GameTest source sets compile, the production JAR passes
the packaged-artifact gate, and the embedded mod version is `1.4-dev`. The
first Step 9 development JAR is
`villagerguardgunners-1.4-dev.jar` with SHA-256
`3f5777605a7b788885c5512f1e92f6af9944eb97d3a2c787b4639cfc3c0e657d`.

Step 9 adds no runtime behavior, so it does not rerun the 87-case Forge server
or 20-workload performance matrix against this metadata-only artifact. Those
runtime suites remain mandatory after the first production world-generation
surface is introduced.

## Step 9 exit criteria

Step 9 is complete when:

- `v1.4-support` contains the exact reviewed 1.3 beta baseline;
- development artifacts identify themselves as `1.4-dev`;
- the inherited commit, artifact checksum, dependency set, and test counts are
  recorded;
- placement, group, loadout, lifecycle, compatibility, and performance cases
  exist before production registration;
- the initial standalone-structure and no-village-pool-mutation decisions are
  explicit;
- hard marker and guard ceilings are recorded;
- no production structure, biome modifier, structure set, template, loot
  table, or world-generation registration has been added; and
- the Java 17 clean build and artifact gate pass.

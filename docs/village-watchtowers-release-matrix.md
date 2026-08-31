# Village Watchtowers 1.4 validation matrix

This matrix is created during Step 9 before production world-generation code
or data is registered. `Not implemented` is therefore the expected initial
state. A row becomes `Pass` only when its retained automated or manual evidence
uses the exact candidate JAR and world seed named by that evidence.

## Candidate identity

- Development version: `1.4-dev`
- Baseline branch: `v1.3-support`
- Baseline commit: `346d953`
- Baseline JAR SHA-256: `d498dabe6e87de95034a70befc92997a6a9d8b931211362471606242cf480417`
- Candidate commit: record before validation
- Step 9 development JAR SHA-256: `3f5777605a7b788885c5512f1e92f6af9944eb97d3a2c787b4639cfc3c0e657d`
- Test seeds: record fixed positive, negative, and random seeds
- Tested worldgen/village mods: record exact names and versions
- Tested TaCZ content packs: record exact names and versions

## Registration and data

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-DATA-01 | Start with the built-in structure, structure set, biome tag, template, processors, and loot data enabled. | Every referenced registry key resolves during server datapack load and `/locate structure` can resolve the watchtower key. | Not implemented | |
| TOWER-DATA-02 | Override the built-in biome tag, structure settings, placement, template, and loot table through a datapack. | Standard datapack priority selects the replacement without Java-side hardcoded copies or duplicate registration. | Not implemented | |
| TOWER-DATA-03 | Remove or disable the structure set after towers already exist. | Existing chunks and saved guards/Armories load normally; no new tower generates and no repair scan starts. | Not implemented | |
| TOWER-DATA-04 | Load missing, malformed, future-format, oversized, cyclic, or conflicting guard-post group documents. | Invalid publication fails closed within hard bounds and retains the last known good generation; unrelated datapack data continues loading. | Not implemented | |

## Placement and terrain

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-PLACE-01 | Generate each permitted Overworld biome and representative flat, sloped, snowy, sandy, forested, river-edge, and coastal terrain. | The tower uses valid terrain, rotation, foundation, and bounding boxes without floating, burying, clipping critically, or generating in an excluded biome or dimension. | Not implemented | |
| TOWER-PLACE-02 | Generate many candidate regions with fixed positive and negative seeds. | Random-spread spacing and separation are deterministic, respect their hard relation, and do not require a nearby village lookup. | Not implemented | |
| TOWER-PLACE-03 | Generate near vanilla and modded villages, outposts, strongholds, roads, and another watchtower. | Minecraft's normal structure exclusion/placement rules prevent invalid overlap; the add-on does not mutate village template pools. | Not implemented | |
| TOWER-PLACE-04 | Reopen a world across restart, chunk unload, and generation-version changes. | A completed start is not placed again and existing blocks are never retroactively rewritten. | Not implemented | |

## Template markers and identity

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-MARKER-01 | Place every supported guard, Armory, and loot marker under every template rotation. | Each marker resolves to the correct world position exactly once and the marker block itself does not remain. | Not implemented | |
| TOWER-MARKER-02 | Add unknown, malformed, duplicate, clipped, and more-than-eight markers. | Unknown or invalid work fails closed; recognized work stays under the eight-marker hard ceiling with no arbitrary command or NBT execution. | Not implemented | |
| TOWER-MARKER-03 | Cause a structure piece to post-process across several chunks and revisit its bounding boxes. | Stable structure/post identity prevents duplicate guards, Armories, loot, and squad metadata. | Not implemented | |
| TOWER-MARKER-04 | Interrupt generation, then save and restart at each observable boundary. | The world contains either a valid completed action or a safe retryable/absent action, never duplicated items or entities. | Not implemented | |

## Guard group composition

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-GROUP-01 | Select every built-in group across Peaceful, Easy, Normal, and Hard. | Composition is deterministic for a seed, contains no more than four guards, respects difficulty eligibility, and performs one bounded selection per marker. | Not implemented | |
| TOWER-GROUP-02 | Use custom groups with weighted members, explicit roles, inherited loadout pools, and optional members. | Strict bounded parsing produces the documented distribution without direct item grants. | Not implemented | |
| TOWER-GROUP-03 | Reference a missing role, missing loadout pool, incompatible gun, explosive-disabled entry, or full guard inventory. | Guard creation or equipment issuance follows the documented fail-closed boundary with no partial equipment or false provenance. | Not implemented | |
| TOWER-GROUP-04 | Generate a tower while the configured nearby natural-gunner cap is already full. | The existing cap remains authoritative; valid guards may retain normal Guard Villagers equipment and the cap never acquires a false record. | Not implemented | |
| TOWER-GROUP-05 | Generate on Peaceful and then change difficulty after the chunks exist. | The documented Peaceful population policy is applied once; difficulty changes never respawn, delete, or reroll a completed post. | Not implemented | |

## Roles, squads, Armories, and loot

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-INTEGRATE-01 | Spawn rifleman, marksman, support, breacher, and demolitions members with explicit per-guard order overrides. | Global safety wins, explicit orders override role defaults, and assignment creates or moves no item. | Not implemented | |
| TOWER-INTEGRATE-02 | Create a shared post squad and locked Armory, then copy ledgers and issue every temporary squad command. | One deterministic squad identity persists; ordinary authorization and command caps apply; no proximity scan reconstructs membership. | Not implemented | |
| TOWER-INTEGRATE-03 | Generate public, squad-locked, empty, and loot-stocked Armories; then use players, hoppers, pipes, and automatic guard resupply. | Loot is evaluated once; Armory access and transactional ammunition rules remain exact with no duplicate inventory. | Not implemented | |
| TOWER-INTEGRATE-04 | Kill, convert, reassign, unload, or remove generated guards and break or replace the generated Armory. | Existing Guard Villagers, sidearm, squad, provenance, drop, reservation, and Armory lifecycle rules remain authoritative. | Not implemented | |

## Configuration and diagnostics

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-CFG-01 | Upgrade a complete schema-9 configuration and change every new watchtower value. | Existing values remain exact; new bounded defaults initialize once; invalid ranges are corrected or fail closed before worldgen use. | Not implemented | |
| TOWER-CFG-02 | Disable watchtowers globally and through datapack structure-set removal in new and existing worlds. | No new starts generate; existing chunks remain untouched; guards, Armories, and unrelated mod systems continue normally. | Not implemented | |
| TOWER-DIAG-01 | Inspect registration, resolved settings, group publication, and a generated post through permission-gated commands. | Diagnostics are snapshot-only, paginated, and bounded; they do not locate structures, load chunks, generate starts, or mutate a post. | Not implemented | |

## Compatibility and performance

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-COMPAT-01 | Generate with representative village, terrain, biome, structure, and optimization mods. | Datapack/registry composition remains deterministic, failures are attributable, and no order-sensitive vanilla village-pool mutation occurs. | Not implemented | |
| TOWER-COMPAT-02 | Run a dedicated server with clients that join before, during, and after watchtower chunk generation. | Server owns placement and spawning; clients agree on blocks, guards, roles, equipment, Armory state, and squad identity without client-only loading errors. | Not implemented | |
| TOWER-PERF-01 | Generate 100 accepted and rejected candidate starts across retained seeds. | Candidate work and marker handling stay within declared bounds; no world/entity scan, recursive structure lookup, or forced chunk occurs. | Not implemented | |
| TOWER-PERF-02 | Keep 100 generated towers loaded with no combat or resupply request. | Watchtower generation contributes zero recurring tasks, entity scans, block scans, paths, chunk tickets, and block-entity ticks after placement. | Not implemented | |
| TOWER-PERF-03 | Simultaneously activate the maximum default occupants at many loaded towers. | Runtime work remains attributable to existing bounded combat, squad, ammunition, and Armory systems rather than a new tower manager or cohort scan. | Not implemented | |

## Step 9 status

- [x] Exact 1.3 beta baseline identity recorded.
- [x] `1.4-dev` development identity reserved.
- [x] Standalone datapack-controlled structure boundary selected.
- [x] Vanilla village template-pool mutation excluded from the initial design.
- [x] Guard, marker, Armory, and recurring-work hard ceilings recorded.
- [x] Placement, group, integration, lifecycle, compatibility, and performance
  cases defined before implementation.
- [ ] Production structure registry and datapack skeleton implemented.
- [ ] Tower template and placement implemented.
- [ ] Guard-post composition and spawning implemented.
- [ ] Integration and lifecycle matrix completed.
- [ ] Exact candidate identity and all release evidence recorded.

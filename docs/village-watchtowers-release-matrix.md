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
- Step 10 development JAR SHA-256: `f4d01c87387d637262537051b8155e1e289859c55c32bed94efe700b3b84268b`
- Step 11 development JAR SHA-256: `53dc78cf6107dc7f15d8019218d560e813153e15d4baad94408505438bd9b953`
- Step 12 development JAR SHA-256: `6946f3e5fa735fab740e1ab33bd82ca01cf6e277ae979032722112b3267fc4ba`
- Step 13 development JAR SHA-256: `51ac3d9eb598b91e303344c1cdee99cd6b94bb49d029d2960175db1790aa4969`
- Test seeds: record fixed positive, negative, and random seeds
- Tested worldgen/village mods: record exact names and versions
- Tested TaCZ content packs: record exact names and versions

## Registration and data

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-DATA-01 | Start with the built-in structure, structure set, biome tag, template, processors, and loot data enabled. | Every referenced registry key resolves during server datapack load and `/locate structure` can resolve the watchtower key. | Partial | Step 11 packages and loads the generated template, registers its persisted piece type, and activates the five village-compatible biome tags. The 400-test static/artifact gate and 88-case Forge server validate dynamic structure lookup and NBT loading; loot data and retained `/locate` evidence remain. |
| TOWER-DATA-02 | Override the built-in biome tag, structure settings, placement, template, and loot table through a datapack. | Standard datapack priority selects the replacement without Java-side hardcoded copies or duplicate registration. | Not implemented | |
| TOWER-DATA-03 | Remove or disable the structure set after towers already exist. | Existing chunks and saved guards/Armories load normally; no new tower generates and no repair scan starts. | Not implemented | |
| TOWER-DATA-04 | Load missing, malformed, future-format, oversized, cyclic, or conflicting guard-post group documents. | Invalid publication fails closed within hard bounds and retains the last known good generation; unrelated datapack data continues loading. | Partial | Step 12 adds strict bounded parsing, cycle/conflict detection, atomic publication, and last-known-good unit evidence. Retained live `/reload` replacement evidence remains for Step 17. |

## Placement and terrain

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-PLACE-01 | Generate each permitted Overworld biome and representative flat, sloped, snowy, sandy, forested, river-edge, and coastal terrain. | The tower uses valid terrain, rotation, foundation, and bounding boxes without floating, burying, clipping critically, or generating in an excluded biome or dimension. | Partial | Unit tests cover dry, wet/frozen-support, slope, sample-count, and build-height decisions; the Forge test loads all four 9 x 14 x 9 rotation bounds. Retained visual generation across the full biome and terrain matrix remains. |
| TOWER-PLACE-02 | Generate many candidate regions with fixed positive and negative seeds. | Random-spread spacing and separation are deterministic, respect their hard relation, and do not require a nearby village lookup. | Partial | The exact spacing 40, separation 12, salt 17290527, worldgen-random rotation, and absence of village lookup are source/artifact locked. Multi-seed generation evidence remains. |
| TOWER-PLACE-03 | Generate near vanilla and modded villages, outposts, strongholds, roads, and another watchtower. | Minecraft's normal structure exclusion/placement rules prevent invalid overlap; the add-on does not mutate village template pools. | Not implemented | |
| TOWER-PLACE-04 | Reopen a world across restart, chunk unload, and generation-version changes. | A completed start is not placed again and existing blocks are never retroactively rewritten. | Partial | The registered piece persists its template position and rotation through Minecraft's normal structure-start format, and no retro-generation or repair path exists. Restart and version-transition evidence remains. |

## Template markers and identity

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-MARKER-01 | Place every supported guard, Armory, and loot marker under every template rotation. | Each marker resolves to the correct world position exactly once and the marker block itself does not remain. | Partial | Step 13 packages one central guard marker, validates its NBT under all four rotations, consumes it during real template placement, and confirms the block is air afterward. Armory and loot markers remain Step 14 work. |
| TOWER-MARKER-02 | Add unknown, malformed, duplicate, clipped, and more-than-eight markers. | Unknown or invalid work fails closed; recognized work stays under the eight-marker hard ceiling with no arbitrary command or NBT execution. | Partial | Step 13 unit/source contracts cover closed 128-character metadata parsing, canonical IDs, malformed/unknown actions, repeated positions, duplicate actions, clipped spawn cells, and the eight-marker ceiling. Retained custom-template server evidence remains. |
| TOWER-MARKER-03 | Cause a structure piece to post-process across several chunks and revisit its bounding boxes. | Stable structure/post identity prevents duplicate guards, Armories, loot, and squad metadata. | Partial | Step 13's Forge test revisits the placed piece, persists and reloads its strict marker ledger, revisits again, and observes the same bounded guard set. Armory, loot, and squad integration remain Step 14 work. |
| TOWER-MARKER-04 | Interrupt generation, then save and restart at each observable boundary. | The world contains either a valid completed action or a safe retryable/absent action, never duplicated items or entities. | Partial | Step 13 claims before selection/spawn, disables missing/future/malformed saved state, and retains safe absent/completed outcomes. Retained process-interruption evidence remains. |

## Guard group composition

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-GROUP-01 | Select every built-in group across Peaceful, Easy, Normal, and Hard. | Composition is deterministic for a seed, contains no more than four guards, respects difficulty eligibility, and performs one bounded selection per marker. | Partial | Steps 12-13 validate deterministic built-in selection across every difficulty, execute one bounded selection from the real template marker, and create no more than four guards. Retained multi-seed distribution evidence remains. |
| TOWER-GROUP-02 | Use custom groups with weighted members, explicit roles, inherited loadout pools, and optional members. | Strict bounded parsing produces the documented distribution without direct item grants. | Partial | Step 12 covers weighted variants, optional member percentages, explicit role IDs, member/variant/document loadout inheritance, and an identifier-only source contract. Retained modpack `/reload` distribution evidence remains. |
| TOWER-GROUP-03 | Reference a missing role, missing loadout pool, incompatible gun, explosive-disabled entry, or full guard inventory. | Guard creation or equipment issuance follows the documented fail-closed boundary with no partial equipment or false provenance. | Not implemented | |
| TOWER-GROUP-04 | Generate a tower while the configured nearby natural-gunner cap is already full. | The existing cap remains authoritative; valid guards may retain normal Guard Villagers equipment and the cap never acquires a false record. | Not implemented | |
| TOWER-GROUP-05 | Generate on Peaceful and then change difficulty after the chunks exist. | The documented Peaceful population policy is applied once; difficulty changes never respawn, delete, or reroll a completed post. | Partial | The Step 13 Forge test generates a zero-occupant tower on Peaceful, changes the server to Hard, revisits the piece, and confirms the claimed post remains empty. Retained world restart evidence remains. |

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
| TOWER-PERF-01 | Generate 100 accepted and rejected candidate starts across retained seeds. | Candidate work and marker handling stay within declared bounds; no world/entity scan, recursive structure lookup, or forced chunk occurs. | Partial | Step 11 caps a candidate at 18 height samples plus nine base-column checks only after preliminary acceptance. Step 13 adds at most eight recognized marker claims, one 64-variant/four-member selection, and four constant-cell spawn checks with no chunk or entity scan. The retained 100-start timing run remains. |
| TOWER-PERF-02 | Keep 100 generated towers loaded with no combat or resupply request. | Watchtower generation contributes zero recurring tasks, entity scans, block scans, paths, chunk tickets, and block-entity ticks after placement. | Not implemented | |
| TOWER-PERF-03 | Simultaneously activate the maximum default occupants at many loaded towers. | Runtime work remains attributable to existing bounded combat, squad, ammunition, and Armory systems rather than a new tower manager or cohort scan. | Not implemented | |

## Implementation status

- [x] Exact 1.3 beta baseline identity recorded.
- [x] `1.4-dev` development identity reserved.
- [x] Standalone datapack-controlled structure boundary selected.
- [x] Vanilla village template-pool mutation excluded from the initial design.
- [x] Guard, marker, Armory, and recurring-work hard ceilings recorded.
- [x] Placement, group, integration, lifecycle, compatibility, and performance
  cases defined before implementation.
- [x] Production structure registry and dormant datapack skeleton implemented.
- [x] Tower template and bounded placement implemented.
- [x] Guard-post composition and spawning implemented.
- [ ] Integration and lifecycle matrix completed.
- [ ] Exact candidate identity and all release evidence recorded.

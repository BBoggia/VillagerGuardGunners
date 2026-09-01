# Village Watchtowers 1.4 validation matrix

This matrix is created during Step 9 before production world-generation code
or data is registered. `Not implemented` is therefore the expected initial
state. A row becomes `Pass` only when its retained automated or manual evidence
uses the exact candidate JAR and world seed named by that evidence.

## Candidate identity

- Release-candidate version: `1.4`
- Baseline branch: `v1.3-support`
- Baseline commit: `346d953`
- Baseline JAR SHA-256: `d498dabe6e87de95034a70befc92997a6a9d8b931211362471606242cf480417`
- Candidate commit: record before validation
- Step 9 development JAR SHA-256: `3f5777605a7b788885c5512f1e92f6af9944eb97d3a2c787b4639cfc3c0e657d`
- Step 10 development JAR SHA-256: `f4d01c87387d637262537051b8155e1e289859c55c32bed94efe700b3b84268b`
- Step 11 development JAR SHA-256: `53dc78cf6107dc7f15d8019218d560e813153e15d4baad94408505438bd9b953`
- Step 12 development JAR SHA-256: `6946f3e5fa735fab740e1ab33bd82ca01cf6e277ae979032722112b3267fc4ba`
- Step 13 development JAR SHA-256: `51ac3d9eb598b91e303344c1cdee99cd6b94bb49d029d2960175db1790aa4969`
- Step 14 development JAR SHA-256: `be24bab3cbd36137e64747b1a06afcb835229f53f82fe054fe6eef9b8bb9cc2b`
- Step 15 development JAR SHA-256: `e72fce155e719789dccaf42a28df89bec87aa4c0488871177ef8be21511c6585`
- Step 16 development JAR SHA-256: `95d79c53f5f0f111689a4791f2002e2e15a8ec4d09a59cdff96fab0231ae0de6`
- Final 1.4 candidate JAR SHA-256: `2ebf9920e7916bc9d7e81e85a55c522020e7149eaabf74d2354bfe6c838e59ac`
- Test seeds: record fixed positive, negative, and random seeds
- Tested worldgen/village mods: record exact names and versions
- Tested TaCZ content packs: record exact names and versions

## Registration and data

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-DATA-01 | Start with the built-in structure, structure set, biome tag, template, processors, and loot data enabled. | Every referenced registry key resolves during server datapack load and `/locate structure` can resolve the watchtower key. | Partial | Steps 11-14 package the generated template, registered piece, five village-compatible biome tags, guard group, and declared Armory chest loot. The static/artifact and Forge gates validate dynamic structure/template/loot loading; retained `/locate` evidence remains. |
| TOWER-DATA-02 | Override the built-in biome tag, structure settings, placement, template, and loot table through a datapack. | Standard datapack priority selects the replacement without Java-side hardcoded copies or duplicate registration. | Partial | The structure, set, biome tag, template, guard group, and Armory loot are standard datapack resources. Step 14's Armory marker resolves the declared loot-table ID at placement rather than embedding items. Retained end-to-end replacement-pack evidence remains. |
| TOWER-DATA-03 | Remove or disable the structure set after towers already exist. | Existing chunks and saved guards/Armories load normally; no new tower generates and no repair scan starts. | Partial | Existing object codecs deserialize independently of the structure set/template/group registry, unreadable future records are preserved inactive, the global/biome/structure-set disable paths schedule no new starts, and no repair system exists. Retained existing-world pack-removal/restart evidence remains for Step 17. |
| TOWER-DATA-04 | Load missing, malformed, future-format, oversized, cyclic, or conflicting guard-post group documents. | Invalid publication fails closed within hard bounds and retains the last known good generation; unrelated datapack data continues loading. | Partial | Strict bounded parsing, cycle/conflict detection, and atomic last-known-good publication are supplemented by paginated bounded `/vgg watchtowers errors` attribution. Retained live `/reload` replacement evidence remains for Step 17. |

## Placement and terrain

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-PLACE-01 | Generate each permitted Overworld biome and representative flat, sloped, snowy, sandy, forested, river-edge, and coastal terrain. | The tower uses valid terrain, rotation, foundation, and bounding boxes without floating, burying, clipping critically, or generating in an excluded biome or dimension. | Partial | Unit tests cover dry, wet/frozen-support, slope, sample-count, and build-height decisions; the Forge test loads all four 9 x 14 x 9 rotation bounds. Retained visual generation across the full biome and terrain matrix remains. |
| TOWER-PLACE-02 | Generate many candidate regions with fixed positive and negative seeds. | Random-spread spacing and separation are deterministic, respect their hard relation, and do not require a nearby village lookup. | Partial | The exact spacing 40, separation 12, salt 17290527, worldgen-random rotation, and absence of village lookup are source/artifact locked. Multi-seed generation evidence remains. |
| TOWER-PLACE-03 | Generate near vanilla and modded villages, outposts, strongholds, roads, and another watchtower. | Minecraft's normal structure exclusion/placement rules prevent invalid overlap; the add-on does not mutate village template pools. | Partial | Source contracts exclude vanilla/third-party jigsaw pool mutation and nearby structure queries; the namespaced structure set remains under Minecraft's normal ordering. Retained visual overlap evidence with representative structure packs remains for Step 17. |
| TOWER-PLACE-04 | Reopen a world across restart, chunk unload, and generation-version changes. | A completed start is not placed again and existing blocks are never retroactively rewritten. | Partial | The registered piece persists its template position and rotation through Minecraft's normal structure-start format, and no retro-generation or repair path exists. Restart and version-transition evidence remains. |

## Template markers and identity

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-MARKER-01 | Place every supported guard, Armory, and loot marker under every template rotation. | Each marker resolves to the correct world position exactly once and the marker block itself does not remain. | Partial | Step 14 validates the central guard and offset Armory marker under all four rotations, consumes both during real placement, and covers public/locked plus empty/declared-loot grammar. Retained custom-template placement for every grammar form remains. |
| TOWER-MARKER-02 | Add unknown, malformed, duplicate, clipped, and more-than-eight markers. | Unknown or invalid work fails closed; recognized work stays under the eight-marker hard ceiling with no arbitrary command or NBT execution. | Partial | Step 13 unit/source contracts cover closed 128-character metadata parsing, canonical IDs, malformed/unknown actions, repeated positions, duplicate actions, clipped spawn cells, and the eight-marker ceiling. Retained custom-template server evidence remains. |
| TOWER-MARKER-03 | Cause a structure piece to post-process across several chunks and revisit its bounding boxes. | Stable structure/post identity prevents duplicate guards, Armories, loot, and squad metadata. | Partial | Step 14's Forge test revisits live and persisted marker state and compares guard count plus exact Armory inventory; the Armory's strict post identity independently makes interrupted same-post setup idempotent. Retained multi-chunk clipping evidence remains. |
| TOWER-MARKER-04 | Interrupt generation, then save and restart at each observable boundary. | The world contains either a valid completed action or a safe retryable/absent action, never duplicated items or entities. | Partial | Actions are claimed before work, schema-1 piece state disables rather than retro-generates under schema 2, member assignments survive protochunk persistence, and generated Armory identity prevents loot replay. Retained process-kill evidence remains. |

## Guard group composition

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-GROUP-01 | Select every built-in group across Peaceful, Easy, Normal, and Hard. | Composition is deterministic for a seed, contains no more than four guards, respects difficulty eligibility, and performs one bounded selection per marker. | Partial | Steps 12-13 validate deterministic built-in selection across every difficulty, execute one bounded selection from the real template marker, and create no more than four guards. Retained multi-seed distribution evidence remains. |
| TOWER-GROUP-02 | Use custom groups with weighted members, explicit roles, inherited loadout pools, and optional members. | Strict bounded parsing produces the documented distribution without direct item grants. | Partial | Step 12 covers weighted variants, optional member percentages, explicit role IDs, member/variant/document loadout inheritance, and an identifier-only source contract. Retained modpack `/reload` distribution evidence remains. |
| TOWER-GROUP-03 | Reference a missing role, missing loadout pool, incompatible gun, explosive-disabled entry, or full guard inventory. | Guard creation or equipment issuance follows the documented fail-closed boundary with no partial equipment or false provenance. | Partial | Step 14 validates explicit roles before selection and routes member pools through the existing selector and atomic issuer, whose retained unit/Forge cases cover missing definitions, incompatibility, explosives, capacity failure, rollback, and false-provenance prevention. Custom group live evidence remains. |
| TOWER-GROUP-04 | Generate a tower while the configured nearby natural-gunner cap is already full. | The existing cap remains authoritative; valid guards may retain normal Guard Villagers equipment and the cap never acquires a false record. | Partial | Post members call the existing loaded/unloaded nearby population check immediately before deterministic pool selection; only successful issuance synchronizes a reservation. A tower-specific prefilled-cap Forge case remains. |
| TOWER-GROUP-05 | Generate on Peaceful and then change difficulty after the chunks exist. | The documented Peaceful population policy is applied once; difficulty changes never respawn, delete, or reroll a completed post. | Partial | The Step 13 Forge test generates a zero-occupant tower on Peaceful, changes the server to Hard, revisits the piece, and confirms the claimed post remains empty. Retained world restart evidence remains. |

## Roles, squads, Armories, and loot

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-INTEGRATE-01 | Spawn rifleman, marksman, support, breacher, and demolitions members with explicit per-guard order overrides. | Global safety wins, explicit orders override role defaults, and assignment creates or moves no item. | Partial | Step 14 assigns each selected explicit role independently of item creation and its Forge case validates the built-in Normal roles. Existing role/order/safety tests retain precedence coverage; every built-in role in generated posts remains for retained evidence. |
| TOWER-INTEGRATE-02 | Create a shared post squad and locked Armory, then copy ledgers and issue every temporary squad command. | One deterministic squad identity persists; ordinary authorization and command caps apply; no proximity scan reconstructs membership. | Partial | A domain-separated post squad is registered once on the server thread, persisted only after successful registration, and cached in the Armory lock. Automatic posts preserve a 256-entry player registry reserve. The Forge case proves shared identity; existing ledger/command tests cover ordinary authorization and caps. A generated-post all-command pass remains. |
| TOWER-INTEGRATE-03 | Generate public, squad-locked, empty, and loot-stocked Armories; then use players, hoppers, pipes, and automatic guard resupply. | Loot is evaluated once; Armory access and transactional ammunition rules remain exact with no duplicate inventory. | Partial | Closed markers cover all four modes. The built-in locked/stocked form persists pending work during placement, evaluates a deterministic declared table through a bounded raw collector on the live server thread, validates TaCZ ammo definitions, and commits through an ammo-only nine-slot plan. Forge revisit evidence preserves exact inventory. Retained generated public/empty automation and resupply passes remain. |
| TOWER-INTEGRATE-04 | Kill, convert, reassign, unload, or remove generated guards and break or replace the generated Armory. | Existing Guard Villagers, sidearm, squad, provenance, drop, reservation, and Armory lifecycle rules remain authoritative. | Pass | The integrated Forge case removes one generated guard, reassigns another, replaces the generated Armory, and proves a piece revisit neither repairs nor resets them. Existing conversion/death-drop and Armory unload/index/reservation suites cover their normal lifecycle paths; no tower manager or reconstruction path exists. |

## Configuration and diagnostics

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-CFG-01 | Upgrade complete schema-9 and schema-10 configurations and change every new watchtower value. | Existing values remain exact; new bounded defaults initialize once; invalid ranges are corrected or fail closed before use. | Pass | Schema-10 unit tests preserve representative schema-9 values and reset the five generation values once. Schema-11 tests initialize only the 0–3 tower gun-chance multiplier, preserve all older settings, clamp the final probability, and retain immutable generation-policy bounds. The live Forge case exercises the one-guard ceiling and unstocked completed Armory. |
| TOWER-CFG-02 | Disable watchtowers globally and through datapack structure-set removal in new and existing worlds. | No new starts generate; existing chunks remain untouched; guards, Armories, and unrelated mod systems continue normally. | Partial | The global gate returns before terrain sampling; guard and Armory markers consume safely while disabled and do not retrofit when re-enabled. Datapack structure-set/filter and empty-biome-tag precedence is documented. Retained new/existing-world datapack removal evidence remains for Steps 16–17. |
| TOWER-DIAG-01 | Inspect registration, resolved settings, group publication, and a generated post through permission-gated commands. | Diagnostics are snapshot-only, paginated, and bounded; they do not locate structures, load chunks, generate starts, or mutate a post. | Pass | `/vgg watchtowers status`, eight-entry paginated `groups`, and explicit loaded-guard `inspect` inherit the permission-level-2 root. Source contracts exclude locate/chunk/structure mutation APIs, and the Forge case executes every command, rejects permission level 1, and preserves exact marker, entity, and inventory state. |

## Compatibility and performance

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| TOWER-COMPAT-01 | Generate with representative village, terrain, biome, structure, and optimization mods. | Datapack/registry composition remains deterministic, failures are attributable, and no order-sensitive vanilla village-pool mutation occurs. | Partial | Standard namespaced resources and datapack priority are retained; worldgen source excludes template-pool mutation/client imports, and bounded error diagnostics expose rejected group reasons. Exact third-party mod combinations and visual results remain Step 17 evidence. |
| TOWER-COMPAT-02 | Run a dedicated server with clients that join before, during, and after watchtower chunk generation. | Server owns placement and spawning; clients agree on blocks, guards, roles, equipment, Armory state, and squad identity without client-only loading errors. | Partial | All placement/integration work remains server-only with no new packet or client worldgen reference; the Forge dedicated GameTest source compiles and runs server-side. Retained multi-client joins around generation remain Step 17 evidence. |
| TOWER-PERF-01 | Generate 100 accepted and rejected candidate starts across retained seeds. | Candidate work and marker handling stay within declared bounds; no world/entity scan, recursive structure lookup, or forced chunk occurs. | Partial | Step 14 records only identifier work during structure placement. Live integration adds one raw loot evaluation aborted after 64 emitted stacks, one nine-slot plan, and at most four server-thread member selections with their existing lazy nearby-cap query. It performs no structure-side live query or chunk access beyond Steps 11-13 bounds. The retained 100-start timing run remains. |
| TOWER-PERF-02 | Keep 100 generated towers loaded with no combat or resupply request. | Watchtower generation contributes zero recurring tasks, entity scans, block scans, paths, chunk tickets, and block-entity ticks after placement. | Partial | Source contracts exclude a tower manager, retry, scan, chunk acquisition, logging, or generated Armory ticker; after the existing one-time entity handoff all runtime work belongs to normal guard/Armory systems. The retained 100-loaded-tower measurement remains. |
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
- [x] Natural-loadout, role, squad, Armory, and loot integration implemented.
- [x] Schema-10 watchtower generation controls, schema-11 tower gun-chance
  multiplier, modpack precedence, and bounded operator diagnostics implemented.
- [x] Future-data quarantine, completed-object lifecycle authority, and
  compatibility failure attribution implemented.
- [ ] Integration and lifecycle matrix completed.
- [ ] Exact candidate identity and all release evidence recorded.

# Village Defense Logistics 1.3 validation matrix

This matrix is created before production implementation. `Not implemented` is
the expected Phase 0 state. A row may become `Pass` only when its automated or
manual evidence is retained against the exact candidate JAR. These cases will
be promoted into `docs/release-checklist.md` before the `v1.3` release tag.

The implemented state, request, precedence, reservation, transaction,
movement, configuration, loaded-index, and Step 6 runtime contracts are
defined in [Armory resupply contracts](armory-resupply-contracts.md). Rows stay
partial until every case named by that row has retained automated or manual
evidence against the candidate JAR.

## Candidate identity

- Candidate version: `1.3-beta1`
- Baseline tag: `v1.2`
- Baseline commit: `ee4a63ee596992e880f50da8463761438739e0e7`
- Baseline JAR SHA-256: `9809fdaf10a7d34369d108383a6f41a3eba5a2fb7e60861a67f26c0373936c6a`
- Candidate commit: record before validation
- Candidate JAR SHA-256: `d498dabe6e87de95034a70befc92997a6a9d8b931211362471606242cf480417`
- Tested TaCZ content packs: record names and exact versions

## Armory index and access

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| ARMORY-INDEX-01 | Place, load, unload, replace, and break public and squad-associated Armories. | The dimension-local index contains exactly the loaded valid block entities and never loads a chunk. | Partial | A Forge GameTest proves real block-entity registration, bounded query, removal, and absence; source contracts require level/server cleanup and forbid chunk acquisition. Direct chunk-unload/reload evidence remains for the final matrix. |
| ARMORY-INDEX-02 | Search with public, matching-squad, different-squad, unassigned, orphaned, corrupt, and future-version locks. | Only readable public or matching-squad Armories are eligible; malformed access fails closed. | Partial | Existing lock and candidate tests cover public, matching, mismatched, missing, orphaned, and malformed access; the automatic runtime GameTest covers public selection. A full runtime lock matrix remains. |
| ARMORY-INDEX-03 | Put more Armories in range than the hard candidate cap. | Candidate inspection and ordering remain deterministic and bounded before sorting. | Pass | A Forge GameTest indexes three real Armories and proves a two-record visit cap; source contracts require deterministic section and final distance/position ordering. |

## Reservations and transactions

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| ARMORY-RESERVE-01 | Make multiple compatible guards request one limited stack simultaneously. | Reservations prevent double claims and expire or release on every terminal outcome. | Pass | Unit tests prove idempotence, contention, claim limits, expiry, and bulk release; automatic and direct-withdrawal GameTests end with zero owned reservations. |
| ARMORY-RESERVE-02 | Change Armory contents through a player, hopper, and Forge pipe while a reservation is active. | Final revalidation transfers only available compatible units without loss or duplication. | Partial | GameTests prove reduced and replaced live sources fail or transfer conservatively through the Forge item handler. Separate manual player/hopper/pipe coverage remains. |
| ARMORY-TXN-01 | Exercise loose ammo, finite boxes, empty boxes, incompatible ammo, partial space, full inventory, and different primary/sidearm calibers. | Simulate-before-commit moves only the quantity both inventories can accept and preserves every remainder. | Pass | Unit and Forge tests cover loose ammunition, indivisible finite boxes, incompatibility, live need, bounded destination capacity, rollback, and both stable weapon roles. |
| ARMORY-TXN-02 | Remove the Armory, kill or convert the guard, replace the weapon, unload the chunk, and stop the server during each transaction phase. | The transaction cancels or recovers exactly once without losing or duplicating items. | Partial | GameTests cover Armory/source replacement, weapon/state change, combat cancellation, and feature disable; lifecycle source contracts release entity, dimension, and server state. Direct stop/unload timing evidence remains. |

## Resupply movement

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| ARMORY-PATH-01 | Request from reachable, blocked, distant, and unloaded Armories. | Only a reachable loaded candidate receives bounded path attempts; failures enter backoff. | Partial | Automatic selection/withdrawal and direct-reservation GameTests prove loaded reachable travel, idempotent start, and source-change cancellation; unit/source tests prove range, eight attempts, progress, backoff, and no-force boundaries. Blocked/distant runtime cases remain. |
| ARMORY-PATH-02 | Interrupt travel with combat, Hold, Fall Back, Focus Target, follow, patrol, death, and recovery-required state. | Precedence is deterministic; reservations release when diversion is no longer valid; unchanged native behavior resumes. | Partial | Combat interruption passes with a real owned path and disabled withdrawal fails closed; the shared gate and low-priority goal cover all named precedence categories, but the complete runtime interruption matrix remains. |
| ARMORY-PATH-03 | Resupply a dry primary with a usable sidearm and a dry active sidearm with a stowed primary. | The effective weapon role and TaCZ reserve model determine the request without weapon thrashing. | Partial | Need, request, preflight, and withdrawal tests cover both stable roles; the automatic runtime GameTest covers a dry active primary. The paired sidearm-runtime cases remain. |

## Configuration and diagnostics

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| ARMORY-CFG-01 | Upgrade a complete schema-8 configuration and change every new schema-9 value. | Existing values remain exact; new bounded defaults synchronize; out-of-range values are corrected and an invalid trigger/target pair fails closed. | Pass | Unit tests preserve representative schema-8 choices, reset every new field only during migration, exercise all numeric bounds, and verify the immutable hard-bound snapshot. |
| ARMORY-DIAG-01 | Inspect idle, searching, reserved, travelling, withdrawing, cooldown, and unreachable guards. | On-demand output reports the authoritative state, reason, candidate, quantity, and retry without enabling background work. | Partial | `/vgg inspect` reports the live registered controller snapshot; GameTests cover dormant compatibility and post-transfer cooldown with target/quantity evidence. Intermediate and unreachable state captures remain. |

## Squad feedback and coordination

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| SQUAD-UX-01 | Issue every command from main hand and offhand with loaded, unloaded, unauthorized, and capped members. | Feedback reports each outcome accurately and visualization is visible only to the issuer for a bounded duration. | Not implemented | |
| SQUAD-TARGET-01 | Engage many weak hostiles with a mixed-role squad. | Normal assignments avoid unnecessary overkill while every chosen target still passes ROE and ally validation. | Not implemented | |
| SQUAD-TARGET-02 | Use Focus Target, kill or unload targets, change membership, and expire commands. | Focus deliberately overrides distribution; stale claims clear without a scan or unsafe fallback target. | Not implemented | |

## Guard roles and compatibility

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| ROLE-DATA-01 | Load valid, malformed, cyclic or conflicting, missing, and future-version role datapacks. | Publication is bounded and atomic; existing roleless guards and last-known-good data remain safe. | Pass | Unit tests cover strict syntax, bounds, inheritance, cycles, conflicts, missing parents, future formats, and last-known-good publication; Forge startup verifies bundled publication. |
| ROLE-ORDER-01 | Combine role defaults with every explicit guard order, global restriction, and temporary command. | Global safety wins, then explicit orders, then role defaults; assigning a role never creates or moves an item. | Pass | Unit tests cover all seven precedence fields and global caps; a Forge GameTest covers persistence, item-neutral assignment, cache invalidation, and effective precedence. Existing squad-command movement remains ahead of effective role movement. |
| VDEF-UPGRADE-01 | Load representative 1.2 guards, sidearm states, Orders Ledgers, squad records, Armory locks, configuration, and format-1/2/3 datapacks. | Every shipped record retains its 1.2 meaning or enters its documented fail-closed recovery path without data loss. | Partial | Existing migration tests cover configuration, guards, sidearms, squads, Armory locks, and formats 1-3. Orders Ledger tests prove version 1 remains readable and does not clear a role; format-4 tests prove older formats reject the new field. Final combined dedicated-server upgrade evidence remains. |

## Performance and dedicated-server evidence

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| VDEF-PERF-01 | Keep 100 loaded Armories idle with no requesting guard. | Zero block-entity ticks, scans, paths, tasks, or forced chunks originate from logistics. | Pass | The default-v9 opt-in run recorded 0 assessments, queries, record/slot inspections, reservations, paths, transfers, and forced-chunk delta; p95/p99 were 0.921/1.191 ms. |
| VDEF-PERF-02 | Keep 100 armed guards above the configured reserve threshold. | Zero Armory queries or logistics paths occur and p95/p99 stays inside the approved no-request envelope. | Pass | The opt-in run recorded 2,149 staggered due summaries but 0 queries, slots, reservations, and paths; p95/p99 were 1.772/2.682 ms and passed the direct armed-idle budget. |
| VDEF-PERF-03 | Trigger 50 simultaneous requests with scarce, unreachable, and mixed-caliber supplies. | Work is hard-bounded, reservations settle, backoff prevents retry storms, and the server remains inside the release timing budget. | Pass | Five inaccessible mixed-caliber Armories produced 218 bounded queries, 205 path constructions, max 8 per trip, 194 no-supply and 32 unreachable outcomes, 0 transfers/forced chunks, and no live reservation after natural settlement or cleanup; p95/p99 were 1.486/1.803 ms. |
| VDEF-DEDICATED-01 | Repeat stocking, request, travel, withdrawal, cancellation, save/restart, and observer synchronization on a dedicated server. | Server and clients agree on inventories and states with no client-only load failure, loss, duplication, or stuck navigation. | Not implemented | |

## Phase 0 status

- [x] Baseline release identity recorded.
- [x] Compatibility inputs recorded.
- [x] Non-goals and performance invariants recorded.
- [x] Feature validation cases defined before implementation.
- [x] Production implementation started with the inactive Step 1 contracts.
- [ ] Candidate identity recorded.
- [ ] All automated rows passed.
- [ ] All manual rows passed.

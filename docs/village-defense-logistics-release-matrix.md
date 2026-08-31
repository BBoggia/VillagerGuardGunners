# Village Defense Logistics 1.3 validation matrix

This matrix is created before production implementation. `Not implemented` is
the expected Phase 0 state. A row may become `Pass` only when its automated or
manual evidence is retained against the exact candidate JAR. These cases will
be promoted into `docs/release-checklist.md` before the `v1.3` release tag.

Step 1's inactive state, request, precedence, and timing contracts are defined
in [Armory resupply contracts](armory-resupply-contracts.md). Matrix rows
remain `Not implemented` until the later runtime phases supply the behavior
and retained evidence each row requires.

## Candidate identity

- Development version: `1.3-dev`
- Baseline tag: `v1.2`
- Baseline commit: `ee4a63ee596992e880f50da8463761438739e0e7`
- Baseline JAR SHA-256: `9809fdaf10a7d34369d108383a6f41a3eba5a2fb7e60861a67f26c0373936c6a`
- Candidate commit: record before validation
- Candidate JAR SHA-256: record before validation
- Tested TaCZ content packs: record names and exact versions

## Armory index and access

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| ARMORY-INDEX-01 | Place, load, unload, replace, and break public and squad-associated Armories. | The dimension-local index contains exactly the loaded valid block entities and never loads a chunk. | Not implemented | |
| ARMORY-INDEX-02 | Search with public, matching-squad, different-squad, unassigned, orphaned, corrupt, and future-version locks. | Only readable public or matching-squad Armories are eligible; malformed access fails closed. | Not implemented | |
| ARMORY-INDEX-03 | Put more Armories in range than the hard candidate cap. | Candidate inspection and ordering remain deterministic and bounded before sorting. | Not implemented | |

## Reservations and transactions

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| ARMORY-RESERVE-01 | Make multiple compatible guards request one limited stack simultaneously. | Reservations prevent double claims and expire or release on every terminal outcome. | Not implemented | |
| ARMORY-RESERVE-02 | Change Armory contents through a player, hopper, and Forge pipe while a reservation is active. | Final revalidation transfers only available compatible units without loss or duplication. | Not implemented | |
| ARMORY-TXN-01 | Exercise loose ammo, finite boxes, empty boxes, incompatible ammo, partial space, full inventory, and different primary/sidearm calibers. | Simulate-before-commit moves only the quantity both inventories can accept and preserves every remainder. | Not implemented | |
| ARMORY-TXN-02 | Remove the Armory, kill or convert the guard, replace the weapon, unload the chunk, and stop the server during each transaction phase. | The transaction cancels or recovers exactly once without losing or duplicating items. | Not implemented | |

## Resupply movement

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| ARMORY-PATH-01 | Request from reachable, blocked, distant, and unloaded Armories. | Only a reachable loaded candidate receives bounded path attempts; failures enter backoff. | Partial | Direct-reservation GameTests prove loaded reachable travel, idempotent start, and source-change cancellation; policy/source tests prove range, eight attempts, progress, backoff, and no-force boundaries. Step 2 selection is still absent. |
| ARMORY-PATH-02 | Interrupt travel with combat, Hold, Fall Back, Focus Target, follow, patrol, death, and recovery-required state. | Precedence is deterministic; reservations release when diversion is no longer valid; unchanged native behavior resumes. | Partial | Combat interruption passes with a real owned path and disabled withdrawal fails closed; the shared gate covers all precedence states, but full runtime orchestration is still absent. |
| ARMORY-PATH-03 | Resupply a dry primary with a usable sidearm and a dry active sidearm with a stowed primary. | The effective weapon role and TaCZ reserve model determine the request without weapon thrashing. | Not implemented | |

## Configuration and diagnostics

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| ARMORY-CFG-01 | Upgrade a complete schema-8 configuration and change every new schema-9 value. | Existing values remain exact; new bounded defaults synchronize; out-of-range values are corrected and an invalid trigger/target pair fails closed. | Pass | Unit tests preserve representative schema-8 choices, reset every new field only during migration, exercise all numeric bounds, and verify the immutable hard-bound snapshot. |
| ARMORY-DIAG-01 | Inspect idle, searching, reserved, travelling, withdrawing, cooldown, and unreachable guards. | On-demand output reports the authoritative state, reason, candidate, quantity, and retry without enabling background work. | Partial | `/vgg inspect` reports the gate, need, complete policy, and truthful dormant runtime with zero idle work; active-state coverage awaits the Step 2 index/runtime orchestrator. |

## Squad feedback and coordination

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| SQUAD-UX-01 | Issue every command from main hand and offhand with loaded, unloaded, unauthorized, and capped members. | Feedback reports each outcome accurately and visualization is visible only to the issuer for a bounded duration. | Not implemented | |
| SQUAD-TARGET-01 | Engage many weak hostiles with a mixed-role squad. | Normal assignments avoid unnecessary overkill while every chosen target still passes ROE and ally validation. | Not implemented | |
| SQUAD-TARGET-02 | Use Focus Target, kill or unload targets, change membership, and expire commands. | Focus deliberately overrides distribution; stale claims clear without a scan or unsafe fallback target. | Not implemented | |

## Guard roles and compatibility

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| ROLE-DATA-01 | Load valid, malformed, cyclic or conflicting, missing, and future-version role datapacks. | Publication is bounded and atomic; existing roleless guards and last-known-good data remain safe. | Not implemented | |
| ROLE-ORDER-01 | Combine role defaults with every explicit guard order, global restriction, and temporary command. | Global safety wins, then explicit orders, then role defaults; assigning a role never creates or moves an item. | Not implemented | |
| VDEF-UPGRADE-01 | Load representative 1.2 guards, sidearm states, Orders Ledgers, squad records, Armory locks, configuration, and format-1/2/3 datapacks. | Every shipped record retains its 1.2 meaning or enters its documented fail-closed recovery path without data loss. | Not implemented | |

## Performance and dedicated-server evidence

| ID | Case | Expected result | Status | Evidence |
| --- | --- | --- | --- | --- |
| VDEF-PERF-01 | Keep 100 loaded Armories idle with no requesting guard. | Zero block-entity ticks, scans, paths, tasks, or forced chunks originate from logistics. | Not implemented | |
| VDEF-PERF-02 | Keep 100 armed guards above the configured reserve threshold. | Zero Armory queries or logistics paths occur and p95/p99 stays inside the approved no-request envelope. | Not implemented | |
| VDEF-PERF-03 | Trigger 50 simultaneous requests with scarce, unreachable, and mixed-caliber supplies. | Work is hard-bounded, reservations settle, backoff prevents retry storms, and the server remains inside the release timing budget. | Not implemented | |
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

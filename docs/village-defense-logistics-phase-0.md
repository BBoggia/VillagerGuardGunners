# Village Defense Logistics Phase 0: Baseline and contracts

Phase 0 establishes the post-1.2 development line without changing production
AI, persistence, world generation, ammunition movement, or player-visible
behavior. The work begins from the immutable `v1.2` tag on branch
`codex/village-defense-logistics`; development artifacts use `1.3-dev` so they
cannot be mistaken for the published release.

## Release anchor

| Identity | Value |
| --- | --- |
| Release tag | `v1.2` |
| Release commit | `ee4a63ee596992e880f50da8463761438739e0e7` |
| Release JAR | `villagerguardgunners-1.2.jar` |
| Release JAR SHA-256 | `9809fdaf10a7d34369d108383a6f41a3eba5a2fb7e60861a67f26c0373936c6a` |
| Plain JUnit/source-contract tests | 317 passed |
| Forge correctness GameTests | 73 passed |
| Performance/JFR workloads | 17 defined |
| Minecraft | `1.20.1` |
| Forge | `47.4.23` |
| Guard Villagers | `1.20.1-1.6.19` |
| TaCZ | `1.1.8-hotfix` |
| Fzzy Config | `0.7.6+1.20.1` |
| Kotlin for Forge | `4.12.0` |

The reference timing tables and interpretation remain in
`docs/phase-8-validation-and-performance.md`. A later implementation phase may
add workloads, but it must not rewrite the 1.2 evidence as though it had been
captured by new production code.

## Compatibility boundary

Phase 0 freezes these shipped formats as migration inputs:

- Fzzy Config schema 8;
- natural-loadout formats 1, 2, and 3;
- natural-loadout provenance schema 1;
- sidearm-storage schemas 1 through 3, with schema 3 current;
- Orders Ledger data version 1;
- squad membership, binding, metadata, and command schema 1; and
- Village Armory squad-lock schema 1.

Future additions must be additive or explicitly migrated. Existing records may
not be reinterpreted to grant items, change squad ownership, relax ally or
rules-of-engagement safety, or discard unreadable future-version data. Existing
format-1 through format-3 natural-loadout datapacks must retain their selection
and issuance meaning when format 4 is eventually introduced.

Configuration schema 9 is reserved for the Armory-resupply settings. Its
migration will initialize only new fields and preserve every schema-8 value.
Orders Ledger version 2 and natural-loadout format 4 are reserved design
targets, not Phase 0 production formats.

## Version 1.3 scope

The Village Defense Logistics release is divided into these implementation
steps:

1. Define the Armory resupply state and request contracts.
2. Add a loaded-chunk-only Armory runtime index.
3. Add expiring reservations and transactional ammunition withdrawal.
4. Add bounded, interruptible resupply movement.
5. Add configuration and on-demand diagnostics.
6. Improve Squad Command Ledger feedback and visualization.
7. Add bounded squad target coordination.
8. Add datapack-defined guard roles without item creation.

Natural group composition and watchtower generation remain planned for the
separate 1.4 world-generation line after the 1.3 systems are stable.

## Non-goals

Phase 0 and the 1.3 logistics line do not authorize:

- guard-tower or outpost world generation;
- retroactive modification of existing chunks;
- custom projectile, reload, or weapon animation systems;
- continuous ammunition balancing or automatic surplus deposits;
- global per-tick Armory, guard, hostile, squad, or structure scans;
- forced chunk loading;
- complex cover finding, suppression, morale, or formation AI; or
- hardcoded weapon or ammunition grants outside the existing natural-loadout
  transaction.

## Required runtime invariants

Every implementation phase must preserve the following:

- Armories and future post metadata have no block-entity ticker.
- A guard with no ammunition need performs no Armory query or path request.
- Searches inspect only a hard-bounded set of indexed Armories in loaded
  chunks.
- A request, reservation, or target claim has an expiry and a bounded retry
  cadence.
- No failure duplicates, deletes, or overwrites a weapon or ammunition stack.
- Temporary squad commands, roles, and logistics remain subordinate to rules
  of engagement, ally protection, explosive safety, and TaCZ native state.
- Operator diagnostics perform no recurring background work.
- Normal Guard Villagers ownership, follow, patrol, inventory authorization,
  death, and conversion behavior remains intact.

## Initial performance budgets

The first production change must add explicit evidence for these boundaries:

- `100` loaded idle Armories execute zero tick callbacks and zero entity or
  block searches.
- `100` armed guards above their reserve threshold execute zero Armory candidate
  queries and create zero paths for logistics.
- A simultaneous `50`-guard low-ammunition event inspects no more than the
  configured hard candidate limit per requesting guard and never loads a chunk.
- Failed navigation enters exponential or tiered backoff rather than retrying
  every tick.
- The no-request armed-guard workload must remain within a provisional `1.05x`
  p95/p99 timing envelope relative to its otherwise identical 1.2 workload;
  any statistically unstable result requires repeated same-machine captures,
  not a widened budget by default.

Exact candidate limits and timing allowances become release requirements only
after their contract phase defines and tests the corresponding production
constants.

## Phase 0 exit criteria

Phase 0 is complete when:

- the development branch points directly to `v1.2` before its Phase 0 commit;
- development artifacts identify themselves as `1.3-dev`;
- the reference release identity and checksum are recorded;
- all shipped persistence and datapack versions are listed as compatibility
  inputs;
- the 1.3 feature matrix exists before production work begins;
- non-goals and performance invariants are explicit;
- no production class or resource behavior changes; and
- `./gradlew clean check` passes on JDK 17.

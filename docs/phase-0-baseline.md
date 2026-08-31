# Phase 0: Baseline and Safety Net

Phase 0 freezes the reviewed compatibility target and establishes the checks that later rewrites must pass. It intentionally makes no gameplay or runtime behavior changes.

## Supported stack

| Component | Reviewed target |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.23 |
| Java target | 17 |
| Guard Villagers | 1.6.19 |
| Timeless and Classics Zero (TaCZ) | 1.1.8-hotfix |

`ProjectCompatibilityContractTest` locks these versions and the exact reviewed Minecraft, Forge, Guard Villagers, and TaCZ runtime ranges. An accidental dependency downgrade or unsupported version drift now fails `./gradlew test`.

Guard Villagers exposes the Forge mod version `1.20.1-1.6.19`; its runtime metadata range includes that Minecraft-prefixed form even though the Gradle artifact selector is `1.6.19`.

## Pre-change snapshot

- Git baseline: `6b1917a` (`Initial commit`), with a clean worktree before Phase 0.
- `./gradlew test build`: successful before Phase 0; Gradle reported `test NO-SOURCE`.
- Production source: 5,288 lines of Java before Phase 0.
- Current runtime remains the legacy implementation: custom guard combat/reload/melee goals, custom movement and gun mechanics, a custom bullet entity/renderer, expanded guard inventory/menu behavior, broad guard/renderer mixins, behavior-tree experiments, and hand-authored rebalance configuration.

The snapshot is descriptive, not an endorsement of the current architecture. Later phases should remove the legacy systems only after their replacement has a passing test path.

## Verification layers

Run the cheapest checks first:

1. `./gradlew test` — fast, deterministic unit and project-contract tests.
2. `./gradlew build` — compilation, resources, mixin processing, packaging, and reobfuscation.
3. `./gradlew runGameTestServer` — 24 Forge integration tests covering real guard construction, capabilities, save/migration/conversion, authorized menu behavior, TaCZ gun/operator initialization, tactical metadata caching, profile-driven movement, visibility, aim, maximum-cap and explosive-standoff firing gates, the preferred-versus-maximum range boundary, registered administrator diagnostics, native per-round burst cancellation, lifecycle edge cases, melee fallback, shield-goal compatibility, and direct/explosive friendly-fire backstops.
4. `./gradlew runServer` — dedicated-server smoke test for side-only classloading and multiplayer behavior.
5. `./gradlew runClient` — menu, animation, sound, and observed combat validation.

The GameTests use Minecraft's built-in minimal empty structure, so the development run enables both the add-on and `minecraft` GameTest template namespaces. They execute against transformed runtime classes and the exported TaCZ default gun pack rather than mocks of either mod.

## Invariants for every implementation phase

- The project compiles and produces a reobfuscated mod jar.
- Guard Villagers and TaCZ remain required on both client and server.
- New combat decisions are server-authoritative.
- The addon does not add a second projectile, damage, sound, recoil, spread, burst, or per-shot networking implementation.
- AI predicates such as `canUse` and `canContinueToUse` remain cheap and free of state-changing work.
- Expensive visibility, entity-query, and pathfinding work has an explicit cadence and cannot accumulate a lag backlog.
- No per-tick or per-shot INFO logging is introduced.
- Existing saves are either read correctly or migrated without silent item loss.

## Functional validation matrix

The following matrix becomes executable incrementally as its owning phase lands.

| Area | Required cases |
| --- | --- |
| Native fire | semi-auto, automatic, burst, shotgun/multiple projectiles |
| Gun state | cooldown, reload, manual bolt, heat/overheat, charge, inventory-fed ammo |
| Gun data | attachments and datapack-defined TaCZ gun behavior |
| Lifecycle | weapon swap during reload/charge, target death during burst, save/load |
| Inventory | loose ammo, ammo boxes, quick-move, death drops, legacy slots 6-8 migration |
| Safety | direct, piercing, and explosive fire near protected villagers/guards/golems/owner |
| AI | approach, hold range, retreat, lost line of sight, permanently dry gun, melee fallback |
| Client | normal and Steve guard models; PlayerAnimator present and absent |
| Environment | integrated client, dedicated server, one observer, multiple observers |

## Performance baseline protocol

Performance comparisons must use the same world, gun definitions, targets, obstacle layout, JVM arguments, view distance, and observation time. Warm each scenario before recording it.

Record p50/p95/p99 MSPT, allocation/GC activity, live TaCZ bullet count, network traffic, and client frame time for:

- 100 idle armed guards;
- 10, 25, and 50 guards with semi-automatic rifles;
- 10, 25, and 50 guards with automatic rifles;
- shotgun and explosive worst cases;
- obstructed targets that force navigation;
- protected friendlies crossing firing lanes;
- one and multiple observing clients.

The addon-specific target is to remain a small minority of total combat cost (approximately 10% or less where profiling can separate it). Structural budgets are:

- no custom projectile or per-shot packet;
- no gun `draw`/attachment-cache rebuild per shot;
- no delayed-shot catch-up after a lag spike;
- at most one visibility sample per active guard every two ticks in steady combat;
- at most one path recalculation per moving guard every 20 ticks in steady combat;
- no more than one friendly-fire corridor query per guard for an initial due shot or an actually scheduled native burst round; a safe result is reused only within the same unmoved tick, while an unsafe result may be retained for two ticks.

Numeric runtime results should be recorded only after the native controller creates a stable, representative combat scenario. Profiling the legacy custom projectile as the future baseline would compare two different gameplay implementations and produce misleading conclusions.

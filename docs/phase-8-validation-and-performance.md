# Phase 8: Validation and Performance Release Gate

Phase 8 turns the behavior and performance requirements from Phases 0–7 into repeatable release evidence. Correctness GameTests and the opt-in scalability suite now live in development-only source sets, so none of the test code is packaged in the production mod.

## Automated release boundary

`./gradlew check` now runs the unit/source contracts, compiles both development GameTest suites, builds and reobfuscates the release JAR, and verifies the artifact itself. The artifact check requires the runtime classes, metadata, mixin configuration, refmap, inventory texture, and supported Fzzy Config integration. It rejects GameTest classes, the old projectile and combat simulation, the behavior tree/reload systems, legacy config classes, `.DS_Store`, a timestamped manifest, unexpected dependency declarations, and missing common-server mixin mappings.

The suites are deliberately isolated:

- `src/gametest` contains 19 correctness GameTests and is selected by the normal `runGameTestServer` task.
- `src/perftest` contains 12 sequential scalability workloads and is selected only with `-Pphase8Performance=true`.
- `src/main` contains production code only.
- the performance run uses `run-phase8-performance`, deletes its generated world and old reports before each matrix, and writes fresh evidence to `build/reports/phase8-performance`.

`PhaseEightReleaseGateContractTest` prevents the source-set separation, matrix, evidence fields, config-profile reset, and artifact gate from silently being removed. `verifyPhase8PerformanceReports` then checks that all 12 JSON/JFR pairs are present, nonempty, internally consistent, percentile ordered, captured with the complete default-v1 config profile, and backed by accepted native TaCZ shots wherever firing is expected.

## Correctness coverage

The automated layer now covers every correctness item that can be made deterministic with the reviewed default gun pack:

| Requirement | Evidence |
| --- | --- |
| Ammo slots accept only loose TaCZ ammunition and ammo boxes | plain JUnit handler tests |
| Ammo save/load, old slot migration, and rollback-format deduplication | migration unit tests plus real Guard save/load GameTests |
| Zombie/lightning guard conversion preserves isolated ammo and pending overflow | transformed conversion GameTest covering both upstream discard paths |
| Gun binding changes only with the held weapon | controller contracts plus real reload/charge/bolt weapon-swap GameTests |
| Every TaCZ `ShootResult` has an explicit transition | source contract over the complete result switch |
| AUTO, HOLD, and DELAY charge/reset progression | deterministic trigger-state unit tests |
| Corridor/spread/movement intersection geometry | geometry unit tests |
| Direct friendly hits and projectile explosions are protected | transformed Forge/TaCZ GameTests |
| Swap during reload, charge, or manual bolt | real operator/weapon GameTests |
| Target death during a native burst | real M16 burst GameTest plus cancellable per-round `GunFireEvent` coverage |
| Permanently dry gun yields to Guard Villagers melee | real free-will Guard goal GameTest |
| Native fire mutates the bound gun without redrawing it per shot | real Glock firing GameTest |
| Preferred TaCZ range does not incorrectly cap firing | real guard-goal TaCZ test beyond preferred range and within the configured maximum |
| Administrator inspection command is registered and executable | transformed command-dispatch GameTest against a real Guard |
| Lag does not create a firing backlog and per-tick work is capped | cadence unit tests and source contracts |
| A blocked lateral reposition cannot loop forever in one direction | progress-helper unit test and bounded reversal source contract |

The native firing assertion checks TaCZ's gun state, ammunition consumption, shot timestamp, and the identity of the bound stack. It also confirms that continued fire does not change `drawTimestamp`, guarding against an expensive draw/property rebuild per shot.

## Performance harness

Run the complete sequence in this order:

1. `./gradlew clean test build`
2. `./gradlew runGameTestServer`
3. `./gradlew runGameTestServer -Pphase8Performance=true`
4. `./gradlew verifyPhase8PerformanceReports`

Each performance workload creates real Guard Villagers, native TaCZ guns, targets, projectiles, safety queries, goal selectors, and entity ticks. It warms for 100 ticks and samples 1,000 ticks. Every scenario produces p50/p95/p99/max MSPT, post-warmup native shot and total-ammunition evidence, live TaCZ bullets, GC count, heap delta, the complete active guard-gunner config snapshot, and a focused JFR profile with 5 ms execution sampling. Total ammunition includes the gun and dedicated guard inventory so native reloads cannot hide consumption. The test task removes its generated config before launch, and report verification rejects any scenario that did not use the default-v1 values.

Forge's GameTest server runs as fast as possible, while TaCZ fire and reload timing use wall-clock milliseconds. The harness therefore advances TaCZ's public cooldown timestamps at a nominal 20 TPS weapon cadence and advances an already-active native reload timestamp. It does not start or complete reloads itself, create projectiles, deal damage, emulate scripts, or bypass the addon controller. The explosive gate requires at least two rounds per guard, proving the RPGs reloaded from the dedicated inventory instead of ending after their preloaded shots. Results are appropriate for same-machine regression comparisons and profiler attribution, not as an absolute public-server capacity claim. For that reason the reports explicitly record `absoluteTimingGateApplied: false`.

The protected-lane workload moves 25 invulnerable, gravity-free villagers back and forth across their corresponding guard-to-target corridors. Making the fixtures invulnerable keeps all lanes present for the full sample; the normal native pre-fire safety logic still decides whether the guard may shoot. The workload must record at least one accepted native shot, proving that both blocked and open portions of the crossing are exercised.

## Reference run: 2026-08-27

All 12 workloads passed on the same Apple-silicon development machine and Java 17.0.12. Times below are total GameTest server work, not addon-only time.

| Scenario | Guards | Avg ms | p95 ms | p99 ms | Max ms | Native shots / ammo | Live bullets | GC |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Unarmed idle baseline | 100 | 1.213 | 1.536 | 1.779 | 6.881 | 0 / 0 | 0 | 1 |
| Armed idle | 100 | 1.402 | 1.783 | 2.072 | 7.534 | 0 / 0 | 0 | 1 |
| Semi-auto | 10 | 0.887 | 1.025 | 1.173 | 7.176 | 2,610 / 2,610 | 8 | 1 |
| Semi-auto | 25 | 1.335 | 1.623 | 1.822 | 23.473 | 7,069 / 7,069 | 18 | 1 |
| Semi-auto | 50 | 1.816 | 2.157 | 2.484 | 8.855 | 14,424 / 14,424 | 41 | 1 |
| Automatic | 10 | 0.935 | 1.069 | 1.200 | 5.443 | 4,217 / 4,217 | 7 | 0 |
| Automatic | 25 | 2.089 | 2.464 | 2.741 | 11.582 | 8,489 / 8,489 | 12 | 1 |
| Automatic | 50 | 2.520 | 3.314 | 4.121 | 22.094 | 19,173 / 19,173 | 30 | 2 |
| Automatic shotgun | 10 | 1.058 | 1.294 | 1.455 | 5.737 | 20,350 / 2,035 | 0 | 0 |
| Explosive | 10 | 2.060 | 3.413 | 5.434 | 15.138 | 0 / 427 | 0 | 2 |
| Obstacle navigation | 25 | 1.639 | 2.626 | 4.042 | 40.499 | 0 / 0 | 0 | 1 |
| Crossing friendlies | 25 | 1.719 | 2.311 | 3.256 | 11.405 | 2,879 / 2,879 | 14 | 1 |

The shotgun's shot count is pellet-level while its ammo count is shell-level. The RPG reports ammunition consumption even though its operator `shootCount` remains zero, so the gate intentionally accepts either native signal. Its 427 consumed rockets are post-warmup evidence from repeated native fire/reload cycles, far above the required 20. A negative heap delta in the raw JSON means a GC reclaimed more pre-existing heap than the scenario allocated; it is not negative allocation. The armed-idle average was 0.189 ms above the 100-guard unarmed baseline, including TaCZ operator and held-gun work as well as this add-on.

JFR captures contained 89–258 server-thread execution samples per scenario. With full 64-frame stacks, inclusive attribution—any sample with the add-on anywhere in its stack—ranged from 0% for both idle baselines to 6.86% for semi-50. Automatic-50 was 6.76% and semi-25 was 5.19%; this inclusive count deliberately includes native calls beneath the guard goal and development-harness callbacks. Leaf/self attribution was zero in 11 scenarios. The explosive workload had one `GuardGunCombatGoal.hasClearShot` leaf sample out of 209 server-thread samples (0.48%). These short profiles are directional evidence rather than a substitute for a long production-server capture.

The largest total-server maxima were isolated ticks in obstacle navigation (40.5 ms) and semi-25 (23.5 ms). Every p99 remained at or below 5.44 ms, including obstacle navigation at 4.04 ms. The obstacle profile contained no add-on leaf sample, but obstacle-heavy real-server profiling remains worthwhile before calling a large battle configuration production-ready.

## Remaining manual compatibility matrix

The default TaCZ pack does not provide a representative gun for every extension point, and headless GameTests cannot validate client animation or observer synchronization. These checks remain required before a public release:

The executable pass/fail worksheet is maintained in `docs/release-checklist.md`.

| Case | Automated coverage | Manual release check still needed |
| --- | --- | --- |
| Semi-auto rifle/pistol | native Glock firing | observe sustained combat, reload, sound, and ammo UI |
| Full-auto rifle | AK-47 workloads at 10/25/50 | observe animation and synchronization under sustained fire |
| Native burst | real M16 burst and target-death cleanup | observe a complete burst and mid-burst target/swap transitions |
| Shotgun/multiple projectile | native AA-12 workload | verify pellet impacts, sound, and animation with a client |
| Manual bolt/lever/pump | real bolt state and swap cancellation | run one representative gun of each action type through repeated cycles |
| Heat-limited weapon | `OVERHEATED` transition and bounded retry contracts | test a real heat-enabled gun through overheat and recovery |
| Inventory-fed gun | shared ammo capability and native reload delegation | test a representative minigun/inventory-fed datapack gun and ammo box |
| Explosive weapon | RPG workload and explosion backstop | observe safe and unsafe blast placement around villagers, guards, golems, and owner |
| Charged AUTO/HOLD/DELAY gun | deterministic TaCZ charge semantics | test representative third-party definitions because the default pack lacks all three |
| Attachments altering ammo/spread/explosion | native property lookup and conservative safety bounds | test representative attachment combinations and datapack reloads |
| Client models | narrow normal/Steve model mixins | verify idle, aim, fire, reload, bolt, movement, crouch, swim, eat, and death poses |
| Optional animation mod | client-only compatibility boundary | repeat models with Player Animator present and absent |
| Observers/network | server-authoritative native path, no addon shot packet | repeat with one and multiple observing clients and watch animation, sound, ammo, bandwidth, and lag-back |

For the observer/performance pass, use a normal dedicated server at a fixed view distance and fixed JVM settings, warm each population before capture, and compare 10/25/50 guard runs with the same world and targets. Record server p95/p99 MSPT, client frame time, network traffic, live projectiles, GC/allocation, and a long JFR or profiler capture. Do not compare results from different gun packs, attachment sets, worlds, or hardware as though they were the same benchmark.

## Release interpretation

The automated gate establishes that the addon remains a narrow bridge to TaCZ: no custom packets or projectiles, no per-shot draw/property rebuild, no firing backlog, bounded visibility/path/safety work, no combat-path logging spike, development tests excluded from the JAR, a reproducible default config profile, and low sampled add-on leaf time in the reference matrix. A release candidate is ready only after the remaining manual client, third-party gun, and multi-observer rows above are checked against the exact modpack being shipped.
